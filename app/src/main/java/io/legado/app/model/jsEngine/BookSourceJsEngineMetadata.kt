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
 * Both engine modes are emitted explicitly so importing over an existing source restores the exact
 * selection instead of inheriting a stale local preference.
 */
object BookSourceJsEngineMetadata {

    private const val META_KEY = "_legadoEnhance"
    private const val VERSION_KEY = "version"
    private const val ENGINE_KEY = "jsEngine"
    private const val VERSION = 1

    fun toJson(
        source: BookSource,
        mode: SourceJsEngineMode = SourceJsEngineModeStore.getMode(source.getKey()),
    ): String = GSON.toJson(toJsonObject(source, mode))

    fun toJson(sources: List<BookSource>): String = GSON.toJson(
        sources.map { source ->
            toJsonObject(source, SourceJsEngineModeStore.getMode(source.getKey()))
        }
    )

    fun readMode(element: JsonElement?): SourceJsEngineMode? {
        if (element == null || !element.isJsonObject) return null
        val metadataElement = element.asJsonObject.get(META_KEY) ?: return null
        if (!metadataElement.isJsonObject) return null
        val metadata = metadataElement.asJsonObject
        val nameElement = metadata.get(ENGINE_KEY) ?: return null
        if (!nameElement.isJsonPrimitive || !nameElement.asJsonPrimitive.isString) return null
        return runCatching { SourceJsEngineMode.valueOf(nameElement.asString) }.getOrNull()
    }

    fun readMode(json: String): SourceJsEngineMode? = runCatching {
        readMode(JsonParser.parseString(json))
    }.getOrNull()

    fun readSourceKey(element: JsonElement?): String? {
        if (element == null || !element.isJsonObject) return null
        return element.asJsonObject.get("bookSourceUrl")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.takeIf { it.isNotBlank() }
    }

    private fun toJsonObject(source: BookSource, mode: SourceJsEngineMode): JsonObject {
        val root = GSON.toJsonTree(source).asJsonObject
        root.add(
            META_KEY,
            JsonObject().apply {
                addProperty(VERSION_KEY, VERSION)
                addProperty(ENGINE_KEY, mode.name)
            },
        )
        return root
    }
}
