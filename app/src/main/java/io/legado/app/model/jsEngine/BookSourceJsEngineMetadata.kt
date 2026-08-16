package io.legado.app.model.jsEngine

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON

/**
 * Portable Enhance-only metadata for BookSource JSON.
 *
 * The metadata intentionally lives outside [BookSource] so the upstream Room entity/schema stays
 * untouched. Upstream/older Legado builds simply ignore the unknown `_legadoEnhance` JSON field.
 * Legacy is the implicit default, so only non-default engine selections need to be emitted.
 */
object BookSourceJsEngineMetadata {

    private const val META_KEY = "_legadoEnhance"
    private const val ENGINE_KEY = "jsEngine"

    fun toJson(source: BookSource, mode: SourceJsEngineMode = SourceJsEngineModeStore.getMode(source.getKey())): String =
        GSON.toJson(toJsonObject(source, mode))

    fun toJson(sources: List<BookSource>): String = GSON.toJson(
        sources.map { source ->
            toJsonObject(source, SourceJsEngineModeStore.getMode(source.getKey()))
        }
    )

    fun readMode(element: JsonElement?): SourceJsEngineMode? {
        if (element == null || !element.isJsonObject) return null
        val metadata = element.asJsonObject.getAsJsonObject(META_KEY) ?: return null
        val name = metadata.get(ENGINE_KEY)?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        return runCatching { SourceJsEngineMode.valueOf(name) }.getOrNull()
    }

    fun readMode(json: String): SourceJsEngineMode? = runCatching {
        readMode(JsonParser.parseString(json))
    }.getOrNull()

    fun readSourceKey(element: JsonElement?): String? {
        if (element == null || !element.isJsonObject) return null
        return element.asJsonObject.get("bookSourceUrl")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it.isNotBlank() }
    }

    private fun toJsonObject(source: BookSource, mode: SourceJsEngineMode): JsonObject {
        val root = GSON.toJsonTree(source).asJsonObject
        if (mode != SourceJsEngineMode.LEGACY) {
            root.add(
                META_KEY,
                JsonObject().apply { addProperty(ENGINE_KEY, mode.name) },
            )
        }
        return root
    }
}
