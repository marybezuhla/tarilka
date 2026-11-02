package ua.tarilka.ai

import android.content.Context

/**
 * The F-Droid build has no AI runtime: no prebuilt native AI libraries, a smaller app.
 * Everything else works the same; the AI settings and buttons are simply not shown.
 */
object AiRuntime {
    const val AVAILABLE = false

    fun open(context: Context, modelPath: String, gpu: Boolean): AiSession =
        throw UnsupportedOperationException("This build has no on-device AI")
}
