package fr.rangephotos.people

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.Locale

/** Un proche : un prénom et quelques empreintes de son visage (variées : angles, lunettes, âge). */
class Person(val name: String, val exemplars: List<FloatArray>)

/**
 * Mémoire des prénoms. Sauvegardée dans l'appli, et recopiée sur la carte SD (dossier « Photos rangées »)
 * pour que tout se retrouve en changeant de téléphone. Ne contient ni photo ni nom de fichier :
 * seulement des prénoms et des suites de nombres.
 */
class PeopleStore(private val file: File? = null) {

    private val map = LinkedHashMap<String, Person>()

    val people: List<Person> get() = map.values.toList()

    fun clean(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    fun key(name: String): String = clean(name).lowercase(Locale.ROOT)

    /** Ajoute des empreintes à un proche (le crée si besoin). « julie » et « Julie » sont la même personne. */
    fun add(name: String, embeddings: List<FloatArray>) {
        val cleaned = clean(name)
        if (cleaned.isEmpty()) return
        val k = key(cleaned)
        val old = map[k]
        val all = (old?.exemplars ?: emptyList()) + embeddings
        map[k] = Person(old?.name ?: cleaned, FaceMatching.selectExemplars(all))
    }

    fun remove(name: String) {
        map.remove(key(name))
    }

    fun known(): List<FaceMatching.Known> = map.values.map { FaceMatching.Known(it.name, it.exemplars) }

    fun toJson(): String {
        val list = JSONArray()
        for (p in map.values) {
            val encoded = JSONArray()
            for (e in p.exemplars) encoded.put(encode(e))
            list.put(JSONObject().put("name", p.name).put("exemplars", encoded))
        }
        return JSONObject().put("version", 1).put("people", list).toString()
    }

    /** Ajoute le contenu d'une sauvegarde (sans rien effacer). Un texte illisible est ignoré. */
    fun mergeJson(text: String) {
        try {
            val list = JSONObject(text).getJSONArray("people")
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val encoded = item.getJSONArray("exemplars")
                val vectors = (0 until encoded.length()).map { decode(encoded.getString(it)) }
                add(item.getString("name"), vectors)
            }
        } catch (_: Exception) {
            // sauvegarde abîmée : on repart de ce qu'on a
        }
    }

    fun load() {
        val f = file ?: return
        if (f.exists()) mergeJson(f.readText())
    }

    fun save() {
        file?.writeText(toJson())
    }

    companion object {
        fun encode(v: FloatArray): String {
            val buffer = ByteBuffer.allocate(4 * v.size).order(ByteOrder.LITTLE_ENDIAN)
            for (x in v) buffer.putFloat(x)
            return Base64.getEncoder().encodeToString(buffer.array())
        }

        fun decode(text: String): FloatArray {
            val bytes = Base64.getDecoder().decode(text)
            val out = FloatArray(bytes.size / 4)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
            return out
        }
    }
}
