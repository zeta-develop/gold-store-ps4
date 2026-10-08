package com.goldstore.core.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NetworkAndCatalogTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun parseFpkgiOfficialDataSchema_handlesAllFieldsAndDeduplication() {
        val provider = FpkgiCatalogProvider()

        val fpkgiJson = """
            {
              "DATA": {
                "https://example.com/games/homebrew1.pkg": {
                  "title_id": "CUSA01234",
                  "region": "USA",
                  "name": "Super Homebrew Bros",
                  "version": "1.02",
                  "release": "2024-05-01",
                  "size": 4294967296,
                  "min_fw": "5.05",
                  "cover_url": "https://example.com/covers/cusa01234.png"
                },
                "https://example.com/games/homebrew2.pkg": {
                  "title_id": "CUSA05678",
                  "region": "EUR",
                  "name": "Another Homebrew Tool",
                  "version": "2.00",
                  "release": "2024-06-15",
                  "size": "1.5 GB",
                  "min_fw": "6.72",
                  "cover_url": "https://example.com/covers/cusa05678.png"
                }
              }
            }
        """.trimIndent()

        val items = provider.parseCatalogJson(fpkgiJson)

        assertEquals("Debe deduplicar URLs idénticas", 2, items.size)

        val item1 = items.first { it.titleId == "CUSA01234" }
        assertEquals("Super Homebrew Bros", item1.name)
        assertEquals("USA", item1.region)
        assertEquals("1.02", item1.version)
        assertEquals(4294967296L, item1.sizeBytes) // > 4 GB
        assertEquals("5.05", item1.minFw)
        assertEquals("2024-05-01", item1.releaseDate)
        assertEquals("https://example.com/covers/cusa01234.png", item1.iconUrl)

        val item2 = items.first { it.titleId == "CUSA05678" }
        assertEquals("Another Homebrew Tool", item2.name)
        assertEquals("EUR", item2.region)
        assertEquals((1.5 * 1024 * 1024 * 1024).toLong(), item2.sizeBytes)
        assertEquals("6.72", item2.minFw)
    }

    @Test
    fun parseSizeBytes_supportsVariousSizeRepresentations() {
        val provider = FpkgiCatalogProvider()

        val jsonGb = org.json.JSONObject("""{"size": "2.5 GB"}""")
        val jsonMb = org.json.JSONObject("""{"size": "512 MB"}""")
        val jsonKb = org.json.JSONObject("""{"size": "1024 KB"}""")
        val jsonBytes = org.json.JSONObject("""{"size": 123456789}""")

        assertEquals((2.5 * 1024 * 1024 * 1024).toLong(), provider.parseSizeBytes(jsonGb))
        assertEquals(512L * 1024 * 1024, provider.parseSizeBytes(jsonMb))
        assertEquals(1024L * 1024, provider.parseSizeBytes(jsonKb))
        assertEquals(123456789L, provider.parseSizeBytes(jsonBytes))
    }

    @Test
    fun ssrfProtection_blocksLocalhostAndInvalidSchemes() {
        val provider = FpkgiCatalogProvider()

        try {
            provider.validateSecurityUrl("http://localhost:8080/catalog.json")
            fail("Debe rechazar localhost")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("SSRF"))
        }

        try {
            provider.validateSecurityUrl("http://127.0.0.1/catalog.json")
            fail("Debe rechazar 127.0.0.1")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("SSRF"))
        }

        try {
            provider.validateSecurityUrl("file:///etc/passwd")
            fail("Debe rechazar file://")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("HTTP y HTTPS"))
        }
    }

    @Test
    fun fetchFromUrl_parsesRemoteCatalogViaMockWebServer() = runTest {
        val provider = FpkgiCatalogProvider()

        val catalogJson = """
            {
              "DATA": {
                "https://external.domain/app.pkg": {
                  "title_id": "CUSA77777",
                  "name": "Remote Homebrew App",
                  "size": 50000000,
                  "version": "1.00",
                  "region": "ALL"
                }
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(catalogJson)
        )

        val parsed = provider.fetchFromUrl(mockWebServer.url("/catalog.json").toString(), allowLocalAddresses = true)
        assertEquals(1, parsed.size)
        assertEquals("Remote Homebrew App", parsed[0].name)
        assertEquals("CUSA77777", parsed[0].titleId)
        assertEquals(50000000L, parsed[0].sizeBytes)
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
