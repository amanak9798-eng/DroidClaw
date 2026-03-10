package com.droidclaw.llm

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(
        val modelId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long = 0
    ) : DownloadState() {
        val progress: Float
            get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
    }
    data class Completed(val modelId: String, val file: File) : DownloadState()
    data class Error(val modelId: String, val message: String) : DownloadState()
}

data class DownloadedModel(
    val modelId: String,
    val file: File,
    val sizeBytes: Long
)

class ModelManager(private val modelsDir: File) {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        modelsDir.mkdirs()
    }

    fun isModelCached(modelId: String): Boolean {
        return getModelPath(modelId) != null
    }

    /**
     * Returns the model file for [modelId], checking:
     * 1. A directly-downloaded `.gguf` in modelsDir
     * 2. A `.link` file in modelsDir that points to an external absolute path
     */
    fun getModelPath(modelId: String): File? {
        val ggufFile = getModelFile(modelId)
        if (ggufFile.exists()) return ggufFile

        val linkFile = getLinkFile(modelId)
        if (linkFile.exists()) {
            val externalPath = linkFile.readText().trim()
            val externalFile = File(externalPath)
            if (externalFile.exists()) return externalFile
        }
        return null
    }

    /**
     * Returns all known models: `.gguf` files in modelsDir plus models registered via `.link` files
     * (which point to files anywhere on the device).
     */
    fun getDownloadedModels(): List<DownloadedModel> {
        val gguf = modelsDir.listFiles()
            ?.filter { it.extension.lowercase() == "gguf" }
            ?.map { file ->
                DownloadedModel(
                    modelId = file.nameWithoutExtension,
                    file = file,
                    sizeBytes = file.length()
                )
            } ?: emptyList()

        val linked = modelsDir.listFiles()
            ?.filter { it.extension.lowercase() == "link" }
            ?.mapNotNull { linkFile ->
                val externalPath = runCatching { linkFile.readText().trim() }.getOrNull()
                    ?: return@mapNotNull null
                val externalFile = File(externalPath)
                if (externalFile.exists() && externalFile.extension.lowercase() == "gguf") {
                    DownloadedModel(
                        modelId = linkFile.nameWithoutExtension,
                        file = externalFile,
                        sizeBytes = externalFile.length()
                    )
                } else null
            } ?: emptyList()

        return gguf + linked
    }

    fun deleteModel(modelId: String): Boolean {
        val file = getModelFile(modelId)
        val partFile = getPartFile(modelId)
        val linkFile = getLinkFile(modelId)
        partFile.delete()
        linkFile.delete()
        return file.delete()
    }

    /**
     * Registers a `.gguf` file selected via Android Storage Access Framework.
     *
     * Strategy:
     * 1. Try to resolve the content URI to an absolute file path on-device (no copy needed).
     *    If successful, writes a `.link` file in modelsDir so future calls to [getModelPath] work.
     * 2. If path resolution fails (e.g., cloud file, scoped storage on Android 10+),
     *    copies the bytes into modelsDir.
     *    Throws on IO failure so the caller can surface the error.
     *
     * Returns [DownloadedModel] on success, or null if the file is not a `.gguf`.
     */
    suspend fun importFromUri(contentResolver: ContentResolver, uri: Uri): DownloadedModel? =
        withContext(Dispatchers.IO) {
            val displayName = contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: return@withContext null

            if (displayName.substringAfterLast(".").lowercase() != "gguf") return@withContext null

            val baseName = displayName.substringBeforeLast(".")
            val modelId = normalizeId(baseName)

            // Step 1: resolve to a real on-device path — avoids copying large files
            val resolvedPath = resolveUriToPath(contentResolver, uri)
            if (resolvedPath != null) {
                val resolvedFile = File(resolvedPath)
                if (resolvedFile.exists() && resolvedFile.canRead()) {
                    val linkFile = getLinkFile(modelId)
                    linkFile.writeText(resolvedPath)
                    return@withContext DownloadedModel(
                        modelId = modelId,
                        file = resolvedFile,
                        sizeBytes = resolvedFile.length()
                    )
                }
            }

            // Step 2: fall back to copying bytes into modelsDir
            val targetFile = getModelFile(modelId)
            if (targetFile.exists() && targetFile.length() > 0) {
                // Already imported — skip re-copy
                return@withContext DownloadedModel(
                    modelId = modelId,
                    file = targetFile,
                    sizeBytes = targetFile.length()
                )
            }
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open file stream — check that the file still exists and you have access.")
                inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output, bufferSize = 65536)
                    }
                }
                if (targetFile.length() == 0L) {
                    targetFile.delete()
                    throw IOException("Copied file is empty — source may be inaccessible.")
                }
                DownloadedModel(modelId = modelId, file = targetFile, sizeBytes = targetFile.length())
            } catch (e: Exception) {
                targetFile.delete()
                throw e
            }
        }

    /**
     * Attempts to resolve a `content://` URI to an absolute file path without opening the stream.
     * Uses DocumentsContract on Android 10+ for more reliable path resolution.
     * Returns null when the path cannot be determined (e.g., cloud-backed file).
     */
    private fun resolveUriToPath(contentResolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path
        if (uri.scheme != "content") return null

        // Try DocumentsContract for "primary:" volume paths (most reliable on Android 10+)
        try {
            val authority = uri.authority
            if (authority == "com.android.externalstorage.documents") {
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                if (docId.startsWith("primary:")) {
                    val relativePath = docId.removePrefix("primary:")
                    val file = File(android.os.Environment.getExternalStorageDirectory(), relativePath)
                    if (file.exists()) return file.absolutePath
                }
            }
        } catch (_: Exception) {
            // Not a documents contract URI or parsing failed
        }

        // Fallback: _data column (deprecated but still works on some devices)
        return try {
            contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex("_data")
                    if (idx >= 0) cursor.getString(idx)?.takeIf { it.isNotBlank() } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getAvailableStorageBytes(): Long {
        return modelsDir.usableSpace
    }

    suspend fun downloadModel(modelId: String, url: String) {
        _downloadState.value = DownloadState.Downloading(modelId, 0, 0)

        try {
            withContext(Dispatchers.IO) {
                val partFile = getPartFile(modelId)
                val finalFile = getModelFile(modelId)
                var existingBytes = if (partFile.exists()) partFile.length() else 0L

                val requestBuilder = Request.Builder().url(url)
                if (existingBytes > 0) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                }

                val response = client.newCall(requestBuilder.build()).execute()

                if (!response.isSuccessful && response.code != 206) {
                    _downloadState.value = DownloadState.Error(modelId, "HTTP ${response.code}: ${response.message}")
                    response.close()
                    return@withContext
                }

                val body = response.body ?: run {
                    _downloadState.value = DownloadState.Error(modelId, "Empty response body")
                    response.close()
                    return@withContext
                }

                val contentLength = body.contentLength()
                val totalBytes = when {
                    contentLength == -1L -> -1L  // Unknown size; progress will be indeterminate
                    response.code == 206 -> existingBytes + contentLength
                    else -> {
                        existingBytes = 0
                        partFile.delete()
                        contentLength
                    }
                }

                val outputStream = FileOutputStream(partFile, existingBytes > 0)
                val buffer = ByteArray(8192)
                var bytesDownloaded = existingBytes
                var lastUpdateTime = System.currentTimeMillis()
                var lastUpdateBytes = bytesDownloaded

                try {
                    body.byteStream().use { input ->
                        outputStream.use { output ->
                            while (true) {
                                coroutineContext.ensureActive()

                                val bytesRead = input.read(buffer)
                                if (bytesRead == -1) break

                                output.write(buffer, 0, bytesRead)
                                bytesDownloaded += bytesRead

                                val now = System.currentTimeMillis()
                                val elapsed = now - lastUpdateTime
                                if (elapsed >= 250) {
                                    val speed = ((bytesDownloaded - lastUpdateBytes) * 1000) / elapsed
                                    _downloadState.value = DownloadState.Downloading(
                                        modelId = modelId,
                                        bytesDownloaded = bytesDownloaded,
                                        totalBytes = totalBytes,
                                        speedBytesPerSec = speed
                                    )
                                    lastUpdateTime = now
                                    lastUpdateBytes = bytesDownloaded
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    _downloadState.value = DownloadState.Idle
                    throw e
                } finally {
                    response.close()
                }

                val renamed = partFile.renameTo(finalFile)
                if (!renamed) {
                    // Fallback: copy then delete (handles cross-filesystem moves)
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }
                _downloadState.value = DownloadState.Completed(modelId, finalFile)
            }
        } catch (e: CancellationException) {
            _downloadState.value = DownloadState.Idle
            throw e
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error(modelId, e.message ?: "Unknown error")
        }
    }

    fun verifyChecksum(modelId: String, expectedSha256: String): Boolean {
        val file = getModelFile(modelId)
        if (!file.exists()) return false

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return hash.equals(expectedSha256, ignoreCase = true)
    }

    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }

    private fun getModelFile(modelId: String): File {
        return File(modelsDir, "${normalizeId(modelId)}.gguf")
    }

    private fun getPartFile(modelId: String): File {
        return File(modelsDir, "${normalizeId(modelId)}.gguf.part")
    }

    private fun getLinkFile(modelId: String): File {
        return File(modelsDir, "${normalizeId(modelId)}.link")
    }

    companion object {
        /** Converts a model ID (which may contain slashes) to a safe filename component. */
        fun normalizeId(modelId: String): String =
            modelId.replace("/", "_").replace("\\", "_")
    }
}
