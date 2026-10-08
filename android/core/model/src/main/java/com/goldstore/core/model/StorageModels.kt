package com.goldstore.core.model

/**
 * Tipo estimado de unidad de almacenamiento.
 */
enum class StorageType {
    INTERNAL,
    PRIMARY_EXTERNAL,
    SD_CARD,
    USB_OTG,
    UNKNOWN
}

/**
 * Representa una fuente/carpeta de almacenamiento seleccionada por el usuario
 * a través de Storage Access Framework (SAF).
 */
data class StorageSource(
    val id: String,
    val uriString: String,
    val displayName: String,
    val storageType: StorageType = StorageType.UNKNOWN,
    val isAccessible: Boolean = true,
    val lastScannedEpochMs: Long? = null
)

/**
 * Representa un archivo de paquete PKG descubierto en el almacenamiento.
 * Utiliza [sizeBytes] como Long para soportar archivos de más de 4 GB.
 */
data class DiscoveredPackage(
    val id: String,
    val sourceId: String,
    val uriString: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModifiedEpochMs: Long,
    val isAvailable: Boolean = true
)
