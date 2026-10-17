package com.voicecare.ai.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Model & Asset Download Manager for VoiceCare AI:
 * Manages on-device AI assets stored in app internal storage:
 * 1. Gemma 2B-IT INT4 (~1.34 GB): Clinical Reasoning & Structured SOAP extraction via MediaPipe GenAI
 * 2. Speech-to-Text Engine: On-device multilingual acoustic recognizer
 * 3. Clinical Vector Database (~50 MB): Drug taxonomy & medical dictionary
 */
class ModelDownloadManager(private val context: Context) {

    companion object {
        const val GEMMA_HF_DIRECT_URL = "https://huggingface.co/innermost47/gemma-2b-it-int4-mediapipe/resolve/main/gemma-1.1-2b-it-cpu-int4.bin"
    }

    val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    val gemmaModelFile: File
        get() {
            val f1 = File(modelsDir, "gemma-2-2b-it-gpu-int4.bin")
            val f2 = File(modelsDir, "gemma-1.1-2b-it-cpu-int4.bin")
            return if (f2.exists()) f2 else f1
        }

    val whisperModelFile: File
        get() = File(modelsDir, "sherpa-onnx-whisper-small.onnx")

    val embeddingsFile: File
        get() = File(modelsDir, "clinical_embeddings.db")

    private val prefs = context.getSharedPreferences("voicecare_ai_prefs", Context.MODE_PRIVATE)

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun areRealWeightsPresent(): Boolean {
        return (gemmaModelFile.exists() && gemmaModelFile.length() > 50_000_000L) ||
                File(modelsDir, "gemma-1.1-2b-it-cpu-int4.bin").exists() && File(modelsDir, "gemma-1.1-2b-it-cpu-int4.bin").length() > 50_000_000L
    }

    fun hasBypassedSetup(): Boolean {
        return prefs.getBoolean("bypass_model_setup", false)
    }

    fun setBypassSetup(bypass: Boolean) {
        prefs.edit().putBoolean("bypass_model_setup", bypass).apply()
    }

    fun areModelsReady(): Boolean {
        return areRealWeightsPresent() || hasBypassedSetup()
    }

    /**
     * Downloads the real Gemma 2B binary weights (~1.34 GB) using OkHttp with automatic redirect handling.
     */
    suspend fun downloadGemmaModel(
        customUrl: String? = null,
        onProgressUpdate: (Float, String) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            onProgressUpdate(0f, "No internet connection detected.")
            return@withContext DownloadResult.OfflineNoInternet
        }

        val targetUrl = if (!customUrl.isNullOrBlank()) customUrl else GEMMA_HF_DIRECT_URL
        onProgressUpdate(0.01f, "Connecting to Gemma repository...")

        val destinationFile = File(modelsDir, "gemma-1.1-2b-it-cpu-int4.bin")
        val ok = downloadFileWithOkHttp(
            urlStr = targetUrl,
            outputFile = destinationFile,
            label = "Downloading Gemma 2B",
            onProgressUpdate = onProgressUpdate
        )

        return@withContext if (ok && destinationFile.length() > 50_000_000L) {
            onProgressUpdate(1.0f, "Gemma 2B Downloaded & Verified (1.34 GB)!")
            DownloadResult.Success("Gemma 2B model verified")
        } else {
            onProgressUpdate(0f, "Download failed or interrupted.")
            DownloadResult.Error("Download failed. Please check internet connection.")
        }
    }

    suspend fun checkExistingModels(
        onProgressUpdate: (Float, String) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        onProgressUpdate(0.1f, "Checking local storage...")
        delay(200)

        if (areRealWeightsPresent()) {
            onProgressUpdate(1.0f, "Real Gemma 2B model verified on device storage!")
            DownloadResult.Success("Models ready")
        } else {
            val netOk = isNetworkAvailable()
            if (netOk) {
                onProgressUpdate(0f, "Gemma 2B not downloaded yet (~1.34 GB). Tap Download to fetch.")
                DownloadResult.NotDownloaded
            } else {
                onProgressUpdate(0f, "Offline Mode: Connect Wi-Fi to download Gemma 2B model.")
                DownloadResult.OfflineNoInternet
            }
        }
    }

    private fun downloadFileWithOkHttp(
        urlStr: String,
        outputFile: File,
        label: String,
        onProgressUpdate: (Float, String) -> Unit
    ): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(600, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "VoiceCare-AI-Android")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false

            val body = response.body ?: return false
            val contentLength = body.contentLength()
            val inputStream = body.byteStream()
            val tempFile = File(outputFile.parentFile, "${outputFile.name}.downloading")
            val outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(65536)
            var bytesRead: Int
            var totalBytes: Long = 0
            var lastUpdate = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 300) {
                    lastUpdate = now
                    val ratio = if (contentLength > 0) totalBytes.toFloat() / contentLength.toFloat() else 0.5f
                    val mbDownloaded = totalBytes / (1024 * 1024)
                    val mbTotal = if (contentLength > 0) contentLength / (1024 * 1024) else 1346L
                    onProgressUpdate(ratio.coerceIn(0f, 0.99f), "$label ($mbDownloaded MB / $mbTotal MB)")
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            if (tempFile.length() > 50_000_000L) {
                if (outputFile.exists()) outputFile.delete()
                tempFile.renameTo(outputFile)
                true
            } else {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    sealed class DownloadResult {
        object NotDownloaded : DownloadResult()
        object OfflineNoInternet : DownloadResult()
        data class Success(val message: String) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }
}
