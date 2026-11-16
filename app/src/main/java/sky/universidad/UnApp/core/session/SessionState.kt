package sky.universidad.UnApp.core.session

enum class SessionStatus {
    UNAUTHENTICATED,
    AUTHENTICATING,
    AUTHENTICATED,
    EXPIRED,
    ERROR
}

data class SessionSnapshot(
    val status: SessionStatus = SessionStatus.UNAUTHENTICATED,
    val userIdentifier: String? = null,
    val errorMessage: String? = null,
    val lastValidatedAt: Long = 0L
) {
    val hasActiveAccess: Boolean
        get() = status == SessionStatus.AUTHENTICATED
}
