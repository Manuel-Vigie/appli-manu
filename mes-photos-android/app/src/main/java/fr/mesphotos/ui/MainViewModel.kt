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
import fr.mesphotos.logic.DupGroup
import fr.mesphotos.logic.DuplicateReview
import fr.mesphotos.logic.Duplicates
import fr.mesphotos.logic.PlaceNamer
import fr.mesphotos.logic.CaptureNames
import fr.mesphotos.logic.RenameNames
import fr.mesphotos.logic.PlannedMove
import fr.mesphotos.logic.Planner
import fr.mesphotos.logic.Shortcut
import fr.mesphotos.logic.Shortcuts
import fr.mesphotos.model.PhotoInfo
import fr.mesphotos.organize.FolderCleanup
import fr.mesphotos.organize.Organizer
import fr.mesphotos.organize.Trash
import fr.mesphotos.organize.TrashEntry
import fr.mesphotos.organize.Verifier
import fr.mesphotos.scan.DateChoice
import fr.mesphotos.scan.PhotoScanner
import fr.mesphotos.storage.Access
import fr.mesphotos.storage.PhotoFiles
import fr.mesphotos.storage.Place
import fr.mesphotos.storage.Places
import fr.mesphotos.update.UpdateChecker
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
enum class MoveKind { TRASH, ASIDE }

/** Un raccourci et sa photo (null si elle est introuvable : à la corbeille, à l'écart ou effacée). */
class ShortcutItem(val shortcut: Shortcut, val file: File?)

/** Une photo prête pour la recherche : le fichier et son chemin sans accents ni majuscules. */
class SearchEntry(val file: File, val text: String)

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
    data class Working(val label: String, val done: Int, val total: Int) : UiState

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
    private val shortcuts by lazy { Shortcuts(File(getApplication<Application>().filesDir, "raccourcis.tsv")).also { it.load() } }

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

    /** Idées de « ce que je photographie » : les noms des raccourcis déjà faits, puis « Immatriculation ». */
    fun labelSuggestions(): List<String> =
        (shortcuts.all().map { it.name } + "Immatriculation").distinctBy { Gallery.normalize(it) }.take(8)

    /** Retour de l'appareil photo : [ok] vrai si la photo a été prise. */
    fun photoTaken(file: File, ok: Boolean) {
        if (ok && file.isFile && file.length() > 0) {
            MediaScannerConnection.scanFile(getApplication(), arrayOf(file.absolutePath), null, null)
            refresh("Photo enregistrée sur la carte, dans « ${Place.CAPTURE_DIR} » (${file.name}). Touchez « Ranger » pour la classer par date.")
            return
        }
        // Photo annulée, ou appareil photo qui n'a pas écrit au bon endroit : on ne laisse pas de fichier vide.
        if (file.isFile && file.length() == 0L) file.delete()
        if (ok) {
            refresh(
                "L'appareil photo du téléphone n'a pas enregistré la photo à l'endroit prévu (« ${Place.CAPTURE_DIR} » sur la carte). Rien n'a été perdu ni déplacé.",
                isError = true,
            )
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
        val items = if (path.isEmpty()) withContext(Dispatchers.IO) { shortcutItems(shortcuts.all(), current) } else emptyList()
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

    /** Cherche [query] dans le chemin de chaque photo (dossiers année, mois, jour, ville + nom du fichier). */
    private suspend fun showSearch(query: String, message: String? = null, undoCount: Int = 0) {
        val current = place ?: return
        searchQuery = query
        val index = searchIndex ?: run {
            _state.value = UiState.Working("Préparation de la recherche…", 0, 0)
            withContext(Dispatchers.IO) {
                Gallery.allMedia(current.outputDir)
                    .map { SearchEntry(it, Gallery.normalize(it.toRelativeString(current.outputDir).replace('/', ' '))) }
                    .sortedByDescending { it.text } // les plus récentes d'abord
            }.also { searchIndex = it }
        }
        val terms = Gallery.terms(query)
        val results = if (terms.isEmpty()) emptyList() else withContext(Dispatchers.Default) {
            index.filter { Gallery.matches(it.text, terms) }.map { it.file }
        }
        val matchingShortcuts = withContext(Dispatchers.IO) { shortcutItems(if (terms.isEmpty()) shortcuts.all() else shortcuts.matching(terms), current) }
        _state.value = UiState.Search(query, results, message, undoCount, matchingShortcuts)
    }

    // ---- Raccourcis ----------------------------------------------------------------------------
    // Un raccourci = un nom + un classement (« Immatriculation » dans « Véhicule ») qui mène droit à une photo.
    // Rien n'est copié ni déplacé : la photo reste là où elle est.

    fun shortcutFor(file: File): Shortcut? = shortcuts.forFile(file)

    /** Les classements déjà utilisés, puis quelques idées. */
    fun shortcutCategories(): List<String> =
        (shortcuts.categories() + Shortcuts.SUGGESTIONS).distinctBy { Gallery.normalize(it) }

    fun saveShortcut(file: File, name: String, category: String) {
        val made = shortcuts.put(file, name, category)
        val viewer = _state.value as? UiState.Viewer ?: return
        val message = if (made == null) "Le nom est vide : rien n'a changé."
        else "Raccourci « ${made.name} » enregistré dans « ${made.category} ». Retrouvez-le avec la loupe de l'onglet Photos."
        _state.value = viewer.copy(message = message)
    }

    fun removeShortcut(shortcut: Shortcut) {
        shortcuts.remove(shortcut)
        val viewer = _state.value as? UiState.Viewer ?: return
        _state.value = viewer.copy(message = "Raccourci « ${shortcut.name} » retiré. La photo n'a pas bougé.")
    }

    /** Les raccourcis avec leur photo ; si elle a changé de place ou de nom, on la retrouve (même nom, même taille). */
    private fun shortcutItems(list: List<Shortcut>, current: Place): List<ShortcutItem> {
        if (list.isEmpty()) return emptyList()
        val candidates by lazy { Gallery.allMedia(current.outputDir) }
        return list.map { ShortcutItem(it, shortcuts.resolve(it) { candidates }) }
    }

    // ---- Renommer une photo --------------------------------------------------------------------

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
        shortcuts.renamed(file, target)
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
        const val FIRST_BATCH = 10

        /** Ressemblance minimale (cosinus) selon le niveau : sûr, normal, large. */
        val FACE_LEVELS = floatArrayOf(0.45f, 0.363f, 0.28f)
    }
}
