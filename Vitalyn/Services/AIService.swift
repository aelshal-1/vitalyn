import Foundation
import Security
import SwiftUI
import UIKit
import VitalynCore

/// Stores the Claude API key in the Keychain (never in the code, the repo or UserDefaults).
enum KeychainStore {
    private static let service = "com.elshal.vitalyn"
    private static let account = "anthropic-api-key"

    static func read() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func save(_ key: String) {
        delete()
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let attributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
            kSecValueData as String: Data(trimmed.utf8)
        ]
        SecItemAdd(attributes as CFDictionary, nil)
    }

    static func delete() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}

/// Entry point for every Claude call in the app.
@MainActor
@Observable
final class AIService {
    private(set) var hasKey: Bool = KeychainStore.read() != nil
    var model: ClaudeModel {
        didSet { UserDefaults.standard.set(model.rawValue, forKey: "claude-model") }
    }

    init() {
        model = UserDefaults.standard.string(forKey: "claude-model").flatMap(ClaudeModel.init(rawValue:)) ?? .opus
    }

    func setKey(_ key: String) {
        KeychainStore.save(key)
        hasKey = KeychainStore.read() != nil
    }

    func removeKey() {
        KeychainStore.delete()
        hasKey = false
    }

    var maskedKey: String {
        guard let key = KeychainStore.read(), key.count > 12 else { return "" }
        return String(key.prefix(10)) + "…" + String(key.suffix(4))
    }

    /// `onText` gets the reply so far as it streams in, on the main actor.
    func run<T: Decodable & Sendable>(_ type: T.Type, _ request: AIRequest, onText: ((String) -> Void)? = nil) async throws -> T {
        guard let key = KeychainStore.read() else { throw ClaudeError.missingAPIKey }
        let client = ClaudeClient(apiKey: key, model: model)
        let stream = AsyncStream<String>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let forward = Task { @MainActor in
            for await text in stream.stream { onText?(text) }
        }
        defer {
            stream.continuation.finish()
            forward.cancel()
        }
        return try await client.generate(type, request, onText: onText == nil ? nil : { stream.continuation.yield($0) })
    }
}

enum ImageTools {
    /// JPEG sized for vision requests: long edge ≤ 1600 px keeps detail and request size reasonable.
    static func jpeg(_ image: UIImage, maxDimension: CGFloat = 1600, quality: CGFloat = 0.8) -> Data? {
        let size = image.size
        let scale = min(1, maxDimension / max(size.width, size.height))
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let resized = UIGraphicsImageRenderer(size: target, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: target))
        }
        return resized.jpegData(compressionQuality: quality)
    }

    static func thumbnail(_ data: Data?, maxDimension: CGFloat = 400) -> UIImage? {
        guard let data, let image = UIImage(data: data) else { return nil }
        guard let jpeg = jpeg(image, maxDimension: maxDimension, quality: 0.7) else { return image }
        return UIImage(data: jpeg)
    }
}
