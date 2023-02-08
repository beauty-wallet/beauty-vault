//
//  JSEvaluator.swift
//  App
//
//  Created by Julia Samol on 06.02.23.
//

import Foundation
import Capacitor
import WebKit

class JSEvaluator {
    private let webViewEnv: WebViewEnvironment
    private let modulesManager: ModulesManager
    
    init(fileExplorer: FileExplorer) {
        self.webViewEnv = .init(fileExplorer: fileExplorer)
        self.modulesManager = .init()
    }
    
    func evaluateLoadModules(_ modules: [JSModule], for protocolType: JSProtocolType?) async throws -> [String: Any] {
        let modulesJSON = try await modules.asyncMap { module in
            let json = try await self.webViewEnv.run(.load(.init(protocolType: protocolType)), in: module)
            try await self.modulesManager.registerModule(module, json: json)
            
            return json
        }
        
        return ["modules": modulesJSON]
    }
    
    func evaluateCallOfflineProtocolMethod(
        _ name: String,
        ofProtocol protocolIdentifier: String,
        withArgs args: JSArray?
    ) async throws -> [String: Any] {
        let modules = await modulesManager.modules
        guard let module = modules[protocolIdentifier] else {
            throw Error.moduleNotFound(protocolIdentifier)
        }
        
        return try await webViewEnv.run(
            .callMethod(
                .offlineProtocol(
                    .init(name: name, args: args, protocolIdentifier: protocolIdentifier)
                )
            ),
            in: module
        )
    }
    
    func evaluateCallOnlineProtocolMethod(
        _ name: String,
        ofProtocol protocolIdentifier: String,
        onNetwork networkID: String?,
        withArgs args: JSArray?
    ) async throws -> [String: Any] {
        let modules = await modulesManager.modules
        guard let module = modules[protocolIdentifier] else {
            throw Error.moduleNotFound(protocolIdentifier)
        }
        
        return try await webViewEnv.run(
            .callMethod(
                .onlineProtocol(
                    .init(name: name, args: args, protocolIdentifier: protocolIdentifier, networkID: networkID)
                )
            ),
            in: module
        )
    }
    
    func evaluateCallBlockExplorerMethod(
        _ name: String,
        ofProtocol protocolIdentifier: String,
        onNetwork networkID: String?,
        withArgs args: JSArray?
    ) async throws -> [String: Any] {
        let modules = await modulesManager.modules
        guard let module = modules[protocolIdentifier] else {
            throw Error.moduleNotFound(protocolIdentifier)
        }
        
        return try await webViewEnv.run(
            .callMethod(
                .blockExplorer(
                    .init(name: name, args: args, protocolIdentifier: protocolIdentifier, networkID: networkID)
                )
            ),
            in: module
        )
    }
    
    func evaluateCallV3SerializerCompanionMethod(
        _ name: String,
        ofModule moduleIdentifier: String,
        withArgs args: JSArray?
    ) async throws -> [String: Any] {
        let modules = await modulesManager.modules
        guard let module = modules[moduleIdentifier] else {
            throw Error.moduleNotFound(moduleIdentifier)
        }
        
        return try await webViewEnv.run(
            .callMethod(
                .v3SerializerCompanion(
                    .init(name: name, args: args)
                )
            ),
            in: module
        )
    }
    
    func destroy() async throws {
        try await webViewEnv.destroy()
    }
    
    private actor ModulesManager {
        private(set) var modules: [String: JSModule] = [:]
        
        func registerModule(_ module: JSModule, json: [String: Any]) throws {
            modules[module.identifier] = module
            
            guard let protocols = json["protocols"] as? [Any] else {
                throw Error.invalidJSON
            }
            
            try protocols.forEach { `protocol` in
                guard let `protocol` = `protocol` as? [String: Any], let identifier = `protocol`["identifier"] as? String else {
                    throw Error.invalidJSON
                }
                
                modules[identifier] = module
            }
        }
    }
    
    enum Error: Swift.Error {
        case moduleNotFound(String)
        case invalidJSON
    }
}

private extension JSArray {
    func replaceNullWithUndefined() -> JSArray {
        map {
            if $0 is NSNull {
                return JSUndefined.value
            } else {
                return $0
            }
        }
    }
}
