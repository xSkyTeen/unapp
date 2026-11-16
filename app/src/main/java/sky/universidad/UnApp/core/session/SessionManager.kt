package sky.universidad.UnApp.core.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sky.universidad.UnApp.core.network.UnapEndpoints

/**
 * Central state holder for institutional session lifecycle (ACC-01 .. ACC-08).
 * Never persists passwords; only maintains non-sensitive session status and encrypted cookies.
 */
class SessionManager(
    context: Context,
    private val cookieJar: PersistentCookieJar
) {
    private val prefs = context.getSharedPreferences("unapp_session_meta", Context.MODE_PRIVATE)

    private val _sessionState = MutableStateFlow(restoreInitialState())
    val sessionState: StateFlow<SessionSnapshot> = _sessionState.asStateFlow()

    private fun restoreInitialState(): SessionSnapshot {
        val userIdentifier = prefs.getString(KEY_USER_ID, null)
        val lastValidatedAt = prefs.getLong(KEY_LAST_VALIDATED, 0L)

        val hasAulaCookie = cookieJar.hasCookiesForHost(UnapEndpoints.HOST_AULA_VIRTUAL)
        val hasCampusCookie = cookieJar.hasCookiesForHost(UnapEndpoints.HOST_CAMPUS_SSO)
        return if (hasAulaCookie || hasCampusCookie) {
            SessionSnapshot(
                status = SessionStatus.AUTHENTICATED,
                userIdentifier = userIdentifier,
                lastValidatedAt = lastValidatedAt
            )
        } else {
            SessionSnapshot(status = SessionStatus.UNAUTHENTICATED)
        }
    }

    fun markAuthenticating() {
        _sessionState.value = _sessionState.value.copy(
            status = SessionStatus.AUTHENTICATING,
            errorMessage = null
        )
    }

    fun markAuthenticated(userIdentifier: String?) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_USER_ID, userIdentifier)
            .putLong(KEY_LAST_VALIDATED, now)
            .apply()

        _sessionState.value = SessionSnapshot(
            status = SessionStatus.AUTHENTICATED,
            userIdentifier = userIdentifier,
            lastValidatedAt = now
        )
    }

    fun markExpired(reason: String = "Tu sesión institucional venció. Inicia sesión nuevamente.") {
        _sessionState.value = SessionSnapshot(
            status = SessionStatus.EXPIRED,
            userIdentifier = _sessionState.value.userIdentifier,
            errorMessage = reason
        )
    }

    fun markError(message: String) {
        _sessionState.value = _sessionState.value.copy(
            status = SessionStatus.ERROR,
            errorMessage = message
        )
    }

    fun clearError() {
        val current = _sessionState.value
        if (current.status == SessionStatus.ERROR || current.status == SessionStatus.EXPIRED) {
            _sessionState.value = current.copy(
                status = SessionStatus.UNAUTHENTICATED,
                errorMessage = null
            )
        }
    }

    fun logout() {
        cookieJar.clearAll()
        prefs.edit().clear().apply()
        _sessionState.value = SessionSnapshot(status = SessionStatus.UNAUTHENTICATED)
    }

    private companion object {
        const val KEY_USER_ID = "user_identifier"
        const val KEY_LAST_VALIDATED = "last_validated_at"
    }
}
