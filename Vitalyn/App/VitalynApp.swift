import SwiftUI
import SwiftData
import VitalynCore

@main
struct VitalynApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @State private var health = HealthService()
    @State private var ai = AIService()
    private let container = VitalynApp.makeContainer()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(\.layoutDirection, .rightToLeft)
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
