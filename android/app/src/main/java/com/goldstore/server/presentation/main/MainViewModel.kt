package com.goldstore.server.presentation.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel que gestiona el estado de la pantalla principal y el servidor.
 * Por defecto en el incremento 0.1.1, el servidor se inicia en estado STOPPED.
 */
class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onToggleServer() {
        // En 0.1.1 la infraestructura del servidor no se arranca aún (previsto para 0.1.4 / 0.1.5)
        // Mantiene el estado en STOPPED
    }
}
