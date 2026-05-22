package com.example.data

import kotlinx.coroutines.flow.Flow

class MusicRepository(private val musicDao: MusicDao) {

    val allSongs: Flow<List<Song>> = musicDao.getAllSongs()
    val favoriteSongs: Flow<List<Song>> = musicDao.getFavoriteSongs()
    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists()

    suspend fun getSongById(id: Long) = musicDao.getSongById(id)

    suspend fun insertSong(song: Song): Long = musicDao.insertSong(song)

    suspend fun updateSong(song: Song) = musicDao.updateSong(song)

    suspend fun deleteSongById(id: Long) = musicDao.deleteSongById(id)

    suspend fun insertPlaylist(playlist: Playlist): Long = musicDao.insertPlaylist(playlist)

    suspend fun deletePlaylistById(id: Long) = musicDao.deletePlaylistById(id)

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        musicDao.insertPlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        musicDao.deletePlaylistSongCrossRef(PlaylistSongCrossRef(playlistId, songId))
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return musicDao.getSongsForPlaylist(playlistId)
    }
}
