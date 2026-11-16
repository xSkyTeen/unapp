package sky.universidad.UnApp.data.remote.parser

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import sky.universidad.UnApp.core.common.CourseColorPalette
import sky.universidad.UnApp.core.common.DateFormatters
import sky.universidad.UnApp.core.common.HtmlCleaner
import sky.universidad.UnApp.data.local.entities.AnnouncementEntity
import sky.universidad.UnApp.data.local.entities.CourseEntity
import sky.universidad.UnApp.data.local.entities.ExamEntity
import sky.universidad.UnApp.data.local.entities.ForumEntity
import sky.universidad.UnApp.data.local.entities.ScheduleEntity
import sky.universidad.UnApp.data.local.entities.StudentEntity
import sky.universidad.UnApp.data.local.entities.TaskEntity
import sky.universidad.UnApp.domain.model.AcademicHistoryCourse
import sky.universidad.UnApp.domain.model.AdminRecordItem
import sky.universidad.UnApp.domain.model.CourseAttendanceItem
import sky.universidad.UnApp.domain.model.CourseCalendarEvent
import sky.universidad.UnApp.domain.model.CourseConferenceItem
import sky.universidad.UnApp.domain.model.CourseGroupItem
import sky.universidad.UnApp.domain.model.CourseParticipant
import sky.universidad.UnApp.domain.model.CourseQualificationItem
import sky.universidad.UnApp.domain.model.CourseReadingItem
import sky.universidad.UnApp.domain.model.CourseResourceItem
import sky.universidad.UnApp.domain.model.CourseUnitItem
import sky.universidad.UnApp.domain.model.CurriculumCycle
import sky.universidad.UnApp.domain.model.ForumReplyItem
import sky.universidad.UnApp.domain.model.HomeworkDetailInfo
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tolerant parsers for Akdemic Aula Virtual JSON endpoints and Intranet HTML/JSON payloads.
 */
object AkdemicParsers {

    fun parseCoursesJson(rawJson: String, nowMillis: Long): List<CourseEntity> {
        val root = parseJsonArrayOrProperty(rawJson) ?: return emptyList()
        val rawCourses = root.mapNotNull { element ->
            val obj = element.asObj() ?: return@mapNotNull null
            val sectionId = obj.str("sectionId", "section_id", "SectionId", "id", "Id") ?: return@mapNotNull null
            val name = obj.str("name", "courseName", "Name", "title", "course")?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val code = obj.str(
                "codeCourse", "code", "courseCode", "Code", "sigla", "internalCode", "course_code", "sectionCode"
            ).orEmpty().ifBlank { "SEC-$sectionId" }
            val sectionMeta = listOfNotNull(
                obj.str("codeSection", "section")?.trim()?.takeIf { it.isNotEmpty() },
                obj.str("type")?.trim()?.takeIf { it.isNotEmpty() }
            ).joinToString(" · ")
            val teacher = obj.str("teacher", "teacherName", "professor", "docente", "TeacherName")
                .orEmpty().ifBlank { sectionMeta.ifBlank { "Docente asignado" } }
            val credits = obj.int("credits", "creditos", "Credits") ?: 0
            val semester = obj.str("term", "termName", "semester", "period", "TermName").orEmpty()

            CourseEntity(
                remoteId = sectionId,
                sectionId = sectionId,
                code = code,
                name = name,
                teacher = teacher,
                credits = credits,
                semester = semester,
                lastUpdated = nowMillis,
                sourcePortal = "aulavirtual",
                sectionGroup = sectionMeta
            )
        }
        return CourseColorPalette.assignColorsByCode(rawCourses)
    }

    /**
     * Parses Intranet course lists from either JSON endpoints or HTML academic/enrollment tables.
     */
    fun parseIntranetCoursesPayload(
        rawBody: String,
        nowMillis: Long,
        defaultSemester: String = ""
    ): List<CourseEntity> {
        if (rawBody.isBlank()) return emptyList()
        val trimmed = rawBody.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val arr = parseJsonArrayOrProperty(trimmed) ?: return emptyList()
            val parsed = arr.mapNotNull { el ->
                val obj = el.asObj() ?: return@mapNotNull null
                val name = obj.str("name", "courseName", "course", "asignatura", "Name", "CourseName")
                    ?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val code = obj.str("code", "courseCode", "Code", "sigla", "codigo").orEmpty().trim()
                val sectionId = obj.str("sectionId", "id", "Id", "courseId")
                    ?: code.ifBlank { stableHash(name) }
                val teacher = obj.str("teacher", "teacherName", "docente").orEmpty()
                val credits = obj.int("credits", "creditos", "Credits") ?: 0
                val semester = obj.str("term", "semester", "termName", "ciclo").orEmpty()
                    .ifBlank { defaultSemester }
                CourseEntity(
                    remoteId = sectionId,
                    sectionId = sectionId,
                    code = code.ifBlank { "INT-${stableHash(name).take(6).uppercase()}" },
                    name = name,
                    teacher = teacher.ifBlank { "Docente asignado" },
                    credits = credits,
                    semester = semester,
                    lastUpdated = nowMillis,
                    sourcePortal = "intranet"
                )
            }
            return CourseColorPalette.assignColorsByCode(parsed)
        }

        val doc = Jsoup.parse(rawBody)
        val courseContainers = doc.select(".course-container")
        if (courseContainers.isNotEmpty()) {
            return CourseColorPalette.assignColorsByCode(
                parseAkdemicCourseContainers(courseContainers, semester = defaultSemester)
                    .map { it.copy(lastUpdated = nowMillis) }
            )
        }

        val parsedHtml = mutableListOf<CourseEntity>()
        val codePattern = Regex("^[A-Za-z]{1,5}[-_ ]?\\d{2,5}[A-Za-z0-9-]*$")
        for (row in doc.select("table tr")) {
            val cols = row.select("td").map { it.text().trim() }.filter { it.isNotBlank() }
            if (cols.size < 2) continue
            val codeCandidate = cols.firstOrNull { codePattern.matches(it) }
            val nameCandidate = cols.firstOrNull {
                it != codeCandidate &&
                    it.length > 3 &&
                    it.toIntOrNull() == null &&
                    !it.contains(":") &&
                    parseDayOfWeek(it) == null
            } ?: continue
            val creditsCandidate = cols.firstNotNullOfOrNull { c ->
                c.toIntOrNull()?.takeIf { it in 1..12 }
            } ?: 0
            val teacherCandidate = cols.lastOrNull {
                it != codeCandidate && it != nameCandidate && it.toIntOrNull() == null
            }.orEmpty()
            val finalCode = codeCandidate ?: "INT-${stableHash(nameCandidate).take(6).uppercase()}"
            val remoteId = codeCandidate?.let { CourseColorPalette.normalizeCode(it) }
                ?: "int_${stableHash(nameCandidate)}"

            parsedHtml.add(
                CourseEntity(
                    remoteId = remoteId,
                    sectionId = remoteId,
                    code = finalCode,
                    name = nameCandidate,
                    teacher = teacherCandidate.ifBlank { "Docente asignado" },
                    credits = creditsCandidate,
                    semester = "",
                    lastUpdated = nowMillis,
                    sourcePortal = "intranet"
                )
            )
        }
        return CourseColorPalette.assignColorsByCode(parsedHtml)
    }

    /**
     * Merges courses obtained from Aula Virtual and Intranet by normalized course code
     * (falling back to normalized course name if one portal lacks the official code),
     * enriches metadata, and assigns a distinct color per code for login/sync.
     */
    fun mergeCoursesByCodeAndAssignColors(
        aulaCourses: List<CourseEntity>,
        intranetCourses: List<CourseEntity>,
        schedules: List<ScheduleEntity> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<CourseEntity> {
        val merged = aulaCourses.toMutableList()
        val matchedIntranetIndices = mutableSetOf<Int>()

        for (i in merged.indices) {
            val aula = merged[i]
            val aulaCodeNorm = CourseColorPalette.normalizeCode(aula.code, "")
            val aulaNameNorm = CourseColorPalette.normalizeCode(null, aula.name)

            val matchIdx = intranetCourses.indexOfFirst { intra ->
                val intraCodeNorm = CourseColorPalette.normalizeCode(intra.code, "")
                val intraNameNorm = CourseColorPalette.normalizeCode(null, intra.name)
                (aulaCodeNorm.isNotBlank() && intraCodeNorm.isNotBlank() && aulaCodeNorm == intraCodeNorm) ||
                    (aulaNameNorm.isNotBlank() && intraNameNorm == aulaNameNorm)
            }

            if (matchIdx >= 0) {
                matchedIntranetIndices.add(matchIdx)
                val intra = intranetCourses[matchIdx]
                val resolvedCode = if (aula.code.startsWith("SEC-", ignoreCase = true) && !intra.code.startsWith("INT-", ignoreCase = true)) {
                    intra.code
                } else {
                    aula.code
                }
                merged[i] = aula.copy(
                    code = resolvedCode,
                    teacher = if (isPlaceholderTeacher(aula.teacher) && !isPlaceholderTeacher(intra.teacher) && intra.teacher.isNotBlank()) {
                        intra.teacher
                    } else {
                        aula.teacher
                    },
                    credits = if (aula.credits == 0 && intra.credits > 0) intra.credits else aula.credits,
                    semester = aula.semester.ifBlank { intra.semester },
                    lastUpdated = nowMillis,
                    sourcePortal = "ambos"
                )
            }
        }

        intranetCourses.forEachIndexed { idx, intra ->
            if (idx !in matchedIntranetIndices) {
                merged.add(intra.copy(lastUpdated = nowMillis))
            }
        }

        // Also map teacher info from schedules if Aula Virtual omitted it
        val teacherByName = schedules
            .filter { it.teacher.isNotBlank() }
            .associateBy { CourseColorPalette.normalizeCode(null, it.courseName) }

        val enriched = merged.map { course ->
            val schedTeacher = teacherByName[CourseColorPalette.normalizeCode(null, course.name)]?.teacher
            if (isPlaceholderTeacher(course.teacher) && !schedTeacher.isNullOrBlank()) {
                course.copy(teacher = schedTeacher)
            } else {
                course
            }
        }

        return CourseColorPalette.assignColorsByCode(enriched)
    }

    fun parseHomeworkJson(
        rawJson: String,
        courseId: String,
        courseName: String,
        nowMillis: Long
    ): List<TaskEntity> {
        val root = parseJsonArrayOrProperty(rawJson) ?: return emptyList()
        return root.mapNotNull { element ->
            val obj = element.asObj() ?: return@mapNotNull null
            val title = obj.str("title", "name", "Title", "homeworkName")?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val rawId = obj.str("id", "homeworkId", "Id", "studentHomeworkId")
            val unit = obj.str("unidad", "unidadName", "unit", "Unidad").orEmpty()
            val contentName = obj.get("content")?.asObj()?.str("name")?.trim().orEmpty()
            val startRaw = obj.str("dateBegin", "dateBeginString", "startDate")
            val dueRaw = obj.str("dateEnd", "dateEndView", "endDate", "DateEnd", "fechaFin")
            val attempts = obj.int("intents", "intent", "attempts") ?: 1
            val fileFilter = obj.str("fileExtensionFilter").orEmpty()
            val isEndedFlag = obj.bool("isEnded") == true
            val description = HtmlCleaner.toPlainText(
                obj.str("description", "detail", "content", "Description")
            ).ifBlank { "Sin descripción adicional." }
            val boolSubmitted = obj.bool("delivered", "isSubmitted", "submitted", "hasDelivery", "isDelivered") == true
            val countSubmitted = (obj.int("homeworkstds", "homeworkStds", "submissionsCount") ?: 0) > 0
            val stateSubmitted = obj.str("status", "state")?.contains("entreg", ignoreCase = true) == true
            val submitted = boolSubmitted || countSubmitted || stateSubmitted

            val remoteId = if (!rawId.isNullOrBlank()) {
                "hw_${courseId}_$rawId"
            } else {
                "hw_${courseId}_${stableHash("$title|${dueRaw.orEmpty()}")}"
            }

            TaskEntity(
                remoteId = remoteId,
                homeworkId = rawId.orEmpty(),
                courseId = courseId,
                courseName = courseName,
                title = title,
                description = description,
                unit = unit,
                topicName = contentName,
                startDateRaw = startRaw,
                dueDateRaw = dueRaw,
                dueTimestamp = DateFormatters.parseToEpochMillis(dueRaw),
                attempts = attempts.coerceAtLeast(1),
                fileFilter = fileFilter,
                isEnded = isEndedFlag,
                isCompletedLocal = submitted,
                isSubmittedRemote = submitted,
                isNew = false,
                lastUpdated = nowMillis
            )
        }
    }

    fun parseForumJson(
        rawJson: String,
        courseId: String,
        courseName: String,
        nowMillis: Long
    ): List<ForumEntity> {
        val root = parseJsonArrayOrProperty(rawJson) ?: return emptyList()
        return root.mapNotNull { element ->
            val obj = element.asObj() ?: return@mapNotNull null
            val title = obj.str("name", "title", "Name", "Title")?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val rawId = obj.str("forumId", "id", "ForumId", "Id")
            val unit = obj.str("unidadName", "unidad", "unit", "UnidadName").orEmpty()
            val startRaw = obj.str("dateStartView", "dateStart", "startDate")
            val closeRaw = obj.str("dateEndView", "dateEnd", "endDate", "DateEndView")
            val answersCount = obj.str("answers")?.toIntOrNull()
                ?: obj.int("answers", "answersCount") ?: 0
            val isEndedFlag = obj.bool("isEnded") == true
            val closeTimestamp = DateFormatters.parseToEpochMillis(closeRaw)
            val isEndedEffective = isEndedFlag || !DateFormatters.isForumActiveNow(
                closeTimestamp = closeTimestamp,
                startDateRaw = startRaw,
                isEnded = isEndedFlag,
                nowMillis = nowMillis
            )
            val description = HtmlCleaner.toPlainText(
                obj.str("description", "content", "topic", "Description")
            )
            val author = obj.str("author", "userName", "teacherName", "createdBy").orEmpty()

            val remoteId = if (!rawId.isNullOrBlank()) {
                "forum_${courseId}_$rawId"
            } else {
                "forum_${courseId}_${stableHash("$title|${closeRaw.orEmpty()}")}"
            }

            ForumEntity(
                remoteId = remoteId,
                rawForumId = rawId.orEmpty(),
                courseId = courseId,
                courseName = courseName,
                title = title,
                unitName = unit,
                description = description,
                startDateRaw = startRaw,
                closeDateRaw = closeRaw,
                closeTimestamp = closeTimestamp,
                author = author,
                answersCount = answersCount.coerceAtLeast(0),
                isEnded = isEndedEffective,
                isNew = false,
                lastUpdated = nowMillis
            )
        }
    }

    fun parseAnnouncementsJson(
        rawJson: String,
        defaultCourseId: String,
        defaultCourseName: String,
        sourcePortal: String,
        nowMillis: Long
    ): List<AnnouncementEntity> {
        val root = parseJsonArrayOrProperty(rawJson) ?: return emptyList()
        return root.mapNotNull { element ->
            val obj = element.asObj() ?: return@mapNotNull null
            val title = obj.str("title", "name", "subject", "Title")?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val content = HtmlCleaner.toPlainText(
                obj.str("description", "content", "body", "message", "Description")
            ).ifBlank { title }
            val dateRaw = obj.str("date", "createdAt", "publishedAt", "dateStart", "CreatedAt")
            val courseId = obj.str("sectionId", "courseId") ?: defaultCourseId
            val courseName = obj.str("courseName", "course") ?: defaultCourseName
            val rawId = obj.str("id", "announcementId", "Id")
            val remoteId = if (!rawId.isNullOrBlank()) {
                "ann_${sourcePortal}_$rawId"
            } else {
                "ann_${sourcePortal}_${stableHash("$title|${dateRaw.orEmpty()}")}"
            }

            AnnouncementEntity(
                remoteId = remoteId,
                courseId = courseId,
                courseName = courseName,
                title = title,
                content = content,
                publishedAtRaw = dateRaw,
                publishedTimestamp = DateFormatters.parseToEpochMillis(dateRaw),
                sourcePortal = sourcePortal,
                lastUpdated = nowMillis
            )
        }
    }

    fun parseAnnouncementsFromHtml(
        html: String,
        sourcePortal: String,
        nowMillis: Long
    ): List<AnnouncementEntity> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val candidates = doc.select(
            ".announcement, .anuncio, .m-widget3__item, .m-timeline-3__item, .notice-item, article"
        )
        return candidates.mapIndexedNotNull { idx, el ->
            val title = el.selectFirst("h1, h2, h3, h4, .title, .m-widget3__username, strong")
                ?.text()?.trim()
            val body = el.selectFirst("p, .m-widget3__text, .content, .description")
                ?.text()?.trim() ?: el.text().trim()
            val finalTitle = title?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
            if (body.length < 4) return@mapIndexedNotNull null
            val dateRaw = el.selectFirst("time, .date, .m-widget3__time, small")?.text()?.trim()
            AnnouncementEntity(
                remoteId = "ann_html_${sourcePortal}_${stableHash("$finalTitle|$idx")}",
                courseId = "general",
                courseName = if (sourcePortal == "intranet") "Intranet UNAP" else "Aula Virtual",
                title = finalTitle,
                content = body,
                publishedAtRaw = dateRaw,
                publishedTimestamp = DateFormatters.parseToEpochMillis(dateRaw),
                sourcePortal = sourcePortal,
                lastUpdated = nowMillis
            )
        }
    }

    fun parseStudentProfile(rawBody: String, fallbackIdentifier: String, nowMillis: Long): StudentEntity? {
        if (rawBody.isBlank()) return null
        val trimmed = rawBody.trim()
        if (trimmed.startsWith("{")) {
            val obj = runCatching { JsonParser.parseString(trimmed).asJsonObject }.getOrNull() ?: return null
            val fullName = obj.str("fullName", "name", "studentName", "nombres")?.takeIf { it.isNotBlank() }
                ?: return null
            return StudentEntity(
                id = obj.str("id", "studentId") ?: fallbackIdentifier,
                code = obj.str("code", "studentCode", "userName") ?: fallbackIdentifier,
                dni = obj.str("dni", "document") ?: "",
                fullName = fullName,
                career = obj.str("career", "careerName", "escuela") ?: "UNAP Puno",
                faculty = obj.str("faculty", "facultyName", "facultad") ?: "",
                semester = obj.str("term", "semester", "termName") ?: "",
                lastUpdated = nowMillis
            )
        }

        val doc = Jsoup.parse(rawBody)
        val fullName = doc.selectFirst(".m-card-user__name, .m-topbar__name, .student-name, .user-name, [data-student-name]")
            ?.text()?.trim()?.takeIf { it.length in 3..80 } ?: return null
        val rawCareer = doc.selectFirst(".m-card-user__email, .career-name, .student-career")
            ?.text()?.trim().orEmpty()
        val career = if (rawCareer.contains("@") || rawCareer.isBlank()) {
            "UNAP Puno"
        } else {
            rawCareer
        }
        return StudentEntity(
            id = fallbackIdentifier,
            code = fallbackIdentifier,
            dni = "",
            fullName = fullName,
            career = career,
            faculty = "",
            semester = "",
            lastUpdated = nowMillis
        )
    }

    fun parseSchedulePayload(rawBody: String, nowMillis: Long): List<ScheduleEntity> {
        if (rawBody.isBlank()) return emptyList()
        val trimmed = rawBody.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val arr = parseJsonArrayOrProperty(trimmed) ?: return emptyList()
            val parsedJson = arr.mapNotNull { el ->
                val obj = el.asObj() ?: return@mapNotNull null
                val rawCourseTitle = obj.str("course", "courseName", "name", "subject", "title")?.trim()
                    ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val (parsedCode, cleanCourseName, parsedSection) = extractCourseCodeAndName(rawCourseTitle)
                val rawStart = obj.str("startTime", "start", "horaInicio", "timeStart") ?: return@mapNotNull null
                val rawEnd = obj.str("endTime", "end", "horaFin", "timeEnd").orEmpty()
                val day = parseDayOfWeek(obj.str("day", "dayOfWeek", "dia") ?: obj.int("dayOfWeek", "day")?.toString())
                    ?: parseIsoDayOfWeek(rawStart)
                    ?: return@mapNotNull null
                val start = extractTimePortion(rawStart) ?: return@mapNotNull null
                val end = extractTimePortion(rawEnd) ?: rawEnd.trim()
                val rawClassroom = obj.str("classroom", "room", "aula", "ambiente", "description").orEmpty().trim()
                val classroom = if (rawClassroom.equals("Sin Asignar", ignoreCase = true)) {
                    "Aula por asignar"
                } else {
                    rawClassroom
                }
                val teacher = obj.str("teacher", "teacherName", "docente").orEmpty().trim()
                val explicitType = obj.str("type", "slotType", "tipo").orEmpty().trim()
                val type = explicitType.ifBlank { parsedSection?.let { "Clase · $it" } ?: "Clase" }
                val courseId = obj.str("sectionId", "courseId")
                    ?: parsedCode?.let { CourseColorPalette.normalizeCode(it) }
                    ?: stableHash(cleanCourseName)

                ScheduleEntity(
                    remoteId = "sched_${stableHash("$courseId|$cleanCourseName|$day|$start|$classroom")}",
                    courseId = courseId,
                    courseName = cleanCourseName,
                    dayOfWeek = day,
                    startTime = start,
                    endTime = end,
                    classroom = classroom,
                    teacher = teacher,
                    slotType = type,
                    isManual = false,
                    lastUpdated = nowMillis
                )
            }
            return parsedJson.distinctBy {
                "${CourseColorPalette.normalizeCode(null, it.courseName)}|${it.dayOfWeek}|${it.startTime}|${it.endTime}"
            }
        }

        // Fallback HTML table parsing for Akdemic Intranet schedule tables
        val doc = Jsoup.parse(rawBody)
        val rows = doc.select("table tr")
        val parsed = mutableListOf<ScheduleEntity>()
        for (row in rows) {
            val cols = row.select("td")
            if (cols.size < 3) continue
            val texts = cols.map { it.text().trim() }
            val day = texts.firstNotNullOfOrNull { parseDayOfWeek(it) } ?: continue
            val timeCol = texts.firstOrNull { it.contains(":") } ?: continue
            val times = timeCol.split("-", "a").map { it.trim() }
            val start = times.getOrNull(0).orEmpty()
            val end = times.getOrNull(1).orEmpty()
            val courseName = texts.firstOrNull {
                it.length > 3 && parseDayOfWeek(it) == null && !it.contains(":")
            } ?: continue
            val classroom = texts.lastOrNull { it != courseName && !it.contains(":") }.orEmpty()

            parsed.add(
                ScheduleEntity(
                    remoteId = "sched_html_${stableHash("$courseName|$day|$start")}",
                    courseId = stableHash(courseName),
                    courseName = courseName,
                    dayOfWeek = day,
                    startTime = start,
                    endTime = end,
                    classroom = classroom,
                    teacher = "",
                    slotType = "Clase",
                    isManual = false,
                    lastUpdated = nowMillis
                )
            )
        }
        return parsed
    }

    fun parseExamsPayload(rawBody: String, defaultCourseId: String = "", defaultCourseName: String = "", nowMillis: Long): List<ExamEntity> {
        if (rawBody.isBlank()) return emptyList()
        val trimmed = rawBody.trim()
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) {
            val doc = Jsoup.parse(rawBody)
            val containers = doc.select(".course-container")
            if (containers.isEmpty()) return emptyList()
            return parseAkdemicCourseGrades(containers, nowMillis)
        }
        val arr = parseJsonArrayOrProperty(trimmed) ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el.asObj() ?: return@mapNotNull null
            val title = obj.str("title", "name", "evaluation", "examName")?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val dateRaw = obj.str("date", "dateStart", "dateEnd", "fecha")
            val courseId = obj.str("sectionId", "courseId") ?: defaultCourseId
            val courseName = obj.str("courseName", "course") ?: defaultCourseName
            val rawId = obj.str("id", "examId", "evaluationId")
            val remoteId = if (!rawId.isNullOrBlank()) {
                "exam_${courseId}_$rawId"
            } else {
                "exam_${courseId}_${stableHash("$title|${dateRaw.orEmpty()}")}"
            }

            ExamEntity(
                remoteId = remoteId,
                courseId = courseId,
                courseName = courseName,
                title = title,
                examType = obj.str("type", "examType", "tipo").orEmpty().ifBlank { "Evaluación" },
                dateRaw = dateRaw,
                timestamp = DateFormatters.parseToEpochMillis(dateRaw),
                classroom = obj.str("classroom", "room", "aula").orEmpty(),
                grade = obj.str("grade", "score", "nota"),
                lastUpdated = nowMillis
            )
        }
    }

    /**
     * Extracts the selected academic period ID and label (e.g. `termId to "2026-II"`)
     * from Intranet `/alumno/notas` HTML `#select-term`.
     */
    fun parseIntranetSelectedTerm(rawHtml: String): Pair<String, String>? {
        if (rawHtml.isBlank()) return null
        val doc = Jsoup.parse(rawHtml)
        val selected = doc.selectFirst("#select-term option[selected], #select-term option")
            ?: return null
        val termId = selected.attr("value").trim().takeIf { it.isNotEmpty() } ?: return null
        val termName = selected.text().trim()
        return termId to termName
    }

    /**
     * Extracts `(department, faculty)` metadata returned by `courseinrole` so the student profile
     * can display their school/faculty when Intranet only provides an email address.
     */
    fun extractDepartmentAndFacultyFromCoursesJson(rawJson: String): Pair<String, String>? {
        val arr = parseJsonArrayOrProperty(rawJson.trim()) ?: return null
        for (element in arr) {
            val obj = element.asObj() ?: continue
            val dept = obj.str("deparment", "department", "school", "career", "escuela")
                ?.trim()?.takeIf { it.isNotEmpty() }
            val faculty = obj.str("faculty", "facultyName", "facultad")
                ?.trim()?.takeIf { it.isNotEmpty() }.orEmpty()
            if (dept != null || faculty.isNotEmpty()) {
                return (dept.orEmpty()) to faculty
            }
        }
        return null
    }

    private fun parseAkdemicCourseContainers(
        containers: org.jsoup.select.Elements,
        semester: String = ""
    ): List<CourseEntity> {
        val parsed = mutableListOf<CourseEntity>()
        for (container in containers) {
            var courseRaw = ""
            var section = ""
            var credits = 0
            var teacher = ""

            for (row in container.select("table tr")) {
                if (row.hasClass("grades")) continue
                val th = row.selectFirst("th")?.text()?.trim()?.lowercase().orEmpty()
                val td = row.selectFirst("td")?.text()?.trim().orEmpty()
                if (th.isEmpty() || td.isEmpty()) continue

                when {
                    th.startsWith("curso") -> courseRaw = td
                    th.startsWith("secc") -> section = td
                    th.startsWith("crédit") || th.startsWith("credit") -> {
                        credits = td.replace(",", ".").toDoubleOrNull()?.roundToInt() ?: 0
                    }
                    th.startsWith("docent") || th.startsWith("prof") -> teacher = td
                }
            }

            if (courseRaw.isBlank()) continue
            val (extractedCode, cleanName, _) = extractCourseCodeAndName(courseRaw)
            val finalCode = extractedCode ?: "INT-${stableHash(cleanName).take(6).uppercase()}"
            val remoteId = extractedCode?.let { CourseColorPalette.normalizeCode(it) }
                ?: "int_${stableHash(cleanName)}"

            parsed.add(
                CourseEntity(
                    remoteId = remoteId,
                    sectionId = remoteId,
                    code = finalCode,
                    name = cleanName,
                    teacher = teacher.ifBlank { section.ifBlank { "Docente asignado" } },
                    credits = credits,
                    semester = semester,
                    lastUpdated = 0L,
                    sourcePortal = "intranet"
                )
            )
        }
        return parsed
    }

    private fun parseAkdemicCourseGrades(
        containers: org.jsoup.select.Elements,
        nowMillis: Long
    ): List<ExamEntity> {
        val parsed = mutableListOf<ExamEntity>()
        val gradeRegex = Regex("""^\d{1,2}([.,]\d{1,2})?$""")
        val dateRegex = Regex("""\d{2}/\d{2}/\d{4}""")

        for (container in containers) {
            var courseRaw = ""
            for (row in container.select("table tr")) {
                val th = row.selectFirst("th")?.text()?.trim()?.lowercase().orEmpty()
                val td = row.selectFirst("td")?.text()?.trim().orEmpty()
                if (th.startsWith("curso") && td.isNotEmpty()) {
                    courseRaw = td
                    break
                }
            }
            if (courseRaw.isBlank()) continue
            val (extractedCode, cleanCourseName, _) = extractCourseCodeAndName(courseRaw)
            val courseId = extractedCode?.let { CourseColorPalette.normalizeCode(it) }
                ?: stableHash(cleanCourseName)

            val gradeRows = container.select("tr.grades")
            for (row in gradeRows) {
                val cells = row.select("td").map { it.text().trim() }.filter { it.isNotBlank() }
                if (cells.isEmpty()) continue
                val dateRaw = cells.firstOrNull { dateRegex.containsMatchIn(it) }
                val gradeCell = cells.lastOrNull { gradeRegex.matches(it.replace(",", ".")) }
                val descriptive = cells.filter {
                    it != dateRaw && it != gradeCell && !it.endsWith("%")
                }
                val title = descriptive.joinToString(" · ").takeIf { it.isNotEmpty() } ?: continue
                val lowerTitle = title.lowercase()
                if (lowerTitle == "evaluación" || lowerTitle == "unidad" || lowerTitle == "nota") continue

                parsed.add(
                    ExamEntity(
                        remoteId = "grade_${courseId}_${stableHash("$cleanCourseName|$title|${dateRaw.orEmpty()}")}",
                        courseId = courseId,
                        courseName = cleanCourseName,
                        title = title,
                        examType = "Nota",
                        dateRaw = dateRaw,
                        timestamp = DateFormatters.parseToEpochMillis(dateRaw),
                        classroom = "",
                        grade = gradeCell,
                        lastUpdated = nowMillis
                    )
                )
            }
        }
        return parsed
    }

    private fun isPlaceholderTeacher(teacher: String): Boolean {
        val trimmed = teacher.trim()
        return trimmed.isEmpty() ||
            trimmed == "Docente asignado" ||
            trimmed.startsWith("GRUPO ", ignoreCase = true) ||
            trimmed.contains("OBLIGATORIO", ignoreCase = true) ||
            trimmed.contains("ELECTIVO", ignoreCase = true)
    }

    private fun extractCourseCodeAndName(rawTitle: String): Triple<String?, String, String?> {
        var working = rawTitle.trim()
        var sectionTag: String? = null
        val sectionRegex = Regex("""\s*\(((?:GRUPO|SEC(?:CIÓN|CION)?)\s*[^)]+)\)\s*$""", RegexOption.IGNORE_CASE)
        sectionRegex.find(working)?.let { match ->
            sectionTag = match.groupValues[1].trim()
            working = working.removeRange(match.range).trim()
        }
        val codePrefixRegex = Regex("""^([A-Za-z]{2,6}[- ]?\d{2,5}[A-Za-z0-9]*)\s*[-:]\s*(.+)$""")
        val codeMatch = codePrefixRegex.find(working)
        return if (codeMatch != null) {
            val code = codeMatch.groupValues[1].trim().uppercase()
            val name = codeMatch.groupValues[2].trim()
            Triple(code, name, sectionTag)
        } else {
            Triple(null, working, sectionTag)
        }
    }

    private fun parseIsoDayOfWeek(raw: String): Int? {
        val datePart = Regex("""^(\d{4}-\d{2}-\d{2})""").find(raw.trim())?.groupValues?.get(1)
            ?: return null
        return runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val date = fmt.parse(datePart) ?: return null
            val cal = Calendar.getInstance().apply { time = date }
            // Calendar.DAY_OF_WEEK: Sun=1, Mon=2..Sat=7 -> convert to ISO Mon=1..Sun=7
            ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        }.getOrNull()
    }

    private fun extractTimePortion(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val isoTime = Regex("""T(\d{2}:\d{2})""").find(trimmed)?.groupValues?.get(1)
        if (isoTime != null) return isoTime
        return Regex("""\b(\d{1,2}:\d{2})\b""").find(trimmed)?.groupValues?.get(1)?.let { t ->
            if (t.length == 4) "0$t" else t
        }
    }

    private fun parseDayOfWeek(raw: String?): Int? {
        val text = raw?.trim()?.lowercase() ?: return null
        text.toIntOrNull()?.let { if (it in 1..7) return it }
        return when {
            text.startsWith("lun") || text == "monday" -> 1
            text.startsWith("mar") || text == "tuesday" -> 2
            text.startsWith("mi") || text == "wednesday" -> 3
            text.startsWith("jue") || text == "thursday" -> 4
            text.startsWith("vie") || text == "friday" -> 5
            text.startsWith("sáb") || text.startsWith("sab") || text == "saturday" -> 6
            text.startsWith("dom") || text == "sunday" -> 7
            else -> null
        }
    }

    internal fun parseJsonArrayOrProperty(rawJson: String): List<JsonElement>? {
        return runCatching {
            val parsed = JsonParser.parseString(rawJson)
            when {
                parsed.isJsonArray -> parsed.asJsonArray.toList()
                parsed.isJsonObject -> {
                    val obj = parsed.asJsonObject
                    val candidateKey = listOf("data", "items", "results", "value", "records")
                        .firstOrNull { obj.has(it) && obj.get(it).isJsonArray }
                    if (candidateKey != null) obj.getAsJsonArray(candidateKey).toList() else null
                }
                else -> null
            }
        }.getOrNull()
    }

    internal fun JsonElement.asObj(): JsonObject? = if (isJsonObject) asJsonObject else null

    internal fun JsonObject.str(vararg keys: String): String? {
        for (k in keys) {
            if (has(k) && !get(k).isJsonNull) {
                val el = get(k)
                if (el.isJsonPrimitive) return el.asString
            }
        }
        return null
    }

    internal fun JsonObject.int(vararg keys: String): Int? {
        for (k in keys) {
            if (has(k) && !get(k).isJsonNull) {
                runCatching { return get(k).asInt }
            }
        }
        return null
    }

    internal fun JsonObject.bool(vararg keys: String): Boolean? {
        for (k in keys) {
            if (has(k) && !get(k).isJsonNull) {
                val el = get(k)
                if (el.isJsonPrimitive && el.asJsonPrimitive.isBoolean) return el.asBoolean
            }
        }
        return null
    }

    fun parseTeachersBySectionJson(rawJson: String): List<String> =
        CourseDetailParsers.parseTeachersBySectionJson(rawJson)

    fun parseCourseUnitsJson(rawJson: String): List<CourseUnitItem> =
        CourseDetailParsers.parseCourseUnitsJson(rawJson)

    fun parseUnitTopicResourcesJson(rawJson: String, baseAvUrl: String): List<CourseResourceItem> =
        CourseDetailParsers.parseUnitTopicResourcesJson(rawJson, baseAvUrl)

    fun parseHomeworkDetailJson(rawJson: String, baseAvUrl: String): HomeworkDetailInfo? =
        CourseDetailParsers.parseHomeworkDetailJson(rawJson, baseAvUrl)

    fun parseForumDetailPrompt(rawBody: String): String =
        CourseDetailParsers.parseForumDetailPrompt(rawBody)

    fun parseForumAnswersJson(rawJson: String): List<ForumReplyItem> =
        CourseDetailParsers.parseForumAnswersJson(rawJson)

    fun parseReadingsJson(rawJson: String, baseAvUrl: String): List<CourseReadingItem> =
        CourseDetailParsers.parseReadingsJson(rawJson, baseAvUrl)

    fun parseQualificationsPayload(
        examsJson: String?,
        homeworksJson: String?,
        forumsJson: String?
    ): List<CourseQualificationItem> =
        CourseDetailParsers.parseQualificationsPayload(examsJson, homeworksJson, forumsJson)

    fun parseParticipantsJson(teachersJson: String?, studentsJson: String?): List<CourseParticipant> =
        CourseDetailParsers.parseParticipantsJson(teachersJson, studentsJson)

    fun parseGroupsJson(rawJson: String?): List<CourseGroupItem> =
        CourseDetailParsers.parseGroupsJson(rawJson)

    fun parseConferencesJson(conferencesJson: String?, virtualClassesJson: String?): List<CourseConferenceItem> =
        CourseDetailParsers.parseConferencesJson(conferencesJson, virtualClassesJson)

    fun parseCalendarEventsJson(rawJson: String?): List<CourseCalendarEvent> =
        CourseDetailParsers.parseCalendarEventsJson(rawJson)

    fun parseHistoryStudentMetaHtml(html: String): Triple<String?, String?, String?> =
        CourseDetailParsers.parseHistoryStudentMetaHtml(html)

    fun parseHistoryPeriodsHtml(html: String): List<Pair<String, String>> =
        CourseDetailParsers.parseHistoryPeriodsHtml(html)

    fun parseHistoryPeriodCoursesJson(rawJson: String): List<AcademicHistoryCourse> =
        CourseDetailParsers.parseHistoryPeriodCoursesJson(rawJson)

    fun parseCurriculumHtml(
        html: String,
        approvedCodes: Set<String> = emptySet(),
        activeCodes: Set<String> = emptySet()
    ): List<CurriculumCycle> =
        CourseDetailParsers.parseCurriculumHtml(html, approvedCodes, activeCodes)

    fun parseAttendanceJson(rawJson: String?): List<CourseAttendanceItem> =
        CourseDetailParsers.parseAttendanceJson(rawJson)

    fun parseAdminRecordsJson(
        debtsJson: String?,
        paymentsJson: String?,
        proceduresJson: String?,
        requestsJson: String?
    ): List<AdminRecordItem> =
        CourseDetailParsers.parseAdminRecordsJson(debtsJson, paymentsJson, proceduresJson, requestsJson)

    fun stableHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
