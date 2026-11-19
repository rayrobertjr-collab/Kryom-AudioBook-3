package ca.raymond.livresaudio.ui

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import ca.raymond.livresaudio.Book
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Couleurs d'un livre : thème Material 3 complet + couleurs du dégradé de fond. */
data class BookColors(
    val scheme: ColorScheme,
    val top: Color,
    val middle: Color,
)

/**
 * Extrait les couleurs de la pochette (Palette) et construit un thème complet.
 * Tous les menus, boutons et fenêtres du lecteur prennent la couleur du livre.
 */
@Composable
fun rememberBookColors(book: Book?): BookColors {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme
    val coverUri = book?.coverUri

    val palette by produceState<Palette?>(initialValue = null, coverUri) {
        value = if (coverUri == null) null else loadPalette(ctx, coverUri)
    }

    return remember(palette, dark, book?.title, coverUri) {
        val p = palette
        val fallback = book?.let { placeholderColor(it.title) }
        val seed: Color? = p?.let { pal ->
            val c = pal.getVibrantColor(pal.getDominantColor(0))
            if (c == 0) null else Color(c)
        } ?: fallback

        if (seed == null) {
            BookColors(base, base.background, base.background)
        } else {
            val vibrant = p?.let { pal ->
                val c = if (dark) pal.getDarkVibrantColor(pal.getDominantColor(0)) else pal.getLightVibrantColor(pal.getDominantColor(0))
                if (c == 0) null else Color(c)
            } ?: seed
            val muted = p?.let { pal ->
                val c = if (dark) pal.getDarkMutedColor(pal.getMutedColor(0)) else pal.getLightMutedColor(pal.getMutedColor(0))
                if (c == 0) null else Color(c)
            } ?: seed

            val scheme = schemeFromSeed(seed, dark)
            BookColors(
                scheme = scheme,
                top = if (dark) tone(vibrant, 0.30f, 0.35f, 0.85f) else tone(vibrant, 0.80f, 0.35f, 0.85f),
                middle = if (dark) tone(muted, 0.15f, 0.15f, 0.5f) else tone(muted, 0.90f, 0.15f, 0.5f),
            )
        }
    }
}

private suspend fun loadPalette(ctx: Context, uri: String): Palette? = try {
    val req = ImageRequest.Builder(ctx)
        .data(Uri.parse(uri))
        .allowHardware(false)
        .size(160)
        .build()
    val bmp = (ctx.imageLoader.execute(req).drawable as? BitmapDrawable)?.bitmap
    if (bmp == null) null else withContext(Dispatchers.Default) { Palette.from(bmp).generate() }
} catch (_: Exception) {
    null
}

/** Même teinte que [c], avec une luminosité [l] et une saturation bornée. */
private fun tone(c: Color, l: Float, sMin: Float = 0f, sMax: Float = 1f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c.toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(sMin, sMax)
    hsl[2] = l
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme =
    if (dark) {
        val primary = tone(seed, 0.78f, 0.35f, 0.9f)
        darkColorScheme().copy(
            primary = primary,
            onPrimary = tone(seed, 0.15f, 0.3f, 0.9f),
            primaryContainer = tone(seed, 0.32f, 0.3f, 0.8f),
            onPrimaryContainer = tone(seed, 0.90f, 0.3f, 0.9f),
            secondary = tone(seed, 0.75f, 0.1f, 0.4f),
            onSecondary = tone(seed, 0.15f, 0.0f, 0.4f),
            secondaryContainer = tone(seed, 0.26f, 0.15f, 0.45f),
            onSecondaryContainer = tone(seed, 0.90f, 0.1f, 0.5f),
            tertiary = tone(seed, 0.80f, 0.2f, 0.6f),
            background = tone(seed, 0.07f, 0.1f, 0.35f),
            onBackground = tone(seed, 0.92f, 0.0f, 0.15f),
            surface = tone(seed, 0.07f, 0.1f, 0.35f),
            onSurface = tone(seed, 0.92f, 0.0f, 0.15f),
            surfaceVariant = tone(seed, 0.20f, 0.1f, 0.3f),
            onSurfaceVariant = tone(seed, 0.80f, 0.05f, 0.25f),
            surfaceTint = primary,
            outline = tone(seed, 0.55f, 0.1f, 0.2f),
            surfaceContainerLowest = tone(seed, 0.05f, 0.1f, 0.3f),
            surfaceContainerLow = tone(seed, 0.10f, 0.1f, 0.3f),
            surfaceContainer = tone(seed, 0.12f, 0.1f, 0.3f),
            surfaceContainerHigh = tone(seed, 0.16f, 0.1f, 0.3f),
            surfaceContainerHighest = tone(seed, 0.20f, 0.1f, 0.3f),
        )
    } else {
        val primary = tone(seed, 0.38f, 0.35f, 0.9f)
        lightColorScheme().copy(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = tone(seed, 0.88f, 0.3f, 0.8f),
            onPrimaryContainer = tone(seed, 0.12f, 0.3f, 0.9f),
            secondary = tone(seed, 0.40f, 0.1f, 0.4f),
            onSecondary = Color.White,
            secondaryContainer = tone(seed, 0.86f, 0.2f, 0.5f),
            onSecondaryContainer = tone(seed, 0.12f, 0.1f, 0.5f),
            tertiary = tone(seed, 0.40f, 0.2f, 0.6f),
            background = tone(seed, 0.97f, 0.2f, 0.5f),
            onBackground = tone(seed, 0.10f, 0.0f, 0.2f),
            surface = tone(seed, 0.97f, 0.2f, 0.5f),
            onSurface = tone(seed, 0.10f, 0.0f, 0.2f),
            surfaceVariant = tone(seed, 0.90f, 0.1f, 0.3f),
            onSurfaceVariant = tone(seed, 0.30f, 0.05f, 0.25f),
            surfaceTint = primary,
            outline = tone(seed, 0.50f, 0.1f, 0.2f),
            surfaceContainerLowest = tone(seed, 0.99f, 0.2f, 0.5f),
            surfaceContainerLow = tone(seed, 0.95f, 0.2f, 0.5f),
            surfaceContainer = tone(seed, 0.93f, 0.2f, 0.5f),
            surfaceContainerHigh = tone(seed, 0.91f, 0.2f, 0.5f),
            surfaceContainerHighest = tone(seed, 0.89f, 0.2f, 0.5f),
        )
    }
