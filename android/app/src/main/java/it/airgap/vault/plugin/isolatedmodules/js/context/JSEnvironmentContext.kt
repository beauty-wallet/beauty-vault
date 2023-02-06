package it.airgap.vault.plugin.isolatedmodules.js.context

import com.getcapacitor.JSObject
import it.airgap.vault.plugin.isolatedmodules.js.JSModule
import it.airgap.vault.plugin.isolatedmodules.js.JSModuleAction
import it.airgap.vault.util.JSException

interface JSEnvironmentContext {
    @Throws(JSException::class)
    suspend fun evaluate(module: JSModule, action: JSModuleAction): JSObject
    suspend fun destroy()
}