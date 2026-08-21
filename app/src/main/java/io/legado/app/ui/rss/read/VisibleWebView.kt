package io.legado.app.ui.rss.read

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.dict.DictActivity
import io.legado.app.utils.toastOnUi

@SuppressLint("SetJavaScriptEnabled")
class VisibleWebView(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    private var lastSelectedText: String = ""

    init {
        setBackgroundColor(0)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true

        addJavascriptInterface(object {
            @JavascriptInterface
            fun onTextSelected(text: String) {
                lastSelectedText = text
            }
        }, "TextSelectionBridge")

        addJavascriptInterface(object {
            @JavascriptInterface
            fun log(message: String) {
                Log.d(DIAG_TAG, message)
            }
        }, DIAG_BRIDGE)

        val js = """
            document.addEventListener('selectionchange', function() {
                const text = window.getSelection().toString();
                if (text) {
                    TextSelectionBridge.onTextSelected(text);
                }
            });
        """.trimIndent()
        evaluateJavascript(js, null)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(VISIBLE)
    }

    override fun loadUrl(url: String) {
        Log.d(DIAG_TAG, "loadUrl url=$url")
        super.loadUrl(url)
        scheduleLoginDiagnostics(url)
    }

    override fun loadUrl(url: String, additionalHttpHeaders: Map<String, String>) {
        Log.d(
            DIAG_TAG,
            "loadUrl headers url=$url headers=${additionalHttpHeaders.keys.joinToString()}"
        )
        super.loadUrl(url, additionalHttpHeaders)
        scheduleLoginDiagnostics(url)
    }

    private fun scheduleLoginDiagnostics(url: String) {
        listOf(500L, 1500L, 3000L).forEach { delay ->
            postDelayed({
                if (!isAttachedToWindow) return@postDelayed
                evaluateJavascript(LOGIN_DIAGNOSTIC_JS) { result ->
                    Log.d(DIAG_TAG, "inject delay=${delay}ms url=$url result=$result")
                }
            }, delay)
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode {
        return super.startActionMode(createWrappedCallback(callback))
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode {
        return super.startActionMode(createWrappedCallback(callback), type)
    }

    private fun createWrappedCallback(original: ActionMode.Callback?): ActionMode.Callback {
        return object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val result = original?.onCreateActionMode(mode, menu) ?: false
                menu.add(Menu.NONE, MENU_ID_DICT, 0, R.string.dict)
                getSelectedText { }
                return result
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                updateDictMenuItem(menu)
                return original?.onPrepareActionMode(mode, menu) ?: false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    MENU_ID_DICT -> {
                        postDelayed({
                            getSelectedText { selectedText ->
                                if (selectedText.isNotBlank()) {
                                    showDictSheet(selectedText)
                                } else {
                                    context.toastOnUi("未获取到选中文本，请重试")
                                }
                            }
                        }, 200)
                        mode.finish()
                        true
                    }

                    else -> original?.onActionItemClicked(mode, item) ?: false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                original?.onDestroyActionMode(mode)
            }
        }
    }

    private fun updateDictMenuItem(menu: Menu) {
        val dictItem = menu.findItem(MENU_ID_DICT)
        dictItem?.let { item ->
            getSelectedText { selectedText ->
                item.isEnabled = selectedText.isNotBlank()
            }
        }
    }

    private fun getSelectedText(callback: (String) -> Unit) {
        if (lastSelectedText.isNotBlank()) {
            callback(lastSelectedText)
        } else {
            evaluateJavascript("(function(){return window.getSelection().toString();})()") { result ->
                val selectedText = result?.removeSurrounding("\"") ?: ""
                lastSelectedText = selectedText
                callback(selectedText)
            }
        }
    }

    private fun showDictSheet(selectedText: String) {
        context.startActivity(DictActivity.startIntent(context, selectedText))
    }

    companion object {
        private const val MENU_ID_DICT = 1001
        private const val DIAG_TAG = "SourceLoginDiag"
        private const val DIAG_BRIDGE = "LegadoLoginDiag"

        private val LOGIN_DIAGNOSTIC_JS = """
            (function() {
                if (window.__legadoLoginDiagInstalled) {
                    try { LegadoLoginDiag.log('diagnostic already installed url=' + location.href); } catch (e) {}
                    return 'already-installed';
                }
                window.__legadoLoginDiagInstalled = true;

                function send(message) {
                    try { LegadoLoginDiag.log(message); } catch (e) {}
                }

                function textOf(el) {
                    try {
                        return ((el && (el.innerText || el.textContent)) || '')
                            .replace(/\s+/g, ' ')
                            .trim()
                            .slice(0, 120);
                    } catch (e) {
                        return '';
                    }
                }

                function describe(el) {
                    if (!el) return 'null';
                    var href = '';
                    try {
                        var anchor = el.closest && el.closest('a');
                        href = anchor ? (anchor.href || anchor.getAttribute('href') || '') : '';
                    } catch (e) {}
                    return 'tag=' + el.tagName +
                        ' id=' + (el.id || '') +
                        ' class=' + (typeof el.className === 'string' ? el.className : '') +
                        ' role=' + (el.getAttribute ? (el.getAttribute('role') || '') : '') +
                        ' href=' + href +
                        ' text=' + textOf(el);
                }

                function dumpLoginState(prefix) {
                    try {
                        var selectors = [
                            'iframe',
                            '[class*="login" i]', '[id*="login" i]',
                            '[class*="modal" i]', '[id*="modal" i]',
                            '[class*="drawer" i]', '[id*="drawer" i]',
                            '[class*="overlay" i]', '[class*="mask" i]'
                        ].join(',');
                        var nodes = Array.prototype.slice.call(document.querySelectorAll(selectors), 0, 30);
                        send(prefix + ' candidates=' + nodes.length + ' ' + nodes.map(function(el) {
                            var style = window.getComputedStyle(el);
                            var rect = el.getBoundingClientRect();
                            return '[' + describe(el) +
                                ' display=' + style.display +
                                ' visibility=' + style.visibility +
                                ' opacity=' + style.opacity +
                                ' z=' + style.zIndex +
                                ' rect=' + [Math.round(rect.left), Math.round(rect.top), Math.round(rect.width), Math.round(rect.height)].join('/') + ']';
                        }).join(' | '));
                    } catch (e) {
                        send(prefix + ' dump error=' + e);
                    }
                }

                send('installed url=' + location.href +
                    ' ua=' + navigator.userAgent +
                    ' viewport=' + innerWidth + 'x' + innerHeight +
                    ' dpr=' + devicePixelRatio);

                document.addEventListener('click', function(event) {
                    var target = event.target;
                    try {
                        target = target && target.closest ?
                            (target.closest('a,button,[role="button"],[onclick],div,span') || target) : target;
                    } catch (e) {}
                    send('click ' + describe(target));
                    setTimeout(function() { dumpLoginState('after-click-100ms'); }, 100);
                    setTimeout(function() { dumpLoginState('after-click-500ms'); }, 500);
                    setTimeout(function() { dumpLoginState('after-click-1500ms'); }, 1500);
                }, true);

                var originalOpen = window.open;
                window.open = function() {
                    send('window.open url=' + (arguments[0] || '') + ' target=' + (arguments[1] || ''));
                    return originalOpen.apply(this, arguments);
                };

                window.addEventListener('error', function(event) {
                    send('window.error message=' + event.message +
                        ' file=' + event.filename +
                        ' line=' + event.lineno + ':' + event.colno);
                });

                window.addEventListener('unhandledrejection', function(event) {
                    var reason = event.reason;
                    send('unhandledrejection reason=' +
                        (reason && (reason.stack || reason.message) ? (reason.stack || reason.message) : String(reason)));
                });

                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        if (mutation.type === 'childList') {
                            Array.prototype.forEach.call(mutation.addedNodes || [], function(node) {
                                if (!node || node.nodeType !== 1) return;
                                if (node.tagName === 'IFRAME') {
                                    send('iframe-added src=' + (node.src || node.getAttribute('src') || ''));
                                }
                                try {
                                    var nested = node.querySelectorAll && node.querySelectorAll('iframe');
                                    if (nested && nested.length) {
                                        Array.prototype.forEach.call(nested, function(frame) {
                                            send('iframe-added-nested src=' + (frame.src || frame.getAttribute('src') || ''));
                                        });
                                    }
                                } catch (e) {}
                            });
                        } else if (mutation.type === 'attributes') {
                            var el = mutation.target;
                            var marker = ((el.id || '') + ' ' +
                                (typeof el.className === 'string' ? el.className : '') + ' ' + textOf(el)).toLowerCase();
                            if (/login|modal|drawer|overlay|mask|account|user|sign/.test(marker)) {
                                send('mutation attr=' + mutation.attributeName + ' ' + describe(el));
                            }
                        }
                    });
                });
                observer.observe(document.documentElement, {
                    subtree: true,
                    childList: true,
                    attributes: true,
                    attributeFilter: ['class', 'style', 'hidden', 'aria-hidden']
                });

                dumpLoginState('initial');
                return 'installed';
            })();
        """.trimIndent()
    }
}

@Composable
fun VisibleWebViewCompose(
    modifier: Modifier = Modifier,
    onCreated: (VisibleWebView) -> Unit,
    onDestroyed: (() -> Unit)? = null
) {
    val webViewHolder = remember { WebViewHolder() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                val webView = VisibleWebView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                addView(webView)
                webViewHolder.webView = webView
                onCreated(webView)
            }
        },
        update = { container ->
            webViewHolder.webView = container.getChildAt(0) as? VisibleWebView
        }
    )
    DisposableEffect(Unit) {
        onDispose {
            onDestroyed?.invoke()
            webViewHolder.webView?.let { webView ->
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
            webViewHolder.webView = null
        }
    }
}

private class WebViewHolder {
    var webView: VisibleWebView? = null
}
