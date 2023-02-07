package it.airgap.vault.plugin.isolatedmodules

import androidx.lifecycle.lifecycleScope
import com.getcapacitor.JSArray
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import it.airgap.vault.plugin.isolatedmodules.js.JSCallMethodTarget
import it.airgap.vault.plugin.isolatedmodules.js.JSEvaluator
import it.airgap.vault.plugin.isolatedmodules.js.JSModule
import it.airgap.vault.plugin.isolatedmodules.js.JSProtocolType
import it.airgap.vault.util.assertReceived
import it.airgap.vault.util.executeCatching
import kotlinx.coroutines.launch

@CapacitorPlugin
class IsolatedModules : Plugin() {
    private val jsEvaluator: JSEvaluator by lazy { JSEvaluator(context) }

    @PluginMethod
    fun loadModules(call: PluginCall) {
        activity.lifecycleScope.launch {
            call.executeCatching {
                // TODO: load dynamically
                val modules = listOf(
                    JSModule.Asset(
                        identifier = "aeternity",
                        paths = listOf("public/assets/libs/aeternity/airgap-aeternity.browserify.js"),
                    ),
                    JSModule.Asset(
                        identifier = "astar",
                        paths = listOf("public/assets/libs/astar/airgap-astar.browserify.js"),
                    ),
                    JSModule.Asset(
                        identifier = "bitcoin",
                        paths = listOf("public/assets/libs/bitcoin/airgap-bitcoin.browserify.js"),
                    ),
                    JSModule.Asset(
                        identifier = "cosmos",
                        paths = listOf("public/assets/libs/cosmos/airgap-cosmos.browserify.js"),
                    ),
                    JSModule.Asset(
                        identifier = "ethereum",
                        paths = listOf("public/assets/libs/ethereum/airgap-ethereum.browserify.js"),
                    ),
                    JSModule.Asset(
                        identifier = "groestlcoin",
                        paths = listOf("public/assets/libs/groestlcoin/airgap-groestlcoin.browserify.js"),
                    ),
                    JSModule.Asset(
                        identifier = "moonbeam",
                        paths = listOf("public/assets/libs/moonbeam/airgap-moonbeam.browserify.js"),
                    ),
                    JSModule.Asset(
                        identifier = "polkadot",
                        paths = listOf("public/assets/libs/polkadot/airgap-polkadot.browserify.js"),
                    ),
                    JSModule.Asset(
                        identifier = "tezos",
                        paths = listOf("public/assets/libs/tezos/airgap-tezos.browserify.js"),
                    ),
                )

                resolve(jsEvaluator.evaluateLoadModules(modules, protocolType))
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
                            jsEvaluator.evaluateCallOfflineProtocolMethod(method, args, protocolIdentifier)
                        }
                        JSCallMethodTarget.OnlineProtocol -> {
                            assertReceived(Param.PROTOCOL_IDENTIFIER)
                            jsEvaluator.evaluateCallOnlineProtocolMethod(method, args, protocolIdentifier, networkId)
                        }
                        JSCallMethodTarget.BlockExplorer -> {
                            assertReceived(Param.PROTOCOL_IDENTIFIER)
                            jsEvaluator.evaluateCallBlockExplorerMethod(method, args, protocolIdentifier, networkId)
                        }
                        JSCallMethodTarget.V3SerializerCompanion -> {
                            assertReceived(Param.MODULE_IDENTIFIER)
                            jsEvaluator.evaluateCallV3SerializerCompanionMethod(method, args, moduleIdentifier)
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
            jsEvaluator.destroy()
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