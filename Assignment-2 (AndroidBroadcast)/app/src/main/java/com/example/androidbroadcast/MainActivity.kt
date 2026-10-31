package com.example.androidbroadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

// 1) Constant
const val CUSTOM_ACTION = "com.example.broadcast.CUSTOM_MESSAGE" // our own broadcast "name"
const val EXTRA_TEXT = "extra_text"                               // key that carries the text
const val IMAGE_URL = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRgekqCqtu8-Rhc9h8eao3EbAau7FqRx_9WFnTB7iG-Iw&s=10"
const val VIDEO_URL = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"
const val AUDIO_URL = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"

private val BROADCAST_OPTIONS = listOf("Custom", "Battery")

// 2) Screens
enum class Screen {
    PICK,     // Broadcast step 1: spinner + Proceed
    INPUT,    // Broadcast step 2 (Custom): type a message
    BATTERY,  // Broadcast step 2 (Battery): show battery %
    RECEIVE,  // Broadcast step 3 (Custom): receiver shows the message
    IMAGE,
    VIDEO,
    AUDIO
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

// 3) Root: navigation drawer
@Composable
fun AppRoot() {
    // State lives here (state hoisting) and survives screen rotation (rememberSaveable)
    var screen by rememberSaveable { mutableStateOf(Screen.PICK) }
    var choice by rememberSaveable { mutableStateOf(BROADCAST_OPTIONS[0]) }
    var message by rememberSaveable { mutableStateOf("") }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun openScreen(target: Screen) {
        screen = target
        scope.launch { drawerState.close() }
    }

    // Phone "Back" button
    BackHandler(enabled = screen != Screen.PICK && drawerState.isClosed) {
        screen = if (screen == Screen.RECEIVE) Screen.INPUT else Screen.PICK
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                DrawerItem("Broadcast Receiver") { openScreen(Screen.PICK) }
                DrawerItem("Image Scale") { openScreen(Screen.IMAGE) }
                DrawerItem("Video") { openScreen(Screen.VIDEO) }
                DrawerItem("Audio") { openScreen(Screen.AUDIO) }
            }
        }
    ) {
        Scaffold(
            topBar = { TopBar(onMenuClick = { scope.launch { drawerState.open() } }) }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (screen) {
                    Screen.PICK -> PickScreen(
                        choice = choice,
                        onChoice = { choice = it },
                        onProceed = {
                            screen = if (choice == "Custom") Screen.INPUT else Screen.BATTERY
                        }
                    )
                    Screen.INPUT -> InputScreen(
                        text = message,
                        onTextChange = { message = it },
                        onNext = { screen = Screen.RECEIVE }
                    )
                    Screen.BATTERY -> BatteryScreen()
                    Screen.RECEIVE -> ReceiveScreen(message = message)
                    Screen.IMAGE -> ImageScreen()
                    Screen.VIDEO -> VideoScreen()
                    Screen.AUDIO -> AudioScreen()
                }
            }
        }
    }
}

@Composable
fun DrawerItem(label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
fun TopBar(onMenuClick: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onMenuClick) { Text("☰", fontSize = 24.sp) }
            Spacer(Modifier.width(8.dp))
            Text("App", style = MaterialTheme.typography.titleLarge)
        }
    }
}

// 4) Broadcast Receiver

// Proceed button
@Composable
fun PickScreen(choice: String, onChoice: (String) -> Unit, onProceed: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Select a broadcast type")
        Box {
            TextButton(onClick = { expanded = true }) { Text("$choice ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                BROADCAST_OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onChoice(option)
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onProceed) { Text("Proceed") }
    }
}

// plain text
@Composable
fun InputScreen(text: String, onTextChange: (String) -> Unit, onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Type a message to broadcast", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("Message") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNext, enabled = text.isNotBlank()) { Text("Next") }
    }
}

// Battery
@Composable
fun BatteryScreen() {
    val ctx = LocalContext.current
    var percent by remember { mutableIntStateOf(-1) }

    // DisposableEffect = "register when this screen appears, unregister when it leaves"
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) percent = level * 100 / scale
            }
        }
        // System broadcast ( must be RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(
            ctx,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("System battery broadcast", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (percent >= 0) "Battery: $percent%" else "Reading battery…",
            fontSize = 32.sp
        )
    }
}

// receiver gets the text
@Composable
fun ReceiveScreen(message: String) {
    val ctx = LocalContext.current
    var received by remember { mutableStateOf("Waiting for broadcast…") }

    DisposableEffect(ctx, message) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                received = intent.getStringExtra(EXTRA_TEXT) ?: "(empty)"
            }
        }
        // Only our app sends this broadcast -> RECEIVER_NOT_EXPORTED
        ContextCompat.registerReceiver(
            ctx,
            receiver,
            IntentFilter(CUSTOM_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // Send AFTER registering, otherwise the receiver would miss it
        ctx.sendBroadcast(
            Intent(CUSTOM_ACTION)
                .setPackage(ctx.packageName)
                .putExtra(EXTRA_TEXT, message)
        )
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Custom broadcast receiver", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Text("Received:")
        Text(received, fontSize = 28.sp)
    }
}

// 5) Image Scale
@Composable
fun ImageScreen() {
    var scale by remember { mutableFloatStateOf(1f) }
    var failed by remember { mutableStateOf(false) }

    // Called while two fingers pinch. 1.05 (zoom in) or 0.95 (zoom out)
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
    }

    // Download the picture on a background thread
    val bitmap by produceState<ImageBitmap?>(initialValue = null) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                URL(IMAGE_URL).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (result == null) failed = true
        value = result
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Pinch with two fingers to zoom   (Zoom: ${(scale * 10).toInt() / 10f}x)")
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            when {
                bmp != null -> Image(
                    bitmap = bmp,
                    contentDescription = "Zoomable image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .transformable(state = transformState)
                )
                failed -> Text("Could not load the image. Check the internet connection.")
                else -> CircularProgressIndicator()
            }
        }
    }
}

// 6) Video: VideoView
@Composable
fun VideoScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tap the video to show the play / pause controls")
        Spacer(Modifier.height(12.dp))
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(260.dp),
            factory = { ctx ->
                VideoView(ctx).apply {
                    setMediaController(MediaController(ctx))
                    setVideoURI(VIDEO_URL.toUri())
                    setOnPreparedListener { start() }
                }
            },
            onRelease = { it.stopPlayback() }
        )
    }
}

// 7) Audio
@Composable
fun AudioScreen() {
    var status by remember { mutableStateOf("Loading audio…") }
    var ready by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }

    DisposableEffect(player) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        player.setOnPreparedListener {
            ready = true
            status = "Ready"
        }
        player.setOnCompletionListener { status = "Finished" }
        player.setOnErrorListener { _, _, _ ->
            status = "Error: could not play the audio"
            true
        }
        try {
            player.setDataSource(AUDIO_URL)
            player.prepareAsync()
        } catch (e: Exception) {
            status = "Error: ${e.message}"
        }
        onDispose { player.release() }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Audio Player", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Text(status)
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = ready,
            onClick = {
                if (player.isPlaying) {
                    player.pause()
                    status = "Paused"
                } else {
                    player.start()
                    status = "Playing"
                }
            }
        ) {
            Text(if (status == "Playing") "Pause" else "Play")
        }
    }
}