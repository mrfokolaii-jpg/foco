package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.connectors.ConnectorsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

sealed class Screen(
    val route: String,
    val persianTitle: String,
    val icon: ImageVector,
    val inBottomBar: Boolean = true
) {
    object Chat : Screen("chat", "گفتگو با فوکو", Icons.AutoMirrored.Filled.Chat)
    object Connectors : Screen("connectors", "ابزارها", Icons.Default.Extension)
    object Settings : Screen("settings", "تنظیمات و دسترسی‌ها", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    private val openTwoWayVoice = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openTwoWayVoice.value = intent?.getBooleanExtra(EXTRA_OPEN_TWO_WAY_VOICE, false) == true
        enableEdgeToEdge()

        requestCorePermissions()

        try {
            val fokoDir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "FOKO")
            if (!fokoDir.exists()) {
                fokoDir.mkdirs()
            }
        } catch (_: Exception) {
        }

        setContent {
            MyApplicationTheme {
                PersianAiApp(
                    openTwoWayVoice = openTwoWayVoice.value,
                    onTwoWayVoiceConsumed = { openTwoWayVoice.value = false }
                )
            }
        }
    }

    private fun requestCorePermissions() {
        val missing = allRuntimePermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    /**
     * Every runtime permission FOKO declares. They are requested so the user sees them
     * in Settings > Apps > FOKO AI > Permissions and can grant them one by one.
     * Special-access permissions (overlay / accessibility / all-files) are launched
     * from the in-app settings screen instead, because Android handles them as
     * settings screens rather than dialogs.
     */
    private fun allRuntimePermissions(): List<String> {
        val wanted = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            // Contacts (تماس / مخاطبین)
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.GET_ACCOUNTS,
            // Phone (تماس تلفنی)
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            // SMS (پیامک)
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            // Location, used by the device tools
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // Storage: legacy permissions up to Android 12, granular media access from Android 13
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            wanted.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            wanted.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
            wanted.add(Manifest.permission.READ_MEDIA_IMAGES)
            wanted.add(Manifest.permission.READ_MEDIA_VIDEO)
            wanted.add(Manifest.permission.READ_MEDIA_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            wanted.add(Manifest.permission.READ_PHONE_NUMBERS)
            wanted.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        return wanted.distinct()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TWO_WAY_VOICE, false)) {
            openTwoWayVoice.value = true
        }
    }

    companion object {
        const val EXTRA_OPEN_TWO_WAY_VOICE = "open_two_way_voice"
    }
}

@Composable
fun PersianAiApp(
    openTwoWayVoice: Boolean = false,
    onTwoWayVoiceConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Chat.route

    val chatViewModel: ChatViewModel = viewModel()

    val screens = listOf(
        Screen.Chat,
        Screen.Connectors,
        Screen.Settings
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                tonalElevation = 6.dp
            ) {
                screens.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.persianTitle
                            )
                        },
                        label = {
                            Text(
                                text = screen.persianTitle,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        },
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_${screen.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Chat.route) {
                chatViewModel.refreshKeys()
                ChatScreen(
                    viewModel = chatViewModel,
                    openTwoWayVoice = openTwoWayVoice,
                    onTwoWayVoiceConsumed = onTwoWayVoiceConsumed,
                    onNavigateToScreen = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Connectors.route) {
                ConnectorsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
