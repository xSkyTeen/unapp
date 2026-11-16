package sky.universidad.UnApp.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoSyncEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true
)

private val Context.dataStore by preferencesDataStore(name = "unapp_user_preferences")

class UserPreferencesRepository(private val context: Context) {

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val mode = prefs[KEY_THEME_MODE]?.let { raw ->
            runCatching { ThemeMode.valueOf(raw) }.getOrNull()
        } ?: ThemeMode.SYSTEM

        UserPreferences(
            themeMode = mode,
            autoSyncEnabled = prefs[KEY_AUTO_SYNC] ?: true,
            notificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SYNC] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_AUTO_SYNC = booleanPreferencesKey("auto_sync_enabled")
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    }
}
