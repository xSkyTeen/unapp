package sky.universidad.UnApp.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.core.common.TaskVisualStatus
import sky.universidad.UnApp.domain.model.AcademicTask
import sky.universidad.UnApp.domain.model.ForumReplyItem
import sky.universidad.UnApp.domain.model.ForumTopic
import sky.universidad.UnApp.domain.model.HomeworkDetailInfo
import sky.universidad.UnApp.ui.theme.LocalUnAppColors

data class TaskStatusPalette(
    val container: Color,
    val content: Color,
    val tintBackground: Color,
    val borderColor: Color
)

@Composable
fun taskStatusPalette(status: TaskVisualStatus): TaskStatusPalette {
    val extended = LocalUnAppColors.current
    val cardGlass = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
    val cardBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.68f)
    return when (status) {
        TaskVisualStatus.DELIVERED -> TaskStatusPalette(
            container = extended.successContainer,
            content = extended.onSuccessContainer,
            tintBackground = cardGlass,
            borderColor = cardBorder
        )
        TaskVisualStatus.ACTIVE -> TaskStatusPalette(
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            tintBackground = cardGlass,
            borderColor = cardBorder
        )
        TaskVisualStatus.DUE_TODAY, TaskVisualStatus.EXPIRED_TODAY -> TaskStatusPalette(
            container = extended.warningContainer,
            content = extended.onWarningContainer,
            tintBackground = cardGlass,
            borderColor = cardBorder
        )
        TaskVisualStatus.EXPIRED_PAST -> TaskStatusPalette(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            tintBackground = cardGlass,
            borderColor = cardBorder
        )
    }
}

@Composable
fun ReadOnlyTaskDetailSheet(
    task: AcademicTask,
    courseColor: Color,
    courseCode: String?,
    onLoadDetail: (AcademicTask, (HomeworkDetailInfo?) -> Unit) -> Unit,
    onCreateNote: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var detail by remember(task.remoteId) { mutableStateOf<HomeworkDetailInfo?>(null) }
    var isLoading by remember(task.remoteId) { mutableStateOf(true) }

    LaunchedEffect(task.remoteId) {
        isLoading = true
        onLoadDetail(task) { loaded ->
            detail = loaded
            isLoading = false
        }
    }

    val status = DateFormatters.resolveTaskVisualStatus(
        dueTimestamp = task.dueTimestamp,
        isSubmitted = task.isSubmittedRemote,
        isEnded = task.isEnded
    )
    val palette = taskStatusPalette(status)

    AppleBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!courseCode.isNullOrBlank()) {
                    ApplePillBadge(
                        text = courseCode,
                        containerColor = courseColor.copy(alpha = 0.16f),
                        contentColor = courseColor
                    )
                }
                ApplePillBadge(
                    text = DateFormatters.statusLabel(status),
                    containerColor = palette.container,
                    contentColor = palette.content
                )
                if (task.unit.isNotBlank()) {
                    ApplePillBadge(
                        text = task.unit,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = detail?.title ?: task.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = listOfNotNull(
                    task.courseName,
                    task.topicName.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AppleSubtleDivider()

            val effectiveBegin = detail?.dateBegin?.takeIf { it.isNotBlank() } ?: task.startDateRaw.orEmpty()
            val effectiveEnd = detail?.dateEnd?.takeIf { it.isNotBlank() }?.let {
                DateFormatters.formatRelativeAcademicDate(it)
            } ?: DateFormatters.formatRelativeAcademicDate(task.dueDateRaw, task.dueTimestamp)
            val attemptsCount = detail?.attempts ?: task.attempts
            val extFilter = detail?.fileFilter?.takeIf { it.isNotBlank() } ?: task.fileFilter

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (effectiveBegin.isNotBlank()) {
                    MetadataLine(label = "Inicio", value = effectiveBegin)
                }
                MetadataLine(label = "Cierre", value = effectiveEnd)
                MetadataLine(label = "Intentos permitidos", value = "$attemptsCount")
                if (extFilter.isNotBlank()) {
                    MetadataLine(label = "Formato permitido", value = extFilter)
                }
                if (detail?.isGroup == true) {
                    MetadataLine(label = "Modalidad", value = "Entrega grupal")
                }
                detail?.rubricScore?.takeIf { it.isNotBlank() }?.let { score ->
                    MetadataLine(label = "Evaluación", value = score)
                }
            }

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Consultando detalles e intentos en Aula Virtual...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Descripción",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail?.description?.ifBlank { task.description } ?: task.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            detail?.teacherFiles?.takeIf { it.isNotEmpty() }?.let { files ->
                Text(
                    text = "Archivos adjuntos del docente",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                files.forEach { file ->
                    AppleCard(
                        onClick = file.url?.let { url -> { onOpenUrl(url) } }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = UnAppIcons.Document,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (file.url != null) {
                                Icon(
                                    imageVector = UnAppIcons.ExternalLink,
                                    contentDescription = "Abrir",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            detail?.studentDeliveries?.takeIf { it.isNotEmpty() }?.let { deliveries ->
                Text(
                    text = "Entregas registradas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                deliveries.forEach { item ->
                    AppleCard(
                        containerColor = palette.tintBackground,
                        onClick = item.url?.let { url -> { onOpenUrl(url) } }
                    ) {
                        Text(
                            text = item.fileName ?: "Archivo entregado",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.dateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ForumDiscussionSheet(
    forum: ForumTopic,
    courseColor: Color,
    onLoadDiscussion: (ForumTopic, (String, List<ForumReplyItem>) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var prompt by remember(forum.remoteId) { mutableStateOf(forum.description) }
    var replies by remember(forum.remoteId) { mutableStateOf<List<ForumReplyItem>>(emptyList()) }
    var isLoading by remember(forum.remoteId) { mutableStateOf(true) }

    LaunchedEffect(forum.remoteId) {
        isLoading = true
        onLoadDiscussion(forum) { resolvedPrompt, resolvedReplies ->
            if (resolvedPrompt.isNotBlank()) prompt = resolvedPrompt
            replies = resolvedReplies
            isLoading = false
        }
    }

    AppleBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ApplePillBadge(
                    text = forum.courseName,
                    containerColor = courseColor.copy(alpha = 0.16f),
                    contentColor = courseColor
                )
                if (forum.unitName.isNotBlank()) {
                    ApplePillBadge(
                        text = forum.unitName,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val isForumActive = DateFormatters.isForumActiveNow(
                    closeTimestamp = forum.closeTimestamp,
                    startDateRaw = forum.startDateRaw,
                    isEnded = forum.isEnded
                )
                ApplePillBadge(
                    text = if (!isForumActive) "Cerrado" else "Abierto",
                    containerColor = if (!isForumActive) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (!isForumActive) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }

            Text(
                text = forum.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            val datesLabel = listOfNotNull(
                forum.startDateRaw?.takeIf { it.isNotBlank() }?.let { "Inicio: $it" },
                forum.closeDateRaw?.takeIf { it.isNotBlank() }?.let {
                    "Cierre: ${DateFormatters.formatRelativeAcademicDate(it, forum.closeTimestamp)}"
                }
            ).joinToString(" · ")
            if (datesLabel.isNotBlank()) {
                Text(
                    text = datesLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AppleSubtleDivider()

            Text(
                text = "Consigna del foro",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = prompt.ifBlank { "Consulta las indicaciones del foro en Aula Virtual." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            AppleSubtleDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Participaciones (${if (replies.isNotEmpty()) replies.size else forum.answersCount})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Cargando intervenciones de estudiantes...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (replies.isEmpty()) {
                Text(
                    text = "Aún no hay respuestas registradas para visualizar en este foro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                replies.forEach { reply ->
                    AppleCard(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = reply.userName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (!reply.score.isNullOrBlank()) {
                                ApplePillBadge(text = "Nota: ${reply.score}")
                            }
                        }
                        if (reply.dateView.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = reply.dateView,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = reply.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
