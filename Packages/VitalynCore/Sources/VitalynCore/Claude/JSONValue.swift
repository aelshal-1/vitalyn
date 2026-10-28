import Foundation

/// A JSON value that keeps object keys in the order they were written.
/// Order matters for structured-output schemas: Claude fills fields in schema order.
public indirect enum JSONValue: Sendable, Equatable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case null
    case array([JSONValue])
    case object([(String, JSONValue)])

    public static func == (lhs: JSONValue, rhs: JSONValue) -> Bool {
        switch (lhs, rhs) {
        case let (.string(a), .string(b)): return a == b
        case let (.number(a), .number(b)): return a == b
        case let (.bool(a), .bool(b)): return a == b
        case (.null, .null): return true
        case let (.array(a), .array(b)): return a == b
        case let (.object(a), .object(b)):
            return a.count == b.count && zip(a, b).allSatisfy { $0.0 == $1.0 && $0.1 == $1.1 }
        default: return false
        }
    }

    public subscript(key: String) -> JSONValue? {
        guard case let .object(pairs) = self else { return nil }
        return pairs.first { $0.0 == key }?.1
    }

    public var stringValue: String? {
        if case let .string(value) = self { return value }
        return nil
    }

    public var arrayValue: [JSONValue]? {
        if case let .array(value) = self { return value }
        return nil
    }

    /// Compact JSON text.
    public func serialized() -> String {
        var out = ""
        write(into: &out)
        return out
    }

    private func write(into out: inout String) {
        switch self {
        case let .string(value):
            Self.writeString(value, into: &out)
        case let .number(value):
            if value.isFinite, value == value.rounded(), abs(value) < 1e15 {
                out += String(Int64(value))
            } else if value.isFinite {
                out += String(value)
            } else {
                out += "null"
            }
        case let .bool(value):
            out += value ? "true" : "false"
        case .null:
            out += "null"
        case let .array(values):
            out += "["
            for (index, value) in values.enumerated() {
                if index > 0 { out += "," }
                value.write(into: &out)
            }
            out += "]"
        case let .object(pairs):
            out += "{"
            for (index, pair) in pairs.enumerated() {
                if index > 0 { out += "," }
                Self.writeString(pair.0, into: &out)
                out += ":"
                pair.1.write(into: &out)
            }
            out += "}"
        }
    }

    private static func writeString(_ value: String, into out: inout String) {
        out += "\""
        for scalar in value.unicodeScalars {
            switch scalar {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            default:
                if scalar.value < 0x20 {
                    out += String(format: "\\u%04x", scalar.value)
                } else {
                    out.unicodeScalars.append(scalar)
                }
            }
        }
        out += "\""
    }

    /// Parses JSON text. Object key order follows `JSONSerialization` and is not guaranteed.
    public static func parse(_ data: Data) throws -> JSONValue {
        let object = try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])
        return from(object)
    }

    static func from(_ any: Any) -> JSONValue {
        switch any {
        case let value as String: return .string(value)
        case let value as NSNumber:
            if CFGetTypeID(value) == CFBooleanGetTypeID() { return .bool(value.boolValue) }
            return .number(value.doubleValue)
        case let value as [Any]: return .array(value.map(from))
        case let value as [String: Any]: return .object(value.map { ($0.key, from($0.value)) })
        default: return .null
        }
    }
}

extension JSONValue: ExpressibleByStringLiteral, ExpressibleByIntegerLiteral, ExpressibleByFloatLiteral, ExpressibleByBooleanLiteral, ExpressibleByArrayLiteral, ExpressibleByDictionaryLiteral {
    public init(stringLiteral value: String) { self = .string(value) }
    public init(integerLiteral value: Int) { self = .number(Double(value)) }
    public init(floatLiteral value: Double) { self = .number(value) }
    public init(booleanLiteral value: Bool) { self = .bool(value) }
    public init(arrayLiteral elements: JSONValue...) { self = .array(elements) }
    public init(dictionaryLiteral elements: (String, JSONValue)...) { self = .object(elements) }
}
