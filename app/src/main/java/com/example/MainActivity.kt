package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.example.ui.theme.PersianTurquoise
import kotlinx.coroutines.launch

sealed class Screen(
    val route: String,
    val persianTitle: String,
    val icon: ImageVector
) {
    object Chat : Screen("chat", "گفتگو با فوکو", Icons.AutoMirrored.Filled.Chat)
    object Connectors : Screen("connectors", "ابزارها و پلاگین‌ها", Icons.Default.Extension)
    object Settings : Screen("settings", "تنظیمات و کلیدها", Icons.Default.Settings)
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

/**
 * Shell of the app.
 *
 * The three-tab bottom bar is gone. In its place a panel that slides in from the left edge
 * (swipe right on the screen, or tap the ☰ button) holds every section of the app, so the
 * chat itself gets the full height of the screen.
 */
@Composable
fun PersianAiApp(
    openTwoWayVoice: Boolean = false,
    onTwoWayVoiceConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Chat.route

    val chatViewModel: ChatViewModel = viewModel()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun openSection(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Swiping from the left edge opens the panel on every screen.
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 6.dp
            ) {
                FokoSectionDrawer(
                    currentRoute = currentRoute,
                    onSelect = { route ->
                        scope.launch { drawerState.close() }
                        openSection(route)
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
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
                        onNavigateToScreen = { route -> openSection(route) },
                        onOpenMenu = { scope.launch { drawerState.open() } }
                    )
                }
                composable(Screen.Connectors.route) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        FokoSectionTopBar(
                            title = Screen.Connectors.persianTitle,
                            onOpenMenu = { scope.launch { drawerState.open() } }
                        )
                        ConnectorsScreen()
                    }
                }
                composable(Screen.Settings.route) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        FokoSectionTopBar(
                            title = Screen.Settings.persianTitle,
                            onOpenMenu = { scope.launch { drawerState.open() } }
                        )
                        SettingsScreen()
                    }
                }
            }
        }
    }
}

/** نوار بالای بخش‌هایی که خودشان نوار ندارند؛ فقط دکمه‌ی منو. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FokoSectionTopBar(title: String, onOpenMenu: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onOpenMenu) {
                Icon(Icons.Default.Menu, contentDescription = "منو")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * فهرست بخش‌ها داخل کشو. همه‌ی آیکون‌ها و صفحه‌ها همین‌جا جمع شده‌اند.
 */
@Composable
private fun FokoSectionDrawer(currentRoute: String, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple(Screen.Chat.route, Screen.Chat.persianTitle, Screen.Chat.icon),
        Triple(Screen.Connectors.route, Screen.Connectors.persianTitle, Screen.Connectors.icon),
        Triple(Screen.Settings.route, Screen.Settings.persianTitle, Screen.Settings.icon)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PersianTurquoise),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "FOKO AI (فوکو)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "دستیار هوشمند و خودکار",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "بخش‌ها:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        items.forEach { (route, title, icon) ->
            val active = currentRoute == route
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(route) },
                color = if (active) PersianTurquoise.copy(alpha = 0.16f) else Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = PersianTurquoise,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
