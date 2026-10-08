package com.goldstore.core.network

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.goldstore.core.model.DownloadStatus
import com.goldstore.core.model.DownloadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DefaultDownloadEngine(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DownloadEngine {

    private val tasksMap = ConcurrentHashMap<String, DownloadTask>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _tasksFlow = MutableStateFlow<List<DownloadTask>>(emptyList())

    override fun observeTasks(): Flow<List<DownloadTask>> = _tasksFlow.asStateFlow()

    override suspend fun enqueueDownload(
        url: String,
        destinationFolderUriString: String,
        fileName: String,
        title: String,
        catalogItemId: String?,
        expectedSha256: String?
    ): String {
        val taskId = UUID.randomUUID().toString()
        val task = DownloadTask(
            id = taskId,
            catalogItemId = catalogItemId,
            title = title,
            url = url,
            destinationUriString = destinationFolderUriString,
            fileName = fileName,
            totalBytes = 0L,
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED,
            expectedSha256 = expectedSha256
        )
        tasksMap[taskId] = task
        notifyTasksChanged()

        startDownloadJob(taskId)
        return taskId
    }

    override suspend fun pauseDownload(taskId: String) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        val current = tasksMap[taskId] ?: return
        tasksMap[taskId] = current.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0)
        notifyTasksChanged()
    }

    override suspend fun resumeDownload(taskId: String) {
        val current = tasksMap[taskId] ?: return
        if (current.status == DownloadStatus.PAUSED || current.status == DownloadStatus.FAILED) {
            tasksMap[taskId] = current.copy(status = DownloadStatus.QUEUED, errorMessage = null)
            notifyTasksChanged()
            startDownloadJob(taskId)
        }
    }

    override suspend fun cancelDownload(taskId: String) {
        val job = activeJobs.remove(taskId)
        job?.cancel()
        val current = tasksMap[taskId] ?: return
        tasksMap[taskId] = current.copy(status = DownloadStatus.CANCELLED, speedBytesPerSec = 0)
        notifyTasksChanged()
    }

    override suspend fun getTask(taskId: String): DownloadTask? = tasksMap[taskId]

    private fun startDownloadJob(taskId: String) {
        val job = scope.launch(ioDispatcher) {
            executeDownload(taskId)
        }
        activeJobs[taskId] = job
    }

    private suspend fun executeDownload(taskId: String) {
        var task = tasksMap[taskId] ?: return
        tasksMap[taskId] = task.copy(status = DownloadStatus.DOWNLOADING)
        notifyTasksChanged()

        val tempFileName = "${task.fileName}.part"
        var outputStream: OutputStream? = null
        var inputStream: InputStream? = null

        try {
            val folderUri = Uri.parse(task.destinationUriString)
            val folderDoc = DocumentFile.fromTreeUri(context, folderUri)
                ?: throw IllegalStateException("Carpeta de destino inaccesible")

            // Buscar si ya existe archivo temporal para resumir Range
            var tempDoc = folderDoc.findFile(tempFileName)
            var startByte = 0L
            if (tempDoc != null && tempDoc.exists()) {
                startByte = tempDoc.length()
            }

            val requestBuilder = Request.Builder().url(task.url)
            if (startByte > 0) {
                requestBuilder.header("Range", "bytes=$startByte-")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                if (startByte > 0 && response.code == 416) {
                    // El rango ya está completo o inválido, reiniciar
                    startByte = 0L
                } else {
                    throw IllegalStateException("Servidor HTTP respondió con código ${response.code}")
                }
            }

            val body = response.body ?: throw IllegalStateException("Cuerpo de respuesta vacío")
            val contentLength = body.contentLength()
            val total = if (response.code == 206) startByte + contentLength else contentLength

            if (tempDoc == null) {
                tempDoc = folderDoc.createFile("application/octet-stream", tempFileName)
                    ?: throw IllegalStateException("No se pudo crear archivo temporal en almacenamiento SAF")
            }

            val mode = if (startByte > 0 && response.code == 206) "wa" else "w"
            outputStream = context.contentResolver.openOutputStream(tempDoc.uri, mode)
                ?: throw IllegalStateException("No se pudo abrir OutputStream para ${tempDoc.uri}")

            inputStream = body.byteStream()
            val buffer = ByteArray(64 * 1024) // Buffer de 64 KB en streaming
            var read: Int
            var downloaded = if (response.code == 206) startByte else 0L

            var lastSpeedUpdate = System.currentTimeMillis()
            var bytesSinceSpeedUpdate = 0L

            val digest = if (task.expectedSha256 != null && startByte == 0L) MessageDigest.getInstance("SHA-256") else null

            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                downloaded += read
                bytesSinceSpeedUpdate += read
                digest?.update(buffer, 0, read)

                val now = System.currentTimeMillis()
                val elapsed = now - lastSpeedUpdate
                var currentSpeed = task.speedBytesPerSec
                if (elapsed >= 1000) {
                    currentSpeed = (bytesSinceSpeedUpdate * 1000) / elapsed
                    bytesSinceSpeedUpdate = 0
                    lastSpeedUpdate = now

                    task = task.copy(
                        downloadedBytes = downloaded,
                        totalBytes = if (total > 0) total else downloaded,
                        speedBytesPerSec = currentSpeed
                    )
                    tasksMap[taskId] = task
                    notifyTasksChanged()
                }
            }

            outputStream.flush()

            // Validar SHA-256 si está disponible
            if (digest != null && task.expectedSha256 != null) {
                val hexString = digest.digest().joinToString("") { "%02x".format(it) }
                if (!hexString.equals(task.expectedSha256, ignoreCase = true)) {
                    throw IllegalStateException("Checksum inválido: se esperaba ${task.expectedSha256} pero se obtuvo $hexString")
                }
            }

            // Renombrar de .part a .pkg final
            val existingFinal = folderDoc.findFile(task.fileName)
            existingFinal?.delete() // Si existía un reemplazo previo, limpiar
            tempDoc.renameTo(task.fileName)

            tasksMap[taskId] = task.copy(
                downloadedBytes = downloaded,
                totalBytes = downloaded,
                speedBytesPerSec = 0,
                status = DownloadStatus.COMPLETED
            )
            notifyTasksChanged()

        } catch (e: Exception) {
            if (e is CancellationException) {
                // Cancelado o pausado cooperativamente
                return
            }
            val current = tasksMap[taskId]
            if (current != null) {
                tasksMap[taskId] = current.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "Fallo desconocido durante la descarga",
                    speedBytesPerSec = 0
                )
                notifyTasksChanged()
            }
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            activeJobs.remove(taskId)
        }
    }

    private fun notifyTasksChanged() {
        _tasksFlow.value = tasksMap.values.toList()
    }
}
