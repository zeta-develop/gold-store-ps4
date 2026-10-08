package com.goldstore.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAndCatalogTest {

    @Test
    fun parseCatalogJson_parsesDemoAndCustomPackagesCorrectly() {
        val provider = FpkgiCatalogProvider()
        val demoItems = FpkgiCatalogProvider.getDemoCatalog()

        assertEquals(3, demoItems.size)
        assertEquals("GoldHEN Cheat Manager", demoItems[0].name)
        assertEquals("CUSA99991", demoItems[0].titleId)
        assertTrue(demoItems[0].sizeBytes > 0)

        val customJson = """
            {
                "packages": [
                    {
                        "title": "Custom Homebrew",
                        "id": "CUSA12345",
                        "size": 1200000000,
                        "url": "https://example.com/test.pkg",
                        "category": "Homebrew"
                    }
                ]
            }
        """.trimIndent()

        val parsed = provider.parseCatalogJson(customJson)
        assertEquals(1, parsed.size)
        assertEquals("Custom Homebrew", parsed[0].name)
        assertEquals("CUSA12345", parsed[0].titleId)
        assertEquals(1200000000L, parsed[0].sizeBytes)
    }

    @Test
    fun downloadTask_computesProgressPercentAccurately() {
        val task = com.goldstore.core.model.DownloadTask(
            id = "t1",
            catalogItemId = null,
            title = "Test PKG",
            url = "https://example.com/pkg.pkg",
            destinationUriString = "content://saf/games",
            fileName = "pkg.pkg",
            totalBytes = 2_000_000_000L,
            downloadedBytes = 1_000_000_000L
        )

        assertEquals(50, task.progressPercent)
    }
}
