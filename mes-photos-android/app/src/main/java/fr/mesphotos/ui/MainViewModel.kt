package fr.mesphotos.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.mesphotos.detect.NudityCache
import fr.mesphotos.detect.NudityDetector
import fr.mesphotos.detect.NudityModel
import fr.mesphotos.faces.FaceCache
import fr.mesphotos.faces.FaceEngine
import fr.mesphotos.gallery.FolderItem
import fr.mesphotos.gallery.Gallery
import fr.mesphotos.gallery.Thumbs
import fr.mesphotos.labels.LabelNames
import fr.mesphotos.labels.Labeler
import fr.mesphotos.labels.SimpleCache
import fr.mesphotos.logic.DupGroup
import fr.mesphotos.logic.FileHealth
import fr.mesphotos.logic.Health
import fr.mesphotos.logic.Verdict
import fr.mesphotos.logic.DuplicateReview
import fr.mesphotos.logic.Duplicates
import fr.mesphotos.logic.PlaceNamer
import fr.mesphotos.logic.CaptureNames
import fr.mesphotos.logic.RenameNames
import fr.mesphotos.logic.PlannedMove
import fr.mesphotos.logic.Planner
import fr.mesphotos.logic.Shortcut
import fr.mesphotos.logic.Shortcuts
import fr.mesphotos.logic.Tags
import fr.mesphotos.model.PhotoInfo
import fr.mesphotos.organize.FolderCleanup
import fr.mesphotos.organize.Organizer
import fr.mesphotos.organize.Trash
import fr.mesphotos.organize.TrashEntry
import fr.mesphotos.organize.Vault
import fr.mesphotos.organize.VaultEntry
import fr.mesphotos.organize.Verifier
import fr.mesphotos.scan.DateChoice
import fr.mesphotos.scan.PhotoScanner
import fr.mesphotos.storage.Access
import fr.mesphotos.storage.PhotoFiles
import fr.mesphotos.storage.Place
import fr.mesphotos.storage.Places
import fr.mesphotos.update.UpdateChecker
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/** Où vont les photos choisies : la corbeille (on peut effacer pour de bon) ou « À l'écart » (rangées à part, jamais effacées). */
enum class MoveKind { TRASH, ASIDE, VAULT }

/** Ordres possibles pour la liste « Toutes les photos ». */
enum class AllOrder(val label: String) {
    NEWEST("Plus récentes"),
    OLDEST("Plus anciennes"),
    NAME("Nom"),
    BIGGEST("Plus grosses"),
}

/** Recherche spéciale : « toutes les photos et vidéos », sans mot à taper. */
/** Un mot proposé par « Classer mes photos » et les photos qui le portent. */
class LabelGroup(val name: String, val files: List<File>)

const val ALL_QUERY = "\u2605toutes"

/** Un raccourci et le nombre de photos qu'il montre en ce moment. */
class ShortcutItem(val shortcut: Shortcut, val count: Int)

/** Une photo prête pour la recherche : le fichier et son chemin sans accents ni majuscules. */
class SearchEntry(val file: File, val text: String)

/** Résultat de la vérification des fichiers : combien sont sains, abîmés, brouillés… et où. */
class HealthReport(
    val total: Int,
    val counts: Map<Verdict, Int>,
    /** Fichiers à problème par date de modification (« 30/01/2026 », nombre), les plus nombreux d'abord. */
    val byDate: List<Pair<String, Int>>,
    /** Fichiers à problème par dossier de la carte. */
    val byFolder: List<Pair<String, Int>>,
    /** Quelques noms de fichiers à problème, avec leur diagnostic. */
    val samples: List<String>,
    val repairable: List<Pair<File, Health>>,
    /** Pour les fichiers « contenu inconnu » : les débuts de fichier les plus fréquents (4 premiers octets) et l'état de la fin. */
    val unknownKinds: List<Pair<String, Int>> = emptyList(),
    /** Analyse poussée des « contenu inconnu » : début / milieu / fin brouillés ou non, trace de JPEG ailleurs dans le fichier. */
    val deepKinds: List<Pair<String, Int>> = emptyList(),
    /** Les fichiers définitivement inutilisables (inconnus, brouillés, vides) : ceux qu'on propose de mettre à la corbeille. */
    val unusable: List<File> = emptyList(),
    val message: String? = null,
)

sealed interface UiState {
    data class Home(
        val access: Boolean,
        val hasCard: Boolean,
        /** Photos et vidéos à ranger / déjà rangées (null tant que le comptage n'est pas fini). */
        val toSort: Int?,
        val sorted: Int?,
        val hasUndo: Boolean,
        val notice: String? = null,
        val noticeIsError: Boolean = false,
        /** Nombre de photos et vidéos à la corbeille. */
        val trashCount: Int = 0,
        /** Nombre de photos et vidéos mises à l'écart. */
        val asideCount: Int = 0,
        /** Nombre de suggestions de la recherche automatique (photos qui semblent montrer des personnes nues). */
        val suggestions: Int = 0,
    ) : UiState

    /** [total] = 0 : durée inconnue. */
    data class Working(val label: String, val done: Int, val total: Int, val onStop: (() -> Unit)? = null) : UiState

    data class Preview(
        val toMove: Int,
        val alreadyOk: Int,
        val undated: Int,
        /** Sans date fiable ET sans lieu : tous dans « Sans date ni lieu ». */
        val nothing: Int,
        /** Copies exactes à ranger dans « Doublons ». */
        val duplicates: Int = 0,
        val townsUnavailable: Boolean,
        /** Années et nombre de fichiers. */
        val years: List<Pair<String, Int>>,
    ) : UiState

    /** Fin du lot test : on regarde, puis on décide. */
    data class StepDone(val moved: Int, val remaining: Int, val folders: List<String>, val problems: List<String>) : UiState

    data class Done(
        val title: String,
        val details: String,
        val failures: List<String>,
        val hasUndo: Boolean,
        val success: Boolean,
        val cleanable: Int = 0,
    ) : UiState

    data class CleanConfirm(val folders: List<String>) : UiState

    /** Galerie : un dossier de « Photos rangées ». */
    data class Browse(
        val path: List<String>,
        val folders: List<FolderItem>,
        val files: List<File>,
        val message: String? = null,
        /** Nombre de fichiers qu'on vient de mettre à la corbeille et qu'on peut remettre d'un geste (0 = rien). */
        val undoTrash: Int = 0,
        /** Les raccourcis, montrés en haut de la page d'accueil des photos (à la racine seulement). */
        val shortcuts: List<ShortcutItem> = emptyList(),
    ) : UiState

    /** Résultat de « Vérifier les photos ». */
    data class HealthView(val report: HealthReport) : UiState
    /** Résultat de « Classer mes photos » : des groupes proposés (mot + photos), à cocher avant d'appliquer. */
    data class LabelView(val groups: List<LabelGroup>, val scanned: Int, val unknown: Int, val message: String? = null) : UiState

    /** Le coffre-fort ouvert : ce qu'il contient (jamais montré ailleurs dans l'appli). */
    data class VaultView(val entries: List<VaultEntry>, val message: String? = null) : UiState

    /** La corbeille (ou « À l'écart ») : ce qui a été mis de côté, à remettre (ou, pour la corbeille, à supprimer pour de bon). */
    data class TrashView(val entries: List<TrashEntry>, val message: String? = null, val kind: MoveKind = MoveKind.TRASH) : UiState

    /** Recherche par mots : les photos dont le chemin (album, nom du fichier…) contient tous les mots de [query]. */
    data class Search(
        val query: String,
        val results: List<File>,
        val message: String? = null,
        val undoTrash: Int = 0,
        /** Les raccourcis : tous quand rien n'est tapé, sinon ceux dont le nom ou le classement correspond. */
        val shortcuts: List<ShortcutItem> = emptyList(),
        /** Seulement pour « Toutes les photos » : l'ordre choisi. */
        val order: AllOrder? = null,
        val hideRenamed: Boolean = false,
        val renamedCount: Int = 0,
    ) : UiState

    /** Accueil de la recherche par visage : combien de photos ont déjà été regardées. */
    data class Faces(val analysed: Int, val total: Int, val faces: Int, val message: String? = null) : UiState

    /** Analyse des visages en cours. */
    data class FaceScan(val done: Int, val total: Int, val faces: Int) : UiState

    /** Plusieurs visages sur la photo choisie : on touche celui à chercher. [crops] : visages redressés. */
    data class FacePick(val crops: List<Bitmap>) : UiState

    /** Les photos où l'on voit la même personne. [level] : 0 = sûr, 1 = normal, 2 = large (plus de photos, moins sûr). */
    data class FaceResults(
        val results: List<File>,
        val level: Int,
        val message: String? = null,
        val undoTrash: Int = 0,
    ) : UiState

    /** Les doublons exacts, par groupes : la personne vérifie lequel garder avant que les autres aillent à la corbeille. */
    data class DupReview(val groups: List<DupGroup>, val message: String? = null, val undoTrash: Int = 0) : UiState

    /** Visionneuse plein écran. */
    data class Viewer(
        val path: List<String>,
        val files: List<File>,
        val index: Int,
        val backToFaces: Boolean = false,
        val backToDups: Boolean = false,
        val backToSearch: String? = null,
        /** Petit message affiché en bas (ex. « Renommée : … »). */
        val message: String? = null,
        val backToReview: Boolean = false,
        /** Vrai quand on regarde une photo du coffre-fort. */
        val fromVault: Boolean = false,
    ) : UiState

    /** Recherche automatique en cours. [total] = 0 : pas encore compté. */
    data class NudityScan(val done: Int, val total: Int, val found: Int) : UiState

    /** Suggestions de la recherche automatique : à regarder, puis à mettre de côté ou à retirer de la liste. */
    data class Review(val files: List<File>, val message: String? = null, val undoTrash: Int = 0) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private class RunState(
        var totalBefore: Int = 0,
        val executed: MutableList<PlannedMove> = ArrayList(),
        var moved: Int = 0,
        var failed: Int = 0,
        val failures: MutableList<String> = ArrayList(),
        var batches: Int = 0,
    )

    private val scanner = PhotoScanner()
    private val organizer = Organizer { paths ->
        // Prévient la galerie du téléphone : les photos apparaissent à leur nouvelle place.
        MediaScannerConnection.scanFile(app, paths.toTypedArray(), null, null)
    }
    private val journal = File(app.filesDir, "journal.tsv")

    private var place: Place? = null
    private var counts: Pair<Int, Int>? = null
    private var notice: String? = null
    private var noticeIsError = false

    private var plan: List<PlannedMove> = emptyList()
    private var pendingMoves: List<PlannedMove> = emptyList()
    private var remainingMoves: List<PlannedMove> = emptyList()
    private var run = RunState()
    private var browsePath: List<String> = emptyList()
    private var trashCount = 0
    private var asideCount = 0
    private var lastMoved: List<TrashEntry> = emptyList()
    private var lastKind = MoveKind.TRASH
    private var searchIndex: List<SearchEntry>? = null
    private var searchQuery = ""
    private var dupGroups: List<DupGroup> = emptyList()
    private var faceJob: Job? = null
    private var faceQuery: FloatArray? = null
    private var faceLevel = 1
    private var pickedFaces: List<FaceEngine.Found> = emptyList()
    private val faceCache by lazy { FaceCache(File(getApplication<Application>().filesDir, "visages.tsv")) }
    private var suggestionCount = 0
    private var scanJob: Job? = null
    private val nudityCache by lazy { NudityCache(File(getApplication<Application>().filesDir, "nudite.tsv")) }
    private val tags by lazy { Tags(File(getApplication<Application>().filesDir, "etiquettes.txt")).also { it.load() } }
    private val shortcuts by lazy { Shortcuts(File(getApplication<Application>().filesDir, "raccourcis.tsv")).also { it.load() } }

    private val vault by lazy { Vault(File(getApplication<Application>().filesDir, "coffre")) }
    private var vaultOpen = false

    // Mise à jour
    var updateMessage: String? = null
        private set
    var updateUrl: String? = null
        private set

    private val _state = MutableStateFlow<UiState>(UiState.Working("Démarrage…", 0, 0))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        // Comme une appli photo : on arrive directement sur les photos.
        if (place != null) browse(emptyList())
    }

    private fun hasUndo() = journal.exists() && journal.length() > 0

    private fun stashOf(p: Place, kind: MoveKind): Trash {
        // Prévient la galerie du téléphone : les photos disparaissent (ou reviennent) à leur place.
        val onChanged = { paths: List<String> -> MediaScannerConnection.scanFile(getApplication(), paths.toTypedArray(), null, null); Unit }
        return when (kind) {
            MoveKind.TRASH -> Trash(p.outputDir, onChanged)
            MoveKind.ASIDE -> Trash(p.outputDir, onChanged, dirName = Place.ASIDE_DIR, batched = false, indexName = ".index-mes-photos.tsv")
            MoveKind.VAULT -> throw IllegalStateException("Le coffre-fort n'est pas une corbeille.")
        }
    }

    private fun trashOf(p: Place) = stashOf(p, MoveKind.TRASH)

    private fun returnsToReview(): Boolean = when (val s = _state.value) {
        is UiState.Review -> true
        is UiState.Viewer -> s.backToReview
        else -> false
    }

    /** Où revenir après un déplacement : aux résultats de la recherche par visage si on en venait (liste ou visionneuse), sinon à la galerie. */
    private fun returnsToFaces(): Boolean = when (val s = _state.value) {
        is UiState.FaceResults -> true
        is UiState.Viewer -> s.backToFaces
        else -> false
    }

    /** Où revenir après un déplacement : à la recherche par mots si on en venait. */
    private fun returnQuery(): String? = when (val s = _state.value) {
        is UiState.Search -> s.query
        is UiState.Viewer -> s.backToSearch
        else -> null
    }

    private fun returnsToDups(): Boolean = when (val s = _state.value) {
        is UiState.DupReview -> true
        is UiState.Viewer -> s.backToDups
        else -> false
    }

    // ---- Accueil -------------------------------------------------------------------------------

    fun refresh(newNotice: String? = null, isError: Boolean = false) {
        searchIndex = null // les photos ont peut-être changé de place
        notice = newNotice
        noticeIsError = isError
        counts = null
        val access = Access.granted(getApplication())
        place = if (access) Places.detect(getApplication()).firstOrNull() else null
        publishHome(access)
        val current = place ?: return
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                trashCount = runCatching { trashOf(current).count() }.getOrDefault(0)
                asideCount = runCatching { stashOf(current, MoveKind.ASIDE).count() }.getOrDefault(0)
                runCatching { moveOldShots(current) }
                if (scanJob?.isActive != true) suggestionCount = runCatching { currentSuggestions(current).size }.getOrDefault(0)
                runCatching { scanner.counts(current) }.getOrDefault(0 to 0)
            }
            counts = found
            if (_state.value is UiState.Home) publishHome(true)
        }
    }

    private fun publishHome(access: Boolean) {
        _state.value = UiState.Home(
            access = access,
            hasCard = place != null,
            toSort = counts?.first,
            sorted = counts?.second,
            hasUndo = hasUndo(),
            notice = notice,
            noticeIsError = noticeIsError,
            trashCount = trashCount,
            asideCount = asideCount,
            suggestions = suggestionCount,
        )
    }

    fun onResume() {
        if (_state.value is UiState.Home) refresh(notice, noticeIsError)
    }

    fun showNotice(message: String, isError: Boolean = false) {
        notice = message
        noticeIsError = isError
        if (_state.value is UiState.Home) publishHome(Access.granted(getApplication()))
    }

    // ---- Appareil photo ------------------------------------------------------------------------
    // L'appareil photo du téléphone fait la photo ; l'appli lui indique où l'enregistrer : sur la carte SD,
    // dans « Photos à trier » (hors de « Photos rangées »), d'où le prochain « Ranger » la classe par date.

    /** Un fichier vide, prêt à recevoir la photo, dans « Photos à trier » ; null s'il n'y a pas de carte ou si on ne peut pas y écrire. */
    fun newShotFile(label: String = ""): File? {
        val current = place ?: return null
        val dir = current.captureDir
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return null
            val target = RenameNames.unique(dir, CaptureNames.fileName(System.currentTimeMillis(), label = label))
            if (target.createNewFile()) target else null
        } catch (e: Exception) {
            null
        }
    }

    /** Les mots proposés avant la photo (« Immatriculation », « Véhicule », « Montagne »…), à compléter. */
    fun tagChoices(): List<String> = tags.all()

    /** Ajoute un mot à la liste ; retourne le mot gardé (null si vide). */
    fun addTag(typed: String): String? = tags.add(typed)

    fun removeTag(tag: String) = tags.remove(tag)

    /** Ramène dans « Photos rangées / Photos à trier » les photos prises avant la V17 (elles étaient à la racine de la carte, hors de la galerie). */
    private fun moveOldShots(current: Place) {
        val old = current.legacyCaptureDir
        val files = old.listFiles { f -> f.isFile && PhotoFiles.mimeOf(f) != null && f.length() > 0 } ?: return
        if (files.isEmpty()) return
        val dir = current.captureDir
        if (!dir.isDirectory && !dir.mkdirs()) return
        val touched = ArrayList<String>()
        for (f in files) {
            val target = RenameNames.unique(dir, f.name)
            if (f.renameTo(target) && target.isFile && !f.exists()) {
                touched += f.absolutePath
                touched += target.absolutePath
            }
        }
        if (touched.isNotEmpty()) MediaScannerConnection.scanFile(getApplication(), touched.toTypedArray(), null, null)
        old.delete() // ne réussit que si le dossier est vide
    }

    /** Retour de l'appareil photo : [ok] vrai si la photo a été prise. On montre tout de suite la photo enregistrée. */
    fun photoTaken(file: File, ok: Boolean) {
        if (ok && file.isFile && file.length() > 0) {
            MediaScannerConnection.scanFile(getApplication(), arrayOf(file.absolutePath), null, null)
            val current = place
            viewModelScope.launch {
                searchIndex = null
                val folder = file.parentFile
                val siblings = withContext(Dispatchers.IO) { if (folder != null) Gallery.media(folder) else listOf(file) }
                val files = if (siblings.any { it.absolutePath == file.absolutePath }) siblings else siblings + file
                val path = if (current != null && folder != null) folder.toRelativeString(current.outputDir).split('/').filter { it.isNotEmpty() } else emptyList()
                browsePath = path
                _state.value = UiState.Viewer(
                    path, files, files.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0),
                    message = "Photo enregistrée dans « Photos rangées / ${Place.CAPTURE_DIR} » : ${file.name}.\nTouchez l'étoile pour en faire un raccourci.",
                )
            }
            return
        }
        // Photo annulée, ou appareil photo qui n'a pas écrit au bon endroit : on ne laisse pas de fichier vide.
        if (file.isFile && file.length() == 0L) file.delete()
        if (ok) {
            refresh(
                "L'appareil photo du téléphone n'a pas enregistré la photo à l'endroit prévu (« ${Place.CAPTURE_DIR} » sur la carte). Rien n'a été perdu ni déplacé. Si la photo a été prise, regardez dans la galerie du téléphone.",
                isError = true,
            )
        } else {
            refresh("Aucune photo enregistrée : l'appareil photo a été fermé sans prendre de photo. Si elle a été prise quand même, regardez dans la galerie du téléphone.")
        }
    }

    fun backToStart() {
        plan = emptyList()
        pendingMoves = emptyList()
        remainingMoves = emptyList()
        refresh()
    }

    // ---- Ranger --------------------------------------------------------------------------------

    /** Analyse TOUTE la carte (même ce qui est déjà rangé) pour tout remettre à sa vraie date, puis montre l'aperçu. */
    fun analyze() {
        val current = place ?: return
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Recherche des photos et vidéos…", 0, 0)
                val scanned = withContext(Dispatchers.IO) {
                    scanner.scan(current, includeSorted = true) { found ->
                        _state.value = UiState.Working("Recherche… $found trouvés", 0, 0)
                    }
                }
                if (scanned.isEmpty()) {
                    refresh("Aucune photo ni vidéo trouvée sur la carte SD.")
                    return@launch
                }
                _state.value = UiState.Working("Recherche des doublons…", 0, 0)
                val split = withContext(Dispatchers.IO) {
                    Duplicates.split(scanned, onProgress = { done, total ->
                        if (done % 20 == 0) _state.value = UiState.Working("Recherche des doublons…", done, total)
                    })
                }
                val (dayPlaces, townsUnavailable) = findDayPlaces(split.originals)
                plan = Planner.plan(split.originals, dayPlaces = dayPlaces) +
                    split.duplicates.map { PlannedMove(it, Planner.duplicateFolder(it)) }
                // Ce qui est déjà au bon endroit ne bouge pas.
                pendingMoves = plan.filter { it.photo.currentFolder != it.folder }
                val dated = plan.filter { it.folder.firstOrNull() == Planner.PHOTOS }
                val years = dated.groupingBy { it.folder.getOrNull(1) ?: "" }.eachCount()
                    .filterKeys { it != Planner.UNDATED && it != Planner.NOTHING }
                    .toList().sortedByDescending { it.first }
                _state.value = UiState.Preview(
                    toMove = pendingMoves.size,
                    alreadyOk = plan.size - pendingMoves.size,
                    undated = dated.count { it.photo.dateGuessed && it.photo.hasGps },
                    nothing = dated.count { it.photo.dateGuessed && !it.photo.hasGps },
                    duplicates = pendingMoves.count { it.folder.firstOrNull() == Planner.DUPLICATES },
                    townsUnavailable = townsUnavailable,
                    years = years,
                )
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /** Ville où l'on était chaque jour, d'après le GPS (nécessite internet). */
    private suspend fun findDayPlaces(list: List<PhotoInfo>): Pair<Map<LocalDate, String>, Boolean> {
        val zone = ZoneId.systemDefault()
        val byDay = list.filter { it.hasGps && !it.dateGuessed }
            .groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
        val namer = PlaceNamer(getApplication())
        val result = LinkedHashMap<LocalDate, String>()
        var n = 0
        for ((date, photos) in byDay) {
            val main = photos.groupBy { "${(it.lat!! * 50).roundToInt()}:${(it.lon!! * 50).roundToInt()}" }
                .maxByOrNull { it.value.size }?.value?.firstOrNull()
            val town = main?.let { namer.nameFor(it.lat!!, it.lon!!) }
            if (town != null) result[date] = town
            n++
            if (n % 10 == 0) _state.value = UiState.Working("Recherche des villes…", n, byDay.size)
        }
        return result to (byDay.isNotEmpty() && result.isEmpty())
    }

    private fun cleanupFor(p: Place) = FolderCleanup(p.root, setOf(p.root, p.outputDir))

    /** « Ranger » : toujours par petits lots, un lot test de 10 fichiers d'abord. */
    fun confirm() {
        val current = place ?: return
        val all = pendingMoves
        if (all.isEmpty()) {
            refresh("Tout est déjà bien rangé.")
            return
        }
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Comptage avant rangement…", 0, 0)
                val before = withContext(Dispatchers.IO) { PhotoFiles.list(current, includeSorted = true).size }
                run = RunState(totalBefore = before)
                val first = all.take(FIRST_BATCH)
                remainingMoves = all.drop(first.size)
                runBatch(current, first, all.size)
                if (remainingMoves.isEmpty()) finish(current, stopped = false) else showStep(first)
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    private suspend fun runBatch(current: Place, batch: List<PlannedMove>, grandTotal: Int) {
        val offset = run.executed.size
        _state.value = UiState.Working("Rangement en cours…", offset, grandTotal)
        val r = withContext(Dispatchers.IO) {
            organizer.execute(current.outputDir, batch, journal, null, append = run.batches > 0) { done, _ ->
                if (done % 5 == 0 || done == batch.size) _state.value = UiState.Working("Rangement en cours…", offset + done, grandTotal)
            }
        }
        run.batches++
        run.executed += batch
        run.moved += r.moved
        run.failed += r.failed
        r.failures.forEach { if (run.failures.size < 8) run.failures += it }
    }

    private suspend fun showStep(batch: List<PlannedMove>) {
        val problems = withContext(Dispatchers.IO) { Verifier.checkMoves(batch, journal).third }
        pendingMoves = emptyList()
        _state.value = UiState.StepDone(
            moved = run.moved,
            remaining = remainingMoves.size,
            folders = batch.map { it.folder.joinToString(" / ") }.distinct().take(5),
            problems = problems.take(5) + run.failures.take(3),
        )
    }

    fun continueSteps() {
        val current = place ?: return
        val rest = remainingMoves
        viewModelScope.launch {
            try {
                remainingMoves = emptyList()
                runBatch(current, rest, run.executed.size + rest.size)
                finish(current, stopped = false)
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    fun stopSteps() {
        val current = place ?: return
        val left = remainingMoves.size
        remainingMoves = emptyList()
        viewModelScope.launch {
            try {
                finish(current, stopped = true, notDone = left)
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    fun cancelSteps() {
        remainingMoves = emptyList()
        undo()
    }

    /** Vérification complète puis écran de résultat. */
    private suspend fun finish(current: Place, stopped: Boolean, notDone: Int = 0) {
        _state.value = UiState.Working("Vérification complète…", 0, 0)
        val (report, cleanable) = withContext(Dispatchers.IO) {
            val leftNames = if (stopped) emptyList() else PhotoFiles.list(current, includeSorted = false).map { it.file.name }
            val after = PhotoFiles.list(current, includeSorted = true).size
            val rep = Verifier.report(Verifier.checkMoves(run.executed, journal), leftNames, run.totalBefore, after, 0)
            rep to organizer.foldersThatWouldBeRemoved(organizer.oldFoldersFromJournal(journal), cleanupFor(current)).size
        }
        pendingMoves = emptyList()
        val lines = ArrayList<String>()
        lines += "${run.moved} photo(s) et vidéo(s) rangées par date dans « ${Place.OUTPUT_DIR} »."
        if (stopped && notDone > 0) lines += "Rangement arrêté : $notDone fichier(s) n'ont pas été touchés."
        if (run.failed > 0) lines += "${run.failed} fichier(s) n'ont pas pu être déplacés et sont restés en place."
        lines += report.lines()
        if (cleanable > 0) lines += "$cleanable ancien(s) dossier(s) sont maintenant vides. Rien n'est supprimé pour l'instant."
        val success = run.failed == 0 && report.allGood
        _state.value = UiState.Done(
            title = when {
                run.moved == 0 && run.failed > 0 -> "Le rangement a échoué"
                success -> "Rangement terminé et vérifié"
                else -> "Rangement terminé, à vérifier"
            },
            details = lines.joinToString("\n"),
            failures = run.failures,
            hasUndo = hasUndo(),
            success = success,
            cleanable = cleanable,
        )
    }

    fun undo() {
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Annulation en cours…", 0, 0)
                val result = withContext(Dispatchers.IO) {
                    organizer.undo(journal) { done, total ->
                        if (done % 5 == 0 || done == total) _state.value = UiState.Working("Annulation en cours…", done, total)
                    }
                }
                _state.value = UiState.Done(
                    title = "Rangement annulé",
                    details = "${result.moved} fichier(s) remis à leur place d'origine." +
                        if (result.failed > 0) "\n${result.failed} élément(s) n'ont pas pu être annulés." else "",
                    failures = result.failures,
                    hasUndo = hasUndo(),
                    success = result.failed == 0,
                )
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    // ---- Suppression des anciens dossiers vides (étape séparée, avec liste) --------------------

    fun askClean() {
        val current = place ?: return
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                organizer.foldersThatWouldBeRemoved(organizer.oldFoldersFromJournal(journal), cleanupFor(current))
            }
            if (list.isEmpty()) refresh("Aucun ancien dossier vide à supprimer.")
            else _state.value = UiState.CleanConfirm(list.map { it.toRelativeString(current.root) })
        }
    }

    fun confirmClean() {
        val current = place ?: return
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Suppression des dossiers vides…", 0, 0)
                val (removed, kept) = withContext(Dispatchers.IO) {
                    organizer.removeEmptyFolders(organizer.oldFoldersFromJournal(journal), cleanupFor(current))
                }
                val lines = ArrayList<String>()
                lines += "$removed ancien(s) dossier(s) vide(s) supprimé(s). Aucune photo ni vidéo n'a été touchée."
                if (kept.isNotEmpty()) lines += "Gardé(s) car il reste des fichiers dedans : ${kept.joinToString(", ") { "« $it »" }}."
                _state.value = UiState.Done(
                    title = "Anciens dossiers supprimés",
                    details = lines.joinToString("\n"),
                    failures = emptyList(),
                    hasUndo = hasUndo(),
                    success = true,
                )
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    // ---- Galerie -------------------------------------------------------------------------------

    fun openGallery() = browse(emptyList())

    fun browse(path: List<String>) {
        if (place == null) return
        viewModelScope.launch {
            _state.value = UiState.Working("Ouverture…", 0, 0)
            showBrowse(path)
        }
    }

    /**
     * Affiche un dossier de la galerie. Si le dossier est (devenu) vide, on remonte au premier dossier qui contient
     * encore quelque chose. [undoCount] : combien de fichiers on peut remettre d'un geste (bouton « Annuler »).
     */
    private suspend fun showBrowse(startPath: List<String>, message: String? = null, undoCount: Int = 0) {
        val current = place ?: return
        var path = startPath
        var listing = withContext(Dispatchers.IO) { Gallery.list(current.outputDir, path) }
        while (path.isNotEmpty() && listing.folders.isEmpty() && listing.files.isEmpty()) {
            path = path.dropLast(1)
            listing = withContext(Dispatchers.IO) { Gallery.list(current.outputDir, path) }
        }
        browsePath = path
        val empty = listing.folders.isEmpty() && listing.files.isEmpty()
        val items = if (path.isEmpty()) withContext(Dispatchers.IO) { shortcutItems(allShortcuts(), searchIndex ?: buildSearchIndex(current)) } else emptyList()
        _state.value = UiState.Browse(
            path, listing.folders, listing.files,
            message = message ?: if (empty && path.isEmpty() && items.isEmpty()) "Rien à voir pour l'instant : rangez d'abord vos photos." else null,
            undoTrash = undoCount,
            shortcuts = items,
        )
    }

    fun browseInto(name: String) = browse(browsePath + name)

    /** Remonte d'un dossier ; depuis la racine, retourne à l'accueil. */
    fun browseUp() {
        if (browsePath.isEmpty()) backToStart() else browse(browsePath.dropLast(1))
    }

    fun openViewer(files: List<File>, index: Int) {
        _state.value = UiState.Viewer(browsePath, files, index, backToFaces = returnsToFaces(), backToDups = returnsToDups(), backToSearch = returnQuery(), backToReview = returnsToReview())
    }

    fun closeViewer() {
        val viewer = _state.value as? UiState.Viewer
        val query = viewer?.backToSearch
        when {
            viewer?.fromVault == true -> if (vaultOpen) openVault() else backToStart()
            viewer?.backToFaces == true -> viewModelScope.launch { showFaceResults() }
            viewer?.backToDups == true -> viewModelScope.launch { showDupReview() }
            query != null -> search(query)
            viewer?.backToReview == true -> openReview()
            else -> browse(browsePath)
        }
    }

    // ---- Corbeille et « À l'écart » -------------------------------------------------------------

    private fun countText(n: Int) = if (n > 1) "$n fichiers" else "$n fichier"

    private fun refreshStashCounts(current: Place) {
        trashCount = runCatching { trashOf(current).count() }.getOrDefault(trashCount)
        asideCount = runCatching { stashOf(current, MoveKind.ASIDE).count() }.getOrDefault(asideCount)
    }

    /**
     * Met de côté les photos [files] et tout le contenu des dossiers [folderNames] (du dossier qu'on regarde) :
     * à la corbeille, ou « À l'écart ». Rien n'est effacé : c'est un déplacement vérifié, qu'on peut annuler.
     */
    fun moveSelection(kind: MoveKind, folderNames: Set<String>, files: List<File>) {
        if (kind == MoveKind.VAULT) {
            moveToVault(folderNames, files)
            return
        }
        val current = place ?: return
        val base = browsePath
        val toFaces = returnsToFaces()
        val toDups = returnsToDups()
        val toSearch = returnQuery()
        val toReview = returnsToReview()
        val label = if (kind == MoveKind.TRASH) "Mise à la corbeille…" else "Mise à l'écart…"
        viewModelScope.launch {
            try {
                _state.value = UiState.Working(label, 0, 0)
                val result = withContext(Dispatchers.IO) {
                    val parent = base.fold(current.outputDir) { acc, name -> File(acc, name) }
                    val all = LinkedHashSet<File>(files)
                    folderNames.forEach { name -> all += Gallery.media(File(parent, name)) }
                    stashOf(current, kind).moveToTrash(all.toList()) { done, total ->
                        if (done % 10 == 0 || done == total) _state.value = UiState.Working(label, done, total)
                    }
                }
                lastMoved = result.entries
                lastKind = kind
                searchIndex = null
                withContext(Dispatchers.IO) { refreshStashCounts(current) }
                val lines = ArrayList<String>()
                lines += when {
                    result.done == 0 -> "Rien n'a été déplacé."
                    kind == MoveKind.TRASH -> "${countText(result.done)} à la corbeille. Rien n'est effacé : vous pouvez les remettre."
                    else -> "${countText(result.done)} mis à l'écart, hors de la galerie. Dossier sur la carte : « ${Place.OUTPUT_DIR} / ${Place.ASIDE_DIR} »."
                }
                if (result.failed > 0) lines += "${result.failed} fichier(s) n'ont pas pu être déplacés et sont restés en place."
                result.failures.take(2).forEach { lines += it }
                val message = lines.joinToString("\n")
                when {
                    toFaces -> showFaceResults(message, result.done)
                    toDups -> showDupReview(message, result.done)
                    toSearch != null -> showSearch(toSearch, message, result.done)
                    toReview -> showReview(message, result.done)
                    else -> showBrowse(base, message = message, undoCount = result.done)
                }
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    // ---- Vérification des fichiers -----------------------------------------------------------------
    // Lit le début (et la fin) de chaque photo et vidéo de la carte pour repérer celles qui sont vides, coupées,
    // brouillées (chiffrées) ou réparables. Rien n'est modifié ; la réparation fait des COPIES à côté des originaux.

    fun startHealthCheck() {
        val current = place ?: return
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Recherche des fichiers…", 0, 0)
                val report = withContext(Dispatchers.IO) {
                    val all = PhotoFiles.list(current, includeSorted = true).map { it.file }
                    val total = all.size
                    val counts = HashMap<Verdict, Int>()
                    val dates = HashMap<String, Int>()
                    val folders = HashMap<String, Int>()
                    val samples = ArrayList<String>()
                    val repairable = ArrayList<Pair<File, Health>>()
                    val unknown = HashMap<String, Int>()
                    val deep = HashMap<String, Int>()
                    val unusable = ArrayList<File>()
                    val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE)
                    for ((i, file) in all.withIndex()) {
                        val checked = healthOf(file)
                        val health = checked.first
                        if (health.verdict == Verdict.UNKNOWN) {
                            unknown[checked.second] = (unknown[checked.second] ?: 0) + 1
                            val d = deepKind(file)
                            deep[d] = (deep[d] ?: 0) + 1
                        }
                        counts[health.verdict] = (counts[health.verdict] ?: 0) + 1
                        if (health.verdict != Verdict.OK) {
                            val day = dateFormat.format(java.util.Date(file.lastModified()))
                            dates[day] = (dates[day] ?: 0) + 1
                            val folder = (file.parentFile ?: current.root).toRelativeString(current.root).ifEmpty { "(racine de la carte)" }
                            folders[folder] = (folders[folder] ?: 0) + 1
                            if (samples.size < 25) samples += "${file.name} : ${verdictText(health.verdict)}"
                            if (health.verdict == Verdict.REPAIRABLE) repairable += file to health
                            if (health.verdict in UNUSABLE) unusable += file
                        }
                        if (i % 40 == 0 || i == total - 1) _state.value = UiState.Working("Vérification des fichiers…", i + 1, total)
                    }
                    HealthReport(
                        total, counts,
                        dates.entries.sortedByDescending { it.value }.take(6).map { it.key to it.value },
                        folders.entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
                        samples, repairable,
                        unknownKinds = unknown.entries.sortedByDescending { it.value }.take(8).map { it.key to it.value },
                        deepKinds = deep.entries.sortedByDescending { it.value }.map { it.key to it.value },
                        unusable = unusable,
                    )
                }
                _state.value = UiState.HealthView(report)
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /** Le diagnostic d'un fichier, et une « étiquette » (début du fichier + état de la fin) pour regrouper les cas inconnus. */
    private fun healthOf(file: File): Pair<Health, String> = try {
        java.io.RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val head = ByteArray(minOf(length, FileHealth.HEAD.toLong()).toInt())
            raf.readFully(head)
            val tail = ByteArray(minOf(length, FileHealth.TAIL.toLong()).toInt())
            raf.seek(length - tail.size)
            raf.readFully(tail)
            var health = FileHealth.classify(file.extension, head, tail, length)
            // « Coupé » n'est confirmé qu'en suivant tout le JPEG : beaucoup de photos ont des données en plus après leur fin.
            if (health.verdict == Verdict.TRUNCATED) {
                val complete = java.io.FileInputStream(file).use { FileHealth.jpegReachesEnd(it) }
                if (complete) health = Health(Verdict.OK)
            }
            val firstBytes = head.take(4).joinToString(" ") { "%02X".format(it) }
            val ends = tail.size >= 2 && tail[tail.size - 2] == 0xFF.toByte() && tail[tail.size - 1] == 0xD9.toByte()
            health to (firstBytes + (if (ends) " · fin JPEG présente" else " · fin JPEG absente"))
        }
    } catch (e: Exception) {
        Health(Verdict.UNKNOWN) to "illisible (erreur de la carte)"
    }

    /** Décrit en une ligne l'état d'un fichier au contenu inconnu : quelles zones ressemblent à du hasard, et reste-t-il une trace de JPEG ? */
    private fun deepKind(file: File): String = try {
        java.io.RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val head = ByteArray(minOf(length, FileHealth.HEAD.toLong()).toInt())
            raf.readFully(head)
            val tail = ByteArray(minOf(length, 4096L).toInt())
            raf.seek(length - tail.size)
            raf.readFully(tail)
            fun zone(random: Boolean?) = when (random) { true -> "hasard"; false -> "structuré"; null -> "trop court" }
            val start = if (head.size >= 2000) FileHealth.blockLooksRandom(head, 0, 4096) else null
            val middle = if (head.size >= 4096 + 2000) FileHealth.blockLooksRandom(head, 4096, FileHealth.HEAD) else null
            val end = if (tail.size >= 2000) FileHealth.blockLooksRandom(tail, 0, tail.size) else null
            val trace = java.io.FileInputStream(file).use { FileHealth.firstJpegTrace(it) }
            "début ${zone(start)} · milieu ${zone(middle)} · fin ${zone(end)} · trace JPEG : " +
                (if (trace == null) "aucune" else "oui, à l'octet $trace")
        }
    } catch (e: Exception) {
        "illisible (erreur de la carte)"
    }

    /**
     * Nettoyage : met à la CORBEILLE les fichiers inutilisables trouvés par la vérification (jamais d'effacement direct).
     * Ils peuvent être remis d'un geste (bouton « Annuler » ou Outils, « Corbeille »), et ne sont supprimés pour de bon que
     * si on le demande depuis la corbeille.
     */
    fun trashUnusable() {
        val current = place ?: return
        val state = _state.value as? UiState.HealthView ?: return
        val files = state.report.unusable
        if (files.isEmpty()) return
        viewModelScope.launch {
            try {
                val label = "Mise à la corbeille…"
                _state.value = UiState.Working(label, 0, files.size)
                val result = withContext(Dispatchers.IO) {
                    trashOf(current).moveToTrash(files) { done, total ->
                        if (done % 10 == 0 || done == total) _state.value = UiState.Working(label, done, total)
                    }
                }
                lastMoved = result.entries
                lastKind = MoveKind.TRASH
                searchIndex = null
                withContext(Dispatchers.IO) { refreshStashCounts(current) }
                val lines = ArrayList<String>()
                lines += if (result.done == 0) "Rien n'a été déplacé."
                else "${countText(result.done)} abîmé(s) mis à la corbeille. Rien n'est effacé : pour les supprimer pour de bon, choisissez-les ici puis « Supprimer pour de bon »."
                if (result.failed > 0) lines += "${result.failed} fichier(s) n'ont pas pu être déplacés et sont restés en place (probablement hors de « ${Place.OUTPUT_DIR} »)."
                result.failures.take(2).forEach { lines += it }
                openTrash(MoveKind.TRASH, lines.joinToString("\n"))
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    private fun verdictText(v: Verdict) = when (v) {
        Verdict.OK -> "sain"
        Verdict.EMPTY -> "vide"
        Verdict.ZEROS -> "rempli de zéros (copie ratée)"
        Verdict.TRUNCATED -> "coupé à la fin"
        Verdict.REPAIRABLE -> "début abîmé, réparable"
        Verdict.SCRAMBLED -> "brouillé (chiffré)"
        Verdict.UNKNOWN -> "contenu inconnu"
    }

    /**
     * Répare les JPEG « début abîmé » : crée à côté de chacun une COPIE « nom (réparée).jpg » (l'original n'est jamais touché),
     * puis la contrôle en la faisant ouvrir ; si elle ne s'ouvre pas, elle est effacée.
     */
    fun repairHealth() {
        val current = place ?: return
        val state = _state.value as? UiState.HealthView ?: return
        val todo = state.report.repairable
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Réparation (copies)…", 0, todo.size)
                val made = withContext(Dispatchers.IO) {
                    val newPaths = ArrayList<String>()
                    var ok = 0
                    for ((i, item) in todo.withIndex()) {
                        val (file, health) = item
                        val target = RenameNames.unique(file.parentFile ?: current.root, file.nameWithoutExtension + " (réparée)." + file.extension)
                        try {
                            java.io.RandomAccessFile(file, "r").use { raf ->
                                java.io.FileOutputStream(target).use { out ->
                                    if (health.addSoi) out.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
                                    raf.seek(health.offset.toLong())
                                    val buffer = ByteArray(256 * 1024)
                                    while (true) {
                                        val r = raf.read(buffer)
                                        if (r < 0) break
                                        out.write(buffer, 0, r)
                                    }
                                }
                            }
                            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeFile(target.absolutePath, bounds)
                            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                                target.setLastModified(file.lastModified())
                                newPaths += target.absolutePath
                                ok++
                            } else {
                                target.delete()
                            }
                        } catch (e: Exception) {
                            target.delete()
                        }
                        if (i % 5 == 0) _state.value = UiState.Working("Réparation (copies)…", i + 1, todo.size)
                    }
                    if (newPaths.isNotEmpty()) MediaScannerConnection.scanFile(getApplication(), newPaths.toTypedArray(), null, null)
                    ok
                }
                searchIndex = null
                val text = if (made > 0) "$made photo(s) réparée(s) : une copie « (réparée) » a été créée à côté de chaque original, qui n'a pas été touché."
                else "Aucune photo n'a pu être réparée : leur contenu n'est pas exploitable."
                _state.value = UiState.HealthView(
                    HealthReport(
                        state.report.total, state.report.counts, state.report.byDate, state.report.byFolder,
                        state.report.samples, emptyList(), unknownKinds = state.report.unknownKinds, deepKinds = state.report.deepKinds, unusable = state.report.unusable, message = text + if (made < todo.size) "\n${todo.size - made} n'ont pas pu l'être." else "",
                    ),
                )
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    // ---- Coffre-fort ------------------------------------------------------------------------------
    // Les photos y sont copiées (copie contrôlée) dans la mémoire privée de l'appli, puis retirées de la carte.
    // L'accès demande le verrouillage du téléphone (voir MainScreen) ; il se referme dès qu'on quitte l'appli.

    /** Met les photos [files] et le contenu des dossiers [folderNames] au coffre-fort. */
    private fun moveToVault(folderNames: Set<String>, files: List<File>) {
        val current = place ?: return
        val base = browsePath
        val toFaces = returnsToFaces()
        val toDups = returnsToDups()
        val toSearch = returnQuery()
        val toReview = returnsToReview()
        val label = "Mise au coffre-fort…"
        viewModelScope.launch {
            try {
                _state.value = UiState.Working(label, 0, 0)
                val result = withContext(Dispatchers.IO) {
                    val parent = base.fold(current.outputDir) { acc, name -> File(acc, name) }
                    val all = LinkedHashSet<File>(files)
                    folderNames.forEach { name -> all += Gallery.media(File(parent, name)) }
                    val r = vault.add(all.toList(), onProgress = { done, total ->
                        if (done % 5 == 0 || done == total) _state.value = UiState.Working(label, done, total)
                    })
                    // Prévient la galerie du téléphone : ces photos n'y sont plus.
                    if (r.removedPaths.isNotEmpty()) MediaScannerConnection.scanFile(getApplication(), r.removedPaths.toTypedArray(), null, null)
                    r
                }
                searchIndex = null
                val lines = ArrayList<String>()
                lines += if (result.done == 0) "Rien n'a été mis au coffre-fort." else
                    "${countText(result.done)} au coffre-fort. Ils ont quitté la carte et ne se voient plus nulle part ailleurs. Pour les revoir : Outils, « Coffre-fort »."
                if (result.failed > 0) lines += "${result.failed} fichier(s) n'ont pas pu être mis au coffre et sont restés en place."
                result.failures.take(2).forEach { lines += it }
                val message = lines.joinToString("\n")
                when {
                    toFaces -> showFaceResults(message)
                    toDups -> showDupReview(message)
                    toSearch != null -> showSearch(toSearch, message, 0)
                    toReview -> showReview(message)
                    else -> showBrowse(base, message = message)
                }
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /**
     * Met au coffre-fort des fichiers qui sont à la corbeille ou « À l'écart ». Le coffre garde leur vraie place d'origine :
     * quand on les sort du coffre, ils retournent là, pas dans le dossier de la corbeille.
     */
    fun moveStashToVault(kind: MoveKind, items: List<TrashEntry>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            try {
                val label = "Mise au coffre-fort…"
                _state.value = UiState.Working(label, 0, items.size)
                val byFile = items.associateBy { it.trashed.absolutePath }
                val result = withContext(Dispatchers.IO) {
                    val r = vault.add(items.map { it.trashed }, { done, total ->
                        if (done % 5 == 0 || done == total) _state.value = UiState.Working(label, done, total)
                    }, originalOf = { f -> byFile[f.absolutePath]?.original?.absolutePath ?: f.absolutePath })
                    if (r.removedPaths.isNotEmpty()) MediaScannerConnection.scanFile(getApplication(), r.removedPaths.toTypedArray(), null, null)
                    r
                }
                searchIndex = null
                lastMoved = emptyList()
                val where = if (kind == MoveKind.ASIDE) "« À l'écart »" else "la corbeille"
                val lines = ArrayList<String>()
                lines += if (result.done == 0) "Rien n'a été mis au coffre-fort."
                else "${countText(result.done)} sorti(s) de $where et mis au coffre-fort. Pour les revoir : Outils, « Coffre-fort »."
                if (result.failed > 0) lines += "${result.failed} fichier(s) n'ont pas pu être mis au coffre et sont restés en place."
                result.failures.take(2).forEach { lines += it }
                openTrash(kind, lines.joinToString("\n"))
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /** À appeler une fois le verrouillage du téléphone vérifié. */
    fun unlockVault() {
        vaultOpen = true
        openVault()
    }

    /** Referme le coffre-fort (quand on quitte l'appli) : si on était dedans, retour aux outils. */
    fun lockVault() {
        vaultOpen = false
        val s = _state.value
        if (s is UiState.VaultView || (s is UiState.Viewer && s.fromVault)) refresh()
    }

    fun isVaultOpen() = vaultOpen

    fun openVault(message: String? = null) {
        if (!vaultOpen) return
        viewModelScope.launch {
            _state.value = UiState.Working("Ouverture du coffre-fort…", 0, 0)
            val entries = withContext(Dispatchers.IO) { vault.entries() }
            _state.value = UiState.VaultView(entries, message)
        }
    }

    fun openVaultViewer(entries: List<VaultEntry>, index: Int) {
        if (!vaultOpen) return
        _state.value = UiState.Viewer(emptyList(), entries.map { it.file }, index, fromVault = true)
    }

    /** Remet des photos du coffre sur la carte, à leur ancienne place (sinon dans « Photos à trier »). */
    fun restoreFromVault(items: List<VaultEntry>) {
        val current = place ?: return
        if (!vaultOpen) return
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Remise sur la carte…", 0, 0)
                val result = withContext(Dispatchers.IO) {
                    val r = vault.restore(items, current.captureDir) { done, total ->
                        if (done % 5 == 0 || done == total) _state.value = UiState.Working("Remise sur la carte…", done, total)
                    }
                    if (r.arrivedPaths.isNotEmpty()) MediaScannerConnection.scanFile(getApplication(), r.arrivedPaths.toTypedArray(), null, null)
                    r
                }
                searchIndex = null
                var text = "${countText(result.done)} sorti(s) du coffre-fort et remis sur la carte."
                if (result.failed > 0) text += "\n${result.failed} fichier(s) n'ont pas pu être remis (ils sont toujours au coffre) : ${result.failures.take(2).joinToString(" ; ")}"
                openVault(text)
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /** Depuis la visionneuse du coffre : sort la photo regardée. */
    fun restoreFromVaultFile(file: File) {
        val entry = vault.entries().firstOrNull { it.file.absolutePath == file.absolutePath } ?: return
        restoreFromVault(listOf(entry))
    }

    fun deleteFromVault(items: List<VaultEntry>) {
        if (!vaultOpen) return
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Suppression…", 0, 0)
                val result = withContext(Dispatchers.IO) { vault.deleteForever(items) }
                var text = "${countText(result.done)} supprimé(s) pour de bon."
                if (result.failed > 0) text += "\n" + result.failures.joinToString(" ")
                openVault(text)
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /** Remet ce qu'on vient de déplacer (bouton « Annuler » de la galerie ou de la recherche). */
    fun undoMove() {
        val current = place ?: return
        val items = lastMoved
        val kind = lastKind
        val base = browsePath
        val toFaces = returnsToFaces()
        val toDups = returnsToDups()
        val toSearch = returnQuery()
        val toReview = returnsToReview()
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Remise en place…", 0, 0)
                val result = withContext(Dispatchers.IO) { stashOf(current, kind).restore(items) }
                lastMoved = emptyList()
                searchIndex = null
                withContext(Dispatchers.IO) { refreshStashCounts(current) }
                var text = "${countText(result.done)} remis à leur place."
                if (result.failed > 0) text += "\n${result.failed} fichier(s) n'ont pas pu être remis (ils sont toujours mis de côté)."
                when {
                    toFaces -> showFaceResults(text)
                    toDups -> showDupReview(text)
                    toSearch != null -> showSearch(toSearch, text, 0)
                    toReview -> showReview(text)
                    else -> showBrowse(base, message = text)
                }
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    fun openTrash(kind: MoveKind = MoveKind.TRASH, message: String? = null) {
        val current = place ?: return
        viewModelScope.launch {
            _state.value = UiState.Working(if (kind == MoveKind.TRASH) "Ouverture de la corbeille…" else "Ouverture de « À l'écart »…", 0, 0)
            val entries = withContext(Dispatchers.IO) { stashOf(current, kind).entries() }
            if (kind == MoveKind.TRASH) trashCount = entries.size else asideCount = entries.size
            _state.value = UiState.TrashView(entries, message, kind)
        }
    }

    fun restoreFromTrash(kind: MoveKind, items: List<TrashEntry>) {
        val current = place ?: return
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Remise en place…", 0, 0)
                val result = withContext(Dispatchers.IO) {
                    stashOf(current, kind).restore(items) { done, total ->
                        if (done % 10 == 0 || done == total) _state.value = UiState.Working("Remise en place…", done, total)
                    }
                }
                lastMoved = emptyList()
                searchIndex = null
                var text = "${countText(result.done)} remis à leur place dans « ${Place.OUTPUT_DIR} »."
                if (result.failed > 0) text += "\n${result.failed} fichier(s) n'ont pas pu être remis : ${result.failures.take(2).joinToString(" ; ")}"
                openTrash(kind, text)
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /** Efface pour de bon ce qui est choisi dans la corbeille (jamais dans « À l'écart »). L'écran demande toujours une confirmation avant. */
    fun deleteFromTrash(items: List<TrashEntry>) {
        val current = place ?: return
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Suppression définitive…", 0, 0)
                val result = withContext(Dispatchers.IO) {
                    trashOf(current).deleteForever(items) { done, total ->
                        if (done % 10 == 0 || done == total) _state.value = UiState.Working("Suppression définitive…", done, total)
                    }
                }
                lastMoved = emptyList()
                var text = "${countText(result.done)} supprimé(s) pour de bon."
                if (result.failed > 0) text += "\n${result.failed} fichier(s) n'ont pas pu être supprimés."
                openTrash(MoveKind.TRASH, text)
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    // ---- Recherche par mots --------------------------------------------------------------------

    fun openSearch() = search(searchQuery)

    fun search(query: String) {
        if (place == null) return
        viewModelScope.launch { showSearch(query) }
    }

    private var allOrder = AllOrder.NEWEST
    /** Vrai d'office : « Toutes les photos » ne montre que celles qui n'ont pas encore été renommées (nom du type « Mot - IMG_… »). */
    private var hideRenamed = true

    private fun isRenamed(name: String) = name.contains(" - ")

    fun setHideRenamed(hide: Boolean) {
        hideRenamed = hide
        search(ALL_QUERY)
    }

    /** « Toutes les photos » : toutes les photos et vidéos de « Photos rangées » à la suite, à choisir et à mettre au coffre-fort. */
    fun openAllPhotos() = search(ALL_QUERY)

    fun setAllOrder(order: AllOrder) {
        allOrder = order
        search(ALL_QUERY)
    }

    /** Cherche [query] dans le chemin de chaque photo (dossiers année, mois, jour, ville + nom du fichier). */
    private suspend fun showSearch(query: String, message: String? = null, undoCount: Int = 0) {
        val current = place ?: return
        searchQuery = query
        val index = searchIndex ?: run {
            _state.value = UiState.Working("Préparation de la recherche…", 0, 0)
            withContext(Dispatchers.IO) { buildSearchIndex(current) }
        }
        if (query == ALL_QUERY) {
            val order = allOrder
            val hide = hideRenamed
            val renamedCount = index.count { isRenamed(it.file.name) }
            val all = withContext(Dispatchers.IO) {
                val everything = index.map { it.file } // déjà de la plus récente à la plus ancienne
                val files = if (hide) everything.filterNot { isRenamed(it.name) } else everything
                when (order) {
                    AllOrder.NEWEST -> files
                    AllOrder.OLDEST -> files.asReversed()
                    AllOrder.NAME -> files.sortedBy { Gallery.normalize(it.name) }
                    AllOrder.BIGGEST -> files.sortedByDescending { it.length() }
                }
            }
            _state.value = UiState.Search(query, all, message, undoCount, emptyList(), order, hide, renamedCount)
            return
        }
        val terms = Gallery.terms(query)
        val results = if (terms.isEmpty()) emptyList() else withContext(Dispatchers.Default) {
            index.filter { Gallery.matches(it.text, terms) }.map { it.file }
        }
        val matchingShortcuts = withContext(Dispatchers.Default) { shortcutItems(if (terms.isEmpty()) allShortcuts() else allShortcuts().filter { Gallery.matches(Gallery.normalize(it.name + " " + it.category), terms) }, index) }
        _state.value = UiState.Search(query, results, message, undoCount, matchingShortcuts)
    }

    // ---- Raccourcis ----------------------------------------------------------------------------
    // Un raccourci = un nom + un classement (« Immatriculation » dans « Véhicule ») qui mène droit à une photo.
    // Rien n'est copié ni déplacé : la photo reste là où elle est.

    /**
     * Raccourcis fournis d'office : « Vidéos » montre toutes les vidéos là où elles sont (rien n'est déplacé ni copié).
     * Si la personne en crée un du même nom et du même classement, le sien remplace celui d'office.
     */
    private val builtInShortcuts = listOf(Shortcut("Vidéos", "Types", "videos"))

    private fun allShortcuts(): List<Shortcut> {
        val own = shortcuts.all()
        val extra = builtInShortcuts.filter { b ->
            own.none { Gallery.normalize(it.name) == Gallery.normalize(b.name) && Gallery.normalize(it.category) == Gallery.normalize(b.category) }
        }
        return (own + extra).sortedWith(compareBy({ Gallery.normalize(it.category) }, { Gallery.normalize(it.name) }))
    }

    /** Les classements déjà utilisés, puis quelques idées. */
    fun shortcutCategories(): List<String> =
        (shortcuts.categories() + Shortcuts.SUGGESTIONS).distinctBy { Gallery.normalize(it) }

    /** Crée un raccourci (ou, avec [replacing], modifie celui-là). [words] vide : il cherche son nom. */
    fun saveShortcut(name: String, category: String, words: String, replacing: Shortcut? = null) {
        replacing?.let { shortcuts.remove(it) }
        val made = shortcuts.put(name, category, words)
        if (made == null) {
            // Nom vide : on remet l'ancien raccourci tel qu'il était.
            replacing?.let { shortcuts.put(it.name, it.category, it.words) }
            announce("Le nom est vide : rien n'a changé.")
        } else {
            announce("Raccourci « ${made.name} » enregistré : il montre les photos qui contiennent « ${made.words} ».")
        }
    }

    fun removeShortcut(shortcut: Shortcut) {
        val own = shortcuts.all().any { Gallery.normalize(it.name) == Gallery.normalize(shortcut.name) && Gallery.normalize(it.category) == Gallery.normalize(shortcut.category) }
        if (!own) {
            announce("Le raccourci « ${shortcut.name} » est fourni d'office : il ne peut pas être retiré.")
            return
        }
        shortcuts.remove(shortcut)
        announce("Raccourci « ${shortcut.name} » retiré. Aucune photo n'a bougé.")
    }

    /** Un appui sur un raccourci : la recherche s'ouvre avec ses mots. */
    fun openShortcut(words: String) = search(words)

    /** Dit ce qui vient de se passer, sur l'écran où l'on est (et le remet à jour : les compteurs changent). */
    private fun announce(message: String) {
        when (val st = _state.value) {
            is UiState.Viewer -> _state.value = st.copy(message = message)
            is UiState.Browse -> viewModelScope.launch { showBrowse(st.path, message) }
            is UiState.Search -> viewModelScope.launch { showSearch(st.query, message) }
            else -> Unit
        }
    }

    /** Le chemin de chaque photo (sans accents ni majuscules), gardé en mémoire : sert à la loupe et au compteur des raccourcis. */
    private fun buildSearchIndex(current: Place): List<SearchEntry> =
        Gallery.allMedia(current.outputDir)
            // Les vidéos portent en plus le mot « videos » : c'est ce qui permet le raccourci « Vidéos » (et de taper « videos » dans la loupe).
            .map { SearchEntry(it, Gallery.normalize(it.toRelativeString(current.outputDir).replace('/', ' ')) + if (PhotoFiles.isVideo(it)) " videos" else "") }
            .sortedByDescending { it.text } // les plus récentes d'abord
            .also { searchIndex = it }

    /** Les raccourcis avec le nombre de photos qu'ils montrent. */
    private fun shortcutItems(list: List<Shortcut>, index: List<SearchEntry>): List<ShortcutItem> =
        list.map { shortcut ->
            val terms = Gallery.terms(shortcut.words)
            ShortcutItem(shortcut, if (terms.isEmpty()) 0 else index.count { Gallery.matches(it.text, terms) })
        }

    // ---- Classer par reconnaissance (Google ML Kit, sur le téléphone) -----------------------------

    private val labelCache by lazy { SimpleCache(File(getApplication<Application>().filesDir, "etiquettes.tsv")) }
    private val portraitCache by lazy { SimpleCache(File(getApplication<Application>().filesDir, "portraits.tsv")) }

    @Volatile private var stopClassify = false

    /** Les photos à regarder : pas encore renommées, pas de vidéos, les plus récentes d'abord ([limit] au plus ; 0 = toutes). */
    private fun classifyCandidates(current: Place, limit: Int): List<File> =
        PhotoFiles.list(current, includeSorted = true).map { it.file }
            .filter { !PhotoFiles.isVideo(it) && !isRenamed(it.name) }
            .sortedByDescending { it.lastModified() }
            .let { if (limit > 0) it.take(limit) else it }

    /**
     * Regarde les photos et les range en groupes par mot (« Plage », « Voiture »…). Rien n'est renommé ici : on propose, on applique
     * ensuite avec [applyLabels]. Rapide : lecture des vignettes en parallèle, mémoire des photos déjà vues, bouton « Arrêter ».
     */
    fun startLabeling(limit: Int) {
        val current = place ?: return
        viewModelScope.launch {
            try {
                stopClassify = false
                val stop = { stopClassify = true }
                _state.value = UiState.Working("Recherche des photos…", 0, 0)
                val view = withContext(Dispatchers.IO) {
                    val candidates = classifyCandidates(current, limit)
                    val groups = LinkedHashMap<String, ArrayList<File>>()
                    var unknown = 0
                    var seen = 0
                    Labeler().use { labeler ->
                        for (chunk in candidates.chunked(8)) {
                            if (stopClassify) break
                            // Les vignettes d'un paquet sont lues en même temps ; la reconnaissance se fait ensuite.
                            val bitmaps = kotlinx.coroutines.coroutineScope {
                                chunk.map { f -> async { if (labelCache.get(f) != null) null else Thumbs.loadUncached(f, 256) } }
                                    .map { it.await() }
                            }
                            for ((j, file) in chunk.withIndex()) {
                                val cached = labelCache.get(file)
                                val word: String? = if (cached != null) cached.takeIf { it != "-" } else {
                                    val bmp = bitmaps[j]
                                    val w = bmp?.let { LabelNames.pick(labeler.labels(it)) }
                                    bmp?.recycle()
                                    labelCache.put(file, w ?: "-")
                                    w
                                }
                                if (word == null) unknown++ else groups.getOrPut(word) { ArrayList() } += file
                            }
                            seen += chunk.size
                            _state.value = UiState.Working("Reconnaissance des photos…", seen, candidates.size, stop)
                        }
                    }
                    val sorted = groups.entries.sortedByDescending { it.value.size }.map { LabelGroup(it.key, it.value) }
                    UiState.LabelView(sorted, seen, unknown)
                }
                _state.value = view
            } catch (e: Exception) {
                refresh("La reconnaissance n'a pas pu se faire : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /**
     * « Trouver les portraits » : cherche les visages (cadres seulement, sans empreinte : rapide) et propose deux groupes :
     * « Portrait » (un visage gros et bien visible, au plus 3 personnes) et « Personnes » (visages plus petits ou groupes).
     */
    fun startPortraits(limit: Int) {
        val current = place ?: return
        viewModelScope.launch {
            var engine: FaceEngine? = null
            try {
                stopClassify = false
                val stop = { stopClassify = true }
                _state.value = UiState.Working("Recherche des photos…", 0, 0)
                val view = withContext(Dispatchers.IO) {
                    val candidates = classifyCandidates(current, limit)
                    val portraits = ArrayList<File>()
                    val people = ArrayList<File>()
                    var none = 0
                    var seen = 0
                    for (chunk in candidates.chunked(8)) {
                        if (stopClassify) break
                        val bitmaps = kotlinx.coroutines.coroutineScope {
                            chunk.map { f -> async { if (portraitCache.get(f) != null) null else Thumbs.loadUncached(f, 640) } }
                                .map { it.await() }
                        }
                        for ((j, file) in chunk.withIndex()) {
                            var verdict = portraitCache.get(file)
                            if (verdict == null) {
                                val bmp = bitmaps[j]
                                val boxes = if (bmp == null) emptyList() else {
                                    val e = engine ?: FaceEngine(getApplication()).also { engine = it }
                                    try { runCatching { e.boxes(bmp) }.getOrDefault(emptyList()) } finally { bmp.recycle() }
                                }
                                val biggest = boxes.maxOfOrNull { it[2] * it[3] } ?: 0f
                                verdict = when {
                                    boxes.isEmpty() -> "none"
                                    biggest >= 0.04f && boxes.size <= 3 -> "portrait"
                                    else -> "people"
                                }
                                portraitCache.put(file, verdict)
                            }
                            when (verdict) {
                                "portrait" -> portraits += file
                                "people" -> people += file
                                else -> none++
                            }
                        }
                        seen += chunk.size
                        _state.value = UiState.Working("Recherche des visages…", seen, candidates.size, stop)
                    }
                    val groups = ArrayList<LabelGroup>()
                    if (portraits.isNotEmpty()) groups += LabelGroup("Portrait", portraits)
                    if (people.isNotEmpty()) groups += LabelGroup("Personnes", people)
                    UiState.LabelView(groups, seen, none)
                }
                _state.value = view
            } catch (e: Exception) {
                refresh("La recherche des portraits n'a pas pu se faire : ${e.message ?: e.javaClass.simpleName}", isError = true)
            } finally {
                engine?.close()
            }
        }
    }

    /** Ajoute le mot de chaque groupe coché devant le nom de ses photos, et crée un raccourci du même nom. */
    fun applyLabels(chosen: Set<String>) {
        val view = _state.value as? UiState.LabelView ?: return
        val groups = view.groups.filter { it.name in chosen }
        if (groups.isEmpty()) return
        viewModelScope.launch {
            try {
                val total = groups.sumOf { it.files.size }
                _state.value = UiState.Working("Renommage…", 0, total)
                val stats = withContext(Dispatchers.IO) {
                    val touched = ArrayList<String>()
                    var done = 0
                    var failed = 0
                    var seen = 0
                    for (group in groups) {
                        var groupDone = 0
                        for (file in group.files) {
                            seen++
                            val name = RenameNames.build(group.name, file.name, keepOriginal = true)
                            val dir = file.parentFile
                            if (!file.isFile || name == null || dir == null) { failed++; continue }
                            val target = RenameNames.unique(dir, name)
                            if (file.renameTo(target) && target.isFile && !file.exists()) {
                                touched += file.absolutePath
                                touched += target.absolutePath
                                updateJournalPath(file, target)
                                done++
                                groupDone++
                            } else failed++
                            if (seen % 10 == 0) _state.value = UiState.Working("Renommage…", seen, total)
                        }
                        if (groupDone > 0) shortcuts.put(group.name, "Classement automatique", group.name)
                    }
                    if (touched.isNotEmpty()) MediaScannerConnection.scanFile(getApplication(), touched.toTypedArray(), null, null)
                    done to failed
                }
                searchIndex = null
                val (done, failed) = stats
                val text = StringBuilder("${countText(done)} renommé(s) selon ce que l'appli a reconnu. Les raccourcis (classement « Classement automatique ») les retrouvent, en haut de l'onglet Photos.")
                if (failed > 0) text.append("\n$failed fichier(s) n'ont pas pu être renommés et sont restés tels quels.")
                refresh(text.toString())
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    // ---- Renommer une photo --------------------------------------------------------------------

    /**
     * Ajoute le mot [label] devant le nom de chaque photo choisie (et de celles des dossiers choisis) : « Mot - IMG_….jpg ».
     * Le reste du nom est gardé, donc la date aussi ; les photos qui portent déjà ce mot sont laissées telles quelles.
     * Elles ne changent pas de dossier : un raccourci du même mot les retrouve là où elles sont.
     */
    fun renameMany(folderNames: Set<String>, files: List<File>, label: String, makeShortcut: Boolean = false) {
        val current = place ?: return
        val base = browsePath
        val workingLabel = "Renommage…"
        viewModelScope.launch {
            try {
                if (label.isBlank()) {
                    val msg = "Aucun mot choisi : rien n'a été renommé."
                    showBrowse(base, message = msg)
                    return@launch
                }
                _state.value = UiState.Working(workingLabel, 0, 0)
                val stats = withContext(Dispatchers.IO) {
                    val parent = base.fold(current.outputDir) { acc, name -> File(acc, name) }
                    val all = LinkedHashSet<File>(files)
                    folderNames.forEach { name -> all += Gallery.media(File(parent, name)) }
                    val wanted = Gallery.normalize(label)
                    val touched = ArrayList<String>()
                    var done = 0
                    var already = 0
                    var failed = 0
                    for ((i, file) in all.withIndex()) {
                        if (!file.isFile) { failed++; continue }
                        if (Gallery.normalize(file.name).contains(wanted)) { already++; continue }
                        val name = RenameNames.build(label, file.name, keepOriginal = true)
                        val dir = file.parentFile
                        if (name == null || dir == null) { failed++; continue }
                        val target = RenameNames.unique(dir, name)
                        if (file.renameTo(target) && target.isFile && !file.exists()) {
                            touched += file.absolutePath
                            touched += target.absolutePath
                            updateJournalPath(file, target)
                            done++
                        } else failed++
                        if (i % 10 == 0 || i == all.size - 1) _state.value = UiState.Working(workingLabel, i + 1, all.size)
                    }
                    if (touched.isNotEmpty()) MediaScannerConnection.scanFile(getApplication(), touched.toTypedArray(), null, null)
                    Triple(done, already, failed)
                }
                searchIndex = null
                val (done, already, failed) = stats
                val lines = ArrayList<String>()
                lines += if (done == 0) "Rien n'a été renommé." else "${countText(done)} renommé(s) : « ${label.trim()} - … ». Un raccourci « ${label.trim()} » les retrouve."
                if (already > 0) lines += "${countText(already)} portaient déjà ce mot."
                if (failed > 0) lines += "$failed fichier(s) n'ont pas pu être renommés et sont restés tels quels."
                val words = label.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (makeShortcut && done > 0 && words.isNotEmpty()) {
                    shortcuts.put(words[0], words.getOrNull(1) ?: "", words[0])
                    lines += "Raccourci « ${words[0]} » créé (menu Raccourcis, en haut de l'onglet Photos)."
                }
                lines += "Voici les photos qui portent « ${words.joinToString(" ")} »."
                showSearch(words.joinToString(" "), lines.joinToString("\n"), 0)
            } catch (e: Exception) {
                refresh("Une erreur est survenue : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /** Renomme [file] (dans la visionneuse). Le type du fichier est gardé ; rien d'autre ne change. */
    fun renameFile(file: File, typed: String) {
        viewModelScope.launch {
            val (target, message) = withContext(Dispatchers.IO) { doRename(file, typed) }
            val viewer = _state.value as? UiState.Viewer ?: return@launch
            searchIndex = null
            val files = if (target == null) viewer.files else viewer.files.map { if (it.absolutePath == file.absolutePath) target else it }
            _state.value = viewer.copy(files = files, message = message)
        }
    }

    private fun doRename(file: File, typed: String): Pair<File?, String> {
        if (!file.isFile) return null to "Cette photo est introuvable."
        // Si la date de la photo n'est que dans son nom (ex. WhatsApp), on la garde dans le nouveau nom.
        val keepDate = DateChoice.fromFileName(file.name) != null && !hasExifDate(file)
        val name = RenameNames.build(typed, file.name, keepDate) ?: return null to "Le nom est vide : rien n'a changé."
        if (name == file.name) return null to "Le nom n'a pas changé."
        val dir = file.parentFile ?: return null to "Dossier introuvable."
        val target = RenameNames.unique(dir, name)
        if (!file.renameTo(target) || !target.isFile || file.exists()) return null to "Impossible de renommer cette photo."
        MediaScannerConnection.scanFile(getApplication(), arrayOf(file.absolutePath, target.absolutePath), null, null)
        updateJournalPath(file, target)
        val note = if (keepDate && target.name.contains(file.nameWithoutExtension, ignoreCase = true)) {
            "\nL'ancien nom (avec la date) est gardé à la suite, pour que la photo reste classée au bon jour."
        } else ""
        return target to "Renommée : ${target.name}$note"
    }

    private fun hasExifDate(file: File): Boolean = try {
        val exif = ExifInterface(file.absolutePath)
        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) != null || exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED) != null
    } catch (e: Exception) {
        false
    }

    /** Si la photo figure dans le journal du dernier rangement, on y met son nouveau chemin (pour que « Annuler » la retrouve). */
    private fun updateJournalPath(old: File, new: File) {
        if (!journal.exists()) return
        runCatching {
            val lines = journal.readLines().map { line ->
                val parts = line.split('\t')
                if (parts.size == 3 && parts[0] == "M" && parts[1] == old.absolutePath) "M\t${new.absolutePath}\t${parts[2]}" else line
            }
            journal.writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }

    // ---- Doublons ------------------------------------------------------------------------------

    /** Compare le contenu des fichiers de « Photos rangées » et montre les copies exactes, groupe par groupe. */
    fun findDuplicates() {
        val current = place ?: return
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Recherche des doublons…", 0, 0)
                dupGroups = withContext(Dispatchers.IO) {
                    DuplicateReview.find(Gallery.allMedia(current.outputDir), current.outputDir) { done, total ->
                        if (done % 20 == 0 || done == total) _state.value = UiState.Working("Comparaison des fichiers…", done, total)
                    }
                }
                showDupReview()
            } catch (e: Exception) {
                refresh("La recherche des doublons a échoué : ${e.message ?: e.javaClass.simpleName}", isError = true)
            }
        }
    }

    /** Les groupes trouvés, sans les fichiers qui ont depuis été mis de côté (et remis, si on annule). */
    private suspend fun showDupReview(message: String? = null, undoCount: Int = 0) {
        val current = place ?: return
        val groups = withContext(Dispatchers.IO) {
            dupGroups.mapNotNull { g ->
                val files = g.files.filter { it.isFile }
                if (files.size < 2) null
                else DupGroup(files, if (g.keep in files) g.keep else DuplicateReview.best(files, current.outputDir))
            }
        }
        _state.value = UiState.DupReview(groups, message, undoCount)
    }

    // ---- Recherche par visage ------------------------------------------------------------------
    // Tout se passe sur le téléphone. On choisit une photo, l'appli retrouve les photos où la même personne apparaît.

    private fun photosToScan(current: Place): List<File> = Gallery.allMedia(current.outputDir).filter { !PhotoFiles.isVideo(it) }

    fun openFaces() {
        val current = place ?: return
        viewModelScope.launch { showFaces(current) }
    }

    private suspend fun showFaces(current: Place, message: String? = null) {
        _state.value = UiState.Working("Ouverture…", 0, 0)
        val screen = withContext(Dispatchers.IO) {
            faceCache.load()
            val photos = photosToScan(current)
            var analysed = 0
            var faces = 0
            for (f in photos) {
                val e = faceCache.get(f) ?: continue
                analysed++
                faces += e.faces.size
            }
            UiState.Faces(analysed, photos.size, faces, message)
        }
        _state.value = screen
    }

    /** Regarde les photos pas encore vues (les autres sont retenues), puis revient à l'accueil des visages. */
    fun startFaceScan() {
        val current = place ?: return
        if (faceJob?.isActive == true) return
        faceJob = viewModelScope.launch {
            var engine: FaceEngine? = null
            try {
                _state.value = UiState.FaceScan(0, 0, 0)
                val todo = withContext(Dispatchers.IO) {
                    faceCache.load()
                    photosToScan(current).filter { faceCache.get(it) == null }
                }
                var faces = withContext(Dispatchers.IO) { photosToScan(current).sumOf { faceCache.get(it)?.faces?.size ?: 0 } }
                if (todo.isNotEmpty()) {
                    val e = withContext(Dispatchers.IO) { FaceEngine(getApplication()) }
                    engine = e
                    for ((i, file) in todo.withIndex()) {
                        ensureActive()
                        val found = withContext(Dispatchers.Default) {
                            val bitmap = Thumbs.loadUncached(file, 1280)
                            if (bitmap == null) emptyList() else try {
                                runCatching { e.analyze(bitmap) }.getOrDefault(emptyList())
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        withContext(Dispatchers.IO) { faceCache.put(file, found.map { FaceCache.Face(it.box, it.embedding) }) }
                        faces += found.size
                        if ((i + 1) % 3 == 0 || i + 1 == todo.size) _state.value = UiState.FaceScan(i + 1, todo.size, faces)
                    }
                }
                showFaces(current, if (todo.isEmpty()) "Rien de nouveau à regarder : toutes les photos ont déjà été analysées." else "Analyse terminée.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refresh("L'analyse des visages a échoué : ${e.message ?: e.javaClass.simpleName}", isError = true)
            } finally {
                engine?.close()
            }
        }
    }

    /** Arrête l'analyse : ce qui est déjà vu est retenu, on pourra reprendre. */
    fun stopFaceScan() {
        val current = place ?: return
        val job = faceJob
        viewModelScope.launch {
            job?.cancelAndJoin()
            showFaces(current, "Analyse arrêtée. Vous pourrez la reprendre : les photos déjà vues ne sont pas refaites.")
        }
    }

    /** La personne a choisi une photo (sélecteur de photos du téléphone) : on y cherche les visages. */
    fun searchFaceFromPhoto(uri: Uri) {
        val current = place ?: return
        viewModelScope.launch {
            var engine: FaceEngine? = null
            try {
                _state.value = UiState.Working("Recherche du visage…", 0, 0)
                val found = withContext(Dispatchers.Default) {
                    val bitmap = loadPicked(uri)
                    if (bitmap == null) null else {
                        val e = FaceEngine(getApplication())
                        engine = e
                        try {
                            e.analyze(bitmap, withCrops = true)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                when {
                    found == null -> showFaces(current, "Cette photo n'a pas pu être lue.")
                    found.isEmpty() -> showFaces(current, "Aucun visage trouvé sur cette photo. Essayez-en une autre, où le visage est bien visible.")
                    found.size == 1 -> startFaceSearch(current, found[0].embedding)
                    else -> {
                        pickedFaces = found
                        _state.value = UiState.FacePick(found.mapNotNull { it.crop })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refresh("La recherche par visage a échoué : ${e.message ?: e.javaClass.simpleName}", isError = true)
            } finally {
                engine?.close()
            }
        }
    }

    /** Plusieurs visages sur la photo : la personne a touché le n° [index]. */
    fun chooseFace(index: Int) {
        val current = place ?: return
        val face = pickedFaces.getOrNull(index) ?: return
        viewModelScope.launch { startFaceSearch(current, face.embedding) }
    }

    private suspend fun startFaceSearch(current: Place, embedding: FloatArray) {
        _state.value = UiState.Working("Recherche des photos…", 0, 0)
        faceQuery = embedding
        faceLevel = 1
        withContext(Dispatchers.IO) { faceCache.load() }
        showFaceResults()
    }

    fun setFaceLevel(level: Int) {
        faceLevel = level.coerceIn(0, FACE_LEVELS.size - 1)
        viewModelScope.launch { showFaceResults() }
    }

    private suspend fun showFaceResults(message: String? = null, undoCount: Int = 0) {
        val current = place ?: return
        val query = faceQuery
        if (query == null) {
            showFaces(current)
            return
        }
        val out = current.outputDir.absolutePath
        val files = withContext(Dispatchers.Default) {
            faceCache.search(query, FACE_LEVELS[faceLevel]) { isRanged(out, it) }.map { File(it.path) }
        }
        _state.value = UiState.FaceResults(files, faceLevel, message, undoCount)
    }

    /** Ouvre l'image choisie, réduite et dans le bon sens. */
    private fun loadPicked(uri: Uri): Bitmap? {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1280 && bounds.outHeight / (sample * 2) >= 1280) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val degrees = try {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // ---- Recherche automatique de personnes nues -----------------------------------------------
    // Le modèle tourne sur le téléphone. L'appli ne fait que PROPOSER : rien ne bouge sans l'accord de la personne.

    /** Les photos proposées : score assez haut, toujours là, dans « Photos rangées » (ni « À l'écart », ni corbeille). */
    private fun currentSuggestions(p: Place): List<File> {
        nudityCache.load()
        val out = p.outputDir.absolutePath
        return nudityCache.hits(NudityModel.THRESHOLD).map { File(it.path) }.filter { isRanged(out, it.absolutePath) }
    }

    /** Vrai pour une photo de « Photos rangées » (ni « À l'écart », ni corbeille ou autre dossier caché). */
    private fun isRanged(outputPath: String, path: String): Boolean =
        path.startsWith("$outputPath/") && !path.startsWith("$outputPath/${Place.ASIDE_DIR}/") && !path.startsWith("$outputPath/.")

    /** Regarde les photos pas encore vues (les autres sont retenues), puis montre les suggestions. */
    fun startNudityScan() {
        val current = place ?: return
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            var detector: NudityDetector? = null
            try {
                _state.value = UiState.NudityScan(0, 0, 0)
                val todo = withContext(Dispatchers.IO) {
                    nudityCache.load()
                    Gallery.allMedia(current.outputDir).filter { !PhotoFiles.isVideo(it) && nudityCache.get(it) == null }
                }
                var found = withContext(Dispatchers.IO) { currentSuggestions(current).size }
                if (todo.isNotEmpty()) {
                    val d = withContext(Dispatchers.IO) { NudityDetector(getApplication()) }
                    detector = d
                    for ((i, file) in todo.withIndex()) {
                        ensureActive()
                        val score = withContext(Dispatchers.Default) { runCatching { d.score(file) }.getOrNull() } ?: 0f
                        withContext(Dispatchers.IO) { nudityCache.put(file, score) }
                        if (score >= NudityModel.THRESHOLD) found++
                        if ((i + 1) % 5 == 0 || i + 1 == todo.size) _state.value = UiState.NudityScan(i + 1, todo.size, found)
                    }
                }
                showReview(if (todo.isEmpty()) "Rien de nouveau à regarder : tout a déjà été vu." else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                refresh("La recherche automatique a échoué : ${e.message ?: e.javaClass.simpleName}", isError = true)
            } finally {
                detector?.close()
            }
        }
    }

    /** Arrête la recherche en cours et montre ce qui a été trouvé jusque-là (on pourra reprendre sans tout refaire). */
    fun stopNudityScan() {
        val job = scanJob
        viewModelScope.launch {
            job?.cancelAndJoin()
            showReview("Recherche arrêtée. Vous pourrez la reprendre : les photos déjà vues ne sont pas refaites.")
        }
    }

    fun openReview() {
        if (place == null) return
        viewModelScope.launch { showReview() }
    }

    private suspend fun showReview(message: String? = null, undoCount: Int = 0) {
        val current = place ?: return
        val files = withContext(Dispatchers.IO) { currentSuggestions(current) }
        suggestionCount = files.size
        _state.value = UiState.Review(files, message, undoCount)
    }

    /** « Ce n'est pas ça » : ces photos ne sont plus proposées. Elles ne bougent pas. */
    fun dismissSuggestions(files: List<File>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { nudityCache.dismiss(files) }
            showReview("${countText(files.size)} retiré(s) de la liste. Elles n'ont pas bougé.")
        }
    }

    // ---- Mise à jour ---------------------------------------------------------------------------

    fun checkUpdate(currentVersion: String, onResult: () -> Unit) {
        viewModelScope.launch {
            updateMessage = "Recherche en cours…"
            onResult()
            val latest = withContext(Dispatchers.IO) { runCatching { UpdateChecker.parseLatest(UpdateChecker.fetch()) }.getOrNull() }
            when {
                latest == null -> {
                    updateMessage = "Impossible de vérifier (pas d'internet ?)."
                    updateUrl = null
                }
                UpdateChecker.isNewer(latest.version, currentVersion) -> {
                    updateMessage = "Nouvelle version disponible : ${latest.version}."
                    updateUrl = latest.apkUrl
                }
                else -> {
                    updateMessage = "Vous avez la dernière version."
                    updateUrl = null
                }
            }
            onResult()
        }
    }

    private companion object {
        val UNUSABLE = setOf(Verdict.UNKNOWN, Verdict.SCRAMBLED, Verdict.ZEROS, Verdict.EMPTY)
        const val FIRST_BATCH = 10

        /** Ressemblance minimale (cosinus) selon le niveau : sûr, normal, large. */
        val FACE_LEVELS = floatArrayOf(0.45f, 0.363f, 0.28f)
    }
}
