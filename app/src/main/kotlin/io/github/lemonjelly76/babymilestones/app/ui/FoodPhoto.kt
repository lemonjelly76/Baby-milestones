package io.github.lemonjelly76.babymilestones.app.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.core.food.Food
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Food photos ship in the app's assets; decoded off the main thread and kept in a small cache. */
private object FoodPhotoCache {
    private val cache = object : android.util.LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(context: Context, file: String, maxPx: Int): Bitmap? = cache.get("$file@$maxPx") ?: withContext(Dispatchers.IO) {
        runCatching { context.assets.open(file).use { it.readBytes() } }.getOrNull()
            ?.let { decodeScaled(it, maxPx) }
            ?.also { cache.put("$file@$maxPx", it) }
    }
}

@Composable
private fun foodBitmap(file: String, maxPx: Int): Bitmap? {
    val context = LocalContext.current
    return produceState<Bitmap?>(null, file, maxPx) { value = FoodPhotoCache.load(context, file, maxPx) }.value
}

/**
 * A small square photo for lists. Foods without one (and the family's own foods) get a plain tile of the same size,
 * so names line up and nothing shifts while a photo loads.
 */
@Composable
fun FoodThumb(food: Food?, size: Dp = 48.dp, modifier: Modifier = Modifier.padding(end = 12.dp)) {
    val file = food?.image?.file
    Box(
        modifier.size(size).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (file == null) {
            Icon(Icons.Outlined.Restaurant, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
        } else {
            foodBitmap(file, 160)?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
    }
}

/**
 * The full-width photo on a food's card, with its credit linking to the source. The photos show the food as you'd
 * buy it, so for foods that need preparing to lower the choking risk the photo says so, rather than look like a way
 * to serve it.
 */
@Composable
fun FoodHeroPhoto(food: Food?) {
    val image = food?.image ?: return
    val uri = LocalUriHandler.current
    Column(Modifier.padding(vertical = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            foodBitmap(image.file, 480)?.let {
                Image(it.asImageBitmap(), food.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (food.chokingRisk) Surface(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                Text("As bought, not as served: see how to prepare it below.", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        Text(image.credit + " (cropped)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp).clickable { uri.openUri(image.source) })
    }
}
