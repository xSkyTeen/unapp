package sky.universidad.UnApp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import sky.universidad.UnApp.core.update.AppUpdateManager

class AppUpdateManagerTest {

    @Test
    fun `parses GitHub releases API payload when newer version exists`() {
        val json = """
            {
              "tag_name": "v1.1",
              "name": "UnApp 1.1",
              "body": "Correcciones y mejoras visuales.",
              "assets": [
                {
                  "name": "UnApp-v1.1.apk",
                  "browser_download_url": "https://github.com/user/unapp/releases/download/v1.1/UnApp-v1.1.apk"
                }
              ]
            }
        """.trimIndent()

        val info = AppUpdateManager.parseUpdatePayload(json, currentVersionCode = 1, currentVersionName = "1.0")
        assertNotNull(info)
        assertEquals("1.1", info!!.versionName)
        assertEquals("https://github.com/user/unapp/releases/download/v1.1/UnApp-v1.1.apk", info.apkUrl)
        assertEquals("Correcciones y mejoras visuales.", info.releaseNotes)
    }

    @Test
    fun `parses custom version json payload and ignores same version`() {
        val newerJson = """
            {
              "versionCode": 2,
              "versionName": "1.1",
              "apkUrl": "https://example.com/unapp-v1.1.apk",
              "notes": "Soporte de tabla en plan de estudios."
            }
        """.trimIndent()

        val newer = AppUpdateManager.parseUpdatePayload(newerJson, currentVersionCode = 1, currentVersionName = "1.0")
        assertNotNull(newer)
        assertEquals(2, newer!!.versionCode)

        val same = AppUpdateManager.parseUpdatePayload(newerJson, currentVersionCode = 2, currentVersionName = "1.1")
        assertNull(same)
    }
}
