//
//  JSModule.swift
//  App
//
//  Created by Julia Samol on 06.02.23.
//

import Foundation
import Capacitor

// MARK: JSModule

enum JSModule: JSModuleProtocol {
    case asset(Asset)
    case external(External)
    
    var identifier: String {
        switch self {
        case .asset(let asset):
            return asset.identifier
        case .external(let external):
            return external.identifier
        }
    }
    
    var paths: [String] {
        switch self {
        case .asset(let asset):
            return asset.paths
        case .external(let external):
            return external.paths
        }
    }
    
    func readSources() throws -> [String] {
        switch self {
        case .asset(let asset):
            return try asset.readSources()
        case .external(let external):
            return try external.readSources()
        }
    }
    
    struct Asset: JSModuleProtocol {
        let identifier: String
        let paths: [String]
        
        func readSources() throws -> [String] {
            try paths.lazy.map { try Assets.readString(at: $0) }
        }
    }
    
    struct External: JSModuleProtocol {
        let identifier: String
        let paths: [String]
        
        func readSources() throws -> [String] {
            try paths.lazy.map { try Files.readString(at: $0) }
        }
    }
}

protocol JSModuleProtocol {
    var identifier: String { get }
    var paths: [String] { get }
    
    func readSources() throws -> [String]
}

// MARK: JSProtocolType

enum JSProtocolType: String, JSONConvertible {
    case offline
    case online
    case full
    
    func toJSONString() throws -> String {
        return try rawValue.toJSONString()
    }
}

// MARK: JSCallMethodTarget

enum JSCallMethodTarget: String, JSONConvertible {
    case offlineProtocol
    case onlineProtocol
    case blockExplorer
    case v3SerializerCompanion
    
    func toJSONString() throws -> String {
        return try rawValue.toJSONString()
    }
}

// MARK: JSModuleAction

enum JSModuleAction: JSONConvertible {
    private static let loadType: String = "load"
    private static let callMethodType: String = "callMethod"
    
    case load(Load)
    case callMethod(CallMethod)
    
    func toJSONString() throws -> String {
        switch self {
        case .load(let load):
            return try load.toJSONString()
        case .callMethod(let callMethod):
            return try callMethod.toJSONString()
        }
    }
    
    struct Load: JSONConvertible {
        let protocolType: JSProtocolType?
        
        func toJSONString() throws -> String {
            return """
                {
                    "type": "\(JSModuleAction.loadType)",
                    "protocolType": \(try protocolType?.toJSONString() ?? (try JSUndefined.value.toJSONString()))
                }
            """
        }
    }
    
    enum CallMethod: JSONConvertible {
        case offlineProtocol(OfflineProtocol)
        case onlineProtocol(OnlineProtocol)
        case blockExplorer(BlockExplorer)
        case v3SerializerCompanion(V3SerializerCompanion)
        
        func toJSONString() throws -> String {
            switch self {
            case .offlineProtocol(let offlineProtocol):
                return try offlineProtocol.toJSONString()
            case .onlineProtocol(let onlineProtocol):
                return try onlineProtocol.toJSONString()
            case .blockExplorer(let blockExplorer):
                return try blockExplorer.toJSONString()
            case .v3SerializerCompanion(let v3SerializerCompanion):
                return try v3SerializerCompanion.toJSONString()
            }
        }
        
        private static func toJSONStringWithPartial(
            target: JSCallMethodTarget,
            name: String,
            args: JSArray?,
            partial partialJSON: String
        ) throws -> String {
            let args = try args?.replaceNullWithUndefined().toJSONString() ?? "[]"
            let objectJSON = """
                {
                    "type": "\(JSModuleAction.callMethodType)",
                    "target": \(try target.toJSONString()),
                    "method": "\(name)",
                    "args": \(args)
                }
            """
            
            guard let objectData = objectJSON.data(using: .utf8),
                  let object = try JSONSerialization.jsonObject(with: objectData) as? [String: Any],
                  let partialData = partialJSON.data(using: .utf8),
                  let partial = try JSONSerialization.jsonObject(with: partialData) as? [String: Any] else {
                throw JSError.invalidJSON
            }
            
            let merged = object.merging(partial, uniquingKeysWith: { $1 })
            guard JSONSerialization.isValidJSONObject(merged) else {
                throw JSError.invalidJSON
            }
            
            let data = try JSONSerialization.data(withJSONObject: merged, options: [])
            return .init(data: data, encoding: .utf8)!
        }
        
        struct OfflineProtocol: JSONConvertible {
            let target: JSCallMethodTarget = .offlineProtocol
            let name: String
            let args: JSArray?
            let protocolIdentifier: String
            
            func toJSONString() throws -> String {
                let partial: String = """
                    {
                        "protocolIdentifier": "\(protocolIdentifier)"
                    }
                """
                
                return try CallMethod.toJSONStringWithPartial(target: target, name: name, args: args, partial: partial)
            }
        }
        
        struct OnlineProtocol: JSONConvertible {
            let target: JSCallMethodTarget = .onlineProtocol
            let name: String
            let args: JSArray?
            let protocolIdentifier: String
            let networkID: String?
            
            func toJSONString() throws -> String {
                let partial: String = """
                    {
                        "protocolIdentifier": "\(protocolIdentifier)",
                        "networkId": \(try networkID?.toJSONString() ?? (try JSUndefined.value.toJSONString()))
                    }
                """
                
                return try CallMethod.toJSONStringWithPartial(target: target, name: name, args: args, partial: partial)
            }
        }
        
        struct BlockExplorer: JSONConvertible {
            let target: JSCallMethodTarget = .blockExplorer
            let name: String
            let args: JSArray?
            let protocolIdentifier: String
            let networkID: String?
            
            func toJSONString() throws -> String {
                let partial: String = """
                    {
                        "protocolIdentifier": "\(protocolIdentifier)",
                        "networkId": \(try networkID?.toJSONString() ?? (try JSUndefined.value.toJSONString()))
                    }
                """
                
                return try CallMethod.toJSONStringWithPartial(target: target, name: name, args: args, partial: partial)
            }
        }
        
        struct V3SerializerCompanion: JSONConvertible {
            let target: JSCallMethodTarget = .v3SerializerCompanion
            let name: String
            let args: JSArray?
            
            func toJSONString() throws -> String {
                return try CallMethod.toJSONStringWithPartial(target: target, name: name, args: args, partial: "{}")
            }
        }
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
