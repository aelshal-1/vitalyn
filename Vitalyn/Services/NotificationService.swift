import Foundation
import SwiftData
import UIKit
import UserNotifications
import VitalynCore

/// Daily reminders, a week of motivational messages, and achievement alerts (all bilingual, all local).
enum NotificationService {
    static let motivationOnKey = "motivation-on"
    static let motivationHourKey = "motivation-hour"
    static let motivationMinuteKey = "motivation-minute"
    private static let earnedBadgesKey = "earned-badges"
    private static let aiWeekKey = "motivation-ai-week"
    private static let aiMessagesKey = "motivation-ai-messages"

    static func scheduleDefaults() {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            center.removePendingNotificationRequests(withIdentifiers: ["morning", "evening"])
            add(id: "morning", hour: 8, minute: 30,
                title: "صباح الخير · Good morning",
                body: "شوف هدف النهارده واقتراح الفطار · See today’s target and a breakfast idea")
            add(id: "evening", hour: 21, minute: 0,
                title: "سجّلت أكلك؟ · Logged your meals?",
                body: "صوّر أو اكتب اللي أكلته عشان المتابعة تبقى دقيقة · Snap or type what you ate to keep tracking accurate")
        }
    }

    static func cancelAll() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["morning", "evening"])
    }

    private static func add(id: String, hour: Int, minute: Int, title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        let trigger = UNCalendarNotificationTrigger(dateMatching: DateComponents(hour: hour, minute: minute), repeats: true)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }

    // MARK: Motivation

    static var motivationEnabled: Bool {
        UserDefaults.standard.object(forKey: motivationOnKey) as? Bool ?? true
    }

    static var motivationTime: (hour: Int, minute: Int) {
        let defaults = UserDefaults.standard
        let hour = defaults.object(forKey: motivationHourKey) as? Int ?? 13
        let minute = defaults.object(forKey: motivationMinuteKey) as? Int ?? 0
        return (hour, minute)
    }

    /// Replaces the next 7 days of motivational notifications. Uses this week's Claude-written messages when
    /// available (fetched at most once a week), otherwise the built-in ones personalised with progress.
    @MainActor
    static func refreshMotivation(context: ModelContext, health: HealthService, ai: AIService) async {
        let center = UNUserNotificationCenter.current()
        let ids = (0..<7).map { "motivation-\($0)" }
        center.removePendingNotificationRequests(withIdentifiers: ids)
        guard motivationEnabled, let profile = AppData.profile(context) else { return }

        let scans = AppData.scans(context)
        let current = AppData.currentWeight(profile: profile, scans: scans, health: health)
        let start = scans.first?.weightKg ?? profile.startWeightKg
        let streak = ProgressMath.streak(loggedDays: AppData.loggedDays(context))
        let targets = AppData.targets(profile: profile, scans: scans, health: health)
        let input = MotivationInput(
            name: profile.name,
            streakDays: streak,
            lostKg: max(0, start - current),
            remainingKg: max(0, current - profile.goalWeightKg),
            proteinTarget: targets.proteinGrams,
            stepsGoal: profile.stepsGoal
        )
        let day = Calendar.current.ordinality(of: .day, in: .era, for: Date()) ?? 0
        var messages = Motivation.messages(for: input, count: 7, seed: day)

        if ai.hasKey, let userContext = AppData.userContext(context, health: health) {
            let week = Calendar.current.component(.weekOfYear, from: Date())
            let defaults = UserDefaults.standard
            if defaults.integer(forKey: aiWeekKey) == week,
               let data = defaults.data(forKey: aiMessagesKey),
               let cached = try? JSONDecoder().decode([MotivationMessage].self, from: data), cached.count >= 7 {
                messages = cached
            } else if let batch = try? await ai.run(MotivationBatch.self, AITasks.motivationWeek(context: userContext, streakDays: streak, lostKg: input.lostKg)),
                      batch.messages.count >= 7 {
                messages = Array(batch.messages.prefix(7))
                defaults.set(week, forKey: aiWeekKey)
                defaults.set(try? JSONEncoder().encode(messages), forKey: aiMessagesKey)
            }
        }

        let time = motivationTime
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        for (index, message) in messages.prefix(7).enumerated() {
            guard let dayStart = calendar.date(byAdding: .day, value: index, to: today),
                  let fire = calendar.date(bySettingHour: time.hour, minute: time.minute, second: 0, of: dayStart),
                  fire > Date() else { continue }
            let content = UNMutableNotificationContent()
            content.title = "\(message.titleAr) · \(message.titleEn)"
            content.body = "\(message.bodyAr)\n\(message.bodyEn)"
            content.sound = .default
            let parts = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: fire)
            let trigger = UNCalendarNotificationTrigger(dateMatching: parts, repeats: false)
            try? await center.add(UNNotificationRequest(identifier: "motivation-\(index)", content: content, trigger: trigger))
        }
    }

    // MARK: Background results

    /// Where tapping a notification takes the user.
    enum OpenTarget: String {
        case plan, coach
    }

    /// Set when a notification is tapped; the root view opens that screen and clears it.
    static let openTargetKey = "open-target"

    /// Tells the user a background request finished. Skipped while they're in the app, where the screen updates itself.
    @MainActor
    static func announce(id: String, title: String, body: String, open: OpenTarget) {
        guard UIApplication.shared.applicationState != .active else { return }
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.userInfo = ["open": open.rawValue]
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
    }

    // MARK: Achievements

    /// Sends a notification for each badge earned since the last check (the first check only records the baseline).
    @MainActor
    static func announceNewBadges(context: ModelContext, health: HealthService) {
        guard let profile = AppData.profile(context) else { return }
        let scans = AppData.scans(context)
        let meals = (try? context.fetchCount(FetchDescriptor<MealEntry>())) ?? 0
        let badges = Achievements.badges(for: AchievementInput(
            scanCount: scans.count,
            startWeightKg: scans.first?.weightKg ?? profile.startWeightKg,
            currentWeightKg: AppData.currentWeight(profile: profile, scans: scans, health: health),
            streakDays: ProgressMath.streak(loggedDays: AppData.loggedDays(context)),
            bestStepsDay: health.bestStepsDay,
            loggedMealCount: meals,
            goalWeightKg: profile.goalWeightKg
        ))
        let earned = Set(badges.filter(\.achieved).map(\.id))
        let defaults = UserDefaults.standard
        guard let known = defaults.stringArray(forKey: earnedBadgesKey) else {
            defaults.set(Array(earned), forKey: earnedBadgesKey)
            return
        }
        let fresh = badges.filter { $0.achieved && !known.contains($0.id) }
        defaults.set(Array(earned.union(known)), forKey: earnedBadgesKey)
        for badge in fresh {
            let content = UNMutableNotificationContent()
            content.title = "🏆 إنجاز جديد · New achievement"
            content.body = "\(badge.ar) · \(badge.en)\nبرافو عليك! · Well done!"
            content.sound = .default
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 2, repeats: false)
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "badge-\(badge.id)", content: content, trigger: trigger))
        }
    }
}

/// Shows notifications as banners even while Vitalyn is open, and opens the right screen when one is tapped.
final class NotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationDelegate()

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        guard let target = response.notification.request.content.userInfo["open"] as? String else { return }
        UserDefaults.standard.set(target, forKey: NotificationService.openTargetKey)
    }
}
