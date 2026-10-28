import Foundation

/// Builders for structured-output JSON schemas.
/// Every object lists all its properties as required and sets `additionalProperties: false`,
/// as structured outputs require. Optional values are expressed with `nullable`.
public enum JSONSchema {
    public static func object(_ properties: [(String, JSONValue)], description: String? = nil) -> JSONValue {
        var pairs: [(String, JSONValue)] = [("type", "object")]
        if let description { pairs.append(("description", .string(description))) }
        pairs.append(("properties", .object(properties)))
        pairs.append(("required", .array(properties.map { .string($0.0) })))
        pairs.append(("additionalProperties", false))
        return .object(pairs)
    }

    public static func string(_ description: String? = nil) -> JSONValue {
        typed("string", description)
    }

    public static func number(_ description: String? = nil) -> JSONValue {
        typed("number", description)
    }

    public static func integer(_ description: String? = nil) -> JSONValue {
        typed("integer", description)
    }

    public static func boolean(_ description: String? = nil) -> JSONValue {
        typed("boolean", description)
    }

    public static func array(_ items: JSONValue, description: String? = nil) -> JSONValue {
        var pairs: [(String, JSONValue)] = [("type", "array"), ("items", items)]
        if let description { pairs.append(("description", .string(description))) }
        return .object(pairs)
    }

    public static func enumeration(_ values: [String], description: String? = nil) -> JSONValue {
        var pairs: [(String, JSONValue)] = [("type", "string"), ("enum", .array(values.map { .string($0) }))]
        if let description { pairs.append(("description", .string(description))) }
        return .object(pairs)
    }

    public static func nullable(_ schema: JSONValue) -> JSONValue {
        .object([("anyOf", .array([schema, .object([("type", "null")])]))])
    }

    private static func typed(_ type: String, _ description: String?) -> JSONValue {
        var pairs: [(String, JSONValue)] = [("type", .string(type))]
        if let description { pairs.append(("description", .string(description))) }
        return .object(pairs)
    }

    /// Returns the problems that would make the API reject a schema (empty when valid).
    public static func problems(in schema: JSONValue, path: String = "$") -> [String] {
        guard case let .object(pairs) = schema else { return [] }
        var issues: [String] = []
        let keys = pairs.map(\.0)
        for banned in ["minimum", "maximum", "minLength", "maxLength", "multipleOf", "minItems", "maxItems"] where keys.contains(banned) {
            issues.append("\(path): unsupported keyword \(banned)")
        }
        if schema["type"] == .string("object") {
            if schema["additionalProperties"] != .bool(false) {
                issues.append("\(path): additionalProperties must be false")
            }
            if case let .object(properties)? = schema["properties"] {
                let required = Set(schema["required"]?.arrayValue?.compactMap(\.stringValue) ?? [])
                for (name, child) in properties {
                    if !required.contains(name) { issues.append("\(path).\(name): not required") }
                    issues += problems(in: child, path: "\(path).\(name)")
                }
            } else {
                issues.append("\(path): object without properties")
            }
        }
        if let items = schema["items"] {
            issues += problems(in: items, path: "\(path)[]")
        }
        if let anyOf = schema["anyOf"]?.arrayValue {
            for (index, option) in anyOf.enumerated() {
                issues += problems(in: option, path: "\(path)|\(index)")
            }
        }
        return issues
    }
}
