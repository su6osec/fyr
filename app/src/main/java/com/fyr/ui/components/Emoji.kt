package com.fyr.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import java.text.BreakIterator

/**
 * Renders an emoji as *art*, not as text.
 *
 * The Android system font paints emoji from Noto — the look the brief
 * explicitly rejects. So Fyr ships a bundled set of Fluent Emoji 3D, which is
 * glossier and considerably closer to the iOS look, and paints that instead.
 *
 * Lookup order:
 *   1. `assets/emoji/<codepoints>.png`  — bundled art
 *   2. the character itself, via the system font — so a missing or newly-added
 *      habit still renders something rather than leaving a hole
 *
 * The folder is enumerated once with `AssetManager.list` rather than indexed by
 * hand, so dropping a file in and rebuilding is enough to make it appear.
 */
@Composable
fun Emoji(
    emoji: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val key = remember(emoji) { codepointKey(emoji) }
    val bitmap = remember(context, key) { loadEmoji(context, key) }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            Text(
                text = emoji,
                // Scaled to the number of graphemes it holds: the box is
                // sized for one shape, so a string holding two — two emoji
                // written side by side — would otherwise spill into the
                // cells either side. Each cluster gets an equal share.
                fontSize = (size.value * 0.78f / graphemeCount(emoji)).sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * How many user-perceived characters [text] holds — never fewer than one.
 *
 * `BreakIterator` rather than `codePointCount`: a family emoji is seven
 * code points and one shape, and counting it as seven would shrink it to a
 * dot. Variation selectors and joiners are part of the cluster it walks, so
 * `🏋️` is one shape — and two emoji written side by side are two.
 */
private fun graphemeCount(text: String): Int {
    val boundaries = BreakIterator.getCharacterInstance()
    boundaries.setText(text)
    var count = 0
    while (boundaries.next() != BreakIterator.DONE) count++
    return count.coerceAtLeast(1)
}

/** True when bundled art exists for this emoji. Used to pick the empty state. */
fun hasEmojiArt(context: Context, emoji: String): Boolean =
    loadEmoji(context, codepointKey(emoji)) != null

/**
 * `🔥` → `1f525`, `🏋️` → `1f3cb`.
 *
 * Variation selector-16 (U+FE0F) is dropped so the same habit written with or
 * without it resolves to one asset.
 */
fun codepointKey(emoji: String): String {
    val sb = StringBuilder()
    emoji.codePoints()
        .filter { it != 0xFE0F }
        .forEach { sb.append(Integer.toHexString(it)) }
    return sb.toString().lowercase()
}

private var keyIndex: Set<String>? = null

private fun knownKeys(context: Context): Set<String> {
    keyIndex?.let { return it }
    val names = runCatching { context.assets.list(DIR) }.getOrNull().orEmpty()
    val index = names.filter { it.endsWith(".png") }
        .mapTo(HashSet(names.size)) { it.removeSuffix(".png") }
    keyIndex = index
    return index
}

private val bitmapCache = HashMap<String, ImageBitmap?>()

private fun loadEmoji(context: Context, key: String): ImageBitmap? {
    if (bitmapCache.containsKey(key)) return bitmapCache[key]
    if (!knownKeys(context).contains(key)) {
        bitmapCache[key] = null
        return null
    }
    val loaded = runCatching {
        context.assets.open("$DIR/$key.png").use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    }.getOrNull()
    bitmapCache[key] = loaded
    return loaded
}

private const val DIR = "emoji"
