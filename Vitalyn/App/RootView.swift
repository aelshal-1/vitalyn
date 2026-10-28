import SwiftUI

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

/// iPhone gets the bilingual bottom bar; iPad gets a sidebar.
struct RootView: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var tab: AppTab = .today

    var body: some View {
        if sizeClass == .regular {
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
            screen(for: tab)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    BilingualTabBar(selection: $tab)
                }
        }
    }

    @ViewBuilder
    private func screen(for tab: AppTab) -> some View {
        switch tab {
        case .today:
            TodayView()
        case .food:
            PlaceholderView(ar: "الأكل", en: "Food", phaseAr: "تصوير الوجبات والمنتجات — المرحلة 2", phaseEn: "Meal photos & products — phase 2", symbol: "camera")
        case .coach:
            PlaceholderView(ar: "المدرب", en: "Coach", phaseAr: "الشات والصوت مع Claude — المرحلة 3", phaseEn: "Chat & voice with Claude — phase 3", symbol: "sparkles")
        case .progress:
            PlaceholderView(ar: "التقدم", en: "Progress", phaseAr: "InBody والأفاتار 3D — المراحل 1 و 5", phaseEn: "InBody & 3D avatar — phases 1 and 5", symbol: "figure.stand")
        case .me:
            PlaceholderView(ar: "ملفي الصحي", en: "Health profile", phaseAr: "الملف الصحي و Apple Health — المرحلة 1", phaseEn: "Profile & Apple Health — phase 1", symbol: "heart.text.square")
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

struct PlaceholderView: View {
    let ar: String
    let en: String
    let phaseAr: String
    let phaseEn: String
    let symbol: String

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: symbol)
                .font(.system(size: 40, weight: .light))
                .foregroundStyle(VColor.primary)
                .frame(width: 88, height: 88)
                .background(VColor.primarySoft, in: Circle())
            BiText(ar: ar, en: en, size: 24, alignment: .center)
            BiText(ar: phaseAr, en: phaseEn, size: 14, weight: 400, color: VColor.inkSecondary, alignment: .center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(VColor.ground)
    }
}
