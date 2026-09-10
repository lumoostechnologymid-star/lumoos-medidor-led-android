import AVFoundation
import SwiftUI
import MedidorCore

final class CameraService: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "com.lumoos.camera")
    private var configured = false
    private var desiredRunning = false
    private var observers: [NSObjectProtocol] = []
    @Published var ready = false
    @Published var error: String?
    @Published var frameAspect: CGFloat = 9.0/16.0
    var onFrame: ((FrameSignals, Double) -> Void)?
    var onInterruption: (() -> Void)?

    override init() {
        super.init()
        for name in [AVCaptureSession.wasInterruptedNotification, AVCaptureSession.runtimeErrorNotification] {
            observers.append(NotificationCenter.default.addObserver(forName: name, object: session, queue: .main) { [weak self] _ in
                self?.ready = false
                self?.error = "La cámara se interrumpió. Pulsa Reintentar cámara."
                self?.onInterruption?()
            })
        }
    }
    deinit { observers.forEach { NotificationCenter.default.removeObserver($0) } }
    func start() {
        queue.async { self.desiredRunning = true }
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configureAndStart()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                if granted { self?.configureAndStart() }
                else { DispatchQueue.main.async { self?.error = "Permite la cámara en Ajustes para medir." } }
            }
        default: error = "Permite la cámara en Ajustes para medir."
        }
    }
    func stop() {
        ready = false
        queue.async { self.desiredRunning = false; if self.session.isRunning { self.session.stopRunning() } }
    }
    private func configureAndStart() {
        queue.async {
            guard self.desiredRunning else { return }
            do {
                if !self.configured {
                    self.session.beginConfiguration()
                    defer { self.session.commitConfiguration() }
                    self.session.sessionPreset = .hd1280x720
                    guard let device = AVCaptureDevice.default(.builtInWideAngleCamera,for:.video,position:.back) else { throw CameraError.unavailable }
                    let input = try AVCaptureDeviceInput(device:device)
                    guard self.session.canAddInput(input) else { throw CameraError.unavailable }
                    self.session.addInput(input)
                    let output = AVCaptureVideoDataOutput()
                    output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
                    output.alwaysDiscardsLateVideoFrames = true
                    output.setSampleBufferDelegate(self,queue:self.queue)
                    guard self.session.canAddOutput(output) else { self.session.removeInput(input); throw CameraError.unavailable }
                    self.session.addOutput(output)
                    if let connection = output.connection(with:.video), connection.isVideoOrientationSupported { connection.videoOrientation = .portrait }
                    do {
                        try device.lockForConfiguration()
                        defer { device.unlockForConfiguration() }
                        if device.isFocusModeSupported(.continuousAutoFocus) { device.focusMode = .continuousAutoFocus }
                        if device.isExposureModeSupported(.continuousAutoExposure) { device.exposureMode = .continuousAutoExposure }
                    } catch { /* Retain the camera's default focus and exposure. */ }
                    self.configured = true
                }
                if !self.session.isRunning { self.session.startRunning() }
                let running = self.session.isRunning
                DispatchQueue.main.async { self.ready = running; self.error = running ? nil : "No se pudo iniciar la cámara." }
            } catch { DispatchQueue.main.async { self.ready = false; self.error = "No se pudo abrir la cámara. Reintenta o revisa el permiso en Ajustes." } }
        }
    }
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard desiredRunning, let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        CVPixelBufferLockBaseAddress(buffer,.readOnly)
        defer { CVPixelBufferUnlockBaseAddress(buffer,.readOnly) }
        guard let base = CVPixelBufferGetBaseAddress(buffer) else { return }
        let width = CVPixelBufferGetWidth(buffer), height = CVPixelBufferGetHeight(buffer)
        guard let sample = FrameAnalyzer.analyze(bgra:base.assumingMemoryBound(to:UInt8.self),width:width,height:height,stride:CVPixelBufferGetBytesPerRow(buffer)) else { return }
        let timestamp = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer))
        DispatchQueue.main.async {
            guard self.ready else { return }
            let ratio = CGFloat(width)/CGFloat(height)
            if self.frameAspect != ratio { self.frameAspect = ratio }
            self.onFrame?(sample,timestamp)
        }
    }
    private enum CameraError: Error { case unavailable }
}

final class PreviewSurface: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    override func layoutSubviews() {
        super.layoutSubviews()
        if let connection = previewLayer.connection, connection.isVideoOrientationSupported { connection.videoOrientation = .portrait }
    }
}
struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    func makeUIView(context:Context) -> PreviewSurface {
        let view = PreviewSurface(); view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspect
        return view
    }
    func updateUIView(_ view:PreviewSurface,context:Context) {}
}
