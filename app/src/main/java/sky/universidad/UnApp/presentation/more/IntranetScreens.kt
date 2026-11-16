package sky.universidad.UnApp.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.core.common.CourseColorPalette
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.domain.model.CurriculumCourseItem
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleBottomSheet
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.AppleCollapsibleSection
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleStickyCurvedHeader
import sky.universidad.UnApp.presentation.components.AppleSubtleDivider
import sky.universidad.UnApp.presentation.components.EmptyStateView
import sky.universidad.UnApp.presentation.components.ErrorRetryBanner
import sky.universidad.UnApp.presentation.components.IntranetModuleSkeleton
import sky.universidad.UnApp.presentation.components.UnAppIcons
import sky.universidad.UnApp.presentation.components.applePressable
import sky.universidad.UnApp.ui.theme.LocalUnAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntranetModuleScreen(
    destination: MoreDestination,
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    onLoadIntranet: (forceRefresh: Boolean) -> Unit = {},
    onLoadIntranetSection: (sectionKey: String, forceRefresh: Boolean) -> Unit = { _, force ->
        onLoadIntranet(force)
    },
    onEnsureGradesLoaded: (forceRefresh: Boolean) -> Unit = {},
    onBack: () -> Unit
) {
    LaunchedEffect(destination) {
        if (destination == MoreDestination.INTRANET_GRADES) {
            onEnsureGradesLoaded(false)
        } else {
            onLoadIntranetSection(destination.name, false)
        }
    }

    val (title, subtitle) = when (destination) {
        MoreDestination.INTRANET_GRADES -> "Notas" to null
        MoreDestination.CURRICULUM -> "Plan de estudios" to null
        MoreDestination.ACADEMIC_HISTORY -> "Historial académico" to null
        MoreDestination.ATTENDANCE -> "Asistencia" to null
        else -> "Pagos y trámites" to null
    }

    val overview = state.intranetOverview
    val extended = LocalUnAppColors.current
    var selectedCurriculumCourse by remember { mutableStateOf<Pair<String, CurriculumCourseItem>?>(null) }
    val isCurrentLoading = if (destination == MoreDestination.INTRANET_GRADES) {
        state.isLoadingGrades
    } else {
        state.isLoadingIntranet
    }
    val triggerRefresh: () -> Unit = {
        if (destination == MoreDestination.INTRANET_GRADES) {
            onEnsureGradesLoaded(true)
        } else {
            onLoadIntranetSection(destination.name, true)
        }
    }

    val courseById = remember(state.courses) { state.courses.associateBy { it.remoteId } }
    val courseByName = remember(state.courses) {
        state.courses.associateBy { CourseColorPalette.normalizeCode(null, it.name) }
    }
    val examsByCourseKey = remember(state.exams, courseById, courseByName) {
        state.exams.groupBy { exam ->
            courseById[exam.courseId]?.remoteId
                ?: courseByName[CourseColorPalette.normalizeCode(null, exam.courseName)]?.remoteId
                ?: exam.courseName.ifBlank { "General" }
        }
    }
    val orderedCourseKeys = remember(state.courses, examsByCourseKey) {
        (state.courses.map { it.remoteId } + examsByCourseKey.keys).distinct()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleStickyCurvedHeader(
            title = title,
            subtitle = subtitle,
            onBack = onBack
        )

        PullToRefreshBox(
            isRefreshing = isCurrentLoading || state.isSyncing,
            onRefresh = triggerRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 14.dp,
                    bottom = bottomContentPadding.calculateBottomPadding() + 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.intranetError?.takeIf { destination != MoreDestination.INTRANET_GRADES }?.let { err ->
                    item {
                        ErrorRetryBanner(message = err, onRetry = triggerRefresh)
                    }
                }

                when (destination) {
                    MoreDestination.INTRANET_GRADES -> {
                        if (isCurrentLoading && state.exams.isEmpty()) {
                            item {
                                IntranetModuleSkeleton()
                            }
                        } else if (state.courses.isEmpty() && state.exams.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = UnAppIcons.Award,
                                    title = "Sin notas registradas"
                                )
                            }
                        } else {
                            // Centralize all grades, evaluations & course info neatly under each enrolled course
                            orderedCourseKeys.forEach { key ->
                                val course = courseById[key]
                                val grades = examsByCourseKey[key].orEmpty()
                                if (course != null || grades.isNotEmpty()) {
                                    val displayTitle = course?.name ?: grades.firstOrNull()?.courseName ?: key
                                    val cColor = course?.let { CourseColorPalette.parseColor(it.colorHex) }
                                        ?: CourseColorPalette.resolveCourseColor(null, displayTitle, state.courses)
                                    val numericScores = grades.mapNotNull { it.grade?.trim()?.toDoubleOrNull() }
                                    val avgText = if (numericScores.isNotEmpty()) {
                                        "Prom: ${"%.1f".format(numericScores.average())}"
                                    } else null
                                    val subtitleParts = listOfNotNull(
                                        course?.code?.takeIf { it.isNotBlank() },
                                        course?.teacher?.takeIf { it.isNotBlank() }?.let { DateFormatters.formatTitleCase(it) },
                                        avgText
                                    ).joinToString(" · ")

                                    item(key = "central_grade_$key") {
                                        AppleCollapsibleSection(
                                            title = displayTitle,
                                            subtitle = subtitleParts.ifBlank { "${grades.size} evaluación(es)" },
                                            countBadge = if (grades.isNotEmpty()) "${grades.size} notas" else "Sin notas",
                                            accentColor = cColor,
                                            initiallyExpanded = grades.isNotEmpty()
                                        ) {
                                            if (grades.isEmpty()) {
                                                Text(
                                                    text = "Aún no se registran calificaciones publicadas en este curso.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                grades.forEach { exam ->
                                                    AppleCard(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        accentColor = cColor
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = exam.title,
                                                                    style = MaterialTheme.typography.titleSmall,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                                val meta = listOfNotNull(
                                                                    exam.examType.takeIf { it.isNotBlank() },
                                                                    exam.dateRaw?.takeIf { it.isNotBlank() }
                                                                ).joinToString(" · ")
                                                                if (meta.isNotBlank()) {
                                                                    Text(
                                                                        text = meta,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            ApplePillBadge(
                                                                text = exam.grade?.takeIf { it.isNotBlank() } ?: "Sin nota",
                                                                containerColor = if (!exam.grade.isNullOrBlank()) {
                                                                    MaterialTheme.colorScheme.primaryContainer
                                                                } else {
                                                                    MaterialTheme.colorScheme.surfaceVariant
                                                                },
                                                                contentColor = if (!exam.grade.isNullOrBlank()) {
                                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MoreDestination.CURRICULUM -> {
                        val cycles = overview?.curriculum.orEmpty().sortedBy { extractCycleOrder(it.cycleName) }
                        if (isCurrentLoading && cycles.isEmpty()) {
                            item { IntranetModuleSkeleton() }
                        } else if (cycles.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = UnAppIcons.Book,
                                    title = "Sin malla curricular"
                                )
                            }
                        } else {
                            items(cycles, key = { it.cycleName }) { cycle ->
                                val orderedCourses = remember(cycle.courses) {
                                    cycle.courses.sortedBy { it.code }
                                }
                                val totalCredits = remember(orderedCourses) {
                                    orderedCourses.sumOf { it.credits.toDoubleOrNull() ?: 0.0 }
                                }
                                val creditsLabel = if (totalCredits % 1.0 == 0.0) {
                                    totalCredits.toInt().toString()
                                } else {
                                    String.format("%.1f", totalCredits)
                                }

                                AppleCollapsibleSection(
                                    title = cycle.cycleName,
                                    subtitle = "${orderedCourses.size} asignaturas · $creditsLabel cr.",
                                    countBadge = "${orderedCourses.size}",
                                    initiallyExpanded = true
                                ) {
                                    val tableShape = RoundedCornerShape(16.dp)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(tableShape)
                                            .border(
                                                0.8.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
                                                tableShape
                                            )
                                    ) {
                                        // Table header
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f))
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Código",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.width(66.dp)
                                            )
                                            Text(
                                                text = "Asignatura",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "Cr.",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.width(34.dp)
                                            )
                                            Text(
                                                text = "Estado",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                textAlign = TextAlign.End,
                                                modifier = Modifier.width(72.dp)
                                            )
                                        }

                                        orderedCourses.forEachIndexed { idx, item ->
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(0.6.dp)
                                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                                            )
                                            val rowBg = if (idx % 2 == 0) {
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(rowBg)
                                                    .applePressable {
                                                        selectedCurriculumCourse = cycle.cycleName to item
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.code.ifBlank { "-" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.width(66.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                                    Text(
                                                        text = item.name,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    val meta = listOfNotNull(
                                                        item.hoursSummary.takeIf { it.isNotBlank() },
                                                        item.prerequisites.takeIf { it.isNotBlank() }?.let { "Req: $it" }
                                                    ).joinToString(" · ")
                                                    if (meta.isNotBlank()) {
                                                        Text(
                                                            text = meta,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = item.credits.ifBlank { "-" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.width(34.dp)
                                                )
                                                Box(
                                                    modifier = Modifier.width(72.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    val statusLabel = item.status.ifBlank { "Pendiente" }
                                                    val isApproved = statusLabel.equals("Aprobado", ignoreCase = true)
                                                    ApplePillBadge(
                                                        text = if (isApproved) "Aprob." else statusLabel,
                                                        containerColor = if (isApproved) {
                                                            extended.successContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.surfaceVariant
                                                        },
                                                        contentColor = if (isApproved) {
                                                            extended.onSuccessContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MoreDestination.ACADEMIC_HISTORY -> {
                        val semesters = overview?.history.orEmpty()
                        if (isCurrentLoading && semesters.isEmpty()) {
                            item { IntranetModuleSkeleton() }
                        } else if (semesters.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = UnAppIcons.Award,
                                    title = "Sin historial académico"
                                )
                            }
                        } else {
                            items(semesters, key = { it.periodName }) { sem ->
                                AppleCollapsibleSection(
                                    title = sem.periodName,
                                    subtitle = "${sem.courses.size} cursos registrados",
                                    countBadge = "${sem.courses.size}",
                                    initiallyExpanded = true
                                ) {
                                    sem.courses.forEach { hc ->
                                        val cColor = CourseColorPalette.resolveCourseColor(null, hc.course, state.courses)
                                        val gradeNum = hc.finalGrade.toDoubleOrNull()
                                        val isPassed = gradeNum != null && gradeNum >= 10.5 && hc.approved
                                        val isFailed = gradeNum != null && (gradeNum < 10.5 || !hc.approved)

                                        AppleCard(modifier = Modifier.fillMaxWidth(), accentColor = cColor) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = hc.course,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                    Text(
                                                        text = "Ciclo ${hc.academicYear} · ${hc.credits} créditos · Intento ${hc.attempts}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                ApplePillBadge(
                                                    text = if (hc.finalGrade == "-") "En curso" else "Prom: ${hc.finalGrade}",
                                                    containerColor = when {
                                                        isPassed -> extended.successContainer
                                                        isFailed -> MaterialTheme.colorScheme.errorContainer
                                                        else -> MaterialTheme.colorScheme.primaryContainer
                                                    },
                                                    contentColor = when {
                                                        isPassed -> extended.onSuccessContainer
                                                        isFailed -> MaterialTheme.colorScheme.onErrorContainer
                                                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MoreDestination.ATTENDANCE -> {
                        val attList = overview?.attendance.orEmpty()
                        if (isCurrentLoading && attList.isEmpty()) {
                            item { IntranetModuleSkeleton() }
                        } else if (attList.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = UnAppIcons.Calendar,
                                    title = "Sin asistencias"
                                )
                            }
                        } else {
                            items(attList, key = { it.sectionId.ifBlank { it.courseName } }) { att ->
                                val cColor = CourseColorPalette.resolveCourseColor(att.sectionId, att.courseName, state.courses)
                                val highAbsences = att.maxAbsences > 0 && att.absences >= (att.maxAbsences * 0.7)
                                AppleCard(modifier = Modifier.fillMaxWidth(), accentColor = cColor) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = att.courseName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        ApplePillBadge(
                                            text = "Faltas: ${att.absences}/${att.maxAbsences}",
                                            containerColor = if (highAbsences) {
                                                MaterialTheme.colorScheme.errorContainer
                                            } else {
                                                extended.successContainer
                                            },
                                            contentColor = if (highAbsences) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                extended.onSuccessContainer
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Dictadas: ${att.dictated} de ${att.totalClasses} sesiones · Asistidas: ${att.assisted}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        val records = overview?.adminRecords.orEmpty()
                        if (isCurrentLoading && records.isEmpty()) {
                            item { IntranetModuleSkeleton() }
                        } else if (records.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = UnAppIcons.Document,
                                    title = "Sin deudas ni trámites"
                                )
                            }
                        } else {
                            items(records, key = { it.id }) { rec ->
                                AppleCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ApplePillBadge(text = rec.category)
                                        ApplePillBadge(
                                            text = rec.amountOrStatus,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = rec.concept,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (rec.dateLabel.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = rec.dateLabel,
                                            style = MaterialTheme.typography.bodySmall,
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

        selectedCurriculumCourse?.let { (cycleName, item) ->
            val normalizedTarget = CourseColorPalette.normalizeCode(null, item.name)
            val historyMatches = overview?.history.orEmpty().flatMap { sem ->
                sem.courses.filter { hc ->
                    CourseColorPalette.normalizeCode(null, hc.course) == normalizedTarget ||
                        hc.course.equals(item.name, ignoreCase = true)
                }.map { hc -> sem.periodName to hc }
            }
            val statusLabel = item.status.ifBlank { "Pendiente" }
            val isApproved = statusLabel.equals("Aprobado", ignoreCase = true)

            AppleBottomSheet(onDismiss = { selectedCurriculumCourse = null }) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ApplePillBadge(text = cycleName)
                            if (item.code.isNotBlank()) {
                                ApplePillBadge(
                                    text = item.code,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        ApplePillBadge(
                            text = statusLabel,
                            containerColor = if (isApproved) extended.successContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isApproved) extended.onSuccessContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    AppleSubtleDivider()

                    CurriculumDetailRow(
                        label = "Créditos",
                        value = item.credits.ifBlank { "No especificado" }
                    )
                    CurriculumDetailRow(
                        label = "Carga horaria",
                        value = item.hoursSummary.ifBlank { "No especificado" }
                    )
                    CurriculumDetailRow(
                        label = "Prerrequisitos",
                        value = item.prerequisites.ifBlank { "Ninguno" }
                    )

                    if (historyMatches.isNotEmpty()) {
                        AppleSubtleDivider()
                        Text(
                            text = "Registro en historial académico",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        historyMatches.forEach { (period, hc) ->
                            AppleCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = period,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Intento ${hc.attempts} · ${hc.credits} cr.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    ApplePillBadge(
                                        text = if (hc.finalGrade == "-") "En curso" else "Nota: ${hc.finalGrade}",
                                        containerColor = if (hc.approved) extended.successContainer else MaterialTheme.colorScheme.errorContainer,
                                        contentColor = if (hc.approved) extended.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurriculumDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun extractCycleOrder(cycleName: String): Int {
    val arabic = Regex("\\d+").find(cycleName)?.value?.toIntOrNull()
    if (arabic != null) return arabic
    val upper = cycleName.uppercase()
    val romanMap = listOf(
        "XII" to 12, "XI" to 11, "X" to 10, "IX" to 9,
        "VIII" to 8, "VII" to 7, "VI" to 6, "V" to 5,
        "IV" to 4, "III" to 3, "II" to 2, "I" to 1
    )
    for ((roman, num) in romanMap) {
        if (Regex("\\b$roman\\b").containsMatchIn(upper)) return num
    }
    return Int.MAX_VALUE
}
