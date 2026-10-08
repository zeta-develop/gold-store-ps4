package com.goldstore.core.network

import android.content.Context
import android.net.Uri
import com.goldstore.core.storage.PackageLibraryCatalog
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

class GoldStoreHttpServer(
    private val context: Context,
    private val catalog: PackageLibraryCatalog,
    private val downloadEngine: DownloadEngine? = null,
    val port: Int = 8080,
    val authToken: String = "goldstore_local_token"
) {

    private var serverEngine: ApplicationEngine? = null
    var isRunning: Boolean = false
        private set

    suspend fun start() = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        serverEngine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.Range)
            }
            configureRoutes()
        }.start(wait = false)
        isRunning = true
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        serverEngine?.stop(1000, 2000)
        serverEngine = null
        isRunning = false
    }

    private fun Application.configureRoutes() {
        routing {
            // GET /health
            get("/health") {
                val json = JSONObject().apply {
                    put("status", "UP")
                    put("server", "Gold Store PS4 Embedded Server")
                    put("version", "0.1.2")
                    put("timestamp", System.currentTimeMillis())
                }
                call.respondText(json.toString(), ContentType.Application.Json)
            }

            // GET /api/v1/packages
            get("/api/v1/packages") {
                val packages = withContext(Dispatchers.IO) {
                    var list = emptyList<com.goldstore.core.model.DiscoveredPackage>()
                    catalog.observeAllPackages().collect {
                        list = it
                        return@collect
                    }
                    list
                }
                val array = JSONArray()
                for (pkg in packages) {
                    val obj = JSONObject().apply {
                        put("id", pkg.id)
                        put("fileName", pkg.fileName)
                        put("sizeBytes", pkg.sizeBytes)
                        put("isAvailable", pkg.isAvailable)
                        put("lastModifiedEpochMs", pkg.lastModifiedEpochMs)
                        put("downloadEndpoint", "/api/v1/packages/${pkg.id}/file")
                    }
                    array.put(obj)
                }
                call.respondText(array.toString(), ContentType.Application.Json)
            }

            // GET /api/v1/packages/{id}
            get("/api/v1/packages/{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val pkg = catalog.getPackageById(id)
                if (pkg == null) {
                    call.respond(HttpStatusCode.NotFound, "Paquete no encontrado")
                    return@get
                }
                val obj = JSONObject().apply {
                    put("id", pkg.id)
                    put("fileName", pkg.fileName)
                    put("sizeBytes", pkg.sizeBytes)
                    put("isAvailable", pkg.isAvailable)
                    put("lastModifiedEpochMs", pkg.lastModifiedEpochMs)
                    put("downloadEndpoint", "/api/v1/packages/${pkg.id}/file")
                }
                call.respondText(obj.toString(), ContentType.Application.Json)
            }

            // HEAD /api/v1/packages/{id}/file
            head("/api/v1/packages/{id}/file") {
                val id = call.parameters["id"] ?: return@head call.respond(HttpStatusCode.BadRequest)
                val pkg = catalog.getPackageById(id)
                if (pkg == null || !pkg.isAvailable) {
                    call.respond(HttpStatusCode.NotFound)
                    return@head
                }
                call.response.header(HttpHeaders.ContentLength, pkg.sizeBytes.toString())
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.response.header("Content-Disposition", "attachment; filename=\"${pkg.fileName}\"")
                call.respond(HttpStatusCode.OK)
            }

            // GET /api/v1/packages/{id}/file (Soporta streaming HTTP Range y respuestas 206)
            get("/api/v1/packages/{id}/file") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val pkg = catalog.getPackageById(id)
                if (pkg == null || !pkg.isAvailable) {
                    call.respond(HttpStatusCode.NotFound, "Archivo desconectado o no encontrado")
                    return@get
                }

                val rangeHeader = call.request.header(HttpHeaders.Range)
                val totalLength = pkg.sizeBytes

                var start = 0L
                var end = totalLength - 1
                var isPartial = false

                if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
                    isPartial = true
                    val rangeValues = rangeHeader.removePrefix("bytes=").split("-")
                    val parsedStart = rangeValues.getOrNull(0)?.toLongOrNull()
                    val parsedEnd = rangeValues.getOrNull(1)?.toLongOrNull()

                    if (parsedStart != null) {
                        start = parsedStart
                    }
                    if (parsedEnd != null) {
                        end = parsedEnd
                    }

                    if (start > end || start >= totalLength) {
                        call.response.header(HttpHeaders.ContentRange, "bytes */$totalLength")
                        call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                        return@get
                    }
                }

                val contentLength = end - start + 1
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.response.header("Content-Disposition", "attachment; filename=\"${pkg.fileName}\"")

                if (isPartial) {
                    call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalLength")
                    call.response.header(HttpHeaders.ContentLength, contentLength.toString())
                    call.respondOutputStream(
                        contentType = ContentType.Application.OctetStream,
                        status = HttpStatusCode.PartialContent
                    ) {
                        streamFileBytes(pkg.uriString, start, contentLength, this)
                    }
                } else {
                    call.response.header(HttpHeaders.ContentLength, totalLength.toString())
                    call.respondOutputStream(
                        contentType = ContentType.Application.OctetStream,
                        status = HttpStatusCode.OK
                    ) {
                        streamFileBytes(pkg.uriString, 0, totalLength, this)
                    }
                }
            }

            // GET /api/v1/downloads
            get("/api/v1/downloads") {
                val array = JSONArray()
                downloadEngine?.observeTasks()?.collect { tasks ->
                    for (t in tasks) {
                        val obj = JSONObject().apply {
                            put("id", t.id)
                            put("title", t.title)
                            put("fileName", t.fileName)
                            put("status", t.status.name)
                            put("progressPercent", t.progressPercent)
                            put("downloadedBytes", t.downloadedBytes)
                            put("totalBytes", t.totalBytes)
                            put("speedBytesPerSec", t.speedBytesPerSec)
                        }
                        array.put(obj)
                    }
                    return@collect
                }
                call.respondText(array.toString(), ContentType.Application.Json)
            }

            // POST /api/v1/downloads (Requiere autenticación por token para operaciones administrativas)
            post("/api/v1/downloads") {
                val auth = call.request.header(HttpHeaders.Authorization)
                if (auth != "Bearer $authToken") {
                    call.respond(HttpStatusCode.Unauthorized, "No autorizado")
                    return@post
                }

                val body = call.receiveText()
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                if (json == null) {
                    call.respond(HttpStatusCode.BadRequest, "JSON inválido")
                    return@post
                }

                val catalogItemId = json.optString("catalogItemId")
                val item = FpkgiCatalogProvider.getDemoCatalog().firstOrNull { it.id == catalogItemId }
                if (item == null) {
                    call.respond(HttpStatusCode.Forbidden, "Solo se permiten referencias a fuentes previamente autorizadas")
                    return@post
                }

                call.respond(HttpStatusCode.Accepted, "Descarga autorizada encolada")
            }

            // GET /api/v1/downloads/{id}
            get("/api/v1/downloads/{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val task = downloadEngine?.getTask(id)
                if (task == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val obj = JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("fileName", task.fileName)
                    put("status", task.status.name)
                    put("progressPercent", task.progressPercent)
                }
                call.respondText(obj.toString(), ContentType.Application.Json)
            }
        }
    }

    private fun streamFileBytes(
        uriString: String,
        offset: Long,
        length: Long,
        out: java.io.OutputStream
    ) {
        var input: InputStream? = null
        try {
            val uri = Uri.parse(uriString)
            input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("No se puede abrir flujo para $uri")

            if (offset > 0) {
                var skipped = 0L
                while (skipped < offset) {
                    val s = input.skip(offset - skipped)
                    if (s <= 0) break
                    skipped += s
                }
            }

            val buffer = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0) {
                val toRead = if (remaining < buffer.size) remaining.toInt() else buffer.size
                val read = input.read(buffer, 0, toRead)
                if (read == -1) break
                out.write(buffer, 0, read)
                remaining -= read
            }
            out.flush()
        } finally {
            try { input?.close() } catch (_: Exception) {}
        }
    }
}
