package com.example.hybridmind.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var debugInfo by remember { mutableStateOf("Not started") }
    val context = LocalContext.current
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedImageUri = uri
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
                                
                                var imageData: ByteArray? = null
                                selectedImageUri?.let { uri ->
                                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                        imageData = inputStream.readBytes()
                                    }
                                }
                                
                                // Prevent sending if no image and no text (though button should be disabled)
                                if (msg.isBlank() && imageData == null) return@launch

                                chatRepository.sendMessage(currentSessionId!!, msg, imageData)
                                debugInfo = "Message sent, reloading..."
                                messages = chatRepository.getMessagesForSession(currentSessionId!!)
                                debugInfo = "Reloaded: ${messages.size} messages"
                                userInput = ""
                                selectedImageUri = null // Clear image after sending
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
                onRemoveImage = { selectedImageUri = null },
                errorMessage = errorMessage,
                debugInfo = debugInfo,
                modifier = Modifier.padding(padding)
            )
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
                MessageBubble(message)
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
                         // Using a simple Icon placeholder if coil isn't clearly imported, 
                         // but ideally AsyncImage. For stability without checking dependencies, using a Box with Icon.
                         // But actually, let's try to use AsyncImage or at least a text placeholder
                         // Since I don't see Coil in the imports list I reviewed (I can add it, but safer to stick to basics if not sure)
                         // Wait, I can see imports. I will use a simple Icon for reliability or just text filename if not.
                         // Better: Use a reliable placeholder
                         Surface(
                             color = MaterialTheme.colorScheme.surfaceVariant,
                             shape = MaterialTheme.shapes.medium
                         ) {
                             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                 Icon(Icons.Default.Image, contentDescription = "Selected Image", modifier = Modifier.size(48.dp))
                             }
                         }
                         
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
fun MessageBubble(message: Message) {
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
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
