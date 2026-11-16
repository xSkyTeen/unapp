package sky.universidad.UnApp.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isSessionExpired: Boolean = false,
        val isNetworkError: Boolean = false
    ) : AppResult<Nothing>
}

enum class DataFreshness {
    LIVE,
    CACHED,
    DEMO
}
