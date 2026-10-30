import Foundation

public enum ClaudeModel: String, Codable, Sendable, CaseIterable, Identifiable {
    case opus = "claude-opus-5-5"
    case sonnet = "claude-sonnet-5-5"
    case haiku = "claude-haiku-5-5"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .opus: "Claude Opus 5.5"
        case .sonnet: "Claude Sonnet 5.5"
        case .haiku: "Claude Haiku 5.5"
        }
    }

    public var noteAr: String {
        switch self {
        case .opus: "الأدق — الافتراضي"
        case .sonnet: "أسرع وأرخص"
        case .haiku: "الأرخص — للمهام البسيطة"
        }
    }

    public var noteEn: String {
        switch self {
        case .opus: "Most accurate — default"
        case .sonnet: "Faster and cheaper"
        case .haiku: "Cheapest — simple tasks"
        }
    }

    /// Server-side refusal fallback is available on Opus and Sonnet (not Haiku).
    var supportsFallback: Bool { self != .haiku }
}

public enum ClaudeEffort: String, Sendable {
    case low, medium, high
}

/// One piece of a user message.
public enum ClaudeContent: Sendable, Equatable {
    case text(String)
    case image(Data, mediaType: String)
    case pdf(Data)

    var json: JSONValue {
        switch self {
        case let .text(text):
            return ["type": "text", "text": .string(text)]
        case let .image(data, mediaType):
            return [
                "type": "image",
                "source": ["type": "base64", "media_type": .string(mediaType), "data": .string(data.base64EncodedString())]
            ]
        case let .pdf(data):
            return [
                "type": "document",
                "source": ["type": "base64", "media_type": "application/pdf", "data": .string(data.base64EncodedString())]
            ]
        }
    }
}

public struct ClaudeMessage: Sendable, Equatable {
    public enum Role: String, Sendable { case user, assistant }
    public var role: Role
    public var content: [ClaudeContent]

    public init(role: Role, content: [ClaudeContent]) {
        self.role = role
        self.content = content
    }

    public static func user(_ content: [ClaudeContent]) -> ClaudeMessage { .init(role: .user, content: content) }
    public static func assistant(_ text: String) -> ClaudeMessage { .init(role: .assistant, content: [.text(text)]) }
}

public enum ClaudeError: Error, LocalizedError, Equatable {
    case missingAPIKey
    case http(status: Int, message: String)
    case refusal
    case truncated
    case emptyResponse
    case invalidOutput(String)

    public var errorDescription: String? {
        switch self {
        case .missingAPIKey:
            "ضيف مفتاح Claude API من الإعدادات · Add your Claude API key in Settings"
        case let .http(status, message):
            "خطأ من Claude (\(status)) · Claude error (\(status)): \(message)"
        case .refusal:
            "Claude رفض الطلب ده · Claude declined this request"
        case .truncated:
            "الرد اتقطع — جرّب تاني · The reply was cut off — try again"
        case .emptyResponse:
            "مفيش رد من Claude · No reply from Claude"
        case let .invalidOutput(detail):
            "الرد مش مفهوم · Couldn't read the reply: \(detail)"
        }
    }
}

/// Minimal Messages API client over URLSession (there is no official Swift SDK).
public struct ClaudeClient: Sendable {
    public static let endpoint = URL(string: "https://api.anthropic.com/v1/messages")!
    public static let apiVersion = "2026-10-21"
    static let fallbackBeta = "server-side-fallback-2026-07-01"

    public let apiKey: String
    public let model: ClaudeModel
    let session: URLSession

    public init(apiKey: String, model: ClaudeModel = .opus, session: URLSession = .shared) {
        self.apiKey = apiKey
        self.model = model
        self.session = session
    }

    /// Sends a request constrained to `schema` and decodes the JSON reply.
    public func generate<T: Decodable>(
        _ type: T.Type,
        system: String,
        messages: [ClaudeMessage],
        schema: JSONValue,
        effort: ClaudeEffort = .medium,
        maxTokens: Int = 16000
    ) async throws -> T {
        let text = try await send(system: system, messages: messages, schema: schema, effort: effort, maxTokens: maxTokens)
        do {
            return try JSONDecoder().decode(T.self, from: Data(text.utf8))
        } catch {
            throw ClaudeError.invalidOutput(String(describing: error))
        }
    }

    /// Streams the reply (server-sent events), so long replies keep the connection alive
    /// instead of sitting silent until they finish, and returns its text.
    public func send(
        system: String,
        messages: [ClaudeMessage],
        schema: JSONValue?,
        effort: ClaudeEffort = .medium,
        maxTokens: Int = 16000
    ) async throws -> String {
        guard !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw ClaudeError.missingAPIKey }
        let request = makeRequest(system: system, messages: messages, schema: schema, effort: effort, maxTokens: maxTokens)
        let (bytes, response) = try await session.bytes(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            var data = Data()
            for try await byte in bytes { data.append(byte) }
            return try Self.parse(data: data, status: status)
        }
        var stream = StreamAccumulator()
        for try await line in bytes.lines {
            try stream.consume(line)
        }
        return try stream.finish()
    }

    /// The reply text from a whole streamed response body (as saved by a background download).
    public static func parseStream(data: Data, status: Int) throws -> String {
        guard (200..<300).contains(status) else { return try parse(data: data, status: status) }
        var stream = StreamAccumulator()
        for line in String(decoding: data, as: UTF8.self).split(whereSeparator: \.isNewline) {
            try stream.consume(String(line))
        }
        return try stream.finish()
    }

    /// The reply text so far in a partial streamed body, for progress while it downloads.
    public static func partialStreamText(_ data: Data) -> String {
        var stream = StreamAccumulator()
        for line in String(decoding: data, as: UTF8.self).split(whereSeparator: \.isNewline) {
            try? stream.consume(String(line))
        }
        return stream.text
    }

    public func makeRequest(
        system: String,
        messages: [ClaudeMessage],
        schema: JSONValue?,
        effort: ClaudeEffort,
        maxTokens: Int
    ) -> URLRequest {
        var request = URLRequest(url: Self.endpoint)
        request.httpMethod = "POST"
        request.timeoutInterval = 240
        request.setValue("application/json", forHTTPHeaderField: "content-type")
        request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue(Self.apiVersion, forHTTPHeaderField: "anthropic-version")
        if model.supportsFallback {
            request.setValue(Self.fallbackBeta, forHTTPHeaderField: "anthropic-beta")
        }
        request.httpBody = Data(body(system: system, messages: messages, schema: schema, effort: effort, maxTokens: maxTokens).serialized().utf8)
        return request
    }

    func body(system: String, messages: [ClaudeMessage], schema: JSONValue?, effort: ClaudeEffort, maxTokens: Int) -> JSONValue {
        var outputConfig: [(String, JSONValue)] = [("effort", .string(effort.rawValue))]
        if let schema {
            outputConfig.append(("format", ["type": "json_schema", "schema": schema]))
        }
        var pairs: [(String, JSONValue)] = [
            ("model", .string(model.rawValue)),
            ("max_tokens", .number(Double(maxTokens))),
            ("system", .array([["type": "text", "text": .string(system), "cache_control": ["type": "ephemeral"]]])),
            ("messages", .array(messages.map { message -> JSONValue in
                ["role": .string(message.role.rawValue), "content": .array(message.content.map(\.json))]
            })),
            ("output_config", .object(outputConfig)),
            ("stream", true)
        ]
        if model.supportsFallback {
            pairs.append(("fallbacks", "default"))
        }
        return .object(pairs)
    }

    /// Extracts the reply text from a Messages API response, turning API failures into `ClaudeError`.
    public static func parse(data: Data, status: Int) throws -> String {
        let json = (try? JSONValue.parse(data)) ?? .null
        guard (200..<300).contains(status) else {
            let message = json["error"]?["message"]?.stringValue ?? String(decoding: data.prefix(300), as: UTF8.self)
            throw ClaudeError.http(status: status, message: message)
        }
        let stopReason = json["stop_reason"]?.stringValue
        if stopReason == "refusal" { throw ClaudeError.refusal }
        let texts = (json["content"]?.arrayValue ?? [])
            .filter { $0["type"] == .string("text") }
            .compactMap { $0["text"]?.stringValue }
        let text = texts.joined()
        if stopReason == "max_tokens" { throw ClaudeError.truncated }
        guard !text.isEmpty else { throw ClaudeError.emptyResponse }
        return text
    }
}

/// Collects the reply text from a Messages API event stream.
struct StreamAccumulator {
    private(set) var text = ""
    private(set) var stopReason: String?

    mutating func consume(_ line: String) throws {
        guard line.hasPrefix("data:") else { return }
        let payload = line.dropFirst(5).trimmingCharacters(in: .whitespaces)
        guard let event = try? JSONValue.parse(Data(payload.utf8)) else { return }
        switch event["type"]?.stringValue {
        case "content_block_delta":
            if event["delta"]?["type"] == .string("text_delta"), let piece = event["delta"]?["text"]?.stringValue {
                text += piece
            }
        case "message_delta":
            if let reason = event["delta"]?["stop_reason"]?.stringValue { stopReason = reason }
        case "error":
            let kind = event["error"]?["type"]?.stringValue
            let message = event["error"]?["message"]?.stringValue ?? "Stream error"
            throw ClaudeError.http(status: kind == "overloaded_error" ? 529 : 500, message: message)
        default:
            break
        }
    }

    func finish() throws -> String {
        if stopReason == "refusal" { throw ClaudeError.refusal }
        if stopReason == "max_tokens" { throw ClaudeError.truncated }
        guard !text.isEmpty else { throw ClaudeError.emptyResponse }
        return text
    }
}
