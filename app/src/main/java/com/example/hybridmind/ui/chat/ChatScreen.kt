package com.example.hybridmind.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.hybridmind.core.NetworkMonitor
import com.example.hybridmind.data.ChatRepository
import com.example.hybridmind.data.local.ChatSession
import com.example.hybridmind.data.local.Message
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage // Assuming Coil is available or using standard Image with Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.launch
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatRepository: ChatRepository,
    networkMonitor: NetworkMonitor,
    onSignOut: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    var sessions by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var userInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var currentSessionImageData by remember { mutableStateOf<ByteArray?>(null) } // Persistent image context
    var isFirstImageSend by remember { mutableStateOf(true) } // Track if this is first time sending current image
    var fullScreenImagePath by remember { mutableStateOf<String?>(null) } // For full-screen viewer
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var debugInfo by remember { mutableStateOf("Not started") }
    val context = LocalContext.current
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedImageUri = uri
        // Load image data for persistent context
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    currentSessionImageData = inputStream.readBytes()
                    isFirstImageSend = true // Reset flag for new image
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    val isOnline by networkMonitor.isOnline.collectAsState(initial = true)

    // Load sessions and create default if needed
    LaunchedEffect(Unit) {
        sessions = chatRepository.getAllSessions()
        if (sessions.isEmpty()) {
            val sessionId = chatRepository.createNewSession("New Chat", false)
            currentSessionId = sessionId
            sessions = chatRepository.getAllSessions()
        } else {
            currentSessionId = sessions.first().id
        }
    }

    // Load messages when session changes
    LaunchedEffect(currentSessionId) {
        currentSessionId?.let {
            messages = chatRepository.getMessagesForSession(it)
            debugInfo = "Loaded ${messages.size} messages for session $it"
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ChatDrawerContent(
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    onSessionClick = { sessionId ->
                        currentSessionId = sessionId
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        scope.launch {
                            val sessionId = chatRepository.createNewSession("New Chat", !isOnline)
                            currentSessionId = sessionId
                            sessions = chatRepository.getAllSessions()
                            drawerState.close()
                        }
                    },
                    onSignOut = onSignOut,
                    onSettingsClick = onSettingsClick
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("HybridMind")
                            Text(
                                text = if (isOnline) "Online - Gemini" else "Offline - Local",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOnline) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                }
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        ) { padding ->
            ChatContent(
                messages = messages,
                userInput = userInput,
                onUserInputChange = { userInput = it },
                isLoading = isLoading,
                onSendMessage = {
                    debugInfo = "CALLBACK TRIGGERED!"
                    if (currentSessionId != null) {
                        scope.launch {
                            try {
                                isLoading = true
                                errorMessage = null
                                debugInfo = "Sending message..."
                                val msg = if (userInput.isBlank()) {
                                    "Summarize this image and describe what is displayed"
                                } else {
                                    userInput
                                }
                                
                                // Use persistent image data if available
                                val imageData = currentSessionImageData
                                
                                // Prevent sending if no image and no text (though button should be disabled)
                                if (msg.isBlank() && imageData == null) return@launch

                                // Only save image to message on first send, but always send to AI for context
                                chatRepository.sendMessage(
                                    sessionId = currentSessionId!!,
                                    userMessage = msg,
                                    imageData = imageData,
                                    saveImageToMessage = isFirstImageSend && imageData != null
                                )
                                
                                // Mark that we've sent this image once
                                if (imageData != null && isFirstImageSend) {
                                    isFirstImageSend = false
                                }
                                
                                debugInfo = "Message sent, reloading..."
                                messages = chatRepository.getMessagesForSession(currentSessionId!!)
                                debugInfo = "Reloaded: ${messages.size} messages"
                                userInput = ""
                                selectedImageUri = null // Clear preview but keep context
                            } catch (e: Exception) {
                                e.printStackTrace()
                                errorMessage = "ERROR: ${e.javaClass.simpleName}: ${e.message}"
                                debugInfo = "ERROR: ${e.javaClass.simpleName}: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    } else {
                        debugInfo = "ERROR: No session ID!"  
                    }
                },
                onPickImage = {
                     imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                selectedImageUri = selectedImageUri,
                onRemoveImage = { 
                    selectedImageUri = null
                    currentSessionImageData = null // Clear persistent context too
                    isFirstImageSend = true // Reset for next image
                },
                onImageClick = { imagePath ->
                    fullScreenImagePath = imagePath
                },
                errorMessage = errorMessage,
                debugInfo = debugInfo,
                modifier = Modifier.padding(padding)
            )
        }
    }
    
    // Full-screen image viewer
    if (fullScreenImagePath != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { fullScreenImagePath = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullScreenImagePath,
                    contentDescription = "Full screen image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                
                // Close button
                IconButton(
                    onClick = { fullScreenImagePath = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ChatDrawerContent(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    onSessionClick: (String) -> Unit,
    onNewChat: () -> Unit,
    onSignOut: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Text(
            text = "Chat History",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat")
        }
        
        OutlinedButton(
            onClick = onSettingsClick,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
             Icon(Icons.Default.Settings, contentDescription = null)
             Spacer(modifier = Modifier.width(8.dp))
             Text("Settings")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(sessions) { session ->
                NavigationDrawerItem(
                    label = {
                        Column {
                            Text(session.title)
                            if (session.is_offline_only) {
                                Text(
                                    text = "Private (Offline)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    },
                    selected = session.id == currentSessionId,
                    onClick = { onSessionClick(session.id) }
                )
            }
        }

        Divider()

        TextButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out")
        }
    }
}

@Composable
fun ChatContent(
    messages: List<Message>,
    userInput: String,
    onUserInputChange: (String) -> Unit,
    isLoading: Boolean,
    onSendMessage: () -> Unit,
    onPickImage: () -> Unit,
    selectedImageUri: Uri?,
    onRemoveImage: () -> Unit,
    onImageClick: (String) -> Unit = {}, // For full-screen image view
    errorMessage: String? = null,
    debugInfo: String = "",
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // DEBUG INFO
        if (debugInfo.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "DEBUG: $debugInfo | Messages: ${messages.size} | Loading: $isLoading",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    onImageClick = onImageClick
                )
            }
            
            if (isLoading) {
                item {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // Input area
        Surface(
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                if (selectedImageUri != null) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .size(100.dp)
                    ) {
                         // Show actual uploaded image
                         AsyncImage(
                             model = selectedImageUri,
                             contentDescription = "Selected Image",
                             modifier = Modifier
                                 .fillMaxSize()
                                 .background(
                                     MaterialTheme.colorScheme.surfaceVariant,
                                     MaterialTheme.shapes.medium
                                 ),
                             contentScale = androidx.compose.ui.layout.ContentScale.Crop
                         )
                         
                         // Close button
                         IconButton(
                             onClick = onRemoveImage,
                             modifier = Modifier
                                 .align(Alignment.TopEnd)
                                 .offset(x = 8.dp, y = (-8).dp)
                                 .size(24.dp)
                                 .background(MaterialTheme.colorScheme.error, CircleShape)
                         ) {
                             Icon(
                                 Icons.Default.Close, 
                                 contentDescription = "Remove",
                                 tint = MaterialTheme.colorScheme.onError,
                                 modifier = Modifier.padding(4.dp)
                             )
                         }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = onUserInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        maxLines = 4
                    )
                    
                    IconButton(onClick = onPickImage) {
                         Icon(Icons.Default.Add, contentDescription = "Add Image")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onSendMessage,
                        enabled = !isLoading && (userInput.isNotBlank() || selectedImageUri != null)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    onImageClick: (String) -> Unit = {}
) {
    val isUser = message.role == "user"
    
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Display image if it exists
                message.image_path?.let { imagePath ->
                    AsyncImage(
                        model = imagePath,
                        contentDescription = "Message image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .padding(bottom = if (message.content.isNotEmpty()) 8.dp else 0.dp)
                            .clickable { onImageClick(imagePath) },
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                
                // Display text content
                if (message.content.isNotEmpty()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
