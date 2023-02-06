package it.airgap.vault.plugin.isolatedmodules.js

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import it.airgap.vault.plugin.isolatedmodules.js.context.JSEnvironmentContext
import it.airgap.vault.util.*
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class JSEnvironment(private val context: JSEnvironmentContext) {
    private val modules: MutableMap<String, JSModule> = mutableMapOf()

    suspend fun evaluateLoadModules(modules: List<JSModule>, protocolType: JSProtocolType?): JSObject {
        val loadedModules = modules.asyncMap { module ->
            context.evaluate(module, JSModuleAction.Load(protocolType)).also { module.registerFor(it) }
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
        return context.evaluate(module, JSModuleAction.CallMethod.OfflineProtocol(name, args, protocolIdentifier))
    }

    suspend fun evaluateCallOnlineProtocolMethod(
        name: String,
        args: JSArray?,
        protocolIdentifier: String,
        networkId: String?,
    ): JSObject {
        val module = modules[protocolIdentifier] ?: failWithModuleForProtocolNotFound(protocolIdentifier)
        return context.evaluate(module, JSModuleAction.CallMethod.OnlineProtocol(name, args, protocolIdentifier, networkId))
    }

    suspend fun evaluateCallBlockExplorerMethod(
        name: String,
        args: JSArray?,
        protocolIdentifier: String,
        networkId: String?,
    ): JSObject {
        val module = modules[protocolIdentifier] ?: failWithModuleForProtocolNotFound(protocolIdentifier)
        return context.evaluate(module, JSModuleAction.CallMethod.BlockExplorer(name, args, protocolIdentifier, networkId))
    }

    suspend fun evaluateCallV3SerializerCompanionMethod(
        name: String,
        args: JSArray?,
        moduleIdentifier: String,
    ): JSObject {
        val module = modules[moduleIdentifier] ?: failWithModuleNotFound(moduleIdentifier)
        return context.evaluate(module, JSModuleAction.CallMethod.V3SerializerCompanion(name, args))
    }


    suspend fun destroy() {
        context.destroy()
    }

    private fun JSModule.registerFor(json: JSObject) {
        modules[identifier] = this

        val protocols = json.getJSONArray("protocols")
        for (i in 0 until protocols.length()) {
            val protocol = protocols.getJSONObject(i)
            val identifier = protocol.getString("identifier")

            modules[identifier] = this
        }
    }

    @Throws(IllegalStateException::class)
    private fun failWithModuleForProtocolNotFound(protocolIdentifier: String): Nothing = throw IllegalStateException("Module for protocol $protocolIdentifier could not be found.")

    @Throws(IllegalStateException::class)
    private fun failWithModuleNotFound(moduleIdentifier: String): Nothing = throw IllegalStateException("Module $moduleIdentifier could not be found.")
}