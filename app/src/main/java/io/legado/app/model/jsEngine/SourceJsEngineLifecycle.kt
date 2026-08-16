package io.legado.app.model.jsEngine

import com.script.upstream.ScriptBindings as UpstreamScriptBindings
import io.legado.app.data.entities.BaseSource
import io.legado.app.utils.MD5Utils

/** Keeps per-source JavaScript engine state outside the upstream database model. */
object SourceJsEngineLifecycle {

    fun clearSource(sourceClass: Class<out BaseSource>, sourceKey: String) {
        SourceJsEngineModeStore.clearMode(sourceKey)
        UpstreamScriptBindings.removeSharedGlobalStatesBySource(
            sourceClass.name,
            MD5Utils.md5Encode(sourceKey),
        )
    }
}
