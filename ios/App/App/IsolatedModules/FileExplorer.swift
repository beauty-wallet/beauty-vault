//
//  FileExplorer.swift
//  App
//
//  Created by Julia Samol on 08.02.23.
//

import Foundation

// MARK: FileExplorer

struct FileExplorer {
    static let shared: FileExplorer = .init()
    
    private let assets: AssetsExplorer
    private let document: DocumentExplorer
    
    init(fileManager: FileManager = .default) {
        self.assets = .init(fileManager: fileManager)
        self.document = .init(fileManager: fileManager)
    }
    
    func readIsolatedModulesScript() throws -> String {
        try assets.readIsolatedModulesScript()
    }
    
    func loadAssetModules() throws -> [JSModule] {
        try loadModules(explorer: assets).map { .asset($0) }
    }
    
    func loadExternalModules() throws -> [JSModule] {
        try loadModules(explorer: document).map { .external($0) }
    }
    
    func readModuleSources(_ module: JSModule) throws -> [String] {
        switch module {
        case .asset(let asset):
            return try assets.readModuleSources(asset)
        case .external(let external):
            return try document.readModuleSources(external)
        }
    }
    
    private func loadModules<T: JSModuleProtocol, E: DynamicSourcesExplorer>(explorer: E) throws -> [T] where E.T == T {
        try explorer.listModules().map { module in
            let jsonDecoder = JSONDecoder()
            guard let manifestData = try explorer.readModuleManifest(module).data(using: .utf8) else {
                throw JSError.invalidJSON
            }
            
            let manifest = try jsonDecoder.decode(ModuleManifest.self, from: manifestData)
            let namespace = manifest.src?.namespace
            let preferredEnvironment = manifest.jsenv?.ios ?? .webview
            let paths = try manifest.include.map { try explorer.absoluteModulePath(ofPath: "\(module)/\($0)") }
            
            return T.init(identifier: module, namespace: namespace, preferredEnvironment: preferredEnvironment, paths: paths)
        }
    }
}

// MARK: AssetsExplorer

private struct AssetsExplorer: DynamicSourcesExplorer {
    typealias T = JSModule.Asset
    
    static let assetsURL: URL = Bundle.main.url(forResource: "public", withExtension: nil)!.appendingPathComponent("assets")
    private static let script: String = "native/isolated_modules/isolated-modules.script.js"
    private static let modulesDir: String = "protocol_modules"
    
    private let fileManager: FileManager
    
    init(fileManager: FileManager) {
        self.fileManager = fileManager
    }
    
    func readIsolatedModulesScript() throws -> String {
        try readString(atPath: Self.script)
    }
    
    func listModules() throws -> [String] {
        let url = Self.assetsURL.appendingPathComponent(Self.modulesDir)
        return try fileManager.contentsOfDirectory(atPath: url.path)
    }
    
    func absoluteModulePath(ofPath path: String) throws -> String {
        "\(Self.modulesDir)/\(path)"
    }
    
    func readModuleSources(_ module: JSModule.Asset) throws -> [String] {
        try module.paths.lazy.map { try readString(atPath: $0) }
    }
    
    func readModuleManifest(_ module: String) throws -> String {
        try readString(atPath: "\(absoluteModulePath(ofPath: module))/manifest.json")
    }
    
    private func readString(atPath pathComponent: String) throws -> String {
        let url = Self.assetsURL.appendingPathComponent(pathComponent)
        return try fileManager.stringContents(atPath: url.path)
    }
}

// MARK: DocumentExplorer

private struct DocumentExplorer: DynamicSourcesExplorer {
    typealias T = JSModule.External
    
    private static let modulesDir: String = "protocol_modules"
    
    private let fileManager: FileManager
    
    init(fileManager: FileManager) {
        self.fileManager = fileManager
    }
    
    func listModules() throws -> [String] {
        guard let url = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
            return []
        }
        
        let modulesDirPath = url.appendingPathComponent(Self.modulesDir).path
        guard fileManager.fileExists(atPath: modulesDirPath) else {
            return []
        }
        
        return try fileManager.contentsOfDirectory(atPath: modulesDirPath)
    }
    
    func absoluteModulePath(ofPath path: String) throws -> String {
        return "\(Self.modulesDir)/\(path)"
    }
    
    func readModuleSources(_ module: JSModule.External) throws -> [String] {
        try module.paths.lazy.map { try readString(atPath: $0) }
    }
    
    func readModuleManifest(_ module: String) throws -> String {
        try readString(atPath: "\(absoluteModulePath(ofPath: module))/manifest.json")
    }
    
    private func readString(atPath path: String) throws -> String {
        let url = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!.appendingPathComponent(path)
        return try fileManager.stringContents(atPath: url.path)
    }
}

// MARK: DynamicSourcesExplorer

private protocol DynamicSourcesExplorer {
    associatedtype T
    
    func listModules() throws -> [String]
    func absoluteModulePath(ofPath path: String) throws -> String
    
    func readModuleSources(_ module: T) throws -> [String]
    func readModuleManifest(_ module: String) throws -> String
}

// MARK: Extensions

private extension FileManager {
    func stringContents(atPath path: String) throws -> String {
        guard let data = contents(atPath: path),
              let string = String(data: data, encoding: .utf8) else {
            throw Error.invalidPath
        }
        
        return string
    }
}

private enum Error: Swift.Error {
    case invalidPath
}
