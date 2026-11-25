package com.deportv.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.deportv.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Actualizaciones desde GitHub Releases (repo público).
 * Cada release lleva un tag "vX.Y.Z" y un .apk adjunto firmado con la misma clave.
 */
object Updater {

    data class Release(val version: String, val apkUrl: String, val notes: String)

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true).build()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    private val rawBase get() = "https://raw.githubusercontent.com/${BuildConfig.UPDATE_REPO}/main/updates/"

    /**
     * Devuelve la versión nueva si es más reciente que la instalada; null si no hay novedades.
     * 1º mira updates/latest.json del repo; si no existe, la última GitHub Release.
     */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val fromRepo = try { checkRepoManifest() } catch (e: Exception) { null }
        if (fromRepo != null) return@withContext fromRepo.takeIf { isNewer(it.version, currentVersion) }
        checkReleases()
    }

    /** updates/latest.json: {"version":"0.2.0","apk":"DeporTV-0.2.0.apk","notes":"..."} */
    private fun checkRepoManifest(): Release? {
        val req = Request.Builder().url(rawBase + "latest.json?t=" + System.currentTimeMillis() / 300_000)
            .header("User-Agent", "DeporTV-updater").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val j = JSONObject(r.body!!.string())
            val v = j.optString("version").removePrefix("v")
            var apk = j.optString("apk")
            if (v.isEmpty() || apk.isEmpty()) return null
            if (!apk.startsWith("http")) apk = rawBase + apk
            return Release(v, apk, j.optString("notes").trim())
        }
    }

    private fun checkReleases(): Release? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DeporTV-updater")
            .build()
        http.newCall(req).execute().use { r ->
            if (r.code == 404) return null // aún no hay releases
            if (!r.isSuccessful) throw IOException("GitHub HTTP ${r.code}")
            val j = JSONObject(r.body!!.string())
            val tag = j.optString("tag_name").removePrefix("v").removePrefix("V")
            val assets = j.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", true)) { apk = a.optString("browser_download_url"); break }
            }
            return if (apk == null || !isNewer(tag, currentVersion)) null
            else Release(tag, apk, j.optString("body").trim())
        }
    }

    /** Compara "0.10.1" con "0.9.3" número a número. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun download(ctx: Context, rel: Release, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "deportv-${rel.version}.apk")
        val req = Request.Builder().url(rel.apkUrl).header("User-Agent", "DeporTV-updater").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Descarga HTTP ${r.code}")
            val body = r.body!!
            val total = body.contentLength()
            body.byteStream().use { inp ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var last = -1
                    while (true) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct != last) { last = pct; withContext(Dispatchers.Main) { progress(pct) } }
                        }
                    }
                }
            }
        }
        out
    }

    /** ¿Puede DeporTV lanzar instalaciones? (Android 8+ pide permiso por app la primera vez) */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (i in intents) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
        }
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }
}
