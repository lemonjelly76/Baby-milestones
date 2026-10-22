package io.github.lemonjelly76.babymilestones.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.lemonjelly76.babymilestones.core.content.ContentBundle
import io.github.lemonjelly76.babymilestones.core.content.Domain
import io.github.lemonjelly76.babymilestones.core.content.Urgency
import io.github.lemonjelly76.babymilestones.core.tracking.MilestoneStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
val DAY_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

fun LocalDate.pretty(): String = format(DAY)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun urgencyColor(urgency: Urgency): Color = when (urgency) {
    Urgency.URGENT -> MaterialTheme.colorScheme.errorContainer
    Urgency.PROMPT -> MaterialTheme.colorScheme.secondaryContainer
    Urgency.ROUTINE -> MaterialTheme.colorScheme.tertiaryContainer
    Urgency.INFO -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
fun StatusPill(status: MilestoneStatus) {
    val (bg, label) = when (status) {
        MilestoneStatus.ACHIEVED -> MaterialTheme.colorScheme.primaryContainer to "Done"
        MilestoneStatus.DUE_NOW -> MaterialTheme.colorScheme.tertiaryContainer to status.label
        MilestoneStatus.COMING_UP -> MaterialTheme.colorScheme.surfaceVariant to status.label
        MilestoneStatus.NOT_SEEN_YET -> MaterialTheme.colorScheme.secondaryContainer to "Not seen yet"
        MilestoneStatus.NOT_SURE -> MaterialTheme.colorScheme.secondaryContainer to status.label
        MilestoneStatus.SKILL_LOST -> MaterialTheme.colorScheme.errorContainer to "Lost skill"
        MilestoneStatus.LATER -> MaterialTheme.colorScheme.surfaceVariant to status.label
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

fun Domain.short(): String = when (this) {
    Domain.SOCIAL -> "Social"
    Domain.LANGUAGE -> "Language"
    Domain.COGNITIVE -> "Thinking"
    Domain.MOVEMENT -> "Movement"
}

/** The footer every info screen carries. */
@Composable
fun Disclaimer(content: ContentBundle) {
    val uri = LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 16.dp).clickable { uri.openUri(content.safety.disclaimerLink) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(content.safety.disclaimer, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, "NHS guidance", Modifier.size(16.dp).padding(start = 4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Where content comes from: each source opens its page. */
@Composable
fun Sources(content: ContentBundle, ids: List<String>) {
    val uri = LocalUriHandler.current
    val refs = ids.mapNotNull { content.reference(it) }
    if (refs.isEmpty()) return
    Text("Sources", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    refs.forEach { ref ->
        Text(
            "${ref.title} — ${ref.publisher}, ${ref.year}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 2.dp).clickable { uri.openUri(ref.url) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, modifier: Modifier = Modifier, max: LocalDate? = LocalDate.now()) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value?.pretty() ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.Event, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    max == null || !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(max)
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() })
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Photos are private: they're fetched with the carer's token and decoded here rather than by an image loader. */
@Composable
fun PhotoImage(photoRef: String, load: suspend (String) -> ByteArray?, size: Dp = 160.dp) {
    var bitmap by remember(photoRef) { mutableStateOf<android.graphics.Bitmap?>(PhotoCache[photoRef]) }
    LaunchedEffect(photoRef) {
        if (bitmap == null) {
            load(photoRef)?.let { bytes -> decodeScaled(bytes, PHOTO_MAX_PX)?.also { PhotoCache[photoRef] = it; bitmap = it } }
        }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)))
    }
}

/** Large enough for the screen and the memory book; a camera photo decoded at full size can use 50 MB. */
const val PHOTO_MAX_PX = 1280

/** Decodes an image no bigger than [maxPx] on its longer side, sampling down by powers of two. */
fun decodeScaled(bytes: ByteArray, maxPx: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Recently shown photos, limited by memory rather than count. */
private object PhotoCache {
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
    }
    operator fun get(key: String): android.graphics.Bitmap? = cache.get(key)
    operator fun set(key: String, value: android.graphics.Bitmap) {
        cache.put(key, value)
    }
}

/** A titled group that starts folded, for long lists the parent rarely needs. */
@Composable
fun Expandable(title: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title.substringBefore(" (")) { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (open) "Hide" else "Show",
            tint = MaterialTheme.colorScheme.primary)
    }
    if (open) Column { content() }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

val ScreenPadding = Modifier.padding(horizontal = 16.dp)
val Gap = Arrangement.spacedBy(8.dp)
