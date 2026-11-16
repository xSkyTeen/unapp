package sky.universidad.UnApp.presentation.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import sky.universidad.UnApp.BuildConfig
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.core.session.SessionStatus
import sky.universidad.UnApp.core.sync.NotificationHelper
import sky.universidad.UnApp.core.sync.UnAppSyncWorker
import sky.universidad.UnApp.data.preferences.ThemeMode
import sky.universidad.UnApp.presentation.MainUiState
import sky.universidad.UnApp.presentation.components.AppleBottomSheet
import sky.universidad.UnApp.presentation.components.AppleCard
import sky.universidad.UnApp.presentation.components.ApplePillBadge
import sky.universidad.UnApp.presentation.components.AppleSegmentedControl
import sky.universidad.UnApp.presentation.components.AppleStickyCurvedHeader
import sky.universidad.UnApp.presentation.components.UnAppIcons

@Composable
fun SettingsScreen(
    state: MainUiState,
    bottomContentPadding: PaddingValues,
    onBack: () -> Unit,
    onSyncNow: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit = {},
    onLogout: (clearPersonalNotes: Boolean) -> Unit
) {
    val context = LocalContext.current
    var showLogoutSheet by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onNotificationsChange(granted)
    }

    val themeIndex = when (state.preferences.themeMode) {
        ThemeMode.SYSTEM -> 0
        ThemeMode.LIGHT -> 1
        ThemeMode.DARK -> 2
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppleStickyCurvedHeader(
            title = "Ajustes y sesión",
            subtitle = "Seguridad, apariencia y sincronización",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 14.dp,
                bottom = bottomContentPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

        item {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Estado de sesión",
                        style = MaterialTheme.typography.titleMedium
                    )
                    ApplePillBadge(
                        text = when (state.session.status) {
                            SessionStatus.AUTHENTICATED -> "Activa"
                            SessionStatus.EXPIRED -> "Expirada"
                            else -> "Inactiva"
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Usuario: ${state.session.userIdentifier ?: "No definido"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Última sincronización: ${DateFormatters.formatSyncTime(state.lastSyncAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSyncNow,
                    enabled = !state.isSyncing,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = UnAppIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (state.isSyncing) "Sincronizando..." else "Sincronizar ahora")
                }
            }
        }

        item {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Apariencia",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                AppleSegmentedControl(
                    options = listOf("Sistema", "Claro", "Oscuro"),
                    selectedIndex = themeIndex,
                    onSelect = { idx ->
                        val next = when (idx) {
                            1 -> ThemeMode.LIGHT
                            2 -> ThemeMode.DARK
                            else -> ThemeMode.SYSTEM
                        }
                        onThemeModeChange(next)
                    }
                )
            }
        }

        item {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sincronización periódica",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Actualiza cursos y tareas cada 6 horas vía WorkManager",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = state.preferences.autoSyncEnabled,
                        onCheckedChange = { enabled ->
                            onAutoSyncChange(enabled)
                            if (enabled) UnAppSyncWorker.schedule(context)
                            else UnAppSyncWorker.cancel(context)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alertas de nuevas tareas",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Notifica una sola vez cuando se detecta una tarea nueva",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = state.preferences.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !NotificationHelper(context).hasNotificationPermission()
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onNotificationsChange(enabled)
                            }
                        }
                    )
                }
            }
        }

        item {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = UnAppIcons.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Privacidad y seguridad",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "UnApp opera en modo solo lectura, no almacena contraseñas y cifra las cookies de sesión mediante Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            AppleCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Actualizaciones de UnApp",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Versión instalada: v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Buscar", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        item {
            Button(
                onClick = { showLogoutSheet = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(
                    imageVector = UnAppIcons.LogOut,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar sesión", style = MaterialTheme.typography.labelLarge)
            }
        }
        }
    }

    if (showLogoutSheet) {
        var clearNotes by remember { mutableStateOf(false) }
        AppleBottomSheet(onDismiss = { showLogoutSheet = false }) {
            Text(
                text = "Cerrar sesión institucional",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Se eliminarán las cookies de sesión y la caché académica descargada del Aula Virtual e Intranet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = clearNotes,
                    onCheckedChange = { clearNotes = it }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Eliminar también mis apuntes personales",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showLogoutSheet = false },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        showLogoutSheet = false
                        onLogout(clearNotes)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Salir")
                }
            }
        }
    }
}
