package fr.rangephotos.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.rangephotos.face.FaceCounter
import fr.rangephotos.logic.Hike
import fr.rangephotos.logic.HikeDetector
import fr.rangephotos.logic.HikeNamer
import fr.rangephotos.logic.Planner
import fr.rangephotos.logic.PlannedMove
import fr.rangephotos.model.PhotoInfo
import fr.rangephotos.organize.Organizer
import fr.rangephotos.scan.PhotoScanner
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UiState {
    data class Start(val hasUndo: Boolean, val message: String? = null) : UiState

    /** [total] = 0 signifie « durée inconnue » (barre de progression indéterminée). */
    data class Working(val label: String, val done: Int, val total: Int) : UiState

    data class Preview(
        val total: Int,
        val topLevel: List<Pair<String, Int>>,
        val hikes: List<Pair<String, Int>>,
    ) : UiState

    data class Done(val title: String, val details: String, val hasUndo: Boolean) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner = PhotoScanner(app)
    private val faces = FaceCounter(app)
    private val namer = HikeNamer(app)
    private val organizer = Organizer(app)
    private val journal = File(app.filesDir, "journal.tsv")

    private val _state = MutableStateFlow<UiState>(UiState.Start(hasUndo = hasUndo()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pendingRoot: Uri? = null
    private var pendingMoves: List<PlannedMove> = emptyList()

    private fun hasUndo() = journal.exists() && journal.length() > 0

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
        viewModelScope.launch {
            try {
                _state.value = UiState.Working("Recherche des photos…", 0, 0)
                val scanned = scanner.scan(root) { found ->
                    _state.value = UiState.Working("Recherche des photos… $found trouvées", 0, 0)
                }
                if (scanned.isEmpty()) {
                    _state.value = UiState.Start(hasUndo(), "Aucune photo trouvée dans ce dossier.")
                    return@launch
                }

                _state.value = UiState.Working("Recherche des randonnées…", 0, 0)
                val hikes = HikeDetector.detect(scanned)
                val hikeNames = nameHikes(hikes)

                val withFaces = detectFaces(scanned)

                val plan = Planner.plan(withFaces, hikes, hikeNames)
                pendingRoot = root
                pendingMoves = plan
                _state.value = buildPreview(plan, hikes, hikeNames)
            } catch (e: Exception) {
                _state.value = UiState.Start(hasUndo(), "Une erreur est survenue : ${e.message}")
            }
        }
    }

    private suspend fun nameHikes(hikes: List<Hike>): Map<Hike, String> {
        val used = HashSet<String>()
        val result = LinkedHashMap<Hike, String>()
        for (hike in hikes) {
            var name = Planner.sanitize(namer.name(hike))
            var n = 2
            val base = name
            while (!used.add(name)) name = "$base ($n)".also { n++ }
            result[hike] = name
        }
        return result
    }

    private suspend fun detectFaces(photos: List<PhotoInfo>): List<PhotoInfo> {
        val result = ArrayList<PhotoInfo>(photos.size)
        var done = 0
        for (chunk in photos.chunked(4)) {
            val counted = coroutineScope {
                chunk.map { photo ->
                    async { if (photo.isScreenshot) photo else photo.copy(faceCount = faces.count(photo.uri)) }
                }.awaitAll()
            }
            result += counted
            done += chunk.size
            _state.value = UiState.Working("Recherche des visages…", done, photos.size)
        }
        return result
    }

    private fun buildPreview(plan: List<PlannedMove>, hikes: List<Hike>, names: Map<Hike, String>): UiState.Preview {
        val topLevel = plan.groupingBy { it.folder.first() }.eachCount().toList().sortedByDescending { it.second }
        val hikeCounts = plan
            .filter { it.folder.first() == Planner.HIKES }
            .groupingBy { it.folder[1] }.eachCount()
        val hikeList = hikes.mapNotNull { names[it] }.map { it to (hikeCounts[it] ?: 0) }
        return UiState.Preview(plan.size, topLevel, hikeList)
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
                val failedText = if (result.failed > 0) "\n${result.failed} photo(s) n'ont pas pu être déplacées et sont restées en place." else ""
                _state.value = UiState.Done(
                    title = "Rangement terminé",
                    details = "${result.moved} photo(s) rangées dans le dossier « ${Organizer.OUTPUT_DIR} ».$failedText",
                    hasUndo = hasUndo(),
                )
            } catch (e: Exception) {
                _state.value = UiState.Start(hasUndo(), "Une erreur est survenue : ${e.message}")
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
                val failedText = if (result.failed > 0) "\n${result.failed} photo(s) n'ont pas pu être remises (déplacées ou supprimées entre-temps)." else ""
                _state.value = UiState.Done(
                    title = "Rangement annulé",
                    details = "${result.moved} photo(s) remises à leur place d'origine.$failedText",
                    hasUndo = hasUndo(),
                )
            } catch (e: Exception) {
                _state.value = UiState.Start(hasUndo(), "Une erreur est survenue : ${e.message}")
            }
        }
    }

    fun backToStart() {
        pendingMoves = emptyList()
        _state.value = UiState.Start(hasUndo())
    }

    override fun onCleared() {
        faces.close()
    }
}
