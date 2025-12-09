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
        val geminiApiKey = "AIzaSyAoeK-NZKgtNyVhjkLm9MzZeQyvHGdqWcs"
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
    var currentScreen by remember { 
        mutableStateOf<Screen>(
            if (FirebaseAuth.getInstance().currentUser != null) {
                Screen.Download
            } else {
                Screen.Landing
            }
        ) 
    }
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
                onGoogleSignupSuccess = {
                    currentScreen = Screen.Download
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
                            chatRepository.initializeOfflineModel(modelPath)
                            currentScreen = Screen.Chat
                        } catch (e: Exception) {
                            // Handle initialization error
                        }
                    }
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
