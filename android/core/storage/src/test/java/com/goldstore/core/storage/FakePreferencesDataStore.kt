package com.goldstore.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePreferencesDataStore : DataStore<Preferences> {

    private val memoryPreferences = FakePreferences(mutableMapOf())
    private val stateFlow = MutableStateFlow<Preferences>(memoryPreferences)

    override val data: Flow<Preferences> = stateFlow.asStateFlow()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(stateFlow.value)
        stateFlow.value = updated
        return updated
    }
}

class FakePreferences(
    private val values: MutableMap<Preferences.Key<*>, Any>
) : Preferences() {

    override fun <T> contains(key: Key<T>): Boolean = values.containsKey(key)

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: Key<T>): T? = values[key] as? T

    override fun asMap(): Map<Key<*>, Any> = values.toMap()

    override fun toMutablePreferences(): MutablePreferences {
        return FakeMutablePreferences(values.toMutableMap())
    }

    override fun toPreferences(): Preferences = this
}

class FakeMutablePreferences(
    private val values: MutableMap<Preferences.Key<*>, Any>
) : MutablePreferences() {

    override fun <T> contains(key: Key<T>): Boolean = values.containsKey(key)

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: Key<T>): T? = values[key] as? T

    override fun asMap(): Map<Key<*>, Any> = values.toMap()

    override fun <T> set(key: Key<T>, value: T) {
        values[key] = value as Any
    }

    override fun <T> remove(key: Key<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return values.remove(key) as? T
    }

    override fun clear() {
        values.clear()
    }

    override fun toMutablePreferences(): MutablePreferences {
        return FakeMutablePreferences(values.toMutableMap())
    }

    override fun toPreferences(): Preferences {
        return FakePreferences(values.toMutableMap())
    }
}
