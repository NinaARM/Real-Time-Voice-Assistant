/*
 * SPDX-FileCopyrightText: Copyright 2026 Arm Limited and/or its affiliates <open-source-office@arm.com>
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.arm.voiceassistant.huggingface

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.arm.voiceassistant.utils.Constants.FAILED_TO_LIST_MODEL_FILES
import com.arm.voiceassistant.utils.Constants.HUGGING_FACE_CONNECTION_TIMEOUT
import com.arm.voiceassistant.utils.Constants.HUGGING_FACE_HEADERS_JSON
import com.arm.voiceassistant.utils.Constants.HUGGING_FACE_HOST
import com.arm.voiceassistant.utils.Constants.VOICE_ASSISTANT_TAG
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * HuggingFace API service.
 *
 * Uses REST API for query and download of models from HuggingFace host.
 */
interface RestModelsApiService {

    /**
     * List available models with specific model tag and maximum parameters
     * @param context application context
     * @param tag tag for models to list, i.e. image-text-to-text
     * @param maxParameters maximum parameters of models to list, i.e. 2 -> 2B params models
     */
    suspend fun listModels(context: Context,
                           tag: String,
                           maxParameters: Int): Result<List<HuggingFaceModel>>

    /**
     * List files (siblings) for a given HuggingFace model repo.
     * @param context application context
     * @param modelInfo model info to list available files for
     */
    suspend fun listModelFiles(
        context: Context,
        modelInfo: HuggingFaceModel,
    ): Result<List<String>>

    /**
     * Download selected HuggingFace model's GGUF file via DownloadManager
     * @param context application context
     * @param client HTTP client to use for download
     * @param modelInfo hugging face model info
     * @param downloadPath path on the device to download to
     * @param spec model download spec
     * @param listener progress listener
     */
    suspend fun downloadModelFile(
        context: Context,
        client: OkHttpClient,
        modelInfo: HuggingFaceModel,
        downloadPath: String,
        spec: ModelDownloadSpec,
        listener: ProgressListener? = null,
        onCallCreated: ((Call) -> Unit)? = null
    ): File
}

/**
 * Hugging Face model class
 */
data class HuggingFaceModel(
    val id: String,
    val modelId: String,
    val filename: String,
    val pipelineTag: String? = null
) {
    val uri: Uri
        get() = "$HUGGING_FACE_HOST${id}/${modelId}/resolve/main/$filename".toUri()
}

private val HUGGING_FACE_HEADERS = JSONObject(HUGGING_FACE_HEADERS_JSON)

private fun applyHuggingFaceHeaders(connection: HttpURLConnection) {
    val keys = HUGGING_FACE_HEADERS.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        connection.setRequestProperty(key, HUGGING_FACE_HEADERS.getString(key))
    }
}

interface ProgressListener {
    fun onProgress(downloadedBytes: Long, totalBytes: Long?)
}

sealed class DownloadProgressEvent {
    data class Start(val fileName: String) : DownloadProgressEvent()
    data class Progress(val downloadedBytes: Long, val totalBytes: Long?) : DownloadProgressEvent()
    data class Complete(val fileName: String) : DownloadProgressEvent()
    data class Error(val fileName: String, val error: Throwable) : DownloadProgressEvent()
}

/**
 * Logging progress listener
 * Adds logging on the device to show percentage downloaded
 */
class LoggingProgressListener(
    private val tag: String = VOICE_ASSISTANT_TAG,
    private val onEvent: ((DownloadProgressEvent) -> Unit)? = null
) : ProgressListener {
    private var lastLoggedPercent: Int = -10
    private var loggedUnknownTotal: Boolean = false

    fun onStart(fileName: String) {
        lastLoggedPercent = -10
        loggedUnknownTotal = false
        Log.d(tag, "Model download started: $fileName")
        onEvent?.invoke(DownloadProgressEvent.Start(fileName))
    }

    fun onComplete(fileName: String) {
        Log.d(tag, "Model download completed: $fileName")
        onEvent?.invoke(DownloadProgressEvent.Complete(fileName))
    }

    fun onError(fileName: String, error: Throwable) {
        Log.e(tag, "Model download failed: $fileName", error)
        onEvent?.invoke(DownloadProgressEvent.Error(fileName, error))
    }

    override fun onProgress(downloadedBytes: Long, totalBytes: Long?) {
        onEvent?.invoke(DownloadProgressEvent.Progress(downloadedBytes, totalBytes))
        if (totalBytes == null || totalBytes <= 0L) {
            if (!loggedUnknownTotal) {
                loggedUnknownTotal = true
                Log.d(tag, "Model download progress: $downloadedBytes bytes")
            }
            return
        }

        val pct = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
        if (pct >= lastLoggedPercent + 10) {
            val snapped = (pct / 10) * 10
            lastLoggedPercent = snapped
            Log.d(tag, "Model download progress: $downloadedBytes/$totalBytes ($snapped%)")
        }
    }
}

/**
 * Model download spec
 * Includes URL to download, filename, sha256sum and token
 */
data class ModelDownloadSpec(
    val url: String,
    val fileName: String,
    val sha256: String? = null,
    val bearerToken: String? = null
)

/*
 * HuggingFace Remote Data Source
 */
class HuggingFaceApiService : RestModelsApiService {

    override suspend fun listModels(
        context: Context,
        tag: String,
        maxParameters: Int
    ): Result<List<HuggingFaceModel>> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = Uri.parse("${HUGGING_FACE_HOST}api/models").buildUpon()
                .appendQueryParameter("pipeline_tag", tag)
                .appendQueryParameter("sort", "downloads")
                .appendQueryParameter("private", "false")
                .appendQueryParameter("apps", "llama.cpp")
                .appendQueryParameter("direction", "-1")
                .appendQueryParameter("limit", "20")

            if (maxParameters > 0) {
                urlBuilder.appendQueryParameter(
                    "num_parameters",
                    "min:0,max:${maxParameters}B"
                )
            }

            val url = URL(urlBuilder.build().toString())
            Log.i(VOICE_ASSISTANT_TAG, "HF url: $url")

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                applyHuggingFaceHeaders(this)
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                return@withContext Result.failure(
                    IllegalStateException("HuggingFace API error ($responseCode): $errorBody")
                )
            }

            val contentType = connection.contentType ?: ""
            val body = connection.inputStream.bufferedReader().use { it.readText() }

            if (!contentType.contains("application/json", ignoreCase = true)) {
                val snippet = body.take(200)
                return@withContext Result.failure(
                    IllegalStateException("Unexpected content type ($contentType): $snippet")
                )
            }

            val reader = JsonReader(StringReader(body)).apply { isLenient = true }
            val jsonArray = JsonParser.parseReader(reader).asJsonArray

            val sizeRegex = Regex("([0-7](\\.?[0-9]*)B)", RegexOption.IGNORE_CASE)
            val models = jsonArray.mapNotNull { element ->
                val obj = element.asJsonObject

                val fullModelId = when {
                    obj.has("modelId") && !obj.get("modelId").isJsonNull ->
                        obj.get("modelId").asString
                    obj.has("id") && !obj.get("id").isJsonNull ->
                        obj.get("id").asString
                    else -> return@mapNotNull null
                }
                if (!sizeRegex.containsMatchIn(fullModelId)) {
                    return@mapNotNull null
                }

                val parts = fullModelId.split("/", limit = 2)
                val owner = parts.getOrElse(0) { "" }
                val repo = parts.getOrElse(1) { fullModelId }

                HuggingFaceModel(
                    id = owner,
                    modelId = repo,
                    filename = "",
                    pipelineTag = obj.get("pipeline_tag")?.takeIf { it.isJsonPrimitive }?.asString
                )
            }

            val modelsWithGguf = models.filter { model ->
                val files = listModelFiles(context, model).getOrElse { emptyList() }
                files.any { it.endsWith(".gguf", ignoreCase = true) }
            }

            Log.i(
                VOICE_ASSISTANT_TAG,
                "HF models found: ${modelsWithGguf.size} -> ${modelsWithGguf.joinToString { "${it.id}/${it.modelId}" }}"
            )

            Result.success(modelsWithGguf)
        } catch (e: Exception) {
            Log.e(VOICE_ASSISTANT_TAG, "Failed to list models", e)
            Result.failure(e)
        }
    }

    override suspend fun listModelFiles(
        context: Context,
        modelInfo: HuggingFaceModel,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = Uri.parse("${HUGGING_FACE_HOST}api/models/${modelInfo.id}/${modelInfo.modelId}")
                .buildUpon()
            val url = URL(urlBuilder.build().toString())

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = HUGGING_FACE_CONNECTION_TIMEOUT
                readTimeout = HUGGING_FACE_CONNECTION_TIMEOUT
                applyHuggingFaceHeaders(this)
            }

            val responseCode = connection.responseCode
            if (!isHttpStatusOk(responseCode)) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                return@withContext Result.failure(
                    IllegalStateException("HuggingFace API error ($responseCode): ${errorBody?.take(200)}")
                )
            }

            val contentType = connection.contentType ?: ""
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (!contentType.contains("application/json", ignoreCase = true)) {
                val snippet = body.take(200)
                return@withContext Result.failure(
                    IllegalStateException("Unexpected content type ($contentType): $snippet")
                )
            }

            val reader = JsonReader(StringReader(body)).apply { isLenient = true }
            val jsonObj = JsonParser.parseReader(reader).asJsonObject
            val siblings = jsonObj.getAsJsonArray("siblings")
            val files = siblings?.mapNotNull { element ->
                val obj = element.asJsonObject
                val name = obj.get("rfilename")?.asString
                name?.takeIf { it.isNotBlank() }
            } ?: emptyList()

            Result.success(files)
        } catch (e: Exception) {
            Log.e(VOICE_ASSISTANT_TAG, FAILED_TO_LIST_MODEL_FILES, e)
            Result.failure(e)
        }
    }

    override suspend fun downloadModelFile(
        context: Context,
        client: OkHttpClient,
        modelInfo: HuggingFaceModel,
        downloadPath: String,
        spec: ModelDownloadSpec,
        listener: ProgressListener?,
        onCallCreated: ((Call) -> Unit)?
    ): File = withContext(Dispatchers.IO) {
        require(spec.fileName.isNotBlank()) { "Model filename is required" }
        require(spec.url.isNotBlank()) { "Model download URL is required" }

        val dir = File(downloadPath).apply { mkdirs() }
        val finalFile = File(dir, spec.fileName)
        val partFile = File(dir, "${spec.fileName}.part")

        if (finalFile.exists() && verifyIfNeeded(finalFile, spec.sha256)) {
            return@withContext finalFile
        }

        var downloaded = if (partFile.exists()) partFile.length() else 0L

        val requestBuilder = Request.Builder()
            .url(spec.url)

        if (downloaded > 0L) {
            requestBuilder.header("Range", "bytes=$downloaded-")
        }
        spec.bearerToken?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        val call = client.newCall(requestBuilder.build())
        onCallCreated?.invoke(call)
        call.execute().use { response ->
            when (response.code) {
                206, 200 -> {
                    if (response.code == 200 && downloaded > 0L) {
                        partFile.delete()
                        downloaded = 0L
                    }

                    val body = response.body ?: error("Empty response body")
                    val total = computeTotalBytes(response, downloaded)

                    partFile.parentFile?.mkdirs()
                    FileOutputStream(partFile, downloaded > 0L && response.code == 206).use { out ->
                        val input = body.byteStream()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read: Int
                        var current = downloaded

                        while (input.read(buffer).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            out.write(buffer, 0, read)
                            current += read
                            listener?.onProgress(current, total)
                        }
                        out.fd.sync()
                    }
                }
                416 -> {
                    if (!partFile.exists()) error("Range not satisfiable and no partial file present")
                }
                else -> {
                    error("HTTP ${response.code}")
                }
            }
        }

        if (!verifyIfNeeded(partFile, spec.sha256)) {
            partFile.delete()
            error("SHA-256 mismatch for ${spec.fileName}")
        }

        if (finalFile.exists()) finalFile.delete()
        if (!partFile.renameTo(finalFile)) {
            error("Failed to promote partial download to final file")
        }

        finalFile
    }

    private fun isHttpStatusOk(status: Int): Boolean {
        return (status in 200..399)
    }

    private fun computeTotalBytes(response: Response, alreadyDownloaded: Long): Long? {
        val cr = response.header("Content-Range")
        if (cr != null) {
            val total = cr.substringAfter('/').toLongOrNull()
            if (total != null) return total
        }
        val cl = response.body?.contentLength()?.takeIf { it >= 0 } ?: return null
        return if (response.code == 206) alreadyDownloaded + cl else cl
    }

    private fun verifyIfNeeded(file: File, expectedSha256: String?): Boolean {
        if (expectedSha256 == null) return file.exists() && file.length() > 0
        return sha256(file).equals(expectedSha256, ignoreCase = true)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            var n: Int
            while (input.read(buf).also { n = it } != -1) {
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
