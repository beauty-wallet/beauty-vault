package it.airgap.vault.plugin.isolatedmodules.js.context

import android.content.Context
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebSettings.RenderPriority
import android.webkit.WebView
import com.getcapacitor.JSObject
import it.airgap.vault.plugin.isolatedmodules.js.JSModule
import it.airgap.vault.plugin.isolatedmodules.js.JSModuleAction
import it.airgap.vault.plugin.isolatedmodules.js.readModuleSources
import it.airgap.vault.util.JSAsyncResult
import it.airgap.vault.util.JSException
import it.airgap.vault.util.addJavascriptInterface
import it.airgap.vault.util.evaluateJavascript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebViewEnvironmentContext(private val context: Context) : JSEnvironmentContext {
    @Throws(JSException::class)
    override suspend fun evaluate(module: JSModule, action: JSModuleAction): JSObject = withContext(Dispatchers.Main) {
        useIsolatedModule(module) { webView, jsAsyncResult, module ->
            // TODO: move create to coinlib
            val script = """
                ${module}.create = () => {
                    return new ${module}.${module.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}Module
                }

                execute(
                    ${module},
                    '${module}',
                    ${action.toJson()},
                    function (result) {
                        ${jsAsyncResult}.completeFromJS(JSON.stringify(result));
                    },
                    function (error) {
                        ${jsAsyncResult}.throwFromJS(error);
                    }
                );
            """.trimIndent()

            webView.evaluateJavascript(script)

            JSObject(jsAsyncResult.await().getOrThrow())
        }
    }

    override suspend fun destroy() {
        /* no action */
    }

    private inline fun <R> useIsolatedModule(module: JSModule, block: (WebView, JSAsyncResult, String) -> R): R {
        val jsAsyncResult = JSAsyncResult()
        val webView = WebView(context).apply {
            visibility = View.GONE

            with(settings) {
                javaScriptEnabled = true

                allowContentAccess = false
                allowFileAccess = false
                blockNetworkImage = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                displayZoomControls = false
                setGeolocationEnabled(false)
                loadsImagesAutomatically = false
                safeBrowsingEnabled = true
                setSupportZoom(false)
            }

            setBackgroundColor(context.resources.getColor(android.R.color.transparent, null))
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setNetworkAvailable(false)

            addJavascriptInterface(jsAsyncResult)
        }

        with(webView) {
            val script = context.assets.open("public/assets/native/isolated_modules/isolated-modules.script.js").use { stream -> stream.readBytes().decodeToString() }

            evaluateJavascript(script)

            val sources = module.readModuleSources(context)
            sources.forEach { evaluateJavascript(it.decodeToString()) }
        }

        return block(webView, jsAsyncResult, module.identifier).also {
            webView.destroy()
        }
    }
}