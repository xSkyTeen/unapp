package sky.universidad.UnApp.data.remote.auth

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import sky.universidad.UnApp.core.common.AppResult
import sky.universidad.UnApp.core.network.UnapEndpoints
import java.io.IOException

data class AuthOutcome(
    val userIdentifier: String,
    val aulaVirtualReady: Boolean,
    val intranetReady: Boolean
)

/**
 * Handles the real Akdemic IdentityServer4 OIDC (response_mode=form_post) login flow
 * for UNAP Puno (`aulavirtual2.unap.edu.pe`, `intranet.unap.edu.pe`, `campus.unap.edu.pe`).
 *
 * Passwords exist strictly in memory for the duration of the HTTP POST and are never persisted.
 */
class AkdemicAuthClient(
    private val httpClient: OkHttpClient,
    private val aulaVirtualBaseUrl: String = UnapEndpoints.BASE_AULA_VIRTUAL,
    private val intranetBaseUrl: String = UnapEndpoints.BASE_INTRANET
) {

    suspend fun loginWithCredentials(
        username: String,
        password: String
    ): AppResult<AuthOutcome> = withContext(Dispatchers.IO) {
        val trimmedUser = username.trim()
        if (trimmedUser.isEmpty() || password.isEmpty()) {
            return@withContext AppResult.Error("Ingresa tu DNI o código de estudiante y tu contraseña.")
        }

        try {
            // 1. Enter Aula Virtual -> redirects to Campus SSO login form (or OIDC form_post if cookie active)
            val entryResponse = executeGet("${aulaVirtualBaseUrl.trimEnd('/')}/")
            val entryUrl = entryResponse.finalUrl
            val entryBody = entryResponse.body

            if (containsCaptcha(entryBody)) {
                return@withContext AppResult.Error(
                    "El portal institucional solicitó verificación CAPTCHA. Usa el acceso vía portal web."
                )
            }

            // Maybe already authenticated via campus cookie and returned OIDC form_post directly
            if (isOidcFormPostHtml(entryBody)) {
                submitOidcFormPost(entryBody, entryUrl)
            } else if (isLoginFormHtml(entryBody)) {
                val postResult = submitCampusLoginForm(
                    pageHtml = entryBody,
                    pageUrl = entryUrl,
                    username = trimmedUser,
                    password = password
                )
                if (postResult is AppResult.Error) return@withContext postResult
                val callbackUrl = (postResult as AppResult.Success).data

                val callbackResponse = executeGet(callbackUrl)
                if (isOidcFormPostHtml(callbackResponse.body)) {
                    submitOidcFormPost(callbackResponse.body, callbackResponse.finalUrl)
                }
            }

            // 2. Verify Aula Virtual session actually yields JSON courses
            val aulaOk = checkAulaVirtualSession()
            if (!aulaOk) {
                return@withContext AppResult.Error(
                    "No se pudo completar el acceso al Aula Virtual. Verifica tus credenciales."
                )
            }

            // 3. Complete silent SSO handshake for Intranet using the active campus session cookie
            val intranetOk = runCatching { completeSilentSso(intranetBaseUrl) }.getOrDefault(false)

            AppResult.Success(
                AuthOutcome(
                    userIdentifier = trimmedUser,
                    aulaVirtualReady = true,
                    intranetReady = intranetOk
                )
            )
        } catch (io: IOException) {
            AppResult.Error(
                message = "No fue posible conectar con los servidores de la UNAP. Revisa tu conexión.",
                cause = io,
                isNetworkError = true
            )
        } catch (t: Throwable) {
            AppResult.Error(
                message = t.message ?: "Error inesperado durante la autenticación.",
                cause = t
            )
        }
    }

    suspend fun verifyAulaVirtualSession(): Boolean = withContext(Dispatchers.IO) {
        runCatching { checkAulaVirtualSession() }.getOrDefault(false)
    }

    suspend fun completeSilentSso(targetBaseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val resp = executeGet("${targetBaseUrl.trimEnd('/')}/")
        if (isOidcFormPostHtml(resp.body)) {
            val postResp = submitOidcFormPost(resp.body, resp.finalUrl)
            return@withContext !isLoginFormHtml(postResp.body)
        }
        !isLoginFormHtml(resp.body)
    }

    private fun checkAulaVirtualSession(): Boolean {
        val url = "${aulaVirtualBaseUrl.trimEnd('/')}${UnapEndpoints.PATH_COURSES}"
        val request = Request.Builder()
            .url(url)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/plain, */*")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body?.string()?.trim().orEmpty()
            return body.startsWith("[") || body.startsWith("{")
        }
    }

    private fun submitCampusLoginForm(
        pageHtml: String,
        pageUrl: String,
        username: String,
        password: String
    ): AppResult<String> {
        val doc = Jsoup.parse(pageHtml, pageUrl)
        val form = doc.selectFirst("form#main_form_login") ?: doc.selectFirst("form")
            ?: return AppResult.Error("No se encontró el formulario de inicio de sesión institucional.")

        val actionAttr = form.attr("action").ifBlank { "/login" }
        val resolvedAction = doc.baseUri().toHttpUrlOrNull()?.resolve(actionAttr)?.toString()
            ?: actionAttr

        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        val hiddenInputs = form.select("input[type=hidden]")
        var userFieldName = "username"
        var passFieldName = "password"

        form.select("input").forEach { input ->
            val type = input.attr("type").lowercase()
            val name = input.attr("name")
            if (name.isBlank()) return@forEach
            when {
                type == "password" -> passFieldName = name
                type == "text" && (name.contains("user", ignoreCase = true) || input.id().contains("User", ignoreCase = true)) -> {
                    userFieldName = name
                }
            }
        }

        hiddenInputs.forEach { hidden ->
            val name = hidden.attr("name")
            if (name.isNotBlank()) {
                multipart.addFormDataPart(name, hidden.attr("value"))
            }
        }
        multipart.addFormDataPart(userFieldName, username)
        multipart.addFormDataPart(passFieldName, password)

        val request = Request.Builder()
            .url(resolvedAction)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", pageUrl)
            .post(multipart.build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return when {
                response.isSuccessful -> {
                    // Akdemic /login returns the callback URL (either raw string or JSON string)
                    val cleanedRedirect = parseRedirectUrl(body, response.request.url.toString())
                    if (cleanedRedirect.isNullOrBlank()) {
                        if (isOidcFormPostHtml(body)) {
                            submitOidcFormPost(body, response.request.url.toString())
                            AppResult.Success(aulaVirtualBaseUrl)
                        } else {
                            AppResult.Error("Respuesta inesperada del servidor de autenticación.")
                        }
                    } else {
                        AppResult.Success(cleanedRedirect)
                    }
                }
                response.code == 400 -> {
                    val serverMsg = extractServerErrorMessage(body)
                        ?: "Usuario o contraseña incorrectos."
                    AppResult.Error(serverMsg)
                }
                response.code >= 500 -> {
                    AppResult.Error("El servidor de la UNAP presentó un error interno (${response.code}). Intenta más tarde.")
                }
                else -> {
                    AppResult.Error("No fue posible validar las credenciales (código ${response.code}).")
                }
            }
        }
    }

    private fun submitOidcFormPost(html: String, baseUrl: String): HttpGetResult {
        val doc = Jsoup.parse(html, baseUrl)
        val form = doc.selectFirst("form")
            ?: return HttpGetResult(baseUrl, html)
        val action = form.attr("action")
        val targetUrl = doc.baseUri().toHttpUrlOrNull()?.resolve(action)?.toString() ?: action

        val formBuilder = FormBody.Builder()
        form.select("input[name]").forEach { input ->
            formBuilder.add(input.attr("name"), input.attr("value"))
        }
        val request = Request.Builder()
            .url(targetUrl)
            .post(formBuilder.build())
            .build()

        httpClient.newCall(request).execute().use { response ->
            return HttpGetResult(
                finalUrl = response.request.url.toString(),
                body = response.body?.string().orEmpty()
            )
        }
    }

    private fun executeGet(url: String): HttpGetResult {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            return HttpGetResult(
                finalUrl = response.request.url.toString(),
                body = response.body?.string().orEmpty()
            )
        }
    }

    private fun parseRedirectUrl(rawBody: String, baseUrl: String): String? {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return null
        val candidate: String = when {
            trimmed.startsWith("\"") && trimmed.endsWith("\"") -> {
                runCatching { JsonParser.parseString(trimmed).asString }
                    .getOrNull() ?: trimmed.trim('"')
            }
            trimmed.startsWith("{") -> {
                runCatching {
                    val obj = JsonParser.parseString(trimmed).asJsonObject
                    obj.get("redirectUrl")?.asString ?: obj.get("url")?.asString
                }.getOrNull()
            }
            !trimmed.startsWith("<") -> trimmed
            else -> null
        } ?: return null

        return baseUrl.toHttpUrlOrNull()?.resolve(candidate)?.toString() ?: candidate
    }

    private fun extractServerErrorMessage(rawBody: String): String? {
        return runCatching {
            val obj = JsonParser.parseString(rawBody).asJsonObject
            val msg = obj.get("message")?.asString?.takeIf { it.isNotBlank() }
            val reason = obj.get("reason")?.asString?.takeIf { it.isNotBlank() }
            when {
                msg != null && reason != null -> "$msg: $reason"
                msg != null -> msg
                reason != null -> reason
                else -> null
            }
        }.getOrNull()
    }

    private fun isOidcFormPostHtml(html: String): Boolean {
        if (!html.contains("signin-oidc", ignoreCase = true)) return false
        return html.contains("name=\"code\"", ignoreCase = true) ||
            html.contains("name='code'", ignoreCase = true) ||
            html.contains("name=code", ignoreCase = true) ||
            html.contains("name=\"id_token\"", ignoreCase = true) ||
            html.contains("name='id_token'", ignoreCase = true) ||
            html.contains("name=id_token", ignoreCase = true)
    }

    private fun isLoginFormHtml(html: String): Boolean {
        return html.contains("main_form_login", ignoreCase = true) ||
            (html.contains("name=\"username\"", ignoreCase = true) &&
                html.contains("name=\"password\"", ignoreCase = true))
    }

    private fun containsCaptcha(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("g-recaptcha") ||
            lower.contains("h-captcha") ||
            lower.contains("cf-turnstile")
    }

    private data class HttpGetResult(
        val finalUrl: String,
        val body: String
    )
}
