package fr.mesphotos.ui

import android.app.Application
import android.media.MediaScannerConnection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.mesphotos.gallery.FolderItem
import fr.mesphotos.gallery.Gallery
import fr.mesphotos.logic.Duplicates
import fr.mesphotos.logic.PlaceNamer
import fr.mesphotos.logic.PlannedMove
import fr.mesphotos.logic.Planner
import fr.mesphotos.model.PhotoInfo
import fr.mesphotos.organize.FolderCleanup
import fr.mesphotos.organize.Organizer
import fr.mesphotos.organize.Trash
import fr.mesphotos.organize.TrashEntry
import fr.mesphotos.organize.Verifier
import fr.mesphotos.scan.PhotoScanner
import fr.mesphotos.storage.Access
import fr.mesphotos.storage.PhotoFiles
import fr.mesphotos.storage.Place
import fr.mesphotos.storage.Places
import fr.mesphotos.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
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

/** Où vont les photos choisies : la corbeille (on peut effacer pour de bon) ou « À l'écart » (rangées à part, jamais effacées). */
enum class MoveKind { TRASH, ASIDE }

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
    ) : UiState

    /** [total] = 0 : durée inconnue. */
    data class Working(val label: String, val done: Int, val total: Int) : UiState

    data class Preview(
        val toMove: Int,
        val alreadyOk: Int,
        val undated: Int,
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
    ) : UiState

    /** La corbeille (ou « À l'écart ») : ce qui a été mis de côté, à remettre (ou, pour la corbeille, à supprimer pour de bon). */
    data class TrashView(val entries: List<TrashEntry>, val message: String? = null, val kind: MoveKind = MoveKind.TRASH) : UiState

    /** Recherche : les photos dont le chemin contient tous les mots de [query]. */
    data class Search(
        val query: String,
        val results: List<File>,
        val message: String? = null,
        val undoTrash: Int = 0,
    ) : UiState

    /** Visionneuse plein écran. */
    data class Viewer(val path: List<String>, val files: List<File>, val index: Int, val backToSearch: String? = null) : UiState
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

    // Mise à jour
    var updateMessage: String? = null
        private set
    var updateUrl: String? = null
        private set

    private val _state = MutableStateFlow<UiState>(UiState.Working("Démarrage…", 0, 0))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
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

    /** Où revenir après un déplacement : à la recherche si on en venait (liste ou visionneuse), sinon à la galerie. */
    private fun returnQuery(): String? = when (val s = _state.value) {
        is UiState.Search -> s.query
        is UiState.Viewer -> s.backToSearch
        else -> null
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
                val (unique, _) = Duplicates.keepOnePerPhoto(scanned)
                val (dayPlaces, townsUnavailable) = findDayPlaces(unique)
                plan = Planner.plan(unique, dayPlaces = dayPlaces)
                // Ce qui est déjà au bon endroit ne bouge pas.
                pendingMoves = plan.filter { it.photo.currentFolder != it.folder }
                val years = plan.groupingBy { it.folder.getOrNull(1) ?: "" }.eachCount()
                    .filterKeys { it != Planner.UNDATED }
                    .toList().sortedByDescending { it.first }
                _state.value = UiState.Preview(
                    toMove = pendingMoves.size,
                    alreadyOk = plan.size - pendingMoves.size,
                    undated = plan.count { it.photo.dateGuessed },
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
        _state.value = UiState.Browse(
            path, listing.folders, listing.files,
            message = message ?: if (empty && path.isEmpty()) "Rien à voir pour l'instant : rangez d'abord vos photos." else null,
            undoTrash = undoCount,
        )
    }

    fun browseInto(name: String) = browse(browsePath + name)

    /** Remonte d'un dossier ; depuis la racine, retourne à l'accueil. */
    fun browseUp() {
        if (browsePath.isEmpty()) backToStart() else browse(browsePath.dropLast(1))
    }

    fun openViewer(files: List<File>, index: Int) {
        _state.value = UiState.Viewer(browsePath, files, index, backToSearch = returnQuery())
    }

    fun closeViewer() {
        val query = (_state.value as? UiState.Viewer)?.backToSearch
        if (query != null) search(query) else browse(browsePath)
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
        val backQuery = returnQuery()
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
                if (backQuery != null) showSearch(backQuery, message, result.done) else showBrowse(base, message = message, undoCount = result.done)
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
        val backQuery = returnQuery()
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Remise en place…", 0, 0)
                val result = withContext(Dispatchers.IO) { stashOf(current, kind).restore(items) }
                lastMoved = emptyList()
                searchIndex = null
                withContext(Dispatchers.IO) { refreshStashCounts(current) }
                var text = "${countText(result.done)} remis à leur place."
                if (result.failed > 0) text += "\n${result.failed} fichier(s) n'ont pas pu être remis (ils sont toujours mis de côté)."
                if (backQuery != null) showSearch(backQuery, text, 0) else showBrowse(base, message = text)
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

    // ---- Recherche -----------------------------------------------------------------------------

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
        _state.value = UiState.Search(query, results, message, undoCount)
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
    }
}
