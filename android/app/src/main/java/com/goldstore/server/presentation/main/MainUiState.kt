package com.goldstore.server.presentation.main

import com.goldstore.core.model.CatalogItem
import com.goldstore.core.model.DiscoveredPackage
import com.goldstore.core.model.DownloadTask
import com.goldstore.core.model.StorageSource

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

enum class NavigationTab(val label: String) {
    HOME("Inicio"),
    STORE("Tienda"),
    DOWNLOADS("Descargas"),
    LIBRARY("Biblioteca"),
    STORAGE("Almacenamiento"),
    SETTINGS("Ajustes")
}

data class MainUiState(
    val currentTab: NavigationTab = NavigationTab.HOME,
    val serverStatus: ServerStatus = ServerStatus.STOPPED,
    val serverPort: Int = 8080,
    val localIpAddress: String? = null,
    val serverErrorMessage: String? = null,
    val storageSources: List<StorageSource> = emptyList(),
    val isScanningStorage: Boolean = false,
    val scanStatusMessage: String? = null,
    val libraryPackages: List<DiscoveredPackage> = emptyList(),
    val catalogItems: List<CatalogItem> = emptyList(),
    val searchQuery: String = "",
    val activeDownloads: List<DownloadTask> = emptyList(),
    val selectedStorageSourceId: String? = null,
    val totalPackagesIndexed: Int = 0,
    val activeConnections: Int = 0
)
