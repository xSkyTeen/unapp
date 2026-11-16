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
import sky.universidad.UnApp.core.security.SecretCipher
import sky.universidad.UnApp.core.session.CookiePersistenceBackend
import sky.universidad.UnApp.core.session.PersistentCookieJar
import sky.universidad.UnApp.data.remote.auth.AkdemicAuthClient

class AkdemicAuthClientTest {

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

    private fun newCookieJar(): PersistentCookieJar {
        val cipher = object : SecretCipher {
            override fun encrypt(plainText: String): String = "ENC:$plainText"
            override fun decrypt(encodedCipherText: String): String? =
                encodedCipherText.removePrefix("ENC:")
        }
        val backend = object : CookiePersistenceBackend {
            var stored: String? = null
            override fun readEncrypted(): String? = stored
            override fun writeEncrypted(value: String?) {
                stored = value
            }
        }
        return PersistentCookieJar(cipher, backend)
    }

    @Test
    fun loginWithCredentials_completesOidcFormPostAndIntranetSso() = runTest {
        var sawLoginPost = false
        var sawAulaSigninOidc = false
        var sawIntranetSigninOidc = false

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/aula/" -> MockResponse()
                        .setResponseCode(302)
                        .addHeader("Location", "/campus/login")

                    path == "/campus/login" && request.method == "GET" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            <html><body>
                            <form id="main_form_login" method="post" action="/campus/login?returnurl=%2Fcallback">
                              <input name="username" type="text" id="UserName" />
                              <input name="password" type="password" id="Password" />
                              <input type="hidden" name="System" value="AULA VIRTUAL" />
                              <input type="hidden" name="SystemLogin" value="12" />
                              <input type="hidden" name="__RequestVerificationToken" value="csrf_token_123" />
                            </form>
                            </body></html>
                            """.trimIndent()
                        )

                    path.startsWith("/campus/login") && request.method == "POST" -> {
                        val body = request.body.readUtf8()
                        assertTrue(body.contains("74128950"))
                        assertTrue(body.contains("secret_pass"))
                        assertTrue(body.contains("csrf_token_123"))
                        sawLoginPost = true
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("Set-Cookie", "idsrv.session=sso_ok; Path=/")
                            .setBody("\"/campus/callback\"")
                    }

                    path == "/campus/callback" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            <html><body>
                            <form method="post" action="/aula/signin-oidc">
                              <input type="hidden" name="code" value="auth_code_abc" />
                              <input type="hidden" name="state" value="state_xyz" />
                            </form>
                            </body></html>
                            """.trimIndent()
                        )

                    path == "/aula/signin-oidc" && request.method == "POST" -> {
                        sawAulaSigninOidc = true
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("Set-Cookie", "aula.auth=valid_aula; Path=/")
                            .setBody("OK")
                    }

                    path == "/aula/web/user/info/system/courseinrole" -> {
                        val cookies = request.getHeader("Cookie").orEmpty()
                        if (cookies.contains("aula.auth=valid_aula")) {
                            MockResponse()
                                .setResponseCode(200)
                                .setBody("""[{"sectionId":"101","name":"Arquitectura"}]""")
                        } else {
                            MockResponse().setResponseCode(302).addHeader("Location", "/campus/login")
                        }
                    }

                    path == "/intranet/" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            <html><body>
                            <form method="post" action="/intranet/signin-oidc">
                              <input type="hidden" name="code" value="intranet_code" />
                              <input type="hidden" name="state" value="intranet_state" />
                            </form>
                            </body></html>
                            """.trimIndent()
                        )

                    path == "/intranet/signin-oidc" && request.method == "POST" -> {
                        sawIntranetSigninOidc = true
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("Set-Cookie", "intranet.auth=valid_intra; Path=/")
                            .setBody("OK")
                    }

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val jar = newCookieJar()
        val client = OkHttpClient.Builder().cookieJar(jar).build()
        val auth = AkdemicAuthClient(
            httpClient = client,
            aulaVirtualBaseUrl = server.url("/aula").toString().trimEnd('/'),
            intranetBaseUrl = server.url("/intranet").toString().trimEnd('/')
        )

        val result = auth.loginWithCredentials("74128950", "secret_pass")
        assertTrue(result is AppResult.Success)
        val outcome = (result as AppResult.Success).data
        assertEquals("74128950", outcome.userIdentifier)
        assertTrue(outcome.aulaVirtualReady)
        assertTrue(outcome.intranetReady)
        assertTrue(sawLoginPost)
        assertTrue(sawAulaSigninOidc)
        assertTrue(sawIntranetSigninOidc)
    }

    @Test
    fun loginWithCredentials_handles400BlockedAccountMessage() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/aula/" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            <form id="main_form_login" method="post" action="/campus/login">
                              <input name="username" type="text" />
                              <input name="password" type="password" />
                              <input type="hidden" name="__RequestVerificationToken" value="tok" />
                            </form>
                            """.trimIndent()
                        )
                    path == "/campus/login" && request.method == "POST" -> MockResponse()
                        .setResponseCode(400)
                        .setBody("""{"message":"Credenciales inválidas","type":1,"reason":"Revisa tu clave"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = OkHttpClient.Builder().cookieJar(newCookieJar()).build()
        val auth = AkdemicAuthClient(
            httpClient = client,
            aulaVirtualBaseUrl = server.url("/aula").toString().trimEnd('/'),
            intranetBaseUrl = server.url("/intranet").toString().trimEnd('/')
        )

        val result = auth.loginWithCredentials("220145", "wrong")
        assertTrue(result is AppResult.Error)
        val err = result as AppResult.Error
        assertTrue(err.message.contains("Credenciales inválidas"))
    }

    @Test
    fun loginWithCredentials_handlesSingleQuotedOidcFormPost() = runTest {
        var sawSingleQuoteCode = false
        var sawAjaxHeaderOnCourses = false

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path == "/aula/" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            <form id="main_form_login" method="post" action="/campus/login?returnurl=%2Fcallback">
                              <input name="username" type="text" />
                              <input name="password" type="password" />
                              <input type="hidden" name="__RequestVerificationToken" value="tok_single" />
                            </form>
                            """.trimIndent()
                        )

                    path.startsWith("/campus/login") && request.method == "POST" -> MockResponse()
                        .setResponseCode(200)
                        .setBody("\"/campus/callback\"")

                    path == "/campus/callback" -> MockResponse()
                        .setResponseCode(200)
                        .setBody(
                            """
                            <html><head><meta http-equiv='X-UA-Compatible' content='IE=edge' /></head><body>
                            <form method='post' action='/aula/signin-oidc'>
                              <input type='hidden' name='code' value='single_quote_code' />
                              <input type='hidden' name='state' value='single_quote_state' />
                            </form>
                            </body></html>
                            """.trimIndent()
                        )

                    path == "/aula/signin-oidc" && request.method == "POST" -> {
                        val body = request.body.readUtf8()
                        sawSingleQuoteCode = body.contains("single_quote_code")
                        MockResponse()
                            .setResponseCode(200)
                            .addHeader("Set-Cookie", ".AspNetCore.Cookies=ok_cookie; Path=/")
                            .setBody("OK")
                    }

                    path == "/aula/web/user/info/system/courseinrole" -> {
                        sawAjaxHeaderOnCourses = "XMLHttpRequest".equals(
                            request.getHeader("X-Requested-With"),
                            ignoreCase = true
                        )
                        if (request.getHeader("Cookie").orEmpty().contains("ok_cookie")) {
                            MockResponse().setResponseCode(200).setBody("[]")
                        } else {
                            MockResponse().setResponseCode(401)
                        }
                    }

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val client = OkHttpClient.Builder().cookieJar(newCookieJar()).build()
        val auth = AkdemicAuthClient(
            httpClient = client,
            aulaVirtualBaseUrl = server.url("/aula").toString().trimEnd('/'),
            intranetBaseUrl = server.url("/intranet").toString().trimEnd('/')
        )

        val result = auth.loginWithCredentials("70011223", "demo_pass")
        assertTrue(result is AppResult.Success)
        val outcome = (result as AppResult.Success).data
        assertTrue(outcome.aulaVirtualReady)
        assertTrue(sawSingleQuoteCode)
        assertTrue(sawAjaxHeaderOnCourses)
    }
}
