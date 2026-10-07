package fr.rangephotos.people

import fr.rangephotos.model.Category
import fr.rangephotos.model.CategoryKind
import fr.rangephotos.model.Subjects
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Mémoire des dossiers personnalisés (« Mes dossiers »), dans un petit fichier JSON de l'appli. */
class CategoryStore(private val file: File? = null) {

    var categories: List<Category> = Subjects.presets()
        private set

    fun enabled(): List<Category> = categories.filter { it.enabled }

    fun load() {
        val text = file?.takeIf { it.isFile }?.readText() ?: return
        categories = withPresets(parse(text))
    }

    fun save() {
        file?.let {
            it.parentFile?.mkdirs()
            it.writeText(toJson())
        }
    }

    fun setEnabled(id: String, on: Boolean) {
        categories = categories.map { if (it.id == id) it.copy(enabled = on) else it }
    }

    /** Seuls les dossiers créés par l'utilisateur peuvent être supprimés. */
    fun remove(id: String) {
        categories = categories.filterNot { it.id == id && !it.builtIn }
    }

    fun add(name: String, kind: CategoryKind, labels: Set<String>): Category {
        val clean = name.trim().replace(Regex("""[\\/:*?"<>|]"""), "-").trim().trimEnd('.').ifEmpty { "Sans nom" }
        var unique = clean
        var n = 2
        while (categories.any { it.name.equals(unique, ignoreCase = true) }) unique = "$clean ($n)".also { n++ }
        val category = Category("c" + System.currentTimeMillis(), unique, kind, labels, enabled = true)
        categories = categories + category
        return category
    }

    fun customCount() = categories.count { !it.builtIn }

    /** Nouveau téléphone : reprend les dossiers sauvegardés sur la carte, sans écraser ceux déjà présents. */
    fun importIfEmpty(json: String) {
        if (customCount() > 0 || categories.any { it.enabled }) return
        categories = withPresets(parse(json))
    }

    fun toJson(): String {
        val array = JSONArray()
        categories.forEach { c ->
            array.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("kind", c.kind.name)
                    .put("labels", JSONArray(c.labels.sorted()))
                    .put("enabled", c.enabled)
                    .put("builtIn", c.builtIn)
                    .put("minConfidence", c.minConfidence.toDouble()),
            )
        }
        return JSONObject().put("categories", array).toString()
    }

    private fun parse(json: String): List<Category> = try {
        val array = JSONObject(json).getJSONArray("categories")
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val kind = runCatching { CategoryKind.valueOf(o.getString("kind")) }.getOrNull() ?: return@mapNotNull null
            val labels = o.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
            Category(
                id = o.getString("id"),
                name = o.getString("name"),
                kind = kind,
                labels = labels,
                enabled = o.optBoolean("enabled", false),
                builtIn = o.optBoolean("builtIn", false),
                minConfidence = o.optDouble("minConfidence", 0.6).toFloat(),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Garde les dossiers lus et ajoute les dossiers proposés d'office qui manqueraient. */
    private fun withPresets(read: List<Category>): List<Category> {
        val ids = read.map { it.id }.toSet()
        val missing = Subjects.presets().filter { it.id !in ids }
        // Les dossiers proposés d'office gardent leurs sujets à jour avec l'appli, mais pas leur interrupteur.
        val refreshed = read.map { c -> Subjects.presets().firstOrNull { it.id == c.id }?.copy(enabled = c.enabled) ?: c }
        return refreshed + missing
    }
}
