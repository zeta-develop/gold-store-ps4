package com.goldstore.server.presentation.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldstore.core.model.CatalogItem
import com.goldstore.core.model.DiscoveredPackage
import com.goldstore.core.model.DownloadStatus
import com.goldstore.core.model.DownloadTask
import com.goldstore.core.model.StorageSource
import com.goldstore.core.model.StorageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Launcher de SAF para seleccionar directorios en memoria interna, microSD o USB OTG
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val displayName = uri.lastPathSegment?.substringAfterLast(':') ?: "Almacenamiento SAF"
            viewModel.addStorageSource(uri, displayName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Gold Store PS4", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "v0.1.2",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    val statusText = when (uiState.serverStatus) {
                        ServerStatus.RUNNING -> "ONLINE"
                        ServerStatus.STARTING -> "STARTING"
                        ServerStatus.STOPPED -> "OFFLINE"
                        ServerStatus.ERROR -> "ERROR"
                    }
                    val badgeColor = when (uiState.serverStatus) {
                        ServerStatus.RUNNING -> Color(0xFF2E7D32)
                        ServerStatus.STARTING -> Color(0xFFEF6C00)
                        ServerStatus.STOPPED -> Color(0xFFC62828)
                        ServerStatus.ERROR -> Color(0xFFC62828)
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(badgeColor)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = uiState.currentTab == tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        icon = {
                            Text(
                                text = when (tab) {
                                    NavigationTab.HOME -> "🏠"
                                    NavigationTab.STORE -> "🛒"
                                    NavigationTab.DOWNLOADS -> "⬇️"
                                    NavigationTab.LIBRARY -> "📦"
                                    NavigationTab.STORAGE -> "💾"
                                    NavigationTab.SETTINGS -> "⚙️"
                                }
                            )
                        }
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                NavigationTab.HOME -> HomeScreen(uiState = uiState, onToggleServer = { viewModel.onToggleServer() })
                NavigationTab.STORE -> StoreScreen(
                    catalogItems = uiState.catalogItems,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onDownloadClick = { viewModel.startDownload(it) }
                )
                NavigationTab.DOWNLOADS -> DownloadsScreen(
                    downloads = uiState.activeDownloads,
                    onPause = { viewModel.pauseDownload(it) },
                    onResume = { viewModel.resumeDownload(it) },
                    onCancel = { viewModel.cancelDownload(it) }
                )
                NavigationTab.LIBRARY -> LibraryScreen(
                    packages = uiState.libraryPackages,
                    serverPort = uiState.serverPort,
                    localIp = uiState.localIpAddress
                )
                NavigationTab.STORAGE -> StorageScreen(
                    sources = uiState.storageSources,
                    isScanning = uiState.isScanningStorage,
                    scanMessage = uiState.scanStatusMessage,
                    selectedSourceId = uiState.selectedStorageSourceId,
                    onSelectSource = { viewModel.onSelectStorageSource(it) },
                    onAddFolderClick = { folderPickerLauncher.launch(null) },
                    onScanSource = { viewModel.scanStorageSource(it) },
                    onRemoveSource = { viewModel.removeStorageSource(it) }
                )
                NavigationTab.SETTINGS -> SettingsScreen(
                    uiState = uiState,
                    onRefreshIp = { viewModel.refreshLocalIp() }
                )
            }
        }
    }
}

/* ============================ TAB 1: HOME ============================ */
@Composable
fun HomeScreen(
    uiState: MainUiState,
    onToggleServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Servidor Local PS4 HTTP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Permite a tu consola PS4 con GoldHEN instalar paquetes PKG directamente por red WiFi o Ethernet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.localIpAddress != null) {
                    Text(
                        text = "Endpoint Base: http://${uiState.localIpAddress}:${uiState.serverPort}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "API Catálogo: http://${uiState.localIpAddress}:${uiState.serverPort}/api/v1/packages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Conecta el teléfono a una red Wi-Fi o punto de acceso compartido",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (uiState.serverErrorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: ${uiState.serverErrorMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Paquetes Indexados",
                value = "${uiState.libraryPackages.size}",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Carpetas SAF",
                value = "${uiState.storageSources.size}",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Descargas Activas",
                value = "${uiState.activeDownloads.count { it.status == DownloadStatus.DOWNLOADING }}",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onToggleServer,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.serverStatus == ServerStatus.RUNNING)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = when (uiState.serverStatus) {
                    ServerStatus.STOPPED -> "Iniciar Servidor HTTP"
                    ServerStatus.STARTING -> "Iniciando Servidor..."
                    ServerStatus.RUNNING -> "Detener Servidor HTTP"
                    ServerStatus.ERROR -> "Reintentar Inicio"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ============================ TAB 2: TIENDA ============================ */
@Composable
fun StoreScreen(
    catalogItems: List<CatalogItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDownloadClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(catalogItems, searchQuery) {
        if (searchQuery.isBlank()) catalogItems
        else catalogItems.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.titleId.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutHeader("Catálogo Autorizado", "Paquetes homebrew y utilidades PS4 verificadas")

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Buscar por título o CUSA...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se encontraron paquetes en el catálogo.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { item ->
                    CatalogItemCard(item = item, onDownloadClick = { onDownloadClick(item) })
                }
            }
        }
    }
}

@Composable
fun CatalogItemCard(
    item: CatalogItem,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[${item.titleId}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "v${item.version}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• ${formatFileSize(item.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (item.author != null) {
                        Text(
                            text = "• Por ${item.author}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(onClick = onDownloadClick) {
                Text("Bajar")
            }
        }
    }
}

/* ============================ TAB 3: DESCARGAS ============================ */
@Composable
fun DownloadsScreen(
    downloads: List<DownloadTask>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutHeader("Gestor de Descargas", "Progreso en tiempo real con reanudación HTTP Range")
        Spacer(modifier = Modifier.height(12.dp))

        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay descargas activas ni en cola.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(downloads, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onPause = { onPause(task.id) },
                        onResume = { onResume(task.id) },
                        onCancel = { onCancel(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = task.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (task.status) {
                        DownloadStatus.COMPLETED -> Color(0xFF2E7D32)
                        DownloadStatus.DOWNLOADING -> Color(0xFF1976D2)
                        DownloadStatus.PAUSED -> Color(0xFFEF6C00)
                        DownloadStatus.FAILED -> Color(0xFFC62828)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { task.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${formatFileSize(task.downloadedBytes)} / ${formatFileSize(task.totalBytes)} (${task.progressPercent}%)",
                    style = MaterialTheme.typography.bodySmall
                )
                if (task.status == DownloadStatus.DOWNLOADING) {
                    Text(
                        text = "${formatFileSize(task.speedBytesPerSec)}/s",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (task.errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Error: ${task.errorMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (task.status == DownloadStatus.DOWNLOADING) {
                    OutlinedButton(onClick = onPause) { Text("Pausar") }
                } else if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                    OutlinedButton(onClick = onResume) { Text("Reanudar") }
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (task.status != DownloadStatus.COMPLETED) {
                    OutlinedButton(onClick = onCancel) { Text("Cancelar") }
                }
            }
        }
    }
}

/* ============================ TAB 4: BIBLIOTECA ============================ */
@Composable
fun LibraryScreen(
    packages: List<DiscoveredPackage>,
    serverPort: Int,
    localIp: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutHeader("Biblioteca de Paquetes PKG", "${packages.size} archivos encontrados en el almacenamiento")
        Spacer(modifier = Modifier.height(12.dp))

        if (packages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay archivos PKG detectados. Configura y escanea una carpeta en Almacenamiento.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(packages, key = { it.id }) { pkg ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = pkg.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (pkg.isAvailable) "DISPONIBLE" else "DESCONECTADO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pkg.isAvailable) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tamaño: ${formatFileSize(pkg.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (localIp != null) {
                                Text(
                                    text = "Endpoint: http://$localIp:$serverPort/api/v1/packages/${pkg.id}/file",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ============================ TAB 5: ALMACENAMIENTO ============================ */
@Composable
fun StorageScreen(
    sources: List<StorageSource>,
    isScanning: Boolean,
    scanMessage: String?,
    selectedSourceId: String?,
    onSelectSource: (String) -> Unit,
    onAddFolderClick: () -> Unit,
    onScanSource: (StorageSource) -> Unit,
    onRemoveSource: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutHeader("Almacenamiento SAF", "Memoria Interna, Tarjeta MicroSD y USB OTG")
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onAddFolderClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Añadir Carpeta (Storage Access Framework)")
        }

        if (scanMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = scanMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (sources.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay fuentes de almacenamiento agregadas.\nToca el botón de arriba para seleccionar una carpeta.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sources, key = { it.id }) { source ->
                    val isSelected = source.id == selectedSourceId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSource(source.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = source.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = when (source.storageType) {
                                        StorageType.INTERNAL, StorageType.PRIMARY_EXTERNAL -> "Interno"
                                        StorageType.SD_CARD -> "MicroSD"
                                        StorageType.USB_OTG -> "USB OTG"
                                        StorageType.UNKNOWN -> "Desconocido"
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (isSelected) {
                                Text(
                                    text = "★ Destino predeterminado de descargas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "URI: ${source.uriString}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val lastScanned = source.lastScannedEpochMs
                            if (lastScanned != null) {
                                val dateStr = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date(lastScanned))
                                Text(
                                    text = "Último escaneo: $dateStr",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { onScanSource(source) },
                                    enabled = !isScanning
                                ) {
                                    Text("Escanear PKGs")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(onClick = { onRemoveSource(source.id) }) {
                                    Text("Eliminar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ============================ TAB 6: AJUSTES ============================ */
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onRefreshIp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutHeader("Configuración del Sistema", "Parámetros de red y seguridad local")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Red Local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("IP Local actual: ${uiState.localIpAddress ?: "No detectada"}")
                Text("Puerto HTTP: ${uiState.serverPort}")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onRefreshIp) {
                    Text("Refrescar Dirección IP")
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Seguridad y Restricciones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("• Permisos SAF controlados: No se usa MANAGE_EXTERNAL_STORAGE.")
                Text("• Token de descargas remotas: goldstore_local_token")
                Text("• Solo contenido homebrew y fuentes autorizadas.")
            }
        }
    }
}

@Composable
fun OutHeader(title: String, subtitle: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}
