import Foundation
import UserNotifications

/// Daily local reminders (bilingual).
enum NotificationService {
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
}
