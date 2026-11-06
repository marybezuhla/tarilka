package ua.tarilka.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.tarilka.R
import ua.tarilka.data.Food
import ua.tarilka.ui.theme.TarilkaTheme

class MainActivity : AppCompatActivity() {
    private val app get() = application as ua.tarilka.TarilkaApp
    /** The fingerprint / PIN prompt opens by itself once per lock, not again after Cancel. */
    private var promptedGen = -1L

    private val healthRationale: Boolean
        get() = intent?.action in setOf("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE", "android.intent.action.VIEW_PERMISSION_USAGE")

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = app.prefs
        Protection.init(app)
        setContent {
            val look by prefs.look.collectAsState()
            val db by app.dbState.collectAsState()
            val dbBroken by app.dbBroken.collectAsState()
            val protectedOn by Protection.enabled.collectAsState()
            val noShots by prefs.noScreenshots.collectAsState()
            val profile by prefs.profile.collectAsState()
            val hideKcal by prefs.hideKcal.collectAsState()
            LaunchedEffect(protectedOn, noShots) { Protection.applyRecents(this@MainActivity) }
            TarilkaTheme(day = look.day, night = look.night) {
                // Opened by Health Connect to explain why Tarilka asks for access: only the explanation, no diary.
                if (healthRationale) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { finish() },
                        title = { androidx.compose.material3.Text(stringResource(R.string.hc_privacy_title)) },
                        text = { androidx.compose.material3.Text(stringResource(R.string.hc_privacy)) },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { finish() }) { androidx.compose.material3.Text(stringResource(R.string.close)) } },
                    )
                    return@TarilkaTheme
                }
                androidx.compose.runtime.CompositionLocalProvider(LocalShowKcal provides (profile.age >= 18 && !hideKcal), LocalTeen provides (profile.age < 18)) {
                if (dbBroken) {
                    DbBrokenScreen()
                } else if (db == null) {
                    // Locked (or being converted): the screens' view models go, they held the closed database.
                    LaunchedEffect(Unit) { viewModelStore.clear() }
                    LockScreen(onUnlock = { Protection.unlock(this) }, onStartFresh = { Protection.startFresh(this) })
                } else {
                    AppRoot()
                }
                }
            }
        }
    }

    // Every picker, "Save as" and the camera go through here: the diary stays open while they are on top.
    @Deprecated("Activity Result API uses this internally")
    override fun startActivityForResult(intent: android.content.Intent, requestCode: Int, options: Bundle?) {
        app.holdLock()
        try {
            super.startActivityForResult(intent, requestCode, options)
        } catch (e: RuntimeException) {
            app.releaseLock() // nothing opened: no reason to keep the diary open
            throw e
        }
    }

    @Deprecated("Activity Result API uses this internally")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        app.releaseLock()
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStart() {
        super.onStart()
        // Locked while away: the screens' view models hold the closed database. Drop them before they resume
        // and query it; the lock screen is drawn next.
        if (app.dbState.value == null) viewModelStore.clear()
    }

    override fun onResume() {
        super.onResume()
        Protection.applyRecents(this)
        val gen = app.lockGen.get()
        if (!healthRationale && app.prefs.appLock && app.dbState.value == null && promptedGen != gen && !Protection.invalid.value && !Protection.busy.value) {
            promptedGen = gen
            Protection.unlock(this)
        }
    }
}

/** Screens shown on top of the tabs. */
sealed interface Overlay {
    data class AddFood(val meal: Int) : Overlay
    data class Photo(val meal: Int) : Overlay
    /** Pick a food for the photo review: replace item [index], or add a new one when null. */
    data class PickForPhoto(val index: Int?) : Overlay
    /** Not a data class: each editor keeps its own form state while ingredients are picked. */
    class EditFood(food: Food?) : Overlay { val draft = FoodDraft(food) }
    class PickIngredient(val draft: FoodDraft) : Overlay
    data object History : Overlay
}

/** Keeps the screen stack (and half-filled forms) across rotation, theme and language changes. */
class NavViewModel : androidx.lifecycle.ViewModel() {
    val stack = mutableStateListOf<Overlay>()
}

@Composable
private fun AppRoot() {
    val vm: MainViewModel = viewModel()
    val ai: AiViewModel = viewModel()
    val nav: NavViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as ua.tarilka.TarilkaApp
    val introShown by app.prefs.introShownFlow.collectAsState()
    if (!introShown) IntroDialog { under18 -> if (app.prefs.setUnder18(under18)) app.prefs.introShown = true } else CrashDialog()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stack = nav.stack
    fun push(o: Overlay) { stack.add(o) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    // The camera may be in front long enough for Android to restart this app.
    // These launchers live at the root, so the result is still delivered and the photo screen comes back.
    var pendingMeal by rememberSaveable { mutableIntStateOf(-1) }
    fun showPhoto(meal: Int) { if (stack.lastOrNull() !is Overlay.Photo) push(Overlay.Photo(meal)) }
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { ok ->
        if (ok && pendingMeal >= 0) {
            showPhoto(pendingMeal)
            ai.analyze(ua.tarilka.ai.PhotoUtil.captureUri(context))
        } else if (!ai.photo.value.working) {
            // Cancelled, or no meal to put it in: the photo must not stay in the cache.
            ua.tarilka.ai.PhotoUtil.clear(context)
        }
    }
    val gallery = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && pendingMeal >= 0) {
            showPhoto(pendingMeal)
            ai.analyze(uri)
        }
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    when (val top = stack.lastOrNull()) {
        is Overlay.AddFood -> {
            // Stays open after each food, so several can be added in a row; "Done" goes back.
            val added = androidx.compose.runtime.saveable.rememberSaveable(top) { mutableStateOf(listOf<String>()) }
            AddFoodScreen(
            vm = vm,
            title = stringResource(R.string.add_to, mealName(top.meal)),
            onBack = ::pop,
            added = added.value,
            onDone = { vm.query.value = ""; pop() },
            onPicked = { food, grams ->
                vm.addFood(food, grams, top.meal)
                vm.query.value = ""
                added.value = added.value + food.name()
            },
            onCreateFood = { push(Overlay.EditFood(null)) },
            // Breakfast/lunch/dinner that already have a time keep it; only a new meal or a snack asks.
            mealTime = if (vm.mealTimes.collectAsState().value && (top.meal == 3 || vm.mealTimeOf(top.meal) == null)) vm.defaultTime(top.meal) else null,
            onPickedAt = { food, grams, time ->
                vm.addFood(food, grams, top.meal, time)
                vm.query.value = ""
                added.value = added.value + food.name()
            },
            onTextList = { text ->
                ai.analyzeText(text)
                vm.query.value = ""
                pop()
                push(Overlay.Photo(top.meal))
            },
            )
        }

        is Overlay.Photo -> PhotoScreen(
            vm = vm,
            ai = ai,
            initialMeal = top.meal,
            onBack = ::pop,
            onSetupModel = { stack.clear(); tab = 3 },
            onReplace = { i ->
                // For a food the database lacks, start the search with the name the AI gave.
                ai.photo.value.items.getOrNull(i)?.takeIf { it.food == null }?.let { vm.query.value = it.detected.nameEn }
                push(Overlay.PickForPhoto(i))
            },
            onAddItem = { push(Overlay.PickForPhoto(null)) },
            onCamera = { meal ->
                pendingMeal = meal
                // A phone without a camera app: say so instead of closing.
                runCatching { camera.launch(ua.tarilka.ai.PhotoUtil.captureUri(context)) }
                    .onFailure { android.widget.Toast.makeText(context, R.string.no_camera, android.widget.Toast.LENGTH_LONG).show() }
            },
            onGallery = { meal ->
                pendingMeal = meal
                gallery.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
        )

        is Overlay.PickForPhoto -> AddFoodScreen(
            vm = vm,
            title = stringResource(R.string.choose_food),
            onBack = ::pop,
            askGrams = false,
            onPicked = { food, _ ->
                if (top.index != null) ai.replaceFood(top.index, food) else ai.addManualItem(food)
                vm.query.value = ""
                pop()
            },
            onCreateFood = { push(Overlay.EditFood(null)) },
        )

        is Overlay.EditFood -> EditFoodScreen(
            draft = top.draft,
            vm = vm,
            onBack = ::pop,
            onSave = { vm.saveFood(it); pop() },
            onAddIngredient = { push(Overlay.PickIngredient(top.draft)) },
        )

        is Overlay.PickIngredient -> AddFoodScreen(
            vm = vm,
            title = stringResource(R.string.add_ingredient),
            onBack = ::pop,
            onPicked = { food, grams ->
                top.draft.items.add(ua.tarilka.data.RecipeItem(food, grams))
                vm.query.value = ""
                pop()
            },
            onCreateFood = { push(Overlay.EditFood(null)) },
        )

        Overlay.History -> HistoryScreen(
            vm = vm,
            onBack = ::pop,
            onOpen = { d -> vm.day.value = d; tab = 0; stack.clear() },
        )

        null -> Tabs(vm, ai, tab, onTab = { tab = it }, push = ::push)
    }
}

@Composable
private fun Tabs(vm: MainViewModel, ai: AiViewModel, tab: Int, onTab: (Int) -> Unit, push: (Overlay) -> Unit) {
    val pendingReason by vm.pendingReason.collectAsState()
    if (pendingReason != null) {
        ReasonDialog(onSave = vm::setReason, onSkip = vm::skipReason)
    }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val undoEvent by vm.undoEvent.collectAsState()
    val deletedText = undoEvent?.let { stringResource(R.string.deleted_n, it.second) }
    val undoText = stringResource(R.string.undo)
    LaunchedEffect(undoEvent) {
        val seq = undoEvent?.first ?: return@LaunchedEffect
        if (deletedText == null) return@LaunchedEffect
        try {
            val r = snackbar.showSnackbar(deletedText, actionLabel = undoText, duration = androidx.compose.material3.SnackbarDuration.Long)
            if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.undo()
        } finally {
            // Also when this screen is left mid-snackbar: an old undo must not come back later.
            vm.undoShown(seq)
        }
    }
    BoxWithConstraints {
        // At the bottom on a phone, at the side on a wide screen
        val wide = maxWidth >= RAIL_FROM
        Row(Modifier.fillMaxSize()) {
            if (wide) TabRail(tab, onTab)
            Scaffold(
                modifier = Modifier.weight(1f),
                // Beside the rail the left edge is already kept clear
                contentWindowInsets = if (wide) WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                else ScaffoldDefaults.contentWindowInsets,
                snackbarHost = { SnackbarHost(snackbar) },
                floatingActionButton = {
                    if (tab == 2) {
                        ExtendedFloatingActionButton(
                            onClick = { push(Overlay.EditFood(null)) },
                            icon = { Icon(Icons.Filled.Add, null) },
                            text = { Text(stringResource(R.string.new_food)) },
                        )
                    }
                },
                bottomBar = { if (!wide) TabBar(tab, onTab) },
            ) { pad ->
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        0 -> TodayScreen(vm, ai, pad, onAdd = { push(Overlay.AddFood(it)) }, onPhoto = {
                            // A typed list left from before must not stand in for the camera.
                            if (ai.photo.value.fromText) ai.resetPhoto()
                            push(Overlay.Photo(it))
                        }, onHistory = { push(Overlay.History) })
                        1 -> WeightScreen(vm, pad)
                        2 -> FoodsScreen(vm, pad, onEdit = { push(Overlay.EditFood(it)) })
                        else -> SettingsScreen(vm, ai, pad)
                    }
                    // Behind the clock and battery: a scrolled list does not run under them
                    Box(
                        Modifier.fillMaxWidth().height(pad.calculateTopPadding())
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
    }
}

private val TAB_NAMES = listOf(R.string.tab_today, R.string.tab_weight, R.string.tab_foods, R.string.tab_options)

@Composable
private fun TabBar(tab: Int, onTab: (Int) -> Unit) {
    NavigationBar {
        TAB_NAMES.forEachIndexed { i, name ->
            NavigationBarItem(tab == i, { onTab(i) }, { TabIcon(i) }, label = { TabLabel(stringResource(name)) })
        }
    }
}

/** The tabs at the side, in the middle of the height, clear of the status, navigation and camera bars. */
@Composable
private fun TabRail(tab: Int, onTab: (Int) -> Unit) {
    NavigationRail(
        Modifier.fillMaxHeight(),
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
    ) {
        Spacer(Modifier.weight(1f))
        TAB_NAMES.forEachIndexed { i, name ->
            NavigationRailItem(tab == i, { onTab(i) }, { TabIcon(i) }, label = { TabLabel(stringResource(name)) })
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun TabIcon(i: Int) = when (i) {
    0 -> Icon(Icons.Filled.Home, null)
    1 -> Icon(painterResource(R.drawable.ic_scale), null)
    2 -> Icon(Icons.AutoMirrored.Filled.List, null)
    else -> Icon(Icons.Filled.Settings, null)
}

/**
 * The diary did not open (an update of its structure failed). Nothing is deleted: the person can save the
 * database file and send feedback; the next version of the app can then open it.
 */
@Composable
private fun DbBrokenScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var saved by remember { mutableStateOf<Boolean?>(null) }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) saved = runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                java.util.zip.ZipOutputStream(out).use { zip ->
                    val main = context.getDatabasePath(ua.tarilka.data.AppDatabase.NAME)
                    // The -wal file holds the latest changes; both are needed to read the diary.
                    listOf(main, java.io.File(main.path + "-wal"), java.io.File(main.path + "-shm")).filter { it.exists() }.forEach { f ->
                        zip.putNextEntry(java.util.zip.ZipEntry(f.name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }.isSuccess
    }
    androidx.compose.material3.Scaffold { pad ->
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.padding(pad).padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            androidx.compose.material3.Text(stringResource(R.string.db_broken_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            androidx.compose.material3.Text(stringResource(R.string.db_broken_text))
            androidx.compose.material3.Button(onClick = { save.launch("tarilka-diary.zip") }) {
                androidx.compose.material3.Text(stringResource(R.string.db_broken_save))
            }
            saved?.let {
                androidx.compose.material3.Text(stringResource(if (it) R.string.db_broken_saved else R.string.db_broken_failed))
            }
        }
    }
}
