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
import kotlinx.coroutines.tasks.await

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
    
    // Delete Account Dialog State
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

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
                // Standard Model - Multimodal
                val isStandardDownloaded = modelDownloader.isModelDownloaded("gemma-2b", "litertlm")
                ModelOptionCard(
                    title = "Standard (Gemma 3n E2B)" + if(isStandardDownloaded) " - Ready" else "",
                    description = "Multimodal AI with image support. ~2GB.",
                    enabled = downloadProgress == null,
                    selected = selectedModel == "gemma-2b",
                    onClick = { selectedModel = "gemma-2b" }
                )

                // Advanced Model - Also Multimodal
                val isPremiumDownloaded = modelDownloader.isModelDownloaded("gemma-4b", "litertlm")
                ModelOptionCard(
                    title = "Advanced (Gemma 3n E4B)" + if(isPremiumDownloaded) " - Ready" else "",
                    description = if (canUseAdvanced) "Multimodal, higher quality. ~4GB" else "Multimodal, higher quality. ~4GB (May be slow on < 8GB RAM)",
                    enabled = downloadProgress == null, // Always enabled
                    selected = selectedModel == "gemma-4b",
                    onClick = { selectedModel = "gemma-4b" }
                )

                if (downloadProgress != null) {
                    when (downloadProgress!!.status) {
                        DownloadStatus.DOWNLOADING -> {
                            LinearProgressIndicator(
                                progress = { downloadProgress!!.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${downloadProgress!!.progress}% - ${formatBytes(downloadProgress!!.downloadedBytes)} / ${formatBytes(downloadProgress!!.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DownloadStatus.COMPLETED -> {
                            Text(
                                text = "Download completed! Initializing...",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        DownloadStatus.FAILED -> {
                            Text(
                                text = "Download failed. Please try again.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        else -> {}
                    }
                } 
                
                Button(
                    onClick = {
                        selectedModel?.let { model ->
                            scope.launch {
                                // If downloaded
                                if (modelDownloader.isModelDownloaded(model, "litertlm")) {
                                    try {
                                        val modelPath = modelDownloader.getModelPath(model, "litertlm")
                                        chatRepository.initializeOfflineModel(modelPath)
                                        onModelSwitched()
                                    } catch (e: Exception) {
                                        // Handle error
                                    }
                                } else { 
                                    // Not downloaded
                                    val url = when (model) {
                                        "gemma-2b" -> "https://huggingface.co/Ph03nix1210/HybridMind-Assets/resolve/main/gemma-3n-E2B-it-int4.litertlm"
                                        "gemma-4b" -> "https://huggingface.co/Ph03nix1210/HybridMind-Assets/resolve/main/gemma-3n-E4B-it-int4.litertlm"
                                        else -> return@launch
                                    }
                                    
                                    modelDownloader.downloadModel(url, model, "litertlm").collect { progress ->
                                        downloadProgress = progress
                                        if (progress.status == DownloadStatus.COMPLETED) {
                                            try {
                                                val modelPath = modelDownloader.getModelPath(model, "litertlm")
                                                chatRepository.initializeOfflineModel(modelPath)
                                                onModelSwitched()
                                                downloadProgress = null
                                            } catch (e: Exception) {
                                                 downloadProgress = DownloadProgress(DownloadStatus.FAILED)
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
                    val label = if (selectedModel != null && modelDownloader.isModelDownloaded(selectedModel!!, "litertlm")) {
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
            
            OutlinedButton(
                onClick = { showDeleteAccountDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Account")
            }
        }
    }

    // Delete All Chats Dialog
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
    
    // Delete Account Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
            title = { Text("Delete Account?") },
            text = { 
                Column {
                    Text("This will permanently delete:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Your account and profile")
                    Text("• All your chat history")
                    Text("• All associated data")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This action cannot be undone!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isDeletingAccount = true
                            try {
                                // Delete all local chats first
                                chatRepository.deleteAllUserChats()
                                
                                // Delete Firebase account
                                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                                user?.delete()?.await()
                                
                                // Navigate back after deletion
                                onSignOut()
                            } catch (e: Exception) {
                                // Handle error - user might need to re-authenticate
                                android.util.Log.e("SettingsScreen", "Delete account failed", e)
                            } finally {
                                isDeletingAccount = false
                                showDeleteAccountDialog = false
                            }
                        }
                    },
                    enabled = !isDeletingAccount,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete My Account")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false },
                    enabled = !isDeletingAccount
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
