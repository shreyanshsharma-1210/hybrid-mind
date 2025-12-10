package com.example.hybridmind.data

import android.content.Context
import com.example.hybridmind.core.NetworkMonitor
import com.example.hybridmind.data.cloud.FirestoreRepository
import com.example.hybridmind.data.local.AppDatabase
import com.example.hybridmind.data.local.ChatSession
import com.example.hybridmind.data.local.Message as ChatMessage  // Use alias for database Message
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message as LiteRTMessage  // Use alias for LiteRT-LM Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import java.util.UUID
import com.google.firebase.auth.FirebaseAuth

class ChatRepository(
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val database: AppDatabase,
    private val geminiApiKey: String
) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null  // Reusable conversation (replaces session)
    private val chatDao = database.chatDao()
    private val firestoreRepository = FirestoreRepository()
    private val syncScope = CoroutineScope(Dispatchers.IO)


    // Initialize LiteRT-LM Engine (call this after model download)
    suspend fun initializeOfflineModel(modelPath: String) {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d("ChatRepository", "Starting LiteRT-LM initialization for: $modelPath")
                
                // 1. Validate file exists
                val file = java.io.File(modelPath)
                if (!file.exists()) {
                    val errorMsg = "Model file not found at: $modelPath"
                    android.util.Log.e("ChatRepository", errorMsg)
                    throw Exception(errorMsg)
                }
                
                // 2. Validate file size
                val fileSizeMB = file.length() / (1024 * 1024)
                android.util.Log.d("ChatRepository", "Model file size: $fileSizeMB MB")
                
                if (file.length() < 1024 * 1024 * 100) {
                    throw Exception("Model file too small ($fileSizeMB MB). Expected at least 100MB.")
                }
                
                // 3. Configure Engine (matching Google AI Edge Gallery)
                android.util.Log.d("ChatRepository", "Creating Engine configuration...")
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU,  // GPU for main LLM
                    visionBackend = Backend.GPU,  // GPU for vision (MUST be GPU for Gemma 3n)
                    maxNumTokens = 2048,
                    cacheDir = context.getExternalFilesDir(null)?.absolutePath
                )
                
                // 4. Initialize Engine
                android.util.Log.d("ChatRepository", "Initializing Engine...")
                engine = Engine(engineConfig)
                engine?.initialize()
                android.util.Log.d("ChatRepository", "✓ Engine initialized")
                
                // 5. Create Conversation (reusable session)
                android.util.Log.d("ChatRepository", "Creating Conversation...")
                conversation = engine?.createConversation(
                    ConversationConfig(
                        samplerConfig = SamplerConfig(
                            topK = 40,
                            topP = 0.95,
                            temperature = 0.8
                        )
                    )
                )
                
                android.util.Log.d("ChatRepository", "✓ LiteRT-LM initialized successfully!")
                
            } catch (e: Exception) {
                val errorMsg = "Failed to initialize offline model: ${e.message}"
                android.util.Log.e("ChatRepository", errorMsg, e)
                e.printStackTrace()
                throw Exception(errorMsg, e)
            }
        }
    }

    private fun saveImageToInternalStorage(imageData: ByteArray): String {
        val filename = "img_${System.currentTimeMillis()}.jpg"
        val file = java.io.File(context.filesDir, filename)
        file.writeBytes(imageData)
        return file.absolutePath
    }

    suspend fun saveUserMessage(
        sessionId: String,
        userMessage: String,
        imageData: ByteArray? = null,
        saveImageToMessage: Boolean = true // Control whether to save image path to message
    ): ChatMessage {
        val timestamp = System.currentTimeMillis()
        
        // Check session existence
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: throw Exception("Not signed in")
        val session = chatDao.getAllSessions(currentUserId).find { it.id == sessionId }
            ?: throw Exception("Session not found")
        
        val isOfflineSession = session.is_offline_only

        // Save image to local storage if present AND we want to save it to the message
        val imagePath = if (imageData != null && saveImageToMessage) {
            saveImageToInternalStorage(imageData)
        } else {
            null
        }

        // Save user message to Room
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            session_id = sessionId,
            role = "user",
            content = userMessage,
            timestamp = timestamp,
            image_path = imagePath
        )
        chatDao.insertMessage(userMsg)

        // Sync user message to Firestore (if online and NOT offline-only session)
        val isOnline = networkMonitor.isOnline.first()
        if (isOnline && !isOfflineSession) {
            syncScope.launch {
                try {
                    firestoreRepository.syncMessage(sessionId, userMsg, isOfflineSession)
                } catch (e: Exception) {
                    // Silently fail - offline mode will take over
                }
            }
        }
        
        return userMsg
    }

    suspend fun generateResponse(
        sessionId: String,
        userMessage: String,
        imageData: ByteArray? = null
    ): String {
        val isOnline = networkMonitor.isOnline.first()
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return "Error: Not signed in"
        
        val session = chatDao.getAllSessions(currentUserId).find { it.id == sessionId } ?: return "Error: Session not found"
        val isOfflineSession = session.is_offline_only

        val modelResponse = withContext(Dispatchers.IO) {
            if (isOnline && !isOfflineSession) { // Only use online if session allows it
                // Online: Try Gemini first
                try {
                    generateWithGemini(sessionId, userMessage, imageData)
                } catch (e: Exception) {
                    android.util.Log.e("ChatRepository", "Gemini failed: ${e.message}")
                    "Error: Online generation failed. ${e.message}"
                }
            } else {
                // Offline: Use MediaPipe
                generateWithMediaPipe(sessionId, userMessage, imageData)
            }
        }

        // Save model response to Room
        val modelMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            session_id = sessionId,
            role = "model",
            content = modelResponse,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(modelMsg)

        // Sync model response to Firestore (if online and NOT offline-only session)
        if (isOnline && !isOfflineSession) {
            syncScope.launch {
                try {
                    firestoreRepository.syncMessage(sessionId, modelMsg, isOfflineSession)
                } catch (e: Exception) {
                    // Silently fail
                }
            }
        }

        // Update session
        val newTitle = if (session.title == "New Chat" && userMessage.isNotBlank()) {
            val words = userMessage.trim().split("\\s+".toRegex())
            if (words.size > 10) {
                words.take(10).joinToString(" ") + "..."
            } else {
                userMessage
            }
        } else {
            session.title
        }

        val updatedSession = session.copy(
            title = newTitle,
            last_updated = System.currentTimeMillis(),
            is_offline_only = session.is_offline_only || !isOnline
        )
        chatDao.updateSession(updatedSession)

        // Sync session to Firestore (if NOT offline-only)
        if (!updatedSession.is_offline_only) {
            syncScope.launch {
                try {
                    firestoreRepository.syncSession(updatedSession)
                } catch (e: Exception) {
                    // Silently fail
                }
            }
        }

        return modelResponse
    }
    
    // Deprecated but kept for compatibility if needed, calling new functions
    suspend fun sendMessage(
        sessionId: String,
        userMessage: String,
        imageData: ByteArray? = null,
        saveImageToMessage: Boolean = true
    ): String {
        saveUserMessage(sessionId, userMessage, imageData, saveImageToMessage)
        return generateResponse(sessionId, userMessage, imageData)
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun generateWithGemini(sessionId: String, prompt: String, imageData: ByteArray?): String {
        // 1. Reconstruct and Sanitize History
        val historyMessages = chatDao.getMessagesForSession(sessionId).filter { it.role != "system" }
        
        // We need to exclude the current message from history because startChat expects history 
        // to be the CONTEXT, and we send the new message via chat.sendMessage().
        // Since we saved the current message in sendMessage(), it IS in the DB.
        // Instead of blind dropLast, we filter out the very last message if it matches the 'user' role,
        // which covers the case where it's our current prompt.
        // But to be safer against ordering quirks, we'll sanitize the FULL list and then 
        // ensure the last item is NOT a user message.
        
        val validHistory = mutableListOf<com.google.ai.client.generativeai.type.Content>()
        var expectedRole = "user"
        
        // Log history for debugging
        if (historyMessages.isNotEmpty()) {
            android.util.Log.d("ChatRepository", "History (Last 3): ${historyMessages.takeLast(3).map { "${it.role}: ${it.content.take(20)}" }}")
        }

        for (msg in historyMessages) {
            val normalizedRole = msg.role.lowercase().trim()
            if (normalizedRole == expectedRole) {
                 validHistory.add(com.google.ai.client.generativeai.type.content(normalizedRole) {
                    if (msg.image_path != null) {
                        try {
                            val options = android.graphics.BitmapFactory.Options()
                            options.inSampleSize = 2 
                            val bitmap = android.graphics.BitmapFactory.decodeFile(msg.image_path, options)
                            if (bitmap != null) image(bitmap)
                        } catch (e: Exception) {
                            android.util.Log.e("ChatRepository", "Failed to load image for history: ${e.message}")
                        }
                    }
                    text(msg.content)
                })
                expectedRole = if (expectedRole == "user") "model" else "user"
            } else {
                 // Skip messages that violate turn order (e.g. double user messages) to separate them
                 android.util.Log.w("ChatRepository", "Skipping message '${msg.content.take(10)}...' ($normalizedRole) exp: $expectedRole")
            }
        }
        
        // CRITICAL: The history passed to startChat MUST END with a MODEL response (or be empty).
        // It cannot end with a USER message, because we are about to send a NEW USER message.
        // So if the valid history ends with 'user', we drop it.
        if (validHistory.isNotEmpty() && validHistory.last().role == "user") {
            android.util.Log.d("ChatRepository", "Dropping trailing 'user' message from history (likely current prompt).")
            validHistory.removeAt(validHistory.size - 1)
        }
        
        val initialHistory = validHistory
        
        
        // 2. Use Gemini 2.5 Flash (latest model)
        try {
            android.util.Log.d("ChatRepository", "Attempting generation with gemini-2.5-flash")
            
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash", 
                apiKey = geminiApiKey
            )
            
            val chat = model.startChat(initialHistory)

            val content = com.google.ai.client.generativeai.type.content("user") {
                if (imageData != null) {
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                    image(bitmap)
                }
                text(prompt)
            }

            val response = chat.sendMessage(content)
            val textResponse = response.text
            
            if (!textResponse.isNullOrBlank()) {
                return textResponse
            } else {
                throw Exception("Empty response from model")
            }

        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Gemini failed: ${e.message}")
            throw Exception("Gemini generation failed: ${e.message}")
        }
    }



    private suspend fun generateWithMediaPipe(sessionId: String, prompt: String, imageData: ByteArray? = null): String {
        val conv = conversation 
            ?: return "Offline model not initialized. Please download the model first."

        return withContext(Dispatchers.IO) {
            suspendCoroutine { continuation ->
                try {
                    // Build contents list (Google AI Edge Gallery pattern)
                    val contents = mutableListOf<Content>()
                    
                    // Add image first if present (as PNG bytes)
                    if (imageData != null) {
                        android.util.Log.d("ChatRepository", "Adding image (${imageData.size} bytes)")
                        contents.add(Content.ImageBytes(imageData))
                    }
                    
                    // Add text prompt
                    if (prompt.trim().isNotEmpty()) {
                        contents.add(Content.Text(prompt))
                    }
                    
                    val fullResponse = StringBuilder()
                    
                    // Send message with callback (streaming support)
                    conv.sendMessageAsync(
                        LiteRTMessage.of(contents),
                        object : MessageCallback {
                            override fun onMessage(message: LiteRTMessage) {
                                // Append streaming chunks
                                fullResponse.append(message.toString())
                                android.util.Log.d("ChatRepository", "Chunk received: ${message.toString().take(50)}...")
                            }
                            
                            override fun onDone() {
                                android.util.Log.d("ChatRepository", "✓ Response complete (${fullResponse.length} chars)")
                                continuation.resume(fullResponse.toString())
                            }
                            
                            override fun onError(throwable: Throwable) {
                                android.util.Log.e("ChatRepository", "Error: ${throwable.message}", throwable)
                                continuation.resumeWithException(throwable)
                            }
                        }
                    )
                } catch (e: Exception) {
                    android.util.Log.e("ChatRepository", "Error: ${e.message}", e)
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    suspend fun createNewSession(title: String, isOffline: Boolean): String {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: throw IllegalStateException("User not signed in")
        val sessionId = UUID.randomUUID().toString()
        val session = ChatSession(
            id = sessionId,
            user_id = currentUserId,
            title = title,
            is_offline_only = isOffline,
            last_updated = System.currentTimeMillis()
        )
        chatDao.insertSession(session)

        // Sync to Firestore if NOT offline-only
        if (!isOffline) {
            syncScope.launch {
                try {
                    firestoreRepository.syncSession(session)
                } catch (e: Exception) {
                    // Silently fail
                }
            }
        }

        return sessionId
    }

    suspend fun getAllSessions(): List<ChatSession> {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return emptyList()
        return withContext(Dispatchers.IO) {
            chatDao.getAllSessions(currentUserId)
        }
    }

    suspend fun getMessagesForSession(sessionId: String): List<ChatMessage> {
        return withContext(Dispatchers.IO) {
            chatDao.getMessagesForSession(sessionId)
        }
    }

    suspend fun deleteAllUserChats() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        withContext(Dispatchers.IO) {
            chatDao.deleteAllSessions(currentUserId)
        }
    }

    fun isOfflineModelReady(): Boolean {
        return engine != null && conversation != null
    }

    fun cleanup() {
        conversation = null
        engine = null
        syncScope.cancel()
    }
}
