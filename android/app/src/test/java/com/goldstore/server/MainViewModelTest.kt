package com.goldstore.server

import com.goldstore.server.presentation.main.MainViewModel
import com.goldstore.server.presentation.main.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainViewModelTest {

    @Test
    fun initialState_serverIsStoppedByDefault() {
        val viewModel = MainViewModel()
        val state = viewModel.uiState.value

        assertEquals(ServerStatus.STOPPED, state.serverStatus)
        assertEquals(8080, state.serverPort)
        assertNull(state.localIpAddress)
        assertEquals(0, state.totalPackagesIndexed)
        assertEquals(0, state.activeConnections)
    }
}
