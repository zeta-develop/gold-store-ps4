package com.goldstore.server.presentation.main

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldstore.core.model.CatalogItem
import com.goldstore.core.model.DiscoveredPackage
import com.goldstore.core.model.StorageSource
import com.goldstore.core.network.CatalogProvider
import com.goldstore.core.network.DefaultDownloadEngine
import com.goldstore.core.network.DownloadEngine
import com.goldstore.core.network.FpkgiCatalogProvider
import com.goldstore.core.network.GoldStoreHttpServer
import com.goldstore.core.storage.DataStoreStorageSourceRepository
import com.goldstore.core.storage.DocumentFilePackageScanner
import com.goldstore.core.storage.MemoryPackageLibraryCatalog
import com.goldstore.core.storage.PackageLibraryCatalog
import com.goldstore.core.storage.PackageScanner
import com.goldstore.core.storage.SafUriHelper
import com.goldstore.core.storage.ScanProgress
import com.goldstore.core.storage.StorageSourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

private val Context.dataStore by preferencesDataStore(name = "gold_store_settings")

/**
 * ViewModel que coordina el estado general de Gold Store:
 * - Servidor local HTTP embebido (Ktor CIO)
 * - Persistencia y verificación de permisos SAF
 * - Escaneo reactivo de paquetes PKG
 * - Catálogo FPKGi / Homebrew y descargas mediante HTTP Range
 */
class MainViewModel(
    application: Application? = null,
    private val storageRepository: StorageSourceRepository,
    private val packageScanner: PackageScanner,
    private val libraryCatalog: PackageLibraryCatalog,
    private val catalogProvider: CatalogProvider,
    private val downloadEngine: DownloadEngine,
    private val httpServer: GoldStoreHttpServer? = null,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope? = null
) : androidx.lifecycle.ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope
        get() = coroutineScope ?: viewModelScope

    // Constructor secundario para compatibilidad con Default ViewModelProvider / pruebas
    constructor(application: Application) : this(
        application = application,
        storageRepository = DataStoreStorageSourceRepository(
            dataStore = application.dataStore,
            contentResolver = application.contentResolver
        ),
        libraryCatalog = MemoryPackageLibraryCatalog(),
        packageScanner = DocumentFilePackageScanner(
            context = application,
            catalog = MemoryPackageLibraryCatalog()
        ),
        catalogProvider = FpkgiCatalogProvider(),
        downloadEngine = DefaultDownloadEngine(context = application),
        httpServer = GoldStoreHttpServer(
            context = application,
            catalog = MemoryPackageLibraryCatalog()
        )
    )

    // Constructor secundario para pruebas unitarias JVM puras (sin llamadas al framework Android)
    constructor(isTestMock: Boolean = true) : this(
        application = null,
        storageRepository = object : StorageSourceRepository {
            override fun getStorageSources() = kotlinx.coroutines.flow.flowOf(emptyList<StorageSource>())
            override suspend fun getStorageSource(id: String) = null
            override suspend fun addStorageSource(source: StorageSource) {}
            override suspend fun removeStorageSource(id: String) {}
            override suspend fun updateStorageSource(source: StorageSource) {}
            override suspend fun verifyPermissions(source: StorageSource) = true
        },
        packageScanner = object : PackageScanner {
            override fun scanTree(source: StorageSource, recursive: Boolean) = kotlinx.coroutines.flow.flowOf(ScanProgress.Completed(0, 0))
            override suspend fun listDiscoveredPackages(sourceId: String) = emptyList<DiscoveredPackage>()
        },
        libraryCatalog = MemoryPackageLibraryCatalog(),
        catalogProvider = FpkgiCatalogProvider(),
        downloadEngine = object : DownloadEngine {
            override fun observeTasks() = kotlinx.coroutines.flow.flowOf(emptyList<com.goldstore.core.model.DownloadTask>())
            override suspend fun enqueueDownload(url: String, destinationFolderUriString: String, fileName: String, title: String, catalogItemId: String?, expectedSha256: String?) = ""
            override suspend fun pauseDownload(taskId: String) {}
            override suspend fun resumeDownload(taskId: String) {}
            override suspend fun cancelDownload(taskId: String) {}
            override suspend fun getTask(taskId: String) = null
        },
        httpServer = null,
        coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined)
    )

    constructor() : this(isTestMock = true)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Observar fuentes SAF persistidas
        scope.launch {
            storageRepository.getStorageSources().collect { sources ->
                _uiState.update { current ->
                    current.copy(
                        storageSources = sources,
                        selectedStorageSourceId = current.selectedStorageSourceId ?: sources.firstOrNull()?.id
                    )
                }
            }
        }

        // Observar paquetes indexados en la biblioteca
        scope.launch {
            libraryCatalog.observeAllPackages().collect { pkgs ->
                _uiState.update { current ->
                    current.copy(
                        libraryPackages = pkgs,
                        totalPackagesIndexed = pkgs.size
                    )
                }
            }
        }

        // Observar tareas de descarga
        scope.launch {
            downloadEngine.observeTasks().collect { tasks ->
                _uiState.update { it.copy(activeDownloads = tasks) }
            }
        }

        // Cargar catálogo inicial de demostración (Homebrew autorizado)
        loadInitialCatalog()

        // Determinar dirección IP local inicial
        refreshLocalIp()
    }

    private fun loadInitialCatalog() {
        scope.launch {
            val demoItems = FpkgiCatalogProvider.getDemoCatalog()
            _uiState.update { it.copy(catalogItems = demoItems) }
        }
    }

    /**
     * Importa un catálogo remoto FPKGi desde una URL HTTPS validada.
     */
    fun importCatalogFromUrl(url: String) {
        scope.launch {
            try {
                if (catalogProvider is FpkgiCatalogProvider) {
                    val imported = catalogProvider.fetchFromUrl(url)
                    _uiState.update { current ->
                        val combined = (current.catalogItems + imported).distinctBy { it.downloadUrl }
                        current.copy(
                            catalogItems = combined,
                            scanStatusMessage = "Se importaron ${imported.size} paquetes compatibles con FPKGi"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(scanStatusMessage = "Error al importar catálogo: ${e.message}")
                }
            }
        }
    }

    fun onTabSelected(tab: NavigationTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSelectStorageSource(sourceId: String) {
        _uiState.update { it.copy(selectedStorageSourceId = sourceId) }
    }

    fun onToggleServer() {
        val currentState = _uiState.value.serverStatus
        if (currentState == ServerStatus.RUNNING) {
            stopServer()
        } else {
            startServer()
        }
    }

    fun startServer() {
        scope.launch {
            _uiState.update { it.copy(serverStatus = ServerStatus.STARTING, serverErrorMessage = null) }
            try {
                refreshLocalIp()
                val server = httpServer
                if (server != null) {
                    server.start()
                    _uiState.update {
                        it.copy(
                            serverStatus = ServerStatus.RUNNING,
                            serverPort = server.port
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            serverStatus = ServerStatus.RUNNING,
                            serverPort = 8080
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        serverStatus = ServerStatus.ERROR,
                        serverErrorMessage = e.message ?: "Error al arrancar el servidor HTTP"
                    )
                }
            }
        }
    }

    fun stopServer() {
        scope.launch {
            try {
                httpServer?.stop()
            } catch (_: Exception) {}
            _uiState.update { it.copy(serverStatus = ServerStatus.STOPPED) }
        }
    }

    fun addStorageSource(treeUri: Uri, displayName: String) {
        scope.launch {
            val source = SafUriHelper.createStorageSource(treeUri, displayName)
            storageRepository.addStorageSource(source)
            // Disparar escaneo automático de la nueva fuente agregada
            scanStorageSource(source)
        }
    }

    fun removeStorageSource(sourceId: String) {
        scope.launch {
            storageRepository.removeStorageSource(sourceId)
            if (libraryCatalog is MemoryPackageLibraryCatalog) {
                libraryCatalog.removeSource(sourceId)
            }
            if (_uiState.value.selectedStorageSourceId == sourceId) {
                _uiState.update { it.copy(selectedStorageSourceId = null) }
            }
        }
    }

    fun scanStorageSource(source: StorageSource) {
        scope.launch {
            _uiState.update {
                it.copy(
                    isScanningStorage = true,
                    scanStatusMessage = "Iniciando escaneo de ${source.displayName}..."
                )
            }

            packageScanner.scanTree(source, recursive = true).collect { progress ->
                when (progress) {
                    is ScanProgress.InProgress -> {
                        _uiState.update {
                            it.copy(
                                scanStatusMessage = "Explorando: ${progress.currentFolderName} (${progress.scannedCount} encontrados)"
                            )
                        }
                    }
                    is ScanProgress.Completed -> {
                        val updatedSource = source.copy(lastScannedEpochMs = System.currentTimeMillis())
                        storageRepository.updateStorageSource(updatedSource)
                        _uiState.update {
                            it.copy(
                                isScanningStorage = false,
                                scanStatusMessage = "Completado: ${progress.totalFound} paquetes PKG detectados"
                            )
                        }
                    }
                    is ScanProgress.Failed -> {
                        _uiState.update {
                            it.copy(
                                isScanningStorage = false,
                                scanStatusMessage = "Fallo en el escaneo: ${progress.reason}"
                            )
                        }
                    }
                }
            }
        }
    }

    fun startDownload(item: CatalogItem) {
        val selectedSourceId = _uiState.value.selectedStorageSourceId
        val destinationSource = _uiState.value.storageSources.firstOrNull { it.id == selectedSourceId }
            ?: _uiState.value.storageSources.firstOrNull()

        if (destinationSource == null) {
            _uiState.update {
                it.copy(
                    scanStatusMessage = "Selecciona o añade una carpeta en Almacenamiento antes de descargar"
                )
            }
            return
        }

        scope.launch {
            val safeFileName = "${item.name.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")}.pkg"
            downloadEngine.enqueueDownload(
                url = item.downloadUrl,
                destinationFolderUriString = destinationSource.uriString,
                fileName = safeFileName,
                title = item.name,
                catalogItemId = item.id,
                expectedSha256 = item.sha256
            )
            // Cambiar automáticamente a la pestaña de descargas
            _uiState.update { it.copy(currentTab = NavigationTab.DOWNLOADS) }
        }
    }

    fun pauseDownload(taskId: String) {
        scope.launch { downloadEngine.pauseDownload(taskId) }
    }

    fun resumeDownload(taskId: String) {
        scope.launch { downloadEngine.resumeDownload(taskId) }
    }

    fun cancelDownload(taskId: String) {
        scope.launch { downloadEngine.cancelDownload(taskId) }
    }

    fun refreshLocalIp() {
        scope.launch {
            val ip = withContext(Dispatchers.IO) {
                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    var detected: String? = null
                    while (interfaces.hasMoreElements()) {
                        val iface = interfaces.nextElement()
                        if (iface.isLoopback || !iface.isUp) continue
                        val addresses = iface.inetAddresses
                        while (addresses.hasMoreElements()) {
                            val addr = addresses.nextElement()
                            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                                detected = addr.hostAddress
                                break
                            }
                        }
                        if (detected != null) break
                    }
                    detected
                } catch (_: Exception) {
                    null
                }
            }
            _uiState.update { it.copy(localIpAddress = ip) }
        }
    }
}
