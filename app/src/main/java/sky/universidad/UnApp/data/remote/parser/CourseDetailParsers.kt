package sky.universidad.UnApp.data.remote.parser

import com.google.gson.JsonParser
import org.jsoup.Jsoup
import sky.universidad.UnApp.core.common.HtmlCleaner
import sky.universidad.UnApp.data.remote.parser.AkdemicParsers.asObj
import sky.universidad.UnApp.data.remote.parser.AkdemicParsers.bool
import sky.universidad.UnApp.data.remote.parser.AkdemicParsers.int
import sky.universidad.UnApp.data.remote.parser.AkdemicParsers.parseJsonArrayOrProperty
import sky.universidad.UnApp.data.remote.parser.AkdemicParsers.str
import sky.universidad.UnApp.domain.model.AcademicHistoryCourse
import sky.universidad.UnApp.domain.model.AdminRecordItem
import sky.universidad.UnApp.domain.model.AttachmentLink
import sky.universidad.UnApp.domain.model.CourseAttendanceItem
import sky.universidad.UnApp.domain.model.CourseCalendarEvent
import sky.universidad.UnApp.domain.model.CourseConferenceItem
import sky.universidad.UnApp.domain.model.CourseGroupItem
import sky.universidad.UnApp.domain.model.CourseParticipant
import sky.universidad.UnApp.domain.model.CourseQualificationItem
import sky.universidad.UnApp.domain.model.CourseReadingItem
import sky.universidad.UnApp.domain.model.CourseResourceItem
import sky.universidad.UnApp.domain.model.CourseResourceType
import sky.universidad.UnApp.domain.model.CourseUnitItem
import sky.universidad.UnApp.domain.model.CourseWeekTopic
import sky.universidad.UnApp.domain.model.CurriculumCourseItem
import sky.universidad.UnApp.domain.model.CurriculumCycle
import sky.universidad.UnApp.domain.model.ForumReplyItem
import sky.universidad.UnApp.domain.model.HomeworkDetailInfo
import sky.universidad.UnApp.domain.model.StudentDeliveryItem

/**
 * Parsers for Akdemic Aula Virtual per-course detail endpoints:
 * teachers, units & weekly contents, homework details, forum discussions,
 * readings, qualifications, chat directories, groups, conferences, and calendar.
 */
internal object CourseDetailParsers {

    fun parseTeachersBySectionJson(rawJson: String): List<String> {
        val arr = parseJsonArrayOrProperty(rawJson) ?: return emptyList()
        return arr.mapNotNull { el ->
            el.asObj()?.str("name", "teacherName", "fullName", "nombre")?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.distinct()
    }

    fun parseCourseUnitsJson(rawJson: String): List<CourseUnitItem> {
        val arr = parseJsonArrayOrProperty(rawJson) ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el.asObj() ?: return@mapNotNull null
            val unitId = obj.str("id", "unitId") ?: return@mapNotNull null
            val unitName = obj.str("name", "title")?.trim()?.takeIf { it.isNotEmpty() } ?: "Unidad"
            val contentsArray = obj.get("contents")?.takeIf { it.isJsonArray }?.asJsonArray
            val topics = contentsArray?.mapNotNull { cEl ->
                val cObj = cEl.asObj() ?: return@mapNotNull null
                val contentId = cObj.str("id", "contentId") ?: return@mapNotNull null
                val topicTitle = cObj.str("name", "title")?.trim().orEmpty().ifBlank { "Semana / Tema" }
                val topicDesc = HtmlCleaner.toPlainText(cObj.str("description", "detail"))
                CourseWeekTopic(
                    id = contentId,
                    name = topicTitle,
                    description = topicDesc,
                    resources = emptyList()
                )
            }.orEmpty()

            CourseUnitItem(
                id = unitId,
                name = unitName,
                topics = topics
            )
        }
    }

    fun parseUnitTopicResourcesJson(rawJson: String, baseAvUrl: String): List<CourseResourceItem> {
        val arr = parseJsonArrayOrProperty(rawJson) ?: return emptyList()
        val base = baseAvUrl.trimEnd('/')
        return arr.mapNotNull { el ->
            val obj = el.asObj() ?: return@mapNotNull null
            if (obj.bool("show") == false) return@mapNotNull null
            val id = obj.str("id") ?: return@mapNotNull null
            val title = obj.str("name", "title")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val activity = obj.str("activity").orEmpty().lowercase()
            val intType = obj.int("type") ?: 0
            val rawUrl = obj.str("url")?.trim()
            val dateRaw = obj.str("date")

            val resolvedType = when (activity) {
                "homework" -> CourseResourceType.HOMEWORK
                "forum" -> CourseResourceType.FORUM
                "exam", "evaluation" -> CourseResourceType.EXAM
                "reading" -> CourseResourceType.READING
                "class", "conference", "virtualclass" -> CourseResourceType.VIRTUAL_CLASS
                else -> if (intType == 1) CourseResourceType.EXTERNAL_LINK else CourseResourceType.DOCUMENT
            }

            val ext = rawUrl
                ?.substringBefore("?")
                ?.substringAfterLast('.', "")
                ?.lowercase()
                ?.takeIf { it.length in 2..6 }
                .orEmpty()

            val resolvedUrl = when (resolvedType) {
                CourseResourceType.EXTERNAL_LINK -> normalizeExternalUrl(rawUrl)
                CourseResourceType.DOCUMENT -> {
                    when {
                        ext == "pdf" -> "$base/web/unit/archivo/$id"
                        !rawUrl.isNullOrBlank() -> resolveAssetUrl(rawUrl, "$base/web/unit/archivo/$id", base)
                        else -> "$base/web/unit/archivo/$id"
                    }
                }
                CourseResourceType.READING -> "$base/web/reading/archivo/$id"
                else -> rawUrl?.let { resolveAssetUrl(it, it, base) }
            }

            val typeLabel = when (resolvedType) {
                CourseResourceType.DOCUMENT -> when (ext) {
                    "pdf" -> "Documento PDF"
                    "doc", "docx" -> "Documento Word (.${ext})"
                    "xls", "xlsx" -> "Hoja de cálculo (.${ext})"
                    "ppt", "pptx" -> "Presentación (.${ext})"
                    "csv" -> "Conjunto de datos (.csv)"
                    "ipynb" -> "Cuaderno Python (.ipynb)"
                    "png", "jpg", "jpeg" -> "Imagen (.${ext})"
                    "zip", "rar" -> "Archivo comprimido (.${ext})"
                    else -> "Archivo adjunto"
                }
                CourseResourceType.EXTERNAL_LINK -> classifyExternalLinkLabel(resolvedUrl)
                CourseResourceType.HOMEWORK -> "Tarea"
                CourseResourceType.FORUM -> "Foro"
                CourseResourceType.EXAM -> "Evaluación"
                CourseResourceType.READING -> "Lectura PDF"
                CourseResourceType.VIRTUAL_CLASS -> "Clase virtual"
            }

            CourseResourceItem(
                id = id,
                title = title,
                subtitle = typeLabel,
                type = resolvedType,
                url = resolvedUrl,
                dateRaw = dateRaw,
                fileExtension = if (resolvedType == CourseResourceType.READING) "pdf" else ext
            )
        }
    }

    fun parseHomeworkDetailJson(rawJson: String, baseAvUrl: String): HomeworkDetailInfo? {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("{")) return null
        val obj = runCatching { JsonParser.parseString(trimmed).asJsonObject }.getOrNull() ?: return null
        val base = baseAvUrl.trimEnd('/')

        val id = obj.str("id").orEmpty()
        val title = obj.str("title", "name")?.trim().orEmpty().ifBlank { return null }
        val description = HtmlCleaner.toPlainText(obj.str("description")).ifBlank { "Sin descripción detallada." }
        val dateBegin = obj.str("dateBeginString", "dateBegin").orEmpty()
        val dateEnd = obj.str("dateEndString", "dateEnd").orEmpty()
        val attempts = (obj.int("intent", "intents") ?: 1).coerceAtLeast(1)
        val fileFilter = obj.str("fileExtensionFilter").orEmpty()
        val isGroup = obj.bool("isGroup") == true

        val teacherFiles = obj.get("files")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { fEl ->
            val fObj = fEl.asObj() ?: return@mapNotNull null
            val fId = fObj.str("id").orEmpty()
            val fName = fObj.str("name", "fileName", "title")?.trim().orEmpty().ifBlank { "Archivo adjunto" }
            val fUrl = fObj.str("url")?.let { resolveAssetUrl(it, it, base) }
                ?: fId.takeIf { it.isNotBlank() }?.let { "$base/web/homework/archivo/$it" }
            AttachmentLink(id = fId, name = fName, url = fUrl)
        }.orEmpty()

        val studentDeliveries = obj.get("previousFiles")?.takeIf { it.isJsonArray }?.asJsonArray?.mapIndexedNotNull { idx, dEl ->
            val dObj = dEl.asObj() ?: return@mapIndexedNotNull null
            val dId = dObj.str("id").orEmpty().ifBlank { "prev_$idx" }
            val dDate = dObj.str("date", "dateView").orEmpty().ifBlank { "Entrega registrada" }
            val dName = dObj.str("name", "fileName")
            val dUrl = dObj.str("url")?.let { resolveAssetUrl(it, it, base) }
                ?: dObj.str("id")?.let { "$base/web/homework/archivo-alumno/$it" }
            StudentDeliveryItem(id = dId, dateLabel = dDate, fileName = dName, url = dUrl)
        }.orEmpty()

        val rubricScore = obj.get("rubrics")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.firstOrNull()?.asObj()?.let { r ->
                val desc = r.str("description").orEmpty().ifBlank { "Puntaje" }
                val score = r.str("score").orEmpty()
                if (score.isNotBlank()) "$desc: $score" else desc
            }

        return HomeworkDetailInfo(
            id = id,
            title = title,
            description = description,
            dateBegin = dateBegin,
            dateEnd = dateEnd,
            attempts = attempts,
            fileFilter = fileFilter,
            isGroup = isGroup,
            teacherFiles = teacherFiles,
            studentDeliveries = studentDeliveries,
            rubricScore = rubricScore
        )
    }

    fun parseForumDetailPrompt(rawBody: String): String {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("{")) {
            val obj = runCatching { JsonParser.parseString(trimmed).asJsonObject }.getOrNull()
            if (obj != null) {
                val rawDesc = obj.str("description", "message", "mensaje", "content")
                if (!rawDesc.isNullOrBlank()) return HtmlCleaner.toPlainText(rawDesc)
            }
        }
        val doc = Jsoup.parse(rawBody)
        val promptEl = doc.selectFirst(".forum-description, .m-portlet__body, .card-body, p")
        return HtmlCleaner.toPlainText(promptEl?.html() ?: rawBody)
    }

    fun parseForumAnswersJson(rawJson: String): List<ForumReplyItem> {
        val arr = parseJsonArrayOrProperty(rawJson) ?: return emptyList()
        return arr.mapIndexedNotNull { idx, el ->
            val obj = el.asObj() ?: return@mapIndexedNotNull null
            val id = obj.str("forumChildId", "id") ?: "reply_$idx"
            val user = obj.str("userName", "nombre", "author")?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Estudiante"
            val dateView = obj.str("dateView", "date").orEmpty()
            val message = HtmlCleaner.toPlainText(obj.str("mensaje", "message", "content"))
                .ifBlank { "(Sin contenido textual)" }
            val rated = obj.bool("rated") == true
            val scoreStr = if (rated) obj.str("score") else null
            ForumReplyItem(
                id = id,
                userName = user,
                dateView = dateView,
                message = message,
                score = scoreStr
            )
        }
    }

    fun parseReadingsJson(rawJson: String, baseAvUrl: String): List<CourseReadingItem> {
        val arr = parseJsonArrayOrProperty(rawJson) ?: return emptyList()
        val base = baseAvUrl.trimEnd('/')
        return arr.mapNotNull { el ->
            val obj = el.asObj() ?: return@mapNotNull null
            val id = obj.str("id") ?: return@mapNotNull null
            val title = obj.str("title", "name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val unit = obj.str("unit", "unidad").orEmpty()
            val desc = HtmlCleaner.toPlainText(obj.str("description")).ifBlank { "Material de lectura" }
            val type = obj.str("type").orEmpty().ifBlank { "Lectura" }
            val dateRaw = obj.str("date")
            val rawUrl = obj.str("url")
            val fileUrl = resolveAssetUrl(rawUrl, "$base/web/reading/archivo/$id", base)
            CourseReadingItem(
                id = id,
                title = title,
                unit = unit,
                description = desc,
                type = type,
                dateRaw = dateRaw,
                fileUrl = fileUrl
            )
        }
    }

    fun parseQualificationsPayload(
        examsJson: String?,
        homeworksJson: String?,
        forumsJson: String?
    ): List<CourseQualificationItem> {
        val result = mutableListOf<CourseQualificationItem>()

        examsJson?.let { parseJsonArrayOrProperty(it) }?.forEachIndexed { idx, el ->
            val obj = el.asObj() ?: return@forEachIndexed
            val title = obj.str("nombreExam", "name", "title")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEachIndexed
            val id = obj.str("examId", "id") ?: "q_ex_$idx"
            val unit = obj.str("unit", "unidad").orEmpty()
            val score = obj.str("score", "qualification", "note").orEmpty()
            result += CourseQualificationItem(
                id = "q_ex_$id",
                title = title,
                category = "Examen",
                unit = unit,
                score = score.ifBlank { "-" },
                status = if (score.isNotBlank()) "Calificado" else "Pendiente"
            )
        }

        homeworksJson?.let { parseJsonArrayOrProperty(it) }?.forEachIndexed { idx, el ->
            val obj = el.asObj() ?: return@forEachIndexed
            val title = obj.str("title", "name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEachIndexed
            val id = obj.str("id", "homeworkId") ?: "q_hw_$idx"
            val unit = obj.str("unidad", "unit").orEmpty()
            val score = obj.str("score", "qualification", "note").orEmpty()
            val state = obj.str("state", "status").orEmpty()
            result += CourseQualificationItem(
                id = "q_hw_$id",
                title = title,
                category = "Tarea",
                unit = unit,
                score = score.ifBlank { "-" },
                status = state.ifBlank { if (score.isNotBlank()) "Calificado" else "No calificado" }
            )
        }

        forumsJson?.let { parseJsonArrayOrProperty(it) }?.forEachIndexed { idx, el ->
            val obj = el.asObj() ?: return@forEachIndexed
            val title = obj.str("name", "title")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEachIndexed
            val id = obj.str("evaluationId", "forumId", "id") ?: "q_fo_$idx"
            val unit = obj.str("unit", "unidad").orEmpty()
            val score = obj.str("qualification", "score").orEmpty()
            val state = obj.str("state", "status").orEmpty()
            result += CourseQualificationItem(
                id = "q_fo_$id",
                title = title,
                category = "Foro",
                unit = unit,
                score = score.ifBlank { "-" },
                status = state.ifBlank { if (score.isNotBlank()) "Calificado" else "No calificado" }
            )
        }

        return result.distinctBy { it.id }
    }

    fun parseParticipantsJson(teachersJson: String?, studentsJson: String?): List<CourseParticipant> {
        val list = mutableListOf<CourseParticipant>()
        teachersJson?.let { parseJsonArrayOrProperty(it) }?.forEachIndexed { idx, el ->
            val obj = el.asObj() ?: return@forEachIndexed
            val name = obj.str("nombre", "name", "fullName")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEachIndexed
            val id = obj.str("userId", "teacherId", "id") ?: "t_$idx"
            list += CourseParticipant(id = "t_$id", name = name, role = "Docente")
        }
        studentsJson?.let { parseJsonArrayOrProperty(it) }?.forEachIndexed { idx, el ->
            val obj = el.asObj() ?: return@forEachIndexed
            val name = obj.str("nombre", "name", "fullName")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEachIndexed
            val id = obj.str("userId", "studentId", "id") ?: "s_$idx"
            list += CourseParticipant(id = "s_$id", name = name, role = "Estudiante")
        }
        return list.distinctBy { it.id }
    }

    fun parseGroupsJson(rawJson: String?): List<CourseGroupItem> {
        val arr = rawJson?.let { parseJsonArrayOrProperty(it) } ?: return emptyList()
        return arr.mapIndexedNotNull { idx, el ->
            val obj = el.asObj() ?: return@mapIndexedNotNull null
            val name = obj.str("name", "groupName", "title")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapIndexedNotNull null
            val id = obj.str("id", "groupId") ?: "grp_$idx"
            val membersCount = obj.int("membersCount", "studentsCount", "members")
                ?: obj.get("members")?.takeIf { it.isJsonArray }?.asJsonArray?.size()
                ?: 0
            val description = HtmlCleaner.toPlainText(obj.str("description"))
            CourseGroupItem(id = id, name = name, membersCount = membersCount, description = description)
        }
    }

    fun parseConferencesJson(
        conferencesJson: String?,
        virtualClassesJson: String?
    ): List<CourseConferenceItem> {
        val list = mutableListOf<CourseConferenceItem>()
        listOfNotNull(conferencesJson, virtualClassesJson).forEach { raw ->
            parseJsonArrayOrProperty(raw)?.forEachIndexed { idx, el ->
                val obj = el.asObj() ?: return@forEachIndexed
                val title = obj.str("name", "title", "topic")?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@forEachIndexed
                val id = obj.str("id", "conferenceId") ?: "conf_${list.size}_$idx"
                val dateRaw = obj.str("dateStartView", "startDate", "date", "createdAt")
                val url = obj.str("url", "link", "meetUrl")
                val status = obj.str("state", "status").orEmpty()
                list += CourseConferenceItem(id = id, title = title, dateRaw = dateRaw, url = url, status = status)
            }
        }
        return list.distinctBy { it.title }
    }

    fun parseCalendarEventsJson(rawJson: String?): List<CourseCalendarEvent> {
        val arr = rawJson?.let { parseJsonArrayOrProperty(it) } ?: return emptyList()
        return arr.mapIndexedNotNull { idx, el ->
            val obj = el.asObj() ?: return@mapIndexedNotNull null
            val title = obj.str("title", "name", "description")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapIndexedNotNull null
            val id = obj.str("id") ?: "cal_$idx"
            val startRaw = obj.str("start", "dateStart", "date")
            val endRaw = obj.str("end", "dateEnd")
            val description = HtmlCleaner.toPlainText(obj.str("description"))
            CourseCalendarEvent(
                id = id,
                title = title,
                startRaw = startRaw,
                endRaw = endRaw,
                description = description
            )
        }
    }

    fun parseHistoryStudentMetaHtml(html: String): Triple<String?, String?, String?> {
        if (html.isBlank()) return Triple(null, null, null)
        val doc = Jsoup.parse(html)
        var fullName: String? = null
        var code: String? = null
        var dni: String? = null
        for (block in doc.select(".m-portlet__body .row > div")) {
            val labels = block.select("label").map { it.text().trim() }.filter { it.isNotBlank() }
            if (labels.size >= 2) {
                val key = labels[0].lowercase()
                val value = labels[1]
                when {
                    key.contains("estudiante") -> fullName = value
                    key.contains("código") || key.contains("codigo") -> code = value
                    key.contains("dni") -> dni = value
                }
            }
        }
        return Triple(fullName, code, dni)
    }

    fun parseHistoryPeriodsHtml(html: String): List<Pair<String, String>> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        return doc.select(".m-accordion__item").mapNotNull { item ->
            val title = item.selectFirst(".m-accordion__item-title")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val pid = item.selectFirst("[data-pid]")?.attr("data-pid")?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            title to pid
        }
    }

    fun parseHistoryPeriodCoursesJson(rawJson: String): List<AcademicHistoryCourse> {
        val arr = parseJsonArrayOrProperty(rawJson) ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el.asObj() ?: return@mapNotNull null
            val course = obj.str("course", "courseName", "name")?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val academicYear = obj.int("academicYear", "level", "cycle") ?: 0
            val credits = obj.str("credits") ?: obj.int("credits")?.toString() ?: "-"
            val finalGrade = obj.str("finalGrade", "grade", "note").orEmpty().ifBlank { "-" }
            val attempts = obj.int("try", "attempts") ?: 1
            val approved = obj.bool("notDisapproved") ?: true
            AcademicHistoryCourse(
                course = course,
                academicYear = academicYear,
                credits = credits,
                finalGrade = finalGrade,
                attempts = attempts,
                approved = approved
            )
        }
    }

    fun parseCurriculumHtml(
        html: String,
        approvedCodes: Set<String> = emptySet(),
        activeCodes: Set<String> = emptySet()
    ): List<CurriculumCycle> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html)
        val cycles = mutableListOf<CurriculumCycle>()
        for (content in doc.select(".m-section__content")) {
            val title = content.selectFirst("h5, h4, h3")?.text()?.trim().orEmpty()
                .ifBlank { "Ciclo ${cycles.size + 1}" }
            val rows = content.select("tbody tr")
            val courses = rows.mapNotNull { tr ->
                val cols = tr.select("td").map { it.text().trim() }
                if (cols.size < 3) return@mapNotNull null
                val code = cols.getOrNull(0).orEmpty()
                val name = cols.getOrNull(1).orEmpty()
                if (name.isBlank()) return@mapNotNull null
                val credits = cols.getOrNull(2).orEmpty()
                val hoursSummary = if (cols.size >= 7) {
                    "HT:${cols[5]} HP:${cols[3]} HV:${cols[6]}"
                } else ""
                val prereq = cols.lastOrNull().orEmpty()
                val normCode = code.uppercase().replace(Regex("[^A-Z0-9]"), "")
                val status = when {
                    normCode in activeCodes -> "En curso"
                    normCode in approvedCodes -> "Aprobado"
                    else -> ""
                }
                CurriculumCourseItem(
                    code = code,
                    name = name,
                    credits = credits,
                    hoursSummary = hoursSummary,
                    prerequisites = prereq,
                    status = status
                )
            }
            if (courses.isNotEmpty()) {
                cycles += CurriculumCycle(cycleName = title, courses = courses)
            }
        }
        return cycles
    }

    fun parseAttendanceJson(rawJson: String?): List<CourseAttendanceItem> {
        val arr = rawJson?.let { parseJsonArrayOrProperty(it) } ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el.asObj() ?: return@mapNotNull null
            val name = obj.str("courseName", "course", "name")?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            CourseAttendanceItem(
                sectionId = obj.str("sectionId", "id").orEmpty(),
                courseName = name,
                totalClasses = obj.int("classCount") ?: 0,
                maxAbsences = obj.int("maxAbsences") ?: 0,
                dictated = obj.int("dictated") ?: 0,
                assisted = obj.int("assisted") ?: 0,
                absences = obj.int("absences") ?: 0
            )
        }
    }

    fun parseAdminRecordsJson(
        debtsJson: String?,
        paymentsJson: String?,
        proceduresJson: String?,
        requestsJson: String?
    ): List<AdminRecordItem> {
        val out = mutableListOf<AdminRecordItem>()
        fun appendList(raw: String?, category: String) {
            val arr = raw?.let { parseJsonArrayOrProperty(it) } ?: return
            arr.forEachIndexed { idx, el ->
                val obj = el.asObj() ?: return@forEachIndexed
                val concept = obj.str("concept", "type", "description", "title", "name")
                    ?.trim()?.takeIf { it.isNotEmpty() } ?: category
                val date = obj.str("issueDate", "paymentDate", "createdAt", "date").orEmpty()
                val amount = obj.str("totalamount", "amount", "total", "status", "state")
                    ?: obj.str("code")
                    ?: "Registrado"
                out += AdminRecordItem(
                    id = "${category}_${idx}_${concept.hashCode()}",
                    category = category,
                    concept = concept,
                    dateLabel = date,
                    amountOrStatus = amount
                )
            }
        }
        appendList(debtsJson, "Deuda pendiente")
        appendList(proceduresJson, "Trámite")
        appendList(requestsJson, "Solicitud")
        appendList(paymentsJson, "Pago realizado")
        return out
    }

    private fun normalizeExternalUrl(rawUrl: String?): String? {
        val cleaned = rawUrl?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        if (cleaned.startsWith("http://", ignoreCase = true) || cleaned.startsWith("https://", ignoreCase = true)) {
            return cleaned
        }
        if (cleaned.startsWith("//")) return "https:$cleaned"
        return "https://$cleaned"
    }

    private fun classifyExternalLinkLabel(url: String?): String {
        val u = url.orEmpty().lowercase()
        return when {
            "docs.google.com/document" in u -> "Google Docs"
            "docs.google.com/spreadsheets" in u -> "Hoja de cálculo Google"
            "drive.google.com" in u -> "Google Drive"
            "canva" in u -> "Presentación Canva"
            "colab.research.google.com" in u -> "Cuaderno Google Colab"
            "kaggle.com" in u -> "Dataset Kaggle"
            "youtube.com" in u || "youtu.be" in u -> "Video YouTube"
            u.endsWith(".pdf") -> "Enlace PDF externo"
            else -> "Enlace externo"
        }
    }

    private fun resolveAssetUrl(rawUrl: String?, fallbackUrl: String, baseAvUrl: String): String {
        val cleaned = rawUrl?.trim().orEmpty()
        if (cleaned.isEmpty()) return fallbackUrl
        if (cleaned.startsWith("http://", ignoreCase = true) || cleaned.startsWith("https://", ignoreCase = true)) {
            return cleaned
        }
        if (cleaned.startsWith("/")) {
            return "$baseAvUrl$cleaned"
        }
        return "$baseAvUrl/archivos/$cleaned"
    }
}
