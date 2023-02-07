package it.airgap.vault.plugin.isolatedmodules.js.environment

import android.content.Context
import android.content.res.AssetManager
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import com.getcapacitor.JSObject
import it.airgap.vault.plugin.isolatedmodules.js.Assets
import it.airgap.vault.plugin.isolatedmodules.js.JSModule
import it.airgap.vault.plugin.isolatedmodules.js.JSModuleAction
import it.airgap.vault.plugin.isolatedmodules.js.readSources
import it.airgap.vault.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WebViewEnvironment(private val context: Context) : JSEnvironment {
    @Throws(JSException::class)
    override suspend fun run(module: JSModule, action: JSModuleAction): JSObject = withContext(Dispatchers.Main) {
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
            evaluateJavascript(context.assets.readScript().decodeToString())

            val sources = module.readSources(context)
            sources.forEach { evaluateJavascript(it.decodeToString()) }
        }

        return block(webView, jsAsyncResult, module.identifier).also {
            webView.destroy()
        }
    }

    private fun AssetManager.readScript(): ByteArray = readBytes(Assets.SCRIPT)
}