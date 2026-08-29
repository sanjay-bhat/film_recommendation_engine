// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "FilmRecommendCore",
    platforms: [
        .iOS(.v16),
        .watchOS(.v9),
        .tvOS(.v16),
        .macOS(.v13),
    ],
    products: [
        .library(name: "FilmRecommendCore", targets: ["FilmRecommendCore"]),
    ],
    targets: [
        .target(name: "FilmRecommendCore"),
    ]
)
