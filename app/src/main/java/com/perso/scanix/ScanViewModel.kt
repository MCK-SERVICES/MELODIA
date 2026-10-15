package com.perso.scanix

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.perso.scanix.data.DocumentStore
import com.perso.scanix.data.ScanDoc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface OcrState {
    data object Idle : OcrState
    data object Running : OcrState
    data class Done(val title: String, val text: String) : OcrState
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    val store = DocumentStore(app)
    val docs: StateFlow<List<ScanDoc>> = store.docs

    private val _message = MutableStateFlow<String?>(null)
    /** Petit message à afficher (« PDF enregistré », erreurs…) */
    val message: StateFlow<String?> = _message

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _ocr = MutableStateFlow<OcrState>(OcrState.Idle)
    val ocr: StateFlow<OcrState> = _ocr

    /** Document ouvert à l'écran (null = accueil) */
    private val _openDoc = MutableStateFlow<String?>(null)
    val openDoc: StateFlow<String?> = _openDoc

    /** Si non nul, le prochain scan ajoute des pages à ce document */
    var scanTarget: String? = null

    init {
        viewModelScope.launch { store.load() }
    }

    fun open(id: String?) {
        _openDoc.value = id
    }

    fun say(msg: String) {
        _message.value = msg
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (e: Exception) {
                _message.value = "Erreur : ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _busy.value = false
            }
        }
    }

    /** Pages reçues du scanner ou de la galerie. */
    fun onPages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val target = scanTarget
        scanTarget = null
        work {
            if (target != null && store.get(target) != null) {
                store.addPages(target, uris)
                _message.value = "${uris.size} page${if (uris.size > 1) "s" else ""} ajoutée${if (uris.size > 1) "s" else ""}"
            } else {
                val doc = store.create(uris)
                if (doc != null) _openDoc.value = doc.id
                else _message.value = "Impossible de lire ces images"
            }
        }
    }

    fun rename(id: String, name: String) = work { store.rename(id, name) }

    fun delete(id: String) = work {
        if (_openDoc.value == id) _openDoc.value = null
        store.delete(id)
        _message.value = "Document supprimé"
    }

    fun deletePage(id: String, page: String) = work {
        store.deletePage(id, page)
        if (store.get(id) == null && _openDoc.value == id) _openDoc.value = null
    }

    fun rotatePage(id: String, page: String) = work { store.rotatePage(id, page) }
    fun movePage(id: String, from: Int, to: Int) = work { store.movePage(id, from, to) }

    fun saveToDownloads(id: String) = work {
        val doc = store.get(id) ?: return@work
        val path = store.saveToDownloads(doc)
        _message.value = "PDF enregistré : $path"
    }

    /** Prépare l'intention de partage du PDF (le lancement se fait depuis l'écran). */
    fun sharePdf(id: String, launch: (Intent) -> Unit) = work {
        val doc = store.get(id) ?: return@work
        val pdf = store.buildPdf(doc)
        launch(shareIntent(store.shareUri(pdf), "application/pdf", doc.name))
    }

    fun shareImage(file: File, title: String, launch: (Intent) -> Unit) {
        launch(shareIntent(store.shareUri(file), "image/jpeg", title))
    }

    fun shareText(text: String, launch: (Intent) -> Unit) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        launch(Intent.createChooser(send, "Partager le texte"))
    }

    private fun shareIntent(uri: Uri, mime: String, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Partager « $title »")
    }

    fun recognize(title: String, files: List<File>) {
        _ocr.value = OcrState.Running
        viewModelScope.launch {
            _ocr.value = try {
                val text = store.recognizeText(files)
                OcrState.Done(title, text.ifBlank { "Aucun texte détecté." })
            } catch (e: Exception) {
                _message.value = "Lecture du texte impossible : ${e.message ?: "erreur"}"
                OcrState.Idle
            }
        }
    }

    fun closeOcr() {
        _ocr.value = OcrState.Idle
    }
}
