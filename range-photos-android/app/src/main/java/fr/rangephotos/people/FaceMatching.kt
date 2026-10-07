package fr.rangephotos.people

import kotlin.math.sqrt

/**
 * Comparaison des empreintes de visages (vecteurs normalisés : le produit scalaire est la ressemblance,
 * de -1 à 1). Seuils réglés d'après les mesures sur ordinateur : même personne ≈ 0,5 à 0,9,
 * personnes différentes ≈ 0 à 0,3 ; le seuil publié pour ce modèle est 0,363.
 */
object FaceMatching {
    /** Ressemblance minimale pour rattacher un visage à un prénom connu. */
    const val SAME_PERSON = 0.40f

    /** Ressemblance minimale pour regrouper deux visages inconnus. */
    const val CLUSTER = 0.42f

    /** Écart minimal avec la deuxième personne la plus proche, pour ne pas confondre deux proches qui se ressemblent. */
    const val MARGIN = 0.03f

    const val MAX_EXEMPLARS = 12
    private const val MERGE_BONUS = 0.08f

    class Known(val name: String, val exemplars: List<FloatArray>)

    fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    fun normalize(v: FloatArray): FloatArray {
        var n = 0f
        for (x in v) n += x * x
        n = sqrt(n)
        return if (n < 1e-9f) v.copyOf() else FloatArray(v.size) { v[it] / n }
    }

    /** Prénom de la personne connue la plus ressemblante, ou null si aucune n'est assez sûre. */
    fun identify(embedding: FloatArray, people: List<Known>): String? {
        var bestName: String? = null
        var best = -1f
        var second = -1f
        for (p in people) {
            var s = -1f
            for (e in p.exemplars) s = maxOf(s, dot(embedding, e))
            if (s > best) {
                second = best
                best = s
                bestName = p.name
            } else if (s > second) {
                second = s
            }
        }
        return if (bestName != null && best >= SAME_PERSON && best - second >= MARGIN) bestName else null
    }

    /**
     * Regroupe les empreintes qui se ressemblent (une personne = un groupe). Retourne des listes d'indices,
     * les plus grands groupes d'abord. Passer les visages les plus nets en premier donne de meilleurs groupes.
     */
    fun cluster(embeddings: List<FloatArray>, threshold: Float = CLUSTER): List<List<Int>> {
        if (embeddings.isEmpty()) return emptyList()
        val dim = embeddings[0].size
        val sums = ArrayList<FloatArray>()
        val centroids = ArrayList<FloatArray>()

        fun nearest(e: FloatArray): Int {
            var index = -1
            var best = threshold
            for (c in centroids.indices) {
                val s = dot(e, centroids[c])
                if (s >= best) {
                    best = s
                    index = c
                }
            }
            return index
        }

        // Passe 1 : chaque visage rejoint le groupe le plus proche, ou en crée un.
        for (e in embeddings) {
            val c = nearest(e)
            if (c < 0) {
                sums += e.copyOf()
                centroids += e.copyOf()
            } else {
                val sum = sums[c]
                for (i in 0 until dim) sum[i] += e[i]
                centroids[c] = normalize(sum)
            }
        }

        // Passe 2 : on redistribue tout le monde avec les centres définitifs.
        val groups = ArrayList<ArrayList<Int>>()
        repeat(centroids.size) { groups.add(ArrayList<Int>()) }
        embeddings.forEachIndexed { index, e ->
            val c = nearest(e)
            if (c < 0) groups.add(arrayListOf(index)) else groups[c].add(index)
        }
        groups.removeAll { it.isEmpty() }

        // Fusion : deux groupes (d'au moins 2 visages) très proches sont la même personne.
        var merged = true
        while (merged) {
            merged = false
            val big = groups.indices.filter { groups[it].size >= 2 }
            val centers = big.associateWith { centroidOf(embeddings, groups[it]) }
            search@ for (x in big.indices) {
                for (y in x + 1 until big.size) {
                    if (dot(centers.getValue(big[x]), centers.getValue(big[y])) >= threshold + MERGE_BONUS) {
                        groups[big[x]].addAll(groups[big[y]])
                        groups.removeAt(big[y])
                        merged = true
                        break@search
                    }
                }
            }
        }
        return groups.sortedByDescending { it.size }.map { it.toList() }
    }

    private fun centroidOf(embeddings: List<FloatArray>, members: List<Int>): FloatArray {
        val sum = FloatArray(embeddings[0].size)
        for (m in members) for (i in sum.indices) sum[i] += embeddings[m][i]
        return normalize(sum)
    }

    /** Garde au plus [max] empreintes bien différentes les unes des autres (pour garder la variété : lunettes, âge, angle). */
    fun selectExemplars(all: List<FloatArray>, max: Int = MAX_EXEMPLARS): List<FloatArray> {
        if (all.size <= max) return all
        val chosen = ArrayList<FloatArray>()
        chosen += all[0]
        val closest = FloatArray(all.size) { dot(all[it], all[0]) }
        while (chosen.size < max) {
            var pick = -1
            var lowest = 2f
            for (i in all.indices) {
                if (closest[i] < lowest) {
                    lowest = closest[i]
                    pick = i
                }
            }
            chosen += all[pick]
            for (i in all.indices) closest[i] = maxOf(closest[i], dot(all[i], all[pick]))
        }
        return chosen
    }
}
