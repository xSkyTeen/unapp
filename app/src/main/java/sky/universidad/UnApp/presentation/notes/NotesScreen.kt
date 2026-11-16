package sky.universidad.UnApp.presentation.notes

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.core.common.CourseColorPalette
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.domain.model.Course
import sky.universidad.UnApp.domain.model.PersonalNote
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleBottomSheet
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.AppleCollapsibleSection
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleStickyCurvedHeader
import sky.universidad.UnApp.presentation.components.EmptyStateView
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.components.applePressable

@Composable
fun NotesScreen(
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    initialCoursePreset: Pair<String, String>? = null,
    onClearCoursePreset: () -> Unit = {},
    onBack: () -> Unit,
    onSaveNote: (id: Long, title: String, content: String, courseId: String?, courseName: String?, isPinned: Boolean) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onTogglePin: (PersonalNote) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<PersonalNote?>(null) }
    var isCreatingNew by remember(initialCoursePreset) { mutableStateOf(initialCoursePreset != null) }
    var notePendingDelete by remember { mutableStateOf<PersonalNote?>(null) }

    val filteredNotes = remember(state.notes, query) {
        val q = query.trim()
        if (q.isEmpty()) state.notes
        else state.notes.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.content.contains(q, ignoreCase = true) ||
                it.courseName.orEmpty().contains(q, ignoreCase = true)
        }
    }

    val groupedNotes = remember(filteredNotes) {
        filteredNotes.groupBy { it.courseName?.takeIf { c -> c.isNotBlank() } ?: "Apuntes generales" }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleStickyCurvedHeader(
            title = "Apuntes",
            subtitle = null,
            onBack = onBack,
            trailingContent = {
                Button(
                    onClick = { isCreatingNew = true },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = UnAppIcons.Plus,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nuevo", style = MaterialTheme.typography.labelMedium)
                }
            },
            bottomContent = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar apuntes o curso") },
                    leadingIcon = {
                        Icon(
                            imageVector = UnAppIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 14.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredNotes.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = UnAppIcons.Note,
                        title = if (query.isBlank()) "Sin apuntes guardados" else "Sin coincidencias",
                        subtitle = "Tus notas se guardan en la base local del dispositivo y no dependen de Internet ni de la sesión web.",
                        actionLabel = if (query.isBlank()) "Crear primer apunte" else null,
                        onAction = if (query.isBlank()) {
                            { isCreatingNew = true }
                        } else null
                    )
                }
            } else {
                groupedNotes.forEach { (courseGroup, notesList) ->
                    val groupColor = CourseColorPalette.resolveCourseColor(null, courseGroup, state.courses)
                    item(key = "note_group_$courseGroup") {
                        AppleCollapsibleSection(
                            title = courseGroup,
                            subtitle = null,
                            countBadge = "${notesList.size}",
                            accentColor = groupColor,
                            initiallyExpanded = true
                        ) {
                            notesList.forEach { note ->
                                AppleCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = groupColor,
                                    onClick = { editingNote = note }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (note.isPinned) {
                                                ApplePillBadge(text = "Fijado")
                                            }
                                        }
                                        Row {
                                            IconButton(
                                                onClick = { onTogglePin(note) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = UnAppIcons.Pin,
                                                    contentDescription = if (note.isPinned) "Desfijar" else "Fijar",
                                                    tint = if (note.isPinned) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { notePendingDelete = note },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = UnAppIcons.Trash,
                                                    contentDescription = "Eliminar apunte",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = note.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (note.content.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = note.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Editado: ${DateFormatters.formatSyncTime(note.updatedAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCreatingNew || editingNote != null) {
        NoteEditorSheet(
            existing = editingNote,
            courses = state.courses,
            presetCourse = initialCoursePreset,
            onDismiss = {
                editingNote = null
                isCreatingNew = false
                onClearCoursePreset()
            },
            onSave = { title, content, courseId, courseName, pinned ->
                onSaveNote(
                    editingNote?.id ?: 0L,
                    title,
                    content,
                    courseId,
                    courseName,
                    pinned
                )
                editingNote = null
                isCreatingNew = false
                onClearCoursePreset()
            }
        )
    }

    notePendingDelete?.let { target ->
        AppleBottomSheet(onDismiss = { notePendingDelete = null }) {
            Text(
                text = "Eliminar apunte",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "¿Deseas eliminar \"${target.title}\"? Esta acción no se puede deshacer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { notePendingDelete = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        onDeleteNote(target.id)
                        notePendingDelete = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text("Eliminar")
                }
            }
        }
    }
}

@Composable
private fun NoteEditorSheet(
    existing: PersonalNote?,
    courses: List<Course>,
    presetCourse: Pair<String, String>?,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, courseId: String?, courseName: String?, isPinned: Boolean) -> Unit
) {
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var content by remember(existing) { mutableStateOf(existing?.content.orEmpty()) }
    var selectedCourseId by remember(existing, presetCourse) {
        mutableStateOf(existing?.courseId ?: presetCourse?.first)
    }
    var selectedCourseName by remember(existing, presetCourse) {
        mutableStateOf(existing?.courseName ?: presetCourse?.second)
    }
    var isPinned by remember(existing) { mutableStateOf(existing?.isPinned ?: false) }

    AppleBottomSheet(onDismiss = onDismiss) {
        Text(
            text = if (existing == null) "Nuevo apunte" else "Editar apunte",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Título") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Contenido") },
            minLines = 4,
            maxLines = 8,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (courses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Curso asociado (opcional)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val isGeneral = selectedCourseId == null
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isGeneral) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .applePressable {
                                selectedCourseId = null
                                selectedCourseName = null
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("General", style = MaterialTheme.typography.labelSmall)
                    }
                }
                items(courses, key = { it.remoteId }) { course ->
                    val selected = selectedCourseId == course.remoteId
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .applePressable {
                                selectedCourseId = course.remoteId
                                selectedCourseName = course.name
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(course.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Fijar al inicio de apuntes",
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(checked = isPinned, onCheckedChange = { isPinned = it })
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Cancelar")
            }
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, content, selectedCourseId, selectedCourseName, isPinned)
                    }
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.weight(1.4f).height(48.dp)
            ) {
                Text("Guardar apunte")
            }
        }
    }
}
