package ua.tarilka.ai

import java.io.File

/**
 * One loaded on-device model. The runtime behind it exists only in the "full" build (LiteRT-LM);
 * the "fdroid" build has none, so the AI is not offered there (see AiRuntime.AVAILABLE).
 */
interface AiSession : AutoCloseable {
    /** One question in a fresh conversation. [jsonSchema] asks for constrained JSON output. */
    fun ask(
        system: String,
        prompt: String,
        image: File?,
        topK: Int,
        topP: Double,
        temperature: Double,
        maxTokens: Int,
        jsonSchema: String? = null,
    ): String
}
