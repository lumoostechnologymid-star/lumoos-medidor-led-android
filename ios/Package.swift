// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "MedidorCore", products: [.library(name: "MedidorCore", targets: ["MedidorCore"])], targets: [.target(name: "MedidorCore"), .testTarget(name: "MedidorCoreTests", dependencies: ["MedidorCore"])])
