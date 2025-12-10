package com.example.hybridmind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.room.Room
import androidx.work.*
import com.example.hybridmind.core.NetworkMonitor
import com.example.hybridmind.data.ChatRepository
import com.example.hybridmind.data.ModelDownloader
import com.example.hybridmind.data.local.AppDatabase
import com.example.hybridmind.ui.auth.LoginScreen
import com.example.hybridmind.ui.auth.SignupScreen
import com.example.hybridmind.ui.settings.SettingsScreen
import com.example.hybridmind.ui.chat.ChatScreen
import com.example.hybridmind.ui.download.DownloadScreen
import com.example.hybridmind.ui.landing.LandingScreen
import com.example.hybridmind.ui.download.DownloadScreen
import com.example.hybridmind.ui.theme.HybridMindTheme
import com.example.hybridmind.workers.AutoPruneWorker
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var database: AppDatabase
    private lateinit var chatRepository: ChatRepository
    private lateinit var modelDownloader: ModelDownloader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize components
        networkMonitor = NetworkMonitor(applicationContext)
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "hybridmind_database"
        ).fallbackToDestructiveMigration().build()

        modelDownloader = ModelDownloader(applicationContext)

        // Gemini API Key
        val geminiApiKey = "AIzaSyBO3WUf6uVeFDVhYU7I9dvvoVt92isv86U"
        chatRepository = ChatRepository(
            context = applicationContext,
            networkMonitor = networkMonitor,
            database = database,
            geminiApiKey = geminiApiKey
        )

        // Schedule auto-prune worker
        scheduleAutoPruneWorker()

        setContent {
            HybridMindTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        chatRepository = chatRepository,
                        networkMonitor = networkMonitor,
                        modelDownloader = modelDownloader
                    )
                }
            }
        }
    }

    private fun scheduleAutoPruneWorker() {
        val workRequest = PeriodicWorkRequestBuilder<AutoPruneWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "AutoPruneWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        chatRepository.cleanup()
    }
}

@Composable
fun AppNavigation(
    chatRepository: ChatRepository,
    networkMonitor: NetworkMonitor,
    modelDownloader: ModelDownloader
) {
    // Check if user is signed in and verified
    val currentUser = FirebaseAuth.getInstance().currentUser
    val initialScreen = if (currentUser != null && currentUser.isEmailVerified) {
        Screen.Download // Verified user
    } else {
        Screen.Landing // Not signed in or not verified
    }
    
    var currentScreen by remember { mutableStateOf(initialScreen) }
    val scope = rememberCoroutineScope()

    when (currentScreen) {
        Screen.Landing -> {
            LandingScreen(
                onGetStarted = {
                    currentScreen = Screen.Login // "Get Started" defaults to Login
                },
                onLogin = {
                    currentScreen = Screen.Login
                },
                onSignup = {
                    currentScreen = Screen.Signup
                }
            )
        }
        Screen.Login -> {
            LoginScreen(
                onLoginSuccess = {
                    currentScreen = Screen.Download
                },
                onNavigateToSignup = {
                    currentScreen = Screen.Signup
                },
                onBack = {
                    currentScreen = Screen.Landing
                }
            )
        }
        Screen.Signup -> {
            SignupScreen(
                onSignupSuccess = {
                    currentScreen = Screen.Login
                },
                onBack = {
                    currentScreen = Screen.Landing
                }
            )
        }
        Screen.Download -> {
            DownloadScreen(
                modelDownloader = modelDownloader,
                onDownloadComplete = { modelPath ->
                    scope.launch {
                        try {
                            android.util.Log.d("MainActivity", "=== Model Initialization Started ===")
                            android.util.Log.d("MainActivity", "Model path: $modelPath")
                            
                            // Check if file exists
                            val file = java.io.File(modelPath)
                            if (!file.exists()) {
                                throw Exception("Model file not found at: $modelPath")
                            }
                            android.util.Log.d("MainActivity", "Model file exists: ${file.length()} bytes")
                            
                            // Initialize the model
                            chatRepository.initializeOfflineModel(modelPath)
                            
                            android.util.Log.d("MainActivity", "✅ Model initialization successful!")
                            
                            currentScreen = Screen.Chat
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "❌ Model initialization failed: ${e.message}", e)
                            e.printStackTrace()
                            // TODO: Show user-friendly error dialog with retry option
                            // For now, error is logged and user stays on download screen
                        }
                    }
                },
                onBack = {
                    // Sign out and go back to landing
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                    currentScreen = Screen.Landing
                }
            )
        }
        Screen.Settings -> {
            SettingsScreen(
                chatRepository = chatRepository,
                modelDownloader = modelDownloader,
                onBack = { currentScreen = Screen.Chat },
                onSignOut = {
                   FirebaseAuth.getInstance().signOut()
                   currentScreen = Screen.Login
                },
                onModelSwitched = {
                    currentScreen = Screen.Chat
                }
            )
        }
        Screen.Chat -> {
            ChatScreen(
                chatRepository = chatRepository,
                networkMonitor = networkMonitor,
                onSignOut = {
                    FirebaseAuth.getInstance().signOut()
                    currentScreen = Screen.Login
                },
                onSettingsClick = {
                    currentScreen = Screen.Settings
                }
            )
        }
    }
}

sealed class Screen {
    object Landing : Screen()
    object Login : Screen()
    object Signup : Screen()
    object Download : Screen()
    object Chat : Screen()
    object Settings : Screen()
}
