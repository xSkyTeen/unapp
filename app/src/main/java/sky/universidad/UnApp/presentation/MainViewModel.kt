package sky.universidad.UnApp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sky.universidad.UnApp.AppContainer
import sky.universidad.UnApp.BuildConfig
import sky.universidad.UnApp.core.common.AppResult
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.core.network.UnapEndpoints
import sky.universidad.UnApp.core.session.SessionSnapshot
import sky.universidad.UnApp.core.session.SessionStatus
import sky.universidad.UnApp.core.update.AppUpdateInfo
import sky.universidad.UnApp.core.update.AppUpdateManager
import sky.universidad.UnApp.data.preferences.ThemeMode
import sky.universidad.UnApp.data.preferences.UserPreferences
import sky.universidad.UnApp.domain.model.AcademicTask
import sky.universidad.UnApp.domain.model.Announcement
import sky.universidad.UnApp.domain.model.BusRoute
import sky.universidad.UnApp.domain.model.Course
import sky.universidad.UnApp.domain.model.CourseFullDetail
import sky.universidad.UnApp.domain.model.Exam
import sky.universidad.UnApp.domain.model.ForumReplyItem
import sky.universidad.UnApp.domain.model.ForumTopic
import sky.universidad.UnApp.domain.model.HomeworkDetailInfo
import sky.universidad.UnApp.domain.model.InAppAcademicAlert
import sky.universidad.UnApp.domain.model.IntranetOverviewData
import sky.universidad.UnApp.domain.model.PersonalNote
import sky.universidad.UnApp.domain.model.ScheduleSlot
import sky.universidad.UnApp.domain.model.StudentProfile
import sky.universidad.UnApp.domain.model.SyncStatusInfo
import java.io.File

data class MainUiState(
    val session: SessionSnapshot = SessionSnapshot(),
    val isOnline: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val student: StudentProfile? = null,
    val courses: List<Course> = emptyList(),
    val tasks: List<AcademicTask> = emptyList(),
    val forums: List<ForumTopic> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    val schedule: List<ScheduleSlot> = emptyList(),
    val exams: List<Exam> = emptyList(),
    val notes: List<PersonalNote> = emptyList(),
    val busRoutes: List<BusRoute> = emptyList(),
    val syncStatuses: List<SyncStatusInfo> = emptyList(),
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val statusBannerMessage: String? = null,
    val inAppAlert: InAppAcademicAlert? = null,
    val courseDetailsCache: Map<String, CourseFullDetail> = emptyMap(),
    val loadingCourseIds: Set<String> = emptySet(),
    val courseDetailError: String? = null,
    val intranetOverview: IntranetOverviewData? = null,
    val isLoadingIntranet: Boolean = false,
    val intranetError: String? = null,
    val isLoadingCourses: Boolean = false,
    val isLoadingForums: Boolean = false,
    val isLoadingGrades: Boolean = false,
    val isLoadingProfile: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null
) {
    val lastSyncAt: Long
        get() = syncStatuses.maxOfOrNull { it.lastSuccessAt } ?: 0L

    val pendingTaskCount: Int
        get() = tasks.count {
            !it.isSubmittedRemote &&
                DateFormatters.isTaskVisibleInGlobalTab(it.dueTimestamp, it.isEnded)
        }
}

private data class SyncTransientState(
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val statusMessage: String? = null,
    val inAppAlert: InAppAcademicAlert? = null,
    val courseDetailsCache: Map<String, CourseFullDetail> = emptyMap(),
    val loadingCourseIds: Set<String> = emptySet(),
    val courseDetailError: String? = null,
    val intranetOverview: IntranetOverviewData? = null,
    val isLoadingIntranet: Boolean = false,
    val intranetError: String? = null,
    val isLoadingCourses: Boolean = false,
    val isLoadingForums: Boolean = false,
    val isLoadingGrades: Boolean = false,
    val isLoadingProfile: Boolean = false,
    val availableUpdate: AppUpdateInfo? = null
)

class MainViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repo = container.repository
    private val transientState = MutableStateFlow(SyncTransientState())
    private var startupAlertDispatched = false
    private val loadedTiers = mutableSetOf<String>()

    private val academicStreams = combine(
        repo.observeStudent(),
        repo.observeCourses(),
        repo.observeTasks(),
        repo.observeForums(),
        repo.observeAnnouncements()
    ) { student, courses, tasks, forums, announcements ->
        AcademicBundle(student, courses, tasks, forums, announcements)
    }

    private val secondaryStreams = combine(
        repo.observeSchedule(),
        repo.observeExams(),
        repo.observeNotes(),
        repo.observeBusRoutes(),
        repo.observeSyncStatuses()
    ) { schedule, exams, notes, busRoutes, syncStatuses ->
        SecondaryBundle(schedule, exams, notes, busRoutes, syncStatuses)
    }

    val uiState: StateFlow<MainUiState> = combine(
        container.sessionManager.sessionState,
        container.networkMonitor.isOnline,
        container.preferencesRepository.preferences,
        academicStreams,
        secondaryStreams
    ) { session, isOnline, prefs, academic, secondary ->
        Triple(Triple(session, isOnline, prefs), academic, secondary)
    }.combine(transientState) { base, transient ->
        val (core, academic, secondary) = base
        val (session, isOnline, prefs) = core
        MainUiState(
            session = session,
            isOnline = isOnline,
            preferences = prefs,
            student = academic.student,
            courses = academic.courses,
            tasks = academic.tasks,
            forums = academic.forums,
            announcements = academic.announcements,
            schedule = secondary.schedule,
            exams = secondary.exams,
            notes = secondary.notes,
            busRoutes = secondary.busRoutes,
            syncStatuses = secondary.syncStatuses,
            isSyncing = transient.isSyncing,
            syncError = transient.syncError,
            statusBannerMessage = transient.statusMessage,
            inAppAlert = transient.inAppAlert,
            courseDetailsCache = transient.courseDetailsCache,
            loadingCourseIds = transient.loadingCourseIds,
            courseDetailError = transient.courseDetailError,
            intranetOverview = transient.intranetOverview,
            isLoadingIntranet = transient.isLoadingIntranet,
            intranetError = transient.intranetError,
            isLoadingCourses = transient.isLoadingCourses,
            isLoadingForums = transient.isLoadingForums,
            isLoadingGrades = transient.isLoadingGrades,
            isLoadingProfile = transient.isLoadingProfile,
            availableUpdate = transient.availableUpdate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(
            session = container.sessionManager.sessionState.value,
            isOnline = container.networkMonitor.isOnline.value
        )
    )

    init {
        viewModelScope.launch {
            repo.inAppAlerts.collect { alert ->
                transientState.value = transientState.value.copy(inAppAlert = alert)
            }
        }
        viewModelScope.launch {
            academicStreams.collect { bundle ->
                if (!startupAlertDispatched &&
                    container.sessionManager.sessionState.value.status != SessionStatus.UNAUTHENTICATED &&
                    (bundle.tasks.isNotEmpty() || bundle.forums.isNotEmpty())
                ) {
                    startupAlertDispatched = true
                    val activeTasks = bundle.tasks.filter {
                        !it.isSubmittedRemote &&
                            DateFormatters.isTaskVisibleInGlobalTab(it.dueTimestamp, it.isEnded)
                    }
                    val openForums = bundle.forums.filter {
                        DateFormatters.isForumActiveNow(it.closeTimestamp, it.startDateRaw, it.isEnded)
                    }
                    if ((activeTasks.isNotEmpty() || openForums.isNotEmpty()) && transientState.value.inAppAlert == null) {
                        val parts = buildList {
                            if (activeTasks.isNotEmpty()) {
                                add("${activeTasks.size} tarea(s) activa(s)")
                            }
                            if (openForums.isNotEmpty()) {
                                add("${openForums.size} foro(s) abierto(s)")
                            }
                        }
                        val nextTitle = activeTasks.firstOrNull()?.title
                            ?: openForums.firstOrNull()?.title
                            ?: "Revisa tus actividades recientes"
                        val nextCourse = activeTasks.firstOrNull()?.courseName
                            ?: openForums.firstOrNull()?.courseName
                        transientState.value = transientState.value.copy(
                            inAppAlert = InAppAcademicAlert(
                                title = "Eventos próximos · ${parts.joinToString(" · ")}",
                                message = nextTitle,
                                courseName = nextCourse
                            )
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repo.ensureSeededBusRoutes()
            if (container.sessionManager.sessionState.value.status == SessionStatus.AUTHENTICATED &&
                container.networkMonitor.isCurrentlyOnline()
            ) {
                syncNow(notifyNewTasks = true)
            }
            if (BuildConfig.UPDATE_FEED_URL.isNotBlank() && container.networkMonitor.isCurrentlyOnline()) {
                checkForUpdates(manual = false)
            }
        }
    }

    fun checkForUpdates(manual: Boolean = true) {
        val feedUrl = BuildConfig.UPDATE_FEED_URL.trim()
        if (feedUrl.isBlank()) {
            if (manual) {
                transientState.value = transientState.value.copy(
                    statusMessage = "No hay un canal de actualizaciones configurado todavía."
                )
            }
            return
        }
        if (!container.networkMonitor.isCurrentlyOnline()) {
            if (manual) {
                transientState.value = transientState.value.copy(
                    statusMessage = "Conéctate a Internet para buscar actualizaciones."
                )
            }
            return
        }
        viewModelScope.launch {
            val update = AppUpdateManager.checkForUpdate(
                feedUrl = feedUrl,
                currentVersionCode = BuildConfig.VERSION_CODE,
                currentVersionName = BuildConfig.VERSION_NAME
            )
            if (update != null) {
                transientState.value = transientState.value.copy(availableUpdate = update)
            } else if (manual) {
                transientState.value = transientState.value.copy(
                    statusMessage = "Tienes la última versión instalada (v${BuildConfig.VERSION_NAME})."
                )
            }
        }
    }

    fun dismissUpdatePrompt() {
        transientState.value = transientState.value.copy(availableUpdate = null)
    }

    fun login(identifier: String, password: String) {
        if (transientState.value.isSyncing) return
        viewModelScope.launch {
            loadedTiers.clear()
            transientState.value = SyncTransientState(isSyncing = true)
            when (val res = repo.login(identifier, password)) {
                is AppResult.Success -> {
                    loadedTiers += "HOME_PRIORITY"
                    withContext(Dispatchers.Main) {
                        container.cookieJar.syncToWebView(UnapEndpoints.SESSION_ORIGINS)
                    }
                    transientState.value = SyncTransientState(isSyncing = false)
                }
                is AppResult.Error -> {
                    transientState.value = SyncTransientState(
                        isSyncing = false,
                        syncError = res.message
                    )
                }
            }
        }
    }

    /**
     * Keeps the encrypted jar aligned with cookies refreshed while browsing the in-app portal.
     */
    fun importPortalCookies() {
        if (container.sessionManager.sessionState.value.status != SessionStatus.AUTHENTICATED) return
        container.cookieJar.importFromWebView(UnapEndpoints.SESSION_ORIGINS)
    }

    fun syncNow(notifyNewTasks: Boolean = false) {
        if (transientState.value.isSyncing) return
        viewModelScope.launch {
            transientState.value = transientState.value.copy(isSyncing = true, syncError = null)
            when (val res = repo.syncPriorityHome(notifyNewTasks)) {
                is AppResult.Success -> {
                    loadedTiers += "HOME_PRIORITY"
                    val msg = if (res.data > 0) {
                        "Sincronizado: ${res.data} novedad(es) en Aula Virtual."
                    } else null
                    transientState.value = transientState.value.copy(
                        isSyncing = false,
                        statusMessage = msg
                    )
                }
                is AppResult.Error -> {
                    transientState.value = transientState.value.copy(
                        isSyncing = false,
                        syncError = res.message
                    )
                }
            }
        }
    }

    fun ensureCoursesLoaded(forceRefresh: Boolean = false) {
        val status = container.sessionManager.sessionState.value.status
        if (status != SessionStatus.AUTHENTICATED || !container.networkMonitor.isCurrentlyOnline()) return
        if (!forceRefresh && "COURSES" in loadedTiers) return
        if (transientState.value.isLoadingCourses) return

        viewModelScope.launch {
            val activeCourses = uiState.value.courses
            if (activeCourses.isEmpty()) return@launch
            transientState.value = transientState.value.copy(isLoadingCourses = true)
            when (repo.syncCoursesEnrichment(activeCourses)) {
                is AppResult.Success -> loadedTiers += "COURSES"
                is AppResult.Error -> Unit
            }
            transientState.value = transientState.value.copy(isLoadingCourses = false)
        }
    }

    fun ensureForumsLoaded(forceRefresh: Boolean = false) {
        val status = container.sessionManager.sessionState.value.status
        if (status != SessionStatus.AUTHENTICATED || !container.networkMonitor.isCurrentlyOnline()) return
        if (!forceRefresh && "FORUMS" in loadedTiers) return
        if (transientState.value.isLoadingForums) return

        viewModelScope.launch {
            val activeCourses = uiState.value.courses
            transientState.value = transientState.value.copy(isLoadingForums = true)
            when (repo.syncForumsAndAnnouncements(activeCourses, notifyNew = false)) {
                is AppResult.Success -> loadedTiers += "FORUMS"
                is AppResult.Error -> Unit
            }
            transientState.value = transientState.value.copy(isLoadingForums = false)
        }
    }

    fun ensureGradesLoaded(forceRefresh: Boolean = false) {
        val status = container.sessionManager.sessionState.value.status
        if (status != SessionStatus.AUTHENTICATED || !container.networkMonitor.isCurrentlyOnline()) return
        if (!forceRefresh && "GRADES" in loadedTiers) return
        if (transientState.value.isLoadingGrades) return

        viewModelScope.launch {
            val activeCourses = uiState.value.courses
            transientState.value = transientState.value.copy(isLoadingGrades = true)
            when (repo.syncGradesAndExams(activeCourses)) {
                is AppResult.Success -> loadedTiers += "GRADES"
                is AppResult.Error -> Unit
            }
            transientState.value = transientState.value.copy(isLoadingGrades = false)
        }
    }

    fun ensureProfileLoaded(forceRefresh: Boolean = false) {
        val status = container.sessionManager.sessionState.value.status
        if (status != SessionStatus.AUTHENTICATED || !container.networkMonitor.isCurrentlyOnline()) return
        if (!forceRefresh && "PROFILE" in loadedTiers) return
        if (transientState.value.isLoadingProfile) return

        viewModelScope.launch {
            transientState.value = transientState.value.copy(isLoadingProfile = true)
            when (repo.syncStudentProfile(uiState.value.student)) {
                is AppResult.Success -> loadedTiers += "PROFILE"
                is AppResult.Error -> Unit
            }
            transientState.value = transientState.value.copy(isLoadingProfile = false)
        }
    }

    fun loadCourseFullDetail(course: Course, forceRefresh: Boolean = false) {
        val current = transientState.value
        if (!forceRefresh && current.courseDetailsCache.containsKey(course.remoteId)) return
        if (current.loadingCourseIds.contains(course.remoteId)) return

        viewModelScope.launch {
            transientState.value = transientState.value.copy(
                loadingCourseIds = transientState.value.loadingCourseIds + course.remoteId,
                courseDetailError = null
            )
            when (val res = repo.fetchCourseFullDetail(course)) {
                is AppResult.Success -> {
                    transientState.value = transientState.value.copy(
                        courseDetailsCache = transientState.value.courseDetailsCache + (course.remoteId to res.data),
                        loadingCourseIds = transientState.value.loadingCourseIds - course.remoteId
                    )
                }
                is AppResult.Error -> {
                    transientState.value = transientState.value.copy(
                        loadingCourseIds = transientState.value.loadingCourseIds - course.remoteId,
                        courseDetailError = res.message
                    )
                }
            }
        }
    }

    fun loadHomeworkDetail(task: AcademicTask, onResult: (HomeworkDetailInfo?) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.fetchHomeworkDetail(task)) {
                is AppResult.Success -> onResult(res.data)
                is AppResult.Error -> onResult(null)
            }
        }
    }

    fun loadForumDiscussion(forum: ForumTopic, onResult: (String, List<ForumReplyItem>) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.fetchForumDiscussion(forum)) {
                is AppResult.Success -> onResult(res.data.first, res.data.second)
                is AppResult.Error -> onResult(forum.description, emptyList())
            }
        }
    }

    fun loadIntranetSection(sectionKey: String, forceRefresh: Boolean = false) {
        val current = transientState.value
        val tierKey = "INTRA_$sectionKey"
        if (!forceRefresh && tierKey in loadedTiers) return
        if (current.isLoadingIntranet) return

        viewModelScope.launch {
            transientState.value = transientState.value.copy(isLoadingIntranet = true, intranetError = null)
            val activeCourses = uiState.value.courses
            when (val res = repo.fetchIntranetSection(sectionKey, activeCourses, current.intranetOverview)) {
                is AppResult.Success -> {
                    loadedTiers += tierKey
                    transientState.value = transientState.value.copy(
                        intranetOverview = res.data,
                        isLoadingIntranet = false
                    )
                }
                is AppResult.Error -> {
                    transientState.value = transientState.value.copy(
                        isLoadingIntranet = false,
                        intranetError = res.message
                    )
                }
            }
        }
    }

    fun loadIntranetOverview(forceRefresh: Boolean = false) {
        val current = transientState.value
        if (!forceRefresh && current.intranetOverview != null) return
        if (current.isLoadingIntranet) return

        viewModelScope.launch {
            transientState.value = transientState.value.copy(isLoadingIntranet = true, intranetError = null)
            val activeCourses = uiState.value.courses
            when (val res = repo.fetchIntranetOverview(activeCourses)) {
                is AppResult.Success -> {
                    transientState.value = transientState.value.copy(
                        intranetOverview = res.data,
                        isLoadingIntranet = false
                    )
                }
                is AppResult.Error -> {
                    transientState.value = transientState.value.copy(
                        isLoadingIntranet = false,
                        intranetError = res.message
                    )
                }
            }
        }
    }

    fun downloadAuthenticatedFile(url: String, targetDir: File, onResult: (File?, String?) -> Unit) {
        viewModelScope.launch {
            when (val res = repo.downloadAuthenticatedFile(url, targetDir)) {
                is AppResult.Success -> onResult(res.data, null)
                is AppResult.Error -> onResult(null, res.message)
            }
        }
    }

    fun dismissInAppAlert() {
        transientState.value = transientState.value.copy(inAppAlert = null)
    }

    fun toggleTaskCompleted(remoteId: String, completed: Boolean) {
        viewModelScope.launch {
            repo.toggleTaskCompleted(remoteId, completed)
        }
    }

    fun markTaskSeen(remoteId: String) {
        viewModelScope.launch {
            repo.markTaskSeen(remoteId)
        }
    }

    fun saveNote(
        id: Long,
        title: String,
        content: String,
        courseId: String?,
        courseName: String?,
        isPinned: Boolean
    ) {
        viewModelScope.launch {
            repo.saveNote(id, title, content, courseId, courseName, isPinned)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repo.deleteNote(id)
        }
    }

    fun toggleNotePinned(note: PersonalNote) {
        viewModelScope.launch {
            repo.toggleNotePinned(note)
        }
    }

    fun saveManualScheduleSlot(
        courseName: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        classroom: String
    ) {
        viewModelScope.launch {
            repo.saveManualScheduleSlot(courseName, dayOfWeek, startTime, endTime, classroom)
        }
    }

    fun deleteScheduleSlot(remoteId: String) {
        viewModelScope.launch {
            repo.deleteScheduleSlot(remoteId)
        }
    }

    fun toggleBusFavorite(routeId: String, favorite: Boolean) {
        viewModelScope.launch {
            repo.toggleBusFavorite(routeId, favorite)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            container.preferencesRepository.setThemeMode(mode)
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean, onUpdated: (Boolean) -> Unit) {
        viewModelScope.launch {
            container.preferencesRepository.setAutoSyncEnabled(enabled)
            onUpdated(enabled)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setNotificationsEnabled(enabled)
        }
    }

    fun syncCookiesToWebView(urls: List<String>) {
        container.cookieJar.syncToWebView(urls)
    }

    fun clearErrors() {
        container.sessionManager.clearError()
        transientState.value = transientState.value.copy(syncError = null, statusMessage = null)
    }

    fun logout(clearPersonalNotes: Boolean) {
        viewModelScope.launch {
            repo.logout(clearPersonalNotes)
            loadedTiers.clear()
            startupAlertDispatched = false
            transientState.value = SyncTransientState()
        }
    }

    private data class AcademicBundle(
        val student: StudentProfile?,
        val courses: List<Course>,
        val tasks: List<AcademicTask>,
        val forums: List<ForumTopic>,
        val announcements: List<Announcement>
    )

    private data class SecondaryBundle(
        val schedule: List<ScheduleSlot>,
        val exams: List<Exam>,
        val notes: List<PersonalNote>,
        val busRoutes: List<BusRoute>,
        val syncStatuses: List<SyncStatusInfo>
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}
