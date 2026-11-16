package sky.universidad.UnApp.presentation.more

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.BuildConfig
import sky.universidad.UnApp.R
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleBottomSheet
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleSectionHeader
import sky.universidad.UnApp.presentation.components.AppleSegmentedControl
import sky.universidad.UnApp.presentation.components.AppleStickyCurvedHeader
import sky.universidad.UnApp.presentation.components.AppleSubtleDivider
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.components.applePressable

enum class MoreDestination {
    HUB,
    PROFILE,
    INTRANET_GRADES,
    CURRICULUM,
    ACADEMIC_HISTORY,
    ATTENDANCE,
    ADMIN_PROCEDURES,
    NOTES,
    FORUMS,
    ANNOUNCEMENTS,
    BUS,
    PORTAL,
    SETTINGS
}

private data class MoreMenuItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String?,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun MoreHubScreen(
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    onNavigate: (MoreDestination) -> Unit
) {
    var showReportSheet by remember { mutableStateOf(false) }

    val academicItems = listOf(
        MoreMenuItem(
            id = "intranet_grades",
            title = "Notas por unidad",
            subtitle = "Calificaciones parciales y evaluaciones del semestre",
            badge = state.exams.size.takeIf { it > 0 }?.toString(),
            icon = UnAppIcons.Award,
            onClick = { onNavigate(MoreDestination.INTRANET_GRADES) }
        ),
        MoreMenuItem(
            id = "curriculum",
            title = "Plan de estudios y currícula",
            subtitle = "Malla curricular completa por ciclos y requisitos",
            badge = state.intranetOverview?.curriculum?.size?.let { "$it ciclos" },
            icon = UnAppIcons.Book,
            onClick = { onNavigate(MoreDestination.CURRICULUM) }
        ),
        MoreMenuItem(
            id = "academic_history",
            title = "Historial de notas",
            subtitle = "Todos los periodos matriculados y promedios",
            badge = state.intranetOverview?.history?.size?.let { "$it periodos" },
            icon = UnAppIcons.Document,
            onClick = { onNavigate(MoreDestination.ACADEMIC_HISTORY) }
        ),
        MoreMenuItem(
            id = "attendance",
            title = "Asistencia e inasistencias",
            subtitle = "Registro por asignatura en Intranet",
            badge = null,
            icon = UnAppIcons.Calendar,
            onClick = { onNavigate(MoreDestination.ATTENDANCE) }
        ),
        MoreMenuItem(
            id = "admin_procedures",
            title = "Pagos, deudas y trámites",
            subtitle = "Historial de pagos y solicitudes en Intranet",
            badge = null,
            icon = UnAppIcons.Document,
            onClick = { onNavigate(MoreDestination.ADMIN_PROCEDURES) }
        )
    )

    val campusItems = listOf(
        MoreMenuItem(
            id = "forums",
            title = "Foros académicos",
            subtitle = "Consignas y participaciones de Aula Virtual",
            badge = "${state.forums.size}",
            icon = UnAppIcons.Forum,
            onClick = { onNavigate(MoreDestination.FORUMS) }
        ),
        MoreMenuItem(
            id = "announcements",
            title = "Anuncios",
            subtitle = "Comunicados de Aula Virtual e Intranet",
            badge = "${state.announcements.size}",
            icon = UnAppIcons.Bell,
            onClick = { onNavigate(MoreDestination.ANNOUNCEMENTS) }
        ),
        MoreMenuItem(
            id = "notes",
            title = "Apuntes personales",
            subtitle = "Notas de clase y recordatorios sin conexión",
            badge = "${state.notes.size}",
            icon = UnAppIcons.Note,
            onClick = { onNavigate(MoreDestination.NOTES) }
        ),
        MoreMenuItem(
            id = "bus",
            title = "Bus universitario",
            subtitle = "Rutas referenciales, paraderos y turnos UNAP",
            badge = "${state.busRoutes.size} rutas",
            icon = UnAppIcons.Bus,
            onClick = { onNavigate(MoreDestination.BUS) }
        )
    )

    val supportItems = listOf(
        MoreMenuItem(
            id = "portal",
            title = "Portal web UNAP",
            subtitle = "Abrir Aula Virtual o Intranet con sesión activa",
            badge = null,
            icon = UnAppIcons.ExternalLink,
            onClick = { onNavigate(MoreDestination.PORTAL) }
        ),
        MoreMenuItem(
            id = "report_issue",
            title = "Reportar problema o sugerencia",
            subtitle = "Enviar fallos, mejoras o comentarios de la app",
            badge = null,
            icon = UnAppIcons.AlertCircle,
            onClick = { showReportSheet = true }
        ),
        MoreMenuItem(
            id = "settings",
            title = "Ajustes y sesión",
            subtitle = "Apariencia, sincronización y cierre de sesión",
            badge = null,
            icon = UnAppIcons.Settings,
            onClick = { onNavigate(MoreDestination.SETTINGS) }
        )
    )

    val displayName = remember(state.student?.fullName) {
        val raw = state.student?.fullName.orEmpty()
        if (raw.isBlank()) "Mi perfil universitario" else DateFormatters.formatTitleCase(raw)
    }
    val givenInitials = remember(state.student?.fullName) {
        val given = DateFormatters.extractGivenName(state.student?.fullName)
        given.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
            .ifBlank { "UN" }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleStickyCurvedHeader(
            title = "Más",
            subtitle = null
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 14.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Featured "Mi Perfil" card at the top using the UNAP entrance photo banner
            item(key = "featured_profile_card") {
                val cardShape = RoundedCornerShape(24.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .applePressable { onNavigate(MoreDestination.PROFILE) },
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(142.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.entrada_ciudad_universitaria),
                            contentDescription = "Puerta de la Ciudad Universitaria UNAP",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF0A1733).copy(alpha = 0.88f),
                                            Color(0xFF0E234E).copy(alpha = 0.72f),
                                            Color(0xFF0A1733).copy(alpha = 0.48f)
                                        )
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .border(1.4.dp, Color.White.copy(alpha = 0.75f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = givenInitials,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                ApplePillBadge(
                                    text = "Mi perfil UNAP",
                                    containerColor = Color.White.copy(alpha = 0.20f),
                                    contentColor = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val careerMeta = state.student?.career?.takeIf { it.isNotBlank() }
                                    ?.let { DateFormatters.formatTitleCase(it) }
                                    ?: "Ver código, DNI y ficha institucional"
                                Text(
                                    text = careerMeta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.86f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = UnAppIcons.ChevronRight,
                                    contentDescription = "Abrir perfil",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(2.dp))
                AppleSectionHeader(
                    title = "Académico (Intranet)",
                    subtitle = null
                )
            }

            items(academicItems, key = { it.id }) { item ->
                MoreMenuCardRow(item = item, onClick = item.onClick)
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                AppleSectionHeader(
                    title = "Campus y comunidad",
                    subtitle = null
                )
            }

            items(campusItems, key = { it.id }) { item ->
                MoreMenuCardRow(item = item, onClick = item.onClick)
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                AppleSectionHeader(
                    title = "Sistema y soporte",
                    subtitle = null
                )
            }

            items(supportItems, key = { it.id }) { item ->
                MoreMenuCardRow(item = item, onClick = item.onClick)
            }
        }
    }

    if (showReportSheet) {
        ReportIssueSheet(
            state = state,
            onDismiss = { showReportSheet = false }
        )
    }
}

@Composable
private fun ReportIssueSheet(
    state: MainUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val categories = listOf("Error", "Sugerencia", "Otro")
    var categoryIndex by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(true) }

    fun buildReportBody(): String = buildString {
        appendLine("Tipo: ${categories[categoryIndex]}")
        appendLine("Detalle:")
        appendLine(message.trim())
        if (includeDiagnostics) {
            appendLine()
            appendLine("--- Diagnóstico técnico anónimo ---")
            appendLine("App: UnApp v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Red: ${if (state.isOnline) "En línea" else "Sin conexión"} · Última sinc.: ${DateFormatters.formatSyncTime(state.lastSyncAt)}")
            appendLine("Cursos cargados: ${state.courses.size} · Tareas: ${state.tasks.size}")
        }
    }

    AppleBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Reportar problema o sugerencia",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Comparte errores, fallos visuales o ideas de mejora. No se incluye DNI, código ni sesión.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AppleSegmentedControl(
                options = categories,
                selectedIndex = categoryIndex,
                onSelect = { categoryIndex = it }
            )

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Describe lo que ocurrió o tu sugerencia") },
                minLines = 4,
                maxLines = 7,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .clickable { includeDiagnostics = !includeDiagnostics }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeDiagnostics,
                    onCheckedChange = { includeDiagnostics = it },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "Adjuntar diagnóstico técnico anónimo (versión y dispositivo)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            AppleSubtleDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (message.isBlank()) {
                            Toast.makeText(context, "Escribe el detalle del reporte.", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        clipboardManager.setText(AnnotatedString(buildReportBody()))
                        Toast.makeText(context, "Reporte copiado al portapapeles.", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.9.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Copiar", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        if (message.isBlank()) {
                            Toast.makeText(context, "Escribe el detalle del reporte.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "[UnApp] ${categories[categoryIndex]}")
                            putExtra(Intent.EXTRA_TEXT, buildReportBody())
                        }
                        val chooser = Intent.createChooser(shareIntent, "Enviar reporte").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(chooser) }
                            .onFailure {
                                clipboardManager.setText(AnnotatedString(buildReportBody()))
                                Toast.makeText(context, "Copiado al portapapeles para compartir.", Toast.LENGTH_SHORT).show()
                            }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Enviar reporte", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun MoreMenuCardRow(
    item: MoreMenuItem,
    onClick: () -> Unit
) {
    AppleCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!item.badge.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                ApplePillBadge(
                    text = item.badge,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = UnAppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
