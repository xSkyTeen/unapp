package sky.universidad.UnApp

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sky.universidad.UnApp.core.security.SecretCipher
import sky.universidad.UnApp.core.session.CookiePersistenceBackend
import sky.universidad.UnApp.core.session.PersistentCookieJar

class PersistentCookieJarTest {

    @Test
    fun cookiesAreEncryptedAtRestAndClearedOnLogout() {
        var persistedCipherText: String? = null
        val cipher = object : SecretCipher {
            override fun encrypt(plainText: String): String =
                plainText.reversed()

            override fun decrypt(encodedCipherText: String): String =
                encodedCipherText.reversed()
        }
        val backend = object : CookiePersistenceBackend {
            override fun readEncrypted(): String? = persistedCipherText
            override fun writeEncrypted(value: String?) {
                persistedCipherText = value
            }
        }

        val jar = PersistentCookieJar(cipher, backend)
        val url = "https://aulavirtual2.unap.edu.pe/web/user/info/system/courseinrole".toHttpUrl()
        val future = System.currentTimeMillis() + 3_600_000L
        val secretCookie = Cookie.Builder()
            .name("aula.session")
            .value("super_secret_token_987")
            .domain("aulavirtual2.unap.edu.pe")
            .path("/")
            .expiresAt(future)
            .secure()
            .httpOnly()
            .build()

        jar.saveFromResponse(url, listOf(secretCookie))

        // Plaintext token must not be written unencrypted to disk
        assertTrue(persistedCipherText != null)
        assertFalse(persistedCipherText!!.contains("super_secret_token_987"))

        // Rehydrated jar restores cookie properly
        val rehydrated = PersistentCookieJar(cipher, backend)
        val loaded = rehydrated.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("super_secret_token_987", loaded.first().value)

        // Logout clears memory and backing storage
        rehydrated.clearAll()
        assertNull(persistedCipherText)
        assertTrue(rehydrated.loadForRequest(url).isEmpty())
    }
}
