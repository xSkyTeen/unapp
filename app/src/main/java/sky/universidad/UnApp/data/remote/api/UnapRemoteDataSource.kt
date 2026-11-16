package sky.universidad.UnApp.data.remote.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import sky.universidad.UnApp.core.common.AppResult
import sky.universidad.UnApp.core.common.CourseColorPalette
import sky.universidad.UnApp.core.network.UnapEndpoints
import sky.universidad.UnApp.data.local.entities.AnnouncementEntity
import sky.universidad.UnApp.data.local.entities.CourseEntity
import sky.universidad.UnApp.data.local.entities.ExamEntity
import sky.universidad.UnApp.data.local.entities.ForumEntity
import sky.universidad.UnApp.data.local.entities.ScheduleEntity
import sky.universidad.UnApp.data.local.entities.StudentEntity
import sky.universidad.UnApp.data.local.entities.TaskEntity
import sky.universidad.UnApp.data.remote.auth.AkdemicAuthClient
import sky.universidad.UnApp.data.remote.parser.AkdemicParsers
import sky.universidad.UnApp.domain.model.AcademicHistorySemester
import sky.universidad.UnApp.domain.model.Course
import sky.universidad.UnApp.domain.model.CourseFullDetail
import sky.universidad.UnApp.domain.model.ForumReplyItem
import sky.universidad.UnApp.domain.model.HomeworkDetailInfo
import sky.universidad.UnApp.domain.model.IntranetOverviewData
import sky.universidad.UnApp.domain.model.StudentProfile
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class RemoteSyncPayload(
    val student: StudentEntity?,
    val courses: List<CourseEntity>,
    val tasks: List<TaskEntity>,
    val forums: List<ForumEntity>,
    val announcements: List<AnnouncementEntity>,
    val schedules: List<ScheduleEntity>,
    val exams: List<ExamEntity>
)

/**
 * Fetches academic records from UNAP Aula Virtual (`aulavirtual2.unap.edu.pe`)
 * and Intranet (`intranet.unap.edu.pe`), isolating remote protocol details from repositories.
 */
class UnapRemoteDataSource(
    private val httpClient: OkHttpClient,
    private val authClient: AkdemicAuthClient,
    private val aulaVirtualBaseUrl: String = UnapEndpoints.BASE_AULA_VIRTUAL,
    private val intranetBaseUrl: String = UnapEndpoints.BASE_INTRANET
) {

    private suspend fun fetchAuthenticatedCoursesRaw(): AppResult<Pair<String, List<CourseEntity>>> {
        val now = System.currentTimeMillis()
        val coursesUrl = "${aulaVirtualBaseUrl.trimEnd('/')}${UnapEndpoints.PATH_COURSES}"
        var coursesResponse = fetchWithStatus(coursesUrl)
            ?: return AppResult.Error(
                message = "No fue posible consultar tus cursos.",
                isNetworkError = true
            )

        if (coursesResponse.indicatesExpiredSession()) {
            val renewed = runCatching { authClient.completeSilentSso(aulaVirtualBaseUrl) }
                .getOrDefault(false)
            if (renewed) {
                coursesResponse = fetchWithStatus(coursesUrl) ?: coursesResponse
            }
        }

        if (coursesResponse.indicatesExpiredSession()) {
            return AppResult.Error(
                message = "Tu sesión institucional expiró.",
                isSessionExpired = true
            )
        }

        val coursesRaw = coursesResponse.body
        if (!coursesResponse.isSuccessful) {
            return AppResult.Error(
                message = "El Aula Virtual respondió con el código ${coursesResponse.code}.",
                isNetworkError = coursesResponse.code >= 500
            )
        }

        val aulaCourses = AkdemicParsers.parseCoursesJson(coursesRaw, now)
        if (aulaCourses.isEmpty() && !coursesRaw.trim().startsWith("[")) {
            return AppResult.Error(
                message = "Respuesta inesperada al consultar cursos."
            )
        }
        return AppResult.Success(coursesRaw to aulaCourses)
    }

    private fun fetchIntranetSchedule(now: Long): List<ScheduleEntity> {
        val (weekStart, weekEnd) = currentWeekIsoBounds(now)
        val intraBase = intranetBaseUrl.trimEnd('/')
        return listOf(
            "$intraBase/alumno/horario-semana/get?start=$weekStart&end=$weekEnd" to true,
            "$intraBase/alumno/horario-ciclo/get?start=$weekStart&end=$weekEnd" to true,
            "$intraBase/api/student/schedule" to true,
            "$intraBase/horario" to false,
            "$intraBase/estudiantes/horario" to false
        ).firstNotNullOfOrNull { (url, isAjax) ->
            val body = fetchString(url, isAjax = isAjax)?.takeIf { !looksLikeLoginRedirect(it) }
            body?.let { AkdemicParsers.parseSchedulePayload(it, now) }?.takeIf { it.isNotEmpty() }
        }.orEmpty()
    }

    private fun linkSchedulesToCourses(
        schedules: List<ScheduleEntity>,
        courses: List<CourseEntity>
    ): List<ScheduleEntity> {
        val courseByCodeNorm = courses.associateBy { CourseColorPalette.normalizeCode(it.code, "") }
        val courseByNameNorm = courses.associateBy { CourseColorPalette.normalizeCode(null, it.name) }
        return schedules.map { slot ->
            val matched = courseByCodeNorm[CourseColorPalette.normalizeCode(slot.courseId, "")]
                ?: courseByNameNorm[CourseColorPalette.normalizeCode(null, slot.courseName)]
            if (matched != null) {
                slot.copy(
                    courseId = matched.remoteId,
                    teacher = slot.teacher.ifBlank { matched.teacher }
                )
            } else {
                slot
            }
        }
    }

    /**
     * Nivel 1 (Prioridad Principal): Carga únicamente cursos base, tareas en paralelo,
     * horario de Intranet y nombre básico para mostrar Inicio, Tareas y Horario al instante.
     */
    suspend fun fetchHomePrioritySnapshot(userIdentifier: String): AppResult<RemoteSyncPayload> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val coursesOutcome = fetchAuthenticatedCoursesRaw()
                val (coursesRaw, aulaCourses) = when (coursesOutcome) {
                    is AppResult.Success -> coursesOutcome.data
                    is AppResult.Error -> return@withContext coursesOutcome
                }

                val avBase = aulaVirtualBaseUrl.trimEnd('/')
                val intraBase = intranetBaseUrl.trimEnd('/')

                coroutineScope {
                    val tasksDeferred = aulaCourses.map { course ->
                        async {
                            fetchString("$avBase${UnapEndpoints.PATH_HOMEWORK_LIST}?s=${course.sectionId}")
                                ?.takeIf { !looksLikeLoginRedirect(it) }
                                ?.let { AkdemicParsers.parseHomeworkJson(it, course.remoteId, course.name, now) }
                                .orEmpty()
                        }
                    }

                    val intranetBundleDeferred = async {
                        runCatching { authClient.completeSilentSso(intranetBaseUrl) }
                        val homeHtml = fetchString("$intraBase/")?.takeIf { !looksLikeLoginRedirect(it) }
                        val rawStudent = homeHtml?.let {
                            AkdemicParsers.parseStudentProfile(it, userIdentifier, now)
                        }
                        val schedules = fetchIntranetSchedule(now)
                        rawStudent to schedules
                    }

                    val tasks = tasksDeferred.awaitAll().flatten()
                    val (rawStudent, schedules) = intranetBundleDeferred.await()

                    val mergedCourses = AkdemicParsers.mergeCoursesByCodeAndAssignColors(
                        aulaCourses = aulaCourses,
                        intranetCourses = emptyList(),
                        schedules = schedules,
                        nowMillis = now
                    )
                    val deptAndFaculty = AkdemicParsers.extractDepartmentAndFacultyFromCoursesJson(coursesRaw)
                    val student = (rawStudent ?: if (userIdentifier.isNotBlank()) {
                        StudentEntity(
                            id = userIdentifier,
                            code = "",
                            dni = "",
                            fullName = userIdentifier,
                            career = deptAndFaculty?.first ?: "UNAP Puno",
                            faculty = deptAndFaculty?.second.orEmpty(),
                            semester = "",
                            lastUpdated = now
                        )
                    } else null)?.let { s ->
                        s.copy(
                            career = if (s.career == "UNAP Puno" && !deptAndFaculty?.first.isNullOrBlank()) {
                                deptAndFaculty!!.first
                            } else s.career,
                            faculty = s.faculty.ifBlank { deptAndFaculty?.second.orEmpty() }
                        )
                    }

                    AppResult.Success(
                        RemoteSyncPayload(
                            student = student,
                            courses = mergedCourses,
                            tasks = tasks,
                            forums = emptyList(),
                            announcements = emptyList(),
                            schedules = linkSchedulesToCourses(schedules, mergedCourses),
                            exams = emptyList()
                        )
                    )
                }
            } catch (io: IOException) {
                AppResult.Error(
                    message = "Sin conexión estable con los servidores universitarios.",
                    cause = io,
                    isNetworkError = true
                )
            } catch (t: Throwable) {
                AppResult.Error(
                    message = "No se pudo procesar la respuesta académica: ${t.message ?: "error desconocido"}",
                    cause = t
                )
            }
        }

    /**
     * Nivel 2 (Pestaña Cursos): Enriquecimiento paralelo de docentes por sección.
     */
    suspend fun fetchCoursesTeachersEnrichment(courses: List<Course>): AppResult<Map<String, String>> =
        withContext(Dispatchers.IO) {
            try {
                val avBase = aulaVirtualBaseUrl.trimEnd('/')
                val map = coroutineScope {
                    courses.map { course ->
                        async {
                            val raw = fetchString(
                                "$avBase/web/section/list/teachersbysection?s=${course.sectionId}",
                                isAjax = true
                            )?.takeIf { !looksLikeLoginRedirect(it) }
                            val names = raw?.let { AkdemicParsers.parseTeachersBySectionJson(it) }.orEmpty()
                            if (names.isNotEmpty()) course.remoteId to names.joinToString(" / ") else null
                        }
                    }.awaitAll().filterNotNull().toMap()
                }
                AppResult.Success(map)
            } catch (io: IOException) {
                AppResult.Error("Sin conexión al consultar docentes.", cause = io, isNetworkError = true)
            } catch (t: Throwable) {
                AppResult.Error("No se pudieron cargar los docentes.", cause = t)
            }
        }

    /**
     * Nivel 3 (Foros y Avisos): Carga únicamente foros por curso y comunicados institucionales.
     */
    suspend fun fetchForumsAndAnnouncementsSnapshot(
        courses: List<Course>
    ): AppResult<Pair<List<ForumEntity>, List<AnnouncementEntity>>> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val avBase = aulaVirtualBaseUrl.trimEnd('/')
                val intraBase = intranetBaseUrl.trimEnd('/')

                coroutineScope {
                    val forumsDeferred = courses.map { course ->
                        async {
                            fetchString("$avBase${UnapEndpoints.PATH_FORUM_LIST}?s=${course.sectionId}")
                                ?.takeIf { !looksLikeLoginRedirect(it) }
                                ?.let { AkdemicParsers.parseForumJson(it, course.remoteId, course.name, now) }
                                .orEmpty()
                        }
                    }

                    val courseAnnDeferred = courses.map { course ->
                        async {
                            fetchString("$avBase/web/announcement/list?s=${course.sectionId}")
                                ?.takeIf { !looksLikeLoginRedirect(it) && it.trim().startsWith("[") }
                                ?.let {
                                    AkdemicParsers.parseAnnouncementsJson(
                                        it, course.remoteId, course.name, "aulavirtual", now
                                    )
                                }.orEmpty()
                        }
                    }

                    val generalAnnDeferred = async {
                        val list = mutableListOf<AnnouncementEntity>()
                        for (path in UnapEndpoints.ANNOUNCEMENT_CANDIDATES) {
                            val body = fetchString("$avBase$path") ?: continue
                            if (!looksLikeLoginRedirect(body)) {
                                if (body.trim().startsWith("[") || body.trim().startsWith("{")) {
                                    list += AkdemicParsers.parseAnnouncementsJson(
                                        body, "general", "Aula Virtual", "aulavirtual", now
                                    )
                                } else {
                                    list += AkdemicParsers.parseAnnouncementsFromHtml(body, "aulavirtual", now)
                                }
                                if (list.isNotEmpty()) break
                            }
                        }
                        val intraHome = fetchString("$intraBase/")?.takeIf { !looksLikeLoginRedirect(it) }
                        if (intraHome != null) {
                            list += AkdemicParsers.parseAnnouncementsFromHtml(intraHome, "intranet", now)
                        }
                        list
                    }

                    val forums = forumsDeferred.awaitAll().flatten()
                    val announcements = (courseAnnDeferred.awaitAll().flatten() + generalAnnDeferred.await())
                        .distinctBy { it.remoteId }
                    AppResult.Success(forums to announcements)
                }
            } catch (io: IOException) {
                AppResult.Error("Sin conexión al cargar foros y avisos.", cause = io, isNetworkError = true)
            } catch (t: Throwable) {
                AppResult.Error("No se pudieron cargar foros y avisos.", cause = t)
            }
        }

    /**
     * Nivel 4 (Notas y Evaluaciones): Carga notas por unidad en Intranet y evaluaciones de Aula Virtual.
     */
    suspend fun fetchGradesAndExamsSnapshot(
        courses: List<Course>
    ): AppResult<Pair<List<ExamEntity>, String>> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val avBase = aulaVirtualBaseUrl.trimEnd('/')
                val intraBase = intranetBaseUrl.trimEnd('/')

                coroutineScope {
                    val avExamsDeferred = courses.map { course ->
                        async {
                            val out = mutableListOf<ExamEntity>()
                            fetchString("$avBase/web/Qualification/GetDataQuali?s=${course.sectionId}", isAjax = true)
                                ?.takeIf { !looksLikeLoginRedirect(it) && it.trim().startsWith("[") }
                                ?.let { out += AkdemicParsers.parseExamsPayload(it, course.remoteId, course.name, now) }
                            fetchString("$avBase/web/exam/list?s=${course.sectionId}")
                                ?.takeIf { !looksLikeLoginRedirect(it) && it.trim().startsWith("[") }
                                ?.let { out += AkdemicParsers.parseExamsPayload(it, course.remoteId, course.name, now) }
                            out
                        }
                    }

                    val intranetGradesDeferred = async {
                        runCatching { authClient.completeSilentSso(intranetBaseUrl) }
                        val notasHtml = fetchString("$intraBase/alumno/notas")
                            ?.takeIf { !looksLikeLoginRedirect(it) }
                        val selectedTerm = notasHtml?.let { AkdemicParsers.parseIntranetSelectedTerm(it) }
                        val termId = selectedTerm?.first
                        val termName = selectedTerm?.second.orEmpty()
                        val notasPeriodo = termId?.let { id ->
                            fetchString("$intraBase/alumno/notas/periodo/$id/get", isAjax = true)
                                ?.takeIf { !looksLikeLoginRedirect(it) }
                        }
                        val exams = notasPeriodo?.let { AkdemicParsers.parseExamsPayload(it, nowMillis = now) }.orEmpty()
                        exams to termName
                    }

                    val avExams = avExamsDeferred.awaitAll().flatten()
                    val (intraExams, termName) = intranetGradesDeferred.await()

                    val courseByCodeNorm = courses.associateBy { CourseColorPalette.normalizeCode(it.code, "") }
                    val courseByNameNorm = courses.associateBy { CourseColorPalette.normalizeCode(null, it.name) }
                    val linked = (avExams + intraExams)
                        .distinctBy { it.remoteId }
                        .map { exam ->
                            val matched = courseByCodeNorm[CourseColorPalette.normalizeCode(exam.courseId, "")]
                                ?: courseByNameNorm[CourseColorPalette.normalizeCode(null, exam.courseName)]
                            if (matched != null) exam.copy(courseId = matched.remoteId) else exam
                        }
                    AppResult.Success(linked to termName)
                }
            } catch (io: IOException) {
                AppResult.Error("Sin conexión al consultar notas.", cause = io, isNetworkError = true)
            } catch (t: Throwable) {
                AppResult.Error("No se pudieron cargar las notas.", cause = t)
            }
        }

    /**
     * Nivel 5 (Mi Perfil): Carga código oficial, DNI, carrera, facultad y semestre desde Intranet.
     */
    suspend fun fetchStudentProfileDetail(
        userIdentifier: String,
        currentStudent: StudentProfile?
    ): AppResult<StudentEntity> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                runCatching { authClient.completeSilentSso(intranetBaseUrl) }
                val intraBase = intranetBaseUrl.trimEnd('/')

                coroutineScope {
                    val historyDef = async {
                        fetchString("$intraBase/alumno/historial")
                            ?.takeIf { !looksLikeLoginRedirect(it) }
                    }
                    val notasDef = async {
                        fetchString("$intraBase/alumno/notas")
                            ?.takeIf { !looksLikeLoginRedirect(it) }
                    }
                    val profileDef = async {
                        listOf(
                            "$intraBase/api/student/profile",
                            "$intraBase/perfil",
                            "$intraBase/"
                        ).firstNotNullOfOrNull { url ->
                            fetchString(url)?.takeIf { !looksLikeLoginRedirect(it) }
                                ?.let { AkdemicParsers.parseStudentProfile(it, userIdentifier, now) }
                        }
                    }

                    val historyHtml = historyDef.await()
                    val (histName, histCode, histDni) = historyHtml
                        ?.let { AkdemicParsers.parseHistoryStudentMetaHtml(it) }
                        ?: Triple(null, null, null)
                    val termName = notasDef.await()?.let { AkdemicParsers.parseIntranetSelectedTerm(it)?.second }.orEmpty()
                    val parsedProfile = profileDef.await()

                    val entity = StudentEntity(
                        id = currentStudent?.id?.takeIf { it.isNotBlank() } ?: parsedProfile?.id ?: userIdentifier.ifBlank { "student" },
                        code = histCode?.takeIf { it.isNotBlank() } ?: currentStudent?.code?.takeIf { it.isNotBlank() } ?: parsedProfile?.code.orEmpty(),
                        dni = histDni?.takeIf { it.isNotBlank() } ?: currentStudent?.dni?.takeIf { it.isNotBlank() } ?: parsedProfile?.dni.orEmpty(),
                        fullName = histName?.takeIf { it.isNotBlank() }
                            ?: parsedProfile?.fullName?.takeIf { it.isNotBlank() }
                            ?: currentStudent?.fullName?.takeIf { it.isNotBlank() }
                            ?: userIdentifier,
                        career = parsedProfile?.career?.takeIf { it.isNotBlank() && it != "UNAP Puno" }
                            ?: currentStudent?.career?.takeIf { it.isNotBlank() }
                            ?: "UNAP Puno",
                        faculty = parsedProfile?.faculty?.takeIf { it.isNotBlank() } ?: currentStudent?.faculty.orEmpty(),
                        semester = termName.ifBlank { currentStudent?.semester.orEmpty() },
                        lastUpdated = now
                    )
                    AppResult.Success(entity)
                }
            } catch (io: IOException) {
                AppResult.Error("Sin conexión al consultar el perfil universitario.", cause = io, isNetworkError = true)
            } catch (t: Throwable) {
                AppResult.Error("No se pudo cargar el perfil del estudiante.", cause = t)
            }
        }

    suspend fun fetchFullAcademicSnapshot(userIdentifier: String): AppResult<RemoteSyncPayload> =
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val coursesOutcome = fetchAuthenticatedCoursesRaw()
                val (coursesRaw, aulaCourses) = when (coursesOutcome) {
                    is AppResult.Success -> coursesOutcome.data
                    is AppResult.Error -> return@withContext coursesOutcome
                }

                val avBase = aulaVirtualBaseUrl.trimEnd('/')
                val intraBase = intranetBaseUrl.trimEnd('/')

                coroutineScope {
                    val perCourseDeferred = aulaCourses.map { course ->
                        async {
                            val sectionId = course.sectionId
                            val tDef = async {
                                fetchString("$avBase/web/section/list/teachersbysection?s=$sectionId", isAjax = true)
                                    ?.takeIf { !looksLikeLoginRedirect(it) }
                                    ?.let { AkdemicParsers.parseTeachersBySectionJson(it) }
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.joinToString(" / ")
                            }
                            val hwDef = async {
                                fetchString("$avBase${UnapEndpoints.PATH_HOMEWORK_LIST}?s=$sectionId")
                                    ?.takeIf { !looksLikeLoginRedirect(it) }
                                    ?.let { AkdemicParsers.parseHomeworkJson(it, course.remoteId, course.name, now) }
                                    .orEmpty()
                            }
                            val fDef = async {
                                fetchString("$avBase${UnapEndpoints.PATH_FORUM_LIST}?s=$sectionId")
                                    ?.takeIf { !looksLikeLoginRedirect(it) }
                                    ?.let { AkdemicParsers.parseForumJson(it, course.remoteId, course.name, now) }
                                    .orEmpty()
                            }
                            val aDef = async {
                                fetchString("$avBase/web/announcement/list?s=$sectionId")
                                    ?.takeIf { !looksLikeLoginRedirect(it) && it.trim().startsWith("[") }
                                    ?.let {
                                        AkdemicParsers.parseAnnouncementsJson(
                                            it, course.remoteId, course.name, "aulavirtual", now
                                        )
                                    }.orEmpty()
                            }
                            val e1Def = async {
                                fetchString("$avBase/web/Qualification/GetDataQuali?s=$sectionId", isAjax = true)
                                    ?.takeIf { !looksLikeLoginRedirect(it) && it.trim().startsWith("[") }
                                    ?.let { AkdemicParsers.parseExamsPayload(it, course.remoteId, course.name, now) }
                                    .orEmpty()
                            }
                            val e2Def = async {
                                fetchString("$avBase/web/exam/list?s=$sectionId")
                                    ?.takeIf { !looksLikeLoginRedirect(it) && it.trim().startsWith("[") }
                                    ?.let { AkdemicParsers.parseExamsPayload(it, course.remoteId, course.name, now) }
                                    .orEmpty()
                            }
                            CourseBundle(
                                courseId = course.remoteId,
                                teacher = tDef.await(),
                                tasks = hwDef.await(),
                                forums = fDef.await(),
                                announcements = aDef.await(),
                                exams = e1Def.await() + e2Def.await()
                            )
                        }
                    }

                    val generalAnnDeferred = async {
                        val list = mutableListOf<AnnouncementEntity>()
                        for (path in UnapEndpoints.ANNOUNCEMENT_CANDIDATES) {
                            val body = fetchString("$avBase$path") ?: continue
                            if (!looksLikeLoginRedirect(body)) {
                                if (body.trim().startsWith("[") || body.trim().startsWith("{")) {
                                    list += AkdemicParsers.parseAnnouncementsJson(
                                        body, "general", "Aula Virtual", "aulavirtual", now
                                    )
                                } else {
                                    list += AkdemicParsers.parseAnnouncementsFromHtml(body, "aulavirtual", now)
                                }
                                if (list.isNotEmpty()) break
                            }
                        }
                        list
                    }

                    val intranetDeferred = async {
                        runCatching { authClient.completeSilentSso(intranetBaseUrl) }
                        val intranetRootUrl = "$intraBase/"
                        val intranetHome = fetchString(intranetRootUrl)?.takeIf { !looksLikeLoginRedirect(it) }

                        val rawStudent = listOf(
                            "$intraBase/api/student/profile",
                            "$intraBase/perfil",
                            intranetRootUrl
                        ).firstNotNullOfOrNull { candidateUrl ->
                            val body = if (candidateUrl == intranetRootUrl) intranetHome else fetchString(candidateUrl)
                            body?.takeIf { !looksLikeLoginRedirect(it) }
                                ?.let { AkdemicParsers.parseStudentProfile(it, userIdentifier, now) }
                        }
                        val homeAnn = intranetHome?.let {
                            AkdemicParsers.parseAnnouncementsFromHtml(it, "intranet", now)
                        }.orEmpty()
                        val schedules = fetchIntranetSchedule(now)

                        val notasOverviewHtml = fetchString("$intraBase/alumno/notas")
                            ?.takeIf { !looksLikeLoginRedirect(it) }
                        val selectedTerm = notasOverviewHtml?.let { AkdemicParsers.parseIntranetSelectedTerm(it) }
                        val termId = selectedTerm?.first
                        val termName = selectedTerm?.second.orEmpty()
                        val notasPeriodoHtml = termId?.let { id ->
                            fetchString("$intraBase/alumno/notas/periodo/$id/get", isAjax = true)
                                ?.takeIf { !looksLikeLoginRedirect(it) }
                        }

                        val intranetGradesExams = notasPeriodoHtml
                            ?.let { AkdemicParsers.parseExamsPayload(it, nowMillis = now) }
                            .orEmpty()
                        val intranetExams = intranetGradesExams.ifEmpty {
                            listOf(
                                "$intraBase/api/student/exams",
                                "$intraBase/examenes",
                                "$intraBase/evaluaciones"
                            ).firstNotNullOfOrNull { url ->
                                val body = fetchString(url)?.takeIf { !looksLikeLoginRedirect(it) }
                                body?.let { AkdemicParsers.parseExamsPayload(it, nowMillis = now) }?.takeIf { it.isNotEmpty() }
                            }.orEmpty()
                        }

                        val intranetCoursesFromNotas = notasPeriodoHtml
                            ?.let { AkdemicParsers.parseIntranetCoursesPayload(it, now, defaultSemester = termName) }
                            ?.takeIf { it.isNotEmpty() }
                        val intranetCourses = intranetCoursesFromNotas
                            ?: UnapEndpoints.INTRANET_COURSE_CANDIDATES
                                .firstNotNullOfOrNull { path ->
                                    val body = fetchString("$intraBase$path")
                                        ?.takeIf { !looksLikeLoginRedirect(it) }
                                    body?.let { AkdemicParsers.parseIntranetCoursesPayload(it, now, defaultSemester = termName) }
                                        ?.takeIf { it.isNotEmpty() }
                                }
                            ?: intranetHome?.let { AkdemicParsers.parseIntranetCoursesPayload(it, now, defaultSemester = termName) }.orEmpty()

                        val historyHtml = fetchString("$intraBase/alumno/historial")
                            ?.takeIf { !looksLikeLoginRedirect(it) }
                        val (histName, histCode, histDni) = historyHtml
                            ?.let { AkdemicParsers.parseHistoryStudentMetaHtml(it) }
                            ?: Triple(null, null, null)

                        IntranetSyncResult(
                            rawStudent = rawStudent,
                            homeAnnouncements = homeAnn,
                            schedules = schedules,
                            intranetExams = intranetExams,
                            intranetCourses = intranetCourses,
                            termName = termName,
                            histName = histName,
                            histCode = histCode,
                            histDni = histDni
                        )
                    }

                    val bundles = perCourseDeferred.awaitAll()
                    val teachersByCourse = bundles.mapNotNull { b -> b.teacher?.let { b.courseId to it } }.toMap()
                    val tasks = bundles.flatMap { it.tasks }
                    val forums = bundles.flatMap { it.forums }
                    val announcements = (bundles.flatMap { it.announcements } + generalAnnDeferred.await()).toMutableList()
                    val exams = bundles.flatMap { it.exams }
                    val intra = intranetDeferred.await()
                    announcements += intra.homeAnnouncements

                    val mergedCourses = AkdemicParsers.mergeCoursesByCodeAndAssignColors(
                        aulaCourses = aulaCourses.map { c ->
                            val resolvedTeacher = teachersByCourse[c.remoteId]
                            if (!resolvedTeacher.isNullOrBlank()) c.copy(teacher = resolvedTeacher) else c
                        },
                        intranetCourses = intra.intranetCourses,
                        schedules = intra.schedules,
                        nowMillis = now
                    )

                    val deptAndFaculty = AkdemicParsers.extractDepartmentAndFacultyFromCoursesJson(coursesRaw)
                    val student = (intra.rawStudent ?: if (!intra.histName.isNullOrBlank()) {
                        StudentEntity(
                            id = userIdentifier.ifBlank { "student" },
                            code = intra.histCode ?: userIdentifier,
                            dni = intra.histDni ?: userIdentifier,
                            fullName = intra.histName,
                            career = deptAndFaculty?.first ?: "UNAP Puno",
                            faculty = deptAndFaculty?.second.orEmpty(),
                            semester = intra.termName,
                            lastUpdated = now
                        )
                    } else null)?.let { s ->
                        val resolvedCareer = if (s.career == "UNAP Puno" && !deptAndFaculty?.first.isNullOrBlank()) {
                            deptAndFaculty!!.first
                        } else {
                            s.career
                        }
                        s.copy(
                            code = intra.histCode?.takeIf { it.isNotBlank() } ?: s.code,
                            dni = intra.histDni?.takeIf { it.isNotBlank() } ?: s.dni,
                            fullName = intra.histName?.takeIf { it.isNotBlank() } ?: s.fullName,
                            career = resolvedCareer,
                            faculty = s.faculty.ifBlank { deptAndFaculty?.second.orEmpty() },
                            semester = s.semester.ifBlank { intra.termName }
                        )
                    }

                    val linkedSchedules = linkSchedulesToCourses(intra.schedules, mergedCourses)
                    val courseByCodeNorm = mergedCourses.associateBy { CourseColorPalette.normalizeCode(it.code, "") }
                    val courseByNameNorm = mergedCourses.associateBy { CourseColorPalette.normalizeCode(null, it.name) }
                    val linkedExams = (exams + intra.intranetExams)
                        .distinctBy { it.remoteId }
                        .map { exam ->
                            val matched = courseByCodeNorm[CourseColorPalette.normalizeCode(exam.courseId, "")]
                                ?: courseByNameNorm[CourseColorPalette.normalizeCode(null, exam.courseName)]
                            if (matched != null) exam.copy(courseId = matched.remoteId) else exam
                        }

                    AppResult.Success(
                        RemoteSyncPayload(
                            student = student,
                            courses = mergedCourses,
                            tasks = tasks,
                            forums = forums,
                            announcements = announcements.distinctBy { it.remoteId },
                            schedules = linkedSchedules,
                            exams = linkedExams
                        )
                    )
                }
            } catch (io: IOException) {
                AppResult.Error(
                    message = "Sin conexión estable con los servidores universitarios.",
                    cause = io,
                    isNetworkError = true
                )
            } catch (t: Throwable) {
                AppResult.Error(
                    message = "No se pudo procesar la respuesta académica: ${t.message ?: "error desconocido"}",
                    cause = t
                )
            }
        }

    private data class CourseBundle(
        val courseId: String,
        val teacher: String?,
        val tasks: List<TaskEntity>,
        val forums: List<ForumEntity>,
        val announcements: List<AnnouncementEntity>,
        val exams: List<ExamEntity>
    )

    private data class IntranetSyncResult(
        val rawStudent: StudentEntity?,
        val homeAnnouncements: List<AnnouncementEntity>,
        val schedules: List<ScheduleEntity>,
        val intranetExams: List<ExamEntity>,
        val intranetCourses: List<CourseEntity>,
        val termName: String,
        val histName: String?,
        val histCode: String?,
        val histDni: String?
    )

    private fun fetchString(url: String, isAjax: Boolean = false): String? {
        val builder = Request.Builder().url(url).get()
        if (isAjax) {
            builder
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/plain, */*")
        }
        return runCatching {
            httpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        }.getOrNull()
    }

    private fun currentWeekIsoBounds(nowMillis: Long): Pair<String, String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val start = fmt.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 7)
        val end = fmt.format(cal.time)
        return start to end
    }

    /**
     * Like [fetchString] but keeps the status code; returns null only on transport failures.
     */
    private fun fetchWithStatus(url: String): FetchResult? {
        val request = Request.Builder()
            .url(url)
            .header("X-Requested-With", "XMLHttpRequest")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                FetchResult(code = response.code, body = response.body?.string().orEmpty())
            }
        } catch (io: IOException) {
            null
        }
    }

    private inner class FetchResult(val code: Int, val body: String) {
        val isSuccessful: Boolean get() = code in 200..299

        fun indicatesExpiredSession(): Boolean =
            code == 401 || code == 403 || (isSuccessful && looksLikeLoginRedirect(body))
    }

    private fun looksLikeLoginRedirect(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("[") || trimmed.startsWith("{")) return false
        return trimmed.contains("main_form_login", ignoreCase = true) ||
            trimmed.contains("campus.unap.edu.pe/login", ignoreCase = true) ||
            trimmed.contains("signin-oidc", ignoreCase = true)
    }

    suspend fun fetchCourseFullDetail(course: Course): AppResult<CourseFullDetail> =
        withContext(Dispatchers.IO) {
            try {
                val base = aulaVirtualBaseUrl.trimEnd('/')
                val sectionId = course.sectionId

                var teachersRaw = fetchString("$base/web/section/list/teachersbysection?s=$sectionId", isAjax = true)
                if (teachersRaw != null && looksLikeLoginRedirect(teachersRaw)) {
                    if (runCatching { authClient.completeSilentSso(aulaVirtualBaseUrl) }.getOrDefault(false)) {
                        teachersRaw = fetchString("$base/web/section/list/teachersbysection?s=$sectionId", isAjax = true)
                    }
                }
                if (teachersRaw != null && looksLikeLoginRedirect(teachersRaw)) {
                    return@withContext AppResult.Error(
                        message = "Tu sesión institucional expiró.",
                        isSessionExpired = true
                    )
                }

                val teachers = teachersRaw?.let { AkdemicParsers.parseTeachersBySectionJson(it) }
                    ?.takeIf { it.isNotEmpty() }
                    ?: listOfNotNull(course.teacher.takeIf { it.isNotBlank() })

                val unitsRaw = fetchString("$base/web/unit/$sectionId/get?s=$sectionId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val baseUnits = unitsRaw?.let { AkdemicParsers.parseCourseUnitsJson(it) }.orEmpty()
                val enrichedUnits = coroutineScope {
                    baseUnits.map { unit ->
                        async {
                            val enrichedTopics = unit.topics.map { topic ->
                                async {
                                    val topicResRaw = fetchString("$base/web/unit/contenidos/${topic.id}", isAjax = true)
                                        ?.takeIf { !looksLikeLoginRedirect(it) }
                                    val resources = topicResRaw?.let {
                                        AkdemicParsers.parseUnitTopicResourcesJson(it, base)
                                    }.orEmpty()
                                    topic.copy(resources = resources)
                                }
                            }.awaitAll()
                            unit.copy(topics = enrichedTopics)
                        }
                    }.awaitAll()
                }

                val readingsRaw = fetchString("$base/web/reading/listbysection?s=$sectionId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val readings = readingsRaw?.let { AkdemicParsers.parseReadingsJson(it, base) }.orEmpty()

                val qualiExams = fetchString("$base/web/Qualification/GetDataQuali?s=$sectionId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val qualiHomeworks = fetchString("$base/web/Qualification/ListHomeworkQualification?s=$sectionId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val qualiForums = fetchString("$base/web/Qualification/listForums?s=$sectionId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val qualifications = AkdemicParsers.parseQualificationsPayload(
                    examsJson = qualiExams,
                    homeworksJson = qualiHomeworks,
                    forumsJson = qualiForums
                )

                val chatTeachers = fetchString("$base/web/chat/section/teachers?s=$sectionId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val chatStudents = fetchString("$base/web/chat/section/students?s=$sectionId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val participants = AkdemicParsers.parseParticipantsJson(chatTeachers, chatStudents)

                val groupsRaw = fetchString("$base/web/group/list?s=$sectionId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val groups = AkdemicParsers.parseGroupsJson(groupsRaw)

                val confRaw = fetchString("$base/web/conference/list?s=$sectionId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val virtualRaw = fetchString("$base/web/unit/clases-virtuales/get?s=$sectionId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val conferences = AkdemicParsers.parseConferencesJson(confRaw, virtualRaw)

                val calendarRaw = fetchString("$base/web/calendar/listbysection?s=$sectionId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val calendarEvents = AkdemicParsers.parseCalendarEventsJson(calendarRaw)

                AppResult.Success(
                    CourseFullDetail(
                        sectionId = sectionId,
                        teachers = teachers,
                        sectionLabel = course.sectionGroup.ifBlank { "Sección asignada" },
                        syllabusUrl = "$base/web/course/syllabus/$sectionId",
                        teacherSyllabusUrl = "$base/web/course/teacher-syllabus/$sectionId",
                        units = enrichedUnits,
                        readings = readings,
                        qualifications = qualifications,
                        participants = participants,
                        groups = groups,
                        conferences = conferences,
                        calendarEvents = calendarEvents
                    )
                )
            } catch (io: IOException) {
                AppResult.Error(
                    message = "Sin conexión al consultar el detalle del curso.",
                    cause = io,
                    isNetworkError = true
                )
            } catch (t: Throwable) {
                AppResult.Error(
                    message = "No se pudo cargar el detalle del curso: ${t.message ?: "error"}",
                    cause = t
                )
            }
        }

    suspend fun fetchHomeworkDetail(homeworkId: String): AppResult<HomeworkDetailInfo> =
        withContext(Dispatchers.IO) {
            if (homeworkId.isBlank()) {
                return@withContext AppResult.Error("La tarea no tiene identificador remoto.")
            }
            try {
                val base = aulaVirtualBaseUrl.trimEnd('/')
                var raw = fetchString("$base/web/homework/get?h=$homeworkId", isAjax = true)
                if (raw != null && looksLikeLoginRedirect(raw)) {
                    if (runCatching { authClient.completeSilentSso(aulaVirtualBaseUrl) }.getOrDefault(false)) {
                        raw = fetchString("$base/web/homework/get?h=$homeworkId", isAjax = true)
                    }
                }
                val parsed = raw?.takeIf { !looksLikeLoginRedirect(it) }
                    ?.let { AkdemicParsers.parseHomeworkDetailJson(it, base) }
                if (parsed != null) {
                    AppResult.Success(parsed)
                } else {
                    AppResult.Error("No se encontró el detalle de la tarea.")
                }
            } catch (io: IOException) {
                AppResult.Error("Sin conexión al consultar la tarea.", cause = io, isNetworkError = true)
            } catch (t: Throwable) {
                AppResult.Error("No se pudo abrir la tarea.", cause = t)
            }
        }

    suspend fun fetchForumDiscussion(forumId: String): AppResult<Pair<String, List<ForumReplyItem>>> =
        withContext(Dispatchers.IO) {
            if (forumId.isBlank()) {
                return@withContext AppResult.Error("El foro no tiene identificador remoto.")
            }
            try {
                val base = aulaVirtualBaseUrl.trimEnd('/')
                var detailRaw = fetchString("$base/web/forum/detail?f=$forumId", isAjax = true)
                if (detailRaw != null && looksLikeLoginRedirect(detailRaw)) {
                    if (runCatching { authClient.completeSilentSso(aulaVirtualBaseUrl) }.getOrDefault(false)) {
                        detailRaw = fetchString("$base/web/forum/detail?f=$forumId", isAjax = true)
                    }
                }
                val prompt = detailRaw?.takeIf { !looksLikeLoginRedirect(it) }
                    ?.let { AkdemicParsers.parseForumDetailPrompt(it) }
                    .orEmpty()

                val answersRaw = fetchString("$base/web/forum/answers/list?f=$forumId", isAjax = true)
                    ?.takeIf { !looksLikeLoginRedirect(it) }
                val replies = answersRaw?.let { AkdemicParsers.parseForumAnswersJson(it) }.orEmpty()
                AppResult.Success(prompt to replies)
            } catch (io: IOException) {
                AppResult.Error("Sin conexión al consultar las respuestas del foro.", cause = io, isNetworkError = true)
            } catch (t: Throwable) {
                AppResult.Error("No se pudieron cargar las respuestas del foro.", cause = t)
            }
        }

    private suspend fun probeIntranetHtmlWithSso(path: String): Pair<String?, Boolean> {
        val base = intranetBaseUrl.trimEnd('/')
        var html = fetchString("$base$path")
        if (html != null && looksLikeLoginRedirect(html)) {
            if (runCatching { authClient.completeSilentSso(intranetBaseUrl) }.getOrDefault(false)) {
                html = fetchString("$base$path")
            }
        }
        val expired = html != null && looksLikeLoginRedirect(html)
        return (html?.takeIf { !expired }) to expired
    }

    private suspend fun fetchIntranetHistoryOnly(): AppResult<List<AcademicHistorySemester>> {
        val base = intranetBaseUrl.trimEnd('/')
        val (historyHtml, expired) = probeIntranetHtmlWithSso("/alumno/historial")
        if (expired) {
            return AppResult.Error(
                message = "Tu sesión institucional en Intranet expiró.",
                isSessionExpired = true
            )
        }
        val periods = historyHtml?.let { AkdemicParsers.parseHistoryPeriodsHtml(it) }.orEmpty()
        val historySemesters = coroutineScope {
            periods.take(8).map { (periodTitle, pid) ->
                async {
                    val raw = fetchString("$base/alumno/historial/periodo/$pid/get", isAjax = true)
                        ?.takeIf { !looksLikeLoginRedirect(it) }
                    val items = raw?.let { AkdemicParsers.parseHistoryPeriodCoursesJson(it) }.orEmpty()
                    AcademicHistorySemester(periodName = periodTitle, courses = items)
                }
            }.awaitAll().filter { it.courses.isNotEmpty() }
        }
        return AppResult.Success(historySemesters)
    }

    private suspend fun probeCurriculumOnly(
        activeCourses: List<Course>,
        knownHistory: List<AcademicHistorySemester>
    ): AppResult<List<sky.universidad.UnApp.domain.model.CurriculumCycle>> {
        val (mallaHtml, expired) = probeIntranetHtmlWithSso("/alumno/malla-curricular")
        if (expired) {
            return AppResult.Error(
                message = "Tu sesión institucional en Intranet expiró.",
                isSessionExpired = true
            )
        }
        val approvedCodes = knownHistory.flatMap { it.courses }
            .filter { it.approved && it.finalGrade != "-" && (it.finalGrade.toDoubleOrNull() ?: 0.0) >= 10.5 }
            .map { it.course.substringBefore("-").trim().uppercase().replace(Regex("[^A-Z0-9]"), "") }
            .toSet()
        val activeCodes = activeCourses
            .map { it.code.uppercase().replace(Regex("[^A-Z0-9]"), "") }
            .toSet()
        val curriculum = mallaHtml?.let {
            AkdemicParsers.parseCurriculumHtml(it, approvedCodes = approvedCodes, activeCodes = activeCodes)
        }.orEmpty()
        return AppResult.Success(curriculum)
    }

    private suspend fun probeAttendanceOnly(): AppResult<List<sky.universidad.UnApp.domain.model.CourseAttendanceItem>> {
        val base = intranetBaseUrl.trimEnd('/')
        val (inasHtml, expired) = probeIntranetHtmlWithSso("/alumno/inasistencias")
        if (expired) {
            return AppResult.Error(
                message = "Tu sesión institucional en Intranet expiró.",
                isSessionExpired = true
            )
        }
        val termId = inasHtml?.let { AkdemicParsers.parseIntranetSelectedTerm(it)?.first }
        val attendanceRaw = termId?.let { tid ->
            fetchString("$base/alumno/inasistencias/periodo/$tid/get", isAjax = true)
                ?.takeIf { !looksLikeLoginRedirect(it) }
        }
        return AppResult.Success(AkdemicParsers.parseAttendanceJson(attendanceRaw))
    }

    private suspend fun fetchAdminRecordsOnly(): List<sky.universidad.UnApp.domain.model.AdminRecordItem> =
        coroutineScope {
            val base = intranetBaseUrl.trimEnd('/')
            val debtsDef = async { fetchString("$base/alumno/deudas_pendientes/get", isAjax = true)?.takeIf { !looksLikeLoginRedirect(it) } }
            val payDef = async { fetchString("$base/alumno/historial_de_pagos/get", isAjax = true)?.takeIf { !looksLikeLoginRedirect(it) } }
            val procDef = async { fetchString("$base/alumno/tramites_pendientes/get", isAjax = true)?.takeIf { !looksLikeLoginRedirect(it) } }
            val reqDef = async { fetchString("$base/alumno/mis-solicitudes/get-records", isAjax = true)?.takeIf { !looksLikeLoginRedirect(it) } }
            AkdemicParsers.parseAdminRecordsJson(
                debtsJson = debtsDef.await(),
                paymentsJson = payDef.await(),
                proceduresJson = procDef.await(),
                requestsJson = reqDef.await()
            )
        }

    /**
     * Carga bajo demanda únicamente el módulo específico de Intranet solicitado por la pestaña abierta,
     * fusionándolo con lo que ya estuviera cargado previamente en memoria.
     */
    suspend fun fetchIntranetSection(
        sectionKey: String,
        activeCourses: List<Course>,
        currentOverview: IntranetOverviewData?
    ): AppResult<IntranetOverviewData> = withContext(Dispatchers.IO) {
        try {
            runCatching { authClient.completeSilentSso(intranetBaseUrl) }
            val existing = currentOverview ?: IntranetOverviewData()
            when (sectionKey) {
                "ACADEMIC_HISTORY" -> when (val res = fetchIntranetHistoryOnly()) {
                    is AppResult.Success -> AppResult.Success(existing.copy(history = res.data))
                    is AppResult.Error -> res
                }
                "CURRICULUM" -> when (val res = probeCurriculumOnly(activeCourses, existing.history)) {
                    is AppResult.Success -> AppResult.Success(existing.copy(curriculum = res.data))
                    is AppResult.Error -> res
                }
                "ATTENDANCE" -> when (val res = probeAttendanceOnly()) {
                    is AppResult.Success -> AppResult.Success(existing.copy(attendance = res.data))
                    is AppResult.Error -> res
                }
                "ADMIN_PROCEDURES" -> {
                    val records = fetchAdminRecordsOnly()
                    AppResult.Success(existing.copy(adminRecords = records))
                }
                else -> fetchIntranetOverview(activeCourses)
            }
        } catch (io: IOException) {
            AppResult.Error("Sin conexión con Intranet UNAP.", cause = io, isNetworkError = true)
        } catch (t: Throwable) {
            AppResult.Error("No se pudo consultar la Intranet: ${t.message ?: "error"}", cause = t)
        }
    }

    suspend fun fetchIntranetOverview(activeCourses: List<Course>): AppResult<IntranetOverviewData> =
        withContext(Dispatchers.IO) {
            try {
                runCatching { authClient.completeSilentSso(intranetBaseUrl) }
                coroutineScope {
                    val historyDef = async { fetchIntranetHistoryOnly() }
                    val attendanceDef = async { probeAttendanceOnly() }
                    val adminDef = async { fetchAdminRecordsOnly() }

                    val historyRes = historyDef.await()
                    if (historyRes is AppResult.Error) return@coroutineScope historyRes
                    val historySemesters = (historyRes as AppResult.Success).data

                    val curriculumRes = probeCurriculumOnly(activeCourses, historySemesters)
                    if (curriculumRes is AppResult.Error) return@coroutineScope curriculumRes
                    val curriculum = (curriculumRes as AppResult.Success).data

                    val attendanceRes = attendanceDef.await()
                    val attendance = (attendanceRes as? AppResult.Success)?.data.orEmpty()

                    AppResult.Success(
                        IntranetOverviewData(
                            curriculum = curriculum,
                            history = historySemesters,
                            attendance = attendance,
                            adminRecords = adminDef.await()
                        )
                    )
                }
            } catch (io: IOException) {
                AppResult.Error("Sin conexión con Intranet UNAP.", cause = io, isNetworkError = true)
            } catch (t: Throwable) {
                AppResult.Error("No se pudo consultar la Intranet: ${t.message ?: "error"}", cause = t)
            }
        }

    suspend fun downloadAuthenticatedFile(url: String, targetDir: File): AppResult<File> =
        withContext(Dispatchers.IO) {
            try {
                if (!targetDir.exists()) targetDir.mkdirs()

                val trimmed = url.trim().replace(" ", "%20")
                val resolvedUrl = when {
                    trimmed.startsWith("http://", ignoreCase = true) ||
                        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                    trimmed.startsWith("//") -> "https:$trimmed"
                    trimmed.startsWith("/") -> "${aulaVirtualBaseUrl.trimEnd('/')}$trimmed"
                    trimmed.isNotBlank() -> "${aulaVirtualBaseUrl.trimEnd('/')}/$trimmed"
                    else -> ""
                }
                if (resolvedUrl.isBlank()) {
                    return@withContext AppResult.Error("El enlace del documento está vacío o es inválido.")
                }

                fun executeDownload(targetUrl: String): okhttp3.Response {
                    val req = Request.Builder().url(targetUrl).get().build()
                    return httpClient.newCall(req).execute()
                }

                var response = executeDownload(resolvedUrl)
                val firstContentType = response.header("Content-Type").orEmpty()
                if (firstContentType.contains("text/html", ignoreCase = true) || response.code == 401 || response.code == 403) {
                    val bodyPreview = response.body?.string().orEmpty()
                    response.close()
                    if (looksLikeLoginRedirect(bodyPreview) || response.code == 401 || response.code == 403) {
                        if (runCatching { authClient.completeSilentSso(aulaVirtualBaseUrl) }.getOrDefault(false)) {
                            response = executeDownload(resolvedUrl)
                        } else {
                            return@withContext AppResult.Error("Tu sesión institucional expiró para descargar este archivo.")
                        }
                    } else {
                        return@withContext AppResult.Error("El enlace devolvió una página web en lugar de un documento.")
                    }
                }

                response.use { res ->
                    if (!res.isSuccessful) {
                        return@withContext AppResult.Error("El servidor respondió con código ${res.code}.")
                    }
                    val cType = res.header("Content-Type").orEmpty().lowercase()
                    val cDisp = res.header("Content-Disposition").orEmpty()
                    val dispName = Regex("""filename\*?=(?:UTF-8'')?["']?([^"';]+)["']?""", RegexOption.IGNORE_CASE)
                        .find(cDisp)?.groupValues?.get(1)?.trim()

                    val urlFileName = url.substringBefore("?").substringAfterLast("/").takeIf {
                        it.contains(".") && it.length in 3..80
                    }

                    val defaultExt = when {
                        "pdf" in cType -> "pdf"
                        "wordprocessingml" in cType -> "docx"
                        "msword" in cType -> "doc"
                        "spreadsheetml" in cType -> "xlsx"
                        "presentationml" in cType -> "pptx"
                        "csv" in cType -> "csv"
                        "image/png" in cType -> "png"
                        "image/jpeg" in cType -> "jpg"
                        else -> "pdf"
                    }

                    val rawFileName = (dispName ?: urlFileName ?: "documento_${System.currentTimeMillis()}.$defaultExt")
                        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val finalFileName = if (rawFileName.contains(".")) rawFileName else "$rawFileName.$defaultExt"

                    val outFile = File(targetDir, finalFileName)
                    val bytes = res.body?.bytes()
                        ?: return@withContext AppResult.Error("El archivo descargado está vacío.")
                    if (bytes.isEmpty()) {
                        return@withContext AppResult.Error("El archivo está vacío o aún no disponible.")
                    }
                    outFile.writeBytes(bytes)
                    AppResult.Success(outFile)
                }
            } catch (io: IOException) {
                AppResult.Error("Error de red al descargar el documento.", cause = io, isNetworkError = true)
            } catch (t: Throwable) {
                AppResult.Error("No se pudo abrir el documento: ${t.message ?: "error"}", cause = t)
            }
        }
}
