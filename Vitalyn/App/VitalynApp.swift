import SwiftUI
import WidgetKit
import VitalynCore

@main
struct VitalynApp: App {
    init() {
        // Until phase 1 brings real data, widgets read the sample day.
        SnapshotStore.save(SampleData.today)
        WidgetCenter.shared.reloadAllTimelines()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(\.layoutDirection, .rightToLeft)
                .tint(VColor.primary)
        }
    }
}
