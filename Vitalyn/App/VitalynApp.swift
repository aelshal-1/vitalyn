import SwiftUI
import SwiftData
import UserNotifications
import VitalynCore

@main
struct VitalynApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase
    @State private var health = HealthService()
    @State private var ai = AIService()
    private let container = AppStore.container

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
        BackgroundAI.shared.health = health
        await BackgroundAI.shared.restore()
        await health.refresh()
        AppData.publishSnapshot(container.mainContext, health: health)
        await NotificationService.refreshMotivation(context: container.mainContext, health: health, ai: ai)
    }
}
