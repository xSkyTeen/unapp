package sky.universidad.UnApp.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UnapEndpoints {
    const val BASE_AULA_VIRTUAL = "https://aulavirtual2.unap.edu.pe"
    const val BASE_INTRANET = "https://intranet.unap.edu.pe"
    const val BASE_CAMPUS_SSO = "https://campus.unap.edu.pe"

    const val HOST_AULA_VIRTUAL = "aulavirtual2.unap.edu.pe"
    const val HOST_INTRANET = "intranet.unap.edu.pe"
    const val HOST_CAMPUS_SSO = "campus.unap.edu.pe"

    // Verified Akdemic Aula Virtual endpoints
    const val PATH_COURSES = "/web/user/info/system/courseinrole"
    const val PATH_HOMEWORK_LIST = "/web/homework/list"
    const val PATH_FORUM_LIST = "/web/forum/list"

    // Candidate Akdemic Aula Virtual & Intranet endpoints with graceful fallback
    val ANNOUNCEMENT_CANDIDATES = listOf(
        "/web/announcement/list",
        "/web/user/announcements",
        "/web/notification/list"
    )

    // Candidate Intranet endpoints for enrolled courses (JSON API + HTML academic views)
    val INTRANET_COURSE_CANDIDATES = listOf(
        "/api/student/courses",
        "/alumno/cursos",
        "/matricula/cursos",
        "/estudiantes/cursos"
    )

    /** Origins whose cookies make up the institutional session (Campus SSO + both portals). */
    val SESSION_ORIGINS = listOf(BASE_CAMPUS_SSO, BASE_AULA_VIRTUAL, BASE_INTRANET)

    private const val INSTITUTIONAL_DOMAIN = "unap.edu.pe"

    fun isInstitutionalHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == INSTITUTIONAL_DOMAIN || h.endsWith(".$INSTITUTIONAL_DOMAIN")
    }
}

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = checkCurrentConnectivity()
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkCurrentConnectivity()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            _isOnline.value = checkCurrentConnectivity()
        }
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, callback)
        }
    }

    fun isCurrentlyOnline(): Boolean {
        val online = checkCurrentConnectivity()
        _isOnline.value = online
        return online
    }

    private fun checkCurrentConnectivity(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
