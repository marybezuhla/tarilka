package ua.tarilka

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Debug build only. Replaces the diary with the invented demo diary (assets/demo.json, written by
 * scripts/screenshots/make_demo.py), dated so that it ends today, then opens the app.
 */
class DemoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TarilkaApp
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        var json = assets.open("demo.json").bufferedReader(Charsets.UTF_8).readText()
        json = Regex("\"@T(\\d+) (\\d\\d):(\\d\\d)@\"").replace(json) { m ->
            val (d, h, mi) = m.destructured
            today.minusDays(d.toLong()).atTime(LocalTime.of(h.toInt(), mi.toInt())).atZone(zone).toInstant().toEpochMilli().toString()
        }
        json = Regex("@D(\\d+)@").replace(json) { m -> today.minusDays(m.groupValues[1].toLong()).toString() }
        kotlinx.coroutines.runBlocking { ua.tarilka.data.Backup.restore(app.db, app.prefs, json) }
        // The language for the screenshots, the same way the app's own language setting does it.
        intent.getStringExtra("lang")?.let {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(it))
        }
        app.prefs.introShown = true
        app.prefs.setSleepEnabled(true)
        app.prefs.setWorkoutsEnabled(true)
        startActivity(
            Intent(this, ua.tarilka.ui.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }
}
