package ua.tarilka.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import ua.tarilka.R
import ua.tarilka.ai.ModelStore
import ua.tarilka.data.Goal
import ua.tarilka.data.Profile
import ua.tarilka.data.Sex

@Composable
fun SettingsScreen(vm: MainViewModel, ai: AiViewModel, padding: PaddingValues) {
    val profile by vm.profile.collectAsState()
    val targets by vm.targets.collectAsState()
    LaunchedEffect(Unit) { ai.refreshModel() }

    // On a tablet: about you on the left, the app and its data on the right
    CardColumns(
        padding,
        header = { Text(stringResource(R.string.tab_options), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp).semantics { heading() }) },
        main = {
            card("profile") { ProfileSection(profile, hideKcal = vm.hideKcal.collectAsState().value, onSave = vm::saveProfile) }
            card("targets") {
                Section(title = stringResource(R.string.your_targets)) {
                    Text(
                        if (targets.showKcal) {
                            stringResource(
                                R.string.targets_text,
                                targets.kcal.f0(), targets.protein.f0(), targets.fat.f0(), targets.carbs.f0(), targets.fiber.f0(),
                            )
                        } else {
                            stringResource(if (targets.teen) R.string.targets_text_teen else R.string.targets_text_hidden, targets.protein.f0(), targets.fiber.f0())
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (targets.customRaised && targets.showKcal) {
                        Text(stringResource(R.string.custom_raised), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    targets.limit?.let { l ->
                        Text(stringResource(limitText(l, teen = targets.teen, noKcal = !targets.showKcal)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.water_target, (targets.waterMl / 1000).f1()), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.water_own_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (profile.isSet) BmiLine(targets.bmi, profile.age, profile.sex == ua.tarilka.data.Sex.FEMALE && profile.pregnant)
                }
            }
            card("activity") { ActivitySection(vm) }
            card("self") { SelfSection(vm) }
            card("reminders") { RemindersSection(vm) }
            if (ua.tarilka.ai.AiRuntime.AVAILABLE) card("model") { ModelSection(ai) }
            card("missing") { MissingSection(vm) }
        },
        side = {
            card("data") { DataSection(vm) }
            card("protection") { ProtectionSection() }
            card("appearance") { AppearanceSection(vm) }
            card("language") { LanguageSection() }
            card("about") {
                Section(title = stringResource(R.string.about)) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val title = stringResource(R.string.feedback_title)
                    val body = stringResource(R.string.feedback_body, Feedback.version(context))
                    Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.about_licences), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { Feedback.open(context, title, body) }) { Text(stringResource(R.string.feedback)) }
                    Text(stringResource(R.string.feedback_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // The author's letter and how to support Ukraine, as in the author's other apps
            card("letter") { AuthorLetter() }
            card("support") {
                val context = androidx.compose.ui.platform.LocalContext.current
                SupportUkraine(open = { url ->
                    // In the browser; a phone without one simply opens nothing
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                })
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileSection(profile: Profile, hideKcal: Boolean = false, onSave: (Profile) -> Unit) {
    var sex by rememberSaveable(profile) { mutableStateOf(profile.sex) }
    var age by rememberSaveable(profile) { mutableStateOf(profile.age.toString()) }
    var height by rememberSaveable(profile) { mutableStateOf(profile.heightCm.toString()) }
    var weight by rememberSaveable(profile) { mutableStateOf(profile.weightKg.f1()) }
    var activity by rememberSaveable(profile) { mutableStateOf(profile.activity) }
    var goal by rememberSaveable(profile) { mutableStateOf(profile.goal) }
    var custom by rememberSaveable(profile) { mutableStateOf(profile.customKcal?.toString() ?: "") }
    var pregnant by rememberSaveable(profile) { mutableStateOf(profile.pregnant) }
    var breastfeeding by rememberSaveable(profile) { mutableStateOf(profile.breastfeeding) }

    val ageN = age.toNumber()?.toInt()
    val heightN = height.toNumber()?.toInt()
    val weightN = weight.toNumber()
    val valid = ageN != null && ageN in 14..100 &&
        heightN != null && heightN in 120..230 &&
        weightN != null && weightN in 30.0..300.0

    Section(title = stringResource(R.string.profile)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(sex == Sex.FEMALE, { sex = Sex.FEMALE }, { Text(stringResource(R.string.female)) })
                FilterChip(sex == Sex.MALE, { sex = Sex.MALE }, { Text(stringResource(R.string.male)) })
            }
            if (sex == Sex.FEMALE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { pregnant = !pregnant },
                ) {
                    androidx.compose.material3.Switch(checked = pregnant, onCheckedChange = { pregnant = it })
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.pregnant), style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { breastfeeding = !breastfeeding },
                ) {
                    androidx.compose.material3.Switch(checked = breastfeeding, onCheckedChange = { breastfeeding = it })
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.breastfeeding), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(stringResource(R.string.age), age, { age = it }, Modifier.weight(1f))
                Field(stringResource(R.string.height_cm), height, { height = it }, Modifier.weight(1f))
                Field(stringResource(R.string.weight_kg), weight, { weight = it }, Modifier.weight(1f))
            }
            Text(stringResource(R.string.activity), style = MaterialTheme.typography.labelLarge)
            val labels = listOf(
                R.string.activity_0, R.string.activity_1, R.string.activity_2, R.string.activity_3, R.string.activity_4,
            )
            labels.forEachIndexed { i, res ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activity = i },
                ) {
                    RadioButton(selected = activity == i, onClick = { activity = i })
                    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(stringResource(R.string.goal), style = MaterialTheme.typography.labelLarge)
            // Weight loss is not offered in pregnancy and breastfeeding, under 18 or with a BMI under 18.5.
            val limit = if (weightN != null && heightN != null && heightN > 0 && ageN != null) {
                ua.tarilka.data.Nutrition.limitFor(sex, pregnant || breastfeeding, ageN, ua.tarilka.data.Nutrition.bmi(weightN, heightN))
            } else null
            // Shown and saved as "Maintain" while a limit applies; the choice itself is kept (half-typed values pass).
            val shown = if (limit != null && goal == Goal.LOSE) Goal.MAINTAIN else goal
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(shown == Goal.LOSE, { goal = Goal.LOSE }, { Text(stringResource(R.string.goal_lose)) }, enabled = limit == null)
                FilterChip(shown == Goal.MAINTAIN, { goal = Goal.MAINTAIN }, { Text(stringResource(R.string.goal_maintain)) })
                FilterChip(goal == Goal.GAIN, { goal = Goal.GAIN }, { Text(stringResource(R.string.goal_gain)) })
            }
            limit?.let { Text(stringResource(limitText(it, teen = (ageN ?: 18) < 18, noKcal = hideKcal)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // Under 18 there is no calorie target, so no own one either; nor when calories are hidden.
            if ((ageN == null || ageN >= 18) && !hideKcal) {
                Field(stringResource(R.string.custom_kcal), custom, { custom = it })
                Text(stringResource(R.string.custom_kcal_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        Profile(
                            sex = sex, age = ageN!!, heightCm = heightN!!, weightKg = weightN!!,
                            activity = activity, goal = if (limit != null && goal == Goal.LOSE) Goal.MAINTAIN else goal,
                            customKcal = custom.toNumber()?.toInt()?.takeIf { it in 800..6000 && (ageN ?: 0) >= 18 },
                            pregnant = sex == Sex.FEMALE && pregnant,
                            breastfeeding = sex == Sex.FEMALE && breastfeeding,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

/** The phone's memory in bytes, as Android reports it. */
private fun totalMemory(context: android.content.Context): Long {
    val info = android.app.ActivityManager.MemoryInfo()
    (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(info)
    return info.totalMem
}

@Composable
private fun ModelSection(ai: AiViewModel) {
    val state by ai.model.collectAsState()
    val importing by ai.importProgress.collectAsState()
    val note by ai.importNote.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val enoughMemory = remember { ModelStore.enoughMemory(totalMemory(context)) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteOriginal by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ai.import(uri, deleteOriginal)
    }

    Section(title = stringResource(R.string.ai_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ai_explain), style = MaterialTheme.typography.bodyMedium)
            note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val s = state
            when {
                importing != null -> {
                    LinearProgressIndicator(progress = { importing ?: 0f }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.ai_importing), style = MaterialTheme.typography.bodySmall)
                }
                s is ModelStore.State.Ready -> {
                    Text(stringResource(R.string.ai_ready, s.sizeMb), style = MaterialTheme.typography.bodyMedium, color = levelTextColor(ua.tarilka.data.Habits.Level.GOOD))
                    if (confirmDelete) {
                        Row {
                            TextButton(onClick = { ai.deleteModel(); confirmDelete = false }) {
                                Text(stringResource(R.string.ai_delete_confirm), color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
                        }
                    } else {
                        OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.ai_delete)) }
                    }
                }
                // A phone with too little memory: say so instead of offering a 2.6 GB download that cannot work
                !enoughMemory -> {
                    Text(stringResource(R.string.ai_low_memory), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    if (s is ModelStore.State.Failed) {
                        Text(s.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(stringResource(R.string.ai_steps, ModelStore.E2B_FILE), style = MaterialTheme.typography.bodySmall)
                    fun open(url: String) = runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                    }
                    // A direct link to the one file that fits: the page lists many chip-specific variants.
                    Button(onClick = { open(ModelStore.E2B_DOWNLOAD) }) { Text(stringResource(R.string.ai_download)) }
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.ai_import)) }
                    TextButton(onClick = { open(ModelStore.E2B_PAGE) }) { Text(stringResource(R.string.ai_open_page)) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { deleteOriginal = !deleteOriginal },
                    ) {
                        androidx.compose.material3.Checkbox(checked = deleteOriginal, onCheckedChange = { deleteOriginal = it })
                        Text(stringResource(R.string.ai_delete_original), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSection() {
    var current by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags()) }
    fun set(tag: String) {
        current = tag
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }
    Section(title = stringResource(R.string.language)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(current.isEmpty(), { set("") }, { Text(stringResource(R.string.lang_system)) })
            FilterChip(current.startsWith("uk"), { set("uk") }, { Text("Українська") })
            FilterChip(current.startsWith("en"), { set("en") }, { Text("English") })
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ActivitySection(vm: MainViewModel) {
    val stepsOn by vm.stepsEnabled.collectAsState()
    val syncOn by vm.healthSync.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var hcAvailable by remember { mutableStateOf(vm.healthAvailable) }
    var hcInstallable by remember { mutableStateOf(vm.healthInstallable) }
    var hcNote by rememberSaveable { mutableStateOf<Int?>(null) }
    var syncAsked by rememberSaveable { mutableStateOf(false) }
    // Checked again on every return: Health Connect may have been installed, or its permissions taken back.
    var resumes by remember { mutableStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(syncOn, resumes) {
        hcAvailable = vm.healthAvailable
        hcInstallable = vm.healthInstallable
        if (syncOn && hcAvailable) {
            val g = vm.healthWriteGranted()
            hcNote = when {
                g == 0 -> R.string.hc_none_granted
                g < ua.tarilka.health.HealthSync.WRITE.size -> R.string.hc_some_granted
                else -> null
            }
        } else if (!syncOn) {
            hcNote = null
        }
    }
    val sensorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshSteps()
    }
    // One system dialog at a time: Health Connect first, then the step sensor.
    val hcLauncher = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        // Sync is on when at least one kind may be written; each kind is written only with its own permission.
        val writes = granted.intersect(ua.tarilka.health.HealthSync.WRITE)
        if (syncAsked && writes.isNotEmpty()) vm.setHealthSync(true)
        hcNote = when {
            !syncAsked -> null
            writes.isEmpty() -> R.string.hc_none_granted
            writes.size < ua.tarilka.health.HealthSync.WRITE.size -> R.string.hc_some_granted
            else -> null
        }
        syncAsked = false
        if (vm.stepsEnabled.value && vm.stepSensorPresent) {
            sensorLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            vm.refreshSteps()
        }
    }

    Section(title = stringResource(R.string.activity_section)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SwitchRow(stringResource(R.string.show_steps), stringResource(R.string.show_steps_hint), stepsOn) { on ->
                vm.setStepsEnabled(on)
                if (on) {
                    when {
                        hcAvailable -> hcLauncher.launch(setOf(ua.tarilka.health.HealthSync.READ_STEPS))
                        vm.stepSensorPresent -> sensorLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }
            }
            if (hcAvailable) {
                SwitchRow(stringResource(R.string.hc_sync), stringResource(R.string.hc_sync_hint), syncOn) { on ->
                    if (on) { syncAsked = true; hcLauncher.launch(ua.tarilka.health.HealthSync.WRITE + ua.tarilka.health.HealthSync.READ_STEPS) }
                    else { vm.setHealthSync(false); hcNote = null }
                }
                hcNote?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            } else {
                Text(stringResource(if (hcInstallable) R.string.hc_not_installed else R.string.hc_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hcInstallable) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"))
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }) { Text(stringResource(R.string.hc_install)) }
                }
            }
        }
    }
}

/** Optional reminders; each one asks for the notification permission only when switched on. */
@Composable
private fun RemindersSection(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = (context.applicationContext as ua.tarilka.TarilkaApp).prefs
    var meals by remember { mutableStateOf((0..2).map { prefs.mealReminder(it) }) }
    var water by remember { mutableStateOf(prefs.waterReminder) }
    var sleep by remember { mutableStateOf(prefs.sleepReminder) }
    var weekly by remember { mutableStateOf(prefs.weeklyReminder) }
    var denied by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pending?.invoke() else denied = true
        pending = null
    }
    fun apply() = ua.tarilka.notify.Reminders.schedule(context)
    /** Runs [turnOn] once notifications are allowed. */
    fun whenAllowed(turnOn: () -> Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) { turnOn(); denied = false } else { pending = turnOn; permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
    fun pickTime(initial: java.time.LocalTime, onPicked: (java.time.LocalTime) -> Unit) {
        android.app.TimePickerDialog(context, { _, h, m -> onPicked(java.time.LocalTime.of(h, m)) }, initial.hour, initial.minute, true).apply { Protection.secure(this) }.show()
    }
    val defaults = listOf(java.time.LocalTime.of(9, 0), java.time.LocalTime.of(14, 0), java.time.LocalTime.of(19, 0))

    Section(title = stringResource(R.string.reminders_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.reminders_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (meal in 0..2) {
                val t = meals[meal]
                fun set(v: java.time.LocalTime?) {
                    prefs.setMealReminder(meal, v)
                    meals = meals.toMutableList().also { it[meal] = v }
                    apply()
                }
                TimeSwitchRow(mealName(meal), t, onTime = { pickTime(t ?: defaults[meal], ::set) }) { on ->
                    if (on) whenAllowed { set(defaults[meal]) } else set(null)
                }
            }
            SwitchRow(stringResource(R.string.remind_water_title), stringResource(R.string.remind_water_hint), water) { on ->
                fun set(v: Boolean) { prefs.waterReminder = v; water = v; apply() }
                if (on) whenAllowed { set(true) } else set(false)
            }
            TimeSwitchRow(stringResource(R.string.remind_sleep_title), sleep, onTime = { pickTime(sleep ?: java.time.LocalTime.of(22, 30)) { prefs.sleepReminder = it; sleep = it; apply() } }) { on ->
                fun set(v: java.time.LocalTime?) { prefs.sleepReminder = v; sleep = v; apply() }
                if (on) whenAllowed { set(java.time.LocalTime.of(22, 30)) } else set(null)
            }
            // Cycle reminders: shown only when cycle tracking is on.
            if (prefs.cycleEnabled.collectAsState().value) {
                var before by remember { mutableStateOf(prefs.cycleRemindDays) }
                var late by remember { mutableStateOf(prefs.cycleLateRemind) }
                Text(stringResource(R.string.remind_cycle_title), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-1, 1, 2, 3).forEach { n ->
                        FilterChip(
                            selected = before == n,
                            onClick = {
                                fun set() { prefs.cycleRemindDays = n; before = n; vm.syncCycleNext(); apply() }
                                if (n > 0) whenAllowed(::set) else set()
                            },
                            label = { Text(if (n < 0) stringResource(R.string.off) else stringResource(R.string.days_before, n)) },
                        )
                    }
                }
                SwitchRow(stringResource(R.string.remind_cycle_late_title), stringResource(R.string.remind_cycle_late_hint), late) { on ->
                    fun set(v: Boolean) { prefs.cycleLateRemind = v; late = v; vm.syncCycleNext(); apply() }
                    if (on) whenAllowed { set(true) } else set(false)
                }
            }
            SwitchRow(stringResource(R.string.remind_weekly_title), stringResource(R.string.remind_weekly_hint), weekly) { on ->
                fun set(v: Boolean) { prefs.weeklyReminder = v; weekly = v; apply() }
                if (on) whenAllowed { set(true) } else set(false)
            }
            if (denied) Text(stringResource(R.string.notif_denied), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** A reminder with a time: tap the time to change it. */
@Composable
private fun TimeSwitchRow(title: String, time: java.time.LocalTime?, onTime: () -> Unit, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (time != null) TextButton(onClick = onTime) { Text("%02d:%02d".format(time.hour, time.minute)) }
        androidx.compose.material3.Switch(checked = time != null, onCheckedChange = onChange)
    }
}

/** Optional self-observation: sleep and "why I eat". Both off by default. */
@Composable
private fun SelfSection(vm: MainViewModel) {
    val sleepOn by vm.sleepEnabled.collectAsState()
    val reasonsOn by vm.reasonsEnabled.collectAsState()
    val sleepLauncher = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { vm.refreshSleep() }
    Section(title = stringResource(R.string.self_section)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SwitchRow(
                stringResource(R.string.sleep_title),
                stringResource(if (vm.healthAvailable) R.string.sleep_hint_hc else R.string.sleep_hint),
                sleepOn,
            ) { on ->
                vm.setSleepEnabled(on)
                // Health Connect is optional: without the permission sleep is simply typed in.
                if (on && vm.healthAvailable) sleepLauncher.launch(setOf(ua.tarilka.health.HealthSync.READ_SLEEP))
            }
            SwitchRow(stringResource(R.string.reasons_title), stringResource(R.string.reasons_hint), reasonsOn) { vm.setReasonsEnabled(it) }
            val timesOn by vm.mealTimes.collectAsState()
            SwitchRow(stringResource(R.string.meal_times_title), stringResource(R.string.meal_times_hint), timesOn) { vm.setMealTimes(it) }
            val workoutsOn by vm.workoutsEnabled.collectAsState()
            SwitchRow(stringResource(R.string.workouts_title), stringResource(R.string.workouts_hint), workoutsOn) { vm.setWorkoutsEnabled(it) }
            val cycleOn by vm.cycleEnabled.collectAsState()
            val cycleLauncher = rememberLauncherForActivityResult(
                androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
            ) { vm.refreshCycle() }
            SwitchRow(stringResource(R.string.cycle_title), stringResource(R.string.cycle_hint), cycleOn) { on ->
                vm.setCycleEnabled(on)
                // Optional: read periods another app wrote to Health Connect.
                if (on && vm.healthAvailable) cycleLauncher.launch(setOf(ua.tarilka.health.HealthSync.READ_PERIODS))
            }
            val bowelOn by vm.bowelEnabled.collectAsState()
            SwitchRow(stringResource(R.string.bowel_short), stringResource(R.string.bowel_hint), bowelOn) { vm.setBowelEnabled(it) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // One switch for TalkBack: the whole row, read with its title and state
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(vm: MainViewModel) {
    val look by vm.look.collectAsState()
    Section(title = stringResource(R.string.appearance)) {
        ua.tarilka.ui.theme.LookSettings(look, vm::setLook)
        Spacer(Modifier.height(6.dp))
        // Under 18 calories are hidden anyway.
        if (vm.profile.collectAsState().value.age >= 18) {
            val hide by vm.hideKcal.collectAsState()
            SwitchRow(stringResource(R.string.hide_kcal), stringResource(R.string.hide_kcal_hint), hide, vm::setHideKcal)
        }
    }
}

/** Foods people searched for and didn't find; shown only when there are some. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingSection(vm: MainViewModel) {
    val missing by vm.missing.collectAsState()
    val text = missing.entries.sortedByDescending { it.value }.joinToString("\n") { "${it.key} (${it.value})" }
    Section(title = stringResource(R.string.missing_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.missing_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (missing.isEmpty()) {
                Text(stringResource(R.string.missing_empty), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
            val context = androidx.compose.ui.platform.LocalContext.current
            val issueTitle = stringResource(R.string.missing_issue_title)
            val issueBody = stringResource(R.string.missing_issue_body, Feedback.version(context), text)
            androidx.compose.foundation.layout.FlowRow {
                TextButton(onClick = { Feedback.open(context, issueTitle, issueBody) }) { Text(stringResource(R.string.send_list)) }
                TextButton(onClick = { Feedback.copyPrivate(context, text) }) {
                    Text(stringResource(R.string.copy_list))
                }
                TextButton(onClick = vm::clearMissing) { Text(stringResource(R.string.clear)) }
            }
        }
    }
}

/** Under 18, or with calories hidden, the pregnancy text has no calorie numbers either. */
fun limitText(l: ua.tarilka.data.Limit, teen: Boolean = false, noKcal: Boolean = false): Int = when (l) {
    ua.tarilka.data.Limit.PREGNANT -> when {
        teen -> R.string.limit_pregnant_teen
        noKcal -> R.string.limit_pregnant_nokcal
        else -> R.string.limit_pregnant
    }
    ua.tarilka.data.Limit.TEEN -> R.string.limit_teen
    ua.tarilka.data.Limit.UNDERWEIGHT -> R.string.limit_underweight
}

/** Optional app protection: encrypted diary, opened with fingerprint or the phone PIN. */
@Composable
private fun ProtectionSection() {
    val activity = androidx.compose.ui.platform.LocalContext.current as androidx.fragment.app.FragmentActivity
    val on by Protection.enabled.collectAsState()
    val msg by Protection.message.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Section(title = stringResource(R.string.protect_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { confirm = true }) {
                Text(stringResource(R.string.protect_switch), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = on, onCheckedChange = { confirm = true })
            }
            Text(stringResource(R.string.protect_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The one way to lose the diary: always in sight while protection is on.
            if (on) {
                Text(
                    "⚠ " + stringResource(R.string.protect_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            }
            msg?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            val prefs = (activity.application as ua.tarilka.TarilkaApp).prefs
            val noShots by prefs.noScreenshots.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { prefs.setNoScreenshots(!noShots) }) {
                Text(stringResource(R.string.no_screenshots), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = noShots, onCheckedChange = { prefs.setNoScreenshots(it) })
            }
            Text(stringResource(R.string.no_screenshots_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(if (on) R.string.protect_off_title else R.string.protect_on_title)) },
            text = { Text(stringResource(if (on) R.string.protect_off_text else R.string.protect_on_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    Protection.message.value = null
                    if (on) Protection.disable(activity) else Protection.enable(activity)
                }) { Text(stringResource(if (on) R.string.protect_off_confirm else R.string.protect_on_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
