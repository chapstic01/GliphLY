package dev.gliphly

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks a GitHub repo's Releases for a newer tag than the app installed,
 * and if found, kicks off a download of the APK asset via DownloadManager.
 * Android always makes the user confirm the actual install (a system
 * security requirement) — this just removes the "go find the new build"
 * step. Point REPO at "owner/name" once this project lives on GitHub.
 */
object UpdateChecker {
    private const val REPO = "yourusername/GliphLY" // TODO: set to your GitHub repo
    private const val TAG = "UpdateChecker"

    data class UpdateInfo(val tag: String, val apkUrl: String)

    /** Runs network I/O — call from a background thread/coroutine, not the main thread. */
    fun checkLatest(currentVersionName: String): UpdateInfo? {
        return try {
            val url = URL("https://api.github.com/repos/$REPO/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8000
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tag = json.getString("tag_name").removePrefix("v")
            if (tag == currentVersionName) return null

            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    apkUrl = a.getString("browser_download_url"); break
                }
            }
            apkUrl?.let { UpdateInfo(tag, it) }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e); null
        }
    }

    /** Queues the APK download; Android will prompt the user to install once it lands. */
    fun downloadAndPromptInstall(context: Context, info: UpdateInfo) {
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("GlyphLab ${info.tag}")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "glyphlab-update.apk")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        // The completed download's system notification opens the installer directly.
    }
}
