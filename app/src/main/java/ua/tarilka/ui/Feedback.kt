package ua.tarilka.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The app has no internet: feedback opens a new GitHub issue in the browser, already filled in.
 * The person sees and edits everything before sending; nothing from the diary is added.
 */
object Feedback {
    private const val NEW_ISSUE = "https://github.com/marybezuhla/tarilka/issues/new"

    /** Copies text marked as sensitive, so the keyboard's clipboard preview does not show it. */
    fun copyPrivate(context: Context, text: String) {
        val clip = android.content.ClipData.newPlainText("Tarilka", text)
        clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(clip)
    }

    fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    fun open(context: Context, title: String, body: String) {
        val uri = Uri.parse(NEW_ISSUE).buildUpon()
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", body.take(6000))
            .build()
        // No browser on the phone: the text is not lost, it goes to the clipboard and the person is told so
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
            copyPrivate(context, "$title\n\n$body")
            android.widget.Toast.makeText(context, ua.tarilka.R.string.feedback_no_browser, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
