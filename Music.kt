package com.perso.melodia.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.util.Collections

const val UNKNOWN_ARTIST = "Artiste inconnu"
const val UNKNOWN_ALBUM = "Album inconnu"

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val path: String?,
)

object MusicRepository {

    fun albumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    /** Lit toutes les musiques présentes sur le téléphone (via la médiathèque Android). */
    fun loadSongs(context: Context): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        // Uniquement la musique, et plus de 10 secondes (évite sonneries et notes vocales)
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = mutableListOf<Song>()
        context.contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val fileName = c.getString(nameCol).orEmpty()
                val title = c.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: fileName.substringBeforeLast('.')
                val artist = c.getString(artistCol)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: UNKNOWN_ARTIST
                val album = c.getString(albumCol)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: UNKNOWN_ALBUM
                songs += Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durCol),
                    uri = ContentUris.withAppendedId(collection, id),
                    path = c.getString(dataCol),
                )
            }
        }
        return songs
    }
}

/** Charge et met en cache les pochettes d'album intégrées aux fichiers. */
object ArtworkLoader {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val missing = Collections.synchronizedSet(HashSet<Long>())

    fun load(context: Context, song: Song, size: Int): Bitmap? {
        val key = "${song.id}-$size"
        cache.get(key)?.let { return it }
        if (missing.contains(song.id)) return null

        val bitmap = try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(song.uri, Size(size, size), null)
            } else {
                context.contentResolver.openInputStream(MusicRepository.albumArtUri(song.albumId))
                    ?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            null
        }

        if (bitmap != null) cache.put(key, bitmap) else missing.add(song.id)
        return bitmap
    }

    /** Couleur dominante de la pochette, assombrie pour servir de fond. */
    fun dominantColor(context: Context, song: Song): Int? {
        val bmp = load(context, song, 300) ?: return null
        return try {
            val soft = if (bmp.config == Bitmap.Config.HARDWARE) {
                bmp.copy(Bitmap.Config.ARGB_8888, false)
            } else bmp
            val px = Bitmap.createScaledBitmap(soft, 1, 1, true).getPixel(0, 0)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(px, hsv)
            hsv[1] = (hsv[1] * 1.2f).coerceAtMost(0.85f)
            hsv[2] = hsv[2].coerceIn(0.25f, 0.5f)
            android.graphics.Color.HSVToColor(hsv)
        } catch (e: Exception) {
            null
        }
    }
}
