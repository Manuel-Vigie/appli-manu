package fr.mesphotos.update

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cherche la dernière version publiée dans les « Releases » GitHub du dépôt (lecture seule, publique).
 * Seul le numéro de version est demandé : aucune photo, aucune donnée personnelle ne part.
 */
object UpdateChecker {
    private const val RELEASES_URL = "https://api.github.com/repos/Manuel-Vigie/appli-manu/releases?per_page=30"
    private const val TAG_PREFIX = "mes-photos-v"

    data class Release(val version: String, val apkUrl: String, val pageUrl: String)

    /** Télécharge la liste des versions (à appeler hors du fil principal). */
    fun fetch(): String {
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "MesPhotos")
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Plus récente version de Mes Photos dans la réponse GitHub, ou null. */
    fun parseLatest(json: String): Release? {
        val releases = try { JSONArray(json) } catch (e: Exception) { return null }
        var best: Release? = null
        for (i in 0 until releases.length()) {
            val item = releases.optJSONObject(i) ?: continue
            if (item.optBoolean("draft") || item.optBoolean("prerelease")) continue
            val tag = item.optString("tag_name")
            if (!tag.startsWith(TAG_PREFIX)) continue
            val version = tag.removePrefix(TAG_PREFIX)
            val assets = item.optJSONArray("assets") ?: continue
            var apk = ""
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                if (asset.optString("name").endsWith(".apk")) apk = asset.optString("browser_download_url")
            }
            if (apk.isEmpty()) continue
            val current = best
            if (current == null || isNewer(version, current.version)) {
                best = Release(version, apk, item.optString("html_url"))
            }
        }
        return best
    }

    /** « 0.10.0 » est plus récent que « 0.9.0 ». */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
