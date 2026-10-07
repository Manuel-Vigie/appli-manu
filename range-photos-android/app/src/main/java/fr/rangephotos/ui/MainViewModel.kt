package fr.rangephotos.ui

import android.app.Application
import android.content.Context
import android.media.MediaScannerConnection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.rangephotos.content.ContentAnalyzer
import fr.rangephotos.face.FaceAnalyzer
import fr.rangephotos.logic.Duplicates
import fr.rangephotos.logic.Hike
import fr.rangephotos.logic.HikeDetector
import fr.rangephotos.logic.HikeNamer
import fr.rangephotos.logic.PlaceNamer
import fr.rangephotos.logic.PlannedMove
import fr.rangephotos.logic.Planner
import fr.rangephotos.model.Category
import fr.rangephotos.model.CategoryKind
import fr.rangephotos.model.PhotoInfo
import fr.rangephotos.model.Subjects
import fr.rangephotos.organize.FolderCleanup
import fr.rangephotos.organize.Organizer
import fr.rangephotos.people.CategoryStore
import fr.rangephotos.people.FaceMatching
import fr.rangephotos.people.PeopleStore
import fr.rangephotos.people.PeopleSync
import fr.rangephotos.scan.PhotoScanner
import fr.rangephotos.storage.Access
import fr.rangephotos.storage.Place
import fr.rangephotos.storage.Places
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Un groupe de visages qui se ressemblent : probablement une même personne, à nommer. */
class ClusterUi(val id: Int, val photos: Int, val thumbs: List<ByteArray>)

/** Un endroit de l'écran d'accueil, avec le nombre de photos (null tant que le comptage n'est pas fini). */
class PlaceUi(val place: Place, val toSort: Int?, val sorted: Int?)

sealed interface UiState {
    data class Home(
        val access: Boolean,
        val places: List<PlaceUi>,
        val people: List<String>,
        /** Noms des dossiers personnalisés actifs. */
        val activeCategories: List<String>,
        val hasUndo: Boolean,
        val reclassifyAll: Boolean,
        val notice: String? = null,
        val noticeIsError: Boolean = false,
    ) : UiState

    /** [total] = 0 signifie « durée inconnue » (barre de progression indéterminée). */
    data class Working(val label: String, val done: Int, val total: Int) : UiState

    /** Étape « Mes proches » : donner un prénom aux personnes qui reviennent souvent. */
    data class People(val clusters: List<ClusterUi>, val known: List<String>) : UiState

    data class ManagePeople(val names: List<String>) : UiState

    /** « Mes dossiers » : les dossiers à remplir d'après le contenu des photos. */
    data class Categories(val items: List<Category>) : UiState

    data object NewCategory : UiState

    data class Preview(
        val placeTitle: String,
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
        /** Reclassement complet : photos déjà au bon endroit et copies existantes laissées telles quelles. */
        val reclassify: Boolean = false,
        val unchanged: Int = 0,
        val leftCopies: Int = 0,
        /** Pas de ville trouvée (hors connexion) : les dossiers de journées n'ont pas de ville. */
        val townsUnavailable: Boolean = false,
        /** Dossier de destination, tel qu'affiché (ex. « Photos rangées »). */
        val destination: String = "",
        /** Supprimer les anciens dossiers une fois vides (jamais s'il reste un fichier). */
        val cleanFolders: Boolean = true,
    ) : UiState

    /** Choix du dossier de destination : on parcourt les dossiers de l'endroit. */
    data class ChooseDestination(
        val placeKey: String,
        val placeTitle: String,
        /** Chemin du dossier affiché, relatif à la racine (vide = la racine). */
        val path: String,
        val folders: List<String>,
        val canGoUp: Boolean,
        /** Vrai si l'on peut choisir le dossier affiché. */
        val canChoose: Boolean,
        val message: String? = null,
    ) : UiState

    data class Done(
        val title: String,
        val details: String,
        val failures: List<String>,
        val hasUndo: Boolean,
        val canOpen: Boolean,
        val success: Boolean,
    ) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private class FaceRef(val photo: Int, val face: Int)

    private val scanner = PhotoScanner()
    private val faceAnalyzer = FaceAnalyzer(app)
    private val namer = HikeNamer(app)
    private val organizer = Organizer { paths ->
        // Prévient la galerie du téléphone : les photos apparaissent à leur nouvelle place.
        MediaScannerConnection.scanFile(app, paths.toTypedArray(), null, null)
    }
    private val journal = File(app.filesDir, "journal.tsv")
    private val store = PeopleStore(File(app.filesDir, "proches.json")).also { runCatching { it.load() } }
    private val categoryStore = CategoryStore(File(app.filesDir, "dossiers.json")).also { runCatching { it.load() } }
    private val contentAnalyzer = ContentAnalyzer()
    private val sync = PeopleSync()
    private val prefs = app.getSharedPreferences("range_photos", Context.MODE_PRIVATE)

    private var places: List<Place> = emptyList()
    private var counts: Map<String, Pair<Int, Int>> = emptyMap()
    private var reclassifyAll = false
    private var notice: String? = null
    private var noticeIsError = false

    private var currentPlace: Place? = null
    private var photos: List<PhotoInfo> = emptyList()
    private var hikes: List<Hike> = emptyList()
    private var hikeNames: Map<Hike, String> = emptyMap()
    private var clusters: List<List<FaceRef>> = emptyList()
    private var copyToPeople = true
    private var pendingMoves: List<PlannedMove> = emptyList()
    private var alreadySorted = 0
    private var leftCopies = 0
    private var reclassifyRun = false
    private var activeCategories: List<Category> = emptyList()
    private var dayPlaces: Map<LocalDate, String> = emptyMap()
    private var townsUnavailable = false
    private var cleanFolders = true
    private var browsing: File? = null
    private var browsingPlace: Place? = null

    private val _state = MutableStateFlow<UiState>(UiState.Working("Démarrage…", 0, 0))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        retireOldJournal()
        refresh()
    }

    private fun hasUndo() = journal.exists() && journal.length() > 0

    /** Les journaux des versions précédentes (sélecteur de dossier Android) ne servent plus : on les met de côté. */
    private fun retireOldJournal() {
        runCatching {
            if (journal.exists() && journal.readText().contains("content://")) {
                journal.renameTo(File(journal.parentFile, "journal-ancien.tsv"))
            }
        }
    }

    // ---- Accueil ------------------------------------------------------------------------------

    /** Réaffiche l'accueil : autorisation, endroits trouvés, nombre de photos de chacun. */
    fun refresh(newNotice: String? = null, isError: Boolean = false) {
        notice = newNotice
        noticeIsError = isError
        counts = emptyMap()
        val access = Access.granted(getApplication())
        places = if (access) Places.detect(getApplication()).map { withSavedDestination(it) } else emptyList()
        publishHome(access)
        if (access) {
            viewModelScope.launch {
                val found = withContext(Dispatchers.IO) {
                    places.associate { place -> place.key to runCatching { scanner.counts(place) }.getOrDefault(0 to 0) }
                }
                counts = found
                if (_state.value is UiState.Home) publishHome(true)
            }
        }
    }

    /** Applique le dossier de destination choisi (s'il existe encore et se trouve bien sur le même volume). */
    private fun withSavedDestination(place: Place): Place {
        val saved = prefs.getString("dest_${place.key}", null) ?: return place
        val dir = File(saved)
        return if (saved.startsWith(place.root.path + "/") && dir.isDirectory && destinationProblem(place, dir) == null) {
            place.copy(destination = dir)
        } else {
            place
        }
    }

    /** Pourquoi ce dossier ne peut pas servir de destination (null = il convient). */
    private fun destinationProblem(place: Place, dir: File): String? = when {
        dir == place.root -> "Choisissez un dossier précis, pas toute la mémoire."
        dir.name.startsWith(".") -> "Choisissez un dossier visible (pas un dossier caché)."
        place.scanRoots.any { it == dir } ->
            "Ce dossier est un dossier où l'appli cherche vos photos (« ${dir.name} ») : choisissez-en un autre, ou un dossier à l'intérieur."
        place.scanRoots.any { it.path.startsWith(dir.path + "/") } ->
            "Ce dossier contient « ${place.scanRoots.first { it.path.startsWith(dir.path + "/") }.name} » : choisissez-en un autre."
        dir.name.lowercase() in FORBIDDEN && dir.parentFile == place.root -> "Ce dossier est un dossier du système : choisissez-en un autre."
        else -> null
    }

    private fun publishHome(access: Boolean) {
        _state.value = UiState.Home(
            access = access,
            places = places.map { PlaceUi(it, counts[it.key]?.first, counts[it.key]?.second) },
            people = store.people.map { it.name },
            activeCategories = categoryStore.enabled().map { it.name },
            hasUndo = hasUndo(),
            reclassifyAll = reclassifyAll,
            notice = notice,
            noticeIsError = noticeIsError,
        )
    }

    /** Appelé quand l'appli revient au premier plan (par exemple après les réglages d'autorisation). */
    fun onResume() {
        if (_state.value is UiState.Home) refresh(notice, noticeIsError)
    }

    fun setReclassifyAll(on: Boolean) {
        reclassifyAll = on
        if (_state.value is UiState.Home) publishHome(Access.granted(getApplication()))
    }

    fun showNotice(message: String, isError: Boolean = false) {
        notice = message
        noticeIsError = isError
        if (_state.value is UiState.Home) publishHome(Access.granted(getApplication()))
    }

    fun backToStart() {
        pendingMoves = emptyList()
        photos = emptyList()
        clusters = emptyList()
        refresh()
    }

    // ---- Analyse ------------------------------------------------------------------------------

    fun analyze(placeKey: String) {
        val place = places.firstOrNull { it.key == placeKey } ?: return
        currentPlace = place
        reclassifyRun = reclassifyAll
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Recherche des photos…", 0, 0)
                var scanned = withContext(Dispatchers.IO) {
                    scanner.scan(place, reclassifyRun) { found ->
                        _state.value = UiState.Working("Recherche des photos… $found trouvées", 0, 0)
                    }
                }
                leftCopies = 0
                alreadySorted = 0
                if (reclassifyRun) {
                    val (unique, left) = Duplicates.keepOnePerPhoto(scanned)
                    scanned = unique
                    leftCopies = left
                } else {
                    alreadySorted = counts[place.key]?.second ?: 0
                }
                if (scanned.isEmpty()) {
                    refresh(
                        if (alreadySorted > 0) {
                            "Aucune nouvelle photo à ranger sur « ${place.title} » : $alreadySorted sont déjà rangées. " +
                                "Pour tout reprendre, choisissez « Tout reclasser »."
                        } else {
                            "Aucune photo trouvée sur « ${place.title} »."
                        },
                    )
                    return@launch
                }

                _state.value = UiState.Working("Recherche des randonnées…", 0, 0)
                hikes = HikeDetector.detect(scanned)
                hikeNames = nameHikes(hikes)

                // Retrouve les prénoms déjà connus sur cette carte (changement de téléphone).
                runCatching {
                    sync.read(place.outputDir)?.let {
                        store.mergeJson(it)
                        store.save()
                    }
                }

                runCatching { sync.readCategories(place.outputDir)?.let { categoryStore.importIfEmpty(it) } }
                activeCategories = categoryStore.enabled()

                val withFaces = detectFaces(scanned)
                photos = detectContent(identifyKnown(withFaces), activeCategories)
                dayPlaces = findDayPlaces(photos)
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
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
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
                    async { if (photo.isScreenshot || photo.isVideo) photo else photo.copy(faces = faceAnalyzer.analyze(photo.uri)) }
                }.awaitAll()
            }
            result += analyzed
            done += chunk.size
            _state.value = UiState.Working("Reconnaissance des visages…", done, list.size)
        }
        return result
    }

    /**
     * Regarde le contenu des photos pour les dossiers de l'utilisateur (codes-barres, sujets, lieux).
     * Les photos de personnes et de randonnées ont déjà leur dossier : on ne les analyse pas.
     */
    private suspend fun detectContent(list: List<PhotoInfo>, categories: List<Category>): List<PhotoInfo> {
        if (categories.isEmpty()) return list
        val needBarcode = categories.any { it.kind == CategoryKind.BARCODE }
        val needLabels = categories.any { it.kind == CategoryKind.SUBJECTS }
        val needPlace = categories.any { it.kind == CategoryKind.PLACE }
        val zone = ZoneId.systemDefault()
        val targets = list.indices.filter { i ->
            val photo = list[i]
            !photo.isVideo && photo.faces.isEmpty() && HikeDetector.hikeFor(photo, hikes, zone) == null
        }
        val result = list.toMutableList()

        if ((needBarcode || needLabels) && targets.isNotEmpty()) {
            var done = 0
            for (chunk in targets.chunked(4)) {
                val analyzed = coroutineScope {
                    chunk.map { i ->
                        async {
                            val path = list[i].path
                            i to (if (path == null) null else contentAnalyzer.analyze(path, needBarcode, needLabels))
                        }
                    }.awaitAll()
                }
                for ((i, content) in analyzed) {
                    if (content != null) result[i] = list[i].copy(hasBarcode = content.hasBarcode, labels = content.labels)
                }
                done += chunk.size
                _state.value = UiState.Working("Analyse du contenu des photos…", done, targets.size)
            }
        }

        if (needPlace && targets.isNotEmpty()) {
            val namer = PlaceNamer(getApplication())
            targets.forEachIndexed { n, i ->
                val photo = result[i]
                val lat = photo.lat
                val lon = photo.lon
                if (lat != null && lon != null) result[i] = photo.copy(place = namer.nameFor(lat, lon))
                if (n % 20 == 0) _state.value = UiState.Working("Recherche des noms de lieux…", n, targets.size)
            }
        }
        return result
    }

    /** Ville où l'on était chaque jour (hors randonnées), pour nommer les dossiers de journées. Nécessite internet. */
    private suspend fun findDayPlaces(list: List<PhotoInfo>): Map<LocalDate, String> {
        val zone = ZoneId.systemDefault()
        val byDay = list
            .filter { it.hasGps && HikeDetector.hikeFor(it, hikes, zone) == null }
            .groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
        val namer = PlaceNamer(getApplication())
        val result = LinkedHashMap<LocalDate, String>()
        var n = 0
        for ((date, photos) in byDay) {
            // Lieu principal du jour : la zone (environ 2 km) où il y a le plus de photos.
            val main = photos.groupBy { "${(it.lat!! * 50).roundToInt()}:${(it.lon!! * 50).roundToInt()}" }
                .maxByOrNull { it.value.size }?.value?.firstOrNull()
            val town = main?.let { namer.nameFor(it.lat!!, it.lon!!) }
            if (town != null) result[date] = town
            n++
            if (n % 10 == 0) _state.value = UiState.Working("Recherche des villes des journées…", n, byDay.size)
        }
        townsUnavailable = byDay.isNotEmpty() && result.isEmpty()
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
        val plan = Planner.plan(photos, hikes, hikeNames, copyToPeople, categories = activeCategories, dayPlaces = dayPlaces)
        // Une photo déjà au bon endroit et sans copie à faire n'a rien à subir.
        pendingMoves = plan.filter { it.photo.currentFolder != it.folder || it.copies.isNotEmpty() }
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
        val moving = plan.count { it.photo.currentFolder != it.folder }
        return UiState.Preview(
            placeTitle = currentPlace?.title ?: "",
            total = moving,
            topLevel = topLevel,
            hikes = hikeList,
            hasNamedPeople = named,
            copyToPeople = copyToPeople,
            copyCount = copyCount,
            copyMegabytes = copyBytes / 1_000_000,
            alreadySorted = alreadySorted,
            reclassify = reclassifyRun,
            unchanged = plan.size - moving,
            leftCopies = leftCopies,
            townsUnavailable = townsUnavailable,
            destination = currentPlace?.let { destinationLabel(it) } ?: "",
            cleanFolders = cleanFolders,
        )
    }

    fun setCleanFolders(on: Boolean) {
        cleanFolders = on
        val current = _state.value
        if (current is UiState.Preview) _state.value = current.copy(cleanFolders = on)
    }

    // ---- Rangement et annulation --------------------------------------------------------------

    fun confirm() {
        val place = currentPlace ?: return
        val moves = pendingMoves
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Rangement en cours…", 0, moves.size)
                val result = withContext(Dispatchers.IO) {
                    val cleanup = if (cleanFolders) FolderCleanup(place.root, setOf(place.root, place.outputDir) + place.scanRoots) else null
                    val r = organizer.execute(place.outputDir, moves, journal, cleanup) { done, total ->
                        if (done % 5 == 0 || done == total) _state.value = UiState.Working("Rangement en cours…", done, total)
                    }
                    // Sauvegarde des prénoms à côté des photos (pour un futur téléphone).
                    if (store.people.isNotEmpty()) sync.write(place.outputDir, store.toJson())
                    if (categoryStore.customCount() > 0 || categoryStore.enabled().isNotEmpty()) {
                        sync.writeCategories(place.outputDir, categoryStore.toJson())
                    }
                    r
                }
                pendingMoves = emptyList()

                val lines = ArrayList<String>()
                lines += "${result.moved} photo(s) et vidéo(s) rangées dans « ${destinationLabel(place)} » (${place.title})."
                if (result.copied > 0) lines += "${result.copied} copie(s) ajoutées dans les dossiers de vos proches."
                if (result.copiesAlreadyThere > 0) lines += "${result.copiesAlreadyThere} copie(s) existaient déjà : rien n'a été refait."
                if (result.foldersRemoved > 0) lines += "${result.foldersRemoved} ancien(s) dossier(s) vide(s) supprimé(s)."
                if (result.foldersKept.isNotEmpty()) {
                    lines += "Dossier(s) gardé(s) car il reste des fichiers dedans : ${result.foldersKept.joinToString(", ") { "« $it »" }}."
                }
                if (result.failed > 0) lines += "${result.failed} photo(s) n'ont pas pu être déplacées et sont restées en place."
                if (result.copyFailed > 0) lines += "${result.copyFailed} copie(s) n'ont pas pu être faites."
                val success = result.failed == 0 && result.copyFailed == 0
                _state.value = UiState.Done(
                    title = when {
                        result.moved == 0 && result.failed > 0 -> "Le rangement a échoué"
                        success -> "Rangement terminé"
                        else -> "Rangement terminé, avec des problèmes"
                    },
                    details = lines.joinToString("\n"),
                    failures = result.failures,
                    hasUndo = hasUndo(),
                    canOpen = place.outputDir.isDirectory,
                    success = success,
                )
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    fun undo() {
        val place = currentPlace
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Annulation en cours…", 0, 0)
                val result = withContext(Dispatchers.IO) {
                    organizer.undo(journal) { done, total ->
                        if (done % 5 == 0 || done == total) _state.value = UiState.Working("Annulation en cours…", done, total)
                    }
                }
                val lines = ArrayList<String>()
                lines += "${result.moved} photo(s) et vidéo(s) remises à leur place d'origine."
                if (result.copied > 0) lines += "${result.copied} copie(s) supprimées (celles créées par l'appli)."
                if (result.failed > 0) lines += "${result.failed} élément(s) n'ont pas pu être annulés."
                _state.value = UiState.Done(
                    title = "Rangement annulé",
                    details = lines.joinToString("\n"),
                    failures = result.failures,
                    hasUndo = hasUndo(),
                    canOpen = place?.outputDir?.isDirectory == true,
                    success = result.failed == 0,
                )
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /** L'endroit du dernier rangement (pour le bouton « Ouvrir mes photos rangées » de l'écran de fin). */
    fun lastPlace(): Place? = currentPlace

    // ---- Dossier de destination ---------------------------------------------------------------

    /** Nom du dossier de destination, relatif à la racine (ex. « Photos rangées » ou « Mes souvenirs/Triées »). */
    fun destinationLabel(place: Place): String = place.outputDir.toRelativeString(place.root)

    fun openDestination(placeKey: String) {
        val place = places.firstOrNull { it.key == placeKey } ?: return
        browsingPlace = place
        val start = place.outputDir.takeIf { it.isDirectory } ?: place.root
        showFolder(start, null)
    }

    private fun showFolder(dir: File, message: String?) {
        val place = browsingPlace ?: return
        browsing = dir
        val folders = (dir.listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith(".") && !(dir == place.root && it.name == "Android") }
            .map { it.name }
            .sortedBy { it.lowercase() }
        _state.value = UiState.ChooseDestination(
            placeKey = place.key,
            placeTitle = place.title,
            path = dir.toRelativeString(place.root),
            folders = folders,
            canGoUp = dir != place.root,
            canChoose = destinationProblem(place, dir) == null,
            message = message ?: destinationProblem(place, dir),
        )
    }

    fun browseInto(name: String) {
        val dir = browsing ?: return
        val child = File(dir, name)
        if (child.isDirectory) showFolder(child, null)
    }

    fun browseUp() {
        val dir = browsing ?: return
        val place = browsingPlace ?: return
        if (dir == place.root) return
        showFolder(dir.parentFile ?: place.root, null)
    }

    fun createFolder(rawName: String) {
        val dir = browsing ?: return
        val name = Planner.sanitize(rawName.trim())
        if (rawName.isBlank()) {
            showFolder(dir, "Écrivez d'abord un nom pour le nouveau dossier.")
            return
        }
        val folder = File(dir, name)
        folder.mkdirs()
        if (folder.isDirectory) showFolder(folder, null) else showFolder(dir, "Impossible de créer le dossier « $name ».")
    }

    fun confirmDestination() {
        val place = browsingPlace ?: return
        val dir = browsing ?: return
        if (destinationProblem(place, dir) != null) return
        prefs.edit().putString("dest_${place.key}", dir.path).apply()
        browsing = null
        browsingPlace = null
        refresh("Les photos et vidéos seront rangées dans « ${dir.toRelativeString(place.root)} » (${place.title}).")
    }

    fun cancelDestination() {
        browsing = null
        browsingPlace = null
        refresh()
    }

    // ---- Mes proches --------------------------------------------------------------------------

    fun openPeople() {
        _state.value = UiState.ManagePeople(store.people.map { it.name })
    }

    /** L'appli oublie ce prénom (les photos déjà rangées ne bougent pas). */
    fun forget(name: String) {
        store.remove(name)
        runCatching { store.save() }
        _state.value = UiState.ManagePeople(store.people.map { it.name })
    }

    // ---- Mes dossiers -------------------------------------------------------------------------

    fun openCategories() {
        _state.value = UiState.Categories(categoryStore.categories)
    }

    fun setCategoryEnabled(id: String, on: Boolean) {
        categoryStore.setEnabled(id, on)
        runCatching { categoryStore.save() }
        _state.value = UiState.Categories(categoryStore.categories)
    }

    fun deleteCategory(id: String) {
        categoryStore.remove(id)
        runCatching { categoryStore.save() }
        _state.value = UiState.Categories(categoryStore.categories)
    }

    fun openNewCategory() {
        _state.value = UiState.NewCategory
    }

    fun saveCategory(name: String, kind: CategoryKind, subjectNames: Set<String>) {
        categoryStore.add(name, kind, if (kind == CategoryKind.SUBJECTS) Subjects.labelsOf(subjectNames) else emptySet())
        runCatching { categoryStore.save() }
        _state.value = UiState.Categories(categoryStore.categories)
    }

    override fun onCleared() {
        contentAnalyzer.close()
        faceAnalyzer.close()
    }

    private companion object {
        const val MAX_CLUSTERS = 30
        val FORBIDDEN = setOf(
            "dcim", "pictures", "movies", "download", "downloads", "documents", "music", "android",
            "alarms", "notifications", "podcasts", "ringtones", "audiobooks", "recordings",
        )
    }
}
