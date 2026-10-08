package com.goldstore.core.network

import com.goldstore.core.model.CatalogFeed
import com.goldstore.core.model.CatalogItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FpkgiCatalogProvider(
    private val httpClient: OkHttpClient = OkHttpClient()
) : CatalogProvider {

    override suspend fun fetchFeed(feed: CatalogFeed): List<CatalogItem> {
        val request = Request.Builder().url(feed.url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Error al descargar catálogo: código HTTP ${response.code}")
            }
            val body = response.body?.string() ?: return emptyList()
            return parseCatalogJson(body)
        }
    }

    override fun parseCatalogJson(jsonString: String): List<CatalogItem> {
        val items = mutableListOf<CatalogItem>()
        try {
            val root = JSONObject(jsonString)
            // Soporta formatos estándar o compatibles con FPKGi ("items" o "packages" o lista directa)
            val jsonArray = when {
                root.has("items") -> root.getJSONArray("items")
                root.has("packages") -> root.getJSONArray("packages")
                root.has("data") -> root.getJSONArray("data")
                else -> JSONArray()
            }
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val item = parseItem(obj)
                if (item != null) items.add(item)
            }
        } catch (_: Exception) {
            // Intento de parsear como array directo raíz
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val item = parseItem(obj)
                    if (item != null) items.add(item)
                }
            } catch (_: Exception) {
                // Formato no reconocido
            }
        }
        return items
    }

    private fun parseItem(obj: JSONObject): CatalogItem? {
        val name = obj.optString("name").ifEmpty { obj.optString("title", "Paquete Desconocido") }
        val downloadUrl = obj.optString("downloadUrl").ifEmpty {
            obj.optString("url").ifEmpty { obj.optString("link", "") }
        }
        if (downloadUrl.isBlank()) return null

        val size = obj.optLong("sizeBytes", obj.optLong("size", 0L))
        val titleId = obj.optString("titleId", obj.optString("id", "CUSA00000"))
        val version = obj.optString("version", "1.00")
        val category = obj.optString("category", "Homebrew")
        val desc = obj.optString("description", "")
        val iconUrl = obj.optString("iconUrl", obj.optString("icon", null))
        val sha256 = obj.optString("sha256", null)
        val author = obj.optString("author", null)

        return CatalogItem(
            id = obj.optString("id", UUID.randomUUID().toString()),
            titleId = titleId,
            name = name,
            description = desc,
            category = category,
            version = version,
            sizeBytes = size,
            downloadUrl = downloadUrl,
            iconUrl = iconUrl,
            sha256 = sha256,
            author = author
        )
    }

    companion object {
        fun getDemoCatalog(): List<CatalogItem> {
            return listOf(
                CatalogItem(
                    id = "demo-item-1",
                    titleId = "CUSA99991",
                    name = "GoldHEN Cheat Manager",
                    description = "Gestor de trucos oficial para PS4 GoldHEN en red local.",
                    category = "Tool",
                    version = "1.5.0",
                    sizeBytes = 25_000_000L, // 25 MB
                    downloadUrl = "https://github.com/GoldHEN/GoldHEN_Cheat_Manager/releases/download/v1.5.0/GoldHEN_Cheat_Manager.pkg",
                    author = "GoldHEN Team"
                ),
                CatalogItem(
                    id = "demo-item-2",
                    titleId = "CUSA99992",
                    name = "ItemzFlow Game Manager",
                    description = "Homebrew Game Launcher y gestor de copias de seguridad PS4.",
                    category = "Homebrew",
                    version = "1.08",
                    sizeBytes = 65_000_000L, // 65 MB
                    downloadUrl = "https://github.com/LightningMods/Itemzflow/releases/download/v1.08/Itemzflow.pkg",
                    author = "LightningMods"
                ),
                CatalogItem(
                    id = "demo-item-3",
                    titleId = "CUSA99993",
                    name = "Apollo Save Tool PS4",
                    description = "Herramienta para gestionar, descargar y aplicar trucos a partidas guardadas.",
                    category = "Tool",
                    version = "1.4.0",
                    sizeBytes = 32_000_000L,
                    downloadUrl = "https://github.com/bucanero/apollo-ps4/releases/download/v1.4.0/apollo-ps4.pkg",
                    author = "Bucanero"
                )
            )
        }
    }
}
