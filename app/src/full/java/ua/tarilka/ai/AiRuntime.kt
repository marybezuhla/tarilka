package ua.tarilka.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File

/** The on-device AI runtime of the full build: Gemma via LiteRT-LM (Google AI Edge, Apache 2.0). */
object AiRuntime {
    const val AVAILABLE = true

    fun open(context: Context, modelPath: String, gpu: Boolean): AiSession {
        val config = EngineConfig(
            modelPath = modelPath,
            backend = if (gpu) Backend.GPU() else Backend.CPU(),
            visionBackend = if (gpu) Backend.GPU() else Backend.CPU(),
            maxNumTokens = 8192,
            cacheDir = context.cacheDir.absolutePath,
        )
        return LiteRtSession(Engine(config).also { it.initialize() })
    }
}

private class LiteRtSession(private val engine: Engine) : AiSession {
    override fun ask(
        system: String, prompt: String, image: File?,
        topK: Int, topP: Double, temperature: Double, maxTokens: Int, jsonSchema: String?,
    ): String = engine.createConversation(
        ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = topK, topP = topP, temperature = temperature),
            // No hidden "thinking": it would use up the output before the answer is written.
            thinkingConfig = ThinkingConfig(enableThinking = false),
            maxOutputToken = maxTokens,
            enableResponseFormat = jsonSchema != null,
        ),
    ).use { conv ->
        val contents = if (image != null) Contents.of(Content.ImageFile(image.absolutePath), Content.Text(prompt)) else Contents.of(Content.Text(prompt))
        if (jsonSchema != null) conv.sendMessage(contents, responseFormat = ResponseFormat.json(jsonSchema)).toString()
        else conv.sendMessage(contents).toString()
    }

    override fun close() = engine.close()
}
