package it.airgap.vault.plugin.isolatedmodules.js.environment

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import com.getcapacitor.JSObject
import it.airgap.vault.plugin.isolatedmodules.js.Assets
import it.airgap.vault.plugin.isolatedmodules.js.JSModule
import it.airgap.vault.plugin.isolatedmodules.js.JSModuleAction
import it.airgap.vault.plugin.isolatedmodules.js.readSources
import it.airgap.vault.util.JSException
import it.airgap.vault.util.readBytes
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.guava.asDeferred
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.O)
class JavaScriptEngineEnvironment(private val context: Context) : JSEnvironment {
    private val sandbox: Deferred<JavaScriptSandbox> = JavaScriptSandbox.createConnectedInstanceAsync(context).asDeferred()
    private val isolates: MutableMap<String, JavaScriptIsolate> = mutableMapOf()

    @Throws(JSException::class)
    override suspend fun run(module: JSModule, action: JSModuleAction): JSObject = withContext(Dispatchers.Default) {
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
        isolates.values.forEach { it.close() }
        sandbox.await().close()
    }

    private suspend inline fun <R> useIsolatedModule(module: JSModule, block: (JavaScriptIsolate, String) -> R): R {
        val jsIsolate = isolates.getOrPut(module.identifier) {
            sandbox.await().createIsolate(IsolateStartupParameters()).also {
                listOf(
                    it.evaluateJavaScriptAsync(context.assets.readUtils().decodeToString()).asDeferred(),
                    it.evaluateJavaScriptAsync(context.assets.readScript().decodeToString()).asDeferred(),
                ).awaitAll()
                it.loadModule(module)
            }
        }

        return block(jsIsolate, module.identifier)
    }

    private suspend fun JavaScriptIsolate.loadModule(module: JSModule) {
        val sources = module.readSources(context)
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

    private fun AssetManager.readUtils(): ByteArray = readBytes(Assets.JAVA_SCRIPT_ENGINE_UTILS)
    private fun AssetManager.readScript(): ByteArray = readBytes(Assets.SCRIPT)
}