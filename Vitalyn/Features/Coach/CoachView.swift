import SwiftUI
import SwiftData
import VitalynCore

struct CoachView: View {
    @Environment(\.modelContext) private var context
    @Environment(AIService.self) private var ai
    @Environment(HealthService.self) private var health
    @Query(sort: \CoachMessage.date) private var messages: [CoachMessage]

    @State private var speech = SpeechService()
    @State private var draft = ""
    @State private var speaking = false
    @State private var sending = false
    @State private var error: String?
    @State private var mealToLog: MealInput?

    private let quickPrompts: [(String, String)] = [
        ("آكل إيه دلوقتي؟", "What should I eat now?"),
        ("قيّم أسبوعي", "Review my week"),
        ("عندي عزومة بكرة", "I have a dinner party tomorrow"),
        ("اقترحلي تمرين النهارده", "Suggest a workout for today")
    ]

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                header
                chat
                inputBar
            }
            .background(VColor.ground)
            .toolbar(.hidden, for: .navigationBar)
            .sheet(item: $mealToLog) { input in MealCaptureView(input: input) }
            .onDisappear {
                speech.stop()
                speech.stopSpeaking()
            }
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: "sparkles")
                .foregroundStyle(VColor.mint)
                .frame(width: 40, height: 40)
                .background(VColor.night, in: Circle())
            BiText(ar: "مدرب Vitalyn", en: "Vitalyn Coach", size: 17)
            Spacer()
            if speaking {
                Button {
                    speech.stopSpeaking()
                    speaking = false
                } label: {
                    Image(systemName: "speaker.slash.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(VColor.ink)
                        .frame(width: 44, height: 44)
                        .background(VColor.surface, in: Circle())
                }
                .accessibilityLabel("وقّف الصوت — Stop speaking")
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }

    private var chat: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    if messages.isEmpty {
                        BiText(ar: "اسألني أي حاجة عن أكلك ورياضتك، أو قولّي عملت إيه النهارده وأنا أسجله.", en: "Ask me anything about food and exercise, or tell me what you did today and I’ll log it.", size: 14, weight: 400, color: VColor.inkSecondary)
                            .padding(.top, 8)
                    }
                    ForEach(messages) { message in
                        MessageBubble(message: message) { action in
                            perform(action)
                        }
                        .id(message.persistentModelID)
                    }
                    if sending {
                        HStack { ProgressView(); Text("بفكر… · Thinking…").font(VFont.readex(12)).foregroundStyle(VColor.muted) }
                            .id("typing")
                    }
                    if let error { ErrorBanner(message: error) }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 12)
                .frame(maxWidth: 720)
                .frame(maxWidth: .infinity)
            }
            .onChange(of: messages.count) { _, _ in
                if let last = messages.last { withAnimation { proxy.scrollTo(last.persistentModelID, anchor: .bottom) } }
            }
            .onAppear {
                if let last = messages.last { proxy.scrollTo(last.persistentModelID, anchor: .bottom) }
            }
        }
    }

    private var inputBar: some View {
        VStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(quickPrompts, id: \.1) { prompt in
                        Button { send(prompt.0) } label: {
                            VStack(spacing: 0) {
                                Text(prompt.0).font(VFont.readex(12))
                                Text(prompt.1).font(VFont.readex(10)).foregroundStyle(VColor.muted)
                            }
                            .padding(.horizontal, 12)
                            .frame(minHeight: 46)
                            .background(VColor.surface, in: RoundedRectangle(cornerRadius: 16))
                            .overlay(RoundedRectangle(cornerRadius: 16).stroke(VColor.line))
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(VColor.ink)
                    }
                }
                .padding(.horizontal, 16)
            }
            if speech.isRecording {
                Label {
                    Text("بسمعك… دوس المايك تاني عشان تبعت · Listening… tap the mic again to send")
                        .font(VFont.readex(11))
                } icon: {
                    Image(systemName: "waveform").symbolEffect(.variableColor.iterative)
                }
                .foregroundStyle(VColor.energy)
                .padding(.horizontal, 16)
            }
            if let message = speech.errorMessage {
                Text(message).font(VFont.readex(11)).foregroundStyle(VColor.energy).padding(.horizontal, 16)
            }
            HStack(alignment: .bottom, spacing: 8) {
                TextField(speech.isRecording ? "بسمعك… · Listening…" : "اكتب أو اتكلم · Type or talk", text: $draft, axis: .vertical)
                    .lineLimit(1...5)
                    .font(VFont.readex(15))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(VColor.surface, in: RoundedRectangle(cornerRadius: 22))
                    .overlay(RoundedRectangle(cornerRadius: 22).stroke(VColor.line))
                Button(action: micTapped) {
                    Image(systemName: speech.isRecording ? "stop.fill" : "mic.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(speech.isRecording ? VColor.energy : VColor.primary, in: Circle())
                }
                .accessibilityLabel(speech.isRecording ? "وقّف وابعت — Stop and send" : "اتكلم — Talk")
                .onChange(of: speech.transcript) { _, value in
                    if speech.isRecording, !value.isEmpty { draft = value }
                }
                Button { send(draft) } label: {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(VColor.ink, in: Circle())
                }
                .disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty || sending)
                .accessibilityLabel("ابعت — Send")
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 10)
        }
    }

    // MARK: Sending

    /// First tap starts dictation into the box; second tap stops and sends, and the reply is read aloud.
    private func micTapped() {
        if speech.isRecording {
            let text = speech.transcript.isEmpty ? draft : speech.transcript
            speech.stop()
            send(text, speakReply: true)
        } else {
            speech.stopSpeaking()
            speaking = false
            draft = ""
            Task { await speech.start() }
        }
    }

    private func send(_ text: String, speakReply: Bool = false) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !sending else { return }
        speech.stop()
        draft = ""
        error = nil

        let history: [ClaudeMessage] = messages.suffix(16).map { message -> ClaudeMessage in
            message.isUser
                ? .user([.text(message.textAr)])
                : .assistant(#"{"replyAr":"\#(escape(message.textAr))","replyEn":"\#(escape(message.textEn))","actions":[]}"#)
        }

        let userMessage = CoachMessage()
        userMessage.roleRaw = "user"
        userMessage.textAr = trimmed
        context.insert(userMessage)
        try? context.save()

        sending = true
        let request = AITasks.coach(history: history, userMessage: trimmed, context: AppData.userContext(context, health: health))
        Task {
            do {
                let reply = try await ai.run(CoachReply.self, request)
                let message = CoachMessage()
                message.roleRaw = "assistant"
                message.textAr = reply.replyAr
                message.textEn = reply.replyEn
                message.actionsData = try? JSONEncoder().encode(reply.actions)
                context.insert(message)
                try? context.save()
                for action in reply.actions where action.type == .logWorkout || action.type == .logWater {
                    perform(action)
                }
                if speakReply {
                    speech.speak(reply.replyAr)
                    speaking = true
                }
            } catch {
                self.error = error.localizedDescription
            }
            sending = false
        }
    }

    private func escape(_ text: String) -> String {
        JSONValue.string(text).serialized().dropFirst().dropLast().description
    }

    private func perform(_ action: CoachAction) {
        switch action.type {
        case .logWorkout:
            guard let workout = action.workout else { return }
            let entry = WorkoutEntry()
            entry.nameAr = workout.nameAr
            entry.nameEn = workout.nameEn
            entry.kind = workout.kind
            entry.minutes = workout.minutes
            entry.kcal = workout.kcal
            entry.date = Date().addingTimeInterval(-workout.minutes * 60)
            context.insert(entry)
            try? context.save()
            Task {
                entry.savedToHealth = await health.saveWorkout(kind: workout.kind, start: entry.date, minutes: workout.minutes, kcal: workout.kcal)
                try? context.save()
                AppData.publishSnapshot(context, health: health)
            }
        case .logWater:
            let glasses = action.waterGlasses ?? 1
            WaterStore.add(glasses)
            Task { await health.saveWater(glasses: glasses, date: Date()) }
            AppData.publishSnapshot(context, health: health)
        case .logMeal:
            guard let meal = action.meal else { return }
            mealToLog = .prefilled(meal, slot: nil)
        case .suggestVideos:
            break
        }
    }
}

struct MessageBubble: View {
    let message: CoachMessage
    let onAction: (CoachAction) -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        if message.isUser {
            Text(message.textAr)
                .font(VFont.readex(15))
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(VColor.primary, in: UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 18, bottomTrailingRadius: 6, topTrailingRadius: 18))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.trailing, 40)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(message.textAr).font(VFont.readex(15)).foregroundStyle(VColor.ink)
                    Text(message.textEn).font(VFont.readex(13)).foregroundStyle(VColor.inkSecondary)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(VColor.surface, in: UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 6, bottomTrailingRadius: 18, topTrailingRadius: 18))

                ForEach(Array(message.actions.enumerated()), id: \.offset) { _, action in
                    actionView(action)
                }
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
            .padding(.leading, 40)
        }
    }

    @ViewBuilder
    private func actionView(_ action: CoachAction) -> some View {
        switch action.type {
        case .logWorkout:
            if let workout = action.workout {
                confirmation(ar: "اتسجل في Apple Health · \(workout.nameAr) \(Int(workout.minutes)) د", en: "Saved to Apple Health · \(workout.nameEn) \(Int(workout.minutes)) min · \(Int(workout.kcal)) kcal")
            }
        case .logWater:
            confirmation(ar: "اتسجل \(action.waterGlasses ?? 1) كوباية مية", en: "Logged \(action.waterGlasses ?? 1) glass(es) of water")
        case .logMeal:
            if let meal = action.meal {
                Button { onAction(action) } label: {
                    BiLabel(ar: "راجع وسجّل: \(meal.titleAr)", en: "Review & log: \(meal.titleEn)", systemImage: "fork.knife", size: 13)
                }
                .buttonStyle(SecondaryButtonStyle())
            }
        case .suggestVideos:
            ForEach(action.videos) { video in
                Button { openURL(video.youtubeURL) } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "play.rectangle.fill").font(.system(size: 24)).foregroundStyle(Color(hex: 0xE02B2B))
                        BiText(ar: video.titleAr, en: video.titleEn, size: 13, weight: 500)
                        Spacer()
                        Image(systemName: "arrow.up.forward").foregroundStyle(VColor.muted)
                    }
                    .padding(12)
                    .background(VColor.surface, in: RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func confirmation(ar: String, en: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
            BiText(ar: ar, en: en, size: 12, weight: 500, color: VColor.primaryDark, enColor: VColor.primaryDark, enSize: 11)
        }
        .foregroundStyle(VColor.primaryDark)
        .padding(10)
        .background(VColor.primarySoft, in: RoundedRectangle(cornerRadius: 14))
    }
}
