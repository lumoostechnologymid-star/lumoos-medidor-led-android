import Foundation

public enum DetectionMode: String, CaseIterable, Identifiable {
    case yellow = "LED amarillo", red = "LED rojo", infrared = "LED infrarrojo", display = "Cuadro del display"
    public var id: String { rawValue }
}

public struct MeasurementEngine {
    public var threshold: Double = 20
    public var hysteresis: Double = 2
    public var debounce: Double = 0.012
    public private(set) var visible: Bool?
    private var candidate: Bool?
    private var candidateSince = 0.0
    public private(set) var armed = false
    public private(set) var measuring = false
    private var seenAbsent = false
    private var start: Double?
    private var lastAppearance: Double?
    public private(set) var revolutions = 0
    public private(set) var elapsed = 0.0
    public private(set) var lastCycle = 0.0
    public private(set) var result: Double?
    public private(set) var target = 5
    private var kh = 1.0
    public init() {}
    public mutating func arm(target: Int, kh: Double) {
        guard target > 0, kh.isFinite, kh > 0 else { return }
        reset()
        self.target = target; self.kh = kh
        armed = true; seenAbsent = visible == false
    }
    public mutating func reset() {
        armed = false; measuring = false; seenAbsent = false
        start = nil; lastAppearance = nil; revolutions = 0
        elapsed = 0; lastCycle = 0; result = nil
    }
    public mutating func sample(_ signal: Double, at time: Double) {
        guard signal.isFinite, time.isFinite else { return }
        let wanted: Bool
        if let visible { wanted = signal >= threshold + (visible ? -hysteresis : hysteresis) }
        else { wanted = signal >= threshold }
        if candidate != wanted { candidate = wanted; candidateSince = time }
        if visible != wanted && time - candidateSince >= debounce {
            visible = wanted
            if armed {
                if !wanted { seenAbsent = true }
                else if !measuring && seenAbsent {
                    measuring = true; start = time; lastAppearance = time
                } else if measuring, let previous = lastAppearance, let start {
                    revolutions += 1; lastCycle = time - previous; lastAppearance = time
                    elapsed = time - start
                    if revolutions >= target && elapsed > 0 {
                        result = Double(revolutions) * kh * 3.6 / elapsed
                        measuring = false; armed = false
                    }
                }
            }
        }
        if measuring, let start { elapsed = max(0, time - start) }
    }
}

public struct FrameSignals {
    public let brightness: Double
    public let yellow: Double
    public let red: Double
    public let infrared: Double
    public let display: Double
    public func signal(for mode: DetectionMode) -> Double {
        switch mode { case .yellow: return yellow; case .red: return red; case .infrared: return infrared; case .display: return display }
    }
    public init(brightness: Double, yellow: Double, red: Double, infrared: Double, display: Double) {
        self.brightness = brightness; self.yellow = yellow; self.red = red; self.infrared = infrared; self.display = display
    }
}

public enum FrameAnalyzer {
    // Coordinates are centered fractions, matching the preview overlay.
    public static func analyze(bgra: UnsafePointer<UInt8>, width: Int, height: Int, stride: Int) -> FrameSignals? {
        guard width >= 32, height >= 32, stride >= width * 4 else { return nil }
        let cw = max(8, Int(Double(width) * 0.10)), ch = max(8, Int(Double(height) * 0.10))
        let ow = max(cw + 8, Int(Double(width) * 0.28)), oh = max(ch + 8, Int(Double(height) * 0.28))
        let cl = (width-cw)/2, ct = (height-ch)/2
        var lum: [Double] = [], reds: [Double] = [], yellows: [Double] = []
        var al = 0.0, ar = 0.0, ay = 0.0, count = 0.0
        for y in Swift.stride(from: (height-oh)/2, to: (height+oh)/2, by: 2) {
            for x in Swift.stride(from: (width-ow)/2, to: (width+ow)/2, by: 2) {
                let i = y * stride + x * 4
                let b = Double(bgra[i]), g = Double(bgra[i+1]), r = Double(bgra[i+2])
                let l = 0.299*r + 0.587*g + 0.114*b
                let red = max(0, r-max(g,b)*0.9), yellow = max(0, min(r,g)-b*0.75)
                if x >= cl && x < cl+cw && y >= ct && y < ct+ch {
                    lum.append(l); reds.append(red); yellows.append(yellow)
                } else { al += l; ar += red; ay += yellow; count += 1 }
            }
        }
        guard !lum.isEmpty, count > 0 else { return nil }
        func top(_ values: [Double]) -> Double { let v = values.sorted(by: >).prefix(16); return v.reduce(0,+)/Double(v.count) }
        func clamp(_ value: Double) -> Double { min(255, max(0,value)) }
        let contrast = max(0,top(lum)-al/count)
        return FrameSignals(brightness: top(lum), yellow: clamp((top(yellows)-ay/count)*1.2+contrast*0.15), red: clamp((top(reds)-ar/count)*1.2+contrast*0.15), infrared: clamp(contrast*1.35), display: clamp(al/count-lum.reduce(0,+)/Double(lum.count)))
    }
}
