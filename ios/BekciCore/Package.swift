// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "BekciCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "BekciCore", targets: ["BekciCore"]),
    ],
    targets: [
        .target(name: "BekciCore"),
        .testTarget(
            name: "BekciCoreTests",
            dependencies: ["BekciCore"],
            resources: [.copy("golden.json")]
        ),
    ]
)
