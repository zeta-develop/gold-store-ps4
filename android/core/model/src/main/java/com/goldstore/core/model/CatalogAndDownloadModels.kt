package com.goldstore.core.model

/**
 * Representa un elemento en el catálogo de paquetes disponibles para descargar o explorar.
 */
data class CatalogItem(
    val id: String,
    val titleId: String,
    val name: String,
    val description: String,
    val category: String, // ej. "Homebrew", "Tool", "Game", "Update", "DLC"
    val version: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val iconUrl: String? = null,
    val sha256: String? = null,
    val author: String? = null,
    val region: String? = null,
    val minFw: String? = null,
    val releaseDate: String? = null
)

/**
 * Representa una fuente/proveedor de catálogo (JSON remoto o local).
 */
data class CatalogFeed(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val itemCount: Int = 0
)

/**
 * Estado del ciclo de vida de una descarga.
 */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Tarea de descarga en progreso o persistida.
 */
data class DownloadTask(
    val id: String,
    val catalogItemId: String?,
    val title: String,
    val url: String,
    val destinationUriString: String,
    val fileName: String,
    val totalBytes: Long,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val speedBytesPerSec: Long = 0L,
    val expectedSha256: String? = null
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}
