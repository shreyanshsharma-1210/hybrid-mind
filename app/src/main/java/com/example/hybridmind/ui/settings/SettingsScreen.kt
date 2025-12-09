package com.example.hybridmind.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.hybridmind.data.ChatRepository
import com.example.hybridmind.data.DownloadProgress
import com.example.hybridmind.data.DownloadStatus
import com.example.hybridmind.data.ModelDownloader
import com.example.hybridmind.ui.download.ModelOptionCard
import com.example.hybridmind.ui.download.getAvailableRAM
import com.example.hybridmind.ui.download.formatBytes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    chatRepository: ChatRepository,
    modelDownloader: ModelDownloader,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onModelSwitched: () -> Unit // Callback when model is changed to re-init app or just clear state
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // Model Selection State
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
    
    // Delete Dialog State
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val availableRamGB = getAvailableRAM(context)
    val canUseAdvanced = availableRamGB >= 8

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section: AI Intelligence
            Text(
                text = "Model Selection",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Standard Model
                val isStandardDownloaded = modelDownloader.isModelDownloaded("gemma-2b")
                ModelOptionCard(
                    title = "Standard (Gemma 2B)" + if(isStandardDownloaded) " - Ready" else "",
                    description = "Fast, efficient. ~2GB.",
                    enabled = downloadProgress == null,
                    selected = selectedModel == "gemma-2b",
                    onClick = { selectedModel = "gemma-2b" }
                )

                // Advanced Model
                val isAdvancedDownloaded = modelDownloader.isModelDownloaded("gemma-4b")
                ModelOptionCard(
                    title = "Advanced (Gemma 4B)" + if(isAdvancedDownloaded) " - Ready" else "",
                    description = if (canUseAdvanced) "Higher reasoning. ~4GB" else "Higher reasoning. ~4GB (May be slow on < 8GB RAM)",
                    enabled = downloadProgress == null, // Always enabled
                    selected = selectedModel == "gemma-4b",
                    onClick = { selectedModel = "gemma-4b" }
                )

                if (downloadProgress != null) {
                   LinearProgressIndicator(
                       progress = downloadProgress!!.progress / 100f,
                       modifier = Modifier.fillMaxWidth()
                   )
                   Text("${downloadProgress!!.progress}%")
                } 
                
                Button(
                    onClick = {
                        selectedModel?.let { model ->
                            scope.launch {
                                // If downloaded, switch immediately
                                if (modelDownloader.isModelDownloaded(model)) {
                                    try {
                                        val modelPath = modelDownloader.getModelPath(model)
                                        chatRepository.initializeOfflineModel(modelPath)
                                        
                                        // Check/Init Vision
                                        if (modelDownloader.isModelDownloaded("efficientnet_lite0", "tflite")) {
                                            val visionPath = modelDownloader.getModelPath("efficientnet_lite0", "tflite")
                                            chatRepository.initializeOfflineVision(visionPath)
                                            onModelSwitched()
                                        } else {
                                            // Vision missing, download it quickly
                                            val visionUrl = "https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/float32/1/efficientnet_lite0.tflite"
                                            modelDownloader.downloadModel(visionUrl, "efficientnet_lite0", "tflite").collect { visionProgress ->
                                                downloadProgress = visionProgress
                                                if (visionProgress.status == DownloadStatus.COMPLETED) {
                                                     val visionPath = modelDownloader.getModelPath("efficientnet_lite0", "tflite")
                                                     chatRepository.initializeOfflineVision(visionPath)
                                                     onModelSwitched()
                                                     downloadProgress = null
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // Handle error
                                    }
                                } else {
                                    // Valid URLs from DownloadScreen
                                     val url = when (model) {
                                        "gemma-2b" -> "https://huggingface.co/Ph03nix1210/HybridMind-Assets/resolve/main/gemma-2b-it-cpu-int4.bin"
                                        "gemma-4b" -> "https://huggingface.co/Ph03nix1210/HybridMind-Assets/resolve/main/gemma-3n-E4B-it-int4.bin"
                                        else -> return@launch
                                    }
                                    modelDownloader.downloadModel(url, model).collect { progress ->
                                        downloadProgress = progress
                                        if (progress.status == DownloadStatus.COMPLETED) {
                                            // Auto-download Vision Model
                                             val visionUrl = "https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/float32/1/efficientnet_lite0.tflite"
                                             modelDownloader.downloadModel(visionUrl, "efficientnet_lite0", "tflite").collect { visionProgress ->
                                                if (visionProgress.status == DownloadStatus.COMPLETED) {
                                                    val modelPath = modelDownloader.getModelPath(model)
                                                    val visionPath = modelDownloader.getModelPath("efficientnet_lite0", "tflite")
                                                    
                                                    chatRepository.initializeOfflineModel(modelPath)
                                                    chatRepository.initializeOfflineVision(visionPath)
                                                    
                                                    onModelSwitched()
                                                    downloadProgress = null
                                                }
                                             }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = selectedModel != null && downloadProgress == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val label = if (selectedModel != null && modelDownloader.isModelDownloaded(selectedModel!!)) {
                        "Switch to Selected Model"
                    } else {
                        "Download & Switch"
                    }
                    Text(label)
                }
            }

            HorizontalDivider()

            // Section: Data
            Text(
                text = "Data Management",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete All Chats")
            }

            HorizontalDivider()

            // Section: Account
            Text(
                text = "Account",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                   containerColor = MaterialTheme.colorScheme.errorContainer,
                   contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign Out")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All Chats?") },
            text = { Text("This action cannot be undone. All your message history will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isDeleting = true
                            chatRepository.deleteAllUserChats()
                            isDeleting = false
                            showDeleteDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
