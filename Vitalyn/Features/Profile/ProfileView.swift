import SwiftUI
import SwiftData
import VitalynCore

struct ProfileView: View {
    @Environment(\.modelContext) private var context
    @Environment(HealthService.self) private var health
    @Environment(AIService.self) private var ai
    @Query(sort: \UserProfile.createdAt) private var profiles: [UserProfile]
    @Query(sort: \InBodyScan.date) private var scans: [InBodyScan]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    BiText(ar: "ملفي الصحي", en: "My health profile", size: 26, enSize: 15)
                    if let profile = profiles.first {
                        TargetsPreview(profile: profile, scans: scans)
                        BasicsSection(profile: profile)
                        GoalSection(profile: profile, currentWeightKg: AppData.currentWeight(profile: profile, scans: scans, health: health), bodyFatPercent: scans.last?.effectiveBodyFatPercent)
                        HealthSection(profile: profile)
                    }
                    ConnectionsSection()
                    BiText(ar: "Vitalyn بيساعدك تتابع وتنظم، لكنه مش بديل عن الدكتور. أي تغيير في الأدوية أو الأكل مع مرض مزمن لازم يكون بموافقة دكتورك.", en: "Vitalyn helps you track and plan — it doesn’t replace your doctor. Check medication or diet changes with them.", size: 12, weight: 400, color: VColor.inkSecondary)
                }
                .padding(20)
                .frame(maxWidth: 680)
                .frame(maxWidth: .infinity)
            }
            .background(VColor.ground)
            .toolbar(.hidden, for: .navigationBar)
            .onDisappear {
                try? context.save()
                AppData.publishSnapshot(context, health: health)
            }
        }
    }
}

struct ConnectionsSection: View {
    @Environment(HealthService.self) private var health
    @Environment(AIService.self) private var ai
    @State private var keyDraft = ""
    @State private var editingKey = false
    @AppStorage("notifications-on") private var notificationsOn = true

    var body: some View {
        @Bindable var ai = ai
        Card {
            VStack(alignment: .leading, spacing: 14) {
                SectionTitle(ar: "الربط والإعدادات", en: "Connections & settings")

                row(symbol: "heart.fill", tint: Color(hex: 0xC2264B), ar: "Apple Health", en: "Apple Health") {
                    if health.requested {
                        BiCaption(ar: "متصل", en: "Connected", color: VColor.primaryDark)
                    } else {
                        Button("اربط · Connect") { Task { await health.requestAuthorization() } }
                            .font(VFont.readex(13, weight: 600))
                    }
                }

                Divider()

                row(symbol: "sparkles", tint: VColor.ink, ar: "مفتاح Claude API", en: "Claude API key") {
                    if ai.hasKey && !editingKey {
                        Button("غيّر · Change") { editingKey = true }
                            .font(VFont.readex(13, weight: 600))
                    }
                }
                if ai.hasKey && !editingKey {
                    Text(ai.maskedKey)
                        .font(.system(.footnote, design: .monospaced))
                        .foregroundStyle(VColor.muted)
                        .environment(\.layoutDirection, .leftToRight)
                } else {
                    HStack {
                        SecureField("sk-ant-…", text: $keyDraft)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .font(.system(.body, design: .monospaced))
                            .environment(\.layoutDirection, .leftToRight)
                        Button("احفظ · Save") {
                            ai.setKey(keyDraft)
                            keyDraft = ""
                            editingKey = false
                        }
                        .font(VFont.readex(13, weight: 600))
                        .disabled(keyDraft.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    .padding(12)
                    .background(VColor.ground, in: RoundedRectangle(cornerRadius: 12))
                }

                VStack(alignment: .leading, spacing: 8) {
                    BiCaption(ar: "الموديل", en: "Model")
                    ForEach(ClaudeModel.allCases) { model in
                        Button {
                            ai.model = model
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 1) {
                                    Text(model.displayName).font(VFont.readex(14, weight: 600))
                                    Text("\(model.noteAr) · \(model.noteEn)").font(VFont.readex(11)).foregroundStyle(VColor.muted)
                                }
                                Spacer()
                                Image(systemName: ai.model == model ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(ai.model == model ? VColor.primary : VColor.line)
                            }
                            .foregroundStyle(VColor.ink)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }

                Divider()

                Toggle(isOn: $notificationsOn) {
                    BiText(ar: "تذكير الصبح وبالليل", en: "Morning & evening reminders", size: 14, weight: 500)
                }
                .tint(VColor.primary)
                .onChange(of: notificationsOn) { _, on in
                    if on { NotificationService.scheduleDefaults() } else { NotificationService.cancelAll() }
                }
            }
        }
    }

    private func row<Trailing: View>(symbol: String, tint: Color, ar: String, en: String, @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).foregroundStyle(tint).frame(width: 24)
            BiText(ar: ar, en: en, size: 14, weight: 500)
            Spacer()
            trailing()
        }
    }
}
