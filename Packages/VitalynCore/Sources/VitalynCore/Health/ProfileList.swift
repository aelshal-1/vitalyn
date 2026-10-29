import Foundation

/// Lists (medications, allergies, other conditions) stored as one text field, one item per line.
public enum ProfileList {
    public static func items(_ text: String) -> [String] {
        text.split(whereSeparator: { $0 == "\n" || $0 == ";" || $0 == "،" })
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    public static func text(_ items: [String]) -> String {
        items.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }.joined(separator: "\n")
    }
}
