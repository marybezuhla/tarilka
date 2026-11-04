package ua.tarilka.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ua.tarilka.TarilkaApp
import ua.tarilka.ai.DetectedFood
import ua.tarilka.ai.FoodMatcher
import ua.tarilka.ai.ModelStore
import ua.tarilka.ai.PhotoUtil
import ua.tarilka.data.DiaryEntry
import ua.tarilka.data.Food

data class ReviewItem(
    val detected: DetectedFood,
    val food: Food?,
    val grams: Double,
    val include: Boolean = true,
) {
    val kcal: Double get() = (food?.kcal ?: 0.0) * grams / 100
    val isEstimate: Boolean get() = food == null
}

data class PhotoUi(
    /** Small in-memory preview; the photo files are deleted right after analysis. */
    val preview: android.graphics.Bitmap? = null,
    val note: String = "",
    val working: Boolean = false,
    val analyzed: Boolean = false,
    val items: List<ReviewItem> = emptyList(),
    val error: String? = null,
    val seconds: Int = 0,
    /** Shown when nothing was recognized, to see what went wrong. */
    val raw: String = "",
    val backend: String = "",
    val loadSec: Int = 0,
    /** Typed in as a list, not a photo: no model needed to review it. */
    val fromText: Boolean = false,
    /** Personal correction applied to the AI's grams (1.0 = none). */
    val factor: Double = 1.0,
    val analyzeSec: Int = 0,
)

class AiViewModel(app: Application) : AndroidViewModel(app) {
    private val tapp = app as TarilkaApp

    /** Raw exception text (often English, technical) is never shown; only messages written for people. */
    private fun userText(t: Throwable, fallback: Int): String =
        (t as? ua.tarilka.ai.UserFacingException)?.message ?: getApplication<Application>().getString(fallback)
    private val models = tapp.models
    private val gemma = tapp.gemma

    private val _model = MutableStateFlow<ModelStore.State>(ModelStore.State.Missing)
    val model: StateFlow<ModelStore.State> = _model.asStateFlow()

    private val _importProgress = MutableStateFlow<Float?>(null)
    val importProgress: StateFlow<Float?> = _importProgress.asStateFlow()

    private val _photo = MutableStateFlow(PhotoUi())
    val photo: StateFlow<PhotoUi> = _photo.asStateFlow()

    init {
        refreshModel()
    }

    fun refreshModel() {
        _model.value = models.state()
    }

    /** Message after importing a model (e.g. the original file could not be deleted). */
    val importNote = MutableStateFlow<Int?>(null)

    fun deleteModel() = viewModelScope.launch {
        gemma.release()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { models.delete() }
        refreshModel()
    }

    fun import(uri: Uri, deleteOriginal: Boolean) = viewModelScope.launch {
        if (_importProgress.value != null) return@launch // one copy at a time
        _importProgress.value = 0f
        importNote.value = null
        gemma.release()
        runCatching { models.import(uri, deleteOriginal) { p -> _importProgress.value = p } }
            .onSuccess { r ->
                refreshModel()
                importNote.value = when (r.originalDeleted) {
                    true -> ua.tarilka.R.string.ai_original_deleted
                    false -> ua.tarilka.R.string.ai_original_kept
                    null -> null
                }
            }
            .onFailure { _model.value = ModelStore.State.Failed(userText(it, ua.tarilka.R.string.ai_import_failed)) }
        _importProgress.value = null
    }

    // ---- photo analysis ----

    fun warmUp() {
        if (_model.value is ModelStore.State.Ready) viewModelScope.launch { gemma.warmUp() }
    }

    fun setNote(text: String) = _photo.update { it.copy(note = text) }

    /**
     * One analysis at a time: a new one (or a reset) cancels the old, and an old one that still
     * finishes cannot overwrite the newer result.
     */
    private var analysis: kotlinx.coroutines.Job? = null
    private var generation = 0L
    private fun newGeneration(): Long { analysis?.cancel(); return ++generation }
    private fun update(gen: Long, f: (PhotoUi) -> PhotoUi) { if (gen == generation) _photo.update(f) }

    fun resetPhoto() {
        newGeneration()
        _photo.value = PhotoUi()
        PhotoUtil.clear(getApplication())
    }

    fun analyze(uri: Uri) {
        val gen = newGeneration()
        analysis = viewModelScope.launch {
            if (_importProgress.value != null) {
                update(gen) { it.copy(error = getApplication<Application>().getString(ua.tarilka.R.string.ai_busy)) }
                PhotoUtil.clear(getApplication())
                return@launch
            }
            update(gen) { it.copy(working = true, error = null, items = emptyList(), analyzed = false, seconds = 0) }
            val ticker = launch {
                while (isActive) { delay(1000); update(gen) { it.copy(seconds = it.seconds + 1) } }
            }
            try {
                val img = PhotoUtil.prepare(getApplication(), uri)
                update(gen) { it.copy(preview = PhotoUtil.thumbnail(img)) }
                val foods = tapp.db.foods().allOnce()
                val result = gemma.analyzePhoto(img, _photo.value.note, tapp.prefs.plateSize)
                val factor = tapp.prefs.gramsFactor()
                update(gen) {
                    it.copy(raw = result.raw.take(600), backend = result.backend, loadSec = result.loadSec, analyzeSec = result.analyzeSec, factor = factor)
                }
                val items = result.foods.map { d ->
                    val match = FoodMatcher.best(d.nameEn, d.nameUk, foods)
                    // Not in the database: off until the person picks the right food with "Replace".
                    // Adjusted to how this person usually corrects the AI's portions (rounded to 5 g).
                    val grams = (kotlin.math.round(d.grams * factor / 5) * 5).coerceAtLeast(5.0)
                    ReviewItem(detected = d, food = match?.food, grams = grams, include = match != null)
                }
                // Unknown foods go to the "Not found" list in Options, like empty searches.
                items.filter { it.food == null }.forEach { tapp.prefs.recordMissing(it.detected.nameEn) }
                update(gen) { it.copy(items = items, analyzed = true) }
            } catch (t: Throwable) {
                update(gen) { it.copy(error = userText(t, ua.tarilka.R.string.ai_error)) }
            } finally {
                // Nothing of the photo stays on disk (a cancelled old run must not delete a newer photo).
                if (gen == generation) PhotoUtil.clear(getApplication())
                ticker.cancel()
                update(gen) { it.copy(working = false) }
            }
        }
    }

    /**
     * A typed list, read by rules at once (no model needed). Foods that were not found are shown as such
     * and can be replaced by hand.
     */
    fun analyzeText(text: String) {
        val gen = newGeneration()
        analysis = viewModelScope.launch {
            _photo.value = PhotoUi(note = text, working = true, fromText = true)
            try {
                val foods = tapp.db.foods().allOnce()
                val items = ua.tarilka.data.TextMeal.parse(text) { FoodMatcher.best(it, it, foods)?.score ?: 0.0 }.map { item ->
                    val food = FoodMatcher.best(item.name, item.name, foods)?.food
                    val grams = when {
                        item.amount == null -> food?.portionG ?: 100.0
                        item.unit == ua.tarilka.data.TextMeal.Unit.PIECES -> item.amount * (food?.portionG ?: 100.0)
                        else -> item.amount
                    }.coerceIn(1.0, 3000.0) // "1000 шт" is a typo, not three tonnes
                    ReviewItem(DetectedFood(item.name, item.name, grams, "manual"), food, grams, include = food != null)
                }
                items.filter { it.food == null }.forEach { tapp.prefs.recordMissing(it.detected.nameEn) }
                update(gen) { it.copy(items = items, analyzed = true) }
            } catch (t: Throwable) {
                update(gen) { it.copy(error = userText(t, ua.tarilka.R.string.ai_error)) }
            } finally {
                update(gen) { it.copy(working = false) }
            }
        }
    }

    /** Plate size for the next photo (remembered): 0 small, 1 dinner, 2 large, 3 bowl. */
    var plate: Int
        get() = tapp.prefs.plateSize
        set(v) { tapp.prefs.plateSize = v }

    fun setGrams(i: Int, grams: Double) = updateItem(i) { it.copy(grams = grams.coerceIn(0.0, 5000.0)) }
    fun setInclude(i: Int, on: Boolean) = updateItem(i) { it.copy(include = on) }
    fun replaceFood(i: Int, food: Food) = updateItem(i) { it.copy(food = food, include = true) }

    fun addManualItem(food: Food) = _photo.update {
        val d = DetectedFood(food.nameEn, food.nameUk, food.portionG ?: 100.0, "manual")
        it.copy(items = it.items + ReviewItem(d, food, d.grams))
    }

    private fun updateItem(i: Int, f: (ReviewItem) -> ReviewItem) = _photo.update { ui ->
        ui.copy(items = ui.items.mapIndexed { idx, item -> if (idx == i) f(item) else item })
    }

    /** Only foods from the database are saved: the numbers never come from the model. */
    fun entriesFor(day: Long, meal: Int): List<DiaryEntry> {
        val chosen = _photo.value.items.filter { it.include && it.grams > 0 && it.food != null }
        // Learn from the corrections: final grams against what the model itself said.
        chosen.filter { it.detected.confidence != "manual" && it.detected.grams > 0 }.forEach { tapp.prefs.addGramsRatio(it.grams / it.detected.grams) }
        return chosen.map { DiaryEntry.of(it.food!!, it.grams, day, meal, fromAi = true) }
    }
}
