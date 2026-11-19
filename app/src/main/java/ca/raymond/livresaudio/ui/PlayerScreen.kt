package ca.raymond.livresaudio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.raymond.livresaudio.Book
import ca.raymond.livresaudio.MainViewModel
import ca.raymond.livresaudio.formatRemaining
import ca.raymond.livresaudio.formatTime
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val books by vm.books.collectAsStateWithLifecycle()
    val st by vm.state.collectAsStateWithLifecycle()
    val sleepLeft by vm.sleepRemaining.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == st.bookId }

    var showChapters by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }

    // Position « en cours de glissement » sur la barre
    var dragPos by remember { mutableStateOf<Float?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("En lecture") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Retour", Modifier.size(32.dp))
                    }
                },
                actions = {
                    if (book != null && book.chapters.size > 1) {
                        IconButton(onClick = { showChapters = true }) {
                            Icon(Icons.AutoMirrored.Rounded.List, contentDescription = "Chapitres")
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (book == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aucun livre sélectionné")
            }
            return@Scaffold
        }

        val chapter = book.chapters.getOrNull(st.chapterIndex)
        val dur = st.durationMs.coerceAtLeast(1)
        val shownPos = dragPos?.toLong() ?: st.positionMs
        val bookPlayed = book.chapters.take(st.chapterIndex).sumOf { it.durationMs } + shownPos
        val bookLeft = ((book.totalMs - bookPlayed) / st.speed.coerceAtLeast(0.1f)).toLong()

        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.4f))
            Cover(
                book,
                Modifier
                    .fillMaxWidth(0.88f)
                    .aspectRatio(1f)
                    .shadow(16.dp, RoundedCornerShape(28.dp)),
                corner = 28.dp,
                letterSize = 120.sp,
            )
            Spacer(Modifier.weight(0.4f))

            Text(
                book.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    book.author,
                    chapter?.name?.takeIf { book.chapters.size > 1 }
                        ?.let { "${st.chapterIndex + 1}/${book.chapters.size} · $it" },
                ).joinToString("  •  "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(20.dp))

            Slider(
                value = shownPos.toFloat().coerceIn(0f, dur.toFloat()),
                onValueChange = { dragPos = it },
                onValueChangeFinished = {
                    dragPos?.let { vm.seekTo(it.toLong()) }
                    dragPos = null
                },
                valueRange = 0f..dur.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(shownPos), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("-" + formatTime(dur - shownPos), style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(16.dp))

            // Commandes principales — gros boutons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::previousChapter, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Chapitre précédent", Modifier.size(36.dp))
                }
                IconButton(onClick = { vm.skip(-10_000) }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.Replay10, "Reculer 10 s", Modifier.size(44.dp))
                }
                FilledIconButton(
                    onClick = vm::togglePlay,
                    modifier = Modifier.size(96.dp),
                    shape = RoundedCornerShape(32.dp),
                ) {
                    Icon(
                        if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (st.isPlaying) "Pause" else "Lecture",
                        modifier = Modifier.size(56.dp),
                    )
                }
                IconButton(onClick = { vm.skip(30_000) }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.Forward30, "Avancer 30 s", Modifier.size(44.dp))
                }
                IconButton(onClick = vm::nextChapter, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Chapitre suivant", Modifier.size(36.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Vitesse + minuterie + temps restant
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = { showSpeed = true }) {
                    Icon(Icons.Rounded.Speed, null)
                    Spacer(Modifier.width(6.dp))
                    Text(speedLabel(st.speed))
                }
                Text(
                    "${formatRemaining(bookLeft)} restantes",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = { showSleep = true }) {
                    Icon(Icons.Rounded.Bedtime, null)
                    if (sleepLeft > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(formatTime(sleepLeft))
                    }
                }
            }
            Spacer(Modifier.weight(0.3f))
        }
    }

    if (showChapters && book != null) {
        ChapterSheet(book, st.chapterIndex, onPick = { vm.goToChapter(it); showChapters = false }) {
            showChapters = false
        }
    }
    if (showSpeed) SpeedDialog(st.speed, vm::setSpeed) { showSpeed = false }
    if (showSleep) SleepDialog(sleepLeft, onPick = { vm.setSleep(it); showSleep = false }) { showSleep = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterSheet(book: Book, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Chapitres",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(book.chapters) { i, ch ->
                val selected = i == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(i) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${i + 1}",
                        modifier = Modifier.width(40.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        ch.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatTime(ch.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedDialog(speed: Float, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(speed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vitesse de lecture") },
        text = {
            Column {
                Text(
                    speedLabel(value),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Slider(
                    value = value,
                    onValueChange = {
                        value = (it * 20).roundToInt() / 20f // pas de 0,05
                        onChange(value)
                    },
                    valueRange = 0.5f..3f,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(0.8f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                        FilterChip(
                            selected = value == s,
                            onClick = { value = s; onChange(s) },
                            label = { Text(if (s == 1f) "1x" else "${s}x") },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun SleepDialog(remaining: Long, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Minuterie de sommeil") },
        text = {
            Column {
                if (remaining > 0) {
                    Text(
                        "Pause dans ${formatTime(remaining)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                listOf(5, 10, 15, 20, 30, 45, 60, 90).chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m ->
                            OutlinedButton(
                                onClick = { onPick(m) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(4.dp),
                            ) { Text("$m min") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        dismissButton = {
            if (remaining > 0) TextButton(onClick = { onPick(0) }) { Text("Désactiver") }
        },
    )
}

private fun speedLabel(s: Float): String {
    val r = (s * 100).roundToInt()
    return (if (r % 10 == 0) "%.1f" else "%.2f").format(r / 100f) + "x"
}
