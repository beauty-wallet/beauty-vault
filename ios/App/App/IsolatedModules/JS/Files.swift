//
//  Files.swift
//  App
//
//  Created by Julia Samol on 07.02.23.
//

import Foundation

enum Files {
    static func readString(at path: String) throws -> String {
        guard let data = FileManager.default.contents(atPath: path),
              let string = String(data: data, encoding: .utf8) else {
            throw Error.invalidPath
        }
        
        return string
    }
    
    private enum Error: Swift.Error {
        case invalidPath
    }
}
