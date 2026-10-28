import SwiftUI
import SwiftData
import Charts
import VitalynCore

/// Progress tab: weight trend, InBody, body/avatar, activity, achievements.
struct ProgressHubView: View {
    @Environment(HealthService.self) private var health
    @Query(sort: \InBodyScan.date) private var scans: [InBodyScan]
    @Query(sort: \UserProfile.createdAt) private var profiles: [UserProfile]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    BiText(ar: "التقدم", en: "Progress", size: 26, enSize: 15)
                    weightCard
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                        tile(ar: "جسمي 3D", en: "My body", symbol: "figure.stand", tint: VColor.night) { BodyView() }
                        tile(ar: "InBody", en: "Body scans", symbol: "doc.text.magnifyingglass", tint: VColor.primary) { InBodyListView() }
                        tile(ar: "النشاط", en: "Activity", symbol: "figure.walk", tint: VColor.electric) { ActivityView() }
                        tile(ar: "الإنجازات", en: "Achievements", symbol: "trophy", tint: Color(hex: 0xC2410C)) { AchievementsView() }
                    }
                }
                .padding(20)
                .frame(maxWidth: 720)
                .frame(maxWidth: .infinity)
            }
            .background(VColor.ground)
            .toolbar(.hidden, for: .navigationBar)
        }
    }

    private struct WeightPoint: Identifiable {
        let id = UUID()
        let date: Date
        let kg: Double
        let source: String
    }

    private var points: [WeightPoint] {
        let fromScans = scans.map { WeightPoint(date: $0.date, kg: $0.weightKg, source: "InBody") }
        let fromHealth = health.weights.map { WeightPoint(date: $0.date, kg: $0.value, source: "Health") }
        return (fromScans + fromHealth).sorted { $0.date < $1.date }
    }

    private var weightCard: some View {
        let data = points
        let start = data.first?.kg ?? profiles.first?.startWeightKg
        let current = data.last?.kg ?? profiles.first?.startWeightKg
        let change = (current ?? 0) - (start ?? 0)
        return Card {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .firstTextBaseline) {
                    SectionTitle(ar: "الوزن", en: "Weight")
                    Spacer()
                    if let current {
                        Text("\(VFormat.decimal(current)) kg").font(VFont.readex(22, weight: 600))
                    }
                }
                .environment(\.layoutDirection, .rightToLeft)
                if data.count > 1 {
                    Chart {
                        ForEach(data) { point in
                            LineMark(x: .value("Date", point.date), y: .value("kg", point.kg))
                                .foregroundStyle(VColor.primary)
                                .interpolationMethod(.monotone)
                            if point.source == "InBody" {
                                PointMark(x: .value("Date", point.date), y: .value("kg", point.kg))
                                    .foregroundStyle(VColor.night)
                            }
                        }
                        if let goal = profiles.first?.goalWeightKg {
                            RuleMark(y: .value("Goal", goal))
                                .foregroundStyle(VColor.primary.opacity(0.5))
                                .lineStyle(StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                                .annotation(position: .top, alignment: .leading) {
                                    Text("Goal · الهدف \(VFormat.decimal(goal))").font(VFont.readex(10)).foregroundStyle(VColor.primaryDark)
                                }
                        }
                    }
                    .chartYScale(domain: .automatic(includesZero: false))
                    .frame(height: 200)
                    .environment(\.layoutDirection, .leftToRight)
                    BiCaption(ar: "التغيير \(change > 0 ? "+" : "")\(VFormat.decimal(change)) كجم", en: "Change \(change > 0 ? "+" : "")\(VFormat.decimal(change)) kg", size: 13, color: change <= 0 ? VColor.primaryDark : VColor.energy)
                } else {
                    BiText(ar: "ضيف InBody أو سجّل وزنك في Apple Health عشان تشوف المنحنى.", en: "Add an InBody scan or log weight in Apple Health to see your trend.", size: 13, weight: 400, color: VColor.inkSecondary)
                }
            }
        }
    }

    private func tile<Destination: View>(ar: String, en: String, symbol: String, tint: Color, @ViewBuilder destination: @escaping () -> Destination) -> some View {
        NavigationLink {
            destination()
        } label: {
            VStack(alignment: .leading, spacing: 14) {
                Image(systemName: symbol).font(.system(size: 26))
                BiText(ar: ar, en: en, size: 15, color: .white, enColor: .white.opacity(0.85))
            }
            .foregroundStyle(.white)
            .padding(16)
            .frame(maxWidth: .infinity, minHeight: 120, alignment: .topLeading)
            .background(tint, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
