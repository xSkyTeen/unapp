package sky.universidad.UnApp.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import sky.universidad.UnApp.core.common.AppResult
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.core.network.NetworkMonitor
import sky.universidad.UnApp.core.session.SessionManager
import sky.universidad.UnApp.core.session.SessionStatus
import sky.universidad.UnApp.core.sync.NotificationHelper
import sky.universidad.UnApp.data.local.database.UnAppDatabase
import sky.universidad.UnApp.data.local.database.UnapBusRoutes
import sky.universidad.UnApp.data.local.database.toDomain
import sky.universidad.UnApp.data.local.entities.NoteEntity
import sky.universidad.UnApp.data.local.entities.ScheduleEntity
import sky.universidad.UnApp.data.local.entities.SyncEntity
import sky.universidad.UnApp.data.preferences.UserPreferencesRepository
import sky.universidad.UnApp.data.remote.api.UnapRemoteDataSource
import sky.universidad.UnApp.data.remote.auth.AkdemicAuthClient
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
import java.util.UUID

interface AcademicRepository {
    fun observeStudent(): Flow<StudentProfile?>
    fun observeCourses(): Flow<List<Course>>
    fun observeTasks(): Flow<List<AcademicTask>>
    fun observeForums(): Flow<List<ForumTopic>>
    fun observeAnnouncements(): Flow<List<Announcement>>
    fun observeSchedule(): Flow<List<ScheduleSlot>>
    fun observeExams(): Flow<List<Exam>>
    fun observeNotes(): Flow<List<PersonalNote>>
    fun observeBusRoutes(): Flow<List<BusRoute>>
    fun observeSyncStatuses(): Flow<List<SyncStatusInfo>>
    val inAppAlerts: SharedFlow<InAppAcademicAlert>

    suspend fun ensureSeededBusRoutes()
    suspend fun login(username: String, password: String): AppResult<Unit>
    suspend fun syncAll(notifyNewTasks: Boolean): AppResult<Int>
    suspend fun syncPriorityHome(notifyNewTasks: Boolean): AppResult<Int>
    suspend fun syncCoursesEnrichment(courses: List<Course>): AppResult<Unit>
    suspend fun syncForumsAndAnnouncements(courses: List<Course>, notifyNew: Boolean): AppResult<Int>
    suspend fun syncGradesAndExams(courses: List<Course>): AppResult<Unit>
    suspend fun syncStudentProfile(currentStudent: StudentProfile?): AppResult<Unit>
    suspend fun fetchCourseFullDetail(course: Course): AppResult<CourseFullDetail>
    suspend fun fetchHomeworkDetail(task: AcademicTask): AppResult<HomeworkDetailInfo>
    suspend fun fetchForumDiscussion(forum: ForumTopic): AppResult<Pair<String, List<ForumReplyItem>>>
    suspend fun fetchIntranetOverview(activeCourses: List<Course>): AppResult<IntranetOverviewData>
    suspend fun fetchIntranetSection(
        sectionKey: String,
        activeCourses: List<Course>,
        currentOverview: IntranetOverviewData?
    ): AppResult<IntranetOverviewData>
    suspend fun downloadAuthenticatedFile(url: String, targetDir: File): AppResult<File>
    suspend fun toggleTaskCompleted(remoteId: String, completed: Boolean)
    suspend fun markTaskSeen(remoteId: String)
    suspend fun saveNote(id: Long, title: String, content: String, courseId: String?, courseName: String?, isPinned: Boolean)
    suspend fun deleteNote(id: Long)
    suspend fun toggleNotePinned(note: PersonalNote)
    suspend fun saveManualScheduleSlot(courseName: String, dayOfWeek: Int, startTime: String, endTime: String, classroom: String)
    suspend fun deleteScheduleSlot(remoteId: String)
    suspend fun toggleBusFavorite(routeId: String, favorite: Boolean)
    suspend fun logout(clearPersonalNotes: Boolean)
}

class UnAppAcademicRepository(
    private val db: UnAppDatabase,
    private val authClient: AkdemicAuthClient,
    private val remote: UnapRemoteDataSource,
    private val sessionManager: SessionManager,
    private val networkMonitor: NetworkMonitor,
    private val notificationHelper: NotificationHelper,
    private val preferencesRepository: UserPreferencesRepository
) : AcademicRepository {

    private val _inAppAlerts = MutableSharedFlow<InAppAcademicAlert>(extraBufferCapacity = 4)
    override val inAppAlerts: SharedFlow<InAppAcademicAlert> = _inAppAlerts.asSharedFlow()

    override fun observeStudent(): Flow<StudentProfile?> =
        db.studentDao().observeStudent().map { it?.toDomain() }

    override fun observeCourses(): Flow<List<Course>> = combine(
        db.courseDao().observeAll(),
        db.taskDao().observeAll()
    ) { courses, tasks ->
        val pendingByCourse = tasks
            .filter {
                !it.isSubmittedRemote &&
                    DateFormatters.isTaskVisibleInGlobalTab(it.dueTimestamp, it.isEnded)
            }
            .groupingBy { it.courseId }
            .eachCount()
        courses.map { it.toDomain(pendingTasksCount = pendingByCourse[it.remoteId] ?: 0) }
    }

    override fun observeTasks(): Flow<List<AcademicTask>> =
        db.taskDao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeForums(): Flow<List<ForumTopic>> =
        db.forumDao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeAnnouncements(): Flow<List<Announcement>> =
        db.announcementDao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSchedule(): Flow<List<ScheduleSlot>> =
        db.scheduleDao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeExams(): Flow<List<Exam>> =
        db.examDao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeNotes(): Flow<List<PersonalNote>> =
        db.noteDao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeBusRoutes(): Flow<List<BusRoute>> =
        db.busRouteDao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeSyncStatuses(): Flow<List<SyncStatusInfo>> =
        db.syncDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun ensureSeededBusRoutes() {
        if (db.busRouteDao().count() == 0) {
            db.busRouteDao().upsertAll(UnapBusRoutes.defaultBusRoutes())
        }
    }

    override suspend fun login(username: String, password: String): AppResult<Unit> {
        val cleanUser = username.trim()
        sessionManager.markAuthenticating()
        return when (val outcome = authClient.loginWithCredentials(cleanUser, password)) {
            is AppResult.Success -> {
                val resolvedUser = outcome.data.userIdentifier.ifBlank { cleanUser }
                val previousUser = sessionManager.sessionState.value.userIdentifier
                if (previousUser != null && previousUser != resolvedUser) {
                    db.clearAcademicCache(clearPersonalNotes = false)
                }
                sessionManager.markAuthenticated(resolvedUser)
                syncPriorityHome(notifyNewTasks = false)
                AppResult.Success(Unit)
            }
            is AppResult.Error -> {
                sessionManager.markError(outcome.message)
                outcome
            }
        }
    }

    override suspend fun syncAll(notifyNewTasks: Boolean): AppResult<Int> =
        executeSyncSnapshot(notifyNewTasks = notifyNewTasks) { userId ->
            remote.fetchFullAcademicSnapshot(userId)
        }

    override suspend fun syncPriorityHome(notifyNewTasks: Boolean): AppResult<Int> =
        executeSyncSnapshot(notifyNewTasks = notifyNewTasks) { userId ->
            remote.fetchHomePrioritySnapshot(userId)
        }

    private suspend fun executeSyncSnapshot(
        notifyNewTasks: Boolean,
        fetcher: suspend (String) -> AppResult<sky.universidad.UnApp.data.remote.api.RemoteSyncPayload>
    ): AppResult<Int> {
        ensureSeededBusRoutes()
        val snapshot = sessionManager.sessionState.value
        if (snapshot.status != SessionStatus.AUTHENTICATED) {
            return AppResult.Error("Sesión no autenticada.", isSessionExpired = true)
        }
        if (!networkMonitor.isCurrentlyOnline()) {
            return AppResult.Error(
                message = "Sin conexión a Internet. Mostrando datos almacenados.",
                isNetworkError = true
            )
        }

        val userId = snapshot.userIdentifier.orEmpty()
        return when (val remoteResult = fetcher(userId)) {
            is AppResult.Success -> {
                val payload = remoteResult.data
                val now = System.currentTimeMillis()

                val (newlyInsertedTasks, newlyInsertedForums) = db.withTransaction {
                    payload.student?.let { incomingStudent ->
                        val prev = db.studentDao().getStudent()
                        val mergedStudent = if (prev != null) {
                            incomingStudent.copy(
                                code = incomingStudent.code.ifBlank { prev.code },
                                dni = incomingStudent.dni.ifBlank { prev.dni },
                                semester = incomingStudent.semester.ifBlank { prev.semester },
                                faculty = incomingStudent.faculty.ifBlank { prev.faculty }
                            )
                        } else incomingStudent
                        db.studentDao().upsert(mergedStudent)
                    }
                    val prevTeachers = db.courseDao().getAll().associate { it.remoteId to it.teacher }
                    val mergedCourses = payload.courses.map { c ->
                        if (c.teacher.isBlank()) {
                            c.copy(teacher = prevTeachers[c.remoteId].orEmpty())
                        } else c
                    }
                    db.courseDao().replaceAllIfNonEmpty(mergedCourses)

                    val insertedTasks = db.taskDao().syncTasksTransactionally(
                        incoming = payload.tasks,
                        notifyOnInsert = notifyNewTasks
                    )
                    val insertedForums = if (payload.forums.isNotEmpty()) {
                        db.forumDao().syncForumsTransactionally(
                            incoming = payload.forums,
                            notifyOnInsert = notifyNewTasks
                        )
                    } else {
                        emptyList()
                    }
                    if (payload.announcements.isNotEmpty()) {
                        db.announcementDao().upsertAll(payload.announcements)
                    }
                    db.scheduleDao().replaceRemoteSlotsIfNonEmpty(payload.schedules)
                    if (payload.exams.isNotEmpty()) {
                        db.examDao().upsertAll(payload.exams)
                    }
                    db.syncDao().upsert(
                        SyncEntity(
                            module = "academico",
                            lastSuccessAt = now,
                            status = "OK",
                            message = "${payload.courses.size} cursos, ${payload.tasks.size} tareas"
                        )
                    )
                    insertedTasks to insertedForums
                }

                val newlyDiscoveredCount = newlyInsertedTasks.size + newlyInsertedForums.size
                if (notifyNewTasks && newlyDiscoveredCount > 0) {
                    val prefs = preferencesRepository.preferences.first()
                    if (prefs.notificationsEnabled) {
                        notificationHelper.notifyAcademicUpdates(newlyInsertedTasks, newlyInsertedForums)
                    }
                    val firstTitle = newlyInsertedTasks.firstOrNull()?.let { "Nueva tarea: ${it.title}" }
                        ?: newlyInsertedForums.firstOrNull()?.let { "Nuevo foro: ${it.title}" }
                        ?: "Novedad en Aula Virtual"
                    val firstCourse = newlyInsertedTasks.firstOrNull()?.courseName
                        ?: newlyInsertedForums.firstOrNull()?.courseName
                    _inAppAlerts.tryEmit(
                        InAppAcademicAlert(
                            title = if (newlyDiscoveredCount == 1) "Actualización en curso" else "$newlyDiscoveredCount novedades en cursos",
                            message = firstTitle,
                            courseName = firstCourse
                        )
                    )
                }
                sessionManager.markAuthenticated(snapshot.userIdentifier)
                AppResult.Success(newlyDiscoveredCount)
            }

            is AppResult.Error -> {
                if (remoteResult.isSessionExpired) {
                    sessionManager.markExpired(remoteResult.message)
                }
                db.syncDao().upsert(
                    SyncEntity(
                        module = "academico",
                        lastSuccessAt = db.syncDao().findByModule("academico")?.lastSuccessAt ?: 0L,
                        status = if (remoteResult.isNetworkError) "OFFLINE" else "ERROR",
                        message = remoteResult.message
                    )
                )
                remoteResult
            }
        }
    }

    override suspend fun syncCoursesEnrichment(courses: List<Course>): AppResult<Unit> {
        val snapshot = sessionManager.sessionState.value
        if (snapshot.status != SessionStatus.AUTHENTICATED || !networkMonitor.isCurrentlyOnline() || courses.isEmpty()) {
            return AppResult.Success(Unit)
        }
        return when (val res = remote.fetchCoursesTeachersEnrichment(courses)) {
            is AppResult.Success -> {
                val now = System.currentTimeMillis()
                db.withTransaction {
                    res.data.forEach { (remoteId, teacher) ->
                        db.courseDao().updateTeacher(remoteId, teacher, now)
                    }
                }
                AppResult.Success(Unit)
            }
            is AppResult.Error -> res
        }
    }

    override suspend fun syncForumsAndAnnouncements(courses: List<Course>, notifyNew: Boolean): AppResult<Int> {
        val snapshot = sessionManager.sessionState.value
        if (snapshot.status != SessionStatus.AUTHENTICATED || !networkMonitor.isCurrentlyOnline()) {
            return AppResult.Success(0)
        }
        return when (val res = remote.fetchForumsAndAnnouncementsSnapshot(courses)) {
            is AppResult.Success -> {
                val (forums, announcements) = res.data
                val newlyInserted = db.withTransaction {
                    val inserted = if (forums.isNotEmpty()) {
                        db.forumDao().syncForumsTransactionally(forums, notifyOnInsert = notifyNew)
                    } else emptyList()
                    if (announcements.isNotEmpty()) {
                        db.announcementDao().upsertAll(announcements)
                    }
                    inserted
                }
                AppResult.Success(newlyInserted.size)
            }
            is AppResult.Error -> res
        }
    }

    override suspend fun syncGradesAndExams(courses: List<Course>): AppResult<Unit> {
        val snapshot = sessionManager.sessionState.value
        if (snapshot.status != SessionStatus.AUTHENTICATED || !networkMonitor.isCurrentlyOnline()) {
            return AppResult.Success(Unit)
        }
        return when (val res = remote.fetchGradesAndExamsSnapshot(courses)) {
            is AppResult.Success -> {
                val (exams, termName) = res.data
                db.withTransaction {
                    if (exams.isNotEmpty()) {
                        db.examDao().upsertAll(exams)
                    }
                    if (termName.isNotBlank()) {
                        db.studentDao().getStudent()?.let { existing ->
                            if (existing.semester.isBlank()) {
                                db.studentDao().upsert(existing.copy(semester = termName))
                            }
                        }
                    }
                }
                AppResult.Success(Unit)
            }
            is AppResult.Error -> res
        }
    }

    override suspend fun syncStudentProfile(currentStudent: StudentProfile?): AppResult<Unit> {
        val snapshot = sessionManager.sessionState.value
        if (snapshot.status != SessionStatus.AUTHENTICATED || !networkMonitor.isCurrentlyOnline()) {
            return AppResult.Success(Unit)
        }
        val userId = snapshot.userIdentifier.orEmpty()
        return when (val res = remote.fetchStudentProfileDetail(userId, currentStudent)) {
            is AppResult.Success -> {
                db.studentDao().upsert(res.data)
                AppResult.Success(Unit)
            }
            is AppResult.Error -> res
        }
    }

    override suspend fun fetchCourseFullDetail(course: Course): AppResult<CourseFullDetail> {
        if (!networkMonitor.isCurrentlyOnline()) {
            return AppResult.Error(
                message = "Sin conexión para cargar módulos detallados.",
                isNetworkError = true
            )
        }
        return remote.fetchCourseFullDetail(course)
    }

    override suspend fun fetchHomeworkDetail(task: AcademicTask): AppResult<HomeworkDetailInfo> {
        if (!networkMonitor.isCurrentlyOnline() || task.homeworkId.isBlank()) {
            return AppResult.Success(
                HomeworkDetailInfo(
                    id = task.homeworkId,
                    title = task.title,
                    description = task.description,
                    dateBegin = task.startDateRaw.orEmpty(),
                    dateEnd = task.dueDateRaw.orEmpty(),
                    attempts = 0,
                    fileFilter = "",
                    isGroup = false
                )
            )
        }
        return remote.fetchHomeworkDetail(task.homeworkId)
    }

    override suspend fun fetchForumDiscussion(forum: ForumTopic): AppResult<Pair<String, List<ForumReplyItem>>> {
        if (!networkMonitor.isCurrentlyOnline() || forum.rawForumId.isBlank()) {
            return AppResult.Success(forum.description to emptyList())
        }
        return remote.fetchForumDiscussion(forum.rawForumId)
    }

    override suspend fun fetchIntranetOverview(activeCourses: List<Course>): AppResult<IntranetOverviewData> {
        if (!networkMonitor.isCurrentlyOnline()) {
            return AppResult.Error(
                message = "Sin conexión para consultar la Intranet UNAP.",
                isNetworkError = true
            )
        }
        return remote.fetchIntranetOverview(activeCourses)
    }

    override suspend fun fetchIntranetSection(
        sectionKey: String,
        activeCourses: List<Course>,
        currentOverview: IntranetOverviewData?
    ): AppResult<IntranetOverviewData> {
        if (!networkMonitor.isCurrentlyOnline()) {
            return AppResult.Error(
                message = "Sin conexión para consultar la Intranet UNAP.",
                isNetworkError = true
            )
        }
        return remote.fetchIntranetSection(sectionKey, activeCourses, currentOverview)
    }

    override suspend fun downloadAuthenticatedFile(url: String, targetDir: File): AppResult<File> {
        if (!networkMonitor.isCurrentlyOnline()) {
            return AppResult.Error("Sin conexión para descargar este archivo.", isNetworkError = true)
        }
        return remote.downloadAuthenticatedFile(url, targetDir)
    }

    override suspend fun toggleTaskCompleted(remoteId: String, completed: Boolean) {
        db.taskDao().setLocalCompleted(remoteId, completed)
    }

    override suspend fun markTaskSeen(remoteId: String) {
        db.taskDao().markSeen(remoteId)
    }

    override suspend fun saveNote(
        id: Long,
        title: String,
        content: String,
        courseId: String?,
        courseName: String?,
        isPinned: Boolean
    ) {
        val now = System.currentTimeMillis()
        val existingCreated = if (id > 0L) {
            db.noteDao().observeAll().first().firstOrNull { it.id == id }?.createdAt ?: now
        } else {
            now
        }
        db.noteDao().upsert(
            NoteEntity(
                id = id,
                title = title.trim(),
                content = content.trim(),
                courseId = courseId,
                courseName = courseName,
                isPinned = isPinned,
                createdAt = existingCreated,
                updatedAt = now
            )
        )
    }

    override suspend fun deleteNote(id: Long) {
        db.noteDao().deleteById(id)
    }

    override suspend fun toggleNotePinned(note: PersonalNote) {
        db.noteDao().upsert(
            NoteEntity(
                id = note.id,
                title = note.title,
                content = note.content,
                courseId = note.courseId,
                courseName = note.courseName,
                isPinned = !note.isPinned,
                createdAt = note.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun saveManualScheduleSlot(
        courseName: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        classroom: String
    ) {
        val now = System.currentTimeMillis()
        db.scheduleDao().upsert(
            ScheduleEntity(
                remoteId = "manual_${UUID.randomUUID()}",
                courseId = "manual",
                courseName = courseName.trim(),
                dayOfWeek = dayOfWeek,
                startTime = startTime.trim(),
                endTime = endTime.trim(),
                classroom = classroom.trim(),
                teacher = "",
                slotType = "Personal",
                isManual = true,
                lastUpdated = now
            )
        )
    }

    override suspend fun deleteScheduleSlot(remoteId: String) {
        db.scheduleDao().deleteByRemoteId(remoteId)
    }

    override suspend fun toggleBusFavorite(routeId: String, favorite: Boolean) {
        db.busRouteDao().setFavorite(routeId, favorite)
    }

    override suspend fun logout(clearPersonalNotes: Boolean) {
        sessionManager.logout()
        db.clearAcademicCache(clearPersonalNotes = clearPersonalNotes)
    }
}
