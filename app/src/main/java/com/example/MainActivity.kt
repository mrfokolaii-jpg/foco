package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
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

/**
 * بخش‌ها بین دو پنل نصف شده‌اند:
 *  • پنل راست (فوکو)  ← گفتگو با فوکو، ابزارها و پلاگین‌ها
 *  • پنل چپ  (کنترل)  ← تنظیمات و کلیدها (به‌همراه میان‌برهای سریع)
 */
private val FOKO_RIGHT_SECTIONS: List<Screen> = listOf(Screen.Chat, Screen.Connectors)
private val FOKO_LEFT_SECTIONS: List<Screen> = listOf(Screen.Settings)

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
 * The three-tab bottom bar is gone. Two panels now split the app between them:
 *
 *  • پنل «فوکو»  — از لبهٔ راست باز می‌شود (کشیدن از راست به چپ) و بخش‌های کاربردی
 *                 (گفتگو با فوکو، ابزارها و پلاگین‌ها) را در خود دارد.
 *  • پنل «کنترل» — از لبهٔ چپ باز می‌شود (کشیدن از چپ به راست) و بخش مدیریت
 *                 (تنظیمات و کلیدها) به‌همراه میان‌برهای سریع را دارد.
 *
 * اپ راست‌به‌چپ است، پس جهت چیدمان هر قاب را جداگانه تنظیم می‌کنیم تا هر پنل از لبهٔ
 * درست باز شود؛ داخلِ هر دو پنل و کل محتوای اپ دوباره راست‌به‌چپ می‌شود تا فارسی سالم بماند.
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
    // دو پنل مستقل: یکی لنگرِ لبهٔ راست، دیگری لنگرِ لبهٔ چپ.
    val rightPanelState = rememberDrawerState(DrawerValue.Closed)
    val leftPanelState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun closePanels() {
        scope.launch { rightPanelState.close() }
        scope.launch { leftPanelState.close() }
    }

    fun openSection(route: String) {
        closePanels()
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

    /** میان‌برهایی که مستقیم به صفحه‌ی تنظیمات گوشی می‌روند. */
    fun openSystemScreen(action: String, withPackage: Boolean = false) {
        closePanels()
        val intent = if (withPackage) {
            Intent(action, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(action)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // قاب بیرونی چپ‌به‌راست ⇒ پنل «کنترل» از لبهٔ چپ باز می‌شود.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ModalNavigationDrawer(
            drawerState = leftPanelState,
            gesturesEnabled = true,
            drawerContent = {
                // داخل پنل دوباره راست‌به‌چپ می‌شود تا متن فارسی درست چیده شود.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        drawerTonalElevation = 6.dp
                    ) {
                        FokoPanel(
                            badge = "کنترل",
                            headline = "تنظیمات و کلیدها",
                            subtitle = "مدیریت هوش مصنوعی، کلیدها و دسترسی‌ها",
                            sections = FOKO_LEFT_SECTIONS,
                            currentRoute = currentRoute,
                            onSelect = { route -> openSection(route) },
                            shortcuts = listOf(
                                FokoShortcut(
                                    title = "کلیدهای پلاگین‌ها",
                                    subtitle = "گیت‌هاب و هاگینگ‌فیس",
                                    icon = Icons.Default.Key
                                ) { openSection(Screen.Settings.route) },
                                FokoShortcut(
                                    title = "صدا و وویست‌های فارسی",
                                    subtitle = "انتخاب و تست گوینده",
                                    icon = Icons.Default.RecordVoiceOver
                                ) { openSection(Screen.Connectors.route) },
                                FokoShortcut(
                                    title = "سرویس دسترسی‌پذیری",
                                    subtitle = "لمس خودکار و اجرای فرمان",
                                    icon = Icons.Default.Accessibility
                                ) {
                                    openSystemScreen(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                },
                                FokoShortcut(
                                    title = "ویجت شناور",
                                    subtitle = "نمایش روی برنامه‌های دیگر",
                                    icon = Icons.Default.Layers
                                ) {
                                    openSystemScreen(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        withPackage = true
                                    )
                                }
                            ),
                            footer = "این پنل از لبهٔ چپ (چپ به راست) باز می‌شود."
                        )
                    }
                }
            }
        ) {
            // قاب داخلی راست‌به‌چپ ⇒ پنل «فوکو» از لبهٔ راست باز می‌شود.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModalNavigationDrawer(
                    drawerState = rightPanelState,
                    gesturesEnabled = true,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = MaterialTheme.colorScheme.surface,
                            drawerTonalElevation = 6.dp
                        ) {
                            FokoPanel(
                                badge = "فوکو",
                                headline = "دستیار هوشمند",
                                subtitle = "گفتگو، ابزارها و پلاگین‌ها",
                                sections = FOKO_RIGHT_SECTIONS,
                                currentRoute = currentRoute,
                                onSelect = { route -> openSection(route) },
                                footer = "این پنل از لبهٔ راست (راست به چپ) باز می‌شود."
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
                                    onOpenMenu = { scope.launch { rightPanelState.open() } },
                                    onOpenQuickPanel = { scope.launch { leftPanelState.open() } }
                                )
                            }
                            composable(Screen.Connectors.route) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    FokoSectionTopBar(
                                        title = Screen.Connectors.persianTitle,
                                        onOpenMenu = { scope.launch { rightPanelState.open() } },
                                        onOpenQuickPanel = { scope.launch { leftPanelState.open() } }
                                    )
                                    ConnectorsScreen()
                                }
                            }
                            composable(Screen.Settings.route) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    FokoSectionTopBar(
                                        title = Screen.Settings.persianTitle,
                                        onOpenMenu = { scope.launch { rightPanelState.open() } },
                                        onOpenQuickPanel = { scope.launch { leftPanelState.open() } }
                                    )
                                    SettingsScreen()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** نوار بالای بخش‌هایی که خودشان نوار ندارند؛ دکمه‌ی پنل راست و پنل چپ. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FokoSectionTopBar(
    title: String,
    onOpenMenu: () -> Unit,
    onOpenQuickPanel: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            // پنل «فوکو» — از لبهٔ راست
            IconButton(onClick = onOpenMenu) {
                Icon(Icons.Default.Menu, contentDescription = "پنل فوکو (بخش‌ها)")
            }
        },
        actions = {
            // پنل «کنترل» — از لبهٔ چپ
            IconButton(onClick = onOpenQuickPanel) {
                Icon(Icons.Default.Tune, contentDescription = "پنل کنترل و تنظیمات")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** یک میان‌بر سریع داخل پنل چپ. */
private data class FokoShortcut(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * محتوای مشترک هر دو پنل: سرصفحه، فهرست بخش‌ها، میان‌برها و راهنمای باز شدن پنل.
 */
@Composable
private fun FokoPanel(
    badge: String,
    headline: String,
    subtitle: String,
    sections: List<Screen>,
    currentRoute: String,
    onSelect: (String) -> Unit,
    shortcuts: List<FokoShortcut> = emptyList(),
    footer: String
) {
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
                    text = headline,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "بخش‌های $badge:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        sections.forEach { screen ->
            FokoPanelRow(
                title = screen.persianTitle,
                subtitle = null,
                icon = screen.icon,
                active = currentRoute == screen.route,
                onClick = { onSelect(screen.route) }
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        if (shortcuts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "دسترسی سریع:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            shortcuts.forEach { shortcut ->
                FokoPanelRow(
                    title = shortcut.title,
                    subtitle = shortcut.subtitle,
                    icon = shortcut.icon,
                    active = false,
                    onClick = shortcut.onClick
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PersianTurquoise.copy(alpha = 0.12f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = PersianTurquoise,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = footer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** یک ردیف قابل لمس داخل پنل. */
@Composable
private fun FokoPanelRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
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
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
