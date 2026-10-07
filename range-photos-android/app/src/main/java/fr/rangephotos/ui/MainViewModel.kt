package fr.rangephotos.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.rangephotos.face.FaceAnalyzer
import fr.rangephotos.logic.Hike
import fr.rangephotos.logic.HikeDetector
import fr.rangephotos.logic.HikeNamer
import fr.rangephotos.logic.PlannedMove
import fr.rangephotos.logic.Planner
import fr.rangephotos.model.PhotoInfo
import fr.rangephotos.organize.Organizer
import fr.rangephotos.people.FaceMatching
import fr.rangephotos.people.PeopleStore
import fr.rangephotos.people.PeopleSync
import fr.rangephotos.scan.PhotoScanner
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Un groupe de visages qui se ressemblent : probablement une même personne, à nommer. */
class ClusterUi(val id: Int, val photos: Int, val thumbs: List<ByteArray>)

sealed interface UiState {
    data class Start(val hasUndo: Boolean, val message: String? = null, val people: List<String> = emptyList(), val lastFolder: String? = null) : UiState

    /** [total] = 0 signifie « durée inconnue » (barre de progression indéterminée). */
    data class Working(val label: String, val done: Int, val total: Int) : UiState

    /** Étape « Mes proches » : donner un prénom aux personnes qui reviennent souvent. */
    data class People(val clusters: List<ClusterUi>, val known: List<String>) : UiState

    data class ManagePeople(val names: List<String>) : UiState

    data class Preview(
        val total: Int,
        val topLevel: List<Pair<String, Int>>,
        val hikes: List<Pair<String, Int>>,
        /** Vrai s'il y a des proches nommés : l'option de copie est alors proposée. */
        val hasNamedPeople: Boolean,
        val copyToPeople: Boolean,
        val copyCount: Int,
        val copyMegabytes: Long,
        /** Photos déjà rangées lors d'un passage précédent (non touchées). */
        val alreadySorted: Int = 0,
    ) : UiState

    data class Done(val title: String, val details: String, val hasUndo: Boolean) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private class FaceRef(val photo: Int, val face: Int)

    private val scanner = PhotoScanner(app)
    private val faceAnalyzer = FaceAnalyzer(app)
    private val namer = HikeNamer(app)
    private val organizer = Organizer(app)
    private val journal = File(app.filesDir, "journal.tsv")
    private val store = PeopleStore(File(app.filesDir, "proches.json")).also { runCatching { it.load() } }
    private val sync = PeopleSync(app)
    private val prefs = app.getSharedPreferences("range_photos", android.content.Context.MODE_PRIVATE)
    private var alreadySorted = 0

    private val _state = MutableStateFlow<UiState>(start())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pendingRoot: Uri? = null
    private var photos: List<PhotoInfo> = emptyList()
    private var hikes: List<Hike> = emptyList()
    private var hikeNames: Map<Hike, String> = emptyMap()
    private var clusters: List<List<FaceRef>> = emptyList()
    private var copyToPeople = true
    private var pendingMoves: List<PlannedMove> = emptyList()

    private fun hasUndo() = journal.exists() && journal.length() > 0

    /** Dernier dossier analysé, s'il est encore autorisé (pour relancer sans le rechoisir). */
    private fun lastRoot(): Uri? {
        val uri = prefs.getString("last_root", null)?.let { Uri.parse(it) } ?: return null
        val allowed = getApplication<Application>().contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        return if (allowed) uri else null
    }

    private fun lastFolderName(): String? {
        val uri = lastRoot() ?: return null
        return runCatching {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), uri)?.name
        }.getOrNull() ?: "dernier dossier"
    }

    private fun start(message: String? = null) =
        UiState.Start(hasUndo(), message, store.people.map { it.name }, runCatching { lastFolderName() }.getOrNull())

    /** Relance la recherche sur le même dossier (nouvelles photos ajoutées depuis). */
    fun rescan() {
        val uri = lastRoot()
        if (uri == null) _state.value = start("Choisissez de nouveau le dossier de photos.") else analyze(uri)
    }

    fun onFolderPicked(uri: Uri) {
        // Garde l'autorisation d'accès au dossier (nécessaire pour déplacer les fichiers).
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
        analyze(uri)
    }

    private fun analyze(root: Uri) {
        prefs.edit().putString("last_root", root.toString()).apply()
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Recherche des photos…", 0, 0)
                val scanned = scanner.scan(root) { found ->
                    _state.value = UiState.Working("Recherche des photos… $found trouvées", 0, 0)
                }
                alreadySorted = runCatching { scanner.countAlreadySorted(root) }.getOrDefault(0)
                if (scanned.isEmpty()) {
                    _state.value = start(
                        if (alreadySorted > 0) {
                            "Aucune nouvelle photo à ranger : $alreadySorted sont déjà rangées dans « ${Organizer.OUTPUT_DIR} »."
                        } else {
                            "Aucune photo trouvée dans ce dossier."
                        },
                    )
                    return@launch
                }

                _state.value = UiState.Working("Recherche des randonnées…", 0, 0)
                hikes = HikeDetector.detect(scanned)
                hikeNames = nameHikes(hikes)

                // Retrouve les prénoms déjà connus sur cette carte (changement de téléphone).
                runCatching {
                    sync.read(root)?.let {
                        store.mergeJson(it)
                        store.save()
                    }
                }

                val withFaces = detectFaces(scanned)
                pendingRoot = root
                photos = identifyKnown(withFaces)
                clusters = buildClusters(photos)

                if (clusters.isEmpty()) {
                    showPreview()
                } else {
                    _state.value = UiState.People(
                        clusters.mapIndexed { index, c ->
                            ClusterUi(index, c.map { it.photo }.distinct().size, thumbsOf(c))
                        },
                        store.people.map { it.name },
                    )
                }
            } catch (e: Exception) {
                _state.value = start("Une erreur est survenue : ${e.message}")
            }
        }
    }

    private suspend fun nameHikes(hikes: List<Hike>): Map<Hike, String> {
        val used = HashSet<String>()
        val result = LinkedHashMap<Hike, String>()
        for (hike in hikes) {
            val base = Planner.sanitize(namer.name(hike))
            var name = base
            var n = 2
            while (!used.add(name)) {
                name = "$base ($n)"
                n++
            }
            result[hike] = name
        }
        return result
    }

    private suspend fun detectFaces(list: List<PhotoInfo>): List<PhotoInfo> {
        val result = ArrayList<PhotoInfo>(list.size)
        var done = 0
        for (chunk in list.chunked(4)) {
            val analyzed = coroutineScope {
                chunk.map { photo ->
                    async { if (photo.isScreenshot) photo else photo.copy(faces = faceAnalyzer.analyze(photo.uri)) }
                }.awaitAll()
            }
            result += analyzed
            done += chunk.size
            _state.value = UiState.Working("Reconnaissance des visages…", done, list.size)
        }
        return result
    }

    /** Rattache aux proches déjà connus les visages qui leur ressemblent assez. */
    private fun identifyKnown(list: List<PhotoInfo>): List<PhotoInfo> {
        val known = store.known()
        if (known.isEmpty()) return list
        return list.map { photo ->
            if (photo.faces.none { it.embedding != null && it.person == null }) {
                photo
            } else {
                photo.copy(
                    faces = photo.faces.map { face ->
                        val embedding = face.embedding
                        if (face.person == null && embedding != null) {
                            face.withPerson(FaceMatching.identify(embedding, known))
                        } else {
                            face
                        }
                    },
                )
            }
        }
    }

    /** Regroupe les visages encore inconnus ; on ne garde que les personnes vues sur au moins 2 photos. */
    private fun buildClusters(list: List<PhotoInfo>): List<List<FaceRef>> {
        val refs = ArrayList<FaceRef>()
        list.forEachIndexed { photoIndex, photo ->
            photo.faces.forEachIndexed { faceIndex, face ->
                if (face.embedding != null && face.person == null) refs += FaceRef(photoIndex, faceIndex)
            }
        }
        if (refs.size < 2) return emptyList()
        refs.sortByDescending { list[it.photo].faces[it.face].area }

        val groups = FaceMatching.cluster(refs.map { list[it.photo].faces[it.face].embedding!! })
        return groups
            .map { group -> group.map { refs[it] } }
            .filter { group -> group.size >= 2 && group.map { it.photo }.distinct().size >= 2 }
            .take(MAX_CLUSTERS)
    }

    private fun thumbsOf(cluster: List<FaceRef>): List<ByteArray> =
        cluster.sortedByDescending { photos[it.photo].faces[it.face].area }
            .take(4)
            .mapNotNull { photos[it.photo].faces[it.face].thumb }

    /** [names] : numéro du groupe -> prénom saisi (vide = on ne nomme pas). */
    fun applyNames(names: Map<Int, String>) {
        val updated = photos.toMutableList()
        for ((id, raw) in names) {
            val typed = store.clean(raw)
            if (typed.isEmpty()) continue
            val group = clusters.getOrNull(id) ?: continue
            // Même prénom que quelqu'un de déjà connu (majuscules ignorées) : c'est la même personne.
            val name = store.people.firstOrNull { store.key(it.name) == store.key(typed) }?.name ?: typed
            for (ref in group) {
                val photo = updated[ref.photo]
                updated[ref.photo] = photo.copy(
                    faces = photo.faces.mapIndexed { i, face -> if (i == ref.face) face.withPerson(name) else face },
                )
            }
            store.add(name, group.mapNotNull { updated[it.photo].faces[it.face].embedding })
        }
        photos = identifyKnown(updated) // les visages isolés peuvent maintenant être reconnus
        runCatching { store.save() }
        showPreview()
    }

    fun skipPeople() = showPreview()

    private fun showPreview() {
        val plan = Planner.plan(photos, hikes, hikeNames, copyToPeople)
        pendingMoves = plan
        _state.value = buildPreview(plan)
    }

    fun setCopyToPeople(on: Boolean) {
        copyToPeople = on
        showPreview()
    }

    private fun buildPreview(plan: List<PlannedMove>): UiState.Preview {
        val topLevel = plan.groupingBy { it.folder.first() }.eachCount().toList().sortedByDescending { it.second }
        val hikeCounts = plan
            .filter { it.folder.first() == Planner.HIKES }
            .groupingBy { it.folder[1] }.eachCount()
        val hikeList = hikes.mapNotNull { hikeNames[it] }.map { it to (hikeCounts[it] ?: 0) }
        val copyCount = plan.sumOf { it.copies.size }
        val copyBytes = plan.sumOf { it.photo.size * it.copies.size }
        val named = photos.any { p -> p.faces.any { it.person != null } }
        return UiState.Preview(
            total = plan.size,
            topLevel = topLevel,
            hikes = hikeList,
            hasNamedPeople = named,
            copyToPeople = copyToPeople,
            copyCount = copyCount,
            copyMegabytes = copyBytes / 1_000_000,
            alreadySorted = alreadySorted,
        )
    }

    fun confirm() {
        val root = pendingRoot ?: return
        val moves = pendingMoves
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Rangement en cours…", 0, moves.size)
                val result = organizer.execute(root, moves, journal) { done, total ->
                    _state.value = UiState.Working("Rangement en cours…", done, total)
                }
                pendingMoves = emptyList()
                // Sauvegarde des prénoms sur la carte SD (pour un futur téléphone).
                if (store.people.isNotEmpty()) runCatching { sync.write(root, store.toJson()) }

                val lines = ArrayList<String>()
                lines += "${result.moved} photo(s) rangées dans le dossier « ${Organizer.OUTPUT_DIR} »."
                if (result.copied > 0) lines += "${result.copied} copie(s) ajoutées dans les dossiers de vos proches."
                if (result.failed > 0) lines += "${result.failed} photo(s) n'ont pas pu être déplacées et sont restées en place."
                if (result.copyFailed > 0) lines += "${result.copyFailed} copie(s) n'ont pas pu être faites."
                _state.value = UiState.Done("Rangement terminé", lines.joinToString("\n"), hasUndo())
            } catch (e: Exception) {
                _state.value = start("Une erreur est survenue : ${e.message}")
            }
        }
    }

    fun undo() {
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Annulation en cours…", 0, 0)
                val result = organizer.undo(journal) { done, total ->
                    _state.value = UiState.Working("Annulation en cours…", done, total)
                }
                val lines = ArrayList<String>()
                lines += "${result.moved} photo(s) remises à leur place d'origine."
                if (result.copied > 0) lines += "${result.copied} copie(s) supprimées (celles créées par l'appli)."
                if (result.failed > 0) lines += "${result.failed} élément(s) n'ont pas pu être annulés (déplacés ou supprimés entre-temps)."
                _state.value = UiState.Done("Rangement annulé", lines.joinToString("\n"), hasUndo())
            } catch (e: Exception) {
                _state.value = start("Une erreur est survenue : ${e.message}")
            }
        }
    }

    fun openPeople() {
        _state.value = UiState.ManagePeople(store.people.map { it.name })
    }

    /** L'appli oublie ce prénom (les photos déjà rangées ne bougent pas). */
    fun forget(name: String) {
        store.remove(name)
        runCatching { store.save() }
        _state.value = UiState.ManagePeople(store.people.map { it.name })
    }

    fun backToStart() {
        pendingMoves = emptyList()
        photos = emptyList()
        clusters = emptyList()
        _state.value = start()
    }

    override fun onCleared() {
        faceAnalyzer.close()
    }

    private companion object {
        const val MAX_CLUSTERS = 30
    }
}
