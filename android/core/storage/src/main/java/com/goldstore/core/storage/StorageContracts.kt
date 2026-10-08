package com.goldstore.core.storage

import com.goldstore.core.model.DiscoveredPackage
import com.goldstore.core.model.StorageSource
import kotlinx.coroutines.flow.Flow

/**
 * Contrato para gestión y persistencia de fuentes de almacenamiento SAF.
 */
interface StorageSourceRepository {
    fun getStorageSources(): Flow<List<StorageSource>>
    suspend fun getStorageSource(id: String): StorageSource?
    suspend fun addStorageSource(source: StorageSource)
    suspend fun removeStorageSource(id: String)
    suspend fun updateStorageSource(source: StorageSource)
    suspend fun verifyPermissions(source: StorageSource): Boolean
}

/**
 * Progreso durante el escaneo de paquetes.
 */
sealed interface ScanProgress {
    data class InProgress(val currentFolderName: String, val scannedCount: Int) : ScanProgress
    data class Completed(val totalFound: Int, val failedCount: Int) : ScanProgress
    data class Failed(val reason: String) : ScanProgress
}

/**
 * Contrato para el escáner de paquetes PKG dentro de árboles SAF.
 */
interface PackageScanner {
    fun scanTree(
        source: StorageSource,
        recursive: Boolean = true
    ): Flow<ScanProgress>

    suspend fun listDiscoveredPackages(sourceId: String): List<DiscoveredPackage>
}

/**
 * Contrato futuro para el catálogo de paquetes e indexación.
 */
interface PackageLibraryCatalog {
    fun observeAllPackages(): Flow<List<DiscoveredPackage>>
    suspend fun getPackageById(id: String): DiscoveredPackage?
}
