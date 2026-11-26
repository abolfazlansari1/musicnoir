package com.noirmusic.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// ============================ SEARCH ============================

@Composable
fun SearchScreen(state: LazyListState) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val q = remember(query) { PlayerViewModel.normalize(query) }
    val songs = remember(q, vm.tracks, vm.searchIndex) {
        if (q.isEmpty()) emptyList() else vm.tracks.filter { vm.searchIndex[it.id]?.contains(q) == true }
    }
    val albums = remember(q, vm.albums) {
        if (q.isEmpty()) emptyList() else vm.albums.filter { PlayerViewModel.normalize("${it.name} ${it.artist}").contains(q) }.take(20)
    }
    val artists = remember(q, vm.artists) {
        if (q.isEmpty()) emptyList() else vm.artists.filter { PlayerViewModel.normalize(it.name).contains(q) }.take(20)
    }
    val currentId = vm.current?.id
    Column(Modifier.fillMaxSize()) {
        Text("Search", color = N.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 10.dp))
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            placeholder = { Text("Songs, artists, albums", color = N.muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = N.muted) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear", tint = N.muted) } },
            singleLine = true, shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = N.accent, unfocusedBorderColor = Color(0xFF3A3047),
                focusedContainerColor = N.panel, unfocusedContainerColor = N.panel, cursorColor = N.accent,
                focusedTextColor = N.text, unfocusedTextColor = N.text
            )
        )
        Spacer(Modifier.height(10.dp))
        when {
            q.isEmpty() -> EmptyState(icon = Icons.Rounded.Search, title = "Search your library", subtitle = "Type a song, artist or album name. Persian and Arabic letters match each other.")
            songs.isEmpty() && albums.isEmpty() && artists.isEmpty() -> EmptyState(icon = Icons.Rounded.Search, title = "No results", subtitle = "Nothing matches \"$query\".")
            else -> LazyColumn(state = state, contentPadding = PaddingValues(bottom = 16.dp)) {
                if (artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(artists, key = { "ar${it.name}" }) { a ->
                                Column(Modifier.width(92.dp).bounceClick { ui.navigate(Route.Artist(a.name)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Artwork(a.tracks.first(), Modifier.size(84.dp), px = 200, corner = 42.dp)
                                    Text(a.name, color = N.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }
                if (albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(albums, key = { "al${it.id}" }) { a -> AlbumCard(a, Modifier.width(130.dp)) { ui.navigate(Route.Album(a.id)) } }
                        }
                    }
                }
                if (songs.isNotEmpty()) {
                    item { SectionHeader("Songs (${songs.size})") }
                    itemsIndexed(songs, key = { _, t -> t.id }) { i, t ->
                        TrackRow(t, t.id == currentId, vm.isPlaying, { keyboard?.hide(); vm.play(songs, i, false) })
                    }
                }
            }
        }
    }
}

// ============================ SETTINGS ============================

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(20.dp)).background(N.panel).padding(vertical = 8.dp)) {
        Text(title.uppercase(), color = N.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        content()
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = N.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = N.muted, fontSize = 12.sp)
        }
        trailing()
    }
}

@Composable
private fun NoirSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(checkedThumbColor = N.onAccent, checkedTrackColor = N.accent, uncheckedThumbColor = N.muted, uncheckedTrackColor = N.panel2, uncheckedBorderColor = Color(0xFF3A3047))
    )
}

@Composable
fun SettingsScreen(state: LazyListState) {
    val vm = LocalVm.current
    val ui = LocalUi.current
    val ctx = LocalContext.current
    val remaining by SleepTimer.remainingSec.collectAsState()
    val endOfTrack by SleepTimer.endOfTrack.collectAsState()
    var cleared by remember { mutableStateOf<String?>(null) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", color = N.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 18.dp, top = 14.dp)) }
        item {
            SettingsCard("Appearance") {
                Text("Accent color", color = N.text, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccentChoices.forEachIndexed { i, (name, color) ->
                        val sel = vm.accent == i
                        val scale by animateFloatAsState(if (sel) 1.12f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "sw")
                        Box(
                            Modifier.size(38.dp).scale(scale).clip(CircleShape).background(color)
                                .then(if (sel) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                                .clickable { vm.setAccent(i) },
                            contentAlignment = Alignment.Center
                        ) { if (sel) Icon(Icons.Rounded.Check, name, tint = Color.Black.copy(alpha = .75f), modifier = Modifier.size(20.dp)) }
                    }
                }
                SettingRow("Pure black background", "Saves battery on OLED screens") { NoirSwitch(vm.amoled) { vm.setAmoled(it) } }
            }
        }
        item {
            SettingsCard("Playback") {
                SettingRow(
                    "Sleep timer",
                    when { endOfTrack -> "Stops after this song"; remaining > 0 -> "Stops in ${formatTime(remaining * 1000L)}"; else -> "Off" },
                    onClick = ui.showSleep
                ) { Icon(Icons.Rounded.Timer, null, tint = N.accent) }
                SettingRow("Speed and pitch", "Speed ${"%.2f".format(Locale.US, vm.speed)}x  •  Pitch ${"%.2f".format(Locale.US, vm.pitch)}x", onClick = ui.showSpeed) { Icon(Icons.Rounded.Speed, null, tint = N.accent) }
                SettingRow("Skip silence", "Jump over silent parts of tracks") { NoirSwitch(vm.skipSilence) { vm.setSkipSilence(it) } }
                SettingRow("Equalizer", "Presets, bands, bass boost, virtualizer", onClick = { ui.navigate(Route.Equalizer) }) { Icon(Icons.Rounded.ChevronRight, null, tint = N.muted) }
            }
        }
        item {
            SettingsCard("Library") {
                SettingRow("Hide songs shorter than") {}
                Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Show all", 15 to "15 s", 30 to "30 s", 60 to "60 s").forEach { (sec, label) ->
                        NoirChip(label, vm.minDurationSec == sec, { vm.setMinDuration(sec) })
                    }
                }
                SettingRow("Include non-music audio", "Voice notes, recordings and similar files") { NoirSwitch(vm.includeNonMusic) { vm.setIncludeNonMusic(it) } }
                SettingRow("Rescan library", "${vm.tracks.size} songs • ${vm.albums.size} albums • ${vm.artists.size} artists", onClick = { vm.loadLibrary() })
            }
        }
        item {
            SettingsCard("Data") {
                SettingRow("Clear play history", "Recently played and most played lists", onClick = { vm.clearHistory(); cleared = "Play history cleared" })
                SettingRow("Clear saved lyrics", "Downloaded lyrics are re-fetched when needed", onClick = { vm.clearLyricsCache(); cleared = "Saved lyrics cleared" })
                cleared?.let { Text(it, color = N.accent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }
        }
        item {
            SettingsCard("About") {
                SettingRow("Noir Music", "Version $version") {}
                SettingRow("Lyrics", "Provided by LRCLIB (lrclib.net)") {}
            }
        }
    }
}

// ============================ EQUALIZER ============================

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val fx by AudioFx.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { TopBar("Equalizer", onBack) { TextButton(onClick = { AudioFx.reset() }, enabled = fx.available) { Text("Reset", color = if (fx.available) N.accent else N.muted) } } }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(20.dp)).background(N.panel).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Audio effects", color = N.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(if (fx.available) (if (fx.enabled) "On" else "Off") else "Not available on this device", color = N.muted, fontSize = 12.sp)
                    }
                    NoirSwitch(fx.enabled) { AudioFx.setEnabled(it) }
                }
                if (fx.available && fx.presets.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { NoirChip("Custom", fx.preset == -1, { }) }
                        itemsIndexed(fx.presets) { i, name -> NoirChip(name, fx.preset == i, { AudioFx.usePreset(i) }) }
                    }
                }
            }
        }
        if (fx.available) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(20.dp)).background(N.panel).padding(vertical = 16.dp, horizontal = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        fx.levels.forEachIndexed { i, level ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(String.format(Locale.US, "%+.1f", level / 100f), color = N.muted, fontSize = 10.sp)
                                Spacer(Modifier.height(6.dp))
                                BandSlider(level, fx.minLevel, fx.maxLevel, fx.enabled) { AudioFx.setBand(i, it) }
                                Spacer(Modifier.height(6.dp))
                                val hz = fx.freqsHz.getOrElse(i) { 0 }
                                Text(if (hz >= 1000) String.format(Locale.US, "%.1fk", hz / 1000f).replace(".0k", "k") else "$hz", color = N.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("Hz", color = N.muted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(20.dp)).background(N.panel).padding(16.dp)) {
                FxSlider("Bass boost", fx.bass / 10, fx.available && fx.bassSupported) { AudioFx.setBass(it * 10) }
                FxSlider("Virtualizer", fx.virt / 10, fx.available && fx.virtSupported) { AudioFx.setVirtualizer(it * 10) }
                FxSlider("Loudness boost", fx.loud / 20, fx.available) { AudioFx.setLoudness(it * 20) }
                Text("Effects are applied by the Android audio engine. Support differs between devices.", color = N.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun FxSlider(label: String, percent: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (enabled) N.text else N.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("$percent%", color = N.muted, fontSize = 12.sp)
    }
    Slider(
        value = percent.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 0f..100f, enabled = enabled,
        colors = SliderDefaults.colors(thumbColor = N.accent, activeTrackColor = N.accent, inactiveTrackColor = N.panel2, disabledThumbColor = N.muted, disabledActiveTrackColor = N.panel2, disabledInactiveTrackColor = N.panel2)
    )
}

@Composable
private fun BandSlider(level: Int, min: Int, max: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val change by rememberUpdatedState(onChange)
    val span = (max - min).coerceAtLeast(1)
    val frac = ((level - min).toFloat() / span).coerceIn(0f, 1f)
    val fill by animateColorAsState(if (enabled) N.accent else N.muted, label = "bandfill")
    val trackColor = N.panel2
    fun toLevel(y: Float, h: Float): Int = (min + (1f - (y / h).coerceIn(0f, 1f)) * span).toInt()
    Canvas(
        Modifier.width(40.dp).height(190.dp)
            .pointerInput(enabled) { if (enabled) detectTapGestures { o -> change(toLevel(o.y, size.height.toFloat())) } }
            .pointerInput(enabled) {
                if (enabled) detectDragGestures(onDragStart = { o -> change(toLevel(o.y, size.height.toFloat())) }) { c, _ ->
                    c.consume(); change(toLevel(c.position.y, size.height.toFloat()))
                }
            }
    ) {
        val w = 6.dp.toPx()
        val x = size.width / 2 - w / 2
        val pad = 9.dp.toPx()
        val usable = size.height - pad * 2
        val thumbY = pad + usable * (1f - frac)
        val zeroY = pad + usable * (1f - (0f - min).toFloat() / span).coerceIn(0f, 1f)
        drawRoundRect(trackColor, Offset(x, pad), Size(w, usable), CornerRadius(w / 2))
        drawRoundRect(fill, Offset(x, minOf(thumbY, zeroY)), Size(w, kotlin.math.abs(thumbY - zeroY)), CornerRadius(w / 2))
        drawRect(Color.White.copy(alpha = .25f), Offset(x - 5.dp.toPx(), zeroY - 0.5f), Size(w + 10.dp.toPx(), 1.dp.toPx()))
        drawCircle(Color.White, 9.dp.toPx(), Offset(size.width / 2, thumbY))
    }
}
