package com.goldstore.core.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.goldstore.core.model.StorageSource
import com.goldstore.core.model.StorageType
import java.util.UUID

object SafUriHelper {

    /**
     * Toma permisos persistentes de lectura y escritura para la URI seleccionada vía SAF.
     * Retorna true si los permisos se adquirieron correctamente.
     */
    fun takePersistablePermissions(
        contentResolver: ContentResolver,
        treeUri: Uri,
        takeWrite: Boolean = false
    ): Boolean {
        return try {
            var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (takeWrite) {
                flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            contentResolver.takePersistableUriPermission(treeUri, flags)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Verifica si el ContentResolver todavía retiene permisos persistentes para la URI indicada.
     */
    fun hasPersistedPermission(
        contentResolver: ContentResolver,
        treeUri: Uri
    ): Boolean {
        return contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == treeUri && permission.isReadPermission
        }
    }

    /**
     * Infiere el tipo de almacenamiento a partir de la URI del documento SAF.
     * Maneja primary (emulated internal), microSD y USB (generalmente con ID hexadecimal o UUID).
     */
    fun estimateStorageType(treeUri: Uri): StorageType {
        val path = treeUri.path ?: return StorageType.UNKNOWN
        return when {
            path.contains("primary:", ignoreCase = true) -> StorageType.PRIMARY_EXTERNAL
            // Los identificadores típicos de microSD y USB suelen ser de formato XXXX-XXXX
            path.matches(Regex(".*[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}.*")) -> StorageType.SD_CARD
            path.contains("usb", ignoreCase = true) || path.contains("otg", ignoreCase = true) -> StorageType.USB_OTG
            else -> StorageType.UNKNOWN
        }
    }

    /**
     * Construye un modelo [StorageSource] a partir de un árbol URI de SAF.
     */
    fun createStorageSource(
        treeUri: Uri,
        displayName: String
    ): StorageSource {
        return StorageSource(
            id = UUID.randomUUID().toString(),
            uriString = treeUri.toString(),
            displayName = displayName,
            storageType = estimateStorageType(treeUri),
            isAccessible = true,
            lastScannedEpochMs = null
        )
    }

    /**
     * Comprueba si un nombre de archivo tiene extensión .pkg (insensible a mayúsculas).
     */
    fun isPkgFile(fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) return false
        return fileName.endsWith(".pkg", ignoreCase = true)
    }
}
