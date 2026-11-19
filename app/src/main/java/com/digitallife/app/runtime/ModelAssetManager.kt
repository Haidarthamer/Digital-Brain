package com.digitallife.app.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ModelAssetManager(private val context: Context) {

    companion object {
        const val DEFAULT_MODEL_FILENAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val ASSETS_MODEL_SUBDIR = "models"
    }

    fun getTargetModelFile(fileName: String = DEFAULT_MODEL_FILENAME): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    suspend fun locateOrInstallBundledModel(
        fileName: String = DEFAULT_MODEL_FILENAME,
        onProgress: (String, Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = getTargetModelFile(fileName)

        // 1. Already installed in internal storage
        if (targetFile.exists() && targetFile.length() > 1024 * 1024) {
            onProgress("Verified local model in app storage (${targetFile.length() / (1024 * 1024)} MB)", 0.5f)
            return@withContext Result.success(targetFile)
        }

        // 2. Check bundled APK assets/models/<fileName>
        val assetPath = "$ASSETS_MODEL_SUBDIR/$fileName"
        val assetList = try {
            context.assets.list(ASSETS_MODEL_SUBDIR)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (assetList.contains(fileName)) {
            onProgress("Extracting bundled model from APK assets...", 0.25f)
            return@withContext try {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                }
                onProgress("Bundled model extracted (${targetFile.length() / (1024 * 1024)} MB)", 0.55f)
                Result.success(targetFile)
            } catch (e: Exception) {
                Result.failure(RuntimeException("Failed to extract bundled model: ${e.message}", e))
            }
        }

        // 3. Not found in internal storage or APK assets
        Result.failure(
            IllegalStateException(
                "Bundled GGUF model '$fileName' not found in APK assets/models/ or app storage (${targetFile.absolutePath}). " +
                    "Place the quantized GGUF model in app/src/main/assets/models/ before building the APK, or import it in Local AI Settings."
            )
        )
    }

    fun computeSha256Prefix(file: File): String {
        if (!file.exists()) return "N/A"
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            var total = 0L
            // Hash first 8 MB for fast startup integrity check
            while (fis.read(buf).also { read = it } != -1 && total < 8 * 1024 * 1024) {
                digest.update(buf, 0, read)
                total += read
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    }
}
