package ca.raymond.livresaudio.ui

import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.raymond.livresaudio.Book
import coil.compose.AsyncImage

/** Material 3 avec couleurs dynamiques (Material You) sur Android 12+. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val placeholderColors = listOf(
    Color(0xFF3F51B5), Color(0xFF00796B), Color(0xFF6D4C41), Color(0xFF5E35B1),
    Color(0xFFC62828), Color(0xFF2E7D32), Color(0xFF00838F), Color(0xFFAD1457),
)

/** Pochette du livre, ou une tuile colorée avec l'initiale si aucune image. */
@Composable
fun Cover(book: Book, modifier: Modifier = Modifier, corner: Dp = 16.dp, letterSize: TextUnit = 48.sp) {
    val bg = placeholderColors[(book.title.hashCode() and 0x7fffffff) % placeholderColors.size]
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = book.title.trim().take(1).uppercase(),
            color = Color.White,
            fontSize = letterSize,
            fontWeight = FontWeight.Bold,
        )
        if (book.coverUri != null) {
            AsyncImage(
                model = Uri.parse(book.coverUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
