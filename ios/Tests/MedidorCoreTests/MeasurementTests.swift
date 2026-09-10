import XCTest
@testable import MedidorCore
final class MeasurementTests: XCTestCase {
    func stable(_ signal: Double, _ time: Double, _ engine: inout MeasurementEngine) {
        engine.sample(signal, at: time); engine.sample(signal, at: time+0.02)
    }
    func testFullCycleAndKh() {
        var e = MeasurementEngine()
        stable(0,0,&e); e.arm(target: 1,kh: 21.6)
        stable(80,1,&e); XCTAssertTrue(e.measuring)
        stable(0,3,&e); XCTAssertEqual(e.revolutions,0); XCTAssertTrue(e.measuring)
        stable(80,5,&e); XCTAssertEqual(e.revolutions,1)
        XCTAssertEqual(e.elapsed,4,accuracy: 0.000001)
        XCTAssertEqual(e.result!,19.44,accuracy: 0.000001)
    }
    func testAlreadyVisibleMustDisappearFirst() {
        var e = MeasurementEngine()
        stable(80,0,&e); e.arm(target: 1,kh: 1)
        stable(80,1,&e); XCTAssertFalse(e.measuring)
        stable(0,2,&e); stable(80,3,&e)
        XCTAssertTrue(e.measuring); XCTAssertEqual(e.revolutions,0)
    }
    func testShortNoiseIsIgnoredAndMultipleCycles() {
        var e = MeasurementEngine()
        stable(0,0,&e); e.arm(target: 2,kh: 1)
        e.sample(80,at: 0.1); e.sample(0,at: 0.105)
        XCTAssertFalse(e.measuring)
        stable(80,1,&e); stable(0,2,&e); stable(80,3,&e)
        XCTAssertNil(e.result); XCTAssertEqual(e.revolutions,1)
        stable(0,4,&e); stable(80,5,&e)
        XCTAssertEqual(e.result!,1.8,accuracy: 0.000001)
        e.reset(); XCTAssertNil(e.result); XCTAssertEqual(e.revolutions,0)
    }
    func testDarkSquareAndUniformLighting() {
        func frame(center: UInt8, ambient: UInt8) -> FrameSignals {
            var bytes = [UInt8](repeating: ambient,count: 100*100*4)
            for y in 45..<55 { for x in 45..<55 { for c in 0..<3 { bytes[(y*100+x)*4+c] = center } } }
            return bytes.withUnsafeBufferPointer { FrameAnalyzer.analyze(bgra: $0.baseAddress!,width:100,height:100,stride:400)! }
        }
        XCTAssertEqual(frame(center:50,ambient:150).display,100,accuracy:0.001)
        XCTAssertEqual(frame(center:20,ambient:120).display,100,accuracy:0.001)
        XCTAssertEqual(frame(center:150,ambient:150).display,0,accuracy:0.001)
        XCTAssertGreaterThan(frame(center:200,ambient:100).infrared,0)
        XCTAssertEqual(frame(center:200,ambient:100).display,0,accuracy:0.001)
    }
}
