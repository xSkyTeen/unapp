package sky.universidad.UnApp.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String = ""
)

object AppUpdateManager {

    private val client = OkHttpClient()

    suspend fun checkForUpdate(
        feedUrl: String,
        currentVersionCode: Int,
        currentVersionName: String
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val cleanUrl = feedUrl.trim()
        if (cleanUrl.isBlank()) return@withContext null
        runCatching {
            val req = Request.Builder()
                .url(cleanUrl)
                .header("Accept", "application/vnd.github+json, application/json")
                .header("User-Agent", "UnApp-Android")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body?.string().orEmpty()
                parseUpdatePayload(body, currentVersionCode, currentVersionName)
            }
        }.getOrNull()
    }

    internal fun parseUpdatePayload(
        rawJson: String,
        currentVersionCode: Int,
        currentVersionName: String
    ): AppUpdateInfo? {
        if (rawJson.isBlank()) return null
        val root = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull()
            ?: return null

        // Format 1: Simple custom JSON { "versionCode": 2, "versionName": "1.1", "apkUrl": "...", "notes": "..." }
        if (root.has("apkUrl") || root.has("downloadUrl")) {
            val remoteCode = root.get("versionCode")?.asInt ?: 0
            val remoteName = root.get("versionName")?.asString?.trim().orEmpty()
                .ifBlank { remoteCode.toString() }
            val apkUrl = (root.get("apkUrl") ?: root.get("downloadUrl"))?.asString?.trim().orEmpty()
            val notes = (root.get("notes") ?: root.get("releaseNotes"))?.asString?.trim().orEmpty()
            if (apkUrl.isBlank()) return null
            if (!isRemoteVersionNewer(remoteCode, remoteName, currentVersionCode, currentVersionName)) {
                return null
            }
            return AppUpdateInfo(
                versionCode = remoteCode,
                versionName = remoteName,
                apkUrl = apkUrl,
                releaseNotes = notes
            )
        }

        // Format 2: GitHub Releases API (/repos/{owner}/{repo}/releases/latest)
        val tagName = root.get("tag_name")?.asString?.trim().orEmpty()
        val releaseName = root.get("name")?.asString?.trim().orEmpty()
        val bodyNotes = root.get("body")?.asString?.trim().orEmpty()
        val assets = root.getAsJsonArray("assets") ?: return null

        var apkUrl = ""
        for (el in assets) {
            val obj = el.asJsonObject
            val url = obj.get("browser_download_url")?.asString?.trim().orEmpty()
            val name = obj.get("name")?.asString?.trim().orEmpty()
            if (url.endsWith(".apk", ignoreCase = true) || name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = url
                break
            }
        }
        if (apkUrl.isBlank()) return null

        val cleanVersion = tagName.ifBlank { releaseName }
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("+")
            .trim()
        val parsedCode = tagName.substringAfter("+", "").toIntOrNull() ?: 0
        if (!isRemoteVersionNewer(parsedCode, cleanVersion, currentVersionCode, currentVersionName)) {
            return null
        }

        return AppUpdateInfo(
            versionCode = parsedCode,
            versionName = cleanVersion.ifBlank { tagName },
            apkUrl = apkUrl,
            releaseNotes = bodyNotes
        )
    }

    internal fun isRemoteVersionNewer(
        remoteCode: Int,
        remoteName: String,
        currentCode: Int,
        currentName: String
    ): Boolean {
        if (remoteCode > 0 && currentCode > 0 && remoteCode != currentCode) {
            return remoteCode > currentCode
        }
        val rParts = remoteName.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
        val cParts = currentName.split(Regex("[^0-9]+")).filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply {
                if (!exists()) mkdirs()
            }
            val outFile = File(updatesDir, "unapp-update.apk")
            val req = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "UnApp-Android")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    error("Error HTTP ${res.code} al descargar actualización")
                }
                val body = res.body ?: error("Archivo APK vacío")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0L) {
                                onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
            }
            if (!outFile.exists() || outFile.length() <= 0L) {
                error("No se pudo guardar el APK descargado")
            }
            outFile
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() <= 0L) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
