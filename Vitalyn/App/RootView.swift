import SwiftUI
import SwiftData

enum AppTab: String, CaseIterable, Identifiable {
    case today, food, coach, progress, me

    var id: String { rawValue }

    var ar: String {
        switch self {
        case .today: "اليوم"
        case .food: "الأكل"
        case .coach: "المدرب"
        case .progress: "التقدم"
        case .me: "أنا"
        }
    }

    var en: String {
        switch self {
        case .today: "Today"
        case .food: "Food"
        case .coach: "Coach"
        case .progress: "Progress"
        case .me: "Me"
        }
    }

    var symbol: String {
        switch self {
        case .today: "house"
        case .food: "fork.knife"
        case .coach: "sparkles"
        case .progress: "chart.bar"
        case .me: "person"
        }
    }
}

/// iPhone gets the bilingual bottom bar; iPad gets a sidebar. First launch shows onboarding.
struct RootView: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Query private var profiles: [UserProfile]
    @State private var tab: AppTab = .today

    var body: some View {
        if profiles.isEmpty {
            OnboardingView { tab = .today }
        } else if sizeClass == .regular {
            NavigationSplitView {
                List(AppTab.allCases) { item in
                    Button {
                        tab = item
                    } label: {
                        Label {
                            BiText(ar: item.ar, en: item.en, size: 15, weight: tab == item ? 600 : 400,
                                   color: tab == item ? VColor.primaryDark : VColor.ink)
                        } icon: {
                            Image(systemName: item.symbol)
                        }
                    }
                    .listRowBackground(tab == item ? VColor.primarySoft : Color.clear)
                }
                .navigationTitle("Vitalyn")
            } detail: {
                screen(for: tab)
            }
        } else {
            // Stacked (not an overlay inset) so scroll views end above the bar and nothing hides under it.
            VStack(spacing: 0) {
                screen(for: tab)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                BilingualTabBar(selection: $tab)
            }
            .ignoresSafeArea(.keyboard, edges: .bottom)
        }
    }

    @ViewBuilder
    private func screen(for tab: AppTab) -> some View {
        switch tab {
        case .today:
            NavigationStack {
                TodayView(openProfile: { self.tab = .me })
                    .toolbar(.hidden, for: .navigationBar)
            }
        case .food:
            FoodView()
        case .coach:
            CoachView()
        case .progress:
            ProgressHubView()
        case .me:
            ProfileView()
        }
    }
}

struct BilingualTabBar: View {
    @Binding var selection: AppTab

    var body: some View {
        HStack(spacing: 0) {
            ForEach(AppTab.allCases) { item in
                let selected = item == selection
                Button {
                    selection = item
                } label: {
                    VStack(spacing: 2) {
                        Image(systemName: item.symbol)
                            .font(.system(size: 20, weight: .regular))
                            .frame(height: 24)
                        Text(item.ar).font(VFont.readex(11, weight: 500, relativeTo: .caption2))
                        Text(item.en).font(VFont.readex(9, relativeTo: .caption2))
                    }
                    .foregroundStyle(selected ? VColor.primary : VColor.muted)
                    .frame(maxWidth: .infinity, minHeight: 52)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(item.ar) — \(item.en)")
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(.horizontal, 6)
        .padding(.top, 6)
        .background(alignment: .top) {
            VColor.surface
                .overlay(alignment: .top) { Rectangle().fill(VColor.line).frame(height: 1) }
                .ignoresSafeArea(edges: .bottom)
        }
    }
}
