package fr.mesphotos.logic

import fr.mesphotos.model.PhotoInfo
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Repère les copies exactes d'une même photo (même nom à un « (2) » près, même taille, même contenu).
 * On garde un exemplaire « original » à ranger normalement ; les autres copies sont rangées à part,
 * dans « Doublons », au lieu d'être oubliées sur place. Rien n'est jamais supprimé.
 * Deux photos de même nom et même taille mais de contenu différent restent deux photos.
 */
object Duplicates {
    private val SUFFIX = Regex("""\s\(\d+\)(?=\.[^.]*$|$)""")
    private const val CHUNK = 256 * 1024

    class Result(val originals: List<PhotoInfo>, val duplicates: List<PhotoInfo>)

    fun split(
        photos: List<PhotoInfo>,
        fingerprint: (PhotoInfo) -> String? = ::fingerprintOf,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Result {
        val groups = LinkedHashMap<String, MutableList<PhotoInfo>>()
        for (photo in photos) {
            if (photo.size <= 0L) continue
            groups.getOrPut(photo.name.replace(SUFFIX, "").lowercase() + "|" + photo.size) { ArrayList() } += photo
        }
        val candidates = groups.values.filter { it.size > 1 }
        val duplicateSet = java.util.IdentityHashMap<PhotoInfo, Boolean>()
        var done = 0
        for (group in candidates) {
            val byContent = LinkedHashMap<String, MutableList<PhotoInfo>>()
            for (photo in group) {
                val print = fingerprint(photo)
                if (print != null) byContent.getOrPut(print) { ArrayList() } += photo
            }
            for (same in byContent.values) {
                if (same.size < 2) continue
                val original = same.minByOrNull { rank(it) }!! // à égalité, le premier de la liste
                for (photo in same) if (photo !== original) duplicateSet[photo] = true
            }
            done++
            onProgress(done, candidates.size)
        }
        val originals = ArrayList<PhotoInfo>(photos.size)
        val duplicates = ArrayList<PhotoInfo>()
        for (photo in photos) if (duplicateSet.containsKey(photo)) duplicates += photo else originals += photo
        return Result(originals, duplicates)
    }

    /** Plus petit = plus probablement l'original : déjà rangé (hors Doublons), puis pas encore rangé, puis déjà dans Doublons. */
    private fun rank(photo: PhotoInfo): Int {
        val folder = photo.currentFolder ?: return 1
        return if (folder.firstOrNull() == Planner.DUPLICATES) 2 else 0
    }

    /** Empreinte du contenu : début + fin du fichier + taille. Null si le fichier ne peut pas être lu. */
    fun fingerprintOf(photo: PhotoInfo): String? {
        val path = photo.path ?: return null
        return try {
            RandomAccessFile(File(path), "r").use { f ->
                val length = f.length()
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(CHUNK)
                var read = f.read(buffer)
                if (read > 0) digest.update(buffer, 0, read)
                if (length > CHUNK) {
                    f.seek(maxOf(CHUNK.toLong(), length - CHUNK))
                    read = f.read(buffer)
                    if (read > 0) digest.update(buffer, 0, read)
                }
                length.toString() + ":" + digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (_: Exception) {
            null
        }
    }
}
