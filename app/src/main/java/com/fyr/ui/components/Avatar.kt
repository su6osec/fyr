package com.fyr.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The picture a profile shows.
 *
 * Three states: a photograph the user chose, their initial, and — only when
 * they have given no name either — the person mark Fyr draws itself. There is
 * no gallery of built-in faces to pick from, because a set of those would be a
 * decision this app was making on the user's behalf.
 *
 * The initial is the one that earns its place. A grey silhouette on a grey
 * disc is neutral and correct and was read as broken anyway, because a
 * featureless placeholder in the one slot that is meant to be personal is
 * indistinguishable from an image that failed to load. One letter in the app's
 * own ember on a warm ground cannot be mistaken for anything but an avatar,
 * and it uses the name Fyr already asked for instead of inventing a second
 * thing to store.
 *
 * The photograph lives in the app's private files directory (see
 * [com.fyr.data.Store.stageAvatar]), so reading it needs no permission and
 * survives the picker's grant expiring.
 */
@Composable
fun Avatar(
    photo: String,
    size: Dp,
    modifier: Modifier = Modifier,
    name: String = "",
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val file = remember(photo) { if (photo.isEmpty()) null else File(context.filesDir, photo) }

    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        thumb = withContext(Dispatchers.IO) {
            file?.takeIf { it.exists() && it.length() > 0L }?.let(::decodeThumb)
        }
    }

    // One letter, never two. The name already stops at twelve characters, and
    // an avatar is not a name tag: a single mark reads from across a room,
    // four read as a label somebody forgot to format.
    val initial = remember(name) { name.trim().firstOrNull()?.uppercase() ?: "" }

    // Chosen from [photo] rather than from the loaded bitmap, so the ground
    // never sits warm under a picture that is still decoding.
    val ground =
        if (photo.isEmpty() && initial.isNotEmpty()) scheme.primary.copy(alpha = 0.16f)
        else scheme.surfaceContainerHighest

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ground),
        contentAlignment = Alignment.Center,
    ) {
        when {
            thumb != null -> Image(
                bitmap = thumb!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                // Crop, not fit: a photograph is rarely square, and letterboxing
                // a face into a circle with bars of ground colour looks like a
                // file that failed to load.
                contentScale = ContentScale.Crop,
            )

            initial.isNotEmpty() -> Text(
                text = initial,
                fontSize = with(LocalDensity.current) { (size * 0.44f).toSp() },
                fontWeight = FontWeight.SemiBold,
                // On the coin rather than on the page: Ember against a 16%-
                // alpha Ember ground is 4.3:1 in the dark theme and 2.9:1 in
                // the light one, and this letter is doing the job a photograph
                // would do — it has to be read at a glance, at any size the
                // coin is drawn. EmberSoft clears 6.5:1 on the dark coin; the
                // light theme's accent is EmberDim, which clears 4.5:1.
                color = if (isDarkTheme()) scheme.secondary else scheme.primary,
            )

            else -> DefaultMark(size, scheme.onSurfaceVariant)
        }
    }
}

/**
 * The last resort: no photograph, and no name to hang a letter on.
 *
 * Flooded rather than stroked, at 118% of the circle. The outline version was
 * right beside a word in a nav bar and wrong here — at this size a wireframe
 * person reads as a loading state. The shoulders run past the grid so that the
 * parent's [CircleShape] cuts them at the rim, and the mark fills the coin
 * instead of floating in it.
 */
@Composable
private fun DefaultMark(size: Dp, color: androidx.compose.ui.graphics.Color) {
    val reach = size * 1.18f
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlyphIcon(
            glyph = Glyph.PERSON_FILLED,
            color = color,
            size = reach,
            modifier = Modifier.requiredSize(reach),
        )
    }
}

/**
 * A copy of the stored picture small enough to hold in memory, or null if the
 * file is gone. Sampled before it is decoded for the same reason the write
 * sampled: a 46dp circle does not need a megabyte of bitmap behind it.
 */
private fun decodeThumb(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > 384 || bounds.outHeight / sample > 384) sample *= 2

    val bitmap = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null

    return bitmap.asImageBitmap()
}
