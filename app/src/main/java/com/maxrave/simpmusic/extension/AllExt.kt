package com.maxrave.simpmusic.extension

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.net.toUri
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.sqlite.db.SimpleSQLiteQuery
import com.maxrave.kotlinytmusicscraper.models.SongItem
import com.maxrave.kotlinytmusicscraper.models.VideoItem
import com.maxrave.kotlinytmusicscraper.models.response.PipedResponse
import com.maxrave.kotlinytmusicscraper.models.spotify.ArtistX
import com.maxrave.kotlinytmusicscraper.models.youtube.YouTubeInitialPage
import com.maxrave.simpmusic.common.SETTINGS_FILENAME
import com.maxrave.simpmusic.data.db.entities.AlbumEntity
import com.maxrave.simpmusic.data.db.entities.LyricsEntity
import com.maxrave.simpmusic.data.db.entities.PlaylistEntity
import com.maxrave.simpmusic.data.db.entities.SearchHistory
import com.maxrave.simpmusic.data.db.entities.SongEntity
import com.maxrave.simpmusic.data.model.browse.album.AlbumBrowse
import com.maxrave.simpmusic.data.model.browse.album.Track
import com.maxrave.simpmusic.data.model.browse.artist.ResultSong
import com.maxrave.simpmusic.data.model.browse.artist.ResultVideo
import com.maxrave.simpmusic.data.model.browse.playlist.PlaylistBrowse
import com.maxrave.simpmusic.data.model.home.Content
import com.maxrave.simpmusic.data.model.metadata.Line
import com.maxrave.simpmusic.data.model.metadata.Lyrics
import com.maxrave.simpmusic.data.model.searchResult.songs.Album
import com.maxrave.simpmusic.data.model.searchResult.songs.Artist
import com.maxrave.simpmusic.data.model.searchResult.songs.SongsResult
import com.maxrave.simpmusic.data.model.searchResult.songs.Thumbnail
import com.maxrave.simpmusic.data.model.searchResult.videos.VideosResult
import com.maxrave.simpmusic.data.parser.toListThumbnail
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

val Context.dataStore by preferencesDataStore(name = SETTINGS_FILENAME)

fun Context.isMyServiceRunning(serviceClass: Class<out Service>) = try {
    (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(Int.MAX_VALUE)
        .any { it.service.className == serviceClass.name }
} catch (e: Exception) {
    false
}

fun SearchHistory.toQuery(): String {
    return this.query
}
fun List<SearchHistory>.toQueryList(): ArrayList<String> {
    val list = ArrayList<String>()
    for (item in this) {
        list.add(item.query)
    }
    return list
}
fun ResultSong.toTrack(): Track {
    return Track(
        album = album,
        artists = artists,
        duration = "",
        durationSeconds = 0,
        isAvailable = isAvailable,
        isExplicit = isExplicit,
        likeStatus = likeStatus,
        thumbnails = thumbnails,
        title = title,
        videoId = videoId,
        videoType = videoType,
        category = null,
        feedbackTokens = null,
        resultType = null,
        year = ""
    )
}
fun ResultVideo.toTrack(): Track {
    return Track(
        album = null,
        artists = this.artists ?: listOf(),
        duration = this.duration,
        durationSeconds = this.durationSeconds,
        isAvailable = false,
        isExplicit = false,
        likeStatus = null,
        thumbnails = this.thumbnails,
        title = this.title,
        videoId = this.videoId,
        videoType = null,
        category = null,
        feedbackTokens = null,
        resultType = null,
        year = ""
    )
}

fun SongsResult.toTrack(): Track {
    return Track(
        this.album,
        this.artists,
        this.duration ?: "",
        this.durationSeconds?: 0,
        true,
        this.isExplicit ?: false,
        "",
        this.thumbnails,
        this.title ?: "",
        this.videoId,
        this.videoType ?: "",
        this.category,
        this.feedbackTokens,
        this.resultType,
        ""
    )
}
fun SongItem.toTrack(): Track {
    return Track(
        album = this.album.let { Album(it?.id ?: "", it?.name ?: "")},
        artists = this.artists.map { artist -> Artist(id = artist.id ?: "", name = artist.name)  },
        duration = this.duration.toString(),
        durationSeconds = this.duration,
        isAvailable = false,
        isExplicit = this.explicit,
        likeStatus = null,
        thumbnails = this.thumbnails?.thumbnails?.toListThumbnail() ?: listOf(),
        title = this.title,
        videoId = this.id,
        videoType = null,
        category = null,
        feedbackTokens = null,
        resultType = null,
        year = null
    )
}
fun VideoItem.toTrack(): Track {
    return Track(
        album = this.album.let { Album(it?.id ?: "", it?.name ?: "")} ,
        artists = this.artists.map { artist -> Artist(id = artist.id ?: "", name = artist.name)  },
        duration = this.duration.toString(),
        durationSeconds = this.duration,
        isAvailable = false,
        isExplicit = false,
        likeStatus = null,
        thumbnails = this.thumbnails?.thumbnails?.toListThumbnail() ?: listOf(),
        title = this.title,
        videoId = this.id,
        videoType = null,
        category = null,
        feedbackTokens = null,
        resultType = null,
        year = null
    )
}
fun List<SongItem>?.toListTrack(): ArrayList<Track> {
    val listTrack = arrayListOf<Track>()
    if (this != null) {
        for (item in this) {
            listTrack.add(item.toTrack())
        }
    }
    return listTrack
}
fun List<Artist>?.toListName(): List<String> {
    val list = mutableListOf<String>()
    if (this != null){
        for (item in this) {
            list.add(item.name)
        }
    }
    return list
}
fun List<Artist>?.toListId(): List<String> {
    val list = mutableListOf<String>()
    if (this != null){
        for (item in this) {
            list.add(item.id ?: "")
        }
    }
    return list
}
fun List<String>.connectArtists(): String {
    val stringBuilder = StringBuilder()

    for ((index, artist) in this.withIndex()) {
        stringBuilder.append(artist)

        if (index < this.size - 1) {
            stringBuilder.append(", ")
        }
    }

    return stringBuilder.toString()
}
fun Track.toSongEntity(): SongEntity {
    return SongEntity(
        videoId = this.videoId,
        albumId = this.album?.id,
        albumName = this.album?.name,
        artistId = this.artists?.toListId(),
        artistName = this.artists?.toListName(),
        duration = this.duration ?: "",
        durationSeconds = this.durationSeconds ?: 0,
        isAvailable = this.isAvailable,
        isExplicit = this.isExplicit,
        likeStatus = this.likeStatus ?: "",
        thumbnails = this.thumbnails?.last()?.url,
        title = this.title,
        videoType = this.videoType ?: "",
        category = this.category,
        resultType = this.resultType,
        liked = false,
        totalPlayTime = 0,
        downloadState = 0
    )
}

fun SongEntity.toTrack(): Track {
    val listArtist = mutableListOf<Artist>()
    if (this.artistName != null ) {
        for (i in 0 until this.artistName.size) {
            listArtist.add(Artist(this.artistId?.get(i) ?: "", this.artistName[i]))
        }
    }
    return Track(
        album = this.albumId?.let { this.albumName?.let { it1 -> Album(it, it1) } },
        artists = listArtist,
        duration = this.duration,
        durationSeconds = this.durationSeconds,
        isAvailable = this.isAvailable,
        isExplicit = this.isExplicit,
        likeStatus = this.likeStatus,
        thumbnails = listOf(Thumbnail(720, this.thumbnails ?: "",1080)),
        title = this.title,
        videoId = this.videoId,
        videoType = this.videoType,
        category = this.category,
        feedbackTokens = null,
        resultType = null,
        year = ""
    )
}
fun List<SongEntity>?.toArrayListTrack(): ArrayList<Track> {
    val listTrack: ArrayList<Track> = arrayListOf()
    if (this != null) {
        for (item in this) {
            listTrack.add(item.toTrack())
        }
    }
    return listTrack
}

fun MediaItem?.toSongEntity(): SongEntity? {
    return if (this != null) SongEntity(
        videoId = this.mediaId,
        albumId = null,
        albumName = this.mediaMetadata.albumTitle.toString(),
        artistId = null,
        artistName = listOf(this.mediaMetadata.artist.toString()),
        duration =  "",
        durationSeconds = 0,
        isAvailable = true,
        isExplicit = false,
        likeStatus = "INDIFFERENT",
        thumbnails = this.mediaMetadata.artworkUri.toString(),
        title = this.mediaMetadata.title.toString(),
        videoType = "",
        category = "",
        resultType = "",
        liked = false,
        totalPlayTime = 0,
        downloadState = 0
    ) else null
}
@UnstableApi
fun Track.toMediaItem() : MediaItem {
    return MediaItem.Builder()
        .setMediaId(this.videoId)
        .setUri(this.videoId)
        .setCustomCacheKey(this.videoId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(this.title)
                .setArtist(this.artists.toListName().connectArtists())
                .setArtworkUri(this.thumbnails?.lastOrNull()?.url?.toUri())
                .setAlbumTitle(this.album?.name)
                .build()
        )
        .build()
}

@UnstableApi
fun List<Track>.toMediaItems(): List<MediaItem> {
    val listMediaItem = mutableListOf<MediaItem>()
    for (item in this) {
        listMediaItem.add(item.toMediaItem())
    }
    return listMediaItem
}

@JvmName("SongResulttoListTrack")
fun ArrayList<SongsResult>.toListTrack(): ArrayList<Track> {
    val listTrack = arrayListOf<Track>()
    for (song in this) {
        listTrack.add(song.toTrack())
    }
    return listTrack
}

fun VideosResult.toTrack(): Track {
    val thumb = Thumbnail(720, "http://i.ytimg.com/vi/${this.videoId}/maxresdefault.jpg", 1280)
    val thumbList: List<Thumbnail>?
    thumbList = this.thumbnails ?: mutableListOf(thumb)
    return Track(
        album = null,
        artists = this.artists,
        duration = this.duration?: "",
        durationSeconds = this.durationSeconds?: 0,
        isAvailable = true,
        isExplicit = false,
        likeStatus = "INDIFFERENT",
        thumbnails = thumbList,
        title = this.title,
        videoId = this.videoId,
        videoType = this.videoType?: "",
        category = this.category,
        feedbackTokens = null,
        resultType = this.resultType,
        year = "")
}

@JvmName("VideoResulttoTrack")
fun ArrayList<VideosResult>.toListTrack(): ArrayList<Track> {
    val listTrack = arrayListOf<Track>()
    for (video in this) {
        listTrack.add(video.toTrack())
    }
    return listTrack
}

fun Content.toTrack(): Track {
    return Track(
        album = album,
        artists = artists ?: listOf(Artist("", "")),
        duration = "",
        durationSeconds = 0,
        isAvailable = false,
        isExplicit = false,
        likeStatus = "INDIFFERENT",
        thumbnails = thumbnails,
        title = title,
        videoId = videoId!!,
        videoType = "",
        category = null,
        feedbackTokens = null,
        resultType = null,
        year = ""
    )
}
fun List<Track>.toListVideoId(): List<String> {
    val list = mutableListOf<String>()
    for (item in this) {
        list.add(item.videoId)
    }
    return list
}

fun AlbumBrowse.toAlbumEntity(id : String): AlbumEntity {
    return AlbumEntity(
        browseId = id,
        artistId = this.artists.toListId(),
        artistName = this.artists.toListName(),
        audioPlaylistId = this.audioPlaylistId,
        description = this.description ?: "",
        duration = this.duration,
        durationSeconds = this.durationSeconds,
        thumbnails = this.thumbnails?.last()?.url,
        title = this.title,
        trackCount = this.trackCount,
        tracks = this.tracks.toListVideoId(),
        type = this.type,
        year = this.year
    )
}

fun PlaylistBrowse.toPlaylistEntity(): PlaylistEntity {
    return PlaylistEntity(
        id = this.id,
        author = this.author.name,
        description = this.description ?: "",
        duration = this.duration,
        durationSeconds = this.durationSeconds,
        privacy = this.privacy,
        thumbnails = this.thumbnails.last().url,
        title = this.title,
        trackCount = this.trackCount,
        tracks = this.tracks.toListVideoId(),
        year = this.year
    )
}

fun Track.addThumbnails(): Track {
    return Track(
        album = this.album,
        artists = this.artists,
        duration = this.duration,
        durationSeconds = this.durationSeconds,
        isAvailable = this.isAvailable,
        isExplicit = this.isExplicit,
        likeStatus = this.likeStatus,
        thumbnails = listOf(Thumbnail(720, "https://i.ytimg.com/vi/${this.videoId}/maxresdefault.jpg", 1280)),
        title = this.title,
        videoId = this.videoId,
        videoType = this.videoType,
        category = this.category,
        feedbackTokens = this.feedbackTokens,
        resultType = this.resultType,
        year = this.year
    )
}

fun LyricsEntity.toLyrics(): Lyrics {
    return Lyrics(
        error = this.error, lines = this.lines, syncType = this.syncType
    )
}

fun Lyrics.toLyricsEntity(videoId: String): LyricsEntity {
    return LyricsEntity(
        videoId = videoId, error = this.error, lines = this.lines, syncType = this.syncType
    )
}

fun setEnabledAll(v: View, enabled: Boolean) {
    v.isEnabled = enabled
    v.isFocusable = enabled
    if (v is ImageButton) {
        if (enabled) v.setColorFilter(Color.WHITE) else v.setColorFilter(Color.GRAY)
    }
    if (v is ViewGroup) {
        val vg = v
        for (i in 0 until vg.childCount) setEnabledAll(vg.getChildAt(i), enabled)
    }
}

fun ArrayList<String>.removeConflicts(): ArrayList<String> {
    val nonConflictingSet = HashSet<String>()
    val nonConflictingList = ArrayList<String>()

    for (item in this) {
        if (nonConflictingSet.add(item)) {
            nonConflictingList.add(item)
        }
    }

    return nonConflictingList
}

fun com.maxrave.kotlinytmusicscraper.models.lyrics.Lyrics.toLyrics(): Lyrics {
    val lines : ArrayList<Line> = arrayListOf()
    if (this.lyrics != null) {
        this.lyrics?.lines?.forEach {
            lines.add(Line(
                endTimeMs = it.endTimeMs, startTimeMs = it.startTimeMs, syllables = it.syllables ?: listOf(), words = it.words
            ))
        }
        return Lyrics(
            error = false,
            lines = lines,
            syncType = this.lyrics!!.syncType
        )
    }
    else {
        return Lyrics(
            error = true,
            lines = null,
            syncType = null
        )
    }

}
fun PipedResponse.toTrack(videoId: String): Track {
    return Track(
        album = null,
        artists = listOf(Artist(this.uploaderUrl?.replace("/channel/", ""), this.uploader.toString())),
        duration = "",
        durationSeconds = 0,
        isAvailable = false,
        isExplicit = false,
        likeStatus = "INDIFFERENT",
        thumbnails = listOf(Thumbnail(720,  this.thumbnailUrl ?: "https://i.ytimg.com/vi/${videoId}/maxresdefault.jpg", 1080)),
        title = this.title ?: " ",
        videoId = videoId,
        videoType = "Song",
        category = "",
        feedbackTokens = null,
        resultType = null,
        year = ""
    )
}
fun List<ArtistX?>?.connectArtistsSpotify(): String {
    val stringBuilder = StringBuilder()

    if (this != null) {
        for ((index, artist) in this.withIndex()) {
            stringBuilder.append(artist?.name)

            if (index < this.size - 1) {
                stringBuilder.append(" ")
            }
        }
    }

    return stringBuilder.toString()
}
fun YouTubeInitialPage.toTrack(): Track {
    val initialPage = this

    return Track(
        album = null,
        artists = listOf(Artist(initialPage.videoDetails?.author, initialPage.videoDetails?.channelId ?: "")),
        duration = initialPage.videoDetails?.lengthSeconds,
        durationSeconds = initialPage.videoDetails?.lengthSeconds?.toInt() ?: 0,
        isAvailable = false,
        isExplicit = false,
        likeStatus = null,
        thumbnails = initialPage.videoDetails?.thumbnail?.thumbnails?.toListThumbnail() ?: listOf(),
        title = initialPage.videoDetails?.title ?: "",
        videoId = initialPage.videoDetails?.videoId ?: "",
        videoType = "",
        category = "",
        feedbackTokens = null,
        resultType = "",
        year = ""
    )
}

operator fun File.div(child: String): File = File(this, child)
fun String.toSQLiteQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(this)
fun InputStream.zipInputStream(): ZipInputStream = ZipInputStream(this)
fun OutputStream.zipOutputStream(): ZipOutputStream = ZipOutputStream(this)

