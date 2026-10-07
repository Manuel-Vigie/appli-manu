package fr.mesphotos.scan

import java.util.Calendar

/**
 * Choix de la date d'une photo ou vidéo. Règle d'or : ne jamais inventer une date. Si ni les informations de la photo
 * (EXIF, vidéo), ni le nom du fichier ne donnent de date fiable, on prend la date du fichier mais on le SIGNALE
 * (« date incertaine »), parce qu'une copie ou un transfert remet cette date à « aujourd'hui ».
 */
object DateChoice {
    private val NAME_DATE = Regex("""(?<!\d)(19[89]\d|20\d{2})[-_.]?(\d{2})[-_.]?(\d{2})(?:[-_. T]?(\d{2})(\d{2})(\d{2}))?(?!\d)""")

    private const val DAY = 24L * 60 * 60 * 1000
    private const val YEAR_1990 = 631_152_000_000L

    /** Date (ms) lue dans un nom du type IMG_20260914_101530.jpg ; null si absente ou impossible (mois 13…). */
    fun fromFileName(name: String): Long? {
        val m = NAME_DATE.find(name) ?: return null
        val h = m.groupValues[4].ifEmpty { "12" }
        val mi = m.groupValues[5].ifEmpty { "0" }
        val s = m.groupValues[6].ifEmpty { "0" }
        return try {
            Calendar.getInstance().apply {
                isLenient = false
                set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt(), h.toInt(), mi.toInt(), s.toInt())
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun plausible(ms: Long?, now: Long): Long? = ms?.takeIf { it in YEAR_1990..(now + DAY) }

    /**
     * @param inside date lue dans le fichier (EXIF « prise de vue », sinon « numérisation » ; pour une vidéo, sa date interne)
     * @param name date lue dans le nom du fichier
     * @param gps date donnée par le GPS (jour seulement), en dernier recours avant la date du fichier
     * @return (date en ms, date incertaine ?)
     */
    fun pick(video: Boolean, inside: Long?, name: Long?, gps: Long?, modified: Long, now: Long = System.currentTimeMillis()): Pair<Long, Boolean> {
        val ordered = if (video) listOf(name, inside, gps) else listOf(inside, name, gps)
        for (candidate in ordered) plausible(candidate, now)?.let { return it to false }
        return modified to true
    }
}
