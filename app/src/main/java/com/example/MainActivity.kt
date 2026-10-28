package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.AppItem
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DownloadState
import com.example.ui.viewmodel.StoreViewModel
import com.example.ui.viewmodel.AuthViewModel
import android.app.Activity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class MainActivity : ComponentActivity() {
    private val viewModel: StoreViewModel by viewModels()
    private val authViewModel: AuthViewModel by viewModels()
    private var pendingTab by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTab = intent?.getIntExtra("open_tab", 0) ?: 0

        setContent {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val useDeviceTheme by viewModel.useDeviceTheme.collectAsState()
    val effectiveDarkMode = if (useDeviceTheme) isSystemInDarkTheme() else isDarkMode
    val currentUser by authViewModel.currentUser.collectAsState()
    val authError by authViewModel.authError.collectAsState()

    var authScreen by remember { mutableStateOf("welcome") }

    MyApplicationTheme(darkTheme = effectiveDarkMode) {
        LaunchedEffect(effectiveDarkMode) {
            val window = (this@MainActivity).window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = window.insetsController ?: return@LaunchedEffect
                controller.setSystemBarsAppearance(
                    if (effectiveDarkMode) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
                controller.setSystemBarsAppearance(
                    if (effectiveDarkMode) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (effectiveDarkMode) {
                    window.decorView.systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                } else {
                    window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
        }

        if (currentUser == null) {
            when (authScreen) {
                "welcome" -> com.example.ui.auth.AuthWelcomeScreen(
                    onLoginClick = { authScreen = "login" },
                    onGoogleLoginClick = { idToken -> authViewModel.googleLogin(idToken) },
                    onRegisterClick = { authScreen = "register" },
                    onBack = { finish() }
                )
                "login" -> com.example.ui.auth.AuthLoginScreen(
                    onLoginSuccess = { email, pass -> authViewModel.emailLogin(email, pass) },
                    onGoogleLoginClick = { idToken -> authViewModel.googleLogin(idToken) },
                    onForgotPassword = { authScreen = "forgot" },
                    onRegisterClick = { authScreen = "register" },
                    onBack = { authScreen = "welcome" },
                    authError = authError
                )
                "register" -> com.example.ui.auth.AuthRegisterScreen(
                    onRegisterSuccess = { name, email, pass -> authViewModel.emailRegister(name, email, pass) },
                    onGoogleLoginClick = { idToken -> authViewModel.googleLogin(idToken) },
                    onLoginClick = { authScreen = "login" },
                    onBack = { authScreen = "welcome" },
                    authError = authError
                )
                "forgot" -> com.example.ui.auth.AuthForgotPasswordScreen(
                    onSendReset = { email -> authViewModel.sendResetEmail(email) },
                    onBackToLogin = { authScreen = "login" },
                    onBack = { authScreen = "login" },
                    authError = authError
                )
            }
        } else {
            AppStoreApp(viewModel = viewModel, authViewModel = authViewModel, selectedTab = pendingTab, onTabSelected = { pendingTab = it }, darkMode = effectiveDarkMode)
        }
    }
}
}
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingTab = intent.getIntExtra("open_tab", 0)
    }

    override fun onResume() {
        super.onResume()
        // Refresh installed packages list when coming back to the app (e.g. after install)
        viewModel.refreshInstalledPackages()
        viewModel.checkForUpdatesAndNotify()
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppStoreApp(viewModel: StoreViewModel, authViewModel: AuthViewModel, selectedTab: Int = 0, onTabSelected: (Int) -> Unit = {}, darkMode: Boolean = true) {
    val context = LocalContext.current
    val allApps by viewModel.allApps.collectAsState()
    val favoriteApps by viewModel.favoriteApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val installedPackages by viewModel.installedPackages.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val syncCount by viewModel.syncCount.collectAsState()

    val useDeviceThemeState by viewModel.useDeviceTheme.collectAsState()
    val isDarkModeState by viewModel.isDarkMode.collectAsState()
    val effectiveDark = if (useDeviceThemeState) isSystemInDarkTheme() else isDarkModeState
    val iconBorderColor = if (isSystemInDarkTheme()) Color(0xFFE5E4E2) else Color(0xFF333333)

    var showSelfUpdateDialog by remember { mutableStateOf(false) }

    fun refreshWithSelfUpdateCheck() {
        viewModel.syncSelfUpdateOnly {
            if (viewModel.isSelfUpdateAvailable()) {
                showSelfUpdateDialog = true
            } else {
                viewModel.forceSync()
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { 5 })
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var isFirstPage by remember { mutableStateOf(true) }

    LaunchedEffect(pagerState.currentPage) {
        if (isFirstPage) {
            isFirstPage = false
        } else if (!isProgrammaticScroll && selectedTab != pagerState.currentPage) {
            onTabSelected(pagerState.currentPage)
        }
    }
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            isProgrammaticScroll = true
            try {
                pagerState.scrollToPage(selectedTab)
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    // Auto-refresh when switching to Store pages (Android or Windows) or Manage Apps
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 || selectedTab == 1 || selectedTab == 2) refreshWithSelfUpdateCheck()
    }

    val androidApps = remember(allApps) { allApps.filter { it.platform == "Android" && it.packageName != "com.mahmuduls.appstore" } }
    val windowsApps = remember(allApps) { allApps.filter { it.platform == "Windows" && it.packageName != "com.mahmuduls.appstore" } }
    val visibleApps = remember(allApps) { allApps.filter { it.packageName != "com.mahmuduls.appstore" } }

    val appsWithUpdates = remember(allApps, installedPackages) {
        visibleApps.filter { app ->
            if (!installedPackages.containsKey(app.packageName)) return@filter false
            try {
                val pkgInfo = context.packageManager.getPackageInfo(app.packageName, 0)
                val installedVerName = pkgInfo.versionName ?: ""
                val installedVerCode = installedPackages[app.packageName] ?: 0
                if (installedVerName.isNotEmpty() && !context.isUpdateAvailable(app, installedVerCode, installedVerName)) return@filter false
            } catch (_: Exception) {
                val installedVer = installedPackages[app.packageName] ?: return@filter false
                if (app.versionCode <= installedVer) return@filter false
            }
            true
        }
    }
    val installedStoreApps = remember(allApps, installedPackages) {
        visibleApps.filter { app ->
            installedPackages.containsKey(app.packageName)
        }
    }

    LaunchedEffect(Unit) {
        if (syncCount == 0) {
            refreshWithSelfUpdateCheck()
        }
    }

    LaunchedEffect(syncCount) {
        if (syncCount > 0 && viewModel.isSelfUpdateAvailable()) {
            showSelfUpdateDialog = true
        }
    }

    if (showSelfUpdateDialog) {
        val selfUpdateApp = viewModel.selfUpdateApp.collectAsState().value ?: viewModel.getCachedSelfUpdateApp()
        val selfInstalledVer = installedPackages[selfUpdateApp.packageName]
        val selfInstalledVerName = try {
            context.packageManager.getPackageInfo(selfUpdateApp.packageName, 0).versionName
        } catch (_: Exception) { null }
        AppDescriptionDialog(
            showDescription = showSelfUpdateDialog,
            onDismiss = { showSelfUpdateDialog = false },
            app = selfUpdateApp,
            installedVersion = selfInstalledVer,
            installedVerName = selfInstalledVerName,
            onInstallClick = { viewModel.startSelfUpdate() },
            onLoadDescription = { app, cb -> viewModel.lazyLoadDescription(app, cb) },
                forced = true,
                downloadState = downloadStates[context.packageName],
                onForcedUpdateClick = {
                    val st = downloadStates[context.packageName]
                    if (st is DownloadState.Downloaded) {
                        viewModel.installDownloadedApp(context.packageName)
                        return@AppDescriptionDialog
                    }
                    if (st is DownloadState.Progress || st is DownloadState.Completed) return@AppDescriptionDialog
                    viewModel.startSelfUpdate()
                }
            )
        }

    Scaffold(
        bottomBar = {
            val navSelectedColor = if (darkMode) Color.White else Color.Black
            val navUnselectedColor = if (darkMode) Color(0xFFBDBDBD) else Color(0xFF616161)
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onTabSelected(0) }.padding(vertical = 2.dp)
                ) {
                    Icon(Icons.Filled.Home, contentDescription = "Store", tint = if (selectedTab == 0 || selectedTab == 1) navSelectedColor else navUnselectedColor, modifier = Modifier.size(22.dp))
                    Text("Home", fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, color = if (selectedTab == 0 || selectedTab == 1) navSelectedColor else navUnselectedColor)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onTabSelected(2) }.padding(vertical = 2.dp)
                ) {
                    BadgedBox(badge = {
                        if (appsWithUpdates.isNotEmpty()) {
                            Badge { Text(appsWithUpdates.size.toString()) }
                        }
                    }) {
                        Icon(Icons.Default.SystemUpdateAlt, contentDescription = "Updates", tint = if (selectedTab == 2) navSelectedColor else navUnselectedColor, modifier = Modifier.size(22.dp))
                    }
                    Text("Manage Apps", fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, color = if (selectedTab == 2) navSelectedColor else navUnselectedColor)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onTabSelected(3) }.padding(vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Downloads", tint = if (selectedTab == 3) navSelectedColor else navUnselectedColor, modifier = Modifier.size(22.dp))
                    Text("Downloads", fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, color = if (selectedTab == 3) navSelectedColor else navUnselectedColor)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onTabSelected(4) }.padding(vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = if (selectedTab == 4) navSelectedColor else navUnselectedColor, modifier = Modifier.size(22.dp))
                    Text("Settings", fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, color = if (selectedTab == 4) navSelectedColor else navUnselectedColor)
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tab contents with swipe
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                        0 -> StoreTab(
                            allApps = androidApps,
                            installedPackages = installedPackages,
                            downloadStates = downloadStates,
                            onInstallClick = { app ->
                                try {
                                    val state = downloadStates[app.packageName]
                                    if (state is DownloadState.Downloaded) {
                                        val file = java.io.File(state.filePath)
                                        if (file.exists() && file.absolutePath.contains("_${app.versionCode}.")) {
                                            viewModel.installDownloadedApp(app.packageName)
                                            viewModel.markPackageUpdated(app.packageName, app.versionCode, app.patchVersion)
                                            viewModel.deleteDownloadedFile(app.packageName)
                                        } else {
                                            if (file.exists()) file.delete()
                                            viewModel.deleteDownloadedFile(app.packageName)
                                            viewModel.startDownload(app)
                                        }
                                    } else {
                                        viewModel.deleteDownloadedFile(app.packageName)
                                        viewModel.startDownload(app)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "Install click failed", e)
                                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                        onWebDownload = { url ->
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUninstall = { pkg ->
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                android.util.Log.d("MainActivity", "Uninstall intent sent for $pkg")
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Uninstall failed for $pkg", e)
                                Toast.makeText(context, "Cannot uninstall $pkg", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCancelDownload = { pkg -> viewModel.cancelDownload(pkg) },
                        isSyncing = isSyncing,
                        onSyncClick = { refreshWithSelfUpdateCheck() },
                        onLoadDescription = { app, cb -> viewModel.lazyLoadDescription(app, cb) },
                        onTabSelected = onTabSelected
                    )
                    1 -> StoreTab(
                        allApps = windowsApps,
                        installedPackages = installedPackages,
                        downloadStates = downloadStates,
                        onInstallClick = { app ->
                            try {
                                val state = downloadStates[app.packageName]
                                if (state is DownloadState.Downloaded) {
                                    val file = java.io.File(state.filePath)
                                    if (file.exists() && file.absolutePath.contains("_${app.versionCode}.")) {
                                        viewModel.installDownloadedApp(app.packageName)
                                        viewModel.markPackageUpdated(app.packageName, app.versionCode, app.patchVersion)
                                        viewModel.deleteDownloadedFile(app.packageName)
                                    } else {
                                        if (file.exists()) file.delete()
                                        viewModel.deleteDownloadedFile(app.packageName)
                                        viewModel.startDownload(app)
                                    }
                                } else {
                                    viewModel.deleteDownloadedFile(app.packageName)
                                    viewModel.startDownload(app)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Install click failed", e)
                                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        onWebDownload = { url ->
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUninstall = { pkg ->
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                android.util.Log.d("MainActivity", "Uninstall intent sent for $pkg")
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Uninstall failed for $pkg", e)
                                Toast.makeText(context, "Cannot uninstall $pkg", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCancelDownload = { pkg -> viewModel.cancelDownload(pkg) },
                        isSyncing = isSyncing,
                        onSyncClick = { refreshWithSelfUpdateCheck() },
                        onLoadDescription = { app, cb -> viewModel.lazyLoadDescription(app, cb) },
                        onTabSelected = onTabSelected
                    )
                     2 -> {
                         val onUpdateClick: (AppItem) -> Unit = { app ->
                             try {
                                 val state = downloadStates[app.packageName]
                                 if (state is DownloadState.Downloaded) {
                                     viewModel.installDownloadedApp(app.packageName)
                                     viewModel.markPackageUpdated(app.packageName, app.versionCode, app.patchVersion)
                                     viewModel.deleteDownloadedFile(app.packageName)
                                 } else viewModel.startDownload(app)
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Update click failed", e)
                                 Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                             }
                         }
                         val onUpdateAll: () -> Unit = {
                             appsWithUpdates.forEach { app ->
                                 viewModel.startDownload(app)
                             }
                         }
                         val onUninstall: (String) -> Unit = { pkg ->
                              try {
                                  val intent = android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                  context.startActivity(intent)
                              } catch (e: Exception) {
                                  Toast.makeText(context, "Cannot uninstall $pkg", Toast.LENGTH_SHORT).show()
                              }
                          }
                          UpdatesTab(
                                allApps = visibleApps,
                               appsWithUpdates = appsWithUpdates,
                               installedStoreApps = installedStoreApps,
                               downloadStates = downloadStates,
                               installedPackages = installedPackages,
                               onUpdateClick = onUpdateClick,
                                onUpdateAll = onUpdateAll,
                                onCancelAll = { viewModel.cancelAllDownloads() },
                                onUninstall = onUninstall,
                                onCancelDownload = { pkg -> viewModel.cancelDownload(pkg) },
                               onCheckUpdates = { viewModel.refreshInstalledPackages(); viewModel.checkForUpdatesAndNotify(); Toast.makeText(context, "Scanning installed packages for updates...", Toast.LENGTH_SHORT).show() },
                               isSyncing = isSyncing,
                               onRefresh = { refreshWithSelfUpdateCheck() },
                               onLoadDescription = { app, cb -> viewModel.lazyLoadDescription(app, cb) }
                           )
                     }
                      3 -> DownloadsTab(
                          viewModel = viewModel,
                          storeApps = visibleApps,
                          downloadStates = downloadStates,
                          installedPackages = installedPackages,
                          isSyncing = isSyncing,
                          onRefresh = { refreshWithSelfUpdateCheck() }
                      )
                      4 -> SettingsTab(
                        viewModel = viewModel,
                        authViewModel = authViewModel,
                        onForceSync = { refreshWithSelfUpdateCheck() },
                        isSyncing = isSyncing,
                        onStartSelfUpdate = { viewModel.startSelfUpdate() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RefreshPillBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            val refreshing = isRefreshing
            val pulled = state.distanceFraction
            AnimatedVisibility(
                visible = refreshing || pulled > 0.02f,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val infinite = rememberInfiniteTransition(label = "refreshSpin")
                        val spin by infinite.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing)),
                            label = "spin"
                        )
                        val angle = if (refreshing) spin else pulled * 360f
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).rotate(angle)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (refreshing) "Refreshing..."
                            else if (pulled >= 1f) "Release to refresh"
                            else "Pull to refresh",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StoreTab(
    allApps: List<AppItem>,
    installedPackages: Map<String, Int>,
    downloadStates: Map<String, DownloadState>,
    onInstallClick: (AppItem) -> Unit,
    onWebDownload: (String) -> Unit = {},
    onUninstall: (String) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    onLoadDescription: ((AppItem, (String) -> Unit) -> Unit)? = null,
    onTabSelected: (Int) -> Unit = {}
) {
    var showSearch by remember { mutableStateOf(false) }
    val iconBorderColor = if (isSystemInDarkTheme()) Color(0xFFE5E4E2) else Color(0xFF333333)

    RefreshPillBox(
        isRefreshing = isSyncing,
        onRefresh = onSyncClick,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // App header and Logo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Image(
                        painter = painterResource(id = R.drawable.app_store_icon),
                        contentDescription = "Logo",
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).border(0.5.dp, iconBorderColor, RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "App Store",
                            fontSize = 22.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Build by Mahmudul",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { showSearch = true }) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                    IconButton(onClick = onSyncClick, enabled = !isSyncing) {
                    Icon(
                        if (isSyncing) Icons.Default.Sync else Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Platform filter chips
            val currentPlatform = allApps.firstOrNull()?.platform ?: "Android"
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("Android", "Windows").forEachIndexed { index, platform ->
                    FilterChip(
                        selected = platform == currentPlatform,
                        onClick = { onTabSelected(index) },
                        label = {
                            Text(
                                platform,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            var query by remember { mutableStateOf("") }
            val focusRequester = remember { FocusRequester() }

        if (showSearch) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            BackHandler { showSearch = false }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps by name or package...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { showSearch = false }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).focusRequester(focusRequester),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            }
            val platformFiltered = allApps

            if (showSearch) {
                val filtered = remember(platformFiltered, query) {
                    if (query.isBlank()) emptyList()
                    else platformFiltered.filter {
                        it.name.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                    }
                }
                if (query.isNotBlank() && filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No applications match your query.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 0.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(if (query.isBlank()) platformFiltered else filtered, key = { it.id }) { app ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                AppCard(
                                    app = app,
                                    installedVersion = installedPackages[app.packageName],
                                    downloadState = downloadStates[app.packageName] ?: DownloadState.Idle,
                                    onInstallClick = onInstallClick,
                                    onWebDownload = onWebDownload,
                                    onUninstall = onUninstall,
                                    onCancelDownload = onCancelDownload,
                                    onLoadDescription = onLoadDescription
                                )
                            }
                        }
                    }
                }
            } else {
                // Standard Catalog Layout
                LazyColumn(
                        contentPadding = PaddingValues(bottom = 0.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (platformFiltered.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                        Icon(
                                            Icons.Default.CloudQueue,
                                            contentDescription = "Empty cloud",
                                            modifier = Modifier.size(56.dp),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "No apps found yet",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Configure your Google Drive credentials in Settings to pull real packages.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(platformFiltered, key = { it.id }) { app ->
                                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                    AppCard(
                                        app = app,
                                        installedVersion = installedPackages[app.packageName],
                                        downloadState = downloadStates[app.packageName] ?: DownloadState.Idle,
                                        onInstallClick = onInstallClick,
                                        onWebDownload = onWebDownload,
                                        onUninstall = onUninstall,
                                        onCancelDownload = onCancelDownload,
                                        onLoadDescription = onLoadDescription
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    app: AppItem,
    installedVersion: Int?,
    downloadState: DownloadState,
    onInstallClick: (AppItem) -> Unit,
    onWebDownload: (String) -> Unit = {},
    onUninstall: (String) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onLoadDescription: ((AppItem, (String) -> Unit) -> Unit)? = null
) {
    var showLongPressMenu by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val iconBorderColor = if (isSystemInDarkTheme()) Color(0xFFE5E4E2) else Color(0xFF333333)
    val installedVerName = remember(app.packageName, installedVersion) {
        try { ctx.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(installedVersion) {
                detectTapGestures(
                    onTap = { showDescription = true },
                    onLongPress = { showLongPressMenu = true }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(app.iconUrl)
                    .memoryCacheKey("icon_${app.packageName}_${app.versionCode}")
                    .diskCacheKey("icon_${app.packageName}_${app.versionCode}")
                    .crossfade(true)
                    .build(),
                contentDescription = "${app.name} icon",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.5.dp, iconBorderColor, RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (app.platform != "Windows") {
                    Text(
                        text = app.packageName,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val displayVersion = if (installedVersion != null && installedVerName.isNotEmpty()) installedVerName else app.versionName
                    val cardHasUpdate = installedVersion != null && installedVerName.isNotEmpty() && ctx.isUpdateAvailable(app, installedVersion, installedVerName)
                    val versionSizeText = "v$displayVersion • ${formatFileSize(app.size)}"
                    Text(
                        text = versionSizeText,
                        fontSize = versionFontSize("v$displayVersion"),
                        lineHeight = 13.sp,
                        color = if (cardHasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                when (downloadState) {
                    is DownloadState.Idle -> {
                        val isInstalled = installedVersion != null
                        val hasUpdate = installedVersion != null && installedVerName?.let { ctx.isUpdateAvailable(app, installedVersion!!, it) } == true
                        val isWebUrl = !app.downloadUrl.contains("googleapis.com/drive") && !app.driveFileId.startsWith("manifest_")
                        if (isWebUrl && !isInstalled) {
                            Button(
                                onClick = { onWebDownload(app.downloadUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Web", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (app.platform == "Windows") {
                            Button(
                                onClick = {
                                    val desc = if (app.description.startsWith("__MD_ID:")) "" else app.description
                                    val overview = desc.lines().dropWhile { !it.trim().startsWith("## Overview") }.drop(1).takeWhile { !it.trim().startsWith("## ") }.joinToString(" ").trim().replace("**", "").replace("* ", "").replace("- ", "")
                                    val dlLink = if (app.driveFileId.startsWith("manifest_")) app.downloadUrl else "https://drive.google.com/uc?export=download&id=${app.driveFileId}"
                                    val shareText = "Check out ${app.name} — ${overview.take(200)}\n\nDownload: $dlLink"
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    }
                                    ctx.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${app.name}"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (isInstalled && !hasUpdate) {
                                        try {
                                            val intent = ctx.packageManager.getLaunchIntentForPackage(app.packageName)
                                            if (intent != null) ctx.startActivity(intent)
                                        } catch (_: Exception) { }
                                    } else {
                                        onInstallClick(app)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        hasUpdate -> Color(0xFFFF7F7F)
                                        isInstalled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                ),
                                modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = when {
                                        hasUpdate -> "Update"
                                        isInstalled -> "Open"
                                        else -> "Install"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    is DownloadState.Progress -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onCancelDownload(app.packageName) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel download", modifier = Modifier.size(16.dp))
                            }
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                                CircularProgressIndicator(
                                    progress = { downloadState.percentage },
                                    modifier = Modifier.size(36.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "${(downloadState.percentage * 100).toInt()}%",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    is DownloadState.Downloaded -> {
                        Button(
                            onClick = { onInstallClick(app) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    is DownloadState.Completed -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                    is DownloadState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                downloadState.message.take(25),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    AppDescriptionDialog(
        showDescription = showLongPressMenu,
        onDismiss = { showLongPressMenu = false },
        app = app,
        installedVersion = installedVersion,
        installedVerName = installedVerName,
        onInstallClick = onInstallClick,
        onLoadDescription = onLoadDescription,
        onUninstall = onUninstall,
        longPress = true
    )

    AppDescriptionDialog(
        showDescription = showDescription,
        onDismiss = { showDescription = false },
        app = app,
        installedVersion = installedVersion,
        installedVerName = installedVerName,
        onInstallClick = onInstallClick,
        onLoadDescription = onLoadDescription,
        onUninstall = onUninstall
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UpdatesTab(
    allApps: List<AppItem>,
    appsWithUpdates: List<AppItem>,
    installedStoreApps: List<AppItem>,
    downloadStates: Map<String, DownloadState>,
    installedPackages: Map<String, Int>,
    onUpdateClick: (AppItem) -> Unit,
    onUpdateAll: () -> Unit = {},
    onCancelAll: () -> Unit = {},
    onUninstall: (String) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onCheckUpdates: () -> Unit,
    isSyncing: Boolean = false,
    onRefresh: () -> Unit = {},
    onLoadDescription: ((AppItem, (String) -> Unit) -> Unit)? = null
) {
    val iconBorderColor = if (isSystemInDarkTheme()) Color(0xFFE5E4E2) else Color(0xFF333333)
    RefreshPillBox(
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Manage Apps", 
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${appsWithUpdates.size} update${if (appsWithUpdates.size != 1) "s" else ""} available • ${installedStoreApps.size} installed",
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onCheckUpdates,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.wrapContentSize()
            ) {
                Text(
                    text = "Check for updates", 
                    fontSize = 12.sp, 
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Section: Available Updates
            if (appsWithUpdates.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "All applications are up to date.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Available updates",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        if (appsWithUpdates.size > 1) {
                            val isAnyUpdating = appsWithUpdates.any { downloadStates[it.packageName] !is DownloadState.Idle && downloadStates[it.packageName] != null }
                            TextButton(onClick = if (isAnyUpdating) onCancelAll else onUpdateAll) {
                                Icon(
                                    if (isAnyUpdating) Icons.Default.Close else Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isAnyUpdating) "Cancel all" else "Update all", fontSize = 12.sp)
                            }
                        }
                    }
                }
                items(appsWithUpdates, key = { "update_${it.id}" }) { app ->
                    val state = downloadStates[app.packageName]
                    val installedVer = installedPackages[app.packageName] ?: 0
                    val ctx = LocalContext.current
                    val installedVerName = try {
                        ctx.packageManager.getPackageInfo(app.packageName, 0).versionName ?: app.versionName
                    } catch (_: Exception) { app.versionName }
                    var showAppDesc by remember { mutableStateOf(false) }
                    var showAppDescLong by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .combinedClickable(
                                                onClick = { showAppDesc = true },
                                                onLongClick = { showAppDescLong = true }
                                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(app.iconUrl)
                                    .memoryCacheKey("icon_${app.packageName}_${app.versionCode}")
                                    .diskCacheKey("icon_${app.packageName}_${app.versionCode}")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(0.5.dp, iconBorderColor, RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    fontSize = 15.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (app.platform != "Windows") {
                                    Text(
                                        text = app.packageName,
                                        fontSize = 11.sp,
                                        lineHeight = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                Text(
                                    text = "v${installedVerName} → v${app.versionName} • ${formatFileSize(app.size)}",
                                    fontSize = versionFontSize("v${installedVerName} → v${app.versionName}"),
                                    lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            when (state) {
                                is DownloadState.Progress -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { onCancelDownload(app.packageName) }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Cancel download", modifier = Modifier.size(16.dp))
                                        }
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                                            CircularProgressIndicator(
                                                progress = { state.percentage },
                                                modifier = Modifier.size(36.dp),
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = "${(state.percentage * 100).toInt()}%",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                is DownloadState.Downloaded -> {
                                    val hasUpdate = installedVer > 0 && app.versionCode > installedVer
                                    Button(
                                        onClick = { onUpdateClick(app) },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (hasUpdate) Color(0xFFFF7F7F) else MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) { Text(if (hasUpdate) "Update" else "Install", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }
                                else -> {
                                    Button(
                                        onClick = { onUpdateClick(app) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7F7F)),
                                        modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) { Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                    AppDescriptionDialog(
                        showDescription = showAppDesc,
                        onDismiss = { showAppDesc = false },
                        app = app,
                        installedVersion = installedVer,
                        installedVerName = installedVerName,
                        onInstallClick = { onUpdateClick(it) },
                        onLoadDescription = onLoadDescription,
                        onUninstall = onUninstall
                    )
                    AppDescriptionDialog(
                        showDescription = showAppDescLong,
                        onDismiss = { showAppDescLong = false },
                        app = app,
                        installedVersion = installedVer,
                        installedVerName = installedVerName,
                        onInstallClick = { onUpdateClick(it) },
                        onLoadDescription = onLoadDescription,
                        onUninstall = onUninstall,
                        longPress = true
                    )
                }
            }

            // Section: Installed Apps
            if (installedStoreApps.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Installed Apps",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(installedStoreApps, key = { "installed_${it.id}" }) { app ->
                    val installedVer = installedPackages[app.packageName] ?: 0
                    val ctx = LocalContext.current
                    val installedVerName = try {
                        ctx.packageManager.getPackageInfo(app.packageName, 0).versionName ?: ""
                    } catch (_: Exception) { "" }
                    val hasUpdate = ctx.isUpdateAvailable(app, installedVer, installedVerName)
                    var showAppDesc by remember { mutableStateOf(false) }
                    var showAppDescLong by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .combinedClickable(
                                                onClick = { showAppDesc = true },
                                                onLongClick = { showAppDescLong = true }
                                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (hasUpdate) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(app.iconUrl)
                                    .memoryCacheKey("icon_${app.packageName}_${app.versionCode}")
                                    .diskCacheKey("icon_${app.packageName}_${app.versionCode}")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(0.5.dp, iconBorderColor, RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    fontSize = 14.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (app.platform != "Windows") {
                                    Text(
                                        text = app.packageName,
                                        fontSize = 11.sp,
                                        lineHeight = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                Text(
                                    text = if (hasUpdate) "v${installedVerName} → v${app.versionName} • ${formatFileSize(app.size)}" else "v${app.versionName} • ${formatFileSize(app.size)}",
                                    fontSize = versionFontSizeNarrow(if (hasUpdate) "v${installedVerName} → v${app.versionName} • ${formatFileSize(app.size)}" else "v${app.versionName} • ${formatFileSize(app.size)}"),
                                    lineHeight = 13.sp,
                                    color = if (hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onUninstall(app.packageName) },
                                modifier = Modifier.size(45.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Uninstall",
                                    modifier = Modifier.size(25.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    if (hasUpdate) onUpdateClick(app)
                                    else try {
                                        val intent = ctx.packageManager.getLaunchIntentForPackage(app.packageName)
                                        if (intent != null) ctx.startActivity(intent)
                                    } catch (_: Exception) { }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (hasUpdate) Color(0xFFFF7F7F) else MaterialTheme.colorScheme.primary),
                                modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text(if (hasUpdate) "Update" else "Open", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    AppDescriptionDialog(
                        showDescription = showAppDesc,
                        onDismiss = { showAppDesc = false },
                        app = app,
                        installedVersion = installedVer,
                        installedVerName = installedVerName,
                        onInstallClick = { onUpdateClick(it) },
                        onLoadDescription = onLoadDescription,
                        onUninstall = onUninstall
                    )
                    AppDescriptionDialog(
                        showDescription = showAppDescLong,
                        onDismiss = { showAppDescLong = false },
                        app = app,
                        installedVersion = installedVer,
                        installedVerName = installedVerName,
                        onInstallClick = { onUpdateClick(it) },
                        onLoadDescription = onLoadDescription,
                        onUninstall = onUninstall,
                        longPress = true
                    )
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsTab(
    viewModel: StoreViewModel,
    storeApps: List<AppItem>,
    downloadStates: Map<String, DownloadState>,
    installedPackages: Map<String, Int>,
    isSyncing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    val iconBorderColor = if (isSystemInDarkTheme()) Color(0xFFE5E4E2) else Color(0xFF333333)
    var refreshTrigger by remember { mutableStateOf(0) }

    val downloadedFiles = remember(refreshTrigger, downloadStates) {
        val files = mutableListOf<DownloadedAppInfo>()
        try {
            val app = context.applicationContext as Application
            val dirs = listOfNotNull(
                app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                app.cacheDir,
                app.externalCacheDir
            )
            for (dir in dirs) {
                dir.listFiles()?.forEach { file ->
                    val isApk = file.name.endsWith(".apk")
                    val isTmp = file.name.endsWith(".tmp")
                    if (isApk || isTmp) {
                        val ext = if (isApk) ".apk" else ".tmp"
                        val nameNoExt = file.name.removeSuffix(ext)
                        val parts = nameNoExt.split("_")
                        // Match by original Drive filename first, fall back to legacy pkg_verCode parsing
                        val storeByFile = storeApps.find { it.fileName.isNotBlank() && (it.fileName == file.name || it.fileName == nameNoExt) }
                        if (parts.size >= 2 || storeByFile != null) {
                            val pkg = storeByFile?.packageName ?: parts.dropLast(1).joinToString("_")
                            val verCode = parts.last().toIntOrNull() ?: storeByFile?.versionCode ?: 0
                            val storeApp = storeByFile ?: storeApps.find { it.packageName == pkg }
                            var apkName = storeApp?.name?.takeIf { it.isNotBlank() }
                            var apkIcon = storeApp?.iconUrl
                            if (apkName.isNullOrBlank() && isApk) {
                                try {
                                    val pkgInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, android.content.pm.PackageManager.GET_ACTIVITIES)
                                    val appInfo = pkgInfo?.applicationInfo
                                    if (appInfo != null) {
                                        appInfo.sourceDir = file.absolutePath
                                        appInfo.publicSourceDir = file.absolutePath
                                        apkName = appInfo.loadLabel(context.packageManager)?.toString()?.takeIf { it.isNotBlank() }
                                    }
                                } catch (_: Exception) { }
                            }
                            files.add(DownloadedAppInfo(
                                name = apkName ?: pkg,
                                packageName = pkg,
                                versionCode = verCode,
                                filePath = file.absolutePath,
                                fileSize = file.length(),
                                lastModified = viewModel.getDownloadTime(pkg).coerceAtLeast(file.lastModified()),
                                iconUrl = apkIcon,
                                versionName = storeApp?.versionName
                            ))
                        }
                    }
                }
            }
            // Also include any items that have an active download state but no file yet
            downloadStates.forEach { (pkg, state) ->
                if (state is DownloadState.Progress && files.none { it.packageName == pkg }) {
                    val storeApp = storeApps.find { it.packageName == pkg }
                    files.add(DownloadedAppInfo(
                        name = storeApp?.name ?: pkg,
                        packageName = pkg,
                        versionCode = storeApp?.versionCode ?: 1,
                        filePath = "",
                        fileSize = (state as DownloadState.Progress).totalBytes,
                        lastModified = System.currentTimeMillis(),
                        iconUrl = storeApp?.iconUrl,
                        versionName = storeApp?.versionName
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadsTab", "Scan failed", e)
        }
        files.distinctBy { it.packageName }.filter { it.packageName != "com.mahmuduls.appstore" }.sortedBy { it.name.lowercase() }
    }

    RefreshPillBox(
        isRefreshing = isSyncing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Downloads", fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${downloadedFiles.size} APK${if (downloadedFiles.size != 1) "s" else ""} downloaded",
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (downloadedFiles.isNotEmpty()) {
                    TextButton(onClick = {
                        viewModel.deleteAllDownloads(onDone = { refreshTrigger++ })
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete all", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete all", fontSize = 13.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (downloadedFiles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "No downloads",
                            modifier = Modifier.size(68.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No downloaded APKs yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Download apps from the Home tab to see them here",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(downloadedFiles, key = { it.filePath }) { info ->
                val storeApp = storeApps.find { it.packageName == info.packageName }
                var showDlDesc by remember { mutableStateOf(false) }
                var showDlDescLong by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = { if (storeApp != null) showDlDesc = true },
                            onLongClick = { if (storeApp != null) showDlDescLong = true }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (info.iconUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(info.iconUrl)
                                    .memoryCacheKey("icon_${info.packageName}_${info.versionCode}")
                                    .diskCacheKey("icon_${info.packageName}_${info.versionCode}")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(0.5.dp, iconBorderColor, RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            val apkIconBitmap = remember(info.filePath) {
                                try {
                                    val apkFile = java.io.File(info.filePath)
                                    if (!apkFile.exists() || !info.filePath.endsWith(".apk", ignoreCase = true)) null
                                    else {
                                        val pkgInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, android.content.pm.PackageManager.GET_ACTIVITIES)
                                        val appInfo = pkgInfo?.applicationInfo ?: return@remember null
                                        appInfo.sourceDir = apkFile.absolutePath
                                        appInfo.publicSourceDir = apkFile.absolutePath
                                        appInfo.loadIcon(context.packageManager).toBitmap(192, 192).asImageBitmap()
                                    }
                                } catch (_: Exception) { null }
                            }
                            if (apkIconBitmap != null) {
                                Image(
                                    bitmap = apkIconBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(0.5.dp, iconBorderColor, RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = info.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val dlPlatform = storeApps.find { it.packageName == info.packageName }?.platform
                            if (dlPlatform != "Windows") {
                                Text(
                                    text = info.packageName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (info.versionCode > 0) {
                                val installedVer = remember { try { context.packageManager.getPackageInfo(info.packageName, 0).versionName } catch (_: Exception) { null } }
                                val installedCode = remember { try { context.packageManager.getPackageInfo(info.packageName, 0).let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it).toInt() } } catch (_: Exception) { 0 } }
                                val driveApp = storeApps.find { it.packageName == info.packageName }
                                val driveNewer = driveApp != null && driveApp.versionCode > maxOf(info.versionCode, installedCode)
                                val verText = when {
                                    driveNewer -> "v${installedVer ?: info.versionName ?: info.versionCode} → v${driveApp!!.versionName}"
                                    installedVer != null -> "v$installedVer"
                                    driveApp != null -> "v${driveApp.versionName}"
                                    else -> "v${info.versionName ?: info.versionCode}"
                                }
                                val sizeSuffix = if (info.fileSize > 0) " • ${formatFileSize(info.fileSize)}" else ""
                                Text(
                                    text = verText + sizeSuffix,
                                    fontSize = versionFontSizeNarrow(verText + sizeSuffix),
                                    lineHeight = 13.sp,
                                    color = if (driveNewer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (info.lastModified > 0) {
                                val dateFmt = remember { java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", java.util.Locale.US) }
                                Text(
                                    text = "Downloaded: ${dateFmt.format(java.util.Date(info.lastModified))}",
                                    fontSize = 8.sp,
                                    lineHeight = 8.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val state = downloadStates[info.packageName]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state is DownloadState.Progress) {
                                IconButton(
                                    onClick = { viewModel.cancelDownload(info.packageName); refreshTrigger++ },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel download", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        viewModel.cancelDownload(info.packageName)
                                        if (info.filePath.isNotEmpty()) java.io.File(info.filePath).delete()
                                        viewModel.deleteDownloadedFile(info.packageName)
                                        refreshTrigger++
                                    },
                                    modifier = Modifier.size(45.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(25.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            when (state) {
                                is DownloadState.Progress -> {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            progress = { state.percentage },
                                            modifier = Modifier.size(36.dp),
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "${(state.percentage * 100).toInt()}%",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                is DownloadState.Completed -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                }
                                is DownloadState.Downloaded -> {
                                    val isInstalled = installedPackages.containsKey(info.packageName)
                                    val driveApp = storeApps.find { it.packageName == info.packageName }
                                    val driveCode = try { context.packageManager.getPackageInfo(info.packageName, 0).let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it).toInt() } } catch (_: Exception) { 0 }
                                    val driveNewer = isInstalled && driveApp != null && driveApp.versionCode > driveCode
                                    if (driveNewer) {
                                        Button(
                                            onClick = { val app = driveApp ?: return@Button; viewModel.startDownload(app) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7F7F)),
                                            modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else if (isInstalled) {
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = context.packageManager.getLaunchIntentForPackage(info.packageName)
                                                    if (intent != null) context.startActivity(intent)
                                                } catch (_: Exception) { }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = { viewModel.installDownloadedApp(info.packageName) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                else -> {
                                    val isInstalled = installedPackages.containsKey(info.packageName)
                                    val driveApp = storeApps.find { it.packageName == info.packageName }
                                    val driveCode = try { context.packageManager.getPackageInfo(info.packageName, 0).let { androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(it).toInt() } } catch (_: Exception) { 0 }
                                    val driveNewer = isInstalled && driveApp != null && driveApp.versionCode > driveCode
                                    if (driveNewer) {
                                        Button(
                                            onClick = { val app = driveApp ?: return@Button; viewModel.startDownload(app) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7F7F)),
                                            modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else if (isInstalled) {
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = context.packageManager.getLaunchIntentForPackage(info.packageName)
                                                    if (intent != null) context.startActivity(intent)
                                                } catch (_: Exception) { }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                val app = storeApps.find { it.packageName == info.packageName } ?: return@Button
                                                viewModel.startDownload(app)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.height(40.dp).widthIn(min = 75.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (storeApp != null) {
                        AppDescriptionDialog(
                            showDescription = showDlDesc,
                            onDismiss = { showDlDesc = false },
                            app = storeApp,
                            installedVersion = installedPackages[storeApp.packageName],
                            installedVerName = try { context.packageManager.getPackageInfo(storeApp.packageName, 0).versionName ?: "" } catch (_: Exception) { "" },
                            onInstallClick = { viewModel.startDownload(it) },
                            onLoadDescription = { a, cb -> viewModel.lazyLoadDescription(a, cb) }
                        )
                        AppDescriptionDialog(
                            showDescription = showDlDescLong,
                            onDismiss = { showDlDescLong = false },
                            app = storeApp,
                            installedVersion = installedPackages[storeApp.packageName],
                            installedVerName = try { context.packageManager.getPackageInfo(storeApp.packageName, 0).versionName ?: "" } catch (_: Exception) { "" },
                            onInstallClick = { viewModel.startDownload(it) },
                            onLoadDescription = { a, cb -> viewModel.lazyLoadDescription(a, cb) },
                            longPress = true
                        )
                    }
                }
            }
        }
    }
    }
}

private fun isVersionNewer(driveVersion: String, installedVersion: String, driveCode: Int = 0, installedCode: Int = 0, driveModifiedTime: Long = 0L, lastSeenTime: Long = 0L, drivePatchVersion: String = "", installedPatchVersion: String = ""): Boolean {
    if (driveCode != installedCode) return driveCode > installedCode
    val driveParts = driveVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val installedParts = installedVersion.split(".").map { it.toIntOrNull() ?: 0 }
    val hasHuge = { parts: List<Int> -> parts.any { it >= 10000 } }
    if (!hasHuge(driveParts) && !hasHuge(installedParts)) {
        val maxLen = maxOf(driveParts.size, installedParts.size)
        for (i in 0 until maxLen) {
            val d = driveParts.getOrElse(i) { 0 }
            val inst = installedParts.getOrElse(i) { 0 }
            if (d != inst) return d > inst
        }
    }
    if (drivePatchVersion.isNotEmpty() || installedPatchVersion.isNotEmpty()) {
        val drivePatchParts = drivePatchVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val installedPatchParts = installedPatchVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val maxPatchLen = maxOf(drivePatchParts.size, installedPatchParts.size)
        for (i in 0 until maxPatchLen) {
            val d = drivePatchParts.getOrElse(i) { 0 }
            val inst = installedPatchParts.getOrElse(i) { 0 }
            if (d != inst) return d > inst
        }
    }
    return false
}

private fun Context.lastSeenModTime(pkg: String, modTime: Long): Long {
    val prefs = getSharedPreferences("drive_store_prefs", android.content.Context.MODE_PRIVATE)
    val json = prefs.getString("seen_mod_times", "{}") ?: "{}"
    return try {
        val obj = org.json.JSONObject(json)
        if (obj.has(pkg)) obj.optLong(pkg, 0L) else { obj.put(pkg, modTime); prefs.edit().putString("seen_mod_times", obj.toString()).apply(); modTime }
    } catch (_: Exception) { 0L }
}

private fun Context.isUpdateAvailable(app: AppItem, installedVer: Int, installedVerName: String): Boolean {
    if (installedVerName.isEmpty()) return false
    if (!app.updateCheck) return false
    val prefs = getSharedPreferences("app_store_prefs", android.content.Context.MODE_PRIVATE)
    if (!prefs.getBoolean("update_check_enabled", true)) return false
    val lastSeen = lastSeenModTime(app.packageName, app.driveModifiedTime)
    val installedPatch = getInstalledPatchVersion(app.packageName)
    return isVersionNewer(app.versionName, installedVerName, app.versionCode, installedVer, app.driveModifiedTime, lastSeen, app.patchVersion, installedPatch)
}

private fun Context.getInstalledPatchVersion(pkg: String): String {
    val prefs = getSharedPreferences("drive_store_prefs", android.content.Context.MODE_PRIVATE)
    val json = prefs.getString("installed_patch_versions", "{}") ?: "{}"
    return try {
        org.json.JSONObject(json).optString(pkg, "")
    } catch (_: Exception) { "" }
}

private fun unescapeMarkdown(text: String): String {
    return text.replace(Regex("""\\(.)""")) { it.groupValues[1] }
}

private fun AnnotatedString.Builder.appendTextWithUrls(text: String) {
    val urlPattern = "https?://[\\w\\d\\-._~:/?#\\[\\]@!$&'()*+,;=%]+".toRegex()
    // Markdown links: [Label](URL) — render Label as the clickable, styled link text
    val mdLinkPattern = Regex("""\[([^\]]+)\]\((https?://[^)\s]+)\)""")
    var segmentStart = 0
    for (md in mdLinkPattern.findAll(text)) {
        if (md.range.first > segmentStart) {
            appendBareUrls(text.substring(segmentStart, md.range.first), urlPattern)
        }
        val label = md.groupValues[1]
        val url = md.groupValues[2]
        pushStringAnnotation("URL", url)
        withStyle(SpanStyle(color = Color(0xFF1976D2), textDecoration = TextDecoration.Underline)) {
            append(label)
        }
        pop()
        segmentStart = md.range.last + 1
    }
    if (segmentStart < text.length) {
        appendBareUrls(text.substring(segmentStart), urlPattern)
    }
}

private fun AnnotatedString.Builder.appendBareUrls(text: String, urlPattern: Regex) {
    var lastIdx = 0
    for (match in urlPattern.findAll(text)) {
        if (match.range.first > lastIdx) {
            append(text.substring(lastIdx, match.range.first))
        }
        pushStringAnnotation("URL", match.value)
        withStyle(SpanStyle(color = Color(0xFF1976D2), textDecoration = TextDecoration.Underline)) {
            append(match.value)
        }
        pop()
        lastIdx = match.range.last + 1
    }
    if (lastIdx < text.length) append(text.substring(lastIdx))
}

@Composable
fun AppDescriptionDialog(
    showDescription: Boolean,
    onDismiss: () -> Unit,
    app: AppItem,
    installedVersion: Int?,
    installedVerName: String?,
    onInstallClick: (AppItem) -> Unit,
    onLoadDescription: ((AppItem, (String) -> Unit) -> Unit)? = null,
    onUninstall: ((String) -> Unit)? = null,
    longPress: Boolean = false,
    forced: Boolean = false,
    downloadState: DownloadState? = null,
    onForcedUpdateClick: (() -> Unit)? = null
) {
    if (showDescription) {
        val isInstalled = installedVersion != null
        val ctx = LocalContext.current
        val iconBorderColor = if (isSystemInDarkTheme()) Color(0xFFE5E4E2) else Color(0xFF333333)
        val hasUpdate = isInstalled && installedVerName?.let { ctx.isUpdateAvailable(app, installedVersion!!, it) } == true
        var descText by remember { mutableStateOf("") }
        val settingsLines = remember(descText) {
            val lines = descText.split("\n")
            val settingsIdx = lines.indexOfFirst { it.trim().startsWith("## ") && it.contains("Settings", ignoreCase = true) }
            if (settingsIdx >= 0 && settingsIdx + 1 < lines.size) {
                lines.drop(settingsIdx + 1).joinToString("\n").trim()
            } else ""
        }
        val linkLines = remember(descText) {
            val lines = descText.split("\n")
            val linkIdx = lines.indexOfFirst { it.trim().startsWith("## ") && it.contains("Link", ignoreCase = true) }
            if (linkIdx >= 0 && linkIdx + 1 < lines.size) {
                lines.drop(linkIdx + 1).joinToString("\n").trim()
            } else ""
        }
        val firstLinkUrl = remember(linkLines) {
            // Prefer a markdown link [Label](URL) — extract URL without the closing paren
            val mdLink = Regex("""\[[^\]]+\]\((https?://[^)\s]+)\)""").find(linkLines)
            mdLink?.groupValues?.get(1)
                ?: Regex("""https?://[\w\d\-._~:/?#\[\]@!$&'()*+,;=%]+""").find(linkLines)?.value
        }
        LaunchedEffect(showDescription) {
            if (showDescription) {
                val d = app.description
                if (d.startsWith("__MD_ID:")) {
                    descText = "Loading description..."
                    onLoadDescription?.invoke(app) { content -> descText = content }
                } else {
                    descText = d.ifEmpty { "No description available." }
                }
            }
        }
        Dialog(
            onDismissRequest = if (forced) ({ }) else onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .fillMaxHeight(0.7f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(app.iconUrl)
                                .memoryCacheKey("icon_${app.packageName}_${app.versionCode}")
                                .diskCacheKey("icon_${app.packageName}_${app.versionCode}")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(0.5.dp, iconBorderColor, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
                            Text(
                                if (hasUpdate) "v${installedVerName} → v${app.versionName}"
                                else "v${app.versionName}",
                                fontSize = 12.sp,
                                lineHeight = 14.sp,
                                color = if (hasUpdate) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (app.driveModifiedTime > 0L) {
                                val dateStr = remember(app.driveModifiedTime) {
                                    java.text.SimpleDateFormat("MMM dd, yyyy  hh:mm a", java.util.Locale.US).format(java.util.Date(app.driveModifiedTime))
                                }
                                Text(
                                    text = "Updated: $dateStr",
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        val scrollState = rememberScrollState()
                        val uriHandler = LocalUriHandler.current
                        val annotatedString = buildAnnotatedString {
                            val lines = descText.split("\n")
                            val headingColor = MaterialTheme.colorScheme.primary
                            var lastRenderedAsHeading = false
                            for ((lineIndex, line) in lines.withIndex()) {
                                val trimmed = line.trim()
                                if (trimmed.isEmpty()) {
                                    lastRenderedAsHeading = false
                                    continue
                                }
                                val raw = line.trimStart()
                                val headingLevel = when {
                                    raw.startsWith("### ") -> 3
                                    raw.startsWith("## ") -> 2
                                    raw.startsWith("# ") -> 1
                                    else -> 0
                                }
                                val isBullet = raw.startsWith("* ") || raw.startsWith("- ") || raw.matches(Regex("^\\d+\\.\\s.*"))
                                if (lineIndex > 0) append("\n")
                                if (headingLevel > 0 && lineIndex > 0) append("\n")
                                val bulletContent = when {
                                    raw.startsWith("* ") -> raw.removePrefix("* ")
                                    raw.startsWith("- ") -> raw.removePrefix("- ")
                                    raw.matches(Regex("^\\d+\\.\\s.*")) -> raw.replace(Regex("^\\d+\\.\\s"), "")
                                    else -> raw
                                }
                                val content = unescapeMarkdown(if (headingLevel > 0) raw.removePrefix("#".repeat(headingLevel) + " ") else if (isBullet) bulletContent else raw)
                                val baseStyle = when (headingLevel) {
                                    1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = headingColor)
                                    2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = headingColor)
                                    3 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = headingColor)
                                    else -> SpanStyle()
                                }
                                val prefix = when {
                                    isBullet && (raw.startsWith("* ") || raw.startsWith("- ")) -> "  \u2022 "
                                    isBullet && raw.matches(Regex("^\\d+\\.\\s.*")) -> {
                                        val num = raw.replace(Regex("[^0-9]"), "")
                                        "  $num. "
                                    }
                                    else -> ""
                                }
                                if (prefix.isNotEmpty()) append(prefix)
                                if (content.contains("**")) {
                                    val parts = content.split("**")
                                    for ((i, part) in parts.withIndex()) {
                                        if (part.isEmpty()) continue
                                        if (i % 2 == 1) {
                                            withStyle(baseStyle.merge(SpanStyle(fontWeight = FontWeight.Bold))) {
                                                appendTextWithUrls(part)
                                            }
                                        } else {
                                            withStyle(baseStyle) {
                                                appendTextWithUrls(part)
                                            }
                                        }
                                    }
                                } else {
                                    withStyle(baseStyle) {
                                        appendTextWithUrls(content)
                                    }
                                }
                                lastRenderedAsHeading = headingLevel > 0
                                if (headingLevel > 0) append("\n")
                            }
                        }
                        Column(
                            modifier = Modifier.verticalScroll(scrollState)
                        ) {
                            SelectionContainer {
                                ClickableText(
                                    text = annotatedString,
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 22.sp
                                    ),
                                    onClick = { offset ->
                                        annotatedString.getStringAnnotations("URL", offset, offset)
                                            .firstOrNull()?.let { annotation ->
                                                try { uriHandler.openUri(annotation.item) } catch (_: Exception) { }
                                            }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (forced) {
                        val isDownloadingState = downloadState is DownloadState.Progress || downloadState is DownloadState.Completed
                        val dialogDarkMode = isSystemInDarkTheme()
                        Button(
                            onClick = { onForcedUpdateClick?.invoke() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    isDownloadingState && dialogDarkMode -> Color(0xFF424242)
                                    isDownloadingState -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> Color(0xFFFF7F7F)
                                },
                                contentColor = if (isDownloadingState && !dialogDarkMode)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            // Downloading states: white text on dark gray in OLED, black text on light gray in light mode
                            val downloadingContentColor = if (dialogDarkMode) Color.White else Color.Black
                            // Red action buttons: black text in light mode, white in dark/OLED
                            val actionContentColor = if (dialogDarkMode) Color.White else Color.Black
                            when (val st = downloadState) {
                                is DownloadState.Progress -> {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = downloadingContentColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Downloading... ${(st.percentage * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = downloadingContentColor)
                                }
                                is DownloadState.Downloaded -> {
                                    Icon(Icons.Default.GetApp, contentDescription = null, modifier = Modifier.size(16.dp), tint = actionContentColor)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Install Update", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = actionContentColor)
                                }
                                is DownloadState.Completed -> {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = downloadingContentColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Installing...", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = downloadingContentColor)
                                }
                                else -> {
                                    Icon(Icons.Default.SystemUpdateAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = actionContentColor)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Update Available", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = actionContentColor)
                                }
                            }
                        }
                    } else {
                        if (settingsLines.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val cleanText = settingsLines.replace("\\_", "_")
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Settings", cleanText))
                                    android.widget.Toast.makeText(ctx, "Settings copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Settings", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (firstLinkUrl != null) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(firstLinkUrl))
                                        ctx.startActivity(intent)
                                    } catch (_: Exception) { }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open Link", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (app.driveFileId.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    val overview = descText.lines().dropWhile { !it.trim().startsWith("## Overview") }.drop(1).takeWhile { !it.trim().startsWith("## ") }.joinToString(" ").trim().replace("**", "").replace("* ", "").replace("- ", "")
                                    val dlLink = if (app.driveFileId.startsWith("manifest_")) app.downloadUrl else "https://drive.google.com/uc?export=download&id=${app.driveFileId}"
                                    val shareText = "Check out ${app.name} — ${overview.take(200)}\n\nDownload: $dlLink"
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                    }
                                    ctx.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${app.name}"))
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share App", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (longPress && isInstalled) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onUninstall?.invoke(app.packageName)
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Uninstall", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onInstallClick(app)
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (hasUpdate) Color(0xFFFF7F7F) else MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(
                                        if (hasUpdate) Icons.Default.SystemUpdateAlt else Icons.Default.Refresh,
                                        contentDescription = null, modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (hasUpdate) "Update" else "Reinstall",
                                        fontWeight = FontWeight.Bold, fontSize = 13.sp
                                    )
                                }
                            }
                        } else if (app.platform != "Windows") {
                            Button(
                                onClick = {
                                    onDismiss()
                                    when {
                                        hasUpdate -> onInstallClick(app)
                                        isInstalled -> {
                                            try {
                                                val intent = ctx.packageManager.getLaunchIntentForPackage(app.packageName)
                                                if (intent != null) ctx.startActivity(intent)
                                            } catch (_: Exception) { }
                                        }
                                        else -> onInstallClick(app)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        hasUpdate -> Color(0xFFFF7F7F)
                                        isInstalled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = when {
                                        hasUpdate -> "Update"
                                        isInstalled -> "Open"
                                        else -> "Install"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun packageNameLabel(pkg: String): String {
    val parts = pkg.split(".")
    return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else pkg
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes.toFloat() / 1024f)
        else -> "%.1f MB".format(bytes.toFloat() / (1024f * 1024f))
    }
}

private fun versionFontSize(text: String): androidx.compose.ui.unit.TextUnit {
    val len = text.length
    return when {
        len <= 10 -> 11.sp
        len <= 20 -> 10.sp
        len <= 30 -> 9.sp
        else -> 9.sp
    }
}

// Tighter thresholds for narrow cards with an extra button (Installed Apps, Downloads)
private fun versionFontSizeNarrow(text: String): androidx.compose.ui.unit.TextUnit {
    val len = text.length
    return when {
        len <= 8 -> 11.sp
        len <= 16 -> 10.sp
        len <= 24 -> 9.sp
        else -> 9.sp
    }
}

private data class DownloadedAppInfo(
    val name: String,
    val packageName: String,
    val versionCode: Int,
    val filePath: String,
    val fileSize: Long,
    val lastModified: Long,
    val iconUrl: String? = null,
    val versionName: String? = null
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    viewModel: StoreViewModel,
    authViewModel: AuthViewModel,
    onForceSync: () -> Unit,
    isSyncing: Boolean,
    onStartSelfUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val useDeviceTheme by viewModel.useDeviceTheme.collectAsState()
    val totalUsers by viewModel.totalUsers.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val iconBorderColor = if (isSystemInDarkTheme()) Color(0xFFE5E4E2) else Color(0xFF333333)

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "Notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Update check always runs: force-enable and ask notification permission once
    LaunchedEffect(Unit) {
        viewModel.setAutoUpdateCheck(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val storePrefs = context.getSharedPreferences("drive_store_prefs", android.content.Context.MODE_PRIVATE)
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted && !storePrefs.getBoolean("notif_perm_asked", false)) {
                storePrefs.edit().putBoolean("notif_perm_asked", true).apply()
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val boxColor = MaterialTheme.colorScheme.surface

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.padding(4.dp)) {
                Text("Settings", fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Manage your account, profile, app preferences, and theme.", fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Avatar Card
        item {
            val currentUser by authViewModel.currentUser.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = boxColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D47A1)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = (currentUser?.displayName ?: currentUser?.email ?: "U").take(1).uppercase()
                        Text(initial, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(currentUser?.displayName ?: "Guest User", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(currentUser?.email ?: "Not signed in", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Section: General
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = boxColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("GENERAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Device Theme Toggle
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = "Device Theme", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Device Theme", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Switch(checked = useDeviceTheme, onCheckedChange = { enabled -> viewModel.setUseDeviceTheme(enabled); if (enabled) viewModel.setDarkMode(false) })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Oled Theme Toggle
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DarkMode, contentDescription = "Dark Theme", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("OLED Theme", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Switch(checked = isDarkMode, onCheckedChange = { enabled -> viewModel.setDarkMode(enabled); if (enabled) viewModel.setUseDeviceTheme(false) })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Auto-Delete Downloads Toggle
                    val autoDeleteDownloads by viewModel.autoDeleteDownloads.collectAsState()
                    val autoDeleteDays by viewModel.autoDeleteDays.collectAsState()
                    val showDeleteDaysPicker = remember { mutableStateOf(false) }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Auto-delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Delete Old Downloads", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    if (autoDeleteDownloads) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Delete files older than ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                            Text(if (autoDeleteDays == 1) "1 day" else "$autoDeleteDays days", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { showDeleteDaysPicker.value = true })
                                        }
                                    }
                                }
                            }
                            Switch(checked = autoDeleteDownloads, onCheckedChange = { viewModel.setAutoDeleteDownloads(it) })
                        }
                        if (showDeleteDaysPicker.value) {
                            LaunchedEffect(showDeleteDaysPicker.value) {
                                val options = (1..30).toList()
                                android.app.AlertDialog.Builder(context).setTitle("Delete files older than").setItems(options.map { if (it == 1) "1 day" else "$it days" }.toTypedArray()) { _, which -> showDeleteDaysPicker.value = false; viewModel.setAutoDeleteDays(options[which]) }.setOnDismissListener { showDeleteDaysPicker.value = false }.show()
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Auto Updates Toggle
                    val autoDownloadUpdates by viewModel.autoDownloadUpdates.collectAsState()
                    var checkIntervalMin by remember { mutableStateOf(viewModel.getCheckIntervalMinutes()) }
                    val showIntervalPicker = remember { mutableStateOf(false) }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Download, contentDescription = "Auto Updates", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Updates", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Automatically download updates in the background and notify you when they are ready to install.",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    textAlign = TextAlign.Justify,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                val retryAnnotated = buildAnnotatedString {
                                    append("Retry every ")
                                    pushStringAnnotation("interval", "pick")
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                        append(com.example.util.AutoDownloadHelper.formatInterval(checkIntervalMin))
                                    }
                                    pop()
                                    append(" if the download fails.")
                                }
                                ClickableText(
                                    text = retryAnnotated,
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        textAlign = TextAlign.Justify,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    ),
                                    onClick = { offset ->
                                        retryAnnotated.getStringAnnotations("interval", offset, offset)
                                            .firstOrNull()?.let { showIntervalPicker.value = true }
                                    }
                                )
                            }
                        }
                        Switch(checked = autoDownloadUpdates, onCheckedChange = { viewModel.setAutoDownloadUpdates(it) })
                    }
                    if (showIntervalPicker.value) {
                        LaunchedEffect(showIntervalPicker.value) {
                            val options = listOf(5, 10, 15, 30, 60, 120, 180, 240, 360, 480, 720, 1440)
                            android.app.AlertDialog.Builder(context).setTitle("Check interval").setItems(options.map { com.example.util.AutoDownloadHelper.formatInterval(it) }.toTypedArray()) { _, which -> val m = options[which]; showIntervalPicker.value = false; checkIntervalMin = m; viewModel.setCheckIntervalMinutes(m) }.setOnDismissListener { showIntervalPicker.value = false }.show()
                        }
                    }
                }
            }
        }

        // Section: Account
        item {
            val currentUser by authViewModel.currentUser.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = boxColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ACCOUNT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Sign Out
                    var showLogoutConfirm by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showLogoutConfirm = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Log out", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (showLogoutConfirm) {
                        LaunchedEffect(showLogoutConfirm) {
                            android.app.AlertDialog.Builder(context).setTitle("Log out").setMessage("Are you sure you want to log out?").setPositiveButton("Log out") { _, _ -> showLogoutConfirm = false; authViewModel.signOut() }.setNegativeButton("Cancel") { _, _ -> showLogoutConfirm = false }.setOnDismissListener { showLogoutConfirm = false }.show()
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Reset Password
                    var showResetConfirm by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showResetConfirm = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LockReset, contentDescription = "Reset Password", tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Reset password", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (showResetConfirm) {
                        LaunchedEffect(showResetConfirm) {
                            val email = currentUser?.email ?: ""
                            android.app.AlertDialog.Builder(context).setTitle("Reset Password").setMessage("Send a password reset link to $email?").setPositiveButton("Send") { _, _ -> showResetConfirm = false; if (email.isNotEmpty()) { authViewModel.sendResetEmail(email); Toast.makeText(context, "Reset link sent to $email", Toast.LENGTH_SHORT).show() } }.setNegativeButton("Cancel") { _, _ -> showResetConfirm = false }.setOnDismissListener { showResetConfirm = false }.show()
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Delete Account
                    var showDeleteConfirm by remember { mutableStateOf(false) }
                    var showReauthGoogle by remember { mutableStateOf(false) }
                    var showReauthPassword by remember { mutableStateOf(false) }
                    val authErr by authViewModel.authError.collectAsState()

                    LaunchedEffect(authErr) {
                        if (authErr == "REAUTH_NEEDED") {
                            authViewModel.clearError()
                            val user = authViewModel.currentUser.value
                            val isGoogleUser = user?.providerData?.any { it.providerId == "google.com" } == true
                            if (isGoogleUser) showReauthGoogle = true else showReauthPassword = true
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showDeleteConfirm = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Delete Account", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Delete account", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Delete Account") },
                            text = { Text("This will permanently delete your account and all saved data. This action cannot be undone.") },
                            confirmButton = {
                                TextButton(onClick = { showDeleteConfirm = false; authViewModel.deleteAccount() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }

                    if (showReauthGoogle) {
                        val reauthCtx = LocalContext.current
                        val reauthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                            if (result.resultCode == Activity.RESULT_OK) {
                                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                                try {
                                    val account = task.getResult(ApiException::class.java)
                                    account?.idToken?.let { authViewModel.reauthAndDelete(it) }
                                } catch (e: Exception) {
                                    Toast.makeText(reauthCtx, "Re-authentication cancelled", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(reauthCtx, "Re-authentication cancelled", Toast.LENGTH_SHORT).show()
                            }
                            showReauthGoogle = false
                        }
                        LaunchedEffect(Unit) {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(reauthCtx.getString(R.string.default_web_client_id))
                                .requestEmail()
                                .build()
                            val client = GoogleSignIn.getClient(reauthCtx, gso)
                            client.signOut().addOnCompleteListener { reauthLauncher.launch(client.signInIntent) }
                        }
                    }

                    if (showReauthPassword) {
                        var reauthPass by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showReauthPassword = false },
                            title = { Text("Verify Identity") },
                            text = {
                                Column {
                                    Text("Enter your password to confirm account deletion")
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = reauthPass,
                                        onValueChange = { reauthPass = it },
                                        label = { Text("Password") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showReauthPassword = false
                                    authViewModel.reauthWithEmailPassword(
                                        authViewModel.currentUser.value?.email ?: "",
                                        reauthPass
                                    )
                                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showReauthPassword = false }) { Text("Cancel") }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Clear Cache & Data
                    var showClearConfirm by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showClearConfirm = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = "Clear Cache", tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Clear cache & data", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (showClearConfirm) {
                        LaunchedEffect(showClearConfirm) {
                            android.app.AlertDialog.Builder(context).setTitle("Clear Cache & Data").setMessage("This will delete all downloaded apps, clear cached data and app database. The app will re-sync from Drive on next launch.").setPositiveButton("Clear") { _, _ -> showClearConfirm = false; viewModel.clearAppCache(); Toast.makeText(context, "Cache cleared. Restarting...", Toast.LENGTH_SHORT).show(); android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ val intent = context.packageManager.getLaunchIntentForPackage(context.packageName); if (intent != null) { intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK); context.startActivity(intent) }; Runtime.getRuntime().exit(0) }, 500) }.setNegativeButton("Cancel", null).show()
                        }
                    }
                }
            }
        }

        // Section: More
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = boxColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MORE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Update App
                    val selfUpdateApp by viewModel.selfUpdateApp.collectAsState()
                    val selfDownloadState = downloadStates[context.packageName]
                    val installedCode = try { val info = context.packageManager.getPackageInfo(context.packageName, 0); androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info).toInt() } catch (_: Exception) { 0 }
                    val installedName = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
                    val hasUpdate = selfUpdateApp != null && viewModel.isUpdateAvailable(selfUpdateApp!!, installedCode, installedName) && selfUpdateApp!!.versionName != installedName
                    val updateText = when {
                        selfDownloadState is DownloadState.Progress -> "Downloading... ${(selfDownloadState.percentage * 100).toInt()}%"
                        selfDownloadState is DownloadState.Downloaded -> "Install Update"
                        selfDownloadState is DownloadState.Completed -> "Installing..."
                        hasUpdate -> "Update available"
                        else -> "Update App"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            if (selfDownloadState is DownloadState.Downloaded) { viewModel.installDownloadedApp(context.packageName); return@clickable }
                            if (selfDownloadState is DownloadState.Progress || selfDownloadState is DownloadState.Completed) return@clickable
                            viewModel.forceSync {
                                val updatedApp = viewModel.selfUpdateApp.value
                                val updatedHasUpdate = updatedApp != null && viewModel.isUpdateAvailable(updatedApp, installedCode, installedName) && updatedApp.versionName != installedName
                                if (updatedHasUpdate) onStartSelfUpdate()
                                else Toast.makeText(context, "Latest version installed", Toast.LENGTH_SHORT).show()
                            }
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.SystemUpdateAlt, contentDescription = "Update App", tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(updateText, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (hasUpdate || selfDownloadState is DownloadState.Progress) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("!", fontSize = 10.sp) }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Share App
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            try {
                                val shareText = "Check out App Store \u2014 A modern app store that syncs apps from Google Drive. Browse, download, install, and manage app updates, all in one convenient place.\n\nDownload: https://u2l.ai/AppStore"
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, shareText) }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share"))
                            } catch (_: Exception) { }
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share App", tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Share App", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // About App
                    var showAbout by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showAbout = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "About", tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("About App", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (showAbout) {
                        var aboutDesc by remember { mutableStateOf("") }
                        LaunchedEffect(Unit) { viewModel.fetchSelfUpdateDescription { aboutDesc = it } }
                        AlertDialog(
                            onDismissRequest = { showAbout = false },
                            title = {},
                            text = {
                                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(painter = painterResource(id = R.drawable.app_store_icon), contentDescription = "App Store Icon", modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).border(0.5.dp, iconBorderColor, RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                    Text("App Store", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (aboutDesc.isNotEmpty()) {
                                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            for (line in aboutDesc.lines()) {
                                                val trimmed = line.trim()
                                                if (trimmed.isEmpty()) continue
                                                if (trimmed.startsWith("## ") || trimmed.startsWith("# ")) {
                                                    val headingText = trimmed.removePrefix("### ").removePrefix("## ").removePrefix("# ")
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(text = headingText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                } else if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                                                    val bulletText = trimmed.removePrefix("* ").removePrefix("- ")
                                                    val parts = bulletText.split(" — ", limit = 2)
                                                    Row(modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                                                        Text("\u2022 ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        if (parts.size == 2) { val label = parts[0].replace("**", ""); Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(" \u2014 ${parts[1]}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } else { Text(bulletText.replace("**", ""), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(text = trimmed.replace("**", ""), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Justify, lineHeight = 20.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {}
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Contact Developer
                    var showContact by remember { mutableStateOf(false) }
                    var contactEntries by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                    var developerName by remember { mutableStateOf("Contact Developer") }
                    LaunchedEffect(Unit) { viewModel.fetchContactMd { md -> val nameEntry = md.lines().firstOrNull { it.trim().removePrefix("- ").removePrefix("* ").lowercase().startsWith("name:") }; if (nameEntry != null) { val name = nameEntry.trim().removePrefix("- ").removePrefix("* ").split(":", limit = 2).getOrNull(1)?.trim(); if (!name.isNullOrEmpty()) developerName = name } } }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            if (contactEntries.isEmpty()) { viewModel.fetchContactMd { md -> val entries = mutableListOf<Pair<String, String>>(); for (line in md.lines()) { val trimmed = line.trim().removePrefix("- ").removePrefix("* "); if (trimmed.contains(":")) { val parts = trimmed.split(":", limit = 2); val label = parts[0].trim(); val value = parts[1].trim(); if (label.isNotEmpty() && value.isNotEmpty()) entries.add(label to value) } }; contactEntries = entries; showContact = true } } else { showContact = true }
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Email, contentDescription = "Contact", tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Contact developer", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (showContact && contactEntries.isNotEmpty()) {
                        val descriptionEntry = contactEntries.firstOrNull { it.first.lowercase() == "description" }
                        AlertDialog(
                            onDismissRequest = { showContact = false },
                            title = { Text(developerName, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 22.sp, modifier = Modifier.fillMaxWidth()) },
                            text = {
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (descriptionEntry != null) {
                                        Text(text = descriptionEntry.second, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
                                    }
                                    for ((label, value) in contactEntries) {
                                        val lowerLabel = label.lowercase()
                                        if (lowerLabel == "name" || lowerLabel == "developer" || lowerLabel == "description") continue
                                        val contactIcon: @Composable () -> Unit = when {
                                            lowerLabel.contains("whatsapp") -> { { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_whatsapp), contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF25D366)) } }
                                            lowerLabel.contains("messenger") -> { { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_messenger), contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF0084FF)) } }
                                            lowerLabel.contains("instagram") -> { { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_instagram), contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFE4405F)) } }
                                            lowerLabel.contains("github") -> { { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_github), contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF9E9E9E)) } }
                                            lowerLabel.contains("mobile") || lowerLabel.contains("phone") || lowerLabel.contains("call") -> { { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) } }
                                            lowerLabel.contains("facebook") -> { { Icon(Icons.Default.Facebook, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF1877F2)) } }
                                            lowerLabel.contains("telegram") -> { { Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF0088CC)) } }
                                            lowerLabel.contains("email") || lowerLabel.contains("mail") -> { { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) } }
                                            lowerLabel.contains("twitter") || lowerLabel.contains("x") -> { { Icon(Icons.Default.AlternateEmail, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) } }
                                            lowerLabel.contains("website") || lowerLabel.contains("web") -> { { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) } }
                                            lowerLabel.contains("location") || lowerLabel.contains("address") -> { { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) } }
                                            lowerLabel.contains("name") || lowerLabel.contains("developer") -> { { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) } }
                                            else -> { { Icon(Icons.Default.ContactPhone, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) } }
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                try {
                                                    val intent = when {
                                                        lowerLabel.contains("mobile") || lowerLabel.contains("phone") || lowerLabel.contains("call") -> android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$value"))
                                                        lowerLabel.contains("whatsapp") -> { val num = value.replace("[^0-9+]".toRegex(), ""); val waValue = if (num.isNotEmpty()) num else value.trim(); android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$waValue")) }
                                                        lowerLabel.contains("messenger") -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://m.me/$value"))
                                                        lowerLabel.contains("instagram") -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/$value"))
                                                        lowerLabel.contains("facebook") -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://facebook.com/$value"))
                                                        lowerLabel.contains("telegram") -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/$value"))
                                                        lowerLabel.contains("email") || lowerLabel.contains("mail") -> android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$value"))
                                                        lowerLabel.contains("twitter") || lowerLabel.contains("x") -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://x.com/$value"))
                                                        lowerLabel.contains("github") -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/$value"))
                                                        lowerLabel.contains("website") || lowerLabel.contains("web") -> { val url = if (value.startsWith("http")) value else "https://$value"; android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)) }
                                                        lowerLabel.contains("location") || lowerLabel.contains("address") -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=${java.net.URLEncoder.encode(value, "UTF-8")}"))
                                                        else -> android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(value))
                                                    }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) { }
                                            },
                                            modifier = Modifier.fillMaxWidth().height(44.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            contactIcon()
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            },
                            confirmButton = {}
                        )
                    }
                }
            }
        }

        // Version text
        item {
            val appVerName = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
            val appVerCode = try { val info = context.packageManager.getPackageInfo(context.packageName, 0); androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info).toInt() } catch (_: Exception) { 0 }
            Text(text = "Version $appVerName (build $appVerCode)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoBox(title: String, desc: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = desc, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
