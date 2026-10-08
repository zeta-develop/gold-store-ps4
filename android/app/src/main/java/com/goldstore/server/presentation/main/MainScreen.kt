package com.goldstore.server.presentation.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gold Store Server") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ServerStatusCard(
                status = uiState.serverStatus,
                port = uiState.serverPort,
                ip = uiState.localIpAddress
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.onToggleServer() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when (uiState.serverStatus) {
                        ServerStatus.STOPPED -> "Iniciar Servidor"
                        ServerStatus.STARTING -> "Iniciando..."
                        ServerStatus.RUNNING -> "Detener Servidor"
                        ServerStatus.ERROR -> "Reintentar"
                    }
                )
            }
        }
    }
}

@Composable
fun ServerStatusCard(
    status: ServerStatus,
    port: Int,
    ip: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Estado del Servidor",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (status) {
                    ServerStatus.STOPPED -> "Detenido"
                    ServerStatus.STARTING -> "Iniciando"
                    ServerStatus.RUNNING -> "En ejecución"
                    ServerStatus.ERROR -> "Error"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = when (status) {
                    ServerStatus.STOPPED -> MaterialTheme.colorScheme.error
                    ServerStatus.STARTING -> MaterialTheme.colorScheme.secondary
                    ServerStatus.RUNNING -> MaterialTheme.colorScheme.primary
                    ServerStatus.ERROR -> MaterialTheme.colorScheme.error
                }
            )

            if (ip != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dirección: http://$ip:$port",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
