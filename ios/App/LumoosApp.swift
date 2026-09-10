import SwiftUI
import MedidorCore

@main
struct LumoosApp: App {
    var body: some Scene { WindowGroup { MeasurementScreen() } }
}

struct MeasurementScreen: View {
    @StateObject private var model = MeasurementModel()
    @StateObject private var camera = CameraService()
    @Environment(\.scenePhase) private var scenePhase
    @FocusState private var editing: Bool
    private let accent = Color(red:0.02,green:0.48,blue:0.48)
    var body: some View {
        GeometryReader { geometry in
            VStack(spacing:0) {
                HStack {
                    Image(systemName:"waveform.path.ecg").foregroundStyle(accent)
                    Text("Lumoos Medidor").font(.headline)
                    Spacer()
                    Text("iPhone · 0.2.0").font(.caption).foregroundStyle(.secondary)
                }.padding()
                cameraPanel.frame(height:min(270,geometry.size.height*0.35)).padding(.horizontal,12)
                ScrollView {
                    VStack(alignment:.leading,spacing:16) {
                        GroupBox("Estado del indicador") {
                            VStack(alignment:.leading,spacing:8) {
                                Text(model.engine.visible == nil ? "Detectando…" : (model.engine.visible == true ? (model.display ? "■ VISIBLE" : "● PRENDIDO") : (model.display ? "□ AUSENTE" : "○ APAGADO"))).font(.headline)
                                metric("Señal actual", model.signal, "")
                                metric(model.display ? "VISIBLE desde" : "ENCENDIDO desde",min(255,model.engine.threshold+model.engine.hysteresis),"")
                                metric(model.display ? "AUSENTE debajo de" : "APAGADO debajo de",max(0,model.engine.threshold-model.engine.hysteresis),"")
                                HStack { Text("Vueltas"); Spacer(); Text("\(model.engine.revolutions) / \(model.turns)").monospacedDigit() }
                                metric("Tiempo total",model.engine.elapsed,"s")
                                metric("Última vuelta",model.engine.lastCycle,"s")
                                Text(model.status).font(.subheadline).foregroundStyle(accent)
                            }.frame(maxWidth:.infinity,alignment:.leading)
                        }
                        GroupBox("Configuración de medición") {
                            VStack(alignment:.leading,spacing:12) {
                                Picker("Tipo de detección",selection:Binding(get:{model.mode},set:{model.select($0)})) {
                                    ForEach(DetectionMode.allCases) { Text($0.rawValue).tag($0) }
                                }.disabled(model.locked || model.calibrating)
                                HStack {
                                    VStack(alignment:.leading) { Text("KH"); TextField("KH",text:$model.kh).keyboardType(.decimalPad) }
                                    VStack(alignment:.leading) { Text("Vueltas"); TextField("Vueltas",text:$model.turns).keyboardType(.numberPad) }
                                }.textFieldStyle(.roundedBorder).focused($editing).disabled(model.locked)
                                HStack { ForEach([1,3,5,10],id:\.self) { n in Button("\(n)") { model.turns = String(n) }.buttonStyle(.bordered).disabled(model.locked) } }
                                Text(model.display ? "Aparece → desaparece → aparece = una vuelta. Si ya está visible al iniciar, esperamos la siguiente aparición." : "Encendido → apagado → encendido = una vuelta.").font(.caption)
                            }
                        }
                        GroupBox("Sensibilidad") {
                            VStack(alignment:.leading,spacing:10) {
                                Text(model.display ? "Contraste del cuadro oscuro con el fondo claro" : "Contraste del LED con la luz alrededor").font(.caption)
                                metric("Brillo del centro",model.brightness,"")
                                ProgressView(value:model.signal,total:255)
                                Text("Umbral: \(model.engine.threshold,specifier:"%.1f")")
                                Slider(value:$model.engine.threshold,in:0...255,step:1).disabled(model.locked || model.calibrating)
                                if model.calibrating { ProgressView(value:model.calibrationProgress) }
                                if model.mode == .infrared { Text("Algunos iPhone filtran la luz infrarroja; verifica que la señal cambie.").font(.caption) }
                            }
                        }
                        if let result = model.engine.result {
                            GroupBox("RESULTADO") {
                                VStack(alignment:.leading,spacing:8) {
                                    Text("\(result,specifier:"%.4f") kW").font(.largeTitle.bold()).foregroundStyle(accent)
                                    Text("kW = (vueltas × KH × 3.6) / segundos").font(.caption)
                                }.frame(maxWidth:.infinity,alignment:.leading)
                            }
                        }
                        if !model.message.isEmpty { Text(model.message).font(.callout) }
                    }.padding(12)
                }.scrollDismissesKeyboard(.interactively)
                VStack(spacing:8) {
                    if editing { Button("Listo · cerrar teclado") { editing = false } }
                    Button { editing = false; model.arm() } label: {
                        Text(model.locked ? model.status : "▶ Iniciar medición").bold().frame(maxWidth:.infinity).frame(minHeight:40)
                    }.buttonStyle(.borderedProminent).disabled(!camera.ready || model.locked || model.calibrating)
                    HStack {
                        Button("Reiniciar") { model.reset() }.frame(maxWidth:.infinity)
                        Button(model.calibrating ? "Calibrando…" : "Calibrar") { editing = false; model.calibrate() }.frame(maxWidth:.infinity).disabled(!camera.ready || model.locked || model.calibrating)
                    }.buttonStyle(.bordered)
                }.padding(12).background(.regularMaterial)
            }.background(Color(uiColor:.systemGroupedBackground)).tint(accent)
        }
        .onAppear {
            camera.onFrame = { sample,time in model.sample(sample,at:time) }
            camera.onInterruption = { model.interrupted() }
            camera.start()
        }
        .onDisappear { camera.stop(); model.interrupted() }
        .onChange(of:scenePhase) { phase in
            if phase == .active { camera.start() }
            else { camera.stop(); model.interrupted() }
        }
    }
    private var cameraPanel: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black
                CameraPreview(session:camera.session)
                let w = min(geometry.size.width,geometry.size.height*camera.frameAspect)
                let h = w/camera.frameAspect
                Rectangle().stroke(.white.opacity(0.5),lineWidth:1).frame(width:w*0.28,height:h*0.28)
                Rectangle().stroke(.white,lineWidth:2).frame(width:w*0.10,height:h*0.10)
                if let error = camera.error {
                    VStack {
                        Text(error).multilineTextAlignment(.center)
                        Button("Reintentar cámara") { camera.start() }
                        Button("Abrir Ajustes") { if let url = URL(string:UIApplication.openSettingsURLString) { UIApplication.shared.open(url) } }
                    }.padding().background(.black.opacity(0.85)).foregroundStyle(.white)
                }
            }.clipShape(RoundedRectangle(cornerRadius:12))
        }
    }
    private func metric(_ label:String,_ value:Double,_ unit:String) -> some View {
        HStack { Text(label); Spacer(); Text("\(value,specifier:"%.3f") \(unit)").monospacedDigit() }.font(.subheadline)
    }
}
