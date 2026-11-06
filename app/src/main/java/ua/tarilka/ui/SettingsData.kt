package ua.tarilka.ui

import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import ua.tarilka.R

/* The "Data" card of the options: backups, export and the PDF report. */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DataSection(vm: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val message by vm.dataMessage.collectAsState()
    val pending by vm.pendingImport.collectAsState()
    val encrypted by vm.encryptedImport.collectAsState()
    val wrong by vm.wrongPassword.collectAsState()
    val activity = androidx.compose.ui.platform.LocalContext.current as androidx.fragment.app.FragmentActivity
    val date = java.time.LocalDate.now().toString()
    var askPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    // Kept across rotation: the dialog, and what to write while the system "Save as" screen is open.
    var pdfDialog by rememberSaveable { mutableStateOf(false) }
    var pdfRequest by rememberSaveable { mutableStateOf<PdfRequest?>(null) }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val r = pdfRequest
        if (uri != null && r != null) vm.exportPdf(context, uri, r.from, r.to, r.parts, r.profile)
        pdfRequest = null
    }
    /** null = no dialog, false = clear the diary, true = delete all personal data */
    var clearing by remember { mutableStateOf<Boolean?>(null) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        // The password is kept in the view model: after rotation the UI state is new,
        // and an empty password here must never silently mean "save unencrypted".
        val pw = vm.exportPassword
        if (uri != null) {
            if (pw == null) vm.dataMessage.value = context.getString(R.string.export_retry) else vm.exportBackup(uri, pw)
        }
        vm.exportPassword = null
        password = ""
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readImport(uri)
    }
    Section(title = stringResource(R.string.data_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.data_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { Protection.confirm(activity) { askPassword = true } }) { Text(stringResource(R.string.export_backup)) }
                OutlinedButton(onClick = { importJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                    Text(stringResource(R.string.import_backup))
                }
                OutlinedButton(onClick = { Protection.confirm(activity) { exportCsv.launch("tarilka-diary-$date.csv") } }) { Text(stringResource(R.string.export_csv)) }
                OutlinedButton(onClick = { Protection.confirm(activity) { pdfDialog = true } }) { Text(stringResource(R.string.pdf_button)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { clearing = false }) {
                    Text(stringResource(R.string.clear_diary), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { clearing = true }) {
                    Text(stringResource(R.string.clear_all), color = MaterialTheme.colorScheme.error)
                }
            }
            // Success or failure, the words say it: plain text, never the theme's accent (red in some themes)
            message?.let {
                // Read out by TalkBack as soon as it appears
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
    if (pdfDialog) {
        PdfDialog(vm, onDismiss = { pdfDialog = false }) { req ->
            pdfRequest = req
            pdfDialog = false
            savePdf.launch(if (req.from == req.to) "tarilka-${req.from}.pdf" else "tarilka-${req.from}_${req.to}.pdf")
        }
    }
    clearing?.let { everything ->
        var alsoHc by remember { mutableStateOf(true) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { clearing = null },
            title = { Text(stringResource(if (everything) R.string.clear_all else R.string.clear_diary)) },
            text = {
                // Scrolls with a large font, so the buttons at the end stay reachable
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (everything) R.string.clear_all_text else R.string.clear_diary_text), style = MaterialTheme.typography.bodyMedium)
                    // Offered even if sync is off now: records written earlier may still be there.
                    if (vm.healthAvailable) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { alsoHc = !alsoHc }) {
                            androidx.compose.material3.Checkbox(checked = alsoHc, onCheckedChange = { alsoHc = it })
                            Text(stringResource(R.string.clear_hc), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    TextButton(onClick = { clearing = null; Protection.confirm(activity) { askPassword = true } }) { Text(stringResource(R.string.export_first)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { val e = everything; val hc = alsoHc && vm.healthAvailable; clearing = null; Protection.confirm(activity) { vm.clearData(e, hc) } }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { clearing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (askPassword) {
        var repeat by remember { mutableStateOf("") }
        // A password is the default; saving without one is a deliberate choice.
        var plain by remember { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askPassword = false },
            title = { Text(stringResource(R.string.export_backup)) },
            text = {
                // Scrolls with a large font, so the buttons at the end stay reachable
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.password_hint), style = MaterialTheme.typography.bodySmall)
                    // Cycle data is especially personal: say so where the choice is made.
                    if (vm.cycleEnabled.collectAsState().value) {
                        Text(stringResource(R.string.password_cycle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (!plain) {
                        PasswordField(stringResource(R.string.password), password) { password = it }
                        PasswordField(stringResource(R.string.password_repeat), repeat) { repeat = it }
                        if (password.isNotEmpty() && password.length < 8) {
                            Text(stringResource(R.string.password_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        } else if (password.isNotEmpty()) {
                            val strength = passwordStrength(password)
                            Text(
                                androidx.compose.ui.res.stringArrayResource(R.array.password_strength)[strength],
                                style = MaterialTheme.typography.bodySmall,
                                color = levelTextColor(listOf(ua.tarilka.data.Habits.Level.WATCH, ua.tarilka.data.Habits.Level.OK, ua.tarilka.data.Habits.Level.GOOD)[strength]),
                            )
                        }
                    } else {
                        Text(stringResource(R.string.export_plain_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    TextButton(onClick = { plain = !plain; password = ""; repeat = "" }) {
                        Text(stringResource(if (plain) R.string.export_use_password else R.string.export_without_password))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = plain || (password.length >= 8 && password == repeat), onClick = {
                    askPassword = false
                    vm.exportPassword = if (plain) "" else password
                    exportJson.launch("tarilka-$date.json")
                }) { Text(stringResource(if (plain) R.string.export_plain else R.string.export_encrypted)) }
            },
            dismissButton = { TextButton(onClick = { askPassword = false; password = "" }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    encrypted?.let {
        var pw by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.encryptedImport.value = null; vm.wrongPassword.value = false },
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                // Scrolls with a large font, so the buttons at the end stay reachable
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.password_enter), style = MaterialTheme.typography.bodySmall)
                    PasswordField(stringResource(R.string.password), pw) { pw = it }
                    if (wrong) Text(stringResource(R.string.password_wrong), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = pw.isNotEmpty(), onClick = { vm.unlockImport(pw) }) { Text(stringResource(R.string.unlock)) } },
            dismissButton = {
                TextButton(onClick = { vm.encryptedImport.value = null; vm.wrongPassword.value = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    // Hidden while "save the current data first" is open; the file waits in the view model.
    pending?.takeIf { !askPassword }?.let { (_, s) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.pendingImport.value = null },
            title = { Text(stringResource(R.string.import_title)) },
            text = {
                // Scrolls with a large font, so the buttons at the end stay reachable
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.import_confirm, s.diary, s.fluids, s.weights, s.foods))
                    TextButton(onClick = { Protection.confirm(activity) { askPassword = true } }) { Text(stringResource(R.string.import_save_first)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { Protection.confirm(activity) { vm.confirmImport() } }) {
                    Text(stringResource(R.string.import_replace), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { vm.pendingImport.value = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** PDF report: period, what to include (with ready-made sets), and whether to add the profile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PdfDialog(vm: MainViewModel, onDismiss: () -> Unit, onCreate: (PdfRequest) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val today = java.time.LocalDate.now()
    // 0 day, 1 week, 2 month, 3 own dates, 4 all time
    var span by rememberSaveable { mutableStateOf(2) }
    var day by rememberSaveable { mutableStateOf(today) }
    var from by rememberSaveable { mutableStateOf(today.minusDays(29)) }
    var to by rememberSaveable { mutableStateOf(today) }
    var parts by rememberSaveable { mutableStateOf(ReportPresets.DIETITIAN) }
    var profile by rememberSaveable { mutableStateOf(false) }
    var first by remember { mutableStateOf<java.time.LocalDate?>(null) }
    LaunchedEffect(Unit) { first = vm.firstEntryDay() }
    fun range(): Pair<java.time.LocalDate, java.time.LocalDate> = when (span) {
        0 -> day to day
        1 -> today.minusDays(6) to today
        2 -> today.minusDays(29) to today
        3 -> minOf(from, to) to maxOf(from, to)
        else -> (first ?: today) to today
    }
    fun pick(initial: java.time.LocalDate, onPicked: (java.time.LocalDate) -> Unit) {
        android.app.DatePickerDialog(context, { _, y, m, d -> onPicked(java.time.LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
            .apply { datePicker.maxDate = System.currentTimeMillis(); Protection.secure(this) }.show()
    }
    val noWeight by vm.hideWeight.collectAsState()
    val names = mapOf(
        ReportPart.SUMMARY to R.string.pdf_part_summary, ReportPart.FOOD to R.string.pdf_part_food,
        ReportPart.REASONS to R.string.pdf_part_reasons, ReportPart.FLUID to R.string.m_fluid,
        ReportPart.WEIGHT to R.string.m_weight, ReportPart.SLEEP to R.string.m_sleep,
        ReportPart.ACTIVITY to R.string.pdf_part_activity, ReportPart.BOWEL to R.string.m_bowel,
        ReportPart.CYCLE to R.string.cycle_title, ReportPart.NOTES to R.string.pdf_part_notes,
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_button)) },
        text = {
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.pdf_period_title), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(R.string.pdf_span_day, R.string.period_week, R.string.period_month, R.string.pdf_span_own, R.string.pdf_span_all)
                        .forEachIndexed { i, res -> FilterChip(span == i, { span = i }, { Text(stringResource(res)) }) }
                }
                when (span) {
                    0 -> TextButton(onClick = { pick(day) { day = it } }) { Text(shortDate(day.toEpochDay())) }
                    3 -> Row {
                        TextButton(onClick = { pick(from) { from = it } }) { Text(shortDate(from.toEpochDay())) }
                        Text("–", modifier = Modifier.align(Alignment.CenterVertically))
                        TextButton(onClick = { pick(to) { to = it } }) { Text(shortDate(to.toEpochDay())) }
                    }
                    else -> Unit
                }
                Text(stringResource(R.string.pdf_what), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(parts == ReportPresets.DIETITIAN, { parts = ReportPresets.DIETITIAN }, { Text(stringResource(R.string.pdf_preset_diet)) })
                    FilterChip(parts == ReportPresets.CYCLE, { parts = ReportPresets.CYCLE }, { Text(stringResource(R.string.pdf_preset_cycle)) })
                    FilterChip(parts == ReportPresets.ALL, { parts = ReportPresets.ALL }, { Text(stringResource(R.string.pdf_preset_all)) })
                }
                ReportPart.entries.filter { it != ReportPart.WEIGHT || !noWeight }.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { parts = if (p in parts) parts - p else parts + p },
                    ) {
                        androidx.compose.material3.Checkbox(checked = p in parts, onCheckedChange = { parts = if (it) parts + p else parts - p })
                        Text(stringResource(names.getValue(p)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { profile = !profile }) {
                    androidx.compose.material3.Checkbox(checked = profile, onCheckedChange = { profile = it })
                    Text(stringResource(R.string.pdf_with_profile), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.pdf_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = parts.isNotEmpty(), onClick = {
                val (a, b) = range()
                onCreate(PdfRequest(a, b, parts, profile))
            }) { Text(stringResource(R.string.pdf_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** What the PDF report should contain; survives rotation while the system "Save as" screen is open. */
data class PdfRequest(
    val from: java.time.LocalDate,
    val to: java.time.LocalDate,
    val parts: Set<ReportPart>,
    val profile: Boolean,
) : java.io.Serializable

/**
 * A rough guide to how hard a backup password is to guess offline: 0 weak, 1 fair, 2 strong.
 * Length matters most; several unrelated words are the easiest strong password to remember.
 */
internal fun passwordStrength(p: String): Int {
    val kinds = listOf(p.any { it.isLowerCase() }, p.any { it.isUpperCase() }, p.any { it.isDigit() }, p.any { !it.isLetterOrDigit() }).count { it }
    val words = p.trim().split(Regex("[\\s\\-_.]+")).count { it.length >= 3 }
    return when {
        p.length >= 16 || (words >= 4 && p.length >= 14) -> 2
        p.length >= 12 && kinds >= 3 -> 2
        p.length >= 10 && kinds >= 2 -> 1
        else -> 0
    }
}
