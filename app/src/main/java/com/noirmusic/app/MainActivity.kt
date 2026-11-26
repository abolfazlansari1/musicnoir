package com.noirmusic.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            NoirTheme(vm.accent, vm.amoled) { NoirRoot(vm) }
        }
    }

    override fun onStart() { super.onStart(); vm.setForeground(true) }
    override fun onStop() { vm.setForeground(false); super.onStop() }
}

private data class Nav(val route: Route?, val depth: Int)

@Composable
private fun NoirRoot(vm: PlayerViewModel) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(vm.startTab) }
    var librarySub by rememberSaveable { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Route>() }
    val states = remember { ListStates() }
    var expanded by remember { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var infoTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var deleteTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var playlistTargets by remember { mutableStateOf<List<MusicTrack>?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }

    // ---- permissions ----
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onPermission(granted)
        if (granted && Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(ctx, audioPermission) == PackageManager.PERMISSION_GRANTED
        vm.onPermission(granted)
        if (!granted) permissionLauncher.launch(audioPermission)
        else if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // ---- delete confirmation from the system ----
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.onDeleteResult(r.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(vm.pendingDelete) {
        vm.pendingDelete?.let { runCatching { deleteLauncher.launch(IntentSenderRequest.Builder(it).build()) } }
    }

    val ui = remember {
        UiActions(
            navigate = { r -> expanded = false; vm.clearSelection(); stack.add(r) },
            back = { vm.clearSelection(); if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
            openPlayer = { expanded = true },
            menu = { menuTrack = it },
            addToPlaylist = { playlistTargets = it },
            showQueue = { showQueue = true },
            showSleep = { showSleep = true },
            showSpeed = { showSpeed = true },
            requestPermission = { permissionLauncher.launch(audioPermission) }
        )
    }

    BackHandler(enabled = expanded) { expanded = false }
    BackHandler(enabled = !expanded && stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = !expanded && stack.isEmpty() && tab != 0) { tab = 0 }
    BackHandler(enabled = vm.selecting && !expanded) { vm.clearSelection() }

    CompositionLocalProvider(LocalVm provides vm, LocalUi provides ui) {
        Box(Modifier.fillMaxSize().background(N.bg)) {
            Scaffold(
                containerColor = N.bg,
                bottomBar = {
                    Column {
                        AnimatedVisibility(visible = vm.selecting, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) { SelectionBar() }
                        AnimatedVisibility(
                            visible = vm.current != null && !vm.selecting,
                            enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeIn() + expandVertically(),
                            exit = slideOutVertically { it } + fadeOut() + shrinkVertically()
                        ) { MiniPlayer(onOpen = { expanded = true }) }
                        NoirNavBar(tab, stack.isEmpty()) { i -> vm.clearSelection(); stack.clear(); tab = i }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    AnimatedContent(
                        targetState = Nav(stack.lastOrNull(), stack.size), label = "nav",
                        transitionSpec = {
                            if (targetState.depth >= initialState.depth)
                                (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 5 } + fadeIn(tween(260))) togetherWith fadeOut(tween(160))
                            else fadeIn(tween(260)) togetherWith (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 5 } + fadeOut(tween(200)))
                        }
                    ) { nav ->
                        if (nav.route == null) {
                            Crossfade(tab, label = "tabs", animationSpec = tween(220)) { t ->
                                when (t) {
                                    0 -> HomeScreen(states.home)
                                    1 -> LibraryScreen(states, librarySub) { librarySub = it }
                                    2 -> SearchScreen(states.search)
                                    else -> SettingsScreen(states.settings)
                                }
                            }
                        } else RouteScreen(nav.route)
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && vm.current != null,
                enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(250))
            ) { NowPlayingScreen(onClose = { expanded = false }) }
        }

        menuTrack?.let { t ->
            TrackMenuSheet(t, onDismiss = { menuTrack = null }, onInfo = { infoTrack = t }, onDelete = { deleteTrack = t })
        }
        infoTrack?.let { InfoDialog(it) { infoTrack = null } }
        deleteTrack?.let { t -> DeleteDialog(t, onConfirm = { vm.requestDelete(t) }, onDismiss = { deleteTrack = null }) }
        playlistTargets?.let { PlaylistPicker(it) { playlistTargets = null } }
        if (showQueue) QueueSheet { showQueue = false }
        if (showSleep) SleepSheet { showSleep = false }
        if (showSpeed) SpeedDialog { showSpeed = false }
    }
}

@Composable
private fun NoirNavBar(selected: Int, onMain: Boolean, onSelect: (Int) -> Unit) {
    val items = listOf("Home" to Icons.Rounded.Home, "Library" to Icons.Rounded.LibraryMusic, "Search" to Icons.Rounded.Search, "Settings" to Icons.Rounded.Settings)
    NavigationBar(containerColor = N.bg.copy(alpha = .98f), contentColor = N.accent, tonalElevation = 0.dp) {
        items.forEachIndexed { i, (label, icon) ->
            NavigationBarItem(
                selected = onMain && selected == i, onClick = { onSelect(i) },
                icon = { Icon(icon, label) }, label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = N.onAccent, selectedTextColor = N.accent, indicatorColor = N.accent,
                    unselectedIconColor = N.muted, unselectedTextColor = N.muted
                )
            )
        }
    }
}
