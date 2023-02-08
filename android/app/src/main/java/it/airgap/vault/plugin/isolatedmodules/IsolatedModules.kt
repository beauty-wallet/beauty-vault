package it.airgap.vault.plugin.isolatedmodules

import androidx.lifecycle.lifecycleScope
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import it.airgap.vault.plugin.isolatedmodules.js.*
import it.airgap.vault.plugin.isolatedmodules.js.environment.JSEnvironment
import it.airgap.vault.util.ExecutableDeferred
import it.airgap.vault.util.assertReceived
import it.airgap.vault.util.executeCatching
import it.airgap.vault.util.readBytes
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

@CapacitorPlugin
class IsolatedModules : Plugin() {
    private val jsEvaluator: Deferred<JSEvaluator> = ExecutableDeferred { JSEvaluator(context, fileExplorer) }
    private val fileExplorer: FileExplorer by lazy { FileExplorer(context) }

    @PluginMethod
    fun loadModules(call: PluginCall) {
        activity.lifecycleScope.launch {
            call.executeCatching {
                val modules = fileExplorer.loadAssetModules() + fileExplorer.loadExternalModules()

                resolve(jsEvaluator.await().evaluateLoadModules(modules, protocolType))
            }
        }
    }

    @PluginMethod
    fun callMethod(call: PluginCall) {
        call.executeCatching {
            assertReceived(Param.TARGET, Param.METHOD)

            activity.lifecycleScope.launch {
                executeCatching {
                    val value = when (target) {
                        JSCallMethodTarget.OfflineProtocol -> {
                            assertReceived(Param.PROTOCOL_IDENTIFIER)
                            jsEvaluator.await().evaluateCallOfflineProtocolMethod(method, args, protocolIdentifier)
                        }
                        JSCallMethodTarget.OnlineProtocol -> {
                            assertReceived(Param.PROTOCOL_IDENTIFIER)
                            jsEvaluator.await().evaluateCallOnlineProtocolMethod(method, args, protocolIdentifier, networkId)
                        }
                        JSCallMethodTarget.BlockExplorer -> {
                            assertReceived(Param.PROTOCOL_IDENTIFIER)
                            jsEvaluator.await().evaluateCallBlockExplorerMethod(method, args, protocolIdentifier, networkId)
                        }
                        JSCallMethodTarget.V3SerializerCompanion -> {
                            assertReceived(Param.MODULE_IDENTIFIER)
                            jsEvaluator.await().evaluateCallV3SerializerCompanionMethod(method, args, moduleIdentifier)
                        }
                    }
                    resolve(value)
                }
            }
        }
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        activity.lifecycleScope.launch {
            jsEvaluator.await().destroy()
        }
    }

    private val PluginCall.protocolType: JSProtocolType?
        get() = getString(Param.PROTOCOL_TYPE)?.let { JSProtocolType.fromString(it) }

    private val PluginCall.target: JSCallMethodTarget
        get() = getString(Param.TARGET)?.let { JSCallMethodTarget.fromString(it) }!!

    private val PluginCall.method: String
        get() = getString(Param.METHOD)!!

    private val PluginCall.args: JSArray?
        get() = getArray(Param.ARGS, null)

    private val PluginCall.protocolIdentifier: String
        get() = getString(Param.PROTOCOL_IDENTIFIER)!!

    private val PluginCall.moduleIdentifier: String
        get() = getString(Param.MODULE_IDENTIFIER)!!

    private val PluginCall.networkId: String?
        get() = getString(Param.NETWORK_ID)

    private object Param {
        const val PROTOCOL_TYPE = "protocolType"
        const val TARGET = "target"
        const val METHOD = "method"
        const val ARGS = "args"
        const val PROTOCOL_IDENTIFIER = "protocolIdentifier"
        const val MODULE_IDENTIFIER = "moduleIdentifier"
        const val NETWORK_ID = "networkId"
    }
}