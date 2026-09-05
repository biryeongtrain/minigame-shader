// QA only. Captures the chosen Minecraft process, without microphone input.
// https://developer.apple.com/documentation/screencapturekit/capturing-screen-content-in-macos
import Foundation
import ScreenCaptureKit
import AVFoundation

final class AudioOutput: NSObject, SCStreamOutput {
    let writer: AVAssetWriter
    let input: AVAssetWriterInput
    var started = false
    var samples = 0
    init(_ url: URL) throws {
        writer = try AVAssetWriter(outputURL: url, fileType: .m4a)
        input = AVAssetWriterInput(mediaType: .audio, outputSettings: [
            AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 48000,
            AVNumberOfChannelsKey: 2, AVEncoderBitRateKey: 128000])
        input.expectsMediaDataInRealTime = true
        writer.add(input)
    }
    func stream(_ stream: SCStream, didOutputSampleBuffer sample: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .audio, sample.isValid, CMSampleBufferDataIsReady(sample) else { return }
        if !started {
            guard writer.startWriting() else { return }
            writer.startSession(atSourceTime: sample.presentationTimeStamp)
            started = true
        }
        if input.isReadyForMoreMediaData && input.append(sample) { samples += CMSampleBufferGetNumSamples(sample) }
    }
}

@main struct Capture {
    static func main() async {
        do { try await capture() }
        catch { FileHandle.standardError.write(Data("Audio capture failed: \(error.localizedDescription)\n".utf8)); exit(1) }
    }
    static func capture() async throws {
        guard CommandLine.arguments.count == 4,
              let pid = Int32(CommandLine.arguments[1]), let seconds = Double(CommandLine.arguments[2]),
              seconds > 0, seconds <= 60 else { fatalError("usage: capture_game_audio PID SECONDS OUTPUT.m4a (1–60 seconds)") }
        let content = try await SCShareableContent.excludingDesktopWindows(true, onScreenWindowsOnly: true)
        guard let app = content.applications.first(where: { $0.processID == pid }), let display = content.displays.first else {
            throw NSError(domain: "capture", code: 1, userInfo: [NSLocalizedDescriptionKey: "Minecraft process/display not found"])
        }
        let filter = SCContentFilter(display: display, including: [app], exceptingWindows: [])
        let config = SCStreamConfiguration()
        config.width = 32; config.height = 32; config.minimumFrameInterval = CMTime(value: 1, timescale: 1)
        config.capturesAudio = true; config.excludesCurrentProcessAudio = true
        config.sampleRate = 48000; config.channelCount = 2
        let output = try AudioOutput(URL(fileURLWithPath: CommandLine.arguments[3]))
        let queue = DispatchQueue(label: "minigamelib.audio.qa")
        let stream = SCStream(filter: filter, configuration: config, delegate: nil)
        try stream.addStreamOutput(output, type: .audio, sampleHandlerQueue: queue)
        try await stream.startCapture()
        print("CAPTURING pid=\(pid) seconds=\(seconds)")
        try await Task.sleep(for: .seconds(seconds))
        try await stream.stopCapture()
        queue.sync { output.input.markAsFinished() }
        if output.started { await output.writer.finishWriting() }
        guard output.writer.status == .completed, output.samples > 0 else {
            throw output.writer.error ?? NSError(domain: "capture", code: 2, userInfo: [NSLocalizedDescriptionKey: "No audio samples captured"])
        }
        print("SAVED samples=\(output.samples) \(CommandLine.arguments[3])")
    }
}
