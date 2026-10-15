package com.perso.melodia.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
}

/**
 * Récupère les paroles :
 *  1. depuis le cache de l'appli,
 *  2. depuis un fichier .lrc placé à côté de la musique (même nom),
 *  3. sinon en ligne via LRCLIB (service gratuit, sans compte ni clé).
 */
class LyricsRepository(context: Context) {

    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    private data class Raw(val synced: String?, val plain: String?)

    suspend fun getLyrics(
        song: Song,
        manualTitle: String? = null,
        manualArtist: String? = null,
    ): Lyrics? = withContext(Dispatchers.IO) {
        val manual = manualTitle != null
        if (!manual) {
            readCache(song)?.let { return@withContext it }
            readLocalLrc(song)?.let { return@withContext it }
        }
        val (artist, title) =
            if (manual) manualArtist.orEmpty() to manualTitle.orEmpty() else guessArtistTitle(song)

        val raw = runCatching { fetchOnline(artist, title, song.album, song.durationMs) }.getOrNull()
            ?: return@withContext null
        writeCache(song, raw)
        toLyrics(raw)
    }

    // ---------- Cache & fichiers locaux ----------

    private fun cacheFile(song: Song, ext: String) = File(dir, "${song.id}.$ext")

    private fun readCache(song: Song): Lyrics? {
        val lrc = cacheFile(song, "lrc")
        if (lrc.exists()) return toLyrics(Raw(lrc.readText(), null))
        val txt = cacheFile(song, "txt")
        if (txt.exists()) return toLyrics(Raw(null, txt.readText()))
        return null
    }

    private fun writeCache(song: Song, raw: Raw) {
        runCatching {
            if (!raw.synced.isNullOrBlank()) cacheFile(song, "lrc").writeText(raw.synced)
            else if (!raw.plain.isNullOrBlank()) cacheFile(song, "txt").writeText(raw.plain)
        }
    }

    private fun readLocalLrc(song: Song): Lyrics? = runCatching {
        val path = song.path ?: return null
        val file = File(path.substringBeforeLast('.') + ".lrc")
        if (file.exists() && file.canRead()) toLyrics(Raw(file.readText(), null)) else null
    }.getOrNull()

    private fun toLyrics(raw: Raw): Lyrics? {
        raw.synced?.let { text ->
            val lines = parseLrc(text)
            if (lines.isNotEmpty()) return Lyrics.Synced(lines)
        }
        val plain = raw.plain ?: raw.synced
        return plain?.takeIf { it.isNotBlank() }?.let { Lyrics.Plain(it.trim()) }
    }

    // ---------- Recherche en ligne (LRCLIB) ----------

    private fun fetchOnline(artist: String, title: String, album: String, durationMs: Long): Raw? {
        if (title.isBlank()) return null
        val sec = durationMs / 1000

        if (artist.isNotBlank()) {
            val url = "$API/get?artist_name=${enc(artist)}&track_name=${enc(title)}" +
                "&album_name=${enc(album)}&duration=$sec"
            httpGet(url)?.let { body -> JSONObject(body).toRaw()?.let { return it } }

            httpGet("$API/search?track_name=${enc(title)}&artist_name=${enc(artist)}")
                ?.let { pickBest(JSONArray(it), durationMs) }?.let { return it }

            httpGet("$API/search?q=${enc("$artist $title")}")
                ?.let { pickBest(JSONArray(it), durationMs) }?.let { return it }
        }
        return httpGet("$API/search?q=${enc(title)}")?.let { pickBest(JSONArray(it), durationMs) }
    }

    private fun pickBest(results: JSONArray, durationMs: Long): Raw? {
        var best: Raw? = null
        var bestScore = Double.MAX_VALUE
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val raw = o.toRaw() ?: continue
            val diff = if (durationMs > 0) abs(o.optDouble("duration", 0.0) - durationMs / 1000.0) else 0.0
            if (durationMs > 0 && diff > 15) continue
            val score = diff + if (raw.synced != null) 0.0 else 5.0
            if (score < bestScore) {
                bestScore = score; best = raw
            }
        }
        return best
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.toRaw(): Raw? {
        if (optBoolean("instrumental", false)) return null
        val synced = str("syncedLyrics")
        val plain = str("plainLyrics")
        return if (synced == null && plain == null) null else Raw(synced, plain)
    }

    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "Melodia/1.0 (lecteur personnel Android)")
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val API = "https://lrclib.net/api"

        private val NOISE = Regex(
            """[(\[][^)\]]*(official|officiel|audio|video|vidéo|clip|lyrics|paroles|visuali[sz]er|hd|hq|4k|remaster)[^)\]]*[)\]]""",
            RegexOption.IGNORE_CASE,
        )
        private val EXT = Regex("""\.(mp3|m4a|flac|wav|ogg|opus|aac|wma)$""", RegexOption.IGNORE_CASE)
        private val TRACK_NO = Regex("""^\s*\d{1,3}\s*[-._)]\s+""")
        private val SPACES = Regex("""\s+""")

        fun clean(s: String): String = s
            .replace(EXT, "")
            .replace(NOISE, "")
            .replace(TRACK_NO, "")
            .replace('_', ' ')
            .replace(SPACES, " ")
            .trim()

        /** Devine artiste et titre, y compris depuis des noms de fichier du type « Artiste - Titre ». */
        fun guessArtistTitle(song: Song): Pair<String, String> {
            var title = clean(song.title)
            var artist = if (song.artist == UNKNOWN_ARTIST) "" else song.artist
            if (artist.isEmpty() && title.contains(" - ")) {
                artist = title.substringBefore(" - ").trim()
                title = title.substringAfter(" - ").trim()
            }
            return artist to title
        }

        private val TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

        fun parseLrc(text: String): List<LyricLine> {
            val out = mutableListOf<LyricLine>()
            text.lineSequence().forEach { line ->
                val tags = TAG.findAll(line).toList()
                if (tags.isEmpty()) return@forEach
                val content = line.substring(tags.last().range.last + 1).trim()
                for (m in tags) {
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3]
                    val ms = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                    out += LyricLine(min * 60_000 + sec * 1000 + ms, content)
                }
            }
            return out.sortedBy { it.timeMs }
        }
    }
}
