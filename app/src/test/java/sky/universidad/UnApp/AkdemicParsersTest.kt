package sky.universidad.UnApp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sky.universidad.UnApp.data.local.entities.CourseEntity
import sky.universidad.UnApp.data.local.entities.ScheduleEntity
import sky.universidad.UnApp.data.remote.parser.AkdemicParsers
import sky.universidad.UnApp.domain.model.CourseResourceType

class AkdemicParsersTest {

    @Test
    fun parseCoursesJson_parsesAkdemicCourseList() {
        val json = """
            [
              {
                "sectionId": "4421",
                "code": "IS-501",
                "name": "  Ingeniería de Software II  ",
                "teacherName": "Dr. Juan Perez",
                "credits": 4,
                "termName": "2026-II"
              }
            ]
        """.trimIndent()

        val courses = AkdemicParsers.parseCoursesJson(json, 1000L)
        assertEquals(1, courses.size)
        assertEquals("4421", courses[0].sectionId)
        assertEquals("Ingeniería de Software II", courses[0].name)
        assertEquals("IS-501", courses[0].code)
        assertEquals("Dr. Juan Perez", courses[0].teacher)
        assertEquals(4, courses[0].credits)
    }

    @Test
    fun parseHomeworkJson_cleansHtmlAndGeneratesStableRemoteId() {
        val json = """
            [
              {
                "id": 98,
                "title": "Avance 1",
                "unidad": "Unidad I",
                "dateEnd": "20/11/2026 23:59",
                "description": "<p>Subir informe en <strong>PDF</strong>.</p><br/>Máximo 10 MB.",
                "delivered": false
              }
            ]
        """.trimIndent()

        val tasks = AkdemicParsers.parseHomeworkJson(json, "4421", "Ingeniería de Software II", 1000L)
        assertEquals(1, tasks.size)
        val task = tasks[0]
        assertEquals("hw_4421_98", task.remoteId)
        assertTrue(task.description.contains("Subir informe en PDF."))
        assertTrue(task.description.contains("Máximo 10 MB."))
        assertFalse(task.description.contains("<p>"))
        assertFalse(task.isCompletedLocal)
        assertTrue(task.dueTimestamp != null)
    }

    @Test
    fun parseForumJson_mapsUnidadAndCloseDate() {
        val json = """
            [
              {
                "id": "12",
                "name": "Foro de arquitectura",
                "unidadName": "Unidad II",
                "dateEndView": "25/11/2026 18:00"
              }
            ]
        """.trimIndent()

        val forums = AkdemicParsers.parseForumJson(json, "4421", "Ingeniería de Software II", 1000L)
        assertEquals(1, forums.size)
        assertEquals("forum_4421_12", forums[0].remoteId)
        assertEquals("Foro de arquitectura", forums[0].title)
        assertEquals("Unidad II", forums[0].unitName)
    }

    @Test
    fun parseSchedulePayload_supportsHtmlTables() {
        val html = """
            <table>
              <tr><th>Día</th><th>Hora</th><th>Curso</th><th>Aula</th></tr>
              <tr><td>Lunes</td><td>08:00 - 10:00</td><td>Arquitectura de Software</td><td>Lab 02</td></tr>
              <tr><td>Miércoles</td><td>14:00 - 16:00</td><td>Redes II</td><td>Aula 305</td></tr>
            </table>
        """.trimIndent()

        val slots = AkdemicParsers.parseSchedulePayload(html, 1000L)
        assertEquals(2, slots.size)
        assertEquals(1, slots[0].dayOfWeek)
        assertEquals("08:00", slots[0].startTime)
        assertEquals("10:00", slots[0].endTime)
        assertEquals("Arquitectura de Software", slots[0].courseName)
        assertEquals("Lab 02", slots[0].classroom)
        assertEquals(3, slots[1].dayOfWeek)
    }

    @Test
    fun parseIntranetCoursesPayload_parsesHtmlTableAndJson() {
        val html = """
            <table>
              <tr><th>Código</th><th>Asignatura</th><th>Créditos</th><th>Docente</th></tr>
              <tr><td>IS-401</td><td>Arquitectura de Software</td><td>4</td><td>Ing. Carlos Quispe</td></tr>
              <tr><td>IS-407</td><td>Proyecto de Tesis I</td><td>4</td><td>Dr. Jorge Velásquez</td></tr>
            </table>
        """.trimIndent()
        val htmlCourses = AkdemicParsers.parseIntranetCoursesPayload(html, 1000L)
        assertEquals(2, htmlCourses.size)
        assertEquals("IS-401", htmlCourses[0].code)
        assertEquals(4, htmlCourses[0].credits)
        assertEquals("intranet", htmlCourses[0].sourcePortal)

        val json = """
            {
              "data": [
                {"courseCode": "IS-408", "courseName": "Auditoría de Sistemas", "credits": 3}
              ]
            }
        """.trimIndent()
        val jsonCourses = AkdemicParsers.parseIntranetCoursesPayload(json, 1000L)
        assertEquals(1, jsonCourses.size)
        assertEquals("IS-408", jsonCourses[0].code)
        assertEquals("Auditoría de Sistemas", jsonCourses[0].name)
    }

    @Test
    fun mergeCoursesByCodeAndAssignColors_mapsPortalsAndAssignsDistinctColors() {
        val aulaCourses = (1..6).map { idx ->
            CourseEntity(
                remoteId = "sec_$idx",
                sectionId = "sec_$idx",
                code = if (idx == 1) "SEC-1" else "IS-40$idx",
                name = if (idx == 1) "Arquitectura de Software" else "Curso $idx",
                teacher = "Docente asignado",
                credits = 0,
                semester = "2026-II",
                lastUpdated = 1000L,
                sourcePortal = "aulavirtual"
            )
        }
        val intranetCourses = listOf(
            CourseEntity(
                remoteId = "intra_401",
                sectionId = "intra_401",
                code = "IS-401",
                name = "Arquitectura de Software",
                teacher = "Ing. Carlos Quispe",
                credits = 4,
                semester = "2026-II",
                lastUpdated = 1000L,
                sourcePortal = "intranet"
            ),
            CourseEntity(
                remoteId = "intra_407",
                sectionId = "intra_407",
                code = "IS-407",
                name = "Proyecto de Tesis I",
                teacher = "Dr. Jorge Velásquez",
                credits = 4,
                semester = "2026-II",
                lastUpdated = 1000L,
                sourcePortal = "intranet"
            )
        )
        val schedules = listOf(
            ScheduleEntity(
                remoteId = "s_1",
                courseId = "sec_2",
                courseName = "Curso 2",
                dayOfWeek = 5,
                startTime = "14:00",
                endTime = "16:00",
                classroom = "A-101",
                teacher = "Ing. Cruz",
                slotType = "Teoría",
                isManual = false,
                lastUpdated = 1000L
            )
        )

        val merged = AkdemicParsers.mergeCoursesByCodeAndAssignColors(
            aulaCourses = aulaCourses,
            intranetCourses = intranetCourses,
            schedules = schedules,
            nowMillis = 1000L
        )

        assertEquals(7, merged.size)
        val arq = merged.first { it.remoteId == "sec_1" }
        assertEquals("IS-401", arq.code)
        assertEquals("ambos", arq.sourcePortal)
        assertEquals("Ing. Carlos Quispe", arq.teacher)
        assertEquals(4, arq.credits)

        val curso2 = merged.first { it.remoteId == "sec_2" }
        assertEquals("Ing. Cruz", curso2.teacher)

        val tesis = merged.first { it.code == "IS-407" }
        assertEquals("intranet", tesis.sourcePortal)

        val uniqueColors = merged.map { it.colorHex }.toSet()
        assertEquals(7, uniqueColors.size)
        assertTrue(uniqueColors.all { it.startsWith("#") && it.length == 7 })
    }

    @Test
    fun parseCoursesJson_readsCodeCourseAndSectionFallback() {
        val json = """
            [
              {
                "id": "u1",
                "codeCourse": "SIS323",
                "sectionId": "08def7b6-294b-4c9f-8597-2ed87d2035f3",
                "codeSection": "GRUPO B",
                "name": "ANÁLISIS DE DATOS",
                "type": "OBLIGATORIO",
                "deparment": "INGENIERIA DE SISTEMAS",
                "faculty": "INGENIERIA MECANICA ELECTRICA, ELECTRONICA Y SISTEMAS"
              }
            ]
        """.trimIndent()

        val courses = AkdemicParsers.parseCoursesJson(json, 1000L)
        assertEquals(1, courses.size)
        assertEquals("SIS323", courses[0].code)
        assertEquals("ANÁLISIS DE DATOS", courses[0].name)
        assertEquals("GRUPO B · OBLIGATORIO", courses[0].teacher)

        val deptFaculty = AkdemicParsers.extractDepartmentAndFacultyFromCoursesJson(json)
        assertEquals("INGENIERIA DE SISTEMAS", deptFaculty?.first)
        assertTrue(deptFaculty?.second?.contains("SISTEMAS") == true)
    }

    @Test
    fun parseHomeworkJson_marksSubmittedWhenHomeworkStdsPositive() {
        val json = """
            [
              {
                "id": "hw-1",
                "title": "Plantillas resueltas",
                "unidad": "UNIDAD 1",
                "dateEnd": "15/10/2026 17:00",
                "description": "Adjunte las plantillas.",
                "homeworkstds": 1
              }
            ]
        """.trimIndent()

        val tasks = AkdemicParsers.parseHomeworkJson(json, "sec_1", "SIS323", 1000L)
        assertEquals(1, tasks.size)
        assertTrue(tasks[0].isCompletedLocal)
        assertTrue(tasks[0].isSubmittedRemote)
    }

    @Test
    fun parseSchedulePayload_supportsFullCalendarEvents() {
        val json = """
            [
              {
                "id": "08defa19-1",
                "title": "SIS232-PROYECTO DE TESIS EN INGENIERÍA DE SISTEMAS (GRUPO B)",
                "description": "Sin Asignar",
                "allDay": false,
                "start": "2026-11-16T17:00:00",
                "end": "2026-11-16T19:00:00"
              }
            ]
        """.trimIndent()

        val slots = AkdemicParsers.parseSchedulePayload(json, 1000L)
        assertEquals(1, slots.size)
        assertEquals(1, slots[0].dayOfWeek)
        assertEquals("17:00", slots[0].startTime)
        assertEquals("19:00", slots[0].endTime)
        assertEquals("PROYECTO DE TESIS EN INGENIERÍA DE SISTEMAS", slots[0].courseName)
        assertEquals("Aula por asignar", slots[0].classroom)
        assertEquals("SIS232", slots[0].courseId)
    }

    @Test
    fun parseIntranetNotasPeriodoHtml_extractsTermCoursesAndGrades() {
        val selectorHtml = """
            <select id="select-term">
              <option value="08ded76d-7c7c-4bdc-82a1-c037081bca0e" selected="selected">2026-II</option>
              <option value="other">2026-I</option>
            </select>
        """.trimIndent()
        val term = AkdemicParsers.parseIntranetSelectedTerm(selectorHtml)
        assertEquals("08ded76d-7c7c-4bdc-82a1-c037081bca0e", term?.first)
        assertEquals("2026-II", term?.second)

        val periodoHtml = """
            <div class="course-container">
              <table>
                <tr><th>Curso: </th><td><a>SIS232 - PROYECTO DE TESIS EN INGENIERÍA DE SISTEMAS</a></td></tr>
                <tr><th>Sección: </th><td>GRUPO B</td></tr>
                <tr><th>Créditos: </th><td>4.0</td></tr>
                <tr class="grades"><td>UNIDAD 1</td><td>EV1: Primer avance</td><td>16.00</td></tr>
              </table>
            </div>
        """.trimIndent()
        val courses = AkdemicParsers.parseIntranetCoursesPayload(periodoHtml, 1000L, defaultSemester = "2026-II")
        assertEquals(1, courses.size)
        assertEquals("SIS232", courses[0].code)
        assertEquals("PROYECTO DE TESIS EN INGENIERÍA DE SISTEMAS", courses[0].name)
        assertEquals(4, courses[0].credits)
        assertEquals("2026-II", courses[0].semester)

        val grades = AkdemicParsers.parseExamsPayload(periodoHtml, nowMillis = 1000L)
        assertEquals(1, grades.size)
        assertEquals("SIS232", grades[0].courseId)
        assertEquals("16.00", grades[0].grade)
        assertTrue(grades[0].title.contains("EV1: Primer avance"))
    }

    @Test
    fun parseStudentProfile_supportsTopbarNameAndSkipsEmailAsCareer() {
        val html = """
            <html><body>
              <span class="m-topbar__name">ESTUDIANTE DEMO UNAP</span>
              <a class="m-card-user__email">demo.estudiante@gmail.com</a>
            </body></html>
        """.trimIndent()

        val student = AkdemicParsers.parseStudentProfile(html, "70011223", 1000L)
        assertEquals("ESTUDIANTE DEMO UNAP", student?.fullName)
        assertEquals("UNAP Puno", student?.career)
    }

    @Test
    fun parseCourseDetailEndpoints_extractTeachersUnitsResourcesHomeworkAndReplies() {
        val teachersJson = """
            [{"teacherId":"T1","sectionId":"S1","name":"DOCENTE RESPONSABLE PRUEBA"}]
        """.trimIndent()
        val teachers = AkdemicParsers.parseTeachersBySectionJson(teachersJson)
        assertEquals(listOf("DOCENTE RESPONSABLE PRUEBA"), teachers)

        val unitsJson = """
            [
              {
                "id": "u1",
                "name": "UNIDAD 1",
                "contents": [
                  {
                    "id": "c1",
                    "name": "Semana 1: Intro",
                    "description": "<p>Tema inicial</p>"
                  }
                ]
              }
            ]
        """.trimIndent()
        val units = AkdemicParsers.parseCourseUnitsJson(unitsJson)
        assertEquals(1, units.size)
        assertEquals("UNIDAD 1", units[0].name)
        assertEquals("Semana 1: Intro", units[0].topics[0].name)
        assertEquals("Tema inicial", units[0].topics[0].description)

        val contentResourcesJson = """
            [
              {"id":"res1","name":"Libro guía.pdf","activity":"unit","type":0,"url":null,"show":true},
              {"id":"res2","name":"Enlace bibliográfico","activity":"unit","type":1,"url":"https://example.org/book","show":true},
              {"id":"res3","name":"Tarea 01","activity":"homework","type":0,"show":true},
              {"id":"res4","name":"Foro 01","activity":"forum","type":0,"show":true}
            ]
        """.trimIndent()
        val resources = AkdemicParsers.parseUnitTopicResourcesJson(
            contentResourcesJson,
            "https://aulavirtual2.unap.edu.pe"
        )
        assertEquals(4, resources.size)
        assertEquals(CourseResourceType.DOCUMENT, resources[0].type)
        assertEquals("https://aulavirtual2.unap.edu.pe/web/unit/archivo/res1", resources[0].url)
        assertEquals(CourseResourceType.EXTERNAL_LINK, resources[1].type)
        assertEquals("https://example.org/book", resources[1].url)
        assertEquals(CourseResourceType.HOMEWORK, resources[2].type)
        assertEquals(CourseResourceType.FORUM, resources[3].type)

        val homeworkDetailJson = """
            {
              "id": "hw1",
              "title": "Memoria descriptiva",
              "intent": 2,
              "dateBeginString": "15/09/2026 17:16",
              "dateEndString": "16/09/2026 23:59",
              "description": "<p>Subir informe en PDF.</p>",
              "fileExtensionFilter": ".pdf",
              "isGroup": false,
              "files": [],
              "previousFiles": [{"id": "prev1", "date": "15/09/2026 20:04"}],
              "rubrics": [{"description": "Calificación total", "score": "20,00"}]
            }
        """.trimIndent()
        val hwDetail = AkdemicParsers.parseHomeworkDetailJson(
            homeworkDetailJson,
            "https://aulavirtual2.unap.edu.pe"
        )
        assertEquals(2, hwDetail?.attempts)
        assertEquals(".pdf", hwDetail?.fileFilter)
        assertEquals(1, hwDetail?.studentDeliveries?.size)
        assertTrue(hwDetail?.rubricScore?.contains("20,00") == true)

        val answersJson = """
            [{"forumChildId":"fc1","userName":"ESTUDIANTE UNO","dateView":"18 octubre, 23:58","rated":false,"score":0.0,"mensaje":"<p>Aporte de ejemplo</p>"}]
        """.trimIndent()
        val answers = AkdemicParsers.parseForumAnswersJson(answersJson)
        assertEquals(1, answers.size)
        assertEquals("ESTUDIANTE UNO", answers[0].userName)
        assertEquals("Aporte de ejemplo", answers[0].message)

        val readingsJson = """
            [{"id":"rd1","title":"Algoritmos Genéticos","unit":"U1","description":"Método","url":"2026/doc.pdf","type":"Lectura obligatoria"}]
        """.trimIndent()
        val readings = AkdemicParsers.parseReadingsJson(readingsJson, "https://aulavirtual2.unap.edu.pe")
        assertEquals(1, readings.size)
        assertEquals("https://aulavirtual2.unap.edu.pe/archivos/2026/doc.pdf", readings[0].fileUrl)
    }

    @Test
    fun parseUnitTopicResourcesJson_normalizesExternalLinksAndDocumentExtensions() {
        val json = """
            [
              {"id":"u1","name":"Silabo del Curso.pdf","activity":"unit","type":0,"url":null,"show":true},
              {"id":"u2","name":"Guia Practica","activity":"unit","type":0,"url":"2026/guia.docx","show":true},
              {"id":"u3","name":"Apuntes compartidos","activity":"unit","type":1,"url":"docs.google.com/document/d/abc123","show":true}
            ]
        """.trimIndent()
        val parsed = AkdemicParsers.parseUnitTopicResourcesJson(json, "https://aulavirtual2.unap.edu.pe")
        assertEquals(3, parsed.size)
        assertEquals("https://aulavirtual2.unap.edu.pe/web/unit/archivo/u1", parsed[0].url)
        assertEquals("https://aulavirtual2.unap.edu.pe/archivos/2026/guia.docx", parsed[1].url)
        assertEquals("docx", parsed[1].fileExtension)
        assertEquals(CourseResourceType.EXTERNAL_LINK, parsed[2].type)
        assertEquals("https://docs.google.com/document/d/abc123", parsed[2].url)
        assertEquals("Google Docs", parsed[2].subtitle)
    }

    @Test
    fun parseIntranetHtmlAndJsonModules() {
        val historyHtml = """
            <div class="m-portlet__body">
              <div class="row">
                <div><label>Estudiante</label><label>LOPEZ QUISPE ANA LUCIA</label></div>
                <div><label>Código</label><label>221001</label></div>
                <div><label>DNI</label><label>70001122</label></div>
              </div>
            </div>
            <div class="m-accordion__item">
              <span class="m-accordion__item-title">2026-II</span>
              <div data-pid="pid-2026-2"></div>
            </div>
        """.trimIndent()

        val meta = AkdemicParsers.parseHistoryStudentMetaHtml(historyHtml)
        assertEquals("LOPEZ QUISPE ANA LUCIA", meta.first)
        assertEquals("221001", meta.second)
        assertEquals("70001122", meta.third)

        val periods = AkdemicParsers.parseHistoryPeriodsHtml(historyHtml)
        assertEquals(1, periods.size)
        assertEquals("2026-II" to "pid-2026-2", periods[0])

        val historyJson = """
            [
              {"course":"Algoritmos I","academicYear":1,"credits":"4","finalGrade":"16","try":1,"notDisapproved":true},
              {"course":"Estadistica","academicYear":1,"credits":3,"finalGrade":"10","try":1,"notDisapproved":false}
            ]
        """.trimIndent()
        val historyCourses = AkdemicParsers.parseHistoryPeriodCoursesJson(historyJson)
        assertEquals(2, historyCourses.size)
        assertEquals("16", historyCourses[0].finalGrade)
        assertTrue(historyCourses[0].approved)
        assertFalse(historyCourses[1].approved)

        val curriculumHtml = """
            <div class="m-section__content">
              <h5>Ciclo 1</h5>
              <table>
                <tbody>
                  <tr><td>IS-101</td><td>Programacion I</td><td>4</td><td>2</td><td>0</td><td>2</td><td>0</td><td>Ninguno</td></tr>
                  <tr><td>IS-102</td><td>Matematica Discreta</td><td>4</td><td>2</td><td>0</td><td>2</td><td>0</td><td>Ninguno</td></tr>
                  <tr><td>IS-103</td><td>Arquitectura</td><td>3</td><td>2</td><td>0</td><td>1</td><td>0</td><td>IS-101</td></tr>
                </tbody>
              </table>
            </div>
        """.trimIndent()
        val cycles = AkdemicParsers.parseCurriculumHtml(
            curriculumHtml,
            approvedCodes = setOf("IS101"),
            activeCodes = setOf("IS102")
        )
        assertEquals(1, cycles.size)
        assertEquals("Ciclo 1", cycles[0].cycleName)
        assertEquals("Aprobado", cycles[0].courses[0].status)
        assertEquals("En curso", cycles[0].courses[1].status)
        assertEquals("", cycles[0].courses[2].status)

        val attendanceJson = """
            [{"sectionId":"s1","courseName":"Procesamiento","classCount":32,"maxAbsences":9,"dictated":12,"assisted":11,"absences":1}]
        """.trimIndent()
        val attendance = AkdemicParsers.parseAttendanceJson(attendanceJson)
        assertEquals(1, attendance.size)
        assertEquals(11, attendance[0].assisted)
        assertEquals(9, attendance[0].maxAbsences)

        val adminRecords = AkdemicParsers.parseAdminRecordsJson(
            debtsJson = """[{"concept":"Carnet Universitario","issueDate":"01/04/2026","totalamount":"S/ 16.00"}]""",
            paymentsJson = """[{"description":"Matricula 2026-II","paymentDate":"10/08/2026","amount":"S/ 25.00"}]""",
            proceduresJson = """[{"title":"Constancia de estudios","createdAt":"12/08/2026","status":"Aprobado"}]""",
            requestsJson = """[{"name":"Rectificacion","date":"15/08/2026","state":"En proceso"}]"""
        )
        assertEquals(4, adminRecords.size)
        assertEquals("Deuda pendiente", adminRecords[0].category)
        assertEquals("Trámite", adminRecords[1].category)
        assertEquals("Solicitud", adminRecords[2].category)
        assertEquals("Pago realizado", adminRecords[3].category)
    }

    @Test
    fun parseForumJson_archivesOldSeptemberForumsRelativeToCurrentTime() {
        val nowNov15 = sky.universidad.UnApp.core.common.DateFormatters.parseToEpochMillis("15/11/2026 14:00")!!
        val json = """
            [
              {"id":"old_sept","name":"Foro Bienvenida","unidadName":"U1","dateStartView":"05/09/2026 08:00","dateEndView":null,"isEnded":false},
              {"id":"closed_sept","name":"Foro Unidad I","unidadName":"U1","dateEndView":"20/09/2026 23:59","isEnded":false},
              {"id":"active_nov","name":"Foro Unidad III","unidadName":"U3","dateEndView":"20/11/2026 23:59","isEnded":false}
            ]
        """.trimIndent()
        val parsed = AkdemicParsers.parseForumJson(json, "c1", "Curso Test", nowNov15)
        assertEquals(3, parsed.size)
        assertTrue(parsed[0].isEnded)
        assertTrue(parsed[1].isEnded)
        assertFalse(parsed[2].isEnded)
    }
}
