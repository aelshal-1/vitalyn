import SwiftUI
import SwiftData
import UserNotifications
import VitalynCore

@main
struct VitalynApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @State private var health = HealthService()
    @State private var ai = AIService()
    private let container = VitalynApp.makeContainer()

    init() {
        UNUserNotificationCenter.current().delegate = NotificationDelegate.shared
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(\.layoutDirection, .rightToLeft)
                // The design is light-only for now; without this, dark mode turns input text white.
                .preferredColorScheme(.light)
                // Western digits (1,770 not ١٬٧٧٠) everywhere, as in the design; dates pass their own locale.
                .environment(\.locale, Locale(identifier: "en_US"))
                .tint(VColor.primary)
                .environment(health)
                .environment(ai)
                .task { await refresh() }
        }
        .modelContainer(container)
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { Task { await refresh() } }
        }
    }

    @MainActor
    private func refresh() async {
        await health.refresh()
        AppData.publishSnapshot(container.mainContext, health: health)
        await NotificationService.refreshMotivation(context: container.mainContext, health: health, ai: ai)
    }

    /// On-device store. Models are CloudKit-compatible so iPhone/iPad sync can be switched on later.
    static func makeContainer() -> ModelContainer {
        let schema = SwiftData.Schema(VitalynSchema.models)
        do {
            return try ModelContainer(for: schema, configurations: ModelConfiguration(schema: schema, cloudKitDatabase: .none))
        } catch {
            fatalError("Could not open the Vitalyn store: \(error)")
        }
    }
}
