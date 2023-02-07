package it.airgap.vault.plugin.isolatedmodules.js.environment

import com.getcapacitor.JSObject
import it.airgap.vault.plugin.isolatedmodules.js.JSModule
import it.airgap.vault.plugin.isolatedmodules.js.JSModuleAction
import it.airgap.vault.util.JSException

interface JSEnvironment {
    @Throws(JSException::class)
    suspend fun run(module: JSModule, action: JSModuleAction): JSObject
    suspend fun destroy()
}