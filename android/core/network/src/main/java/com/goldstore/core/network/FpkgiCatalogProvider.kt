package com.goldstore.core.network

import com.goldstore.core.model.CatalogFeed
import com.goldstore.core.model.CatalogItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.UUID

/**
 * Proveedor de catálogos con soporte completo para la especificación FPKGi (ItsJokerZz/FPKGi):
 * - Estructura "DATA": { "<download_url>": { "title_id": "...", "region": "...", "name": "...", ... } }
 * - Estructura "CONTENT_URLS": { "GAMES": "https://...", "APPS": "https://...", ... }
 * - Formatos tradicionales de arrays ("items", "packages", "data" o array raíz).
 * - Protección SSRF (rechazo de esquema no HTTP/HTTPS, localhost, 127.0.0.1, etc.).
 * - Límite de tamaño de catálogo remoto (máx 20 MB).
 * - Normalización de tamaños numéricos y cadenas con sufijos (GB, MB, KB, B).
 * - Deduplicación por URL y TitleID.
 */
class FpkgiCatalogProvider(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : CatalogProvider {

    companion object {
        private const val MAX_CATALOG_RESPONSE_BYTES = 20 * 1024 * 1024L // 20 MB

        fun getDemoCatalog(): List<CatalogItem> {
            return listOf(
                CatalogItem(
                    id = "demo-item-1",
                    titleId = "CUSA99991",
                    name = "GoldHEN Cheat Manager",
                    description = "Gestor de trucos oficial para PS4 GoldHEN en red local.",
                    category = "Tool",
                    version = "1.5.0",
                    sizeBytes = 25_000_000L,
                    downloadUrl = "https://github.com/GoldHEN/GoldHEN_Cheat_Manager/releases/download/v1.5.0/GoldHEN_Cheat_Manager.pkg",
                    author = "GoldHEN Team",
                    region = "ALL",
                    minFw = "5.05"
                ),
                CatalogItem(
                    id = "demo-item-2",
                    titleId = "CUSA99992",
                    name = "ItemzFlow Game Manager",
                    description = "Homebrew Game Launcher y gestor de copias de seguridad PS4.",
                    category = "Homebrew",
                    version = "1.08",
                    sizeBytes = 65_000_000L,
                    downloadUrl = "https://github.com/LightningMods/Itemzflow/releases/download/v1.08/Itemzflow.pkg",
                    author = "LightningMods",
                    region = "ALL",
                    minFw = "5.05"
                ),
                CatalogItem(
                    id = "demo-item-3",
                    titleId = "CUSA99993",
                    name = "Apollo Save Tool PS4",
                    description = "Herramienta para gestionar, descargar y aplicar trucos a partidas guardadas.",
                    category = "Tool",
                    version = "1.4.0",
                    sizeBytes = 32_000_000L,
                    downloadUrl = "https://github.com/bucanero/apollo-ps4/releases/download/v1.4.0/apollo-ps4.pkg",
                    author = "Bucanero",
                    region = "ALL",
                    minFw = "5.05"
                )
            )
        }
    }

    override suspend fun fetchFeed(feed: CatalogFeed): List<CatalogItem> {
        return fetchFromUrl(feed.url)
    }

    suspend fun fetchFromUrl(url: String, allowLocalAddresses: Boolean = false): List<CatalogItem> {
        validateSecurityUrl(url, allowLocalAddresses)

        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Error al descargar catálogo: código HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Cuerpo de catálogo vacío")
            val contentLength = body.contentLength()
            if (contentLength > MAX_CATALOG_RESPONSE_BYTES) {
                throw IllegalStateException("El catálogo remoto supera el límite de seguridad permitido (20 MB)")
            }

            val bodyString = body.string()
            return parseCatalogJson(bodyString)
        }
    }

    override fun parseCatalogJson(jsonString: String): List<CatalogItem> {
        val uniqueItems = LinkedHashMap<String, CatalogItem>() // Deduplicación por downloadUrl

        try {
            val root = JSONObject(jsonString)

            // Caso 1: Estándar FPKGi ("DATA" como mapa { "<url>": { metadatos } })
            if (root.has("DATA")) {
                val dataObj = root.optJSONObject("DATA")
                if (dataObj != null) {
                    val keys = dataObj.keys()
                    while (keys.hasNext()) {
                        val downloadUrl = keys.next()
                        val itemObj = dataObj.optJSONObject(downloadUrl)
                        if (itemObj != null) {
                            val parsed = parseFpkgiDataItem(downloadUrl, itemObj)
                            if (parsed != null && !uniqueItems.containsKey(parsed.downloadUrl)) {
                                uniqueItems[parsed.downloadUrl] = parsed
                            }
                        }
                    }
                } else {
                    // Variante si "DATA" fuese array
                    val dataArray = root.optJSONArray("DATA")
                    if (dataArray != null) {
                        for (i in 0 until dataArray.length()) {
                            val obj = dataArray.optJSONObject(i)
                            if (obj != null) {
                                val parsed = parseStandardItem(obj)
                                if (parsed != null && !uniqueItems.containsKey(parsed.downloadUrl)) {
                                    uniqueItems[parsed.downloadUrl] = parsed
                                }
                            }
                        }
                    }
                }
            }

            // Caso 2: Formatos de array ("items", "packages", "data")
            val array = when {
                root.has("items") -> root.optJSONArray("items")
                root.has("packages") -> root.optJSONArray("packages")
                root.has("data") -> root.optJSONArray("data")
                else -> null
            }
            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i)
                    if (obj != null) {
                        val parsed = parseStandardItem(obj)
                        if (parsed != null && !uniqueItems.containsKey(parsed.downloadUrl)) {
                            uniqueItems[parsed.downloadUrl] = parsed
                        }
                    }
                }
            }

        } catch (_: Exception) {
            // Caso 3: Array JSON directo raíz `[ { ... }, { ... } ]`
            try {
                val rootArray = JSONArray(jsonString)
                for (i in 0 until rootArray.length()) {
                    val obj = rootArray.optJSONObject(i)
                    if (obj != null) {
                        val parsed = parseStandardItem(obj)
                        if (parsed != null && !uniqueItems.containsKey(parsed.downloadUrl)) {
                            uniqueItems[parsed.downloadUrl] = parsed
                        }
                    }
                }
            } catch (_: Exception) {
                // Formato no reconocido
            }
        }

        return uniqueItems.values.toList()
    }

    /**
     * Parsea un elemento del esquema "DATA" oficial de FPKGi.
     */
    private fun parseFpkgiDataItem(downloadUrl: String, obj: JSONObject): CatalogItem? {
        val sanitizedUrl = downloadUrl.trim()
        if (sanitizedUrl.isBlank() || !isValidHttpUrl(sanitizedUrl)) return null

        val name = obj.optString("name").ifEmpty {
            obj.optString("title", sanitizedUrl.substringAfterLast('/').substringBefore('?'))
        }
        val titleId = obj.optString("title_id").ifEmpty {
            obj.optString("titleId", obj.optString("id", "CUSA00000"))
        }
        val region = obj.optString("region", "ALL")
        val version = obj.optString("version", "1.00")
        val releaseDate = if (obj.has("release")) obj.optString("release") else null
        val minFw = if (obj.has("min_fw")) obj.optString("min_fw") else null
        val iconUrl = if (obj.has("cover_url")) obj.optString("cover_url") else if (obj.has("icon")) obj.optString("icon") else null
        val sizeBytes = parseSizeBytes(obj)
        val category = obj.optString("category", "Game")

        return CatalogItem(
            id = UUID.nameUUIDFromBytes(sanitizedUrl.toByteArray()).toString(),
            titleId = titleId,
            name = name,
            description = "Región: $region | Min FW: ${minFw ?: "N/A"}",
            category = category,
            version = version,
            sizeBytes = sizeBytes,
            downloadUrl = sanitizedUrl,
            iconUrl = iconUrl,
            sha256 = if (obj.has("sha256")) obj.getString("sha256") else null,
            author = if (obj.has("author")) obj.getString("author") else null,
            region = region,
            minFw = minFw,
            releaseDate = releaseDate
        )
    }

    /**
     * Parsea un elemento del esquema estándar basado en objetos.
     */
    private fun parseStandardItem(obj: JSONObject): CatalogItem? {
        val downloadUrl = obj.optString("downloadUrl").ifEmpty {
            obj.optString("url").ifEmpty { obj.optString("link", "") }
        }.trim()

        if (downloadUrl.isBlank() || !isValidHttpUrl(downloadUrl)) return null

        val name = obj.optString("name").ifEmpty { obj.optString("title", "Paquete Desconocido") }
        val titleId = obj.optString("titleId", obj.optString("title_id", obj.optString("id", "CUSA00000")))
        val version = obj.optString("version", "1.00")
        val category = obj.optString("category", "Homebrew")
        val desc = obj.optString("description", "")
        val iconUrl = if (obj.has("iconUrl")) obj.getString("iconUrl")
        else if (obj.has("cover_url")) obj.getString("cover_url")
        else if (obj.has("icon")) obj.getString("icon") else null

        val sha256 = if (obj.has("sha256")) obj.getString("sha256") else null
        val author = if (obj.has("author")) obj.getString("author") else null
        val region = if (obj.has("region")) obj.getString("region") else null
        val minFw = if (obj.has("min_fw")) obj.getString("min_fw") else null
        val release = if (obj.has("release")) obj.getString("release") else null

        val size = parseSizeBytes(obj)

        return CatalogItem(
            id = obj.optString("id", UUID.nameUUIDFromBytes(downloadUrl.toByteArray()).toString()),
            titleId = titleId,
            name = name,
            description = desc,
            category = category,
            version = version,
            sizeBytes = size,
            downloadUrl = downloadUrl,
            iconUrl = iconUrl,
            sha256 = sha256,
            author = author,
            region = region,
            minFw = minFw,
            releaseDate = release
        )
    }

    /**
     * Parsea el tamaño en bytes admitiendo tipos numéricos (Long) y cadenas con sufijos ("1.5 GB", "500 MB").
     */
    fun parseSizeBytes(obj: JSONObject): Long {
        if (obj.has("size")) {
            val raw = obj.get("size")
            if (raw is Number) return raw.toLong()
            if (raw is String) return parseFormattedSize(raw)
        }
        if (obj.has("sizeBytes")) {
            val raw = obj.get("sizeBytes")
            if (raw is Number) return raw.toLong()
            if (raw is String) return parseFormattedSize(raw)
        }
        return 0L
    }

    private fun parseFormattedSize(sizeStr: String): Long {
        val clean = sizeStr.trim().uppercase(Locale.US)
        val numericPart = clean.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: return 0L
        return when {
            clean.endsWith("GB") -> (numericPart * 1024 * 1024 * 1024).toLong()
            clean.endsWith("MB") -> (numericPart * 1024 * 1024).toLong()
            clean.endsWith("KB") -> (numericPart * 1024).toLong()
            clean.endsWith("B") -> numericPart.toLong()
            else -> numericPart.toLong()
        }
    }

    private fun isValidHttpUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase(Locale.US)
            scheme == "http" || scheme == "https"
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Validación de seguridad contra SSRF:
     * - Debe ser HTTPS o HTTP legítimo.
     * - No permite esquemas file://, content://, ftp://.
     * - No permite URLs locales como localhost, 127.0.0.1, 0.0.0.0 ni IPs de enlace local (169.254.x.x) a menos que allowLocalAddresses sea true (ej. pruebas unitarias con MockWebServer).
     */
    fun validateSecurityUrl(url: String, allowLocalAddresses: Boolean = false) {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw IllegalArgumentException("URL malformada: ${e.message}")
        }

        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "https" && scheme != "http") {
            throw SecurityException("Solo se admiten protocolos HTTP y HTTPS para catálogos")
        }

        val host = uri.host?.lowercase(Locale.US) ?: throw SecurityException("URL sin host válido")
        if (!allowLocalAddresses && (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" || host.startsWith("169.254."))) {
            throw SecurityException("Destino de catálogo no permitido por protección SSRF")
        }
    }
}
