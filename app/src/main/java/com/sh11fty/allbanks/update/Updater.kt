package com.sh11fty.allbanks.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.sh11fty.allbanks.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

@Serializable
data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String = "",
)

/** Self-update from a release feed: version.json -> download APK -> verify SHA-256 -> system installer. */
class Updater(private val context: Context) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the newer release, or null if already up to date. */
    suspend fun check(): VersionInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(BuildConfig.UPDATE_URL).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Update feed returned ${r.code}")
            val info = json.decodeFromString<VersionInfo>(r.body!!.string())
            if (info.versionCode > BuildConfig.VERSION_CODE) info else null
        }
    }

    suspend fun download(info: VersionInfo): File = withContext(Dispatchers.IO) {
        require(info.apkUrl.startsWith("https://")) { "Update URL must be HTTPS" }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "all-banks.apk")
        http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("Download failed (${r.code})")
            r.body!!.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(info.sha256.trim(), ignoreCase = true)) {
            file.delete()
            throw IllegalStateException("Checksum mismatch, update discarded")
        }
        file
    }

    /** Opens Android's installer. Returns false if the user must first allow installs from this app. */
    fun install(file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
