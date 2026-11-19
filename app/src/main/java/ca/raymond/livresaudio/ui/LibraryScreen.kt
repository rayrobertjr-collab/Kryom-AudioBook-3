package ca.raymond.livresaudio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.raymond.livresaudio.Book
import ca.raymond.livresaudio.BookStatus
import ca.raymond.livresaudio.status
import ca.raymond.livresaudio.MainViewModel
import ca.raymond.livresaudio.PlayState
import ca.raymond.livresaudio.formatRemaining

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: MainViewModel, onOpenPlayer: () -> Unit) {
    val books by vm.books.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val st by vm.state.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val current = books.firstOrNull { it.id == st.bookId }
    var filter by rememberSaveable { mutableStateOf<BookStatus?>(null) }

    var pendingFolder by remember { mutableStateOf<Uri?>(null) }
    var showFolders by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) pendingFolder = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Livres audio", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { pickFolder.launch(null) }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Ajouter un dossier")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Actualiser") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                onClick = { menuOpen = false; vm.rescan() },
                            )
                            DropdownMenuItem(
                                text = { Text("Gérer les dossiers") },
                                leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                                onClick = { menuOpen = false; showFolders = true },
                            )
                            if (hidden.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Réafficher les livres supprimés (${hidden.size})") },
                                    leadingIcon = { Icon(Icons.Rounded.Restore, null) },
                                    onClick = { menuOpen = false; vm.unhideAll() },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (current != null) MiniPlayer(current, st, onOpen = onOpenPlayer, onToggle = vm::togglePlay)
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (books.isEmpty()) {
                EmptyState(
                    hasFolders = folders.isNotEmpty(),
                    scanning = scanning,
                    onAdd = { pickFolder.launch(null) },
                )
            } else {
                val byStatus = books.groupBy { it.status() }
                val inProgress = byStatus[BookStatus.CURRENT].orEmpty().sortedByDescending { it.lastPlayed }
                val notStarted = byStatus[BookStatus.NOT_STARTED].orEmpty()
                val finished = byStatus[BookStatus.FINISHED].orEmpty().sortedByDescending { it.lastPlayed }

                // Filtre : Tous / En cours / Pas commencés / Terminés
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tous") })
                    FilterChip(selected = filter == BookStatus.CURRENT, onClick = { filter = BookStatus.CURRENT }, label = { Text("En cours") })
                    FilterChip(selected = filter == BookStatus.NOT_STARTED, onClick = { filter = BookStatus.NOT_STARTED }, label = { Text("À lire") })
                    FilterChip(selected = filter == BookStatus.FINISHED, onClick = { filter = BookStatus.FINISHED }, label = { Text("Terminés") })
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val tile: @Composable (Book) -> Unit = { book ->
                        BookTile(
                            book = book,
                            isCurrent = book.id == st.bookId,
                            onClick = {
                                vm.load(book.id, autoplay = false)
                                onOpenPlayer()
                            },
                            onMenu = { vm.openActions(book.id) },
                        )
                    }
                    if (filter == null || filter == BookStatus.CURRENT) section("En cours", inProgress, tile)
                    if (filter == null || filter == BookStatus.NOT_STARTED) section("Pas commencés", notStarted, tile)
                    if (filter == null || filter == BookStatus.FINISHED) section("Terminés", finished, tile)
                }
            }
        }
    }

    BookActionsHost(vm)

    // Choix du type de dossier (comme dans Voice)
    pendingFolder?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingFolder = null },
            title = { Text("Ce dossier contient…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FolderChoice(
                        icon = { Icon(Icons.Rounded.Folder, null, Modifier.size(32.dp)) },
                        title = "Plusieurs livres",
                        subtitle = "Chaque sous-dossier (ou fichier M4B/MP3 seul) est un livre",
                        onClick = { vm.addFolder(uri, singleBook = false); pendingFolder = null },
                    )
                    FolderChoice(
                        icon = { Icon(Icons.Rounded.Book, null, Modifier.size(32.dp)) },
                        title = "Un seul livre",
                        subtitle = "Tous les fichiers du dossier sont les chapitres d'un même livre",
                        onClick = { vm.addFolder(uri, singleBook = true); pendingFolder = null },
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingFolder = null }) { Text("Annuler") } },
        )
    }

    if (showFolders) {
        AlertDialog(
            onDismissRequest = { showFolders = false },
            title = { Text("Dossiers") },
            text = {
                if (folders.isEmpty()) {
                    Text("Aucun dossier pour l'instant.")
                } else {
                    Column {
                        folders.forEach { f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (f.singleBook) Icons.Rounded.Book else Icons.Rounded.Folder,
                                    null,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    folderLabel(f.uri),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(onClick = { vm.removeFolder(f.uri) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Retirer")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFolders = false }) { Text("Fermer") } },
            dismissButton = {
                TextButton(onClick = { showFolders = false; pickFolder.launch(null) }) { Text("Ajouter") }
            },
        )
    }
}

private fun folderLabel(uri: String): String {
    val seg = Uri.parse(uri).lastPathSegment ?: uri
    return seg.substringAfter(':').ifBlank { seg }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderChoice(icon: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyState(hasFolders: Boolean, scanning: Boolean, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Headphones, null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            when {
                scanning -> "Recherche des livres…"
                hasFolders -> "Aucun livre audio trouvé dans tes dossiers"
                else -> "Ta bibliothèque est vide"
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Choisis le dossier où sont tes livres audio (MP3, M4B, M4A, OGG, FLAC…).",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
            Icon(Icons.Rounded.CreateNewFolder, null)
            Spacer(Modifier.width(8.dp))
            Text("Choisir un dossier", fontSize = 18.sp)
        }
    }
}

private fun LazyGridScope.section(title: String, list: List<Book>, tile: @Composable (Book) -> Unit) {
    if (list.isEmpty()) return
    item(key = "header_$title", span = { GridItemSpan(maxLineSpan) }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                "${list.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(list, key = { it.id }) { book -> tile(book) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookTile(
    book: Book,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    Column(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onMenu)
    ) {
        Box {
            Cover(book, Modifier.fillMaxWidth().aspectRatio(1f), corner = 20.dp)
            if (isCurrent) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq, contentDescription = "En cours",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(6.dp).size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { book.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 6.dp)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = buildString {
                    book.author?.let { append(it); append(" · ") }
                    val left = book.totalMs - book.playedMs
                    append(if (book.status() == BookStatus.FINISHED) "Terminé" else "${formatRemaining(left)} restantes")
                }
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onMenu, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options du livre")
            }
        }
    }
}

@Composable
private fun MiniPlayer(book: Book, st: PlayState, onOpen: () -> Unit, onToggle: () -> Unit) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            val chapterDur = st.durationMs.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { (st.positionMs.toFloat() / chapterDur).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
            Row(
                Modifier
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(book, Modifier.size(56.dp), corner = 12.dp, letterSize = 22.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        book.chapters.getOrNull(st.chapterIndex)?.name ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(56.dp)) {
                    Icon(
                        if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (st.isPlaying) "Pause" else "Lecture",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}
