package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState as SessionPlaybackState
import android.media.MediaMetadata
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PlaybackState
import com.example.data.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileInputStream
import java.util.Collections

class PlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {

    private val binder = LocalBinder()

    // State Flow for Compose UI
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    // Media players
    private var primaryPlayer: MediaPlayer? = null
    private var secondaryPlayer: MediaPlayer? = null
    
    // Playback playlist queue
    private val playlistQueue = mutableListOf<Song>()
    private val shuffledQueue = mutableListOf<Song>()
    private var currentQueueIndex = -1

    // Setting options
    private var isShuffle = false
    private var isRepeat = false
    private var isCrossfadeEnabled = true
    private var crossfadeSeconds = 3
    
    // Background Customization Options
    private var bgBlurRadius = 10f
    private var bgAlpha = 0.24f
    private var bgScale = 2.4f
    private var bgWaveOpacity = 0.85f
    private var bgContentScale = "Crop"

    // Crossfade helper state
    private var isCrossfading = false
    private var crossfadeJob: Job? = null

    // System integrations
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSession? = null
    private var currentAlbumArtBitmap: android.graphics.Bitmap? = null

    // Coroutines Scope for updates
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    
    // Audio Visualizer
    private var visualizer: android.media.audiofx.Visualizer? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        setupMediaSession()
        setupNotificationChannel()
        
        // Start updating progress loop
        startProgressUpdater()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayback()
            ACTION_SKIP_NEXT -> skipToNext()
            ACTION_SKIP_PREV -> skipToPrevious()
        }
        return START_STICKY
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // --- Audio Player Initialization ---

    private fun setupVisualizer(audioSessionId: Int) {
        try {
            visualizer?.release()
            visualizer = android.media.audiofx.Visualizer(audioSessionId).apply {
                captureSize = 128
                setDataCaptureListener(object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        v: android.media.audiofx.Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform != null && _playbackState.value.isPlaying) {
                            var sum = 0f
                            val ints = IntArray(waveform.size)
                            for (i in waveform.indices) {
                                val amp = waveform[i].toInt() - 128
                                ints[i] = amp
                                sum += kotlin.math.abs(amp)
                            }
                            // Calculate average energy normalized to 0..1 roughly
                            val energy = (sum / waveform.size) / 128f
                            _playbackState.update { it.copy(audioEnergy = energy * 2f, audioWaveform = ints) }
                        }
                    }

                    override fun onFftDataCapture(
                        v: android.media.audiofx.Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {}
                }, android.media.audiofx.Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getOrCreatePrimaryPlayer(): MediaPlayer {
        val player = primaryPlayer ?: MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnCompletionListener {
                if (!isCrossfading) {
                    onSongCompleted()
                }
            }
        }
        if (primaryPlayer == null) {
            setupVisualizer(player.audioSessionId)
        }
        primaryPlayer = player
        return player
    }

    private fun getOrCreateSecondaryPlayer(): MediaPlayer {
        val player = secondaryPlayer ?: MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
        }
        secondaryPlayer = player
        return player
    }

    // --- Media Session & Lock Screen Core ---

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "AcousticMediaSession").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { play() }
                override fun onPause() { pause() }
                override fun onSkipToNext() { skipToNext() }
                override fun onSkipToPrevious() { skipToPrevious() }
                override fun onSeekTo(pos: Long) { seekTo(pos) }
            })
            isActive = true
        }
    }

    private fun updateMediaSessionState() {
        val state = _playbackState.value
        val playState = if (state.isPlaying) SessionPlaybackState.STATE_PLAYING else SessionPlaybackState.STATE_PAUSED
        
        val actions = SessionPlaybackState.ACTION_PLAY or
                SessionPlaybackState.ACTION_PAUSE or
                SessionPlaybackState.ACTION_SKIP_TO_NEXT or
                SessionPlaybackState.ACTION_SKIP_TO_PREVIOUS or
                SessionPlaybackState.ACTION_SEEK_TO

        val stateBuilder = SessionPlaybackState.Builder()
            .setActions(actions)
            .setState(playState, state.progress, 1.0f, SystemClock.elapsedRealtime())
        
        mediaSession?.setPlaybackState(stateBuilder.build())

        state.currentSong?.let { song ->
            val metadataBuilder = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, song.duration)
            
            currentAlbumArtBitmap?.let { bmp ->
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bmp)
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, bmp)
            }
            mediaSession?.setMetadata(metadataBuilder.build())
        }
    }

    // --- Foreground Notification Manager ---

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows dynamic playback controls for Acoustic Player background playing"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val song = _playbackState.value.currentSong ?: return
        val isPlaying = _playbackState.value.isPlaying

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = PendingIntent.getService(this, 1, Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }, PendingIntent.FLAG_IMMUTABLE)
        val prevIntent = PendingIntent.getService(this, 2, Intent(this, PlaybackService::class.java).apply { action = ACTION_SKIP_PREV }, PendingIntent.FLAG_IMMUTABLE)
        val nextIntent = PendingIntent.getService(this, 3, Intent(this, PlaybackService::class.java).apply { action = ACTION_SKIP_NEXT }, PendingIntent.FLAG_IMMUTABLE)

        val mediaStyle = android.app.Notification.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }

        val notification = notificationBuilder
            .setStyle(mediaStyle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setLargeIcon(currentAlbumArtBitmap)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setContentIntent(pendingIntent)
            .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(
                android.app.Notification.Action.Builder(
                    android.R.drawable.ic_media_previous, "Previous", prevIntent
                ).build()
            )
            .addAction(
                android.app.Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Play/Pause", playPauseIntent
                ).build()
            )
            .addAction(
                android.app.Notification.Action.Builder(
                    android.R.drawable.ic_media_next, "Next", nextIntent
                ).build()
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            return audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                primaryPlayer?.setVolume(0.2f, 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                primaryPlayer?.setVolume(1.0f, 1.0f)
                if (!_playbackState.value.isPlaying) {
                    play()
                }
            }
        }
    }

    // --- Playback State & Queue Modifiers ---

    fun playNext(song: Song) {
        if (playlistQueue.isEmpty()) {
            setQueue(listOf(song), 0)
            return
        }

        playlistQueue.remove(song)
        val activeQueue = getActiveQueue()
        val currentSongObj = activeQueue.getOrNull(currentQueueIndex)
        
        val playlistInsertIndex = if (currentSongObj != null) {
            val idx = playlistQueue.indexOf(currentSongObj)
            if (idx != -1) idx + 1 else playlistQueue.size
        } else {
            playlistQueue.size
        }.coerceIn(0, playlistQueue.size)
        playlistQueue.add(playlistInsertIndex, song)

        shuffledQueue.remove(song)
        val shuffledInsertIndex = if (isShuffle && currentQueueIndex >= 0) {
            currentQueueIndex + 1
        } else {
            playlistInsertIndex
        }.coerceIn(0, shuffledQueue.size)
        shuffledQueue.add(shuffledInsertIndex, song)

        val updatedQueue = getActiveQueue()
        if (currentSongObj != null) {
            currentQueueIndex = updatedQueue.indexOf(currentSongObj).coerceIn(-1, updatedQueue.size - 1)
        } else {
            currentQueueIndex = 0
        }

        updateState()
    }

    fun setQueue(songs: List<Song>, startIndex: Int) {
        playlistQueue.clear()
        playlistQueue.addAll(songs)
        
        applyShuffleQueue()

        val activeQueue = if (isShuffle) shuffledQueue else playlistQueue
        val selectedSong = songs.getOrNull(startIndex)
        
        currentQueueIndex = if (selectedSong != null) activeQueue.indexOf(selectedSong) else -1
        
        if (selectedSong != null) {
            loadAndPlaySong(selectedSong)
        } else {
            stopPlayback()
        }
        
        updateState()
    }

    private fun applyShuffleQueue() {
        shuffledQueue.clear()
        shuffledQueue.addAll(playlistQueue)
        if (isShuffle) {
            shuffledQueue.shuffle()
        }
    }

    private fun loadAndPlaySong(song: Song) {
        // Stop secondary/crossfade if running
        crossfadeJob?.cancel()
        isCrossfading = false
        secondaryPlayer?.reset()

        val player = getOrCreatePrimaryPlayer()
        try {
            player.reset()
            setPlayerDataSource(player, song)
            player.prepare()
            
            val oldBitmap = currentAlbumArtBitmap
            currentAlbumArtBitmap = null
            oldBitmap?.recycle()

            serviceScope.launch(Dispatchers.IO) {
                val bitmap = loadAlbumArtBitmap(song)
                withContext(Dispatchers.Main) {
                    if (_playbackState.value.currentSong?.id == song.id) {
                        currentAlbumArtBitmap = bitmap
                        updateMediaSessionState()
                        updateNotification()
                    } else {
                        bitmap?.recycle()
                    }
                }
            }

            if (requestAudioFocus()) {
                player.setVolume(1.0f, 1.0f)
                player.start()
                
                _playbackState.update {
                    it.copy(
                        currentSong = song,
                        isPlaying = true,
                        progress = 0L,
                        duration = player.duration.toLong(),
                        queueIndex = getActiveQueue().indexOf(song),
                        queueSize = getActiveQueue().size,
                        currentQueue = getActiveQueue()
                    )
                }
                
                updateMediaSessionState()
                updateNotification()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If failed, skip to next
            skipToNext()
        }
    }

    private fun setPlayerDataSource(player: MediaPlayer, song: Song) {
        if (song.path.startsWith("content://")) {
            player.setDataSource(this, Uri.parse(song.path))
        } else {
            val file = File(song.path)
            if (file.exists()) {
                val fis = FileInputStream(file)
                player.setDataSource(fis.fd)
                fis.close()
            } else {
                player.setDataSource(song.path)
            }
        }
    }

    fun play() {
        if (playlistQueue.isEmpty()) return
        
        val player = getOrCreatePrimaryPlayer()
        if (!player.isPlaying && requestAudioFocus()) {
            player.start()
            _playbackState.update { it.copy(isPlaying = true) }
            updateMediaSessionState()
            updateNotification()
        }
    }

    fun pause() {
        primaryPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playbackState.update { state -> state.copy(isPlaying = false) }
                updateMediaSessionState()
                updateNotification()
            }
        }
    }

    fun togglePlayback() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        primaryPlayer?.seekTo(positionMs.toInt())
        _playbackState.update { it.copy(progress = positionMs) }
        updateMediaSessionState()
    }

    fun skipToNext() {
        val queue = getActiveQueue()
        if (queue.isEmpty()) return

        var nextIndex = currentQueueIndex + 1
        if (nextIndex >= queue.size) {
            nextIndex = if (isRepeat) 0 else -1
        }

        if (nextIndex != -1) {
            currentQueueIndex = nextIndex
            loadAndPlaySong(queue[nextIndex])
        } else {
            stopPlayback()
        }
    }

    fun skipToPrevious() {
        val currentProgress = primaryPlayer?.currentPosition ?: 0
        if (currentProgress > 4000) {
            // Restart current song
            seekTo(0)
            return
        }

        val queue = getActiveQueue()
        if (queue.isEmpty()) return

        var prevIndex = currentQueueIndex - 1
        if (prevIndex < 0) {
            prevIndex = if (isRepeat) queue.size - 1 else 0
        }

        currentQueueIndex = prevIndex
        loadAndPlaySong(queue[prevIndex])
    }

    private fun getActiveQueue(): List<Song> = if (isShuffle) shuffledQueue else playlistQueue

    private fun stopPlayback() {
        primaryPlayer?.stop()
        _playbackState.update {
            it.copy(
                currentSong = null,
                isPlaying = false,
                progress = 0L,
                duration = 0L,
                queueIndex = -1
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // --- Configuration State Triggers ---

    fun toggleShuffle() {
        isShuffle = !isShuffle
        applyShuffleQueue()
        
        // Re-align currentSong index in shuffled list
        val activeSong = _playbackState.value.currentSong
        if (activeSong != null) {
            currentQueueIndex = getActiveQueue().indexOf(activeSong)
        }
        updateState()
    }

    fun shuffleAndPlay(songs: List<Song>) {
        isShuffle = true
        playlistQueue.clear()
        playlistQueue.addAll(songs)
        applyShuffleQueue()
        
        currentQueueIndex = if (shuffledQueue.isNotEmpty()) 0 else -1
        val selectedSong = shuffledQueue.firstOrNull()
        if (selectedSong != null) {
            loadAndPlaySong(selectedSong)
        } else {
            stopPlayback()
        }
        updateState()
    }

    fun toggleRepeat() {
        isRepeat = !isRepeat
        updateState()
    }

    fun setCrossfadeParams(enabled: Boolean, seconds: Int) {
        isCrossfadeEnabled = enabled
        crossfadeSeconds = seconds
        updateState()
    }

    fun setBackgroundParams(blur: Float, alpha: Float, scale: Float, waveOpacity: Float) {
        bgBlurRadius = blur
        bgAlpha = alpha
        bgScale = scale
        bgWaveOpacity = waveOpacity
        updateState()
    }

    fun setBackgroundContentScale(scaleType: String) {
        bgContentScale = scaleType
        updateState()
    }

    private fun updateState() {
        _playbackState.update {
            it.copy(
                isShuffle = isShuffle,
                isRepeat = isRepeat,
                crossfadeEnabled = isCrossfadeEnabled,
                crossfadeSeconds = crossfadeSeconds,
                queueSize = getActiveQueue().size,
                queueIndex = currentQueueIndex,
                currentQueue = getActiveQueue(),
                bgBlurRadius = bgBlurRadius,
                bgAlpha = bgAlpha,
                bgScale = bgScale,
                bgWaveOpacity = bgWaveOpacity,
                bgContentScale = bgContentScale
            )
        }
    }

    // --- Real-time Progress Monitoring & Seamless Crossfade Logic ---

    private fun startProgressUpdater() {
        progressJob = serviceScope.launch {
            var lastMediaSessionUpdate = System.currentTimeMillis()
            while (isActive) {
                delay(30)
                primaryPlayer?.let { player ->
                    if (player.isPlaying && !isCrossfading) {
                        val currentPos = player.currentPosition.toLong()
                        val totDuration = player.duration.toLong()
                        
                        _playbackState.update { it.copy(progress = currentPos, duration = totDuration) }
                        
                        val now = System.currentTimeMillis()
                        if (now - lastMediaSessionUpdate > 1000) {
                            updateMediaSessionState()
                            lastMediaSessionUpdate = now
                        }

                        // Check for crossfade trigger
                        val queue = getActiveQueue()
                        val nextIndex = currentQueueIndex + 1
                        
                        if (isCrossfadeEnabled && 
                            totDuration > 0 && 
                            nextIndex < queue.size && 
                            currentPos >= totDuration - (crossfadeSeconds * 1000)
                        ) {
                            triggerCrossfade(queue[nextIndex])
                        }
                    }
                }
            }
        }
    }

    private fun triggerCrossfade(nextSong: Song) {
        isCrossfading = true
        crossfadeJob = serviceScope.launch {
            val secPlayer = getOrCreateSecondaryPlayer()
            try {
                secPlayer.reset()
                setPlayerDataSource(secPlayer, nextSong)
                secPlayer.prepare()
                
                secPlayer.setVolume(0.0f, 0.0f)
                secPlayer.start()

                val steps = 20
                val fadeInterval = (crossfadeSeconds * 1000) / steps
                
                for (i in 1..steps) {
                    val progressRatio = i.toFloat() / steps
                    
                    // Fade primary down, secondary up
                    primaryPlayer?.setVolume(1.0f - progressRatio, 1.0f - progressRatio)
                    secPlayer.setVolume(progressRatio, progressRatio)

                    // Track progress in state flow
                    _playbackState.update {
                        it.copy(
                            progress = (primaryPlayer?.currentPosition ?: 0).toLong()
                        )
                    }
                    
                    delay(fadeInterval.toLong())
                }

                // Finalize swap
                primaryPlayer?.stop()
                primaryPlayer?.reset()

                val temp = primaryPlayer
                primaryPlayer = secPlayer
                secondaryPlayer = temp

                // Set primary to standard completion listener
                primaryPlayer?.setOnCompletionListener {
                    if (!isCrossfading) {
                        onSongCompleted()
                    }
                }

                currentQueueIndex += 1
                isCrossfading = false

                _playbackState.update {
                    it.copy(
                        currentSong = nextSong,
                        progress = 0L,
                        duration = primaryPlayer?.duration?.toLong() ?: 0L,
                        queueIndex = currentQueueIndex,
                        currentQueue = getActiveQueue()
                    )
                }

                updateMediaSessionState()
                updateNotification()

            } catch (e: Exception) {
                e.printStackTrace()
                // Safeguard: if crossfade fails, switch role immediately and complete
                isCrossfading = false
                skipToNext()
            }
        }
    }

    private fun onSongCompleted() {
        if (isRepeat && getActiveQueue().size == 1) {
            // Repeat single song
            seekTo(0)
            play()
        } else {
            skipToNext()
        }
    }

    private fun loadAlbumArtBitmap(song: Song): android.graphics.Bitmap? {
        if (song.albumArtPath != null) {
            try {
                val file = File(song.albumArtPath)
                if (file.exists()) {
                    return android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (song.path.startsWith("content://")) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(this, Uri.parse(song.path))
                val artBytes = retriever.embeddedPicture
                retriever.release()
                if (artBytes != null) {
                    return android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(song.path)
                val artBytes = retriever.embeddedPicture
                retriever.release()
                if (artBytes != null) {
                    return android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
        crossfadeJob?.cancel()
        serviceScope.cancel()

        currentAlbumArtBitmap?.recycle()
        currentAlbumArtBitmap = null

        visualizer?.release()
        visualizer = null

        primaryPlayer?.release()
        secondaryPlayer?.release()
        
        mediaSession?.release()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    companion object {
        const val CHANNEL_ID = "acoustic_playback_channel"
        const val NOTIFICATION_ID = 20054
        
        const val ACTION_PLAY_PAUSE = "com.example.action.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.example.action.SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.example.action.SKIP_PREV"
    }
}
