package com.example.data

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val isShuffle: Boolean = false,
    val isRepeat: Boolean = false,
    val crossfadeEnabled: Boolean = false,
    val crossfadeSeconds: Int = 3,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val currentQueue: List<Song> = emptyList(),
    val bgBlurRadius: Float = 10f,
    val bgAlpha: Float = 0.24f,
    val bgScale: Float = 2.4f,
    val bgWaveOpacity: Float = 0.35f,
    val bgContentScale: String = "Crop"
)
