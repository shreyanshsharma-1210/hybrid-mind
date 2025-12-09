package com.example.hybridmind.data.cloud

import com.example.hybridmind.data.local.ChatSession
import com.example.hybridmind.data.local.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private fun getUserId(): String? = auth.currentUser?.uid
    
    /**
     * Syncs a chat session to Firestore (online messages only)
     */
    suspend fun syncSession(session: ChatSession) {
        val userId = getUserId() ?: return
        
        // Only sync if NOT offline-only
        if (session.is_offline_only) {
            return // Privacy rule: offline sessions never sync
        }
        
        firestore.collection("users")
            .document(userId)
            .collection("sessions")
            .document(session.id)
            .set(mapOf(
                "title" to session.title,
                "is_offline_only" to session.is_offline_only,
                "last_updated" to session.last_updated
            ))
            .await()
    }
    
    /**
     * Syncs a message to Firestore (online messages only)
     */
    suspend fun syncMessage(sessionId: String, message: Message, isOfflineSession: Boolean) {
        val userId = getUserId() ?: return
        
        // Privacy rule: don't sync if session is offline-only
        if (isOfflineSession) {
            return
        }
        
        firestore.collection("users")
            .document(userId)
            .collection("sessions")
            .document(sessionId)
            .collection("messages")
            .document(message.id)
            .set(mapOf(
                "role" to message.role,
                "content" to message.content,
                "timestamp" to message.timestamp
            ))
            .await()
    }
    
    /**
     * Fetches all sessions from Firestore for current user
     */
    suspend fun fetchSessions(): List<ChatSession> {
        val userId = getUserId() ?: return emptyList()
        
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("sessions")
                .orderBy("last_updated", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                ChatSession(
                    id = doc.id,
                    user_id = userId,
                    title = doc.getString("title") ?: "Untitled",
                    is_offline_only = doc.getBoolean("is_offline_only") ?: false,
                    last_updated = doc.getLong("last_updated") ?: 0L
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Fetches all messages for a session from Firestore
     */
    suspend fun fetchMessages(sessionId: String): List<Message> {
        val userId = getUserId() ?: return emptyList()
        
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("sessions")
                .document(sessionId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                Message(
                    id = doc.id,
                    session_id = sessionId,
                    role = doc.getString("role") ?: "user",
                    content = doc.getString("content") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Deletes a session from Firestore
     */
    suspend fun deleteSession(sessionId: String) {
        val userId = getUserId() ?: return
        
        try {
            // Delete all messages first
            val messagesRef = firestore.collection("users")
                .document(userId)
                .collection("sessions")
                .document(sessionId)
                .collection("messages")
            
            val messages = messagesRef.get().await()
            messages.documents.forEach { it.reference.delete() }
            
            // Delete session
            firestore.collection("users")
                .document(userId)
                .collection("sessions")
                .document(sessionId)
                .delete()
                .await()
        } catch (e: Exception) {
            // Handle error
        }
    }
}
