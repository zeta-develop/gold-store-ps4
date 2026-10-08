package com.goldstore.core.storage

import com.goldstore.core.model.DiscoveredPackage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Catálogo de biblioteca en memoria / caché ligero para el incremento 0.1.2.
 * - Evita duplicados por identidad documental (`uriString`).
 * - Conserva el último catálogo válido ante desconexión de una unidad o fallos parciales de escaneo.
 * - Marca como no disponibles (`isAvailable = false`) los paquetes de fuentes inaccesibles o desconectadas.
 */
class MemoryPackageLibraryCatalog : PackageLibraryCatalog {

    private val sourcePackagesMap = mutableMapOf<String, List<DiscoveredPackage>>()
    private val _packagesFlow = MutableStateFlow<List<DiscoveredPackage>>(emptyList())

    override fun observeAllPackages(): Flow<List<DiscoveredPackage>> = _packagesFlow.asStateFlow()

    override suspend fun getPackageById(id: String): DiscoveredPackage? {
        return _packagesFlow.value.firstOrNull { it.id == id }
    }

    /**
     * Actualiza o reemplaza los paquetes válidos descubiertos para una fuente específica.
     */
    fun updateSourcePackages(sourceId: String, packages: List<DiscoveredPackage>) {
        synchronized(sourcePackagesMap) {
            sourcePackagesMap[sourceId] = packages
            publishCombinedPackages()
        }
    }

    /**
     * Marca los paquetes de una fuente como no disponibles (por ejemplo, desconexión de USB OTG o SD).
     * Mantiene los datos en el catálogo en lugar de eliminarlos.
     */
    fun setSourceAvailability(sourceId: String, isAvailable: Boolean) {
        synchronized(sourcePackagesMap) {
            val currentList = sourcePackagesMap[sourceId] ?: return
            sourcePackagesMap[sourceId] = currentList.map { it.copy(isAvailable = isAvailable) }
            publishCombinedPackages()
        }
    }

    /**
     * Retira definitivamente una fuente y sus paquetes asociados.
     */
    fun removeSource(sourceId: String) {
        synchronized(sourcePackagesMap) {
            sourcePackagesMap.remove(sourceId)
            publishCombinedPackages()
        }
    }

    private fun publishCombinedPackages() {
        val uniqueByUri = mutableMapOf<String, DiscoveredPackage>()
        for (pkgList in sourcePackagesMap.values) {
            for (pkg in pkgList) {
                // Prevenir duplicados por identidad documental URI
                if (!uniqueByUri.containsKey(pkg.uriString)) {
                    uniqueByUri[pkg.uriString] = pkg
                }
            }
        }
        _packagesFlow.value = uniqueByUri.values.toList()
    }
}
