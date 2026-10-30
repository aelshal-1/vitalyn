import Foundation
import SwiftData
import UIKit
import VitalynCore

/// A Claude request that keeps going after the user leaves the app. Stored in the task's description,
/// so it survives iOS relaunching the app just to hand over the result.
struct AIJob: Codable, Equatable {
    enum Kind: String, Codable { case weekPlan, coachReply }

    var kind: Kind
    /// For a weekly plan, the plan record's uid.
    var target: String = ""
    /// Read the coach's reply aloud (the question was spoken).
    var speak: Bool = false
    /// Base name of the request body and reply files.
    var file: String = UUID().uuidString
    var attempt: Int = 0
}

/// What the screens show while jobs run.
@MainActor
@Observable
final class AIJobStatus {
    /// Meals written so far, by plan uid. iOS may hand over a background reply only once it's
    /// complete, so this can stay at 0 until the end.
    var planMeals: [String: Int] = [:]
    var coachWaiting = false
    var coachError: String?
    /// A coach reply to read aloud, picked up by the coach screen.
    var replyToSpeak: String?
}

/// Sends Claude requests through a background URLSession: iOS keeps them running while the app is
/// suspended, and relaunches it if needed when they finish. Each streamed reply is written to a file,
/// then saved to the store and announced with a notification. Swiping the app away in the app switcher
/// still cancels them; that is reported as a failure the user can retry.
@MainActor
final class BackgroundAI: NSObject {
    static let shared = BackgroundAI()
    static let sessionID = "com.elshal.vitalyn.claude"

    let status = AIJobStatus()
    /// Set by the app; when iOS relaunches the app only to deliver a result, a fresh one is used.
    var health: HealthService?
    private var backgroundCompletion: (() -> Void)?
    /// Reply bytes received this launch, by task, for progress.
    private var received: [Int: Data] = [:]
    private var progressCheckedAt: [Int: Int] = [:]

    private lazy var session: URLSession = {
        let configuration = URLSessionConfiguration.background(withIdentifier: Self.sessionID)
        configuration.isDiscretionary = false
        configuration.sessionSendsLaunchEvents = true
        // Streamed replies send bytes regularly, so a long silence means the connection is gone.
        configuration.timeoutIntervalForRequest = 300
        configuration.timeoutIntervalForResource = 20 * 60
        return URLSession(configuration: configuration, delegate: self, delegateQueue: .main)
    }()

    // MARK: Starting

    func submit(_ job: AIJob, _ request: AIRequest, model: ClaudeModel) throws {
        guard let key = KeychainStore.read() else { throw ClaudeError.missingAPIKey }
        var urlRequest = ClaudeClient(apiKey: key, model: model).makeRequest(request)
        try urlRequest.httpBody?.write(to: Self.bodyURL(job), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        urlRequest.httpBody = nil
        start(job, urlRequest)
    }

    private func start(_ job: AIJob, _ request: URLRequest, after delay: TimeInterval = 0) {
        let task = session.uploadTask(with: request, fromFile: Self.bodyURL(job))
        task.taskDescription = (try? JSONEncoder().encode(job)).map { String(decoding: $0, as: UTF8.self) }
        if delay > 0 { task.earliestBeginDate = Date().addingTimeInterval(delay) }
        task.resume()
        markRunning(job)
    }

    private func markRunning(_ job: AIJob) {
        switch job.kind {
        case .weekPlan: status.planMeals[job.target] = status.planMeals[job.target] ?? 0
        case .coachReply:
            status.coachWaiting = true
            status.coachError = nil
        }
    }

    /// Reconnects to requests still running from before (after a relaunch) and marks plans whose
    /// request was lost as failed. Call when the app comes to the foreground.
    func restore() async {
        let tasks = await session.allTasks
        let running = tasks.compactMap { Self.job(of: $0) }
        running.forEach(markRunning)
        let context = AppStore.container.mainContext
        let building = (try? context.fetch(FetchDescriptor<MealPlanRecord>())) ?? []
        for record in building where record.status == .building && !running.contains(where: { $0.target == record.uid }) {
            record.status = .failed
            record.errorMessage = "اتوقف قبل ما يخلص — جرّب تاني · Stopped before it finished — try again"
            status.planMeals[record.uid] = nil
        }
        try? context.save()
    }

    /// iOS relaunched the app to deliver results; `completion` is called once they're handled.
    func handleEvents(completion: @escaping () -> Void) {
        backgroundCompletion = completion
        _ = session
    }

    // MARK: Files

    private static var folder: URL {
        let url = URL.applicationSupportDirectory.appending(path: "ai-jobs", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    private static func bodyURL(_ job: AIJob) -> URL { folder.appending(path: "\(job.file).json") }
    private static func replyURL(_ job: AIJob) -> URL { folder.appending(path: "\(job.file).sse") }

    private static func job(of task: URLSessionTask) -> AIJob? {
        guard let description = task.taskDescription else { return nil }
        return try? JSONDecoder().decode(AIJob.self, from: Data(description.utf8))
    }

    // MARK: Receiving

    fileprivate func receive(_ data: Data, task: URLSessionTask) {
        guard let job = Self.job(of: task) else { return }
        let url = Self.replyURL(job)
        if let handle = try? FileHandle(forWritingTo: url) {
            _ = try? handle.seekToEnd()
            try? handle.write(contentsOf: data)
            try? handle.close()
        } else {
            try? data.write(to: url, options: .completeFileProtectionUntilFirstUserAuthentication)
        }
        guard job.kind == .weekPlan else { return }
        // Count meals written so far, re-reading the stream only every ~16 KB.
        var bytes = received[task.taskIdentifier] ?? Data()
        bytes.append(data)
        received[task.taskIdentifier] = bytes
        guard bytes.count - (progressCheckedAt[task.taskIdentifier] ?? 0) > 16_000 else { return }
        progressCheckedAt[task.taskIdentifier] = bytes.count
        let text = ClaudeClient.partialStreamText(bytes)
        status.planMeals[job.target] = text.components(separatedBy: "\"slot\"").count - 1
    }

    fileprivate func complete(_ task: URLSessionTask, error: Error?) {
        guard let job = Self.job(of: task) else { return }
        received[task.taskIdentifier] = nil
        progressCheckedAt[task.taskIdentifier] = nil
        let data = (try? Data(contentsOf: Self.replyURL(job))) ?? Data()
        try? FileManager.default.removeItem(at: Self.replyURL(job))
        let statusCode = (task.response as? HTTPURLResponse)?.statusCode ?? 0
        let result: Result<String, Error>
        if let error {
            result = .failure(error)
        } else {
            result = Result { try ClaudeClient.parseStream(data: data, status: statusCode) }
        }

        if case let .failure(failure) = result, job.attempt < 2, let request = task.originalRequest, let delay = Self.retryDelay(for: failure) {
            var next = job
            next.attempt += 1
            start(next, request, after: delay)
            return
        }
        try? FileManager.default.removeItem(at: Self.bodyURL(job))
        switch job.kind {
        case .weekPlan: finishPlan(job, result)
        case .coachReply: finishCoach(job, result)
        }
    }

    fileprivate func finishEvents() {
        let completion = backgroundCompletion
        backgroundCompletion = nil
        completion?()
    }

    /// Seconds to wait before retrying, or nil when retrying won't help.
    private static func retryDelay(for error: Error) -> TimeInterval? {
        if let error = error as? ClaudeError, case let .http(status, _) = error {
            if status == 429 { return 30 }
            if status >= 500 { return 10 }
            return nil
        }
        // A timeout already took many minutes; fail so the user can choose to try again.
        if let error = error as? URLError, error.code != .cancelled, error.code != .timedOut { return 5 }
        return nil
    }

    private static func describe(_ error: Error) -> String {
        if let error = error as? URLError, error.code == .cancelled {
            return "اتوقف لما التطبيق اتقفل من قائمة التطبيقات — جرّب تاني · Stopped when the app was closed from the app switcher — try again"
        }
        if error is DecodingError { return ClaudeError.invalidOutput("").localizedDescription }
        return error.localizedDescription
    }

    // MARK: Results

    private func finishPlan(_ job: AIJob, _ result: Result<String, Error>) {
        status.planMeals[job.target] = nil
        let context = AppStore.container.mainContext
        let uid = job.target
        guard let record = try? context.fetch(FetchDescriptor<MealPlanRecord>(predicate: #Predicate { $0.uid == uid })).first else { return }
        do {
            let text = try result.get()
            record.plan = try JSONDecoder().decode(WeekPlanDraft.self, from: Data(text.utf8)).plan
            record.status = .ready
            record.errorMessage = ""
            try? context.save()
            NotificationService.announce(
                id: "plan-\(uid)",
                title: "جدول الأسبوع جاهز 🍽️ · Your weekly plan is ready",
                body: "الوجبات وقائمة المشتريات مستنياك · Meals and grocery list are waiting",
                open: .plan
            )
        } catch {
            record.status = .failed
            record.errorMessage = Self.describe(error)
            try? context.save()
            NotificationService.announce(
                id: "plan-\(uid)",
                title: "معرفناش نعمل الجدول · Couldn't make your plan",
                body: "افتح التطبيق وجرّب تاني · Open the app to try again",
                open: .plan
            )
        }
    }

    private func finishCoach(_ job: AIJob, _ result: Result<String, Error>) {
        status.coachWaiting = false
        let context = AppStore.container.mainContext
        do {
            let text = try result.get()
            let reply = try JSONDecoder().decode(CoachReply.self, from: Data(text.utf8))
            let message = CoachMessage()
            message.roleRaw = "assistant"
            message.textAr = reply.replyAr
            message.textEn = reply.replyEn
            message.actionsData = try? JSONEncoder().encode(reply.actions)
            context.insert(message)
            try? context.save()
            let health = self.health ?? HealthService()
            for action in reply.actions where action.type == .logWorkout || action.type == .logWater {
                CoachActions.apply(action, context: context, health: health)
            }
            if job.speak, UIApplication.shared.applicationState == .active {
                status.replyToSpeak = reply.replyAr
            }
            NotificationService.announce(id: "coach-\(job.file)", title: "رد المدرب · Coach replied", body: "\(reply.replyAr)\n\(reply.replyEn)", open: .coach)
        } catch {
            status.coachError = Self.describe(error)
            NotificationService.announce(id: "coach-\(job.file)", title: "المدرب مردّش · The coach couldn't reply", body: "افتح التطبيق وابعت تاني · Open the app and send again", open: .coach)
        }
    }
}

extension BackgroundAI: URLSessionDataDelegate {
    nonisolated func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        MainActor.assumeIsolated { receive(data, task: dataTask) }
    }

    nonisolated func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        MainActor.assumeIsolated { complete(task, error: error) }
    }

    nonisolated func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        MainActor.assumeIsolated { finishEvents() }
    }
}

/// Lets iOS hand background request results to the app.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, handleEventsForBackgroundURLSession identifier: String, completionHandler: @escaping () -> Void) {
        guard identifier == BackgroundAI.sessionID else {
            completionHandler()
            return
        }
        MainActor.assumeIsolated { BackgroundAI.shared.handleEvents(completion: completionHandler) }
    }
}
