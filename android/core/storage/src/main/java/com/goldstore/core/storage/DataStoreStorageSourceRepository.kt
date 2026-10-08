package com.goldstore.core.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.goldstore.core.model.StorageSource
import com.goldstore.core.model.StorageType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Implementación de [StorageSourceRepository] respaldada por DataStore Preferences.
 * No borra archivos físicos ni elimina fuentes automáticamente si están inaccesibles.
 */
class DataStoreStorageSourceRepository(
    private val dataStore: DataStore<Preferences>,
    private val contentResolver: ContentResolver? = null
) : StorageSourceRepository {

    private val storageSourcesKey = stringPreferencesKey("gold_store_storage_sources")

    override fun getStorageSources(): Flow<List<StorageSource>> {
        return dataStore.data.map { preferences ->
            val jsonString = preferences[storageSourcesKey] ?: "[]"
            deserializeSources(jsonString)
        }
    }

    override suspend fun getStorageSource(id: String): StorageSource? {
        val jsonString = dataStore.data.map { it[storageSourcesKey] ?: "[]" }.firstOrNull() ?: "[]"
        return deserializeSources(jsonString).firstOrNull { it.id == id }
    }


    override suspend fun addStorageSource(source: StorageSource) {
        dataStore.edit { preferences ->
            val jsonString = preferences[storageSourcesKey] ?: "[]"
            val currentList = deserializeSources(jsonString).toMutableList()

            // Prevenir duplicados por URI
            val existingIndex = currentList.indexOfFirst { it.uriString == source.uriString }
            if (existingIndex >= 0) {
                // Actualiza metadatos si ya existía la misma URI
                currentList[existingIndex] = source
            } else {
                currentList.add(source)
            }
            preferences[storageSourcesKey] = serializeSources(currentList)
        }
    }

    override suspend fun removeStorageSource(id: String) {
        dataStore.edit { preferences ->
            val jsonString = preferences[storageSourcesKey] ?: "[]"
            val currentList = deserializeSources(jsonString).toMutableList()
            val target = currentList.firstOrNull { it.id == id }
            if (target != null) {
                currentList.remove(target)
                preferences[storageSourcesKey] = serializeSources(currentList)

                // Liberar permisos persistentes únicamente si ninguna otra fuente comparte la misma URI
                val uriStillUsed = currentList.any { it.uriString == target.uriString }
                if (!uriStillUsed && contentResolver != null) {
                    try {
                        val uri = Uri.parse(target.uriString)
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        contentResolver.releasePersistableUriPermission(uri, flags)
                    } catch (_: Exception) {
                        // Tolerar si ya fue revocado o no es liberable
                    }
                }
            }
        }
    }

    override suspend fun updateStorageSource(source: StorageSource) {
        dataStore.edit { preferences ->
            val jsonString = preferences[storageSourcesKey] ?: "[]"
            val currentList = deserializeSources(jsonString).toMutableList()
            val index = currentList.indexOfFirst { it.id == source.id }
            if (index >= 0) {
                currentList[index] = source
                preferences[storageSourcesKey] = serializeSources(currentList)
            }
        }
    }

    override suspend fun verifyPermissions(source: StorageSource): Boolean {
        if (contentResolver == null) return source.isAccessible
        return try {
            val uri = Uri.parse(source.uriString)
            SafUriHelper.hasPersistedPermission(contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }

    internal fun serializeSources(sources: List<StorageSource>): String {
        val jsonArray = JSONArray()
        for (source in sources) {
            val jsonObject = JSONObject().apply {
                put("id", source.id)
                put("uriString", source.uriString)
                put("displayName", source.displayName)
                put("storageType", source.storageType.name)
                put("isAccessible", source.isAccessible)
                if (source.lastScannedEpochMs != null) {
                    put("lastScannedEpochMs", source.lastScannedEpochMs)
                }
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    internal fun deserializeSources(jsonString: String): List<StorageSource> {
        val list = mutableListOf<StorageSource>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val typeName = obj.optString("storageType", StorageType.UNKNOWN.name)
                val type = try {
                    StorageType.valueOf(typeName)
                } catch (_: Exception) {
                    StorageType.UNKNOWN
                }
                list.add(
                    StorageSource(
                        id = obj.getString("id"),
                        uriString = obj.getString("uriString"),
                        displayName = obj.getString("displayName"),
                        storageType = type,
                        isAccessible = obj.optBoolean("isAccessible", true),
                        lastScannedEpochMs = if (obj.has("lastScannedEpochMs")) obj.getLong("lastScannedEpochMs") else null
                    )
                )
            }
        } catch (_: Exception) {
            // Manejar JSON corrupto de forma resiliente
        }
        return list
    }
}
