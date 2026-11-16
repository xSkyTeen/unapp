package sky.universidad.UnApp.presentation.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DocumentOpenRequest(
    val title: String,
    val url: String,
    val isExternalLink: Boolean = false,
    val fileExtension: String = ""
)

fun normalizeResourceUrl(rawUrl: String, baseUrl: String = "https://aulavirtual2.unap.edu.pe"): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return ""
    return when {
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> "${baseUrl.trimEnd('/')}$trimmed"
        trimmed.startsWith("web/", ignoreCase = true) -> "${baseUrl.trimEnd('/')}/$trimmed"
        !trimmed.contains("://") && !trimmed.contains(" ") -> "https://$trimmed"
        else -> trimmed
    }
}

fun isUnapInternalUrl(rawUrl: String): Boolean {
    val normalized = normalizeResourceUrl(rawUrl)
    return normalized.contains("unap.edu.pe", ignoreCase = true)
}

fun openExternalUri(context: Context, rawUrl: String): Boolean {
    val normalized = normalizeResourceUrl(rawUrl)
    if (normalized.isBlank()) {
        Toast.makeText(context, "Enlace no disponible.", Toast.LENGTH_SHORT).show()
        return false
    }
    return runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrElse {
        Toast.makeText(context, "No se encontró una app para abrir este enlace.", Toast.LENGTH_SHORT).show()
        false
    }
}

fun openDownloadedFileExternally(context: Context, file: File): Boolean {
    if (!file.exists() || file.length() <= 0L) {
        Toast.makeText(context, "El archivo aún no está disponible.", Toast.LENGTH_SHORT).show()
        return false
    }
    return runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "doc" -> "application/msword"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "ppt" -> "application/vnd.ms-powerpoint"
            "csv" -> "text/csv"
            "txt" -> "text/plain"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "*/*"
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(viewIntent, "Abrir documento con").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    }.getOrElse {
        Toast.makeText(context, "No hay una aplicación instalada para abrir este tipo de archivo.", Toast.LENGTH_SHORT).show()
        false
    }
}

@Composable
fun DocumentViewerDialog(
    request: DocumentOpenRequest,
    onDownloadFile: (url: String, targetDir: File, onResult: (File?, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var downloadedFile by remember(request.url) { mutableStateOf<File?>(null) }
    var isLoading by remember(request.url) { mutableStateOf(true) }
    var errorMsg by remember(request.url) { mutableStateOf<String?>(null) }
    var pdfBitmaps by remember(request.url) { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(request.url) {
        val resolvedUrl = normalizeResourceUrl(request.url)
        if (resolvedUrl.isBlank()) {
            errorMsg = "El enlace del documento no es válido."
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMsg = null
        pdfBitmaps = emptyList()
        val targetDir = File(context.cacheDir, "unapp_docs")
        onDownloadFile(resolvedUrl, targetDir) { file, err ->
            if (file == null) {
                errorMsg = err ?: "No se pudo descargar el documento."
                isLoading = false
            } else {
                downloadedFile = file
            }
        }
    }

    LaunchedEffect(downloadedFile) {
        val file = downloadedFile ?: return@LaunchedEffect
        val isPdf = file.extension.equals("pdf", ignoreCase = true) ||
            request.fileExtension.equals("pdf", ignoreCase = true)
        if (isPdf) {
            val rendered = withContext(Dispatchers.IO) {
                renderPdfPages(file)
            }
            if (rendered.isNotEmpty()) {
                pdfBitmaps = rendered
            } else {
                errorMsg = "El archivo se descargó, pero no pudo renderizarse como PDF."
            }
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = UnAppIcons.Close,
                            contentDescription = "Cerrar visor",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = request.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = downloadedFile?.name ?: "Aula Virtual UNAP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    downloadedFile?.let { f ->
                        OutlinedButton(
                            onClick = { openDownloadedFileExternally(context, f) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = UnAppIcons.ExternalLink,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Abrir", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                AppleSubtleDivider()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Descargando documento oficial...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        pdfBitmaps.isNotEmpty() -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(pdfBitmaps) { idx, bmp ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.White,
                                        shadowElevation = 2.dp,
                                        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Página ${idx + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat())
                                            )
                                            Text(
                                                text = "Página ${idx + 1} de ${pdfBitmaps.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        downloadedFile != null -> {
                            val file = downloadedFile!!
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                AppleCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = UnAppIcons.Document,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val kb = (file.length() / 1024L).coerceAtLeast(1L)
                                            Text(
                                                text = "Archivo descargado ($kb KB) listo para abrir.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Button(
                                    onClick = { openDownloadedFileExternally(context, file) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Icon(
                                        imageVector = UnAppIcons.ExternalLink,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Abrir documento")
                                }
                            }
                        }

                        else -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                EmptyStateView(
                                    icon = UnAppIcons.Document,
                                    title = "No se pudo abrir en visor",
                                    subtitle = errorMsg ?: "Puedes abrir el enlace directamente en tu navegador."
                                )
                                Button(
                                    onClick = { openExternalUri(context, request.url) },
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("Abrir en navegador")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun renderPdfPages(file: File): List<Bitmap> {
    if (!file.exists() || file.length() <= 0L) return emptyList()
    val pages = mutableListOf<Bitmap>()
    runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = renderer.pageCount.coerceAtMost(20)
                for (i in 0 until count) {
                    runCatching {
                        renderer.openPage(i).use { page ->
                            val width = 920
                            val height = ((width.toFloat() / page.width.coerceAtLeast(1)) * page.height)
                                .toInt()
                                .coerceIn(120, 2400)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            pages.add(bmp)
                        }
                    }
                }
            }
        }
    }
    return pages
}
