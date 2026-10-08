package com.goldstore.server.presentation.main

enum class ServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

data class MainUiState(
    val serverStatus: ServerStatus = ServerStatus.STOPPED,
    val serverPort: Int = 8080,
    val localIpAddress: String? = null,
    val totalPackagesIndexed: Int = 0,
    val activeConnections: Int = 0
)
