package sky.universidad.UnApp.core.network

import android.util.Log
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import sky.universidad.UnApp.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Redacted network metadata interceptor. Never logs request bodies, headers, cookies, or credentials.
 */
private class SafeMetadataInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; UnApp) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36"
            )
            .build()
        val startMs = System.currentTimeMillis()
        val response = chain.proceed(request)
        if (BuildConfig.DEBUG) {
            val elapsed = System.currentTimeMillis() - startMs
            Log.d(
                "UnAppHttp",
                "${request.method} ${request.url.host}${request.url.encodedPath} -> ${response.code} (${elapsed}ms)"
            )
        }
        return response
    }
}

object OkHttpFactory {
    fun create(cookieJar: CookieJar): OkHttpClient {
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(SafeMetadataInterceptor())
            .build()
    }
}
