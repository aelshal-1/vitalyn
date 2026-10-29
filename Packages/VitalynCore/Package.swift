// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "VitalynCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "VitalynCore", targets: ["VitalynCore"])
    ],
    targets: [
        .target(name: "VitalynCore"),
        .testTarget(name: "VitalynCoreTests", dependencies: ["VitalynCore"])
    ]
)
