package sky.universidad.UnApp.presentation.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.R
import sky.universidad.UnApp.core.common.CourseColorPalette
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.AppleCollapsibleSection
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleStickyCurvedHeader
import sky.universidad.UnApp.presentation.components.AppleSubtleDivider
import sky.universidad.UnApp.presentation.components.StudentProfileSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileScreen(
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    onEnsureProfileLoaded: (forceRefresh: Boolean) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        onEnsureProfileLoaded(false)
    }

    val student = state.student
    val fullNameFormatted = remember(student?.fullName) {
        student?.fullName?.takeIf { it.isNotBlank() }?.let { DateFormatters.formatTitleCase(it) }
            ?: "Estudiante UNAP"
    }
    val initials = remember(student?.fullName) {
        val given = DateFormatters.extractGivenName(student?.fullName)
        given.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
            .ifBlank { "UN" }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleStickyCurvedHeader(
            title = fullNameFormatted,
            subtitle = "Mi perfil UNAP",
            onBack = onBack
        )

        PullToRefreshBox(
            isRefreshing = state.isLoadingProfile,
            onRefresh = { onEnsureProfileLoaded(true) },
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.isLoadingProfile && student == null) {
                    item {
                        StudentProfileSkeleton()
                    }
                } else {
                    // Top entrance banner card with UNAP gate image + transparent crest over solid emblem
                    item {
                        val bannerShape = RoundedCornerShape(26.dp)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = bannerShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                0.8.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                            ),
                            shadowElevation = 2.dp
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(176.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.entrada_ciudad_universitaria),
                                        contentDescription = "Puerta Ciudad Universitaria UNAP",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color(0xFF071226).copy(alpha = 0.25f),
                                                        Color(0xFF091B3D).copy(alpha = 0.85f)
                                                    )
                                                )
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .background(Color.Transparent)
                                            .padding(14.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.logo_unap),
                                            contentDescription = "Escudo UNAP",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(58.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                                .border(2.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = initials,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = fullNameFormatted,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = student?.career?.takeIf { it.isNotBlank() }
                                                    ?.let { DateFormatters.formatTitleCase(it) }
                                                    ?: "Universidad Nacional del Altiplano",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.90f)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    student?.semester?.takeIf { it.isNotBlank() }?.let { sem ->
                                        ApplePillBadge(text = "Ciclo $sem")
                                    }
                                    ApplePillBadge(
                                        text = "${state.courses.size} cursos matriculados",
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Sensitive institutional data isolated exclusively inside Profile screen
                    item {
                        AppleCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Identificación institucional",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            ProfileInfoRow(
                                label = "Nombre completo",
                                value = fullNameFormatted
                            )
                            AppleSubtleDivider(modifier = Modifier.padding(vertical = 8.dp))
                            ProfileInfoRow(
                                label = "Código universitario",
                                value = student?.code?.takeIf { it.isNotBlank() }
                                    ?: state.session.userIdentifier?.takeIf { it.isNotBlank() }
                                    ?: "No registrado"
                            )
                            AppleSubtleDivider(modifier = Modifier.padding(vertical = 8.dp))
                            ProfileInfoRow(
                                label = "DNI / Documento",
                                value = student?.dni?.takeIf { it.isNotBlank() } ?: "Consultar en Intranet"
                            )
                            if (!student?.faculty.isNullOrBlank()) {
                                AppleSubtleDivider(modifier = Modifier.padding(vertical = 8.dp))
                                ProfileInfoRow(
                                    label = "Facultad",
                                    value = DateFormatters.formatTitleCase(student?.faculty)
                                )
                            }
                            if (!student?.career.isNullOrBlank()) {
                                AppleSubtleDivider(modifier = Modifier.padding(vertical = 8.dp))
                                ProfileInfoRow(
                                    label = "Escuela Profesional",
                                    value = DateFormatters.formatTitleCase(student?.career)
                                )
                            }
                        }
                    }

                    if (state.courses.isNotEmpty()) {
                        item {
                            AppleCollapsibleSection(
                                title = "Carga académica",
                                subtitle = null,
                                countBadge = "${state.courses.size}",
                                initiallyExpanded = true
                            ) {
                                state.courses.forEach { course ->
                                    val cColor = CourseColorPalette.parseColor(course.colorHex)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ApplePillBadge(
                                                text = course.code.ifBlank { "UNAP" },
                                                containerColor = cColor.copy(alpha = 0.16f),
                                                contentColor = cColor
                                            )
                                            Text(
                                                text = course.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (course.credits > 0) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${course.credits} cr.",
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
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
