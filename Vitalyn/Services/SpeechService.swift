import AVFoundation
import Foundation
import Speech
import SwiftUI

/// On-device dictation (Egyptian Arabic by default) and spoken replies.
@MainActor
@Observable
final class SpeechService {
    var transcript = ""
    var isRecording = false
    var errorMessage: String?
    var localeIdentifier = "ar-EG"

    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private let synthesizer = AVSpeechSynthesizer()

    func toggle() {
        if isRecording { stop() } else { Task { await start() } }
    }

    func start() async {
        errorMessage = nil
        transcript = ""
        let speechStatus = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0) }
        }
        guard speechStatus == .authorized else {
            errorMessage = "اسمح بالتعرف على الكلام من الإعدادات · Allow speech recognition in Settings"
            return
        }
        let micAllowed = await AVAudioApplication.requestRecordPermission()
        guard micAllowed else {
            errorMessage = "اسمح بالمايك من الإعدادات · Allow the microphone in Settings"
            return
        }
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: localeIdentifier)), recognizer.isAvailable else {
            errorMessage = "التعرف على الكلام مش متاح دلوقتي · Speech recognition is unavailable"
            return
        }

        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker])
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            if recognizer.supportsOnDeviceRecognition { request.requiresOnDeviceRecognition = true }
            self.request = request

            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.removeTap(onBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
                request.append(buffer)
            }
            engine.prepare()
            try engine.start()
            isRecording = true

            task = recognizer.recognitionTask(with: request) { [weak self] result, error in
                let text = result?.bestTranscription.formattedString
                let finished = error != nil || (result?.isFinal ?? false)
                Task { @MainActor in
                    guard let self else { return }
                    if let text { self.transcript = text }
                    if finished { self.stop() }
                }
            }
        } catch {
            errorMessage = error.localizedDescription
            stop()
        }
    }

    func stop() {
        guard isRecording || task != nil else { return }
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        isRecording = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func speak(_ text: String, language: String = "ar-SA") {
        synthesizer.stopSpeaking(at: .immediate)
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: language)
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        synthesizer.speak(utterance)
    }

    func stopSpeaking() {
        synthesizer.stopSpeaking(at: .immediate)
    }
}

/// Mic button bound to a SpeechService, writing the transcript into `text`.

struct DictationButton: View {
    @Bindable var speech: SpeechService
    @Binding var text: String

    var body: some View {
        Button {
            speech.toggle()
        } label: {
            Image(systemName: speech.isRecording ? "stop.fill" : "mic.fill")
                .font(.system(size: 18))
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(speech.isRecording ? VColor.energy : VColor.primary, in: Circle())
        }
        .accessibilityLabel(speech.isRecording ? "وقّف — Stop" : "اتكلم — Dictate")
        .onChange(of: speech.transcript) { _, value in
            if !value.isEmpty { text = value }
        }
    }
}
