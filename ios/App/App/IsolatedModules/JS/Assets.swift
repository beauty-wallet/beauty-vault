//
//  Assets.swift
//  App
//
//  Created by Julia Samol on 07.02.23.
//

import Foundation

enum Assets {
    static let url: URL = Bundle.main.url(forResource: "public", withExtension: nil)!.appendingPathComponent("assets")
    private static let script = "native/isolated_modules/isolated-modules.script.js"
    
    static func readString(at pathComponent: String) throws -> String {
        let url = Self.url.appendingPathComponent(pathComponent)
        return try Files.readString(at: url.path)
    }
    
    static func readScript() throws -> String {
        try Self.readString(at: Self.script)
    }
}
