package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val duration: Long,
    val path: String, // local file path in internal storage or Uri string
    val uriString: String? = null,
    val isFavorite: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val albumArtPath: String? = null,
    val albumArtColor: Int? = null
) : Serializable
