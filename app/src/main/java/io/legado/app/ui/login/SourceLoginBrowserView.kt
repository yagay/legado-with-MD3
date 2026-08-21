package io.legado.app.ui.login

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/**
 * Dedicated WebView for source web login.
 *
 * Keep this view intentionally clean: source login must not inherit RSS-reader
 * background, selection, dictionary, or JavaScript bridge customizations.
 */
class SourceLoginBrowserView(
    context: Context,
    attrs: AttributeSet? = null,
) : WebView(context, attrs) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Match upstream login WebView visibility behavior without carrying the
        // RSS reader's rendering customizations into the login page.
        super.onWindowVisibilityChanged(VISIBLE)
    }
}
