package com.goldstore.core.network

import com.goldstore.core.model.DownloadStatus
import com.goldstore.core.model.DownloadTask
import kotlinx.coroutines.flow.Flow

interface DownloadEngine {
    fun observeTasks(): Flow<List<DownloadTask>>
    suspend fun enqueueDownload(
        url: String,
        destinationFolderUriString: String,
        fileName: String,
        title: String,
        catalogItemId: String? = null,
        expectedSha256: String? = null
    ): String
    suspend fun pauseDownload(taskId: String)
    suspend fun resumeDownload(taskId: String)
    suspend fun cancelDownload(taskId: String)
    suspend fun getTask(taskId: String): DownloadTask?
}
