package sky.universidad.UnApp

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import sky.universidad.UnApp.core.network.NetworkMonitor
import sky.universidad.UnApp.core.network.OkHttpFactory
import sky.universidad.UnApp.core.security.AndroidKeystoreCipher
import sky.universidad.UnApp.core.session.PersistentCookieJar
import sky.universidad.UnApp.core.session.SessionManager
import sky.universidad.UnApp.core.session.SharedPrefsCookieBackend
import sky.universidad.UnApp.core.sync.NotificationHelper
import sky.universidad.UnApp.core.sync.UnAppSyncWorker
import sky.universidad.UnApp.data.local.database.UnAppDatabase
import sky.universidad.UnApp.data.preferences.UserPreferencesRepository
import sky.universidad.UnApp.data.remote.api.UnapRemoteDataSource
import sky.universidad.UnApp.data.remote.auth.AkdemicAuthClient
import sky.universidad.UnApp.data.repository.AcademicRepository
import sky.universidad.UnApp.data.repository.UnAppAcademicRepository

class AppContainer(context: Application) {
    val cookieJar: PersistentCookieJar by lazy {
        PersistentCookieJar(
            cipher = AndroidKeystoreCipher(),
            backend = SharedPrefsCookieBackend(context)
        )
    }

    val httpClient: OkHttpClient by lazy {
        OkHttpFactory.create(cookieJar)
    }

    val sessionManager: SessionManager by lazy {
        SessionManager(context, cookieJar)
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(context)
    }

    val notificationHelper: NotificationHelper by lazy {
        NotificationHelper(context)
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    val database: UnAppDatabase by lazy {
        UnAppDatabase.build(context)
    }

    private val authClient: AkdemicAuthClient by lazy {
        AkdemicAuthClient(httpClient)
    }

    private val remoteDataSource: UnapRemoteDataSource by lazy {
        UnapRemoteDataSource(httpClient, authClient)
    }

    val repository: AcademicRepository by lazy {
        UnAppAcademicRepository(
            db = database,
            authClient = authClient,
            remote = remoteDataSource,
            sessionManager = sessionManager,
            networkMonitor = networkMonitor,
            notificationHelper = notificationHelper,
            preferencesRepository = preferencesRepository
        )
    }
}

class UnAppApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.ensureChannels()
        appScope.launch {
            container.repository.ensureSeededBusRoutes()
            val prefs = container.preferencesRepository.preferences.first()
            if (prefs.autoSyncEnabled) {
                UnAppSyncWorker.schedule(this@UnAppApplication)
            }
        }
    }
}
