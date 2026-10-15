package com.perso.scanix.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ScanDoc(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Noms des fichiers images des pages, dans l'ordre */
    val pages: List<String>,
    val dir: File,
) {
    fun pageFile(name: String) = File(dir, name)
    val pageFiles: List<File> get() = pages.map { File(dir, it) }
    val cover: File? get() = pages.firstOrNull()?.let { File(dir, it) }
}

/**
 * Stockage des documents dans la mémoire privée de l'appli :
 *   files/docs/<id>/meta.json  +  p_xxx.jpg (une image par page)
 */
class DocumentStore(private val context: Context) {

    private val root = File(context.filesDir, "docs").apply { mkdirs() }
    private val _docs = MutableStateFlow<List<ScanDoc>>(emptyList())
    val docs: StateFlow<List<ScanDoc>> = _docs

    suspend fun load() = withContext(Dispatchers.IO) {
        _docs.value = root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readMeta(it) }
            ?.sortedByDescending { it.updatedAt }
            .orEmpty()
    }

    private fun readMeta(dir: File): ScanDoc? = runCatching {
        val o = JSONObject(File(dir, "meta.json").readText())
        val arr = o.getJSONArray("pages")
        ScanDoc(
            id = dir.name,
            name = o.getString("name"),
            createdAt = o.getLong("created"),
            updatedAt = o.getLong("updated"),
            pages = (0 until arr.length()).map { arr.getString(it) }.filter { File(dir, it).exists() },
            dir = dir,
        )
    }.getOrNull()

    private fun writeMeta(doc: ScanDoc) {
        val o = JSONObject()
            .put("name", doc.name)
            .put("created", doc.createdAt)
            .put("updated", doc.updatedAt)
            .put("pages", JSONArray(doc.pages))
        val tmp = File(doc.dir, "meta.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(File(doc.dir, "meta.json"))
    }

    private fun save(doc: ScanDoc): ScanDoc {
        writeMeta(doc)
        _docs.value = (listOf(doc) + _docs.value.filterNot { it.id == doc.id }).sortedByDescending { it.updatedAt }
        return doc
    }

    fun get(id: String): ScanDoc? = _docs.value.firstOrNull { it.id == id }

    private fun copyPages(dir: File, uris: List<Uri>): List<String> = uris.mapNotNull { uri ->
        val name = "p_${System.nanoTime()}.jpg"
        val out = File(dir, name)
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            // Les images importées de la galerie peuvent être énormes : on les réduit
            normalizeImage(out)
            name
        }.getOrNull()?.takeIf { out.exists() && out.length() > 0 }
    }

    /** Crée un nouveau document à partir d'images (pages scannées ou importées). */
    suspend fun create(uris: List<Uri>, name: String? = null): ScanDoc? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dir = File(root, now.toString()).apply { mkdirs() }
        val pages = copyPages(dir, uris)
        if (pages.isEmpty()) {
            dir.deleteRecursively(); return@withContext null
        }
        val defaultName = "Scan " + SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.FRANCE).format(Date(now))
        save(ScanDoc(now.toString(), name ?: defaultName, now, now, pages, dir))
    }

    suspend fun addPages(id: String, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val doc = get(id) ?: return@withContext
        val pages = copyPages(doc.dir, uris)
        save(doc.copy(pages = doc.pages + pages, updatedAt = System.currentTimeMillis()))
    }

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        val doc = get(id) ?: return@withContext
        if (name.isBlank()) return@withContext
        save(doc.copy(name = name.trim(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val doc = get(id) ?: return@withContext
        doc.dir.deleteRecursively()
        _docs.value = _docs.value.filterNot { it.id == id }
    }

    suspend fun deletePage(id: String, page: String) = withContext(Dispatchers.IO) {
        val doc = get(id) ?: return@withContext
        File(doc.dir, page).delete()
        val rest = doc.pages - page
        if (rest.isEmpty()) delete(id)
        else save(doc.copy(pages = rest, updatedAt = System.currentTimeMillis()))
    }

    suspend fun movePage(id: String, from: Int, to: Int) = withContext(Dispatchers.IO) {
        val doc = get(id) ?: return@withContext
        if (from !in doc.pages.indices || to !in doc.pages.indices) return@withContext
        val list = doc.pages.toMutableList()
        list.add(to, list.removeAt(from))
        save(doc.copy(pages = list, updatedAt = System.currentTimeMillis()))
    }

    /** Pivote une page de 90° (l'image est réécrite sous un nouveau nom pour rafraîchir l'affichage). */
    suspend fun rotatePage(id: String, page: String, clockwise: Boolean = true) = withContext(Dispatchers.IO) {
        val doc = get(id) ?: return@withContext
        val src = File(doc.dir, page)
        val bmp = BitmapFactory.decodeFile(src.absolutePath) ?: return@withContext
        val m = Matrix().apply { postRotate(if (clockwise) 90f else -90f) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val newName = "p_${System.nanoTime()}.jpg"
        File(doc.dir, newName).outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle(); rotated.recycle()
        src.delete()
        save(doc.copy(pages = doc.pages.map { if (it == page) newName else it }, updatedAt = System.currentTimeMillis()))
    }

    // ───────────────────────── Export ─────────────────────────

    private fun safeName(doc: ScanDoc) =
        doc.name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "Scan" }

    /** Construit le PDF (format A4, sans filigrane). */
    suspend fun buildPdf(doc: ScanDoc): File = withContext(Dispatchers.IO) {
        val outDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val out = File(outDir, safeName(doc) + ".pdf")
        val pdf = PdfDocument()
        try {
            doc.pageFiles.forEachIndexed { i, f ->
                val bmp = decodeSampled(f, 2000) ?: return@forEachIndexed
                // Page A4 dans l'orientation de l'image (en points : 1/72 de pouce)
                val landscape = bmp.width > bmp.height
                val pw = if (landscape) 842 else 595
                val ph = if (landscape) 595 else 842
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                val scale = minOf(pw.toFloat() / bmp.width, ph.toFloat() / bmp.height)
                val w = (bmp.width * scale).toInt()
                val h = (bmp.height * scale).toInt()
                val left = (pw - w) / 2
                val top = (ph - h) / 2
                canvas.drawBitmap(bmp, null, Rect(left, top, left + w, top + h), null)
                pdf.finishPage(page)
                bmp.recycle()
            }
            out.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        out
    }

    fun shareUri(file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file)

    /** Enregistre le PDF dans le dossier « Téléchargements/Scanix ». Renvoie le chemin lisible. */
    suspend fun saveToDownloads(doc: ScanDoc): String = withContext(Dispatchers.IO) {
        val pdf = buildPdf(doc)
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, pdf.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Scanix")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Impossible de créer le fichier")
            context.contentResolver.openOutputStream(uri)?.use { out -> pdf.inputStream().use { it.copyTo(out) } }
            "Téléchargements/Scanix/${pdf.name}"
        } else {
            // Android 8–9 : dossier de l'appli, accessible via le gestionnaire de fichiers
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Scanix").apply { mkdirs() }
            val dest = File(dir, pdf.name)
            pdf.copyTo(dest, overwrite = true)
            dest.absolutePath
        }
    }

    // ───────────────────────── OCR ─────────────────────────

    /** Extrait le texte des pages (hors ligne). */
    suspend fun recognizeText(files: List<File>): String = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            files.mapIndexed { i, f ->
                val image = InputImage.fromFilePath(context, Uri.fromFile(f))
                val text = recognizer.process(image).await().text.trim()
                if (files.size > 1) "— Page ${i + 1} —\n$text" else text
            }.joinToString("\n\n")
        } finally {
            recognizer.close()
        }
    }

    // ───────────────────────── Images ─────────────────────────

    private fun normalizeImage(file: File, maxSide: Int = 2600) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0) {
            file.delete(); return
        }
        if (maxOf(opts.outWidth, opts.outHeight) <= maxSide) return
        val bmp = decodeSampled(file, maxSide) ?: return
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
    }

    private fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val big = maxOf(bmp.width, bmp.height)
        if (big <= maxSide) return bmp
        val s = maxSide.toFloat() / big
        val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }
}

/** Attend le résultat d'une tâche Google Play services dans une coroutine. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
