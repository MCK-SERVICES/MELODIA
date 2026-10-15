@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.perso.scanix.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.perso.scanix.OcrState
import com.perso.scanix.ScanViewModel
import com.perso.scanix.data.ScanDoc
import kotlinx.coroutines.launch

/** Racine de l'interface. */
@Composable
fun ScanixApp(
    vm: ScanViewModel,
    onScan: (targetDocId: String?) -> Unit,
    onImport: () -> Unit,
    launch: (Intent) -> Unit,
) {
    val docs by vm.docs.collectAsStateWithLifecycle()
    val openId by vm.openDoc.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ocr by vm.ocr.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val openDoc = docs.firstOrNull { it.id == openId }

    BackHandler(enabled = openDoc != null && ocr is OcrState.Idle) { vm.open(null) }

    Box(Modifier.fillMaxSize().background(Ink)) {
        HomeScreen(docs, vm, onScan = { onScan(null) }, onImport = onImport, launch = launch)

        AnimatedVisibility(
            visible = openDoc != null,
            enter = fadeIn() + slideInVertically { it / 8 },
            exit = fadeOut() + slideOutVertically { it / 8 },
        ) {
            // Garde le dernier document affiché pendant l'animation de sortie
            var last by remember { mutableStateOf<ScanDoc?>(null) }
            if (openDoc != null) last = openDoc
            last?.let { DocumentScreen(it, vm, onBack = { vm.open(null) }, onAddPages = { onScan(it.id) }, launch = launch) }
        }

        if (busy) {
            LinearProgressIndicator(
                color = Mint,
                trackColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
            )
        }

        if (ocr !is OcrState.Idle) {
            OcrScreen(ocr, vm, launch)
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 90.dp))
    }
}

// ───────────────────────── Accueil ─────────────────────────

@Composable
private fun HomeScreen(
    docs: List<ScanDoc>,
    vm: ScanViewModel,
    onScan: () -> Unit,
    onImport: () -> Unit,
    launch: (Intent) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(docs, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) docs else docs.filter { it.name.lowercase().contains(q) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Scanix",
                        style = TextStyle(brush = Accent, fontSize = 32.sp, fontWeight = FontWeight.Black),
                    )
                    Text(
                        "${docs.size} document${if (docs.size > 1) "s" else ""}",
                        color = Dim, fontSize = 13.sp,
                    )
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Rechercher un document", color = Dim) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Dim) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Effacer") }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Panel,
                    unfocusedContainerColor = Panel,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Mint,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            )

            if (docs.isEmpty()) {
                EmptyHome(Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 120.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(shown, key = { it.id }) { doc ->
                        DocCard(doc, vm, launch) { vm.open(doc.id) }
                    }
                }
            }
        }

        // Barre d'action flottante
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction(Icons.Rounded.PhotoLibrary, "Importer des images", onImport)
            Spacer(Modifier.width(14.dp))
            AccentButton("Scanner", Icons.Rounded.DocumentScanner, onScan)
        }
    }
}

@Composable
private fun EmptyHome(modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(110.dp).clip(RoundedCornerShape(30.dp)).background(Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.DocumentScanner, null, tint = Ink, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Aucun document", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Touche « Scanner » pour numériser un document : les bords sont détectés et redressés automatiquement.",
            color = Dim, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DocCard(doc: ScanDoc, vm: ScanViewModel, launch: (Intent) -> Unit, onOpen: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(
        Modifier
            .padding(6.dp)
            .card()
            .clickable(onClick = onOpen)
            .padding(10.dp)
    ) {
        Box {
            AsyncImage(
                model = doc.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White),
            )
            Text(
                "${doc.pages.size}",
                color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Mint)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(doc.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(friendlyDate(doc.updatedAt), color = Dim, fontSize = 12.sp)
            }
            Box {
                IconButton({ menu = true }, Modifier.size(32.dp)) { Icon(Icons.Rounded.MoreVert, "Options", tint = Dim) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Partager en PDF") },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = { menu = false; vm.sharePdf(doc.id, launch) },
                    )
                    DropdownMenuItem(
                        text = { Text("Enregistrer le PDF") },
                        leadingIcon = { Icon(Icons.Rounded.Download, null) },
                        onClick = { menu = false; vm.saveToDownloads(doc.id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer", color = Danger) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Danger) },
                        onClick = { menu = false; deleting = true },
                    )
                }
            }
        }
    }

    if (renaming) RenameDialog(doc.name, onDismiss = { renaming = false }) { vm.rename(doc.id, it); renaming = false }
    if (deleting) DeleteDialog(doc.name, onDismiss = { deleting = false }) { vm.delete(doc.id); deleting = false }
}

// ───────────────────────── Document ─────────────────────────

@Composable
private fun DocumentScreen(
    doc: ScanDoc,
    vm: ScanViewModel,
    onBack: () -> Unit,
    onAddPages: () -> Unit,
    launch: (Intent) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = viewer != null) { viewer = null }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundAction(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onBack, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { renaming = true }
                        .padding(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            doc.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Edit, "Renommer", tint = Dim, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        "${doc.pages.size} page${if (doc.pages.size > 1) "s" else ""} · ${friendlyDate(doc.updatedAt)}",
                        color = Dim, fontSize = 12.sp,
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 12.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(doc.pages, key = { _, p -> p }) { i, page ->
                    Box(
                        Modifier
                            .padding(6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { viewer = i }
                    ) {
                        AsyncImage(
                            model = doc.pageFile(page),
                            contentDescription = "Page ${i + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f)
                                .background(Panel2),
                        )
                        Text(
                            "${i + 1}",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(Ink.copy(alpha = 0.75f))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // Barre d'outils
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .card(RoundedCornerShape(24.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                ToolAction(Icons.Rounded.AddAPhoto, "Ajouter", Modifier.weight(1f), tint = Mint, onClick = onAddPages)
                ToolAction(Icons.Rounded.Share, "Partager", Modifier.weight(1f)) { vm.sharePdf(doc.id, launch) }
                ToolAction(Icons.Rounded.Download, "PDF", Modifier.weight(1f)) { vm.saveToDownloads(doc.id) }
                ToolAction(Icons.Rounded.TextFields, "Texte", Modifier.weight(1f)) { vm.recognize(doc.name, doc.pageFiles) }
                ToolAction(Icons.Rounded.Delete, "Supprimer", Modifier.weight(1f), tint = Danger) { deleting = true }
            }
        }

        viewer?.let { start ->
            PageViewer(doc, start, vm, onClose = { viewer = null }, launch = launch)
        }
    }

    if (renaming) RenameDialog(doc.name, onDismiss = { renaming = false }) { vm.rename(doc.id, it); renaming = false }
    if (deleting) DeleteDialog(doc.name, onDismiss = { deleting = false }) { vm.delete(doc.id); deleting = false }
}

// ───────────────────────── Visionneuse ─────────────────────────

@Composable
private fun PageViewer(doc: ScanDoc, start: Int, vm: ScanViewModel, onClose: () -> Unit, launch: (Intent) -> Unit) {
    val count = doc.pages.size
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (count - 1).coerceAtLeast(0))) { count }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val index = pager.currentPage.coerceIn(0, (count - 1).coerceAtLeast(0))
    val page = doc.pages.getOrNull(index)

    LaunchedEffect(count) { if (count == 0) onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { doc.pages.getOrElse(it) { "$it" } }) { i ->
            doc.pages.getOrNull(i)?.let { ZoomableImage(doc.pageFile(it)) }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction(Icons.Rounded.Close, "Fermer", onClose, size = 44.dp)
            Spacer(Modifier.weight(1f))
            Text(
                "Page ${index + 1} / $count",
                color = Color.White, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Ink.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        if (page != null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Panel.copy(alpha = 0.92f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                ToolAction(Icons.Rounded.ChevronLeft, "Avant", Modifier.weight(1f)) {
                    if (index > 0) {
                        vm.movePage(doc.id, index, index - 1)
                        scope.launch { pager.animateScrollToPage(index - 1) }
                    }
                }
                ToolAction(Icons.Rounded.RotateRight, "Pivoter", Modifier.weight(1f)) { vm.rotatePage(doc.id, page) }
                ToolAction(Icons.Rounded.TextFields, "Texte", Modifier.weight(1f)) {
                    vm.recognize("${doc.name} – page ${index + 1}", listOf(doc.pageFile(page)))
                }
                ToolAction(Icons.Rounded.Share, "Image", Modifier.weight(1f)) {
                    vm.shareImage(doc.pageFile(page), doc.name, launch)
                }
                ToolAction(Icons.Rounded.Delete, "Suppr.", Modifier.weight(1f), tint = Danger) { confirmDelete = true }
                ToolAction(Icons.Rounded.ChevronRight, "Après", Modifier.weight(1f)) {
                    if (index < count - 1) {
                        vm.movePage(doc.id, index, index + 1)
                        scope.launch { pager.animateScrollToPage(index + 1) }
                    }
                }
            }
        }
    }

    if (confirmDelete && page != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer la page ${index + 1} ?") },
            confirmButton = {
                TextButton({ confirmDelete = false; vm.deletePage(doc.id, page) }) { Text("Supprimer", color = Danger) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Annuler", color = Dim) } },
            containerColor = Panel2,
        )
    }
}

/** Image zoomable : pincer pour zoomer, double-toucher pour zoomer/dézoomer. */
@Composable
private fun ZoomableImage(file: java.io.File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f; offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // On ne capture le geste que pour zoomer (2 doigts) ou déplacer une image zoomée,
                        // sinon le glissement change de page.
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

// ───────────────────────── Texte (OCR) ─────────────────────────

@Composable
private fun OcrScreen(state: OcrState, vm: ScanViewModel, launch: (Intent) -> Unit) {
    val clipboard = LocalClipboardManager.current
    BackHandler { vm.closeOcr() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundAction(Icons.Rounded.Close, "Fermer", vm::closeOcr, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Texte extrait", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    if (state is OcrState.Done) {
                        Text(state.title, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            when (state) {
                is OcrState.Done -> {
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(
                            state.text,
                            color = Color.White,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                                .card(RoundedCornerShape(18.dp))
                                .padding(16.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AccentButton("Copier", Icons.Rounded.ContentCopy, {
                            clipboard.setText(AnnotatedString(state.text))
                            vm.say("Texte copié")
                        }, Modifier.weight(1f))
                        RoundAction(Icons.Rounded.Share, "Partager le texte", { vm.shareText(state.text, launch) })
                    }
                }

                else -> Column(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Mint)
                    Spacer(Modifier.height(16.dp))
                    Text("Lecture du texte…", color = Dim)
                }
            }
        }
    }
}

// ───────────────────────── Dialogues ─────────────────────────

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer") },
        text = {
            OutlinedTextField(
                name, { name = it }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Mint, cursorColor = Mint),
            )
        },
        confirmButton = {
            TextButton({ onConfirm(name) }, enabled = name.isNotBlank()) { Text("Valider", color = Mint) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Annuler", color = Dim) } },
        containerColor = Panel2,
    )
}

@Composable
private fun DeleteDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Supprimer « $name » ?") },
        text = { Text("Le document et toutes ses pages seront définitivement supprimés.") },
        confirmButton = { TextButton(onConfirm) { Text("Supprimer", color = Danger) } },
        dismissButton = { TextButton(onDismiss) { Text("Annuler", color = Dim) } },
        containerColor = Panel2,
    )
}
