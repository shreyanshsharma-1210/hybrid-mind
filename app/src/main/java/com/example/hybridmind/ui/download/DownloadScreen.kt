package com.example.hybridmind.ui.download

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.hybridmind.data.DownloadProgress
import com.example.hybridmind.data.DownloadStatus
import com.example.hybridmind.data.ModelDownloader
import kotlinx.coroutines.launch

@Composable
fun DownloadScreen(
    modelDownloader: ModelDownloader,
    onDownloadComplete: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    
    val availableRamGB = getAvailableRAM(context)
    val canUseAdvanced = availableRamGB >= 8

    LaunchedEffect(Unit) {
        // Check if model was previously downloaded
        if (modelDownloader.isModelDownloaded("gemma-2b")) {
            onDownloadComplete(modelDownloader.getModelPath("gemma-2b"))
            return@LaunchedEffect
        }
        
        if (modelDownloader.isModelDownloaded("gemma-4b")) {
            onDownloadComplete(modelDownloader.getModelPath("gemma-4b"))
            return@LaunchedEffect
        }
        
        // No model found - show download screen
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Download Intelligence",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "Select an AI model to download for offline use",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Standard Model
                ModelOptionCard(
                    title = "Standard (Gemma 2B Int4)",
                    description = "Recommended for most devices. ~2GB",
                    enabled = downloadProgress == null,
                    selected = selectedModel == "gemma-2b",
                    onClick = { selectedModel = "gemma-2b" }
                )

                // Advanced Model
                ModelOptionCard(
                    title = "Advanced (Gemma 4B Int4)",
                    description = if (canUseAdvanced) "Better quality. ~4GB" else "Better quality. ~4GB (May be slow on < 8GB RAM)",
                    enabled = downloadProgress == null, // Always enabled if not downloading
                    selected = selectedModel == "gemma-4b",
                    onClick = { selectedModel = "gemma-4b" }
                )

                // Progress indicator
                if (downloadProgress != null) {
                    when (downloadProgress!!.status) {
                        DownloadStatus.DOWNLOADING -> {
                            LinearProgressIndicator(
                                progress = downloadProgress!!.progress / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${downloadProgress!!.progress}% - ${formatBytes(downloadProgress!!.downloadedBytes)} / ${formatBytes(downloadProgress!!.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        DownloadStatus.COMPLETED -> {
                            Text(
                                text = "Download completed! Initializing...",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        DownloadStatus.FAILED -> {
                            Text(
                                text = "Download failed. Please try again.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }
                }

                Button(
                    onClick = {
                        selectedModel?.let { model ->
                            scope.launch {
                                // Model URLs hosted on Hugging Face
                                val url = when (model) {
                                    "gemma-2b" -> "https://huggingface.co/Ph03nix1210/HybridMind-Assets/resolve/main/gemma-2b-it-cpu-int4.bin"
                                    "gemma-4b" -> "https://huggingface.co/Ph03nix1210/HybridMind-Assets/resolve/main/gemma-3n-E4B-it-int4.bin"
                                    else -> return@launch
                                }
                                
                                modelDownloader.downloadModel(url, model).collect { progress ->
                                    downloadProgress = progress
                                    if (progress.status == DownloadStatus.COMPLETED) {
                                        // Auto-download Vision Model (Silent or chained)
                                        // We won't block the UI for this small file (4MB), but we should try to get it.
                                        val visionUrl = "https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/float32/1/efficientnet_lite0.tflite"
                                        modelDownloader.downloadModel(visionUrl, "efficientnet_lite0", "tflite").collect { visionProgress ->
                                            // Optional: Update UI to say "Finalizing..."
                                            if (visionProgress.status == DownloadStatus.COMPLETED) {
                                                onDownloadComplete(modelDownloader.getModelPath(model))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedModel != null && downloadProgress == null
                ) {
                    Text("Download")
                }

                Text(
                    text = "Device RAM: ${availableRamGB}GB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelOptionCard(
    title: String,
    description: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        enabled = enabled
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
    }
}

fun getAvailableRAM(context: Context): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    return (memoryInfo.totalMem / (1024 * 1024 * 1024)).toInt()
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
