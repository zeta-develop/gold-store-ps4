package com.goldstore.core.storage

import com.goldstore.core.model.DiscoveredPackage
import com.goldstore.core.model.StorageSource
import com.goldstore.core.model.StorageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageAndCatalogTest {

    @Test
    fun serialization_roundTrip_preservesSourcesAndMetadata() = runTest {
        val repo = DataStoreStorageSourceRepository(
            dataStore = TestDataStoreFactory.createInMemory(this)
        )


        val sources = listOf(
            StorageSource(
                id = "src-1",
                uriString = "content://com.android.externalstorage.documents/tree/primary%3AGames",
                displayName = "Internal Games",
                storageType = StorageType.PRIMARY_EXTERNAL,
                isAccessible = true,
                lastScannedEpochMs = 1700000000000L
            ),
            StorageSource(
                id = "src-2",
                uriString = "content://com.android.externalstorage.documents/tree/1234-5678%3APKG",
                displayName = "USB Drive",
                storageType = StorageType.USB_OTG,
                isAccessible = false,
                lastScannedEpochMs = null
            )
        )

        val json = repo.serializeSources(sources)
        val deserialized = repo.deserializeSources(json)

        assertEquals(2, deserialized.size)
        assertEquals("src-1", deserialized[0].id)
        assertEquals(StorageType.PRIMARY_EXTERNAL, deserialized[0].storageType)
        assertTrue(deserialized[0].isAccessible)
        assertEquals(1700000000000L, deserialized[0].lastScannedEpochMs)

        assertEquals("src-2", deserialized[1].id)
        assertEquals(StorageType.USB_OTG, deserialized[1].storageType)
        assertFalse(deserialized[1].isAccessible)
    }

    @Test
    fun repository_addSource_preventsDuplicateUris() = runTest {
        val repo = DataStoreStorageSourceRepository(
            dataStore = TestDataStoreFactory.createInMemory(this)
        )


        val source1 = StorageSource(
            id = "id-1",
            uriString = "content://saf/tree/Games",
            displayName = "Original Name"
        )
        val source2 = StorageSource(
            id = "id-2",
            uriString = "content://saf/tree/Games",
            displayName = "Updated Name"
        )

        repo.addStorageSource(source1)
        var list = repo.getStorageSources().first()
        assertEquals(1, list.size)
        assertEquals("Original Name", list[0].displayName)

        // Al intentar agregar la misma URI, actualiza los metadatos sin duplicar
        repo.addStorageSource(source2)
        list = repo.getStorageSources().first()
        assertEquals(1, list.size)
        assertEquals("Updated Name", list[0].displayName)
    }

    @Test
    fun catalog_preservesResultsWhenDisconnected_andMarksUnavailable() = runTest {
        val catalog = MemoryPackageLibraryCatalog()

        val sourceId = "usb-otg-1"
        val pkg1 = DiscoveredPackage(
            id = "pkg-1",
            sourceId = sourceId,
            uriString = "content://saf/tree/usb/game1.pkg",
            fileName = "game1.pkg",
            sizeBytes = 10_000_000_000L, // 10 GB
            lastModifiedEpochMs = 1700000000L,
            isAvailable = true
        )
        val pkg2 = DiscoveredPackage(
            id = "pkg-2",
            sourceId = sourceId,
            uriString = "content://saf/tree/usb/game2.pkg",
            fileName = "game2.pkg",
            sizeBytes = 4_500_000_000L, // 4.5 GB (> 4 GB)
            lastModifiedEpochMs = 1700000100L,
            isAvailable = true
        )

        catalog.updateSourcePackages(sourceId, listOf(pkg1, pkg2))

        var packages = catalog.observeAllPackages().first()
        assertEquals(2, packages.size)
        assertTrue(packages.all { it.isAvailable })

        // Simular desconexión de USB OTG: no se borra el catálogo, se marca no disponible
        catalog.setSourceAvailability(sourceId, isAvailable = false)

        packages = catalog.observeAllPackages().first()
        assertEquals(2, packages.size)
        assertTrue(packages.none { it.isAvailable })

        val fetched = catalog.getPackageById("pkg-1")
        assertNotNull(fetched)
        assertFalse(fetched!!.isAvailable)
    }

    @Test
    fun catalog_preventsDuplicateUrisAcrossMultipleSources() = runTest {
        val catalog = MemoryPackageLibraryCatalog()

        val pkg1 = DiscoveredPackage(
            id = "1",
            sourceId = "src-a",
            uriString = "content://shared/game.pkg",
            fileName = "game.pkg",
            sizeBytes = 2_000_000_000L,
            lastModifiedEpochMs = 100L
        )
        val pkgDuplicateUri = DiscoveredPackage(
            id = "2",
            sourceId = "src-b",
            uriString = "content://shared/game.pkg",
            fileName = "game.pkg",
            sizeBytes = 2_000_000_000L,
            lastModifiedEpochMs = 100L
        )

        catalog.updateSourcePackages("src-a", listOf(pkg1))
        catalog.updateSourcePackages("src-b", listOf(pkgDuplicateUri))

        val packages = catalog.observeAllPackages().first()
        assertEquals(1, packages.size)
    }
}
