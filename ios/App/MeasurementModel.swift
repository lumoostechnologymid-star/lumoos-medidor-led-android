import SwiftUI
import MedidorCore

final class MeasurementModel: ObservableObject {
    @Published var engine = MeasurementEngine()
    @Published var mode: DetectionMode = .yellow
    @Published var kh = "1.0"
    @Published var turns = "5"
    @Published var signal = 0.0
    @Published var brightness = 0.0
    @Published var calibrating = false
    @Published var calibrationProgress = 0.0
    @Published var message = "Centra el indicador y calibra antes de medir."
    private var calibrationStart: Double?
    private var minimum = 255.0, maximum = 0.0
    var locked: Bool { engine.armed || engine.measuring }
    var display: Bool { mode == .display }
    var status: String {
        if engine.result != nil { return "Medición finalizada" }
        if engine.measuring { return "Midiendo · \(engine.revolutions)/\(engine.target) vueltas" }
        if engine.armed { return engine.visible == true ? (display ? "Esperando que desaparezca" : "Esperando que se apague") : (display ? "Esperando la siguiente aparición" : "Esperando el primer encendido") }
        return "Listo para medir"
    }
    func select(_ mode: DetectionMode) {
        guard !locked && !calibrating else { return }
        self.mode = mode; engine = MeasurementEngine(); signal = 0
        message = mode == .display ? "Centra solo el cuadrado superior. Deja fuera las flechas y el cuadrado inferior." : "Centra el LED dentro del recuadro pequeño."
    }
    func arm() {
        guard !locked && !calibrating else { return }
        guard let value = Double(kh.replacingOccurrences(of:",",with:".")),value.isFinite,value > 0 else { message = "Ingresa una KH válida mayor que cero."; return }
        guard let target = Int(turns), (1...999).contains(target) else { message = "Ingresa entre 1 y 999 vueltas."; return }
        engine.arm(target:target,kh:value); message = ""
    }
    func reset() { engine.reset(); calibrating = false; calibrationStart = nil; calibrationProgress = 0; message = "Listo para una nueva medición." }
    func interrupted() { reset(); engine = MeasurementEngine(); message = "Medición cancelada por interrupción. Vuelve a calibrar." }
    func calibrate() {
        guard !locked else { return }
        calibrating = true; calibrationStart = nil; minimum = 255; maximum = 0; calibrationProgress = 0
        message = display ? "Deja que el cuadro aparezca y desaparezca durante 3 segundos." : "Mantén el LED centrado durante 3 segundos."
    }
    func sample(_ sample: FrameSignals, at time: Double) {
        signal = sample.signal(for:mode); brightness = sample.brightness
        if calibrating {
            if calibrationStart == nil { calibrationStart = time }
            minimum = min(minimum,signal); maximum = max(maximum,signal)
            let elapsed = time-(calibrationStart ?? time)
            calibrationProgress = min(1,elapsed/3)
            if elapsed >= 3 {
                calibrating = false
                if maximum-minimum >= 2 {
                    engine.threshold = (minimum+maximum)/2
                    engine.hysteresis = min(8,max(0.5,(maximum-minimum)*0.15))
                    message = "Calibración lista. Comprueba ambos estados antes de iniciar."
                } else { message = "Cambio insuficiente. Reencuadra y repite cuando cambie el indicador." }
            }
        }
        engine.sample(signal,at:time)
    }
}
