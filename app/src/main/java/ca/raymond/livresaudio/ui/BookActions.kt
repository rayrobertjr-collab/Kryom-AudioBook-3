package ca.raymond.livresaudio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.raymond.livresaudio.MainViewModel
import coil.compose.AsyncImage

/**
 * Menu d'un livre (comme dans Voice) : modifier les infos, couverture Internet,
 * couverture depuis les fichiers, recommencer, terminé, supprimer.
 * Utilisé dans la bibliothèque et dans le lecteur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookActionsHost(vm: MainViewModel) {
    val books by vm.books.collectAsStateWithLifecycle()
    val actionsFor by vm.actionsFor.collectAsStateWithLifecycle()
    val editFor by vm.editFor.collectAsStateWithLifecycle()
    val deleteFor by vm.deleteFor.collectAsStateWithLifecycle()
    val coverFor by vm.coverSearchFor.collectAsStateWithLifecycle()

    var fileTarget by remember { mutableStateOf<String?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = fileTarget
        if (uri != null && id != null) vm.setCoverFromFile(id, uri)
        fileTarget = null
    }

    // ---- Feuille d'actions ----
    actionsFor?.let { id ->
        val book = books.firstOrNull { it.id == id }
        if (book == null) {
            vm.closeActions()
        } else {
            ModalBottomSheet(onDismissRequest = vm::closeActions) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(book, Modifier.size(64.dp), corner = 12.dp, letterSize = 26.sp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        book.author?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ActionItem(Icons.Rounded.Edit, "Modifier le titre et l'auteur") { vm.startEdit(id) }
                ActionItem(Icons.Rounded.Public, "Couverture depuis Internet") { vm.startCoverSearch(id) }
                ActionItem(Icons.Rounded.Image, "Couverture depuis les fichiers") {
                    vm.closeActions()
                    fileTarget = id
                    pickImage.launch(arrayOf("image/*"))
                }
                ActionItem(Icons.Rounded.Replay, "Recommencer au début") { vm.closeActions(); vm.resetBook(id) }
                ActionItem(Icons.Rounded.DoneAll, "Marquer comme terminé") { vm.closeActions(); vm.markFinished(id) }
                ActionItem(Icons.Rounded.Delete, "Supprimer le livre", danger = true) { vm.askDelete(id) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ---- Modifier titre / auteur ----
    editFor?.let { id ->
        val book = books.firstOrNull { it.id == id } ?: return@let
        var title by remember(id) { mutableStateOf(book.title) }
        var author by remember(id) { mutableStateOf(book.author ?: "") }
        AlertDialog(
            onDismissRequest = vm::closeEdit,
            title = { Text("Modifier les informations") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("Titre") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = author, onValueChange = { author = it },
                        label = { Text("Auteur") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.saveInfo(id, title, author) }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = vm::closeEdit) { Text("Annuler") } },
        )
    }

    // ---- Supprimer ----
    deleteFor?.let { id ->
        val book = books.firstOrNull { it.id == id } ?: return@let
        AlertDialog(
            onDismissRequest = vm::closeDelete,
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Supprimer ce livre ?") },
            text = {
                Text("« ${book.title} » sera retiré de la bibliothèque, avec ses marque-pages. Les fichiers audio restent sur ton téléphone.")
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteBook(id) }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::closeDelete) { Text("Annuler") } },
        )
    }

    // ---- Couverture depuis Internet ----
    coverFor?.let { id ->
        val book = books.firstOrNull { it.id == id } ?: return@let
        CoverSearchDialog(vm, id, listOfNotNull(book.title, book.author).joinToString(" "))
    }
}

@Composable
private fun ActionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(text, color = color, style = MaterialTheme.typography.titleMedium) },
        leadingContent = { Icon(icon, null, tint = color) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverSearchDialog(vm: MainViewModel, id: String, initialQuery: String) {
    val results by vm.coverResults.collectAsStateWithLifecycle()
    val loading by vm.coverLoading.collectAsStateWithLifecycle()
    var query by remember(id) { mutableStateOf(initialQuery) }

    Dialog(
        onDismissRequest = vm::closeCoverSearch,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                TopAppBar(
                    title = { Text("Couverture depuis Internet") },
                    navigationIcon = {
                        IconButton(onClick = vm::closeCoverSearch) { Icon(Icons.Rounded.Close, "Fermer") }
                    },
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Rechercher (titre, auteur)") },
                    trailingIcon = {
                        IconButton(onClick = { vm.searchCovers(query) }) { Icon(Icons.Rounded.Search, "Rechercher") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.searchCovers(query) }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Text(
                    "Touche une image pour l'utiliser comme couverture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                Box(Modifier.fillMaxSize()) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        results.isEmpty() -> Text(
                            "Aucune image trouvée. Essaie avec d'autres mots (ex. seulement le titre).",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        )
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(140.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(results) { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { vm.pickCover(id, url) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
