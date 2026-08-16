package io.legado.app.model.jsEngine

import android.content.Context
import com.script.upstream.ScriptBindings as UpstreamScriptBindings
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.utils.MD5Utils
import splitties.init.appCtx

/**
 * JavaScript engine selection is intentionally stored outside BookSource/Room.
 * This keeps the compatibility switch independent from the upstream database schema.
 * Sources without an explicit selection always stay on the legacy engine.
 */
enum class SourceJsEngineMode {
    LEGACY,
    MODERN,
}

object SourceJsEngineModeStore {

    private const val PREFS_NAME = "source_js_engine_modes"

    private val preferences by lazy {
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getMode(sourceKey: String): SourceJsEngineMode {
        val stored = preferences.getString(sourceKey, null) ?: return SourceJsEngineMode.LEGACY
        return runCatching { SourceJsEngineMode.valueOf(stored) }
            .getOrDefault(SourceJsEngineMode.LEGACY)
    }

    fun setMode(sourceKey: String, mode: SourceJsEngineMode) {
        if (getMode(sourceKey) != mode) {
            clearUpstreamGlobals(sourceKey)
        }
        if (mode == SourceJsEngineMode.LEGACY) {
            preferences.edit().remove(sourceKey).apply()
        } else {
            preferences.edit().putString(sourceKey, mode.name).apply()
        }
    }

    fun clearMode(sourceKey: String) {
        preferences.edit().remove(sourceKey).apply()
    }

    private fun clearUpstreamGlobals(sourceKey: String) {
        val sourceHash = MD5Utils.md5Encode(sourceKey)
        UpstreamScriptBindings.removeSharedGlobalStatesBySource(
            BookSource::class.java.name,
            sourceHash,
        )
        UpstreamScriptBindings.removeSharedGlobalStatesBySource(
            RssSource::class.java.name,
            sourceHash,
        )
    }
}
