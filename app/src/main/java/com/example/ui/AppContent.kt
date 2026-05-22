package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.viewmodel.SongSortOrder
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.data.PlaybackState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntSize
import android.graphics.Bitmap
import com.example.data.Playlist
import com.example.data.Song
import com.example.ui.theme.*
import com.example.viewmodel.MusicViewModel
import java.io.File
import java.util.Locale

private val CyberEmerald: Color @Composable get() = LocalCyberEmerald.current
private val ElectricCyan: Color @Composable get() = LocalElectricCyan.current

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcousticApp(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Core data streams
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val playState by viewModel.playbackState.collectAsStateWithLifecycle()
    val themeColorMode by viewModel.themeColorMode.collectAsStateWithLifecycle()
    val fluidBgEnabled by viewModel.fluidBgEnabled.collectAsStateWithLifecycle()
    val waveSpeed by viewModel.waveSpeed.collectAsStateWithLifecycle()
    val waveRoughness by viewModel.waveRoughness.collectAsStateWithLifecycle()
    val waveColorStyle by viewModel.waveColorStyle.collectAsStateWithLifecycle()
    val currentTrackColor by viewModel.currentTrackColor.collectAsStateWithLifecycle()
    val currentTrackColors by viewModel.currentTrackColors.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    
    val animatedTrackColor by animateColorAsState(
        targetValue = currentTrackColor ?: Color(0xFFD0BCFF),
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = "dynamic_track_background"
    )
    
    val animatedColor2 by animateColorAsState(
        targetValue = currentTrackColors.getOrNull(1) ?: Color(0xFFEADDFF),
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = "dynamic_color_2"
    )

    val animatedColor3 by animateColorAsState(
        targetValue = currentTrackColors.getOrNull(2) ?: Color(0xFF381E72),
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = "dynamic_color_3"
    )

    val animatedSecondaryColor = animatedColor2
    
    val selectedPlaylistId by viewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val playlistSongs by viewModel.playlistSongs.collectAsStateWithLifecycle()

    // Screen navigation parameters (Tabs)
    var currentTab by remember { mutableStateOf("Songs") }
    
    // Overlay players
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isQueueExpanded by remember { mutableStateOf(false) }

    // Dialog state controllers
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var selectedSongForPlaylist by remember { mutableStateOf<Song?>(null) }
    var showPlaylistSelectorDialog by remember { mutableStateOf(false) }

    // Storage scanning parameters
    val scanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    val systemLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanDeviceMedia(context)
        }
    }

    // Launchers for choosing audio tracks from system files explorer (Document picker)
    val filesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importSongs(context, uris)
        }
    }

    // Launcher for choosing a folder to recursively scan and import
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFolder(context, uri)
        }
    }

    // Register state links on app load
    LaunchedEffect(Unit) {
        viewModel.bindPlaybackService(context)
        
        // Grant post notifications automatically if available
        notificationPermission?.let { permission ->
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                systemLauncher.launch(permission)
            }
        }
    }

    CompositionLocalProvider(
        LocalCyberEmerald provides animatedTrackColor,
        LocalElectricCyan provides animatedSecondaryColor,
        com.example.ui.theme.LocalTrackColors provides listOf(animatedTrackColor, animatedColor2, animatedColor3)
    ) {
        // --- 1D Wave Fallback Simulation State ---
        val steps = 50
        val waveH1 = remember { FloatArray(steps + 1) }
        val waveV1 = remember { FloatArray(steps + 1) }
        val waveH2 = remember { FloatArray(steps + 1) }
        val waveV2 = remember { FloatArray(steps + 1) }
        val waveH3 = remember { FloatArray(steps + 1) }
        val waveV3 = remember { FloatArray(steps + 1) }

        val applyTouchForce = remember {
            { normalizedX: Float, forceX: Float ->
                val centerIndex = (normalizedX * steps).toInt().coerceIn(0, steps)
                val spread = 5
                for (i in -spread..spread) {
                    val idx = centerIndex + i
                    if (idx in 0..steps) {
                        val dist = Math.abs(i).toFloat() / spread
                        val factor = (1f - dist * dist).coerceIn(0f, 1f)
                        waveH1[idx] += forceX * factor * 1.0f
                        waveH2[idx] -= forceX * factor * 1.2f
                        waveH3[idx] += forceX * factor * 0.8f
                    }
                }
            }
        }

        var waveTime by remember { mutableStateOf(0f) }
        var tick1D by remember { mutableStateOf(0L) }

        LaunchedEffect(playState.isPlaying) {
            var lastTime = withFrameNanos { it }
            while (true) {
                withFrameNanos { frameTimeNanos ->
                    val diffNanos = frameTimeNanos - lastTime
                    val diffSeconds = (diffNanos / 1_000_000_000f).coerceAtMost(0.05f)
                    lastTime = frameTimeNanos
                    
                    if (!fluidBgEnabled) {
                        // Time based orbital drift
                        val speed = if (playState.isPlaying) 0.60f else 0.12f
                        waveTime = (waveTime + speed * diffSeconds) % (2f * Math.PI.toFloat())

                        // 1D Wave equation simulation update
                        val subSteps = 2
                        val dt = diffSeconds / subSteps
                        val damping = 0.94f
                        val k1 = 120f
                        val k2 = 180f
                        val k3 = 90f

                        for (step in 1..subSteps) {
                            // Wave 1 Physics
                            for (i in 1 until steps) {
                                val accel = k1 * (waveH1[i - 1] + waveH1[i + 1] - 2 * waveH1[i])
                                waveV1[i] = (waveV1[i] + accel * dt) * damping
                            }
                            waveV1[0] = (waveV1[0] + k1 * (waveH1[1] - waveH1[0]) * dt) * damping
                            waveV1[steps] = (waveV1[steps] + k1 * (waveH1[steps - 1] - waveH1[steps]) * dt) * damping
                            for (i in 0..steps) {
                                waveH1[i] += waveV1[i] * dt
                            }

                            // Wave 2 Physics
                            for (i in 1 until steps) {
                                val accel = k2 * (waveH2[i - 1] + waveH2[i + 1] - 2 * waveH2[i])
                                waveV2[i] = (waveV2[i] + accel * dt) * damping
                            }
                            waveV2[0] = (waveV2[0] + k2 * (waveH2[1] - waveH2[0]) * dt) * damping
                            waveV2[steps] = (waveV2[steps] + k2 * (waveH2[steps - 1] - waveH2[steps]) * dt) * damping
                            for (i in 0..steps) {
                                waveH2[i] += waveV2[i] * dt
                            }

                            // Wave 3 Physics
                            for (i in 1 until steps) {
                                val accel = k3 * (waveH3[i - 1] + waveH3[i + 1] - 2 * waveH3[i])
                                waveV3[i] = (waveV3[i] + accel * dt) * damping
                            }
                            waveV3[0] = (waveV3[0] + k3 * (waveH3[1] - waveH3[0]) * dt) * damping
                            waveV3[steps] = (waveV3[steps] + k3 * (waveH3[steps - 1] - waveH3[steps]) * dt) * damping
                            for (i in 0..steps) {
                                waveH3[i] += waveV3[i] * dt
                            }
                        }

                        for (i in 0..steps) {
                            waveH1[i] *= 0.985f
                            waveH2[i] *= 0.985f
                            waveH3[i] *= 0.985f
                        }

                        tick1D++
                    }
                }
            }
        }

        // --- 2D WebGL Fluid Simulation State ---
        val fluidSim = remember { FluidSimulation(NX = 1440, NY = 3168) }
        var tick2D by remember { mutableStateOf(0L) }

        val currentWaveSpeed by rememberUpdatedState(waveSpeed)
        val currentWaveRoughness by rememberUpdatedState(waveRoughness)
        val currentWaveColorStyle by rememberUpdatedState(waveColorStyle)

        LaunchedEffect(fluidBgEnabled) {
            if (fluidBgEnabled) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    var lastTime = System.nanoTime()
                    var elapsedPassiveTime = 0f
                    val stride = fluidSim.simNX + 2
                    while (true) {
                        val currentTime = System.nanoTime()
                        val diffSeconds = ((currentTime - lastTime) / 1_000_000_000f).coerceIn(0.002f, 0.05f)
                        lastTime = currentTime
                        
                        val speed = currentWaveSpeed
                        val roughness = currentWaveRoughness
                        val colorStyle = currentWaveColorStyle

                        elapsedPassiveTime += diffSeconds * speed

                        val dt = diffSeconds * 0.95f
                        val time = elapsedPassiveTime

                        // Precalculate horizontal (column-wise) rough wave components with high frequency octaves
                        val colWaveU = FloatArray(fluidSim.simNX + 1)
                        val colWaveV = FloatArray(fluidSim.simNX + 1)
                        val colWaveColor = FloatArray(fluidSim.simNX + 1)
                        
                        for (i in 1..fluidSim.simNX) {
                            val progressX = i.toFloat() / fluidSim.simNX
                            val arg1 = progressX * 6.28f
                            // Multi-octave wave superposition for ultra-fine choppy sea texture
                            val wave1 = kotlin.math.sin((arg1 * 2.8f + time * 2.2f).toDouble()).toFloat()
                            val wave2 = kotlin.math.cos((arg1 * 7.5f - time * 3.5f).toDouble()).toFloat() * 0.42f * roughness
                            val wave3 = kotlin.math.sin((arg1 * 16.0f + time * 5.5f).toDouble()).toFloat() * 0.22f * roughness
                            val wave4 = kotlin.math.cos((arg1 * 36.0f - time * 8.5f).toDouble()).toFloat() * 0.11f * roughness
                            colWaveU[i] = wave1 + wave2 + wave3 + wave4

                            val waveV1 = kotlin.math.cos((arg1 * 2.5f - time * 1.8f).toDouble()).toFloat()
                            val waveV2 = kotlin.math.sin((arg1 * 8.2f + time * 3.2f).toDouble()).toFloat() * 0.45f * roughness
                            val waveV3 = kotlin.math.cos((arg1 * 19.0f - time * 4.8f).toDouble()).toFloat() * 0.2f * roughness
                            val waveV4 = kotlin.math.sin((arg1 * 42.0f + time * 8.0f).toDouble()).toFloat() * 0.1f * roughness
                            colWaveV[i] = waveV1 + waveV2 + waveV3 + waveV4

                            // Intense multi-frequency color interference coordinates
                            colWaveColor[i] = (kotlin.math.sin((arg1 * 3.8f + time * 1.6f).toDouble()).toFloat() +
                                              kotlin.math.cos((arg1 * 12.0f - time * 3.0f).toDouble()).toFloat() * 0.38f * roughness +
                                              kotlin.math.sin((arg1 * 28.0f + time * 4.6f).toDouble()).toFloat() * 0.2f * roughness +
                                              kotlin.math.cos((arg1 * 56.0f - time * 7.5f).toDouble()).toFloat() * 0.1f * roughness)
                        }

                        // Precalculate vertical (row-wise) rough wave components with high frequency octaves
                        val rowWaveU = FloatArray(fluidSim.simNY + 1)
                        val rowWaveV = FloatArray(fluidSim.simNY + 1)
                        val rowWaveColor = FloatArray(fluidSim.simNY + 1)
                        for (j in 1..fluidSim.simNY) {
                            val progressY = j.toFloat() / fluidSim.simNY
                            val arg2 = progressY * 6.28f
                            val wave1 = kotlin.math.cos((arg2 * 2.2f - time * 1.6f).toDouble()).toFloat()
                            val wave2 = kotlin.math.sin((arg2 * 6.4f + time * 2.8f).toDouble()).toFloat() * 0.45f * roughness
                            val wave3 = kotlin.math.cos((arg2 * 14.5f - time * 4.2f).toDouble()).toFloat() * 0.22f * roughness
                            val wave4 = kotlin.math.sin((arg2 * 32.0f + time * 6.8f).toDouble()).toFloat() * 0.11f * roughness
                            rowWaveU[j] = wave1 + wave2 + wave3 + wave4

                            val waveV1 = kotlin.math.sin((arg2 * 3.0f + time * 2.0f).toDouble()).toFloat()
                            val waveV2 = kotlin.math.cos((arg2 * 8.5f - time * 3.4f).toDouble()).toFloat() * 0.48f * roughness
                            val waveV3 = kotlin.math.sin((arg2 * 21.0f + time * 5.0f).toDouble()).toFloat() * 0.22f * roughness
                            val waveV4 = kotlin.math.cos((arg2 * 48.0f - time * 7.6f).toDouble()).toFloat() * 0.1f * roughness
                            rowWaveV[j] = waveV1 + waveV2 + waveV3 + waveV4

                            // Choppy vertical contrast profiles
                            rowWaveColor[j] = (kotlin.math.sin((arg2 * 3.0f + time * 1.2f).toDouble()).toFloat() +
                                              kotlin.math.cos((arg2 * 10.5f - time * 2.6f).toDouble()).toFloat() * 0.4f * roughness +
                                              kotlin.math.sin((arg2 * 24.0f + time * 4.0f).toDouble()).toFloat() * 0.2f * roughness +
                                              kotlin.math.cos((arg2 * 52.0f - time * 6.5f).toDouble()).toFloat() * 0.12f * roughness)
                        }

                        // Determine base theme colors dynamically or override based on user preference preset
                        val rBase: Float
                        val gBase: Float
                        val bBase: Float
                        when (colorStyle) {
                            "Deep Sea Navy" -> {
                                rBase = 10f
                                gBase = 85f + 15f * kotlin.math.sin(time * 0.4f).toFloat()
                                bBase = 195f
                            }
                            "Midnight Violet" -> {
                                rBase = 165f
                                gBase = 25f + 10f * kotlin.math.cos(time * 0.5f).toFloat()
                                bBase = 185f
                            }
                            "Toxic Emerald" -> {
                                rBase = 20f
                                gBase = 215f
                                bBase = 75f + 25f * kotlin.math.sin(time * 0.3f).toFloat()
                            }
                            "Cyber Sunset" -> {
                                rBase = 225f
                                gBase = 80f + 20f * kotlin.math.cos(time * 0.35f).toFloat()
                                bBase = 10f
                            }
                            else -> { // "Dynamic Track"
                                rBase = (animatedTrackColor.red * 140f)
                                gBase = (animatedColor2.green * 140f)
                                bBase = (animatedColor3.blue * 140f)
                            }
                        }

                        // Populate grid combining row and column components to form non-uniform turbulent interference patterns
                        for (j in 1..fluidSim.simNY) {
                            val rowOffset = j * stride
                            val ry = rowWaveColor[j]
                            val uy = rowWaveU[j]
                            val vy = rowWaveV[j]

                            for (i in 1..fluidSim.simNX) {
                                val idx = i + rowOffset
                                
                                // Multiply and add the vertical and horizontal frequencies to create extremely detailed choppy crests/troughs
                                val cx = colWaveColor[i]
                                val totalRoughWave = ry + cx + ry * cx * 0.85f * roughness
                                
                                val rWave = rBase + 68f * totalRoughWave * roughness
                                val gWave = gBase + 68f * totalRoughWave * roughness
                                val bWave = bBase + 68f * totalRoughWave * roughness

                                val ux = colWaveU[i]
                                val vx = colWaveV[i]
                                
                                val uWaveCurrent = uy + ux + uy * ux * 0.5f
                                val vWaveCurrent = vy + vx + vy * vx * 0.5f

                                fluidSim.u[idx] += uWaveCurrent * 2.4f
                                fluidSim.v[idx] += vWaveCurrent * 2.4f
                                
                                val rVal = fluidSim.rPrev[idx] * 0.91f + rWave * 0.09f
                                fluidSim.r[idx] = if (rVal < 0f) 0f else if (rVal > 255f) 255f else rVal

                                val gVal = fluidSim.gPrev[idx] * 0.91f + gWave * 0.09f
                                fluidSim.g[idx] = if (gVal < 0f) 0f else if (gVal > 255f) 255f else gVal

                                val bVal = fluidSim.bPrev[idx] * 0.91f + bWave * 0.09f
                                fluidSim.b[idx] = if (bVal < 0f) 0f else if (bVal > 255f) 255f else bVal
                            }
                        }

                        fluidSim.step(dt, diffusionAndDyeDecay = 0.995f)
                        fluidSim.updateBitmap()
                        tick2D++
                        kotlinx.coroutines.yield()
                    }
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(fluidBgEnabled) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    val currentX = change.position.x
                                    val currentY = change.position.y
                                    val previousX = change.previousPosition.x
                                    val previousY = change.previousPosition.y

                                    val dx = currentX - previousX
                                    val dy = currentY - previousY

                                    val w = size.width
                                    val h = size.height
                                    if (w > 0f && h > 0f) {
                                        if (fluidBgEnabled) {
                                            val gridX = (currentX / w * fluidSim.NX).toInt().coerceIn(1, fluidSim.NX)
                                            val gridY = (currentY / h * fluidSim.NY).toInt().coerceIn(1, fluidSim.NY)

                                            // Determine drag velocity versus static taps
                                            val speedScale = 1.6f
                                            val forceX = if (kotlin.math.abs(dx) > 0.05f) (dx / w * fluidSim.NX) * 580f * speedScale else (Math.random().toFloat() - 0.5f) * 120f
                                            val forceY = if (kotlin.math.abs(dy) > 0.05f) (dy / h * fluidSim.NY) * 580f * speedScale else (Math.random().toFloat() - 0.5f) * 120f
                                            fluidSim.addVelocity(gridX, gridY, forceX, forceY, radius = 12)

                                            // Thick, glowing neon dye! Always active on touch
                                            val c = when ((1..3).random()) {
                                                1 -> animatedTrackColor
                                                2 -> animatedColor2
                                                else -> animatedColor3
                                            }
                                            fluidSim.addDensity(
                                                gridX,
                                                gridY,
                                                c.red * 1400f,
                                                c.green * 1400f,
                                                c.blue * 1400f,
                                                radius = 15
                                            )
                                        } else {
                                            val normX = (currentX / w).coerceIn(0f, 1f)
                                            val force = if (kotlin.math.abs(dx) > 0.5f) 180f else 60f
                                            applyTouchForce(normX, force)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Layer 1: Ambient background waves / fluid simulations taking up the whole background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    animatedTrackColor.copy(alpha = 0.15f),
                                    animatedColor2.copy(alpha = 0.05f),
                                    Color.Transparent
                                 )
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(animatedTrackColor.copy(alpha = 0.28f), Color.Transparent),
                                center = Offset(size.width * 1.1f, -size.height * 0.1f),
                                radius = size.width * 1.3f
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(animatedColor2.copy(alpha = 0.22f), Color.Transparent),
                                center = Offset(-size.width * 0.1f, size.height * 1.1f),
                                radius = size.width * 1.3f
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(animatedColor3.copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(size.width * 0.5f, size.height * 0.6f),
                                radius = size.width * 1.0f
                            )
                        )
                    }
            ) {
                if (fluidBgEnabled) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(playState.bgWaveOpacity)
                    ) {
                        val drawTick = tick2D
                        val width = size.width
                        val height = size.height
                        drawImage(
                            image = fluidSim.bitmap.asImageBitmap(),
                            dstSize = IntSize(width.toInt(), height.toInt()),
                            filterQuality = FilterQuality.Medium
                        )
                    }
                } else {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(playState.bgWaveOpacity)
                    ) {
                        val drawTick = tick1D
                        val width = size.width
                        val height = size.height

                        val baseLine1 = height * 0.40f
                        val baseLine2 = height * 0.55f
                        val baseLine3 = height * 0.70f

                        val path1 = androidx.compose.ui.graphics.Path()
                        val path2 = androidx.compose.ui.graphics.Path()
                        val path3 = androidx.compose.ui.graphics.Path()

                        path1.moveTo(0f, height)
                        path1.lineTo(0f, baseLine1)

                        path2.moveTo(0f, height)
                        path2.lineTo(0f, baseLine2)

                        path3.moveTo(0f, height)
                        path3.lineTo(0f, baseLine3)

                        for (xIndex in 0..steps) {
                            val x = (width / steps) * xIndex
                            val progress = xIndex.toFloat() / steps

                            val physH1 = waveH1[xIndex]
                            val physH2 = waveH2[xIndex]
                            val physH3 = waveH3[xIndex]

                            val y1 = baseLine1 - physH1 + 35.dp.toPx() * Math.sin((progress * 2.0 * Math.PI + waveTime).toDouble()).toFloat()
                            val y2 = baseLine2 + physH2 + 45.dp.toPx() * Math.sin((progress * 1.5 * Math.PI - waveTime * 1.3).toDouble()).toFloat()
                            val y3 = baseLine3 - physH3 + 55.dp.toPx() * Math.cos((progress * 2.5 * Math.PI + waveTime * 0.8).toDouble()).toFloat()

                            if (xIndex == 0) {
                                path1.lineTo(x, y1)
                                path2.lineTo(x, y2)
                                path3.lineTo(x, y3)
                            } else {
                                path1.quadraticTo(x - (width / steps) / 2f, y1, x, y1)
                                path2.quadraticTo(x - (width / steps) / 2f, y2, x, y2)
                                path3.quadraticTo(x - (width / steps) / 2f, y3, x, y3)
                            }
                        }

                        path1.lineTo(width, height)
                        path2.lineTo(width, height)
                        path3.lineTo(width, height)

                        path1.close()
                        path2.close()
                        path3.close()

                        drawPath(
                            path = path3,
                            brush = Brush.verticalGradient(
                                colors = listOf(animatedColor3.copy(alpha = 0.45f), Color.Transparent),
                                startY = baseLine3 - 50.dp.toPx(),
                                endY = height
                            )
                        )

                        drawPath(
                            path = path2,
                            brush = Brush.verticalGradient(
                                colors = listOf(animatedColor2.copy(alpha = 0.35f), Color.Transparent),
                                startY = baseLine2 - 40.dp.toPx(),
                                endY = height
                            )
                        )

                        drawPath(
                            path = path1,
                            brush = Brush.verticalGradient(
                                colors = listOf(animatedTrackColor.copy(alpha = 0.25f), Color.Transparent),
                                startY = baseLine1 - 30.dp.toPx(),
                                endY = height
                            )
                        )
                    }
                }
            }

            // Layer 2: Main application Scaffold rendered on top of the transparent canvas
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    Spacer(modifier = Modifier.statusBarsPadding())
                },
                bottomBar = {
                    Column {
                        // Collapsed bottom bar playback widget
                    playState.currentSong?.let { _ ->
                        AnimatedVisibility(
                            visible = !isPlayerExpanded,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            CollapsedMiniPlayer(
                                state = playState,
                                onPlayPauseToggle = { viewModel.togglePlayback() },
                                onNext = { viewModel.skipNext() },
                                onExpandToggle = { isPlayerExpanded = true }
                            )
                        }
                    }
                    
                    // Navigation tab switcher bar
                    TabNavigationBar(
                        activeTab = currentTab,
                        onTabSelected = {
                            currentTab = it
                            // clear selected playlist if routing out to general tabs
                            if (it != "Playlists") {
                                viewModel.selectPlaylist(null)
                            }
                        }
                    )
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main views dynamic swap
            when (currentTab) {
                "Songs" -> {
                    SongsDashboard(
                        songs = songs,
                        currentSong = playState.currentSong,
                        isPlaying = playState.isPlaying,
                        playbackProgress = playState.progress,
                        sortOrder = sortOrder,
                        onSortOrderChange = { viewModel.setSortOrder(it) },
                        onSongSelected = { song -> viewModel.playSong(song, songs) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDeleteSong = { viewModel.deleteSong(it) },
                        onAddToPlaylist = {
                            selectedSongForPlaylist = it
                            showPlaylistSelectorDialog = true
                        },
                        onLaunchPicker = { filesPickerLauncher.launch("audio/*") },
                        onLaunchFolderPicker = { folderPickerLauncher.launch(null) },
                        onTriggerScan = {
                            val hasPerm = ContextCompat.checkSelfPermission(context, scanPermission) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                viewModel.scanDeviceMedia(context)
                            } else {
                                systemLauncher.launch(scanPermission)
                            }
                        },
                        onPlayNext = { viewModel.playNext(it) }
                    )
                }
                "Playlists" -> {
                    PlaylistsDashboard(
                        playlists = playlists,
                        selectedPlaylistId = selectedPlaylistId,
                        playlistSongs = playlistSongs,
                        currentSong = playState.currentSong,
                        isPlaying = playState.isPlaying,
                        playbackProgress = playState.progress,
                        onCreatePlaylist = { showCreatePlaylistDialog = true },
                        onSelectPlaylist = { viewModel.selectPlaylist(it) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onPlaySongInPlaylist = { song -> viewModel.playSong(song, playlistSongs) },
                        onRemoveSongFromPlaylist = { playlistId, songId -> viewModel.removeSongFromPlaylist(playlistId, songId) }
                    )
                }
                "Favorites" -> {
                    FavoritesDashboard(
                        songs = favorites,
                        currentSong = playState.currentSong,
                        isPlaying = playState.isPlaying,
                        playbackProgress = playState.progress,
                        onSongSelected = { song -> viewModel.playSong(song, favorites) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onAddToPlaylist = {
                            selectedSongForPlaylist = it
                            showPlaylistSelectorDialog = true
                        },
                        onPlayNext = { viewModel.playNext(it) }
                    )
                }
                "Settings" -> {
                    SettingsDashboard(
                        state = playState,
                        themeMode = themeColorMode,
                        onThemeModeChanged = { viewModel.setThemeColorMode(it) },
                        onCrossfadeToggled = { enabled -> viewModel.setCrossfadeParams(enabled, playState.crossfadeSeconds) },
                        onCrossfadeSecondsChanged = { sec -> viewModel.setCrossfadeParams(playState.crossfadeEnabled, sec) },
                        onBackgroundParamsChanged = { blur, alpha, scale, waveOpacity ->
                            viewModel.setBackgroundParams(blur, alpha, scale, waveOpacity)
                        },
                        onBackgroundContentScaleChanged = { scaleType ->
                            viewModel.setBackgroundContentScale(scaleType)
                        },
                        fluidBgEnabled = fluidBgEnabled,
                        onFluidBgToggled = { viewModel.setFluidBgEnabled(it) },
                        waveSpeed = waveSpeed,
                        waveRoughness = waveRoughness,
                        waveColorStyle = waveColorStyle,
                        onWaveSpeedChanged = { viewModel.setWaveSpeed(it) },
                        onWaveRoughnessChanged = { viewModel.setWaveRoughness(it) },
                        onWaveColorStyleChanged = { viewModel.setWaveColorStyle(it) }
                    )
                }
            }

            // Expanding Full Screen Music Core Player Sheet
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut()
            ) {
                FullMusicPlayerSheet(
                    state = playState,
                    trackColor = animatedTrackColor,
                    onCollapse = { isPlayerExpanded = false },
                    onPlayPauseToggle = { viewModel.togglePlayback() },
                    onPrevious = { viewModel.skipPrev() },
                    onNext = { viewModel.skipNext() },
                    onSeek = { viewModel.seekTo(it) },
                    onShuffleToggle = { viewModel.toggleShuffle() },
                    onRepeatToggle = { viewModel.toggleRepeat() },
                    onQueueToggle = { isQueueExpanded = true }
                )
            }

            // Expanding Full Screen Music Queue Sheet
            AnimatedVisibility(
                visible = isPlayerExpanded && isQueueExpanded,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut()
            ) {
                QueuePlayerSheet(
                    state = playState,
                    trackColor = animatedTrackColor,
                    onCollapse = { isQueueExpanded = false },
                    onSongSelected = { song ->
                        val idx = playState.currentQueue.indexOf(song)
                        if (idx != -1) {
                            viewModel.playSong(song, playState.currentQueue)
                        }
                    }
                )
            }

            // Playlist custom creation dialog trigger
            if (showCreatePlaylistDialog) {
                CreatePlaylistDialog(
                    onDismiss = { showCreatePlaylistDialog = false },
                    onConfirm = { name ->
                        viewModel.createPlaylist(name)
                        showCreatePlaylistDialog = false
                    }
                )
            }

            // Map track allocation to playlists dialog trigger
            if (showPlaylistSelectorDialog && selectedSongForPlaylist != null) {
                PlaylistSelectionDialog(
                    playlists = playlists,
                    song = selectedSongForPlaylist!!,
                    onDismiss = {
                        showPlaylistSelectorDialog = false
                        selectedSongForPlaylist = null
                    },
                    onPlaylistSelected = { playlist ->
                        viewModel.addSongToPlaylist(playlist.id, selectedSongForPlaylist!!.id)
                        showPlaylistSelectorDialog = false
                        selectedSongForPlaylist = null
                    }
                )
            }
        }
    }
}
}
}

// --- Composite UI Tab Switcher Navigation ---

@Composable
fun TabNavigationBar(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar(
        containerColor = CosmicSurface.copy(alpha = 0.60f),
        contentColor = TextPrimary,
        tonalElevation = 8.dp,
        modifier = Modifier
            .shadow(16.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val tabs = listOf(
            Triple("Songs", Icons.Default.MusicNote, "Songs"),
            Triple("Playlists", Icons.Default.QueueMusic, "Playlists"),
            Triple("Favorites", Icons.Default.Favorite, "Favorites"),
            Triple("Settings", Icons.Default.Tune, "Settings")
        )

        tabs.forEach { (tabName, icon, label) ->
            val active = activeTab == tabName
            NavigationBarItem(
                selected = active,
                onClick = { onTabSelected(tabName) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (active) CyberEmerald else TextSecondary
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) CyberEmerald else TextSecondary
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = CosmicSurfaceValue
                ),
                modifier = Modifier.testTag("tab_$tabName")
            )
        }
    }
}

// --- Songs Library Dashboard Tab ---

@Composable
fun SongsDashboard(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    playbackProgress: Long = 0L,
    sortOrder: SongSortOrder,
    onSortOrderChange: (SongSortOrder) -> Unit,
    onSongSelected: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onLaunchPicker: () -> Unit,
    onLaunchFolderPicker: () -> Unit,
    onTriggerScan: () -> Unit,
    onPlayNext: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        // App decorative header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val trackColors = LocalTrackColors.current
                Text(
                    text = "Acoustic",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        brush = Brush.horizontalGradient(trackColors),
                        fontWeight = FontWeight.ExtraBold,
                        shadow = Shadow(color = trackColors.getOrNull(0)?.copy(alpha = 0.3f) ?: CyberEmerald.copy(0.3f), blurRadius = 8f)
                    )
                )
                Text(
                    text = "Your High-Fidelity Local Player",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Premium horizontal ambient spectral visualizer right on the dashboard
                PlayingVisualizer(
                    isPlaying = isPlaying,
                    color = CyberEmerald,
                    modifier = Modifier.width(180.dp).height(8.dp),
                    barCount = 20,
                    gapFraction = 0.22f,
                    songId = currentSong?.id ?: 0L,
                    songTitle = currentSong?.title ?: "",
                    playbackProgress = playbackProgress
                )
            }

            // Quick Actions Imports & Local Scans
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onLaunchPicker,
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceValue),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("import_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Import Files",
                        tint = CyberEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("File", fontSize = 11.sp, color = TextPrimary)
                }

                Button(
                    onClick = onLaunchFolderPicker,
                    colors = ButtonDefaults.buttonColors(containerColor = CosmicSurfaceValue),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("import_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Import Folder",
                        tint = CyberEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Folder", fontSize = 11.sp, color = TextPrimary)
                }

                Button(
                    onClick = onTriggerScan,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("scan_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Scan Media Store",
                        tint = CosmicDarkBg,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Scan", fontSize = 11.sp, color = CosmicDarkBg, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (songs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Text(
                        text = "Sort:",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                items(SongSortOrder.values()) { order ->
                    val isSelected = sortOrder == order
                    val chipBg = if (isSelected) CyberEmerald.copy(alpha = 0.2f) else CosmicSurfaceValue
                    val chipBorder = if (isSelected) CyberEmerald else Color.Transparent
                    val chipTextCol = if (isSelected) CyberEmerald else TextSecondary
                    val chipIcon = when (order) {
                        SongSortOrder.DATE_ADDED_DESC -> Icons.Default.ArrowDownward
                        SongSortOrder.DATE_ADDED_ASC -> Icons.Default.ArrowUpward
                        SongSortOrder.TITLE_ASC -> Icons.Default.Sort
                        SongSortOrder.ARTIST_ASC -> Icons.Default.Person
                    }
                    val label = when (order) {
                        SongSortOrder.DATE_ADDED_DESC -> "Newest"
                        SongSortOrder.DATE_ADDED_ASC -> "Oldest"
                        SongSortOrder.TITLE_ASC -> "Title"
                        SongSortOrder.ARTIST_ASC -> "Artist"
                    }

                    Surface(
                        onClick = { onSortOrderChange(order) },
                        selected = isSelected,
                        shape = RoundedCornerShape(12.dp),
                        color = chipBg,
                        border = BorderStroke(1.dp, chipBorder),
                        modifier = Modifier.testTag("sort_chip_${label.lowercase(java.util.Locale.getDefault())}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = chipIcon,
                                contentDescription = label,
                                tint = chipTextCol,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = label,
                                color = chipTextCol,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Empty database fallback placeholder
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(CosmicSurface)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicOff,
                            contentDescription = "No Local Tracks Available",
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your Audio Library is Empty",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Click 'Scan Library' to index device audio files or 'Import' to copy audio clips directly from files and folders.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Display Scrollable list of songs
            Text(
                text = "${songs.size} Track${if (songs.size > 1) "s" else ""}",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val context = androidx.compose.ui.platform.LocalContext.current
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("songs_list")
            ) {
                items(songs, key = { it.id }) { song ->
                    SongTrackItemCard(
                        song = song,
                        isActive = currentSong?.id == song.id,
                        isPlaying = isPlaying,
                        onClick = { onSongSelected(song) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onDeleteSong = { onDeleteSong(song) },
                        onPlayNext = {
                            onPlayNext(song)
                            android.widget.Toast.makeText(context, "Added '${song.title}' to play next", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

// --- Playing Music Visualizer micro component ---

fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = start.alpha + (end.alpha - start.alpha) * fraction
    )
}

@Composable
fun PlayingVisualizer(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barCount: Int = 12,
    gapFraction: Float = 0.35f,
    songId: Long = 0L,
    songTitle: String = "",
    playbackProgress: Long = 0L
) {
    // Columns are completely disabled per user feedback
}

// --- Composite Specific Song Item Card Layout ---

@Composable
fun SongTrackItemCard(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDeleteSong: () -> Unit,
    onPlayNext: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    val trackColor = LocalCyberEmerald.current
    val blendedSurface = lerpColor(CosmicSurface, trackColor, 0.08f)
    val blendedActive = lerpColor(CosmicSurfaceValue, trackColor, 0.22f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .onSwipeGesture(
                onSwipeRight = { onPlayNext?.invoke() }
            )
            .testTag("song_card_${song.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = (if (isActive) blendedActive else blendedSurface).copy(alpha = 0.60f)
        ),
        border = if (isActive) {
            val trackColors = LocalTrackColors.current
            BorderStroke(
                width = 1.3.dp,
                brush = Brush.horizontalGradient(trackColors)
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Numeric identifier Box icon or Album art
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isActive) CyberEmerald.copy(0.15f) else CosmicSurfaceValue),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtPath != null) {
                    AsyncImage(
                        model = song.albumArtPath,
                        contentDescription = "Cover art thumbnail photo for ${song.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (isActive) Icons.Default.VolumeUp else Icons.Default.MusicNote,
                        contentDescription = "Audio track clip node icon indicator",
                        tint = if (isActive) CyberEmerald else ElectricCyan
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Body Details Column text
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val trackColors = LocalTrackColors.current
                    Text(
                        text = song.title,
                        style = if (isActive) {
                            LocalTextStyle.current.copy(
                                brush = Brush.horizontalGradient(trackColors),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        } else {
                            LocalTextStyle.current.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        PlayingVisualizer(
                            isPlaying = isPlaying,
                            color = CyberEmerald,
                            modifier = Modifier.size(20.dp, 12.dp),
                            barCount = 4,
                            songId = song.id,
                            songTitle = song.title
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration metrics
            Text(
                text = formatDuration(song.duration),
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Favorite heart outline/filled button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag("fav_toggle_${song.id}")
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Toggle Favorite song status flag",
                    tint = if (song.isFavorite) CyberEmerald else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Overflow Options Context dropdown menu
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.testTag("menu_button_${song.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Track Option parameters dropdown panel trigger",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(CosmicSurfaceValue)
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Next", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Queue, contentDescription = null, tint = CyberEmerald) },
                        onClick = {
                            showMenu = false
                            onPlayNext?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add To Playlist", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = ElectricCyan) },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Song", color = ErrorRed) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
                        onClick = {
                            showMenu = false
                            onDeleteSong()
                        }
                    )
                }
            }
        }
    }
}

// --- Playlists Dashboard Tab ---

@Composable
fun PlaylistsDashboard(
    playlists: List<Playlist>,
    selectedPlaylistId: Long?,
    playlistSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    playbackProgress: Long = 0L,
    onCreatePlaylist: () -> Unit,
    onSelectPlaylist: (Long?) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onPlaySongInPlaylist: (Song) -> Unit,
    onRemoveSongFromPlaylist: (Long, Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (selectedPlaylistId == null) {
            // Show master list of playlists
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val trackColors = LocalTrackColors.current
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            brush = Brush.horizontalGradient(trackColors),
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                    Text(
                        text = "Organize music by vibes and moods",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    PlayingVisualizer(
                        isPlaying = isPlaying,
                        color = CyberEmerald.copy(alpha = 0.7f),
                        modifier = Modifier.width(120.dp).height(4.dp),
                        barCount = 14,
                        gapFraction = 0.25f,
                        songId = currentSong?.id ?: 0L,
                        songTitle = currentSong?.title ?: "",
                        playbackProgress = playbackProgress
                    )
                }

                Button(
                    onClick = onCreatePlaylist,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("create_playlist_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create playlist index folder",
                        tint = CosmicDarkBg
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", color = CosmicDarkBg, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Playlists Available",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'New' to compile a clean track playlist.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("playlists_list")
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPlaylist(playlist.id) }
                                .testTag("playlist_card_${playlist.id}"),
                            colors = CardDefaults.cardColors(containerColor = CosmicSurface.copy(alpha = 0.60f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CosmicSurfaceValue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LibraryMusic,
                                        contentDescription = null,
                                        tint = CyberEmerald
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = "Custom User Playlist",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                IconButton(
                                    onClick = { onDeletePlaylist(playlist.id) },
                                    modifier = Modifier.testTag("delete_playlist_${playlist.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete full custom playlist directory structure",
                                        tint = TextMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Selected/Focus View inside a specific Playlist
            val activePlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onSelectPlaylist(null) },
                    modifier = Modifier.testTag("playlist_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Master List of custom playlists",
                        tint = CyberEmerald
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    val trackColors = LocalTrackColors.current
                    Text(
                        text = activePlaylist?.name ?: "Playlist Info",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            brush = Brush.horizontalGradient(trackColors),
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlistSongs.size} Track${if (playlistSongs.size > 1) "s" else ""} tied locally",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (playlistSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Queue,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Playlist is Empty",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Go to 'Songs' tab and tap a track's overflow button to add.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(playlistSongs, key = { it.id }) { song ->
                        val trackColor = LocalCyberEmerald.current
                        val isActive = currentSong?.id == song.id
                        val blendedSurface = lerpColor(CosmicSurface, trackColor, 0.08f)
                        val blendedActive = lerpColor(CosmicSurfaceValue, trackColor, 0.22f)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaySongInPlaylist(song) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = (if (isActive) blendedActive else blendedSurface).copy(alpha = 0.60f)
                            ),
                            border = if (isActive) {
                                val trackColors = LocalTrackColors.current
                                BorderStroke(
                                    width = 1.3.dp,
                                    brush = Brush.horizontalGradient(trackColors)
                                )
                            } else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CosmicSurfaceValue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (currentSong?.id == song.id) Icons.Default.VolumeUp else Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (currentSong?.id == song.id) CyberEmerald else TextSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = if (currentSong?.id == song.id) CyberEmerald else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = formatDuration(song.duration),
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                IconButton(
                                    onClick = { onRemoveSongFromPlaylist(selectedPlaylistId, song.id) },
                                    modifier = Modifier.testTag("remove_from_playlist_${song.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove single track pointer from custom playlist mapping folder",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Favorites Dashboard Tab ---

@Composable
fun FavoritesDashboard(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    playbackProgress: Long = 0L,
    onSongSelected: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onPlayNext: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column {
            val trackColors = LocalTrackColors.current
            Text(
                text = "Favorites",
                style = MaterialTheme.typography.headlineMedium.copy(
                    brush = Brush.horizontalGradient(trackColors),
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Text(
                text = "Your absolute favorite curated tracks on repeat",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            PlayingVisualizer(
                isPlaying = isPlaying,
                color = CyberEmerald.copy(alpha = 0.7f),
                modifier = Modifier.width(120.dp).height(8.dp),
                barCount = 14,
                gapFraction = 0.25f,
                songId = currentSong?.id ?: 0L,
                songTitle = currentSong?.title ?: "",
                playbackProgress = playbackProgress
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Curate Your Standouts!",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mark heart icons on active list files to display tracks here instantly.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            val context = androidx.compose.ui.platform.LocalContext.current
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("favorites_list")
            ) {
                items(songs, key = { it.id }) { song ->
                    // Standard Track Card reusable block
                    SongTrackItemCard(
                        song = song,
                        isActive = currentSong?.id == song.id,
                        isPlaying = isPlaying,
                        onClick = { onSongSelected(song) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onDeleteSong = {}, // disable full disk purging from favorites stream
                        onPlayNext = {
                            onPlayNext(song)
                            android.widget.Toast.makeText(context, "Added '${song.title}' to play next", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

// --- Settings/Crossfade Dashboard Tab ---

@Composable
fun SettingsDashboard(
    state: PlaybackState,
    themeMode: MusicViewModel.ThemeColorMode,
    onThemeModeChanged: (MusicViewModel.ThemeColorMode) -> Unit,
    onCrossfadeToggled: (Boolean) -> Unit,
    onCrossfadeSecondsChanged: (Int) -> Unit,
    onBackgroundParamsChanged: (Float, Float, Float, Float) -> Unit,
    onBackgroundContentScaleChanged: (String) -> Unit,
    fluidBgEnabled: Boolean,
    onFluidBgToggled: (Boolean) -> Unit,
    waveSpeed: Float,
    waveRoughness: Float,
    waveColorStyle: String,
    onWaveSpeedChanged: (Float) -> Unit,
    onWaveRoughnessChanged: (Float) -> Unit,
    onWaveColorStyleChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val trackColors = LocalTrackColors.current
        Text(
            text = "Fine-Tuning",
            style = MaterialTheme.typography.headlineMedium.copy(
                brush = Brush.horizontalGradient(trackColors),
                fontWeight = FontWeight.ExtraBold
            )
        )
        Text(
            text = "Accompany audio loops with master settings",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Dynamic Accent Theme Color Source Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Dynamic Theme Color Source",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Control how track theme colors are extracted. Selecting Complementary shifts the hue 180° to get colors not present in the art.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf(
                        Triple(MusicViewModel.ThemeColorMode.EXACT_ART_COLORS, "Exact Artwork Colors", "Generates a dynamic multi-colored layout exact to the artwork's native colors."),
                        Triple(MusicViewModel.ThemeColorMode.ART_COMPLEMENTARY, "Complementary Art Accent", "Inverts art hue to pick striking colors NOT present in the art!"),
                        Triple(MusicViewModel.ThemeColorMode.ART_VIBRANT, "Art Dominant (Vibrant)", "Extracts the exact dominant vocal/instrumental color."),
                        Triple(MusicViewModel.ThemeColorMode.SONG_AURA, "Holographic Aura Shape", "Generates cohesive aura themes based on song titles.")
                    )

                    modes.forEach { (modeOption, title, description) ->
                        val isSelected = themeMode == modeOption
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onThemeModeChanged(modeOption) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) CyberEmerald else Color.Transparent
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) CosmicSurfaceValue else CosmicSurface.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onThemeModeChanged(modeOption) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = CyberEmerald,
                                        unselectedColor = TextSecondary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = title,
                                        color = if (isSelected) CyberEmerald else TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = description,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card Crossfade wrapper block
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DJ Seamless Crossfade",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Blend adjacent tracks seamlessly for non-stop play gaps.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = state.crossfadeEnabled,
                        onCheckedChange = onCrossfadeToggled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CosmicDarkBg,
                            checkedTrackColor = CyberEmerald,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CosmicSurfaceValue
                        ),
                        modifier = Modifier.testTag("crossfade_switch")
                    )
                }

                if (state.crossfadeEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Crossfade Period: ${state.crossfadeSeconds} Seconds",
                        color = ElectricCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = state.crossfadeSeconds.toFloat(),
                        onValueChange = { onCrossfadeSecondsChanged(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberEmerald,
                            activeTrackColor = CyberEmerald,
                            inactiveTrackColor = CosmicSurfaceValue
                        ),
                        modifier = Modifier.testTag("crossfade_slider")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Background visual settings card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Background Visual Canvas",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Tweak the blur intensity, scale, and alpha properties of the rotating background artwork and flowing ambient waves.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Fluid Simulation Switch row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onFluidBgToggled(!fluidBgEnabled) }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Interactive Fluid Waves",
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "A colorful WebGL-style fluid sim that ripples with touch dragging and spikes dynamically to music beats.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = fluidBgEnabled,
                        onCheckedChange = onFluidBgToggled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CosmicDarkBg,
                            checkedTrackColor = CyberEmerald,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = CosmicSurfaceValue
                        ),
                        modifier = Modifier.testTag("fluid_sim_switch")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Slider 4: Wave Opacity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Flowing Wave Intensity: ${(state.bgWaveOpacity * 100).toInt()}%",
                        color = ElectricCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = state.bgWaveOpacity,
                    onValueChange = {
                        onBackgroundParamsChanged(state.bgBlurRadius, state.bgAlpha, state.bgScale, it)
                    },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = CosmicSurfaceValue
                    ),
                    modifier = Modifier.testTag("bg_wave_slider")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Wave customization parameters card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Wave Engine Configurations",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Configure physics coefficients of the real-time fluid ripples and visual waves.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Wave color style selection
                Text(
                    text = "Wave Color Style Theme",
                    color = ElectricCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                val styles = listOf(
                    "Dynamic Track",
                    "Deep Sea Navy",
                    "Midnight Violet",
                    "Toxic Emerald",
                    "Cyber Sunset"
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(styles) { style ->
                        val isSelected = waveColorStyle == style
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) CyberEmerald.copy(alpha = 0.2f) else CosmicSurfaceValue
                                )
                                .border(
                                    BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) CyberEmerald else Color.White.copy(alpha = 0.12f)
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { onWaveColorStyleChanged(style) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = style,
                                color = if (isSelected) CyberEmerald else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Slider for Wave Speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Wave Velocity / Speed: ${String.format("%.1fx", waveSpeed)}",
                        color = ElectricCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = waveSpeed,
                    onValueChange = onWaveSpeedChanged,
                    valueRange = 0.2f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = CosmicSurfaceValue
                    ),
                    modifier = Modifier.testTag("wave_speed_slider")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Slider for Wave Roughness
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Wave Choppiness / Roughness: ${String.format("%.1fx", waveRoughness)}",
                        color = ElectricCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = waveRoughness,
                    onValueChange = onWaveRoughnessChanged,
                    valueRange = 0.0f..3.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan,
                        inactiveTrackColor = CosmicSurfaceValue
                    ),
                    modifier = Modifier.testTag("wave_roughness_slider")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Generic Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CosmicSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Acoustic Metadata Engine",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version 1.2.5 • Developed under Dark Aesthetics. High-fidelity codecs supported: FLAC, MP3, WAV, AAC, M4A.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// --- Dynamic Collapsed Mini Bottom Player Bar ---

@Composable
fun CollapsedMiniPlayer(
    state: PlaybackState,
    onPlayPauseToggle: () -> Unit,
    onNext: () -> Unit,
    onExpandToggle: () -> Unit
) {
    val currentSong = state.currentSong ?: return
    val accentColor = CyberEmerald

    // Infinite Rotation animation of a mini CD icon while playing
    val infiniteTransition = rememberInfiniteTransition(label = "MiniCDRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CDRotationAngle"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .onSwipeGesture(
                onSwipeUp = onExpandToggle
            )
            .clickable { onExpandToggle() }
            .testTag("mini_player"),
        color = lerpColor(CosmicSurface, accentColor, 0.12f).copy(alpha = 0.60f),
        shadowElevation = 12.dp,
        border = BorderStroke(
            width = 1.3.dp,
            brush = Brush.horizontalGradient(LocalTrackColors.current)
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Beautiful rounded-corner square artwork thumbnail
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CosmicDarkBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentSong.albumArtPath != null) {
                        AsyncImage(
                            model = currentSong.albumArtPath,
                            contentDescription = "Cover art thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val trackColors = LocalTrackColors.current
                        Text(
                            text = currentSong.title,
                            style = LocalTextStyle.current.copy(
                                brush = Brush.horizontalGradient(trackColors),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        PlayingVisualizer(
                            isPlaying = state.isPlaying,
                            color = CyberEmerald,
                            modifier = Modifier.size(24.dp, 12.dp),
                            barCount = 6,
                            songId = currentSong.id,
                            songTitle = currentSong.title,
                            playbackProgress = state.progress
                        )
                    }
                    Text(
                        text = currentSong.artist,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Mini Quick controls
                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier.testTag("mini_play_pause")
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Quick Toggle Active playback status",
                        tint = CyberEmerald,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.testTag("mini_skip_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Quick skip active track forwards index",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // High precision underlying loading linear indicator styled with custom multicolored gradient!
            val progressRatio = if (state.duration > 0) state.progress.toFloat() / state.duration else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(CosmicDarkBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressRatio)
                        .background(Brush.horizontalGradient(LocalTrackColors.current))
                )
            }
        }
    }
}

// --- Expanded Dynamic Full Visual Music Player Screen ---

@Composable
fun FullMusicPlayerSheet(
    state: PlaybackState,
    trackColor: Color,
    onCollapse: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onQueueToggle: () -> Unit
) {
    val currentSong = state.currentSong ?: return
    val animatedTrackColor by animateColorAsState(trackColor, label = "fullPlayerTrackColor")

    var sliderDraggingValue by remember { mutableStateOf<Float?>(null) }

    // CD rotation animation for immersive CD visual
    val infiniteTransition = rememberInfiniteTransition(label = "CDSpinLarge")
    val largeRotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "CDRotationAngleLarge"
    )

    // Dynamic Pulsing ambient glow animation following cover-art tempo
    val infinitePulse = rememberInfiniteTransition(label = "VinylGlowPulse")
    val pulseRatio by infinitePulse.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScaleAnim"
    )
    val finalGlowScale = if (state.isPlaying) pulseRatio else 1.0f

    // Premium organic breathing float animation for artwork
    val visualizerTransition = rememberInfiniteTransition(label = "VisualizerBreathAnim")
    val beatScale by visualizerTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "artScaleBreath"
    )
    val finalArtworkScale = if (state.isPlaying) beatScale else 1.0f

    val playerBgColors = com.example.ui.theme.LocalTrackColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        playerBgColors.getOrElse(0) { trackColor }.copy(alpha = 0.55f),
                        playerBgColors.getOrElse(1) { trackColor }.copy(alpha = 0.35f),
                        CosmicDarkBg.copy(alpha = 0.94f)
                    )
                )
            )
            .pointerInput(Unit) {} // Absolutely absorb click propagation so background main menu icons are untouched!
            .onSwipeGesture(
                onSwipeUp = onQueueToggle,
                onSwipeDown = onCollapse
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("full_player_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("full_player_back")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize full music player sheet overlay",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0F14).copy(alpha = 0.7f))
                        .background(animatedTrackColor.copy(alpha = 0.20f))
                        .border(1.dp, animatedTrackColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NOW PLAYING",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }

                IconButton(
                    onClick = onQueueToggle,
                    modifier = Modifier.testTag("full_player_queue_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Show active playback queue overlay",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Big artwork card wrapper with multi-layered pulsing ambient glows
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Secondary outer expanded pulse glow (noticeable breathing wave)
                val pulseOuterScale = 1.0f + (finalGlowScale - 1.0f) * 1.6f
                val haloColors = com.example.ui.theme.LocalTrackColors.current
                Box(
                    modifier = Modifier
                        .size(310.dp * pulseOuterScale)
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(
                                    colors = listOf(
                                        haloColors.getOrElse(0) { trackColor }.copy(alpha = 0.50f),
                                        haloColors.getOrElse(1) { trackColor }.copy(alpha = 0.15f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width / 2, size.height / 2),
                                    radius = size.width / 1.5f
                                )
                            )
                        }
                )

                // Primary inner rich pulse glow
                Box(
                    modifier = Modifier
                        .size(260.dp * finalGlowScale)
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(
                                    colors = listOf(
                                        haloColors.getOrElse(1) { trackColor }.copy(alpha = 0.70f),
                                        haloColors.getOrElse(2) { trackColor }.copy(alpha = 0.25f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width / 2, size.height / 2),
                                    radius = size.width / 1.6f
                                )
                            )
                        }
                )

                // Beautiful rounded-corner square artwork card with bouncing beat scale animation
                val cardTrackColors = com.example.ui.theme.LocalTrackColors.current
                Card(
                    modifier = Modifier
                        .size(260.dp)
                        .scale(finalArtworkScale)
                        .shadow(24.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicDarkBg),
                    border = BorderStroke(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                cardTrackColors.getOrElse(0) { trackColor },
                                cardTrackColors.getOrElse(1) { trackColor },
                                cardTrackColors.getOrElse(2) { trackColor }
                            )
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentSong.albumArtPath != null) {
                            AsyncImage(
                                model = currentSong.albumArtPath,
                                contentDescription = "Album Case Artwork Cover Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(trackColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Default Music Note Icon",
                                    tint = trackColor,
                                    modifier = Modifier.size(84.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Text Metadata Detail wrapper block
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0D0F14).copy(alpha = 0.75f))
                    .background(animatedTrackColor.copy(alpha = 0.15f))
                    .border(1.dp, animatedTrackColor.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
                    .padding(vertical = 12.dp, horizontal = 24.dp)
            ) {
                val trackColors = LocalTrackColors.current
                Text(
                    text = currentSong.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        brush = Brush.horizontalGradient(trackColors),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentSong.artist,
                    color = CyberEmerald,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                // Beautiful live equalizer visualizer elements bouncing to the music!
                PlayingVisualizer(
                    isPlaying = state.isPlaying,
                    color = CyberEmerald,
                    modifier = Modifier.width(240.dp).height(24.dp),
                    barCount = 32,
                    songId = currentSong.id,
                    songTitle = currentSong.title,
                    playbackProgress = state.progress
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Slider seeking widgets
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D0F14).copy(alpha = 0.75f))
                    .background(animatedTrackColor.copy(alpha = 0.15f))
                    .border(1.dp, animatedTrackColor.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                val currentProgress = sliderDraggingValue ?: state.progress.toFloat()
                val progressMax = maxOf(state.duration.toFloat(), 1f)

                Slider(
                    value = currentProgress.coerceIn(0f, progressMax),
                    onValueChange = { sliderDraggingValue = it },
                    onValueChangeFinished = {
                        sliderDraggingValue?.let {
                            onSeek(it.toLong())
                        }
                        sliderDraggingValue = null
                    },
                    valueRange = 0f..progressMax,
                    colors = SliderDefaults.colors(
                        thumbColor = CyberEmerald,
                        activeTrackColor = CyberEmerald,
                        inactiveTrackColor = CosmicSurfaceValue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("track_progress_slider")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentProgress.toLong()),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatDuration(state.duration),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Playback controls Row of buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button toggle
                IconButton(
                    onClick = onShuffleToggle,
                    modifier = Modifier.testTag("full_shuffle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Toggle Shuffle Order",
                        tint = if (state.isShuffle) CyberEmerald else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous Button
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.testTag("full_prev")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Skip back track previous index",
                        tint = TextPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Big play button with circle surround
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(CyberEmerald)
                        .clickable { onPlayPauseToggle() }
                        .testTag("full_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle playing session",
                        tint = CosmicDarkBg,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next Button
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.testTag("full_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip active track forwards index",
                        tint = TextPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Repeat Button toggle
                IconButton(
                    onClick = onRepeatToggle,
                    modifier = Modifier.testTag("full_repeat")
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Toggle Repeat song mode triggers",
                        tint = if (state.isRepeat) CyberEmerald else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// --- Dynamic Full Screen Playback Queue Sheet View Overlay ---

@Composable
fun QueuePlayerSheet(
    state: PlaybackState,
    trackColor: Color,
    onCollapse: () -> Unit,
    onSongSelected: (Song) -> Unit
) {
    val animatedTrackColor by animateColorAsState(trackColor, label = "queuePlayerTrackColor")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CosmicDarkBg)
            .pointerInput(Unit) {} // Absolutely absorb click propagation so background elements are untouched!
            .onSwipeGesture(
                onSwipeDown = onCollapse
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("queue_player_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("queue_back")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse queue view overlay",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0F14).copy(alpha = 0.7f))
                        .background(animatedTrackColor.copy(alpha = 0.20f))
                        .border(1.dp, animatedTrackColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PLAYBACK QUEUE",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))
            }

            // Central content layout
            val queue = state.currentQueue
            val currentIndex = state.queueIndex

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Queue is empty",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                val listState = rememberLazyListState()
                val density = androidx.compose.ui.platform.LocalDensity.current

                val pastSongs = if (currentIndex > 0) queue.take(currentIndex) else emptyList()
                val nextSongs = if (currentIndex >= -1 && currentIndex < queue.size - 1) {
                    queue.drop(currentIndex + 1)
                } else {
                    emptyList()
                }

                LaunchedEffect(currentIndex, queue) {
                    if (currentIndex >= 0 && queue.isNotEmpty()) {
                        val targetItemIndex = if (pastSongs.isNotEmpty()) {
                            pastSongs.size + 2 // PREVIOUS SONGS header + pastSongs + NOW PLAYING header
                        } else {
                            1 // NOW PLAYING header
                        }
                        val centeredOffset = with(density) { -180.dp.roundToPx() }
                        listState.scrollToItem(maxOf(targetItemIndex, 0), centeredOffset)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // --- SECTION 1: PREVIOUS SONGS (Temporal order: past items sit above active item) ---
                    if (pastSongs.isNotEmpty()) {
                        item {
                            Text(
                                text = "PREVIOUS SONGS",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(pastSongs, key = { "past_${it.id}" }) { song ->
                            QueueSongItem(
                                song = song,
                                isActive = false,
                                isPast = true,
                                trackColor = trackColor,
                                onClick = { onSongSelected(song) }
                            )
                        }
                    }

                    // --- SECTION 2: NOW PLAYING (The central anchor node) ---
                    val currentSong = state.currentSong
                    if (currentSong != null) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "NOW PLAYING",
                                color = trackColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("queue_current_item"),
                                shape = RoundedCornerShape(12.dp),
                                color = trackColor.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, trackColor.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Artwork with rounded corners
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CosmicDarkBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (currentSong.albumArtPath != null) {
                                            AsyncImage(
                                                model = currentSong.albumArtPath,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(trackColor.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = trackColor,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentSong.title,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = currentSong.artist,
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    PlayingVisualizer(
                                        isPlaying = state.isPlaying,
                                        color = trackColor,
                                        modifier = Modifier.size(24.dp, 16.dp),
                                        barCount = 5,
                                        songId = currentSong.id,
                                        songTitle = currentSong.title,
                                        playbackProgress = state.progress
                                    )
                                }
                            }
                        }
                    }

                    // --- SECTION 3: UP NEXT ---
                    if (nextSongs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "UP NEXT",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(nextSongs, key = { "next_${it.id}" }) { song ->
                            QueueSongItem(
                                song = song,
                                isActive = false,
                                isPast = false,
                                trackColor = trackColor,
                                onClick = { onSongSelected(song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QueueSongItem(
    song: Song,
    isActive: Boolean,
    isPast: Boolean,
    trackColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("queue_item_${song.title.lowercase(java.util.Locale.getDefault()).replace(" ", "_")}"),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork representation
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CosmicSurface),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtPath != null) {
                    AsyncImage(
                        model = song.albumArtPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (isPast) 0.5f else 1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(trackColor.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = trackColor.copy(alpha = if (isPast) 0.3f else 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isPast) TextMuted else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(song.duration),
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// --- Universal Gesture Navigation Swipe Helper Modifier ---

fun Modifier.onSwipeGesture(
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null
): Modifier {
    val hasHorizontal = onSwipeLeft != null || onSwipeRight != null
    val hasVertical = onSwipeUp != null || onSwipeDown != null

    return if (hasHorizontal && !hasVertical) {
        this.pointerInput(onSwipeLeft, onSwipeRight) {
            var totalDragX = 0f
            detectHorizontalDragGestures(
                onDragStart = { totalDragX = 0f },
                onDragEnd = {
                    val minThreshold = 50f
                    if (totalDragX < -minThreshold) {
                        onSwipeLeft?.invoke()
                    } else if (totalDragX > minThreshold) {
                        onSwipeRight?.invoke()
                    }
                },
                onDragCancel = {},
                onHorizontalDrag = { change, dragAmount ->
                    totalDragX += dragAmount
                    change.consume()
                }
            )
        }
    } else if (hasVertical && !hasHorizontal) {
        this.pointerInput(onSwipeUp, onSwipeDown) {
            var totalDragY = 0f
            detectVerticalDragGestures(
                onDragStart = { totalDragY = 0f },
                onDragEnd = {
                    val minThreshold = 50f
                    if (totalDragY < -minThreshold) {
                        onSwipeUp?.invoke()
                    } else if (totalDragY > minThreshold) {
                        onSwipeDown?.invoke()
                    }
                },
                onDragCancel = {},
                onVerticalDrag = { change, dragAmount ->
                    totalDragY += dragAmount
                    change.consume()
                }
            )
        }
    } else {
        this.pointerInput(onSwipeUp, onSwipeDown, onSwipeLeft, onSwipeRight) {
            var totalDragY = 0f
            var totalDragX = 0f
            detectDragGestures(
                onDragStart = {
                    totalDragY = 0f
                    totalDragX = 0f
                },
                onDragEnd = {
                    val minThreshold = 50f
                    if (Math.abs(totalDragY) > Math.abs(totalDragX)) {
                        if (totalDragY < -minThreshold) {
                            onSwipeUp?.invoke()
                        } else if (totalDragY > minThreshold) {
                            onSwipeDown?.invoke()
                        }
                    } else {
                        if (totalDragX < -minThreshold) {
                            onSwipeLeft?.invoke()
                        } else if (totalDragX > minThreshold) {
                            onSwipeRight?.invoke()
                        }
                    }
                },
                onDragCancel = {},
                onDrag = { change, dragAmount ->
                    totalDragY += dragAmount.y
                    totalDragX += dragAmount.x
                    val isMainlyHorizontal = Math.abs(totalDragX) > Math.abs(totalDragY)
                    if (isMainlyHorizontal && hasHorizontal && Math.abs(totalDragX) > 15f) {
                        change.consume()
                    } else if (!isMainlyHorizontal && hasVertical && Math.abs(totalDragY) > 15f) {
                        change.consume()
                    }
                }
            )
        }
    }
}

// --- Dynamic Create Playlist Dialog ---

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "New Playlist",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    "Compile a custom library category playlist:",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Playlist Name", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberEmerald,
                        unfocusedBorderColor = CosmicSurfaceValue,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_input_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textValue.trim().isNotEmpty()) {
                        onConfirm(textValue.trim())
                    }
                },
                enabled = textValue.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
                modifier = Modifier.testTag("playlist_dialog_confirm")
            ) {
                Text("Compile", color = CosmicDarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextSecondary)
            }
        },
        containerColor = CosmicSurfaceValue
    )
}

// --- Dynamic Map Song To Playlist Selector Dialog ---

@Composable
fun PlaylistSelectionDialog(
    playlists: List<Playlist>,
    song: Song,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add Track to Playlist",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    "Select target destination category folder for '${song.title}':",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (playlists.isEmpty()) {
                    Text(
                        text = "Create a custom playlist directory in the 'Playlists' dashboard before mapping individual track clips.",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .testTag("playlist_selector_list")
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlaylistSelected(playlist) }
                                    .testTag("selector_playlist_${playlist.id}"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = CosmicSurface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistAdd,
                                        contentDescription = null,
                                        tint = CyberEmerald
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = playlist.name,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        },
        containerColor = CosmicSurfaceValue
    )
}

// --- Dynamic Duration Metrics Converter Helper ---

@SuppressLint("DefaultLocale")
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

class FluidSimulation(val NX: Int = 1440, val NY: Int = 3168) {
    // Highly optimized internal grid dimension exactly matching the 1440:3168 aspect ratio
    val simNX = 273
    val simNY = 600
    val size = (simNX + 2) * (simNY + 2)
    
    val u = FloatArray(size)
    val v = FloatArray(size)
    val uPrev = FloatArray(size)
    val vPrev = FloatArray(size)
    
    val r = FloatArray(size)
    val g = FloatArray(size)
    val b = FloatArray(size)
    val rPrev = FloatArray(size)
    val gPrev = FloatArray(size)
    val bPrev = FloatArray(size)

    private val pixelBuffer = IntArray(simNX * simNY)
    var bitmap: Bitmap = Bitmap.createBitmap(simNX, simNY, Bitmap.Config.ARGB_8888)

    fun clear() {
        for (i in 0 until size) {
            u[i] = 0f; v[i] = 0f; uPrev[i] = 0f; vPrev[i] = 0f
            r[i] = 0f; g[i] = 0f; b[i] = 0f; rPrev[i] = 0f; gPrev[i] = 0f; bPrev[i] = 0f
        }
    }

    fun addDensity(x: Int, y: Int, amountR: Float, amountG: Float, amountB: Float, radius: Int = 2) {
        // Map high-resolution x/y coordinates to internal grid coordinates
        val mappedX = (x.toFloat() / NX * simNX).toInt().coerceIn(1, simNX)
        val mappedY = (y.toFloat() / NY * simNY).toInt().coerceIn(1, simNY)
        val stride = simNX + 2
        for (i in -radius..radius) {
            for (j in -radius..radius) {
                val gx = mappedX + i
                val gy = mappedY + j
                if (gx in 1..simNX && gy in 1..simNY) {
                    val idx = gx + gy * stride
                    val factor = 1.0f - (i*i + j*j).toFloat() / (radius*radius + 1)
                    if (factor > 0f) {
                        r[idx] = (r[idx] + amountR * factor).coerceIn(0f, 255f)
                        g[idx] = (g[idx] + amountG * factor).coerceIn(0f, 255f)
                        b[idx] = (b[idx] + amountB * factor).coerceIn(0f, 255f)
                    }
                }
            }
        }
    }

    fun addVelocity(x: Int, y: Int, amountU: Float, amountV: Float, radius: Int = 2) {
        // Map high-resolution x/y coordinates to internal grid coordinates
        val mappedX = (x.toFloat() / NX * simNX).toInt().coerceIn(1, simNX)
        val mappedY = (y.toFloat() / NY * simNY).toInt().coerceIn(1, simNY)
        val stride = simNX + 2
        for (i in -radius..radius) {
            for (j in -radius..radius) {
                val gx = mappedX + i
                val gy = mappedY + j
                if (gx in 1..simNX && gy in 1..simNY) {
                    val idx = gx + gy * stride
                    val factor = 1.0f - (i*i + j*j).toFloat() / (radius*radius + 1)
                    if (factor > 0f) {
                        u[idx] += amountU * factor
                        v[idx] += amountV * factor
                    }
                }
            }
        }
    }

    private fun IX(x: Int, y: Int): Int {
        return x + (simNX + 2) * y
    }

    private fun set_bnd(b: Int, x: FloatArray) {
        val stride = simNX + 2
        for (i in 1..simNX) {
            x[i] = if (b == 2) -x[i + stride] else x[i + stride]
            x[i + (simNY + 1) * stride] = if (b == 2) -x[i + simNY * stride] else x[i + simNY * stride]
        }
        for (j in 1..simNY) {
            val rowOffset = j * stride
            x[rowOffset] = if (b == 1) -x[1 + rowOffset] else x[1 + rowOffset]
            x[simNX + 1 + rowOffset] = if (b == 1) -x[simNX + rowOffset] else x[simNX + rowOffset]
        }
        
        val cornerY1 = (simNY + 1) * stride
        
        x[0] = 0.5f * (x[1] + x[stride])
        x[cornerY1] = 0.5f * (x[1 + cornerY1] + x[simNY * stride])
        x[simNX + 1] = 0.5f * (x[simNX] + x[simNX + 1 + stride])
        x[simNX + 1 + cornerY1] = 0.5f * (x[simNX + cornerY1] + x[simNX + 1 + simNY * stride])
    }

    private fun project(u: FloatArray, v: FloatArray, p: FloatArray, div: FloatArray) {
        val stride = simNX + 2
        val scale = -0.5f / simNX.toFloat()
        for (j in 1..simNY) {
            val rowOffset = j * stride
            val rowOffsetPrev = (j - 1) * stride
            val rowOffsetNext = (j + 1) * stride
            for (i in 1..simNX) {
                val idx = i + rowOffset
                div[idx] = scale * (
                    u[idx + 1] - u[idx - 1] +
                    v[i + rowOffsetNext] - v[i + rowOffsetPrev]
                )
                p[idx] = 0f
            }
        }
        set_bnd(0, div)
        set_bnd(0, p)

        for (k in 0 until 6) {
            for (j in 1..simNY) {
                val rowOffset = j * stride
                val rowOffsetPrev = (j - 1) * stride
                val rowOffsetNext = (j + 1) * stride
                for (i in 1..simNX) {
                    val idx = i + rowOffset
                    p[idx] = (div[idx] + p[idx - 1] + p[idx + 1] + p[i + rowOffsetPrev] + p[i + rowOffsetNext]) * 0.25f
                }
            }
            set_bnd(0, p)
        }

        val uScale = 0.5f * simNX
        val vScale = 0.5f * simNY
        for (j in 1..simNY) {
            val rowOffset = j * stride
            val rowOffsetPrev = (j - 1) * stride
            val rowOffsetNext = (j + 1) * stride
            for (i in 1..simNX) {
                val idx = i + rowOffset
                u[idx] -= uScale * (p[idx + 1] - p[idx - 1])
                v[idx] -= vScale * (p[i + rowOffsetNext] - p[i + rowOffsetPrev])
            }
        }
        set_bnd(1, u)
        set_bnd(2, v)
    }

    private fun advect(b: Int, d: FloatArray, d0: FloatArray, u: FloatArray, v: FloatArray, dt: Float) {
        val dt0_x = dt * simNX
        val dt0_y = dt * simNY
        val stride = simNX + 2
        for (j in 1..simNY) {
            val rowOffset = j * stride
            for (i in 1..simNX) {
                val idx = i + rowOffset
                var x = i - dt0_x * u[idx]
                var y = j - dt0_y * v[idx]
                
                if (x < 0.5f) x = 0.5f
                if (x > simNX + 0.5f) x = simNX + 0.5f
                val i0 = x.toInt()
                val i1 = i0 + 1
                
                if (y < 0.5f) y = 0.5f
                if (y > simNY + 0.5f) y = simNY + 0.5f
                val j0 = y.toInt()
                val j1 = j0 + 1
                
                val s1 = x - i0
                val s0 = 1f - s1
                val t1 = y - j0
                val t0 = 1f - t1
                
                val row0 = j0 * stride
                val row1 = j1 * stride
                
                d[idx] = s0 * (t0 * d0[i0 + row0] + t1 * d0[i0 + row1]) +
                         s1 * (t0 * d0[i1 + row0] + t1 * d0[i1 + row1])
            }
        }
        set_bnd(b, d)
    }

    private fun diffuse(b: Int, x: FloatArray, x0: FloatArray, diff: Float, dt: Float) {
        val a = dt * diff * simNX * simNY
        val stride = simNX + 2
        val invDenom = 1f / (1f + 4f * a)
        for (k in 0 until 4) {
            for (j in 1..simNY) {
                val rowOffset = j * stride
                val rowOffsetPrev = (j - 1) * stride
                val rowOffsetNext = (j + 1) * stride
                for (i in 1..simNX) {
                    val idx = i + rowOffset
                    x[idx] = (x0[idx] + a * (x[idx - 1] + x[idx + 1] + x[i + rowOffsetPrev] + x[i + rowOffsetNext])) * invDenom
                }
            }
            set_bnd(b, x)
        }
    }

    fun step(dt: Float, diffusionAndDyeDecay: Float = 0.982f) {
        diffuse(1, uPrev, u, 0.0001f, dt)
        diffuse(2, vPrev, v, 0.0001f, dt)
        project(uPrev, vPrev, u, v)

        advect(1, u, uPrev, uPrev, vPrev, dt)
        advect(2, v, vPrev, uPrev, vPrev, dt)
        project(u, v, uPrev, vPrev)

        advect(0, rPrev, r, u, v, dt)
        advect(0, gPrev, g, u, v, dt)
        advect(0, bPrev, b, u, v, dt)

        for (i in 0 until size) {
            r[i] = rPrev[i] * diffusionAndDyeDecay
            g[i] = gPrev[i] * diffusionAndDyeDecay
            b[i] = bPrev[i] * diffusionAndDyeDecay
            u[i] *= 0.95f
            v[i] *= 0.95f
        }
    }

    fun updateBitmap() {
        var pixelIndex = 0
        val stride = simNX + 2
        for (y in 1..simNY) {
            val rowOffset = y * stride
            for (x in 1..simNX) {
                val idx = x + rowOffset
                val red = r[idx].toInt().coerceIn(0, 255)
                val green = g[idx].toInt().coerceIn(0, 255)
                val blue = b[idx].toInt().coerceIn(0, 255)
                
                // Maximize alpha luminosity so neon colors are brilliantly opaque and visible!
                val maxVal = kotlin.math.max(red, kotlin.math.max(green, blue))
                val alpha = if (maxVal == 0) 0 else (maxVal * 1.6f).toInt().coerceIn(60, 255)
                
                pixelBuffer[pixelIndex++] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        bitmap.setPixels(pixelBuffer, 0, simNX, 0, 0, simNX, simNY)
    }
}
