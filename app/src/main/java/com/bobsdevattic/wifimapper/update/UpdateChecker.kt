package com.bobsdevattic.wifimapper.update

import com.bobsdevattic.wifimapper.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Details of an available update parsed from a GitHub release. */
data class UpdateInfo(
    val versionName: String,
    val tag: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val releaseNotes: String,
)

/**
 * Checks the project's GitHub Releases for a version newer than the running one.
 *
 * Uses the public, unauthenticated GitHub API (60 req/hr/IP — ample for an
 * occasional update check). No user data is sent; the User-Agent is a generic
 * app identifier as the GitHub API requires.
 */
class UpdateChecker(
    private val repo: String = BuildConfig.UPDATE_REPO,
    private val currentVersionName: String = BuildConfig.VERSION_NAME,
) {
    /** @return an [UpdateInfo] if a newer release with an APK asset exists, else null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = httpGet("https://api.github.com/repos/$repo/releases/latest") ?: return@withContext null
        val release = JSONObject(json)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null

        val tag = release.optString("tag_name").ifBlank { return@withContext null }
        val latest = tag.removePrefix("v")
        if (compareSemver(latest, currentVersionName) <= 0) return@withContext null

        val assets = release.optJSONArray("assets") ?: return@withContext null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                return@withContext UpdateInfo(
                    versionName = latest,
                    tag = tag,
                    apkUrl = a.optString("browser_download_url"),
                    apkSizeBytes = a.optLong("size"),
                    releaseNotes = release.optString("body").take(2000),
                )
            }
        }
        null
    }

    private fun httpGet(urlStr: String): String? = try {
        (URL(urlStr).openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "WifiMapper-Updater")
            connectTimeout = 10_000
            readTimeout = 10_000
            if (responseCode == 200) inputStream.bufferedReader().use { it.readText() }
            else null
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Compares dotted numeric versions. Returns >0 if a>b, 0 if equal, <0 if a<b. */
        fun compareSemver(a: String, b: String): Int {
            val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
            val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x - y
            }
            return 0
        }
    }
}
