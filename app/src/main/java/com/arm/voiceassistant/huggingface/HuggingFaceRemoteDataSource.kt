/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.huggingface

import android.content.Context
import android.app.DownloadManager
import android.util.Log
import android.net.Uri
import androidx.core.net.toUri
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import com.arm.voiceassistant.utils.Constants.HUGGING_FACE_HOST
import com.arm.voiceassistant.utils.Constants.DOWNLOAD_MANAGER_DOUBLE_CHECK_DELAY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File


/*
 * HuggingFace Remote Data Source
 */
interface HuggingFaceRemoteDataSource {

    /**
     * Download selected HuggingFace model's GGUF file via DownloadManager
     */
    suspend fun downloadModelFile(
        context: Context,
        downloadInfo: HuggingFaceDownloadInfo,
        downloadPath: String,
    ): Result<Long>
}

class HuggingFaceRemoteDataSourceImpl : HuggingFaceRemoteDataSource {

    override suspend fun downloadModelFile(
        context: Context,
        downloadInfo: HuggingFaceDownloadInfo,
        downloadPath: String,
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadDir = File(downloadPath)
            if (!downloadDir.exists()) {
                val created = downloadDir.mkdirs()
                if (!created) {
                    Log.w(VOICE_ASSISTANT_TAG, "Failed to create download directory: ${downloadDir.absolutePath}")
                }
            }
            val destinationFile = File(downloadDir, downloadInfo.filename)
            Log.d(VOICE_ASSISTANT_TAG, "Downloading model to path: ${destinationFile.absolutePath}")

            val request = DownloadManager.Request(downloadInfo.uri).apply {
                setTitle(downloadInfo.filename)
                setDescription("Downloading directly from HuggingFace")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }
            Log.d(VOICE_ASSISTANT_TAG, "Enqueuing download request for: ${downloadInfo.modelId}")
            val downloadId = downloadManager.enqueue(request)

            delay(DOWNLOAD_MANAGER_DOUBLE_CHECK_DELAY)

            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))

            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex >= 0) {
                    val status = cursor.getInt(statusIndex)
                    Log.i(VOICE_ASSISTANT_TAG, "Status is $status")
                    cursor.close()

                    when (status) {
                        DownloadManager.STATUS_FAILED -> {
                            // Get failure reason if available
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                            val errorMessage = when (reason) {
                                DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP error"
                                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage"
                                DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
                                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP code"
                                DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
                                DownloadManager.ERROR_FILE_ERROR -> "File error"
                                else -> "Unknown error"
                            }
                            Result.failure(Exception(errorMessage))
                        }
                        else -> {
                            // Download is pending, paused, or running
                            Result.success(downloadId)
                        }
                    }
                } else {
                    // Assume success if we can't check status
                    cursor.close()
                    Result.success(downloadId)
                }
            } else {
                // Assume success if cursor is empty
                cursor?.close()
                Result.success(downloadId)
            }

        } catch (e: Exception) {
            Log.e(VOICE_ASSISTANT_TAG, "Failed to enqueue download: ${e.message}")
            Result.failure(e)
        }
    }
}

data class HuggingFaceDownloadInfo(
    val id: String,
    val modelId: String,
    val filename: String,
) {
    val uri: Uri
        get() = "$HUGGING_FACE_HOST${id}/${modelId}/resolve/main/$filename".toUri()
}
