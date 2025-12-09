package com.example.hybridmind.data

import android.content.Context
import com.example.hybridmind.core.NetworkMonitor
import com.example.hybridmind.data.cloud.FirestoreRepository
import com.example.hybridmind.data.local.AppDatabase
import com.example.hybridmind.data.local.ChatSession
import com.example.hybridmind.data.local.Message
import com.google.ai.client.generativeai.GenerativeModel
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import com.google.firebase.auth.FirebaseAuth

class ChatRepository(
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val database: AppDatabase,
    private val geminiApiKey: String
) {
    private var llmInference: LlmInference? = null
    private val chatDao = database.chatDao()
    private val firestoreRepository = FirestoreRepository()
    private val syncScope = CoroutineScope(Dispatchers.IO)
    private var imageClassifier: com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier? = null // Offline Vision Helper

    // Initialize MediaPipe LLM (call this after model download)
    suspend fun initializeOfflineModel(modelPath: String) {
        withContext(Dispatchers.IO) {
            try {
                val file = java.io.File(modelPath)
                if (!file.exists()) {
                    throw Exception("Model file not found at: $modelPath")
                }
                
                // Check for plausible size (e.g. at least 100MB for a Gemma model)
                // If it's small, it might be a partial download or error page
                if (file.length() < 1024 * 1024 * 10) { // < 10MB
                    throw Exception("Model file is too small (${file.length()} bytes). Download might be corrupted.")
                }

                android.util.Log.d("ChatRepository", "Initializing LLM from ${file.absolutePath} (Size: ${file.length() / (1024 * 1024)} MB)")

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(1024)
                    .setTemperature(0.7f)
                    .setTopK(40)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                android.util.Log.d("ChatRepository", "LLM Initialized successfully")
            } catch (e: Exception) {
                e.printStackTrace()
                throw Exception("Failed to initialize offline model: ${e.message}")
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
    ): Message {
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
        val userMsg = Message(
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
        val modelMsg = Message(
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

    // Initialize MediaPipe Vision (Image Classifier)
    suspend fun initializeOfflineVision(modelPath: String) {
        withContext(Dispatchers.IO) {
            try {
                val file = java.io.File(modelPath)
                if (file.exists()) {
                     val baseOptions = com.google.mediapipe.tasks.core.BaseOptions.builder()
                        .setModelAssetPath(modelPath)
                        .build()
                    
                    val options = com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier.ImageClassifierOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setMaxResults(5)
                        .build()
                        
                    imageClassifier = com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier.createFromOptions(context, options)
                    android.util.Log.d("ChatRepository", "Offline Vision Initialized")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun generateWithMediaPipe(sessionId: String, prompt: String, imageData: ByteArray? = null): String {
        val inference = llmInference 
            ?: return "Offline model not initialized. Please download the model first."

        var currentTurnUserContent = prompt
        
        // Offline Vision: Classify image and append context
        if (imageData != null && imageClassifier != null) {
             try {
                 val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                 if (bitmap != null) {
                     val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()
                     val result = imageClassifier!!.classify(mpImage)
                     
                     val labels = result.classificationResult().classifications().flatMap { 
                         it.categories().map { category -> category.categoryName() } 
                     }.joinToString(", ")
                     
                     if (labels.isNotEmpty()) {
                         currentTurnUserContent = "Context: The user has uploaded an image containing: $labels.\n\nUser: $prompt"
                     }
                 }
             } catch (e: Exception) {
                 e.printStackTrace()
             }
        }

        // Build History Prompt
        val historyMessages = chatDao.getMessagesForSession(sessionId).filter { it.role != "system" }
        // Take last 6 messages to keep context window manageable
        val recentHistory = historyMessages.takeLast(6) 
        
        val fullPromptBuilder = StringBuilder()
        for (msg in recentHistory) {
             // Skip the current prompt if it was already inserted into DB (which it is, in sendMessage)
             // sendMessage inserts userMsg BEFORE calling this function.
             // So recentHistory INCLUDES the current prompt at the end?
             // Yes: "sendMessage inserts userMsg" -> "generateWith..."
             // So the last message in DB is the current user prompt.
             // But we want to format it ourselves with the augmented vision content if applicable.
             // So we should SKIP the very last message from history if it matches our current turn?
             // Actually, `sendMessage` inserts `userMsg`. Then calls generation.
             // So `historyMessages` contains `userMsg` as the last item.
             // We should exclude the *last* item from history iteration, and append our `currentTurnUserContent` instead.
             // Because `currentTurnUserContent` might have the image/vision context which is NOT in the DB content (DB has raw text).
        }
        
        // Correct Logic:
        // 1. Get history excluding the latest message (which is current turn)
        val historyContext = if (historyMessages.isNotEmpty()) historyMessages.dropLast(1).takeLast(6) else emptyList()
        
        for (msg in historyContext) {
            fullPromptBuilder.append("<start_of_turn>${msg.role}\n${msg.content}<end_of_turn>\n")
        }
        
        // 2. Append current turn
        fullPromptBuilder.append("<start_of_turn>user\n$currentTurnUserContent<end_of_turn>\n<start_of_turn>model\n")
        
        return try {
            inference.generateResponse(fullPromptBuilder.toString())
        } catch (e: Exception) {
            "Error from offline model: ${e.message}"
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

    suspend fun getMessagesForSession(sessionId: String): List<Message> {
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
        return llmInference != null
    }

    fun cleanup() {
        llmInference?.close()
    }
}
