package it.airgap.vault.plugin.isolatedmodules

import android.content.Context
import com.getcapacitor.JSObject
import it.airgap.vault.plugin.isolatedmodules.js.JSModule
import it.airgap.vault.plugin.isolatedmodules.js.environment.JSEnvironment
import it.airgap.vault.util.readBytes
import java.io.File

interface StaticSourcesExplorer {
    fun readJavaScriptEngineUtils(): ByteArray
    fun readIsolatedModulesScript(): ByteArray
}

interface DynamicSourcesExplorer<in M : JSModule> {
    fun listModules(): List<String>
    fun absoluteModulePath(path: String): String

    fun readModuleSources(module: M): Sequence<ByteArray>
    fun readModuleManifest(module: String): ByteArray
}

class FileExplorer private constructor(
    private val assetsExplorer: AssetsExplorer,
    private val internalFilesExplorer: InternalFilesExplorer,
) : StaticSourcesExplorer by assetsExplorer {
    constructor(context: Context) : this(AssetsExplorer(context), InternalFilesExplorer(context))

    fun loadAssetModules(): List<JSModule> = loadModules(assetsExplorer, JSModule::Asset)
    fun loadExternalModules(): List<JSModule> = loadModules(internalFilesExplorer, JSModule::External)

    fun readModuleSources(module: JSModule): Sequence<ByteArray> =
        when (module) {
            is JSModule.Asset -> assetsExplorer.readModuleSources(module)
            is JSModule.External -> internalFilesExplorer.readModuleSources(module)
        }

    private fun <T : JSModule> loadModules(
        explorer: DynamicSourcesExplorer<T>,
        constructor: (identifier: String, namespace: String?, preferredEnvironment: JSEnvironment.Type, paths: List<String>) -> T,
    ): List<T> = explorer.listModules().map { module ->
        val manifest = JSObject(explorer.readModuleManifest(module).decodeToString())
        val namespace = manifest.getJSObject("src")?.getString("namespace")
        val preferredEnvironment = manifest.getJSObject("jsenv")?.getString("android")?.let { JSEnvironment.Type.fromString(it) } ?: JSEnvironment.Type.JavaScriptEngine
        val paths = buildList {
            val include = manifest.getJSONArray("include")
            for (i in 0 until include.length()) {
                val path = include.getString(i).takeIf { it.endsWith(".js") } ?: continue
                add(explorer.absoluteModulePath("$module/${path.trimStart('/')}"))
            }
        }

        constructor(module, namespace, preferredEnvironment, paths)
    }
}

private class AssetsExplorer(private val context: Context) : StaticSourcesExplorer, DynamicSourcesExplorer<JSModule.Asset> {
    override fun readJavaScriptEngineUtils(): ByteArray = context.assets.readBytes(JAVA_SCRIPT_ENGINE_UTILS)
    override fun readIsolatedModulesScript(): ByteArray = context.assets.readBytes(SCRIPT)

    override fun listModules(): List<String> = context.assets.list(MODULES_DIR)?.toList() ?: emptyList()
    override fun absoluteModulePath(path: String): String = "${MODULES_DIR}/${path.trimStart('/')}"

    override fun readModuleSources(module: JSModule.Asset): Sequence<ByteArray> = module.paths.asSequence().map { context.assets.readBytes(it)}
    override fun readModuleManifest(module: String): ByteArray = context.assets.readBytes("${absoluteModulePath(module)}/manifest.json")

    companion object {
        private const val SCRIPT = "public/assets/native/isolated_modules/isolated-modules.script.js"
        private const val JAVA_SCRIPT_ENGINE_UTILS = "public/assets/native/isolated_modules/isolated-modules.js-engine-android.js"

        private const val MODULES_DIR = "public/assets/protocol_modules"
    }
}

private class InternalFilesExplorer(private val context: Context) : DynamicSourcesExplorer<JSModule.External> {
    private val modulesDir: File
        get() = File(context.filesDir, MODULES_DIR)

    override fun listModules(): List<String> = modulesDir.list()?.toList() ?: emptyList()
    override fun absoluteModulePath(path: String): String = File(modulesDir, path).absolutePath

    override fun readModuleSources(module: JSModule.External): Sequence<ByteArray> = module.paths.asSequence().map { File(it).readBytes() }
    override fun readModuleManifest(module: String): ByteArray = File(modulesDir, "$module/manifest.json").readBytes()

    companion object {
        private const val MODULES_DIR = "protocol_modules"
    }
}