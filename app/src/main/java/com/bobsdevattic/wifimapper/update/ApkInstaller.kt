package com.bobsdevattic.wifimapper.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads an update APK into the app cache and hands it to the system package
 * installer. Install still requires explicit user confirmation in the OS dialog,
 * and on Android 8+ the "install unknown apps" permission for this app.
 */
class ApkInstaller(private val context: Context) {

    val canRequestInstall: Boolean
        get() = context.packageManager.canRequestPackageInstalls()

    /** Intent that sends the user to the "allow this app to install" settings page. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )

    /**
     * Download [info]'s APK, reporting fractional progress via [onProgress].
     * @return the downloaded file, or null on failure.
     */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                // Clear stale downloads so the cache doesn't grow unbounded.
                dir.listFiles()?.forEach { it.delete() }
                val outFile = File(dir, "wifimapper-${info.versionName}.apk")

                val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "WifiMapper-Updater")
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                if (conn.responseCode !in 200..299) return@withContext null
                val total = if (info.apkSizeBytes > 0) info.apkSizeBytes else conn.contentLengthLong

                conn.inputStream.use { input ->
                    outFile.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var downloaded = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read
                            if (total > 0) onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
                onProgress(1f)
                outFile
            } catch (e: Exception) {
                null
            }
        }

    /** Launch the system installer for a previously-downloaded [apk]. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
