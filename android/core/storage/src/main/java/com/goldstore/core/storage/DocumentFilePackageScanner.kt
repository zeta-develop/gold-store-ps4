package com.goldstore.core.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.goldstore.core.model.DiscoveredPackage
import com.goldstore.core.model.StorageSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.UUID

/**
 * Escáner recursivo de paquetes PKG basado en Storage Access Framework ([DocumentFile]).
 * - Ejecución reactiva mediante [Flow<ScanProgress>].
 * - No bloquea el hilo principal (`Dispatchers.IO`).
 * - Soporta cancelación cooperativa sin perder consistencia.
 * - Tolera fallos en archivos individuales sin interrumpir el escaneo global.
 * - Detecta identidades documentales para prevenir ciclos.
 */
class DocumentFilePackageScanner(
    private val context: Context,
    private val catalog: PackageLibraryCatalog? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PackageScanner {

    private val discoveredCache = mutableMapOf<String, MutableList<DiscoveredPackage>>()

    override fun scanTree(
        source: StorageSource,
        recursive: Boolean
    ): Flow<ScanProgress> = flow {
        val rootUri = Uri.parse(source.uriString)
        val rootDoc = try {
            DocumentFile.fromTreeUri(context, rootUri)
        } catch (e: Exception) {
            emit(ScanProgress.Failed("No se puede acceder al árbol SAF: ${e.message}"))
            return@flow
        }

        if (rootDoc == null || !rootDoc.canRead()) {
            emit(ScanProgress.Failed("El directorio no existe o no tiene permisos de lectura"))
            return@flow
        }

        val visitedDirs = mutableSetOf<String>()
        val dirQueue = ArrayDeque<DocumentFile>()
        dirQueue.add(rootDoc)

        val discoveredList = mutableListOf<DiscoveredPackage>()
        var failedFilesCount = 0

        while (dirQueue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            val currentDir = dirQueue.removeFirst()
            val dirUriString = currentDir.uri.toString()

            // Prevenir ciclos de carpetas
            if (!visitedDirs.add(dirUriString)) {
                continue
            }

            emit(ScanProgress.InProgress(
                currentFolderName = currentDir.name ?: "Desconocido",
                scannedCount = discoveredList.size
            ))

            val children = try {
                currentDir.listFiles()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failedFilesCount++
                emptyArray()
            }

            for (child in children) {
                currentCoroutineContext().ensureActive()

                try {
                    if (child.isDirectory && recursive) {
                        dirQueue.add(child)
                    } else if (child.isFile) {
                        val name = child.name
                        if (SafUriHelper.isPkgFile(name)) {
                            val pkg = DiscoveredPackage(
                                id = UUID.randomUUID().toString(),
                                sourceId = source.id,
                                uriString = child.uri.toString(),
                                fileName = name ?: "sin_nombre.pkg",
                                sizeBytes = child.length(), // Soporta Long (> 4 GB)
                                lastModifiedEpochMs = child.lastModified(),
                                isAvailable = true
                            )
                            discoveredList.add(pkg)
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    failedFilesCount++
                }
            }
        }

        synchronized(discoveredCache) {
            discoveredCache[source.id] = discoveredList
        }

        if (catalog is MemoryPackageLibraryCatalog) {
            catalog.updateSourcePackages(source.id, discoveredList)
        }

        emit(ScanProgress.Completed(
            totalFound = discoveredList.size,
            failedCount = failedFilesCount
        ))
    }.flowOn(ioDispatcher)

    override suspend fun listDiscoveredPackages(sourceId: String): List<DiscoveredPackage> {
        return withContext(ioDispatcher) {
            synchronized(discoveredCache) {
                discoveredCache[sourceId]?.toList() ?: emptyList()
            }
        }
    }
}
