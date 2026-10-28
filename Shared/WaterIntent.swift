import AppIntents
import WidgetKit
import VitalynCore

/// "+1 glass" from the interactive water widget (runs without opening the app).
struct AddWaterIntent: AppIntent {
    static let title: LocalizedStringResource = "Add a glass of water"
    static let description = IntentDescription("ضيف كوباية مية · Adds one glass of water to today.")

    func perform() async throws -> some IntentResult {
        WaterStore.add(1)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}
