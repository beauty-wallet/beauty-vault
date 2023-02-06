package it.airgap.vault.plugin.isolatedmodules

import android.content.Context
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import it.airgap.vault.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.asDeferred
import java.io.File
import java.util.*

class ModuleJSContext(private val context: Context) {
    private val jsSandbox: Deferred<JavaScriptSandbox> = JavaScriptSandbox.createConnectedInstanceAsync(context).asDeferred()
    private val jsIsolates: MutableMap<String, JavaScriptIsolate> = mutableMapOf()

    private val modules: MutableMap<String, JSModule> = mutableMapOf()

    suspend fun evaluateLoadModules(modules: List<JSModule>, protocolType: JSProtocolType?): JSObject {
        val loadedModules = modules.asyncMap {  module ->
            evaluate(module, JSModuleAction.Load(protocolType)).also { module.registerFor(it) }
        }

        return JSObject("""
            {
                "modules": $loadedModules
            }
        """.trimIndent())
    }

    suspend fun evaluateCallOfflineProtocolMethod(
        name: String,
        args: JSArray?,
        protocolIdentifier: String,
    ): JSObject {
        val module = modules[protocolIdentifier] ?: failWithModuleForProtocolNotFound(protocolIdentifier)
        return evaluate(module, JSModuleAction.CallMethod.OfflineProtocol(name, args, protocolIdentifier))
    }

    suspend fun evaluateCallOnlineProtocolMethod(
        name: String,
        args: JSArray?,
        protocolIdentifier: String,
        networkId: String?,
    ): JSObject {
        val module = modules[protocolIdentifier] ?: failWithModuleForProtocolNotFound(protocolIdentifier)
        return evaluate(module, JSModuleAction.CallMethod.OnlineProtocol(name, args, protocolIdentifier, networkId))
    }

    suspend fun evaluateCallBlockExplorerMethod(
        name: String,
        args: JSArray?,
        protocolIdentifier: String,
        networkId: String?,
    ): JSObject {
        val module = modules[protocolIdentifier] ?: failWithModuleForProtocolNotFound(protocolIdentifier)
        return evaluate(module, JSModuleAction.CallMethod.BlockExplorer(name, args, protocolIdentifier, networkId))
    }

    suspend fun evaluateCallV3SerializerCompanionMethod(
        name: String,
        args: JSArray?,
        moduleIdentifier: String,
    ): JSObject {
        val module = modules[moduleIdentifier] ?: failWithModuleNotFound(moduleIdentifier)
        return evaluate(module, JSModuleAction.CallMethod.V3SerializerCompanion(name, args))
    }


    suspend fun destroy() {
        jsIsolates.values.forEach { it.close() }
        jsSandbox.await().close()
    }

    @Throws(JSException::class)
    private suspend fun evaluate(module: JSModule, action: JSModuleAction): JSObject = withContext(Dispatchers.Default) {
        useIsolatedModule(module) { jsIsolate, module ->
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

    private suspend inline fun <R> useIsolatedModule(module: JSModule, block: (JavaScriptIsolate, String) -> R): R {
        val jsIsolate = jsIsolates.getOrPut(module.identifier) {
            jsSandbox.await().createIsolate(IsolateStartupParameters()).also {
                val utils = context.assets.open("public/assets/native/isolated_modules/isolated-modules.android.js").use { stream -> stream.readBytes().decodeToString() }
                val script = context.assets.open("public/assets/native/isolated_modules/isolated-modules.script.js").use { stream -> stream.readBytes().decodeToString() }
                listOf(it.evaluateJavaScriptAsync(utils).asDeferred(), it.evaluateJavaScriptAsync(script).asDeferred()).awaitAll()
                it.loadModule(module)
            }
        }

        return block(jsIsolate, module.identifier)
    }

    private suspend fun JavaScriptIsolate.loadModule(module: JSModule) {
        val sources = module.readModuleSources()
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

    private fun JSModule.readModuleSources(): List<ByteArray> =
        when (this) {
            is JSModule.Asset -> readModuleSources()
            is JSModule.External -> readModuleSources()
        }

    private fun JSModule.Asset.readModuleSources(): List<ByteArray> =
        paths.map { path -> context.assets.open(path).use { it.readBytes() } }

    private fun JSModule.External.readModuleSources(): List<ByteArray> =
        paths.map { path -> File(path).readBytes() }

    private fun JSModule.registerFor(json: JSObject) {
        val protocols = json.getJSONArray("protocols")
        for (i in 0 until protocols.length()) {
            val protocol = protocols.getJSONObject(i)
            val identifier = protocol.getString("identifier")

            modules[identifier] = this
        }
    }

    enum class JSProtocolType {
        Offline, Online, Full;

        override fun toString(): String = name.replaceFirstChar { it.lowercase(Locale.getDefault()) }

        companion object {
            fun fromString(value: String): JSProtocolType? = values().find { it.name.lowercase() == value.lowercase() }
        }
    }

    enum class JSCallMethodTarget {
        OfflineProtocol, OnlineProtocol, BlockExplorer, V3SerializerCompanion;

        override fun toString(): String = name.replaceFirstChar { it.lowercase(Locale.getDefault()) }

        companion object {
            fun fromString(value: String): JSCallMethodTarget? = values().find { it.name.lowercase() == value.lowercase() }
        }
    }

    sealed interface JSModule {
        val identifier: String
        val paths: List<String>

        data class Asset(override val identifier: String, override val paths: List<String>) : JSModule
        data class External(override val identifier: String, override val paths: List<String>) : JSModule
    }

    private sealed interface JSModuleAction {
        fun toJson(): String

        data class Load(val protocolType: JSProtocolType?) : JSModuleAction {
            override fun toJson(): String = JSObject("""
                {
                    "type": "$TYPE",
                    "protocolType": ${protocolType?.toString().toJson()}
                }
            """.trimIndent()).toString()

            companion object {
                private const val TYPE = "load"
            }
        }

        sealed class CallMethod(val target: JSCallMethodTarget, private val partial: JSObject) : JSModuleAction {
            abstract val name: String
            abstract val args: JSArray?

            override fun toJson(): String {
                val args = args?.replaceNullWithUndefined()?.toString() ?: "[]"

                return JSObject("""
                    {
                        "type": "$TYPE",
                        "target": "$target",
                        "method": "$name",
                        "args": $args
                    }
                """.trimIndent())
                    .assign(partial)
                    .toString()
            }

            data class OfflineProtocol(
                override val name: String,
                override val args: JSArray?,
                val protocolIdentifier: String,
            ) : CallMethod(JSCallMethodTarget.OfflineProtocol, JSObject("""
            {
                protocolIdentifier: "$protocolIdentifier"
            }
        """.trimIndent()))

            data class OnlineProtocol(
                override val name: String,
                override val args: JSArray?,
                val protocolIdentifier: String,
                val networkId: String?,
            ) : CallMethod(JSCallMethodTarget.OnlineProtocol, JSObject("""
            {
                protocolIdentifier: "$protocolIdentifier",
                networkId: ${networkId.toJson()}
            }
        """.trimIndent()))

            data class BlockExplorer(
                override val name: String,
                override val args: JSArray?,
                val protocolIdentifier: String,
                val networkId: String?,
            ) : CallMethod(JSCallMethodTarget.BlockExplorer, JSObject("""
            {
                protocolIdentifier: "$protocolIdentifier",
                networkId: ${networkId.toJson()}
            }
        """.trimIndent()))

            data class V3SerializerCompanion(
                override val name: String,
                override val args: JSArray?
            ) : CallMethod(JSCallMethodTarget.V3SerializerCompanion, JSObject("{}"))

            companion object {
                private const val TYPE = "callMethod"
            }
        }
    }

    @Throws(IllegalStateException::class)
    private fun failWithModuleForProtocolNotFound(protocolIdentifier: String): Nothing = throw IllegalStateException("Module for protocol $protocolIdentifier could not be found.")

    @Throws(IllegalStateException::class)
    private fun failWithModuleNotFound(moduleIdentifier: String): Nothing = throw IllegalStateException("Module $moduleIdentifier could not be found.")
}

private fun JSObject?.orUndefined(): Any = this ?: JSUndefined

private fun JSArray.replaceNullWithUndefined(): JSArray =
    JSArray(toList<Any>().map { if (it == JSObject.NULL) JSUndefined else it })