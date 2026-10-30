import Foundation
import VitalynCore

/// Builds a weekly plan in two steps: one request picks the week's dishes and ingredient names,
/// then each day's portions and recipes are written in parallel. One request for the whole week
/// is too long to finish before the network times out.
@MainActor
enum WeekPlanner {
    /// Days written at once; more risks hitting the API key's rate limit.
    private static let parallelDays = 4

    static func build(
        ai: AIService,
        context: UserContext,
        preset: PlanPreset,
        mealsPerDay: MealsPerDay,
        start: Date,
        request: String,
        progress: @escaping (_ daysDone: Int) -> Void
    ) async throws -> WeekPlan {
        let menu = try await ai.run(WeekMenu.self, AITasks.weekMenu(context: context, preset: preset, mealsPerDay: mealsPerDay, start: start, request: request))
        progress(0)
        var details: [Int: [PlannedMeal]] = [:]
        var pending = menu.days.map(\.dayIndex)
        try await withThrowingTaskGroup(of: (Int, [PlannedMeal]).self) { group in
            func startNext() {
                guard !pending.isEmpty else { return }
                let index = pending.removeFirst()
                let dayRequest = AITasks.planDay(context: context, menu: menu, dayIndex: index, start: start, request: request)
                group.addTask { @MainActor in (index, try await runDay(ai, dayRequest)) }
            }
            for _ in 0..<parallelDays { startNext() }
            for try await (index, meals) in group {
                details[index] = meals
                progress(details.count)
                startNext()
            }
        }
        return menu.assemble(details)
    }

    /// Retries a day twice, waiting longer after a rate-limit error.
    private static func runDay(_ ai: AIService, _ request: AIRequest) async throws -> [PlannedMeal] {
        var attempt = 0
        while true {
            do {
                return try await ai.run(DayMeals.self, request).meals
            } catch {
                attempt += 1
                guard attempt < 3, (error as? ClaudeError) != .missingAPIKey else { throw error }
                var rateLimited = false
                if case .http(429, _)? = error as? ClaudeError { rateLimited = true }
                try await Task.sleep(for: .seconds(rateLimited ? 30 : 3))
            }
        }
    }
}
