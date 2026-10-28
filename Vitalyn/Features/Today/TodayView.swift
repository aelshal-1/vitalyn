import SwiftUI
import VitalynCore

struct TodayView: View {
    var snapshot: TodaySnapshot = SampleData.today
    var meals: [SampleData.Meal] = SampleData.meals

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                caloriesCard
                activityRow
                coachCard
                mealsSection
                snapButton
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .background(VColor.ground)
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                BiCaption(ar: snapshot.date.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(Locale(identifier: "ar"))),
                          en: snapshot.date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated).locale(Locale(identifier: "en"))))
                BiText(ar: greeting.ar, en: greeting.en, size: 26, enColor: VColor.inkSecondary, enSize: 15)
            }
            Spacer()
            Image(systemName: "person")
                .font(.system(size: 18))
                .foregroundStyle(VColor.ink)
                .frame(width: 44, height: 44)
                .background(VColor.surface, in: Circle())
                .overlay(Circle().stroke(VColor.line))
                .accessibilityLabel("الملف الشخصي — Profile")
        }
    }

    private var greeting: (ar: String, en: String) {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<12: return ("صباح الخير", "Good morning")
        case 12..<18: return ("مساء الخير", "Good afternoon")
        default: return ("مساء النور", "Good evening")
        }
    }

    private var caloriesCard: some View {
        Card {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    BiText(ar: "سعرات النهارده", en: "Calories today")
                    Spacer()
                    BiCaption(ar: "ماشي صح", en: "On track", color: VColor.primaryDark)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(VColor.primarySoft, in: Capsule())
                }
                HStack(spacing: 20) {
                    ZStack {
                        ProgressRing(progress: snapshot.calorieProgress, color: VColor.primary)
                        VStack(spacing: 2) {
                            Text(VFormat.number(snapshot.caloriesLeft))
                                .font(VFont.readex(38, weight: 600, relativeTo: .largeTitle))
                                .foregroundStyle(VColor.ink)
                            BiText(ar: "سعرة باقية", en: "kcal left", size: 12, weight: 400, color: VColor.inkSecondary, enSize: 11, alignment: .center)
                        }
                    }
                    .frame(width: 160, height: 160)

                    VStack(alignment: .leading, spacing: 12) {
                        stat(ar: "الهدف", en: "Target", value: snapshot.targets.calories, color: VColor.ink)
                        stat(ar: "أكلت", en: "Eaten", value: snapshot.eatenCalories, color: VColor.ink)
                        stat(ar: "نشاط", en: "Active", value: snapshot.activeCalories, color: VColor.energy)
                    }
                }
                Divider().overlay(VColor.track)
                VStack(spacing: 14) {
                    MacroBar(ar: "بروتين", en: "Protein", value: snapshot.proteinGrams, goal: snapshot.targets.proteinGrams, color: VColor.protein)
                    MacroBar(ar: "كارب", en: "Carbs", value: snapshot.carbsGrams, goal: snapshot.targets.carbsGrams, color: VColor.carbs)
                    MacroBar(ar: "دهون", en: "Fat", value: snapshot.fatGrams, goal: snapshot.targets.fatGrams, color: VColor.fat)
                }
            }
        }
    }

    private func stat(ar: String, en: String, value: Int, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            BiCaption(ar: ar, en: en)
            Text(VFormat.number(value))
                .font(VFont.readex(18, weight: 600))
                .foregroundStyle(color)
        }
        .accessibilityElement(children: .combine)
    }

    private var activityRow: some View {
        HStack(spacing: 10) {
            tile(symbol: "figure.walk", tint: VColor.primary, value: VFormat.number(snapshot.steps), ar: "خطوة", en: "Steps")
            tile(symbol: "flame", tint: VColor.energy, value: "42", ar: "دقيقة تمرين", en: "Workout min")
            tile(symbol: "drop", tint: VColor.water, value: "\(snapshot.waterGlasses)/\(snapshot.waterGoal)", ar: "كوباية مية", en: "Water cups")
        }
    }

    private func tile(symbol: String, tint: Color, value: String, ar: String, en: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: symbol).font(.system(size: 20)).foregroundStyle(tint)
            Text(value)
                .font(VFont.readex(20, weight: 600))
                .foregroundStyle(VColor.ink)
                .environment(\.layoutDirection, .leftToRight)
            BiText(ar: ar, en: en, size: 12, weight: 400, color: VColor.inkSecondary, enSize: 11)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private var coachCard: some View {
        Card(background: VColor.night, padding: 20) {
            VStack(alignment: .leading, spacing: 12) {
                Label {
                    BiCaption(ar: "مدرب Vitalyn", en: "Vitalyn Coach", size: 13, color: VColor.mint)
                } icon: {
                    Image(systemName: "sparkles").foregroundStyle(VColor.mint)
                }
                Text("فاضلك 58 جم بروتين. اقتراح للعشا: 200 جم فراخ مشوية + سلطة خضرا + نص كوباية رز — حوالي 520 سعرة.")
                    .font(VFont.readex(15))
                    .foregroundStyle(VColor.nightText)
                    .lineSpacing(4)
                Text("58 g protein to go. Dinner idea: 200 g grilled chicken, green salad, half a cup of rice — about 520 kcal.")
                    .font(VFont.readex(13))
                    .foregroundStyle(VColor.nightMuted)
                    .lineSpacing(3)
                HStack(spacing: 10) {
                    Button {} label: {
                        BiText(ar: "سجّلها", en: "Log it", size: 14, color: VColor.night, enColor: VColor.night, enSize: 10, alignment: .center)
                            .padding(.horizontal, 18)
                            .frame(minHeight: 48)
                            .background(VColor.mint, in: Capsule())
                    }
                    Button {} label: {
                        BiText(ar: "اقترح غيرها", en: "Suggest another", size: 14, weight: 400, color: VColor.nightText, enColor: VColor.nightMuted, enSize: 10, alignment: .center)
                            .padding(.horizontal, 18)
                            .frame(minHeight: 48)
                            .overlay(Capsule().stroke(VColor.nightLine))
                    }
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var mealsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            BiText(ar: "وجبات النهارده", en: "Today’s meals")
            ForEach(meals) { meal in
                HStack(spacing: 12) {
                    Image(systemName: "fork.knife")
                        .foregroundStyle(VColor.ink)
                        .frame(width: 52, height: 52)
                        .background(VColor.primarySoft, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text(meal.slotAr).font(VFont.readex(14, weight: 600))
                            Text("\(meal.slotEn) · \(meal.time)").font(VFont.readex(11)).foregroundStyle(VColor.muted)
                            Spacer()
                            Text("\(meal.calories) kcal").font(VFont.readex(14, weight: 600))
                        }
                        Text(meal.itemsAr).font(VFont.readex(12)).foregroundStyle(VColor.inkSecondary).lineLimit(1)
                        Text(meal.itemsEn).font(VFont.readex(11)).foregroundStyle(VColor.muted).lineLimit(1)
                    }
                }
                .padding(12)
                .background(VColor.surface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
                .accessibilityElement(children: .combine)
            }
        }
    }

    private var snapButton: some View {
        Button {} label: {
            HStack(spacing: 12) {
                Image(systemName: "camera").font(.system(size: 20))
                BiText(ar: "صوّر وجبة", en: "Snap a meal", size: 16, color: .white, enColor: Color(hex: 0xCDEBDD), enSize: 11)
            }
            .frame(maxWidth: .infinity, minHeight: 60)
            .foregroundStyle(.white)
            .background(VColor.primary, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    TodayView().environment(\.layoutDirection, .rightToLeft)
}
