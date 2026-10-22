package io.github.lemonjelly76.babymilestones.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(state: AppState, babyId: String, viewModel: AppViewModel, onBack: () -> Unit) {
    val view = state.view(babyId) ?: return
    val text = view.summary.toPlainText()
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(title = { Text("Health visitor summary") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).then(ScreenPadding)) {
            Text("Take this to a review, or share it before. It lists what ${view.baby.name} has done, anything not seen yet, and your questions.",
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Gap, modifier = Modifier.padding(vertical = 12.dp)) {
                Button(onClick = { Export.sharePdf(context, "${view.baby.name} development summary", text) }) { Text("Share PDF") }
                OutlinedButton(onClick = { Export.shareText(context, text) }) { Text("Share text") }
            }
            InfoCard { Text(text, fontFamily = FontFamily.Default, style = MaterialTheme.typography.bodySmall) }
            Disclaimer(view.content)
        }
    }
}

/** Shares through the Android share sheet; files go to the app's private cache and are offered via FileProvider. */
object Export {

    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Share summary"))
    }

    fun shareFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share"))
    }

    fun sharePdf(context: Context, title: String, text: String) = shareFile(context, pdf(context, title, text), "application/pdf")

    fun shareJson(context: Context, json: String) {
        val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "baby-milestones-export.json")
        file.writeText(json)
        shareFile(context, file, "application/json")
    }

    /** A plain A4 PDF of the summary text, wrapped to the page. */
    fun pdf(context: Context, title: String, text: String): File {
        val doc = PdfDocument()
        val width = 595
        val height = 842
        val margin = 48f
        val body = Paint().apply { textSize = 11f; isAntiAlias = true }
        val heading = Paint().apply { textSize = 16f; isFakeBoldText = true; isAntiAlias = true }
        val lineHeight = 16f
        val maxWidth = width - 2 * margin

        val lines = text.lines().flatMap { wrap(it, body, maxWidth) }
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, pageNumber).create())
        var y = margin + 8
        page.canvas.drawText(title, margin, y, heading)
        y += lineHeight * 2
        for (line in lines) {
            if (y > height - margin) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, ++pageNumber).create())
                y = margin
            }
            page.canvas.drawText(line, margin, y, body)
            y += lineHeight
        }
        doc.finishPage(page)
        val file = File(File(context.cacheDir, "exports").apply { mkdirs() }, "development-summary.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun wrap(line: String, paint: Paint, maxWidth: Float): List<String> {
        if (line.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (word in line.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                out += current.toString()
                current = StringBuilder("  $word")
            } else {
                current = StringBuilder(candidate)
            }
        }
        out += current.toString()
        return out
    }
}
