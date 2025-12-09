package com.example.hybridmind.data

import android.content.Context
import android.os.Environment
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class DownloadProgress(
    val status: DownloadStatus,
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0
)

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

class ModelDownloader(private val context: Context) {

    private var wakeLock: PowerManager.WakeLock? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private val isCancelled = AtomicBoolean(false)

    fun downloadModel(modelUrl: String, modelName: String, extension: String = "bin"): Flow<DownloadProgress> = kotlinx.coroutines.flow.callbackFlow {
        trySend(DownloadProgress(DownloadStatus.IDLE))
        isCancelled.set(false)

        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$modelName.$extension")
        val marker = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$modelName.$extension.complete")
        
        // Check if already downloaded
        if (destination.exists() && marker.exists() && destination.length() > 0) {
             trySend(DownloadProgress(DownloadStatus.COMPLETED, 100, destination.length(), destination.length()))
             close()
             return@callbackFlow
        }

        acquireWakeLock()
        
        // Launch download in a separate coroutine so we can keep the flow open
        val job = launch(Dispatchers.IO) {
            try {
                // 1. Get Content Length
                val headRequest = Request.Builder().url(modelUrl).head().build()
                val response = client.newCall(headRequest).execute()
                
                val totalBytes = response.header("Content-Length")?.toLong() ?: -1L
                val acceptRanges = response.header("Accept-Ranges") == "bytes"
                response.close()

                if (totalBytes <= 0L) {
                    // Fallback or error - unknown size
                     trySend(DownloadProgress(DownloadStatus.FAILED))
                     close()
                     return@launch
                }
                
                // Delete existing partial file and marker
                if (destination.exists()) destination.delete()
                if (marker.exists()) marker.delete()
                destination.createNewFile()
                
                // Set file size
                RandomAccessFile(destination, "rw").use { it.setLength(totalBytes) }

                val downloadedBytes = AtomicLong(0)
                val chorusCount = 1 // Force single thread to prevent corruption issues
                val chunkSize = totalBytes / chorusCount

                // Monitor Job
                val monitorJob = launch {
                    while (isActive && downloadedBytes.get() < totalBytes && !isCancelled.get()) {
                        val current = downloadedBytes.get()
                        val progress = ((current * 100) / totalBytes).toInt()
                        trySend(DownloadProgress(DownloadStatus.DOWNLOADING, progress, current, totalBytes))
                        delay(200)
                    }
                }

                // Download Chunks
                try {
                    coroutineScope {
                        val deferreds = (0 until chorusCount).map { index ->
                            async(Dispatchers.IO) {
                                val start = index * chunkSize
                                val end = if (index == chorusCount - 1) totalBytes - 1 else (start + chunkSize - 1)
                                downloadChunk(modelUrl, start, end, destination, downloadedBytes)
                            }
                        }
                        deferreds.awaitAll()
                    }
                    
                    // Final success emit
                    if (!isCancelled.get()) {
                        monitorJob.cancel() // Stop monitoring
                        marker.createNewFile() // Mark as complete
                        trySend(DownloadProgress(DownloadStatus.COMPLETED, 100, totalBytes, totalBytes))
                    }
                    
                } catch (e: Exception) {
                    Log.e("ModelDownloader", "Download failed", e)
                    monitorJob.cancel()
                    trySend(DownloadProgress(DownloadStatus.FAILED))
                } finally {
                    close()
                }

            } catch (e: Exception) {
                Log.e("ModelDownloader", "Setup failed", e)
                trySend(DownloadProgress(DownloadStatus.FAILED))
                close()
            } finally {
                releaseWakeLock()
            }
        }
        
        awaitClose { 
            // Cleanup if flow cancelled
            job.cancel()
            releaseWakeLock()
        }
    }

    private suspend fun downloadChunk(
        url: String, 
        start: Long, 
        end: Long, 
        destFile: File, 
        downloadedCounter: AtomicLong
    ) {
        if (isCancelled.get()) return

        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=$start-$end")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw java.io.IOException("Unexpected code $response")

                val source = response.body?.byteStream() ?: return@use
                val raf = RandomAccessFile(destFile, "rw")
                raf.seek(start)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                try {
                    while (source.read(buffer).also { bytesRead = it } != -1) {
                         if (isCancelled.get()) break
                         raf.write(buffer, 0, bytesRead)
                         downloadedCounter.addAndGet(bytesRead.toLong())
                    }
                } finally {
                    raf.close()
                    source.close()
                }
            }
        }
    }

    fun getModelPath(modelName: String, extension: String = "bin"): String {
        return File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$modelName.$extension").absolutePath
    }

    fun isModelDownloaded(modelName: String, extension: String = "bin"): Boolean {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$modelName.$extension")
        val marker = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$modelName.$extension.complete")
        val threshold = if (extension == "tflite") 1024 * 1024 * 2 else 1024 * 1024 * 10
        return file.exists() && file.length() > threshold && marker.exists()
    }

    private fun acquireWakeLock() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HybridMind::ModelDownloadWakeLock"
        )
        wakeLock?.acquire(60 * 60 * 1000L) // 60 minutes
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    fun cancelDownload() {
        isCancelled.set(true)
        releaseWakeLock()
    }
}
