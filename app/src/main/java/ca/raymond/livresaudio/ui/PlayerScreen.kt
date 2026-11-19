package ca.raymond.livresaudio.ui

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.raymond.livresaudio.Book
import ca.raymond.livresaudio.Bookmark
import ca.raymond.livresaudio.MainViewModel
import ca.raymond.livresaudio.formatRemaining
import ca.raymond.livresaudio.formatTime
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun PlayerScreen(vm: MainViewModel, onBack: () -> Unit) {
    val books by vm.books.collectAsStateWithLifecycle()
    val st by vm.state.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == st.bookId }

    // Couleurs tirées de la pochette, appliquées à tout l'écran et à ses menus
    val colors = rememberBookColors(book)
    val anim = tween<Color>(durationMillis = 700)
    val top by animateColorAsState(colors.top, anim, label = "top")
    val middle by animateColorAsState(colors.middle, anim, label = "middle")
    val bottom by animateColorAsState(colors.scheme.background, anim, label = "bottom")

    MaterialTheme(colorScheme = colors.scheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(top, middle, bottom)))
        ) {
            PlayerContent(vm, book, onBack)
        }
        BookActionsHost(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerContent(vm: MainViewModel, book: Book?, onBack: () -> Unit) {
    val st by vm.state.collectAsStateWithLifecycle()
    val sleepLeft by vm.sleepRemaining.collectAsStateWithLifecycle()
    val skipSilence by vm.skipSilence.collectAsStateWithLifecycle()
    val boostDb by vm.boostDb.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    var showChapters by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showMarks by remember { mutableStateOf(false) }
    var showBoost by remember { mutableStateOf(false) }
    var dragPos by remember { mutableStateOf<Float?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
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
                    if (book != null) {
                        IconButton(onClick = { vm.openActions(book.id) }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Options du livre")
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
            Spacer(Modifier.weight(0.3f))
            Cover(
                book,
                Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .shadow(24.dp, RoundedCornerShape(28.dp)),
                corner = 28.dp,
                letterSize = 120.sp,
            )
            Spacer(Modifier.weight(0.3f))

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

            Spacer(Modifier.height(16.dp))

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
                Text(
                    "${formatRemaining(bookLeft)} restantes",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text("-" + formatTime(dur - shownPos), style = MaterialTheme.typography.labelLarge)
            }

            Spacer(Modifier.height(12.dp))

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

            // Barre « pilule » en surbrillance, couleur du livre
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton(
                        icon = Icons.Rounded.Bedtime,
                        label = if (sleepLeft > 0) formatTime(sleepLeft) else null,
                        active = sleepLeft > 0,
                        description = "Minuterie de sommeil",
                    ) { showSleep = true }
                    PillButton(
                        text = speedLabel(st.speed),
                        active = st.speed != 1f,
                        description = "Vitesse de lecture",
                    ) { showSpeed = true }
                    PillButton(
                        icon = Icons.Rounded.Bookmarks,
                        description = "Marque-pages et historique",
                    ) { showMarks = true }
                    PillButton(
                        icon = Icons.Rounded.ContentCut,
                        active = skipSilence,
                        description = "Sauter les silences",
                    ) {
                        vm.toggleSkipSilence()
                        Toast.makeText(
                            ctx,
                            if (!skipSilence) "Saut des silences activé" else "Saut des silences désactivé",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    PillButton(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        label = if (boostDb > 0f) "+%.1f".format(boostDb) else null,
                        active = boostDb > 0f,
                        description = "Amplification du volume",
                    ) { showBoost = true }
                }
            }
            Spacer(Modifier.weight(0.2f))
        }
    }

    if (showChapters && book != null) {
        ChapterSheet(book, st.chapterIndex, onPick = { vm.goToChapter(it); showChapters = false }) {
            showChapters = false
        }
    }
    if (showSpeed) SpeedDialog(st.speed, vm::setSpeed) { showSpeed = false }
    if (showSleep) SleepDialog(sleepLeft, onPick = { vm.setSleep(it); showSleep = false }) { showSleep = false }
    if (showMarks && book != null) MarksSheet(vm, book, st.chapterIndex, st.positionMs) { showMarks = false }
    if (showBoost) BoostDialog(boostDb, vm::setBoost) { showBoost = false }
}

@Composable
private fun RowScope.PillButton(
    icon: ImageVector? = null,
    text: String? = null,
    label: String? = null,
    active: Boolean = false,
    description: String,
    onClick: () -> Unit,
) {
    val bg = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        Modifier
            .weight(1f)
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) Icon(icon, contentDescription = description, tint = fg, modifier = Modifier.size(26.dp))
            if (text != null) Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (label != null) Text(label, color = fg, fontSize = 11.sp, maxLines = 1)
        }
    }
}

// ---------------- Chapitres ----------------

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

// ---------------- Marque-pages + historique ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarksSheet(vm: MainViewModel, book: Book, chapterIndex: Int, positionMs: Long, onDismiss: () -> Unit) {
    val allMarks by vm.marks.collectAsStateWithLifecycle()
    val marks = allMarks.filter { it.bookId == book.id }
    val manual = marks.filter { !it.auto }.sortedWith(compareBy({ it.chapterIndex }, { it.positionMs }))
    val history = marks.filter { it.auto }.sortedByDescending { it.time }
    var tab by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Marque-pages (${manual.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Historique") })
        }
        if (tab == 0) {
            FilledTonalButton(
                onClick = { adding = true },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Icon(Icons.Rounded.BookmarkBorder, null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter un marque-page ici")
            }
        }
        val list = if (tab == 0) manual else history
        if (list.isEmpty()) {
            Text(
                if (tab == 0) "Aucun marque-page pour ce livre." else "L'historique se remplit tout seul : à chaque pause et avant chaque saut.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                items(list, key = { it.id }) { m ->
                    MarkRow(book, m, onClick = { vm.jumpTo(m); onDismiss() }, onDelete = { vm.deleteMark(m.id) })
                }
            }
        }
    }

    if (adding) {
        val defaultTitle = "${book.chapters.getOrNull(chapterIndex)?.name ?: ""} · ${formatTime(positionMs)}"
        var title by remember { mutableStateOf(defaultTitle) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Nouveau marque-page") },
            text = {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Nom") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.addBookmark(title.ifBlank { defaultTitle }); adding = false }) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun MarkRow(book: Book, m: Bookmark, onClick: () -> Unit, onDelete: () -> Unit) {
    val date = remember(m.time) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(m.time))
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Icon(if (m.auto) Icons.Rounded.History else Icons.Rounded.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(m.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val ch = if (book.chapters.size > 1) "Ch. ${m.chapterIndex + 1} · " else ""
            Text("$ch${formatTime(m.positionMs)}  —  $date")
        },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Effacer") }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

// ---------------- Amplification du volume ----------------

@Composable
private fun BoostDialog(db: Float, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(db) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null) },
        title = { Text("Amplification du volume") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BoostMeter(value, Modifier.fillMaxWidth().height(110.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    if (value <= 0f) "Désactivée" else "+%.1f dB".format(value),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = value,
                    onValueChange = {
                        value = (it * 2).roundToInt() / 2f // pas de 0,5 dB
                        onChange(value)
                    },
                    valueRange = 0f..12f,
                    steps = 23,
                )
                Text(
                    "Utile pour les livres enregistrés trop bas. Un réglage trop fort peut saturer le son.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** Graphique : des barres qui grandissent et s'allument avec le volume. */
@Composable
private fun BoostMeter(db: Float, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(db / 12f, label = "boost")
    val low = MaterialTheme.colorScheme.primary
    val high = MaterialTheme.colorScheme.error
    val off = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val n = 24
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val f = (i + 1) / n.toFloat()
            val h = size.height * (0.15f + 0.85f * f)
            val lit = level > 0f && f <= level + 0.001f
            val color = if (lit) lerp(low, high, ((f - 0.6f) / 0.4f).coerceIn(0f, 1f)) else off
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}

// ---------------- Vitesse et minuterie ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedDialog(speed: Float, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(speed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Speed, null) },
        title = { Text("Vitesse de lecture") },
        text = {
            Column {
                Text(
                    speedLabel(value),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
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
        icon = { Icon(Icons.Rounded.Bedtime, null) },
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
