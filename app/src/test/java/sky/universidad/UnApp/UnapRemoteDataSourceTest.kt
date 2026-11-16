package sky.universidad.UnApp

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import sky.universidad.UnApp.core.common.AppResult
import sky.universidad.UnApp.data.remote.api.UnapRemoteDataSource
import sky.universidad.UnApp.data.remote.auth.AkdemicAuthClient

class UnapRemoteDataSourceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchFullAcademicSnapshot_integratesCoursesTasksForumsAnnouncementsAndIntranet() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/aula/web/user/info/system/courseinrole" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {"sectionId":"501","code":"IS-301","name":"Bases de Datos","teacherName":"Ing. Rosa Mamani","credits":4}
                            ]
                            """.trimIndent()
                        )

                    path == "/aula/web/homework/list?s=501" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {"id":11,"title":"Normalización 3FN","unidad":"Unidad I","dateEnd":"30/11/2026 23:59","description":"<p>Enviar script SQL</p>"}
                            ]
                            """.trimIndent()
                        )

                    path == "/aula/web/forum/list?s=501" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {"id":77,"name":"Índices B-Tree","unidadName":"Unidad I","dateEndView":"28/11/2026 20:00"}
                            ]
                            """.trimIndent()
                        )

                    path == "/aula/web/announcement/list?s=501" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {"id":5,"title":"Cambio de horario de laboratorio","description":"Se traslada al Lab 03.","date":"14/11/2026 10:00"}
                            ]
                            """.trimIndent()
                        )

                    path == "/intranet/" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            <html><body>
                            <div class="m-card-user__name">María Elena Quispe</div>
                            <div class="m-card-user__email">Ingeniería de Sistemas</div>
                            </body></html>
                            """.trimIndent()
                        )

                    path == "/intranet/horario" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            <table>
                              <tr><td>Martes</td><td>08:00 - 10:00</td><td>Bases de Datos</td><td>Lab 03</td></tr>
                            </table>
                            """.trimIndent()
                        )

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = OkHttpClient.Builder().build()
        val aulaBase = server.url("/aula").toString().trimEnd('/')
        val intraBase = server.url("/intranet").toString().trimEnd('/')
        val auth = AkdemicAuthClient(client, aulaBase, intraBase)
        val remote = UnapRemoteDataSource(client, auth, aulaBase, intraBase)

        val result = remote.fetchFullAcademicSnapshot("220145")
        assertTrue(result is AppResult.Success)
        val payload = (result as AppResult.Success).data
        assertEquals(1, payload.courses.size)
        assertEquals(1, payload.tasks.size)
        assertEquals("Normalización 3FN", payload.tasks[0].title)
        assertEquals(1, payload.forums.size)
        assertEquals(1, payload.announcements.size)
        assertEquals("María Elena Quispe", payload.student?.fullName)
        assertEquals(1, payload.schedules.size)
        assertEquals(2, payload.schedules[0].dayOfWeek)
    }

    @Test
    fun fetchFullAcademicSnapshot_detectsExpiredSessionHtmlRedirect() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(200)
                    .setBody("<form id=\"main_form_login\" action=\"/login\"></form>")
            }
        }

        val client = OkHttpClient.Builder().build()
        val base = server.url("/aula").toString().trimEnd('/')
        val auth = AkdemicAuthClient(client, base, base)
        val remote = UnapRemoteDataSource(client, auth, base, base)

        val result = remote.fetchFullAcademicSnapshot("220145")
        assertTrue(result is AppResult.Error)
        assertTrue((result as AppResult.Error).isSessionExpired)
    }

    @Test
    fun fetchFullAcademicSnapshot_mergesSevenCoursesAcrossPortalsWithUniqueColors() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/aula/web/user/info/system/courseinrole" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {"sectionId":"101","code":"SEC-101","name":"Arquitectura de Software","teacherName":"Ing. Quispe","credits":4},
                              {"sectionId":"102","code":"IS-402","name":"Ingeniería de Requisitos","teacherName":"Mg. Ticona","credits":3},
                              {"sectionId":"103","code":"IS-403","name":"Sistemas Distribuidos","teacherName":"Dr. Condori","credits":4},
                              {"sectionId":"104","code":"IS-404","name":"Seguridad Informática","teacherName":"Ing. Huanca","credits":3},
                              {"sectionId":"105","code":"IS-405","name":"Inteligencia Artificial","teacherName":"Mg. Flores","credits":3}
                            ]
                            """.trimIndent()
                        )

                    path == "/intranet/api/student/courses" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {"code":"IS-401","name":"Arquitectura de Software","credits":4,"teacherName":"Ing. Quispe"},
                              {"code":"IS-406","name":"Computación en la Nube","credits":3,"teacherName":"Ing. Arpita"},
                              {"code":"IS-407","name":"Proyecto de Tesis I","credits":4,"teacherName":"Dr. Velásquez"}
                            ]
                            """.trimIndent()
                        )

                    path.startsWith("/aula/web/homework/list") ||
                        path.startsWith("/aula/web/forum/list") ||
                        path.startsWith("/aula/web/announcement/list") ||
                        path.startsWith("/aula/web/exam/list") -> MockResponse()
                        .setResponseCode(200)
                        .setBody("[]")

                    path == "/intranet/" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("<html><body></body></html>")

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = OkHttpClient.Builder().build()
        val aulaBase = server.url("/aula").toString().trimEnd('/')
        val intraBase = server.url("/intranet").toString().trimEnd('/')
        val auth = AkdemicAuthClient(client, aulaBase, intraBase)
        val remote = UnapRemoteDataSource(client, auth, aulaBase, intraBase)

        val result = remote.fetchFullAcademicSnapshot("220145")
        assertTrue(result is AppResult.Success)
        val courses = (result as AppResult.Success).data.courses
        assertEquals(7, courses.size)
        val arq = courses.first { it.name == "Arquitectura de Software" }
        assertEquals("IS-401", arq.code)
        assertEquals("ambos", arq.sourcePortal)
        assertEquals(7, courses.map { it.colorHex }.toSet().size)
    }

    @Test
    fun fetchFullAcademicSnapshot_parsesRealUnapNotasScheduleAndCourseCodes() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/aula/web/user/info/system/courseinrole" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {
                                "id":"u1",
                                "codeCourse":"SIS323",
                                "sectionId":"08def7b6-sec1",
                                "codeSection":"GRUPO B",
                                "name":"ANÁLISIS DE DATOS",
                                "type":"OBLIGATORIO",
                                "deparment":"INGENIERIA DE SISTEMAS",
                                "faculty":"INGENIERIA MECANICA ELECTRICA, ELECTRONICA Y SISTEMAS"
                              }
                            ]
                            """.trimIndent()
                        )

                    path == "/aula/web/homework/list?s=08def7b6-sec1" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {"id":"h1","title":"Plantillas","unidad":"UNIDAD 1","dateEnd":"15/10/2026 17:00","description":"Ok","homeworkstds":1}
                            ]
                            """.trimIndent()
                        )

                    path.startsWith("/aula/web/forum/list") -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {"forumId":"f1","name":"Foro 01","unidadName":"UNIDAD 1","dateEndView":"18/10/2026 23:59"}
                            ]
                            """.trimIndent()
                        )

                    path.startsWith("/aula/web/announcement/list") ||
                        path.startsWith("/aula/web/exam/list") -> MockResponse()
                        .setResponseCode(200)
                        .setBody("[]")

                    path == "/intranet/" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            <html><body>
                              <span class="m-topbar__name">ESTUDIANTE DEMO UNAP</span>
                              <a class="m-card-user__email">demo@gmail.com</a>
                            </body></html>
                            """.trimIndent()
                        )

                    path.startsWith("/intranet/alumno/horario-semana/get") -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            [
                              {
                                "id":"sch1",
                                "title":"SIS323-ANÁLISIS DE DATOS (GRUPO B)",
                                "description":"Sin Asignar",
                                "allDay":false,
                                "start":"2026-11-16T17:00:00",
                                "end":"2026-11-16T19:00:00"
                              }
                            ]
                            """.trimIndent()
                        )

                    path == "/intranet/alumno/notas" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            <select id="select-term">
                              <option selected="selected" value="term-2026-2">2026-II</option>
                            </select>
                            """.trimIndent()
                        )

                    path == "/intranet/alumno/notas/periodo/term-2026-2/get" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            <div class="course-container">
                              <table>
                                <tr><th>Curso: </th><td><a>SIS323 - ANÁLISIS DE DATOS</a></td></tr>
                                <tr><th>Sección: </th><td>GRUPO B</td></tr>
                                <tr><th>Créditos: </th><td>4.0</td></tr>
                                <tr class="grades"><td>UNIDAD 1</td><td>EV1</td><td>17.00</td></tr>
                              </table>
                            </div>
                            """.trimIndent()
                        )

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = OkHttpClient.Builder().build()
        val aulaBase = server.url("/aula").toString().trimEnd('/')
        val intraBase = server.url("/intranet").toString().trimEnd('/')
        val auth = AkdemicAuthClient(client, aulaBase, intraBase)
        val remote = UnapRemoteDataSource(client, auth, aulaBase, intraBase)

        val result = remote.fetchFullAcademicSnapshot("70011223")
        assertTrue(result is AppResult.Success)
        val payload = (result as AppResult.Success).data
        assertEquals("ESTUDIANTE DEMO UNAP", payload.student?.fullName)
        assertEquals("INGENIERIA DE SISTEMAS", payload.student?.career)
        assertEquals("2026-II", payload.student?.semester)
        assertEquals(1, payload.courses.size)
        assertEquals("SIS323", payload.courses[0].code)
        assertEquals(4, payload.courses[0].credits)
        assertEquals("ambos", payload.courses[0].sourcePortal)
        assertEquals(1, payload.tasks.size)
        assertTrue(payload.tasks[0].isCompletedLocal)
        assertEquals(1, payload.forums.size)
        assertEquals("forum_08def7b6-sec1_f1", payload.forums[0].remoteId)
        assertEquals(1, payload.schedules.size)
        assertEquals("08def7b6-sec1", payload.schedules[0].courseId)
        assertEquals(1, payload.schedules[0].dayOfWeek)
        assertEquals("17:00", payload.schedules[0].startTime)
        assertEquals(1, payload.exams.size)
        assertEquals("08def7b6-sec1", payload.exams[0].courseId)
        assertEquals("17.00", payload.exams[0].grade)
    }
}
