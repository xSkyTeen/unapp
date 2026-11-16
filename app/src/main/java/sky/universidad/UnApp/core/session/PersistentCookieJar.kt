package sky.universidad.UnApp.core.session

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import sky.universidad.UnApp.core.security.SecretCipher
import java.util.concurrent.ConcurrentHashMap

data class StoredCookieDto(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
    val persistent: Boolean
)

interface CookiePersistenceBackend {
    fun readEncrypted(): String?
    fun writeEncrypted(value: String?)
}

class SharedPrefsCookieBackend(context: Context) : CookiePersistenceBackend {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("unapp_encrypted_cookies", Context.MODE_PRIVATE)

    override fun readEncrypted(): String? = prefs.getString(KEY_PAYLOAD, null)

    override fun writeEncrypted(value: String?) {
        prefs.edit().apply {
            if (value == null) remove(KEY_PAYLOAD) else putString(KEY_PAYLOAD, value)
        }.apply()
    }

    private companion object {
        const val KEY_PAYLOAD = "session_cookies_v1"
    }
}

/**
 * Thread-safe, Keystore-encrypted CookieJar that persists institutional session cookies
 * and synchronizes them with Android WebView's CookieManager when needed.
 */
class PersistentCookieJar(
    private val cipher: SecretCipher,
    private val backend: CookiePersistenceBackend,
    private val gson: Gson = Gson()
) : CookieJar {

    private val cookieStore = ConcurrentHashMap<String, Cookie>()
    private val lock = Any()

    init {
        loadFromDisk()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            for (cookie in cookies) {
                val key = cookieKey(cookie)
                if (cookie.expiresAt <= now) {
                    cookieStore.remove(key)
                } else {
                    cookieStore[key] = cookie
                }
            }
            persistToDisk()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val expiredKeys = mutableListOf<String>()
            val matching = mutableListOf<Cookie>()

            for ((key, cookie) in cookieStore) {
                if (cookie.expiresAt <= now) {
                    expiredKeys.add(key)
                } else if (cookie.matches(url)) {
                    matching.add(cookie)
                }
            }

            if (expiredKeys.isNotEmpty()) {
                expiredKeys.forEach { cookieStore.remove(it) }
                persistToDisk()
            }
            return matching
        }
    }

    fun hasCookiesForHost(host: String): Boolean {
        val now = System.currentTimeMillis()
        return cookieStore.values.any { cookie ->
            cookie.expiresAt > now && (cookie.domain == host || host.endsWith(".${cookie.domain}"))
        }
    }

    /**
     * Copies the WebView session (including HttpOnly auth cookies) into the encrypted OkHttp jar.
     * Cookies previously stored for the same hosts are replaced so stale sessions never linger.
     *
     * @return number of cookies imported.
     */
    fun importFromWebView(urls: List<String>): Int {
        val webCookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return 0
        val headers = urls.associateWith { rawUrl -> webCookieManager.getCookie(rawUrl).orEmpty() }
        return importCookieHeaders(headers)
    }

    /**
     * Platform-independent core of [importFromWebView]: maps `url -> "a=1; b=2"` cookie headers.
     */
    fun importCookieHeaders(headersByUrl: Map<String, String>): Int {
        var imported = 0
        synchronized(lock) {
            var changed = false
            for ((rawUrl, cookieHeader) in headersByUrl) {
                val httpUrl = rawUrl.toHttpUrlOrNull() ?: continue
                if (cookieHeader.isBlank()) continue

                val host = httpUrl.host
                val staleKeys = cookieStore.filterValues { it.domain == host }.keys
                if (staleKeys.isNotEmpty()) {
                    staleKeys.forEach { cookieStore.remove(it) }
                    changed = true
                }

                for (pair in cookieHeader.split(";")) {
                    val trimmed = pair.trim()
                    val eqIdx = trimmed.indexOf('=')
                    if (eqIdx <= 0) continue
                    val name = trimmed.substring(0, eqIdx).trim()
                    val value = trimmed.substring(eqIdx + 1).trim()
                    val parsed = runCatching {
                        Cookie.Builder()
                            .name(name)
                            .value(value)
                            .domain(host)
                            .path("/")
                            .secure()
                            .httpOnly()
                            .build()
                    }.getOrNull() ?: continue
                    cookieStore[cookieKey(parsed)] = parsed
                    imported++
                    changed = true
                }
            }
            if (changed) {
                persistToDisk()
            }
        }
        return imported
    }

    fun syncToWebView(urls: List<String>) {
        val webCookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        webCookieManager.setAcceptCookie(true)
        synchronized(lock) {
            for (rawUrl in urls) {
                val httpUrl = rawUrl.toHttpUrlOrNull() ?: continue
                val cookies = loadForRequest(httpUrl)
                for (cookie in cookies) {
                    webCookieManager.setCookie(rawUrl, cookie.toString())
                }
            }
        }
        webCookieManager.flush()
    }

    fun clearAll() {
        synchronized(lock) {
            cookieStore.clear()
            backend.writeEncrypted(null)
        }
        runCatching {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
        }
    }

    private fun cookieKey(cookie: Cookie): String =
        "${cookie.domain}|${cookie.path}|${cookie.name}"

    private fun loadFromDisk() {
        val encrypted = backend.readEncrypted() ?: return
        val decryptedJson = cipher.decrypt(encrypted) ?: return
        val type = object : TypeToken<List<StoredCookieDto>>() {}.type
        val items: List<StoredCookieDto> = runCatching {
            gson.fromJson<List<StoredCookieDto>>(decryptedJson, type)
        }.getOrNull() ?: return

        val now = System.currentTimeMillis()
        for (dto in items) {
            if (dto.expiresAt <= now) continue
            val builder = Cookie.Builder()
                .name(dto.name)
                .value(dto.value)
                .path(dto.path)
                .expiresAt(dto.expiresAt)
            if (dto.hostOnly) builder.hostOnlyDomain(dto.domain) else builder.domain(dto.domain)
            if (dto.secure) builder.secure()
            if (dto.httpOnly) builder.httpOnly()
            val cookie = runCatching { builder.build() }.getOrNull() ?: continue
            cookieStore[cookieKey(cookie)] = cookie
        }
    }

    private fun persistToDisk() {
        val dtos = cookieStore.values.map { c ->
            StoredCookieDto(
                name = c.name,
                value = c.value,
                expiresAt = c.expiresAt,
                domain = c.domain,
                path = c.path,
                secure = c.secure,
                httpOnly = c.httpOnly,
                hostOnly = c.hostOnly,
                persistent = c.persistent
            )
        }
        val json = gson.toJson(dtos)
        val encrypted = cipher.encrypt(json)
        backend.writeEncrypted(encrypted)
    }
}
