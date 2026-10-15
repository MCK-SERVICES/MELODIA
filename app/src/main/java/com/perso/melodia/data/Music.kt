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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
    /** Dossier qui contient le fichier, ex. /storage/emulated/0/Music/Rap */
    val folder: String,
)

object MusicRepository {

    fun albumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    /** Lit tous les fichiers audio du téléphone (le tri par dossier se fait ensuite). */
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
        val selection = "${MediaStore.Audio.Media.DURATION} >= 5000"
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
                val path = c.getString(dataCol)
                val title = c.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: fileName.substringBeforeLast('.')
                val artist = c.getString(artistCol)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: UNKNOWN_ARTIST
                val album = c.getString(albumCol)
                    ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: UNKNOWN_ALBUM
                val folder = path?.substringBeforeLast('/', "")
                    ?.takeIf { it.startsWith("/") }.orEmpty()
                songs += Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durCol),
                    uri = ContentUris.withAppendedId(collection, id),
                    path = path,
                    folder = folder,
                )
            }
        }
        return songs
    }
}

// ───────────────────────── Dossiers ─────────────────────────

data class FolderInfo(
    val path: String,
    /** Nom affiché dans l'arborescence */
    val name: String,
    /** Chemin lisible complet, ex. « Music/Rap » */
    val label: String,
    val depth: Int,
    val count: Int,
)

object Folders {
    private const val INTERNAL = "/storage/emulated/0"

    fun rootOf(p: String): String = when {
        p == INTERNAL || p.startsWith("$INTERNAL/") -> INTERNAL
        p.startsWith("/storage/") -> "/storage/" + p.removePrefix("/storage/").substringBefore('/')
        else -> "/" + p.trim('/').substringBefore('/')
    }

    fun label(p: String): String {
        val root = rootOf(p)
        val rel = p.removePrefix(root).trim('/')
        val rootName = when {
            root == INTERNAL -> "Stockage interne"
            root.startsWith("/storage/") -> "Carte SD"
            else -> root
        }
        return rel.ifEmpty { rootName }
    }

    /** Un titre est gardé si son dossier est un des dossiers choisis ou un de leurs sous-dossiers. */
    fun matches(folder: String, selected: Set<String>): Boolean =
        selected.any { folder == it || folder.startsWith("$it/") }

    /** Construit une arborescence compacte des dossiers qui contiennent de l'audio. */
    fun build(songs: List<Song>): List<FolderInfo> {
        val direct = songs.filter { it.folder.isNotEmpty() }.groupingBy { it.folder }.eachCount()
        val all = HashSet<String>()
        for (f in direct.keys) {
            val root = rootOf(f)
            var p = f
            while (true) {
                all += p
                if (p == root || p.length <= root.length || !p.contains('/')) break
                p = p.substringBeforeLast('/')
            }
        }
        val children = HashMap<String, Int>()
        for (p in all) {
            if (p == rootOf(p)) continue
            val parent = p.substringBeforeLast('/')
            children[parent] = (children[parent] ?: 0) + 1
        }
        // On masque les dossiers « de passage » (un seul sous-dossier, aucun audio direct)
        val shown = all.filter { it == rootOf(it) || direct.containsKey(it) || (children[it] ?: 0) >= 2 }.toSet()

        fun shownParent(p: String): String? {
            val root = rootOf(p)
            if (p == root) return null
            var q = p
            while (true) {
                val next = q.substringBeforeLast('/', "")
                if (next.isEmpty() || next == q || next.length < root.length) return null
                if (next in shown) return next
                q = next
            }
        }

        return shown.map { p ->
            val parent = shownParent(p)
            var depth = 0
            var q = parent
            while (q != null) {
                depth++; q = shownParent(q)
            }
            FolderInfo(
                path = p,
                name = if (parent == null) label(p) else p.removePrefix("$parent/"),
                label = label(p),
                depth = depth,
                count = songs.count { it.folder == p || it.folder.startsWith("$p/") },
            )
        }.sortedBy { it.path.lowercase().replace('/', '\u0001') }
    }
}

/** Réglages enregistrés sur le téléphone. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("melodia", Context.MODE_PRIVATE)

    /** null = pas encore configuré */
    var folders: Set<String>?
        get() = if (prefs.contains(KEY_FOLDERS)) prefs.getStringSet(KEY_FOLDERS, emptySet())!!.toSet() else null
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_FOLDERS) else putStringSet(KEY_FOLDERS, value)
            }.apply()
        }

    private companion object {
        const val KEY_FOLDERS = "folders"
    }
}

// ───────────────────────── Playlists ─────────────────────────

const val FAVORITES_ID = 0L

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>,
    val createdAt: Long,
) {
    val isFavorites get() = id == FAVORITES_ID
}

/** Playlists enregistrées dans un petit fichier JSON privé à l'appli. */
class PlaylistStore(context: Context) {
    private val file = File(context.filesDir, "playlists.json")
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Playlist>> = _items

    private fun load(): List<Playlist> {
        val list = runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("songs")
                Playlist(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    songIds = (0 until ids.length()).map { ids.getLong(it) },
                    createdAt = o.optLong("created", 0L),
                )
            }
        }.getOrDefault(emptyList())
        return if (list.any { it.isFavorites }) list
        else listOf(Playlist(FAVORITES_ID, "Titres favoris", emptyList(), 0L)) + list
    }

    private fun update(transform: (List<Playlist>) -> List<Playlist>) {
        val next = transform(_items.value)
        _items.value = next
        runCatching {
            val arr = JSONArray()
            next.forEach { p ->
                arr.put(
                    JSONObject()
                        .put("id", p.id)
                        .put("name", p.name)
                        .put("created", p.createdAt)
                        .put("songs", JSONArray(p.songIds))
                )
            }
            val tmp = File(file.parentFile, "playlists.json.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(file)
        }
    }

    fun create(name: String, songIds: List<Long> = emptyList()): Long {
        val id = System.currentTimeMillis()
        update { it + Playlist(id, name.trim().ifEmpty { "Nouvelle playlist" }, songIds.distinct(), id) }
        return id
    }

    fun rename(id: Long, name: String) = update { list ->
        list.map { if (it.id == id && name.isNotBlank()) it.copy(name = name.trim()) else it }
    }

    fun delete(id: Long) {
        if (id == FAVORITES_ID) return
        update { list -> list.filterNot { it.id == id } }
    }

    fun add(id: Long, songIds: List<Long>) = update { list ->
        list.map { p -> if (p.id == id) p.copy(songIds = p.songIds + songIds.filterNot { it in p.songIds }) else p }
    }

    fun remove(id: Long, songId: Long) = update { list ->
        list.map { p -> if (p.id == id) p.copy(songIds = p.songIds - songId) else p }
    }

    fun toggleFavorite(songId: Long) = update { list ->
        list.map { p ->
            if (!p.isFavorites) p
            else if (songId in p.songIds) p.copy(songIds = p.songIds - songId)
            else p.copy(songIds = listOf(songId) + p.songIds)
        }
    }
}

// ───────────────────────── Pochettes ─────────────────────────

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

    /** Couleur dominante de la pochette, saturée pour l'ambiance néon. */
    fun dominantColor(context: Context, song: Song): Int? {
        val bmp = load(context, song, 300) ?: return null
        return try {
            val soft = if (bmp.config == Bitmap.Config.HARDWARE) {
                bmp.copy(Bitmap.Config.ARGB_8888, false)
            } else bmp
            val px = Bitmap.createScaledBitmap(soft, 1, 1, true).getPixel(0, 0)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(px, hsv)
            hsv[1] = (hsv[1] * 1.4f).coerceIn(0.45f, 0.95f)
            hsv[2] = hsv[2].coerceIn(0.55f, 0.9f)
            android.graphics.Color.HSVToColor(hsv)
        } catch (e: Exception) {
            null
        }
    }
}
