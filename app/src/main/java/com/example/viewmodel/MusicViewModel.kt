package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MusicDatabase
import com.example.data.MusicRepository
import com.example.data.PlaybackState
import com.example.data.Playlist
import com.example.data.Song
import com.example.service.PlaybackService
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

enum class SongSortOrder {
    DATE_ADDED_DESC,
    DATE_ADDED_ASC,
    TITLE_ASC,
    ARTIST_ASC
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MusicRepository
    
    // Playback state routed from the Active Bound Service
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentTrackColor = MutableStateFlow<Color?>(null)
    val currentTrackColor: StateFlow<Color?> = _currentTrackColor.asStateFlow()

    private val _currentTrackColors = MutableStateFlow<List<Color>>(listOf(Color(0xFFD0BCFF), Color(0xFFEADDFF), Color(0xFF381E72)))
    val currentTrackColors: StateFlow<List<Color>> = _currentTrackColors.asStateFlow()

    enum class ThemeColorMode {
        ART_VIBRANT,       // Dominant color in art
        ART_COMPLEMENTARY, // Complementary color in art (inverse/hue rotated) - colored theme NOT in art!
        SONG_AURA,          // Pure deterministic song-specific color based on title & artist
        EXACT_ART_COLORS   // Exact artwork multiple colors (theme & component gradients)
    }

    private val _themeColorMode = MutableStateFlow(ThemeColorMode.EXACT_ART_COLORS)
    val themeColorMode: StateFlow<ThemeColorMode> = _themeColorMode.asStateFlow()

    private val _artworkColorGrid = MutableStateFlow<IntArray?>(null)
    val artworkColorGrid: StateFlow<IntArray?> = _artworkColorGrid.asStateFlow()

    private val _fluidBgEnabled = MutableStateFlow(true)
    val fluidBgEnabled: StateFlow<Boolean> = _fluidBgEnabled.asStateFlow()

    private val _introAnimationEnabled = MutableStateFlow(false)
    val introAnimationEnabled: StateFlow<Boolean> = _introAnimationEnabled.asStateFlow()

    private val _waveSpeed = MutableStateFlow(1.0f)
    val waveSpeed: StateFlow<Float> = _waveSpeed.asStateFlow()

    private val _waveRoughness = MutableStateFlow(1.0f)
    val waveRoughness: StateFlow<Float> = _waveRoughness.asStateFlow()

    private val _waveArtworkInfluence = MutableStateFlow(0.7f)
    val waveArtworkInfluence: StateFlow<Float> = _waveArtworkInfluence.asStateFlow()

    private val _waveColorStyle = MutableStateFlow("Dynamic Track")
    val waveColorStyle: StateFlow<String> = _waveColorStyle.asStateFlow()

    fun setThemeColorMode(mode: ThemeColorMode) {
        _themeColorMode.value = mode
        playbackState.value.currentSong?.let {
            updateCurrentSongColor(it)
        }
    }

    fun setFluidBgEnabled(enabled: Boolean) {
        _fluidBgEnabled.value = enabled
    }

    fun setIntroAnimationEnabled(enabled: Boolean) {
        _introAnimationEnabled.value = enabled
    }

    fun setWaveSpeed(speed: Float) {
        _waveSpeed.value = speed
    }

    fun setWaveRoughness(roughness: Float) {
        _waveRoughness.value = roughness
    }

    fun setWaveArtworkInfluence(influence: Float) {
        _waveArtworkInfluence.value = influence
    }

    fun setWaveColorStyle(style: String) {
        _waveColorStyle.value = style
    }

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _sortOrder = MutableStateFlow(SongSortOrder.DATE_ADDED_DESC)
    val sortOrder: StateFlow<SongSortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: SongSortOrder) {
        _sortOrder.value = order
    }

    private var playbackService: PlaybackService? = null

    // Flows for database variables
    val allSongs: StateFlow<List<Song>>
    val favoriteSongs: StateFlow<List<Song>>
    val allPlaylists: StateFlow<List<Playlist>>

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    val playlistSongs: StateFlow<List<Song>> = _selectedPlaylistId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getSongsForPlaylist(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlaybackService.LocalBinder
            playbackService = binder.getService()
            _serviceConnected.value = true

            // Observe playback state changes from the service
            viewModelScope.launch {
                var lastSongId: Long? = null
                binder.getService().playbackState.collect { state ->
                    _playbackState.value = state
                    val currentSongId = state.currentSong?.id
                    if (currentSongId != lastSongId) {
                        lastSongId = currentSongId
                        updateCurrentSongColor(state.currentSong)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            _serviceConnected.value = false
        }
    }

    init {
        val musicDao = MusicDatabase.getDatabase(application).musicDao()
        repository = MusicRepository(musicDao)

        allSongs = combine(repository.allSongs, _sortOrder) { songs, order ->
            when (order) {
                SongSortOrder.DATE_ADDED_DESC -> songs.sortedByDescending { it.dateAdded }
                SongSortOrder.DATE_ADDED_ASC -> songs.sortedBy { it.dateAdded }
                SongSortOrder.TITLE_ASC -> songs.sortedBy { it.title.lowercase(java.util.Locale.getDefault()) }
                SongSortOrder.ARTIST_ASC -> songs.sortedBy { it.artist.lowercase(java.util.Locale.getDefault()) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        favoriteSongs = repository.favoriteSongs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allPlaylists = repository.allPlaylists.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // --- Service Lifecycle Links ---

    fun bindPlaybackService(context: Context) {
        try {
            val appContext = context.applicationContext
            val intent = Intent(appContext, PlaybackService::class.java)
            appContext.startService(intent) // keeps service sticky
            appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unbindPlaybackService(context: Context) {
        if (_serviceConnected.value) {
            try {
                val appContext = context.applicationContext
                appContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _serviceConnected.value = false
            playbackService = null
        }
    }

    // --- Media Controls Mapping ---

    fun playNext(song: Song) {
        playbackService?.playNext(song)
    }

    fun playSong(song: Song, list: List<Song>) {
        playbackService?.setQueue(list, list.indexOf(song))
    }

    fun shuffleAndPlay(list: List<Song>) {
        playbackService?.shuffleAndPlay(list)
    }

    fun togglePlayback() {
        playbackService?.togglePlayback()
    }

    fun skipNext() {
        playbackService?.skipToNext()
    }

    fun skipPrev() {
        playbackService?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        playbackService?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        playbackService?.toggleShuffle()
    }

    fun toggleRepeat() {
        playbackService?.toggleRepeat()
    }

    fun setCrossfadeParams(enabled: Boolean, seconds: Int) {
        playbackService?.setCrossfadeParams(enabled, seconds)
    }

    fun setBackgroundParams(blur: Float, alpha: Float, scale: Float, waveOpacity: Float) {
        playbackService?.setBackgroundParams(blur, alpha, scale, waveOpacity)
    }

    fun setBackgroundContentScale(scaleType: String) {
        playbackService?.setBackgroundContentScale(scaleType)
    }

    // --- Playlist & Favorites Core Logic ---

    fun selectPlaylist(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlist = Playlist(name = name)
            repository.insertPlaylist(playlist)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePlaylistById(playlistId)
            if (_selectedPlaylistId.value == playlistId) {
                _selectedPlaylistId.value = null
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = song.copy(isFavorite = !song.isFavorite)
            repository.updateSong(updated)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete actual sandbox file if copied internal file
            if (!song.path.startsWith("content://")) {
                val file = File(song.path)
                if (file.exists()) {
                    file.delete()
                }
            }
            repository.deleteSongById(song.id)
        }
    }

    // --- Media Store Library Scan ---

    fun scanDeviceMedia(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = context.contentResolver
            val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.DATA,
                android.provider.MediaStore.Audio.Media.ALBUM_ID
            )
            // Retrieve actual music files only, filtered for typical formats
            val selection = "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0"

            try {
                val cursor = resolver.query(uri, projection, selection, null, null)
                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                    val titleCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                    val artistCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                    val durCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                    val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                    val albumIdCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ALBUM_ID)

                    while (c.moveToNext()) {
                        val mediaId = c.getLong(idCol)
                        val title = c.getString(titleCol) ?: "Unknown Track"
                        val artist = c.getString(artistCol) ?: "Unknown Artist"
                        val duration = c.getLong(durCol)
                        val dataPath = c.getString(dataCol)
                        val albumId = if (albumIdCol != -1) c.getLong(albumIdCol) else -1L

                        val contentUri = android.content.ContentUris.withAppendedId(
                            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            mediaId
                        )

                        val albumArtUri = if (albumId != -1L) {
                            "content://media/external/audio/albumart/$albumId"
                        } else {
                            null
                        }

                        // Avoid inserting tracks already indexed on path
                        val song = Song(
                            title = title,
                            artist = artist,
                            duration = duration,
                            path = contentUri.toString(),
                            uriString = contentUri.toString(),
                            albumArtPath = albumArtUri
                        )
                        repository.insertSong(song)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- File Storage Import Copy Controller ---

    fun importSongs(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (uri in uris) {
                try {
                    val resolver = context.contentResolver
                    var title = "Unknown Track"
                    var artist = "Unknown Artist"
                    var duration = 0L

                    var lastMod = System.currentTimeMillis()

                    // Access Openable metrics using dynamic columns auto-discovery (null projection)
                    try {
                        resolver.query(uri, null, null, null, null)?.use { c ->
                            if (c.moveToFirst()) {
                                val nameCol = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                val titleCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE)
                                val artistCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST)
                                val durCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DURATION)
                                val dateModCol = c.getColumnIndex("date_modified")
                                val lastModCol = c.getColumnIndex("last_modified")

                                if (nameCol != -1) {
                                    var displayName = c.getString(nameCol) ?: "imported_track"
                                    if (displayName.contains(".")) {
                                        displayName = displayName.substringBeforeLast(".")
                                    }
                                    title = displayName
                                }
                                if (titleCol != -1) {
                                    val t = c.getString(titleCol)
                                    if (!t.isNullOrEmpty()) title = t
                                }
                                if (artistCol != -1) {
                                    val a = c.getString(artistCol)
                                    if (!a.isNullOrEmpty()) artist = a
                                }
                                if (durCol != -1) {
                                    duration = c.getLong(durCol)
                                }
                                if (dateModCol != -1) {
                                    val dm = c.getLong(dateModCol)
                                    if (dm > 0L) {
                                        lastMod = if (dm < 100000000000L) dm * 1000L else dm
                                    }
                                } else if (lastModCol != -1) {
                                    val lm = c.getLong(lastModCol)
                                    if (lm > 0L) {
                                        lastMod = if (lm < 100000000000L) lm * 1000L else lm
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        try {
                            val projectionSimple = arrayOf(
                                android.provider.OpenableColumns.DISPLAY_NAME,
                                android.provider.MediaStore.Audio.Media.TITLE,
                                android.provider.MediaStore.Audio.Media.ARTIST,
                                android.provider.MediaStore.Audio.Media.DURATION
                            )
                            resolver.query(uri, projectionSimple, null, null, null)?.use { c ->
                                if (c.moveToFirst()) {
                                    val nameCol = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    val titleCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE)
                                    val artistCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.ARTIST)
                                    val durCol = c.getColumnIndex(android.provider.MediaStore.Audio.Media.DURATION)

                                    if (nameCol != -1) {
                                        var displayName = c.getString(nameCol) ?: "imported_track"
                                        if (displayName.contains(".")) {
                                            displayName = displayName.substringBeforeLast(".")
                                        }
                                        title = displayName
                                    }
                                    if (titleCol != -1) {
                                        val t = c.getString(titleCol)
                                        if (!t.isNullOrEmpty()) title = t
                                    }
                                    if (artistCol != -1) {
                                        val a = c.getString(artistCol)
                                        if (!a.isNullOrEmpty()) artist = a
                                    }
                                    if (durCol != -1) {
                                        duration = c.getLong(durCol)
                                    }
                                }
                            }
                        } catch (e2: Exception) {
                            try {
                                resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                                    if (c.moveToFirst()) {
                                        val nameCol = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                        if (nameCol != -1) {
                                            var displayName = c.getString(nameCol) ?: "imported_track"
                                            if (displayName.contains(".")) {
                                                displayName = displayName.substringBeforeLast(".")
                                            }
                                            title = displayName
                                        }
                                    }
                                }
                            } catch (e3: Exception) {
                                e3.printStackTrace()
                            }
                        }
                    }

                    // Metadata fallback extraction
                    if (duration <= 0) {
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(context, uri)
                            val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            duration = durStr?.toLongOrNull() ?: 180000L
                            
                            val tMeta = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                            val aMeta = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            if (!tMeta.isNullOrEmpty()) title = tMeta
                            if (!aMeta.isNullOrEmpty()) artist = aMeta
                            
                            retriever.release()
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            duration = 180000L // 3 minutes standard fallback
                        }
                    }

                    // Extract embedded art if available
                    var extractedArtPath: String? = null
                    var extractedVibrantColor: Int? = null
                    try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(context, uri)
                        val artBytes = retriever.embeddedPicture
                        if (artBytes != null) {
                            val artFileName = "acoustic_art_${System.currentTimeMillis()}_${(0..999).random()}.png"
                            val artFile = File(context.filesDir, artFileName)
                            artFile.writeBytes(artBytes)
                            extractedArtPath = artFile.absolutePath
                            
                            // Extract vibrant color from newly imported artwork bitmap
                            val options = android.graphics.BitmapFactory.Options().apply {
                                inSampleSize = 8
                            }
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                            if (bitmap != null) {
                                var maxSat = -1f
                                var bestColor = 0
                                val hsv = FloatArray(3)
                                for (x in 0 until bitmap.width step 4) {
                                    for (y in 0 until bitmap.height step 4) {
                                        if (x < bitmap.width && y < bitmap.height) {
                                            val pixel = bitmap.getPixel(x, y)
                                            android.graphics.Color.colorToHSV(pixel, hsv)
                                            val saturation = hsv[1]
                                            val value = hsv[2]
                                            if (saturation > maxSat && value > 0.2f && value < 0.9f) {
                                                maxSat = saturation
                                                bestColor = pixel
                                            }
                                        }
                                    }
                                }
                                bitmap.recycle()
                                if (bestColor != 0) {
                                    extractedVibrantColor = bestColor
                                }
                            }
                        }
                        retriever.release()
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }

                    val song = Song(
                        title = title,
                        artist = artist,
                        duration = duration,
                        path = uri.toString(),
                        uriString = uri.toString(),
                        albumArtPath = extractedArtPath,
                        albumArtColor = extractedVibrantColor,
                        dateAdded = lastMod
                    )
                    repository.insertSong(song)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // --- Directory Scanner Logic ---

    fun importFolder(context: Context, treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            try {
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val documentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            
            val projection = arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            
            val foundUris = mutableListOf<Uri>()
            val queue = java.util.ArrayDeque<Uri>()
            queue.add(childrenUri)
            
            while (queue.isNotEmpty()) {
                val currentUri = queue.poll() ?: continue
                try {
                    contentResolver.query(currentUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val mimeCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val nameCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        
                        while (cursor.moveToNext()) {
                            val docId = cursor.getString(idCol)
                            val mime = cursor.getString(mimeCol)
                            val name = cursor.getString(nameCol) ?: "unknown"
                            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            
                            if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                                val subChildrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                                queue.add(subChildrenUri)
                            } else if (mime != null && (mime.startsWith("audio/") || name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".ogg") || name.endsWith(".flac"))) {
                                foundUris.add(docUri)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (foundUris.isNotEmpty()) {
                importSongs(context, foundUris)
            }
        }
    }

    // --- Dynamic Accent Extraction Engine ---

    fun extractMultipleColorsFromArt(song: Song): List<Color> {
        val colors = mutableListOf<Color>()
        try {
            var artInputStream: java.io.InputStream? = null
            val context = getApplication<Application>()
            
            if (song.albumArtPath != null) {
                if (song.albumArtPath.startsWith("content://")) {
                    try {
                        artInputStream = context.contentResolver.openInputStream(Uri.parse(song.albumArtPath))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    val file = File(song.albumArtPath)
                    if (file.exists()) {
                        artInputStream = file.inputStream()
                    }
                }
            }

            if (artInputStream == null && song.path.startsWith("content://")) {
                try {
                    artInputStream = context.contentResolver.openInputStream(Uri.parse(song.path))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (artInputStream == null) {
                val file = File(song.path)
                if (file.exists()) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(song.path)
                        val bytes = retriever.embeddedPicture
                        if (bytes != null) {
                            artInputStream = java.io.ByteArrayInputStream(bytes)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        retriever.release()
                    }
                }
            }

            artInputStream?.use { stream ->
                val options = android.graphics.BitmapFactory.Options().apply {
                     inSampleSize = 8
                }
                val bitmap = android.graphics.BitmapFactory.decodeStream(stream, null, options)
                if (bitmap != null) {
                    val candidateColors = mutableListOf<Int>()
                    val hsv = FloatArray(3)
                    for (x in 0 until bitmap.width step 4) {
                        for (y in 0 until bitmap.height step 4) {
                            if (x < bitmap.width && y < bitmap.height) {
                                val pixel = bitmap.getPixel(x, y)
                                android.graphics.Color.colorToHSV(pixel, hsv)
                                val saturation = hsv[1]
                                val value = hsv[2]
                                if (saturation > 0.15f && value > 0.15f && value < 0.95f) {
                                    candidateColors.add(pixel)
                                }
                            }
                        }
                    }
                    bitmap.recycle()

                    val sortedCandidates = candidateColors.distinct().sortedWith(Comparator { c1, c2 ->
                        val hsv1 = FloatArray(3)
                        val hsv2 = FloatArray(3)
                        android.graphics.Color.colorToHSV(c1, hsv1)
                        android.graphics.Color.colorToHSV(c2, hsv2)
                        hsv2[1].compareTo(hsv1[1])
                    })

                    val distinctPicked = mutableListOf<Int>()
                    for (c in sortedCandidates) {
                        if (distinctPicked.size >= 3) break
                        val hsvC = FloatArray(3)
                        android.graphics.Color.colorToHSV(c, hsvC)
                        
                        var isDistinct = true
                        for (p in distinctPicked) {
                            val hsvP = FloatArray(3)
                            android.graphics.Color.colorToHSV(p, hsvP)
                            val hueDiff = Math.abs(hsvC[0] - hsvP[0])
                            val shortestHueDiff = if (hueDiff > 180f) 360f - hueDiff else hueDiff
                            if (shortestHueDiff < 30f) {
                                isDistinct = false
                                break
                            }
                        }
                        if (isDistinct) {
                            distinctPicked.add(c)
                        }
                    }

                    if (distinctPicked.size < 3) {
                        for (c in sortedCandidates) {
                            if (distinctPicked.size >= 3) break
                            if (!distinctPicked.contains(c)) {
                                distinctPicked.add(c)
                            }
                        }
                    }

                    colors.addAll(distinctPicked.map { Color(it) })
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return colors
    }

    fun getTrackColors(song: Song?): List<Color> {
         val baseColors = runCatching {
             if (song != null) extractMultipleColorsFromArt(song) else emptyList()
         }.getOrDefault(emptyList())

         val primary = baseColors.firstOrNull() ?: song?.albumArtColor?.let { Color(it) } ?: Color(0xFFD0BCFF)
         val list = baseColors.toMutableList()
         if (list.isEmpty()) {
             list.add(primary)
         }
         if (list.size < 2) {
             val hsv = FloatArray(3)
             android.graphics.Color.colorToHSV(primary.toArgb(), hsv)
             hsv[0] = (hsv[0] + 180f) % 360f
             hsv[1] = hsv[1].coerceAtLeast(0.6f)
             hsv[2] = hsv[2].coerceIn(0.6f, 0.9f)
             list.add(Color(android.graphics.Color.HSVToColor(hsv)))
         }
         if (list.size < 3) {
             val hsv = FloatArray(3)
             android.graphics.Color.colorToHSV(primary.toArgb(), hsv)
             hsv[0] = (hsv[0] + 120f) % 360f
             hsv[1] = hsv[1].coerceAtLeast(0.5f)
             hsv[2] = hsv[2].coerceIn(0.5f, 0.9f)
             list.add(Color(android.graphics.Color.HSVToColor(hsv)))
         }
         return list.take(3)
    }

    private fun updateCurrentSongColor(song: Song?) {
        if (song == null) {
            _currentTrackColor.value = null
            _currentTrackColors.value = listOf(Color(0xFFD0BCFF), Color(0xFFEADDFF), Color(0xFF381E72))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val currentMode = _themeColorMode.value
            var resolvedColor: Color? = null
            var resolvedColors: List<Color> = listOf(Color(0xFFD0BCFF), Color(0xFFEADDFF), Color(0xFF381E72))

            when (currentMode) {
                ThemeColorMode.SONG_AURA -> {
                    // Detached custom aura color derived entirely from hashing the song title/artist
                    val songHash = (song.title + song.artist).hashCode()
                    val hue1 = (Math.abs(songHash) % 360).toFloat()
                    val hue2 = (hue1 + 120f) % 360f
                    val hue3 = (hue1 + 240f) % 360f
                    
                    val color1 = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue1, 0.70f, 0.85f)))
                    val color2 = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue2, 0.65f, 0.90f)))
                    val color3 = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue3, 0.75f, 0.80f)))
                    
                    resolvedColor = color1
                    resolvedColors = listOf(color1, color2, color3)
                }
                ThemeColorMode.ART_COMPLEMENTARY -> {
                    // Rotate the color wheel by 180° so the color is NOT present in the artwork, yet complements it nicely
                    val baseColor = song.albumArtColor?.let { Color(it) } ?: extractDominantFromArt(song)
                    val mainColor = baseColor?.let { color ->
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                        hsv[0] = (hsv[0] + 180f) % 360f  // Force full diametric contrast on hue
                        hsv[1] = hsv[1].coerceAtLeast(0.65f) // Vibrant saturation
                        hsv[2] = hsv[2].coerceIn(0.65f, 0.90f) // Pleasant brightness
                        Color(android.graphics.Color.HSVToColor(hsv))
                    } ?: Color(0xFFD0BCFF)
                    
                    val hsvSec = FloatArray(3)
                    android.graphics.Color.colorToHSV(mainColor.toArgb(), hsvSec)
                    hsvSec[0] = (hsvSec[0] + 60f) % 360f
                    val secondColor = Color(android.graphics.Color.HSVToColor(hsvSec))
                    
                    val hsvTer = FloatArray(3)
                    android.graphics.Color.colorToHSV(mainColor.toArgb(), hsvTer)
                    hsvTer[0] = (hsvTer[0] - 60f + 360f) % 360f
                    val thirdColor = Color(android.graphics.Color.HSVToColor(hsvTer))

                    resolvedColor = mainColor
                    resolvedColors = listOf(mainColor, secondColor, thirdColor)
                }
                ThemeColorMode.ART_VIBRANT -> {
                    val mainColor = song.albumArtColor?.let { Color(it) } ?: extractDominantFromArt(song) ?: Color(0xFFD0BCFF)
                    
                    val hsvSec = FloatArray(3)
                    android.graphics.Color.colorToHSV(mainColor.toArgb(), hsvSec)
                    hsvSec[0] = (hsvSec[0] + 30f) % 360f
                    hsvSec[1] = (hsvSec[1] * 0.8f).coerceAtLeast(0.5f)
                    val secondColor = Color(android.graphics.Color.HSVToColor(hsvSec))
                    
                    val hsvTer = FloatArray(3)
                    android.graphics.Color.colorToHSV(mainColor.toArgb(), hsvTer)
                    hsvTer[0] = (hsvTer[0] - 30f + 360f) % 360f
                    hsvTer[2] = (hsvTer[2] * 0.8f).coerceAtLeast(0.5f)
                    val thirdColor = Color(android.graphics.Color.HSVToColor(hsvTer))

                    resolvedColor = mainColor
                    resolvedColors = listOf(mainColor, secondColor, thirdColor)
                }
                ThemeColorMode.EXACT_ART_COLORS -> {
                    resolvedColors = getTrackColors(song)
                    resolvedColor = resolvedColors.first()
                }
            }

            _currentTrackColor.value = resolvedColor
            _currentTrackColors.value = resolvedColors
            _artworkColorGrid.value = if (song != null) extractArtworkColorGrid(song) else null
        }
    }

    private fun extractArtworkColorGrid(song: Song): IntArray? {
        try {
            var artInputStream: java.io.InputStream? = null
            val context = getApplication<Application>()
            
            if (song.albumArtPath != null) {
                if (song.albumArtPath.startsWith("content://")) {
                    try {
                        artInputStream = context.contentResolver.openInputStream(Uri.parse(song.albumArtPath))
                    } catch (e: Exception) {}
                } else {
                    val file = File(song.albumArtPath)
                    if (file.exists()) artInputStream = file.inputStream()
                }
            }

            if (artInputStream == null && song.path.startsWith("content://")) {
                try {
                    artInputStream = context.contentResolver.openInputStream(Uri.parse(song.path))
                } catch (e: Exception) {}
            }

            if (artInputStream == null) {
                val file = File(song.path)
                if (file.exists()) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(song.path)
                        val bytes = retriever.embeddedPicture
                        if (bytes != null) artInputStream = java.io.ByteArrayInputStream(bytes)
                    } catch (e: Exception) {
                    } finally {
                        retriever.release()
                    }
                }
            }

            artInputStream?.use { stream ->
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
                
                // reset stream to reread
                stream.reset()
            }
        } catch (e: Exception) {}
        
        // Let's do a simpler stream reopen for decoded
        try {
            var artInputStream: java.io.InputStream? = null
            val context = getApplication<Application>()
            if (song.albumArtPath != null && song.albumArtPath.startsWith("content://")) {
                artInputStream = context.contentResolver.openInputStream(Uri.parse(song.albumArtPath))
            } else if (song.albumArtPath != null) {
                val file = File(song.albumArtPath)
                if (file.exists()) artInputStream = file.inputStream()
            }
            if (artInputStream == null && song.path.startsWith("content://")) {
               artInputStream = context.contentResolver.openInputStream(Uri.parse(song.path))
            }
            if (artInputStream == null) {
                val file = File(song.path)
                if (file.exists()) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(song.path)
                        val bytes = retriever.embeddedPicture
                        if (bytes != null) artInputStream = java.io.ByteArrayInputStream(bytes)
                    } finally { retriever.release() }
                }
            }
            
            artInputStream?.use { stream ->
                val options = android.graphics.BitmapFactory.Options().apply {
                     inSampleSize = 4
                }
                val bitmap = android.graphics.BitmapFactory.decodeStream(stream, null, options)
                if (bitmap != null) {
                    val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 64, 64, true)
                    val pixels = IntArray(64 * 64)
                    scaled.getPixels(pixels, 0, 64, 0, 0, 64, 64)
                    if (scaled != bitmap) scaled.recycle()
                    bitmap.recycle()
                    return pixels
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        return null
    }

    private fun extractDominantFromArt(song: Song): Color? {
        try {
            var artInputStream: java.io.InputStream? = null
            val context = getApplication<Application>()
            
            if (song.albumArtPath != null) {
                if (song.albumArtPath.startsWith("content://")) {
                    try {
                        artInputStream = context.contentResolver.openInputStream(Uri.parse(song.albumArtPath))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    val file = File(song.albumArtPath)
                    if (file.exists()) {
                        artInputStream = file.inputStream()
                    }
                }
            }

            if (artInputStream == null && song.path.startsWith("content://")) {
                try {
                    artInputStream = context.contentResolver.openInputStream(Uri.parse(song.path))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (artInputStream == null) {
                val file = File(song.path)
                if (file.exists()) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(song.path)
                        val bytes = retriever.embeddedPicture
                        if (bytes != null) {
                            artInputStream = java.io.ByteArrayInputStream(bytes)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        retriever.release()
                    }
                }
            }

            artInputStream?.use { stream ->
                val options = android.graphics.BitmapFactory.Options().apply {
                     inSampleSize = 8
                }
                val bitmap = android.graphics.BitmapFactory.decodeStream(stream, null, options)
                if (bitmap != null) {
                    var maxSat = -1f
                    var bestColor = 0
                    val hsv = FloatArray(3)
                    for (x in 0 until bitmap.width step 4) {
                        for (y in 0 until bitmap.height step 4) {
                            if (x < bitmap.width && y < bitmap.height) {
                                val pixel = bitmap.getPixel(x, y)
                                android.graphics.Color.colorToHSV(pixel, hsv)
                                val saturation = hsv[1]
                                val value = hsv[2]
                                if (saturation > maxSat && value > 0.2f && value < 0.9f) {
                                    maxSat = saturation
                                    bestColor = pixel
                                }
                            }
                        }
                    }
                    bitmap.recycle()
                    if (bestColor != 0) {
                        return Color(bestColor)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
