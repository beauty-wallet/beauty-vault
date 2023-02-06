package it.airgap.vault.plugin.isolatedmodules.js.context

import android.content.Context
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import com.getcapacitor.JSObject
import it.airgap.vault.plugin.isolatedmodules.js.JSModule
import it.airgap.vault.plugin.isolatedmodules.js.JSModuleAction
import it.airgap.vault.plugin.isolatedmodules.js.readModuleSources
import it.airgap.vault.util.JSException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.guava.asDeferred
import kotlinx.coroutines.withContext

class JavaScriptEngineEnvironmentContext(private val context: Context) : JSEnvironmentContext {
    private val jsSandbox: Deferred<JavaScriptSandbox> = JavaScriptSandbox.createConnectedInstanceAsync(context).asDeferred()
    private val jsIsolates: MutableMap<String, JavaScriptIsolate> = mutableMapOf()

    @Throws(JSException::class)
    override suspend fun evaluate(module: JSModule, action: JSModuleAction): JSObject = withContext(Dispatchers.Default) {
        useIsolatedModule(module) { jsIsolate, module ->
            // TODO: move create to coinlib
            val script = """
                global.${module}.create = () => {
                    return new global.${module}.${module.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}Module
                }

                new Promise((resolve, reject) => {
                    execute(
                        global.${module},
                        '${module}',
                        ${action.toJson()},
                        function (result) {
                            resolve(JSON.stringify(result));
                        },
                        function (error) {
                            reject(JSON.stringify({ error }));
                        }
                    );
                })
            """.trimIndent()

            val result = jsIsolate.evaluateJavaScriptAsync(script).asDeferred().await()
            val jsObject = JSObject(result)
            jsObject.getString("error")?.let { error -> throw JSException(error) }

            jsObject
        }
    }

    override suspend fun destroy() {
        jsIsolates.values.forEach { it.close() }
        jsSandbox.await().close()
    }

    private suspend inline fun <R> useIsolatedModule(module: JSModule, block: (JavaScriptIsolate, String) -> R): R {
        val jsIsolate = jsIsolates.getOrPut(module.identifier) {
            jsSandbox.await().createIsolate(IsolateStartupParameters()).also {
                val utils = context.assets.open("public/assets/native/isolated_modules/isolated-modules.js-engine-android.js").use { stream -> stream.readBytes().decodeToString() }
                val script = context.assets.open("public/assets/native/isolated_modules/isolated-modules.script.js").use { stream -> stream.readBytes().decodeToString() }
                listOf(it.evaluateJavaScriptAsync(utils).asDeferred(), it.evaluateJavaScriptAsync(script).asDeferred()).awaitAll()
                it.loadModule(module)
            }
        }

        return block(jsIsolate, module.identifier)
    }

    private suspend fun JavaScriptIsolate.loadModule(module: JSModule) {
        val sources = module.readModuleSources(context)
        sources.forEachIndexed { idx, source ->
            val scriptId = "${module.identifier}-$idx-script"
            provideNamedData(scriptId, source)
            evaluateJavaScriptAsync("""
                android.consumeNamedDataAsArrayBuffer('${scriptId}').then((value) => {
                    var string = utf8ArrayToString(new Uint8Array(value));
                    eval(string);
                });
            """.trimIndent()).asDeferred().await()
        }
    }
}