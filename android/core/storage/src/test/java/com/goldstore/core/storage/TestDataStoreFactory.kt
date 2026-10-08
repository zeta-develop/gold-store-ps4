package com.goldstore.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import java.io.File

object TestDataStoreFactory {
    fun createInMemory(
        scope: CoroutineScope,
        name: String = "test_prefs"
    ): DataStore<Preferences> {
        val tempFile = File.createTempFile(name, ".preferences_pb").apply {
            deleteOnExit()
        }
        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFile }
        )
    }
}
