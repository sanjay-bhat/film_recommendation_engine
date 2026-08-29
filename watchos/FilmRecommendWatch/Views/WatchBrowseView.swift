import SwiftUI

private let goldAccent = Color(red: 0.769, green: 0.639, blue: 0.353)
private let darkBg = Color(red: 0.031, green: 0.031, blue: 0.047)
private let cardBg = Color(red: 0.067, green: 0.067, blue: 0.094)
private let textPrimary = Color(red: 0.831, green: 0.812, blue: 0.784)
private let textSecondary = Color(red: 0.533, green: 0.533, blue: 0.533)

private func ratingColor(_ score: Double) -> Color {
    if score >= 8.0 { return goldAccent }
    if score >= 7.0 { return Color(red: 0.541, green: 0.620, blue: 0.541) }
    return Color(red: 0.541, green: 0.494, blue: 0.431)
}

struct WatchBrowseView: View {
    @ObservedObject var viewModel: WatchViewModel
    @State private var crownValue: Double = 0
    @State private var lastDetent: Int = 0

    var body: some View {
        ZStack {
            darkBg.ignoresSafeArea()

            if viewModel.isLoading {
                loadingView
            } else if let movie = viewModel.currentMovie {
                movieCard(movie)
            } else {
                emptyState
            }
        }
        .focusable()
        .digitalCrownRotation($crownValue, from: -1000, through: 1000, sensitivity: .low)
        .onChange(of: crownValue) { oldValue, newValue in
            let newDetent = Int(newValue)
            if newDetent != lastDetent {
                lastDetent = newDetent
                viewModel.nextMovie()
            }
        }
        .sensoryFeedback(.impact(flexibility: .rigid, intensity: 0.6), trigger: lastDetent)
    }

    // MARK: - Loading

    private var loadingView: some View {
        VStack(spacing: 8) {
            ProgressView()
                .tint(goldAccent)
            Text("Loading...")
                .font(.system(size: 14))
                .foregroundColor(textSecondary)
        }
    }

    // MARK: - Empty state

    private var emptyState: some View {
        VStack(spacing: 12) {
            Text("FILM RECOMMEND")
                .font(.system(size: 13, weight: .bold, design: .serif))
                .foregroundColor(goldAccent)

            surpriseMeButton
        }
    }

    // MARK: - Movie card

    private func movieCard(_ movie: Recommendation) -> some View {
        ZStack {
            // Full-bleed blurred poster background
            posterBackground(movie)

            // Dark gradient overlay
            LinearGradient(
                colors: [
                    .clear,
                    darkBg.opacity(0.3),
                    darkBg.opacity(0.7),
                    darkBg.opacity(0.95)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            // Content overlay
            VStack(spacing: 0) {
                Spacer()

                // On This Day badge (if available)
                if let otd = viewModel.otdMovie {
                    otdBadge(otd)
                        .padding(.bottom, 6)
                }

                // Movie info at bottom
                VStack(spacing: 4) {
                    Text(movie.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(textPrimary)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)

                    HStack(spacing: 6) {
                        if movie.year > 0 {
                            Text("\(movie.year)")
                                .font(.system(size: 12))
                                .foregroundColor(textSecondary)
                        }

                        ratingBadge(movie.imdbScore)
                    }
                }

                surpriseMeButton
                    .padding(.top, 8)

                // Crown hint
                Text("Turn crown for more")
                    .font(.system(size: 9))
                    .foregroundColor(textSecondary.opacity(0.6))
                    .padding(.top, 4)
            }
            .padding(.horizontal, 8)
            .padding(.bottom, 4)
        }
    }

    // MARK: - Poster background

    @ViewBuilder
    private func posterBackground(_ movie: Recommendation) -> some View {
        let posterPath = movie.posterPath ?? PosterData.posters[movie.title]
        if let path = posterPath {
            AsyncImage(url: URL(string: "\(PosterData.tmdbImgBase)w154\(path)")) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .blur(radius: 6)
                        .ignoresSafeArea()
                case .failure:
                    darkBg.ignoresSafeArea()
                case .empty:
                    darkBg.ignoresSafeArea()
                @unknown default:
                    darkBg.ignoresSafeArea()
                }
            }
        } else {
            darkBg.ignoresSafeArea()
        }
    }

    // MARK: - Rating badge

    private func ratingBadge(_ score: Double) -> some View {
        let color = ratingColor(score)
        return Text("\u{2605} \(score, specifier: "%.1f")")
            .font(.system(size: 12, weight: .bold))
            .foregroundColor(color)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.2))
            .cornerRadius(4)
    }

    // MARK: - On This Day badge

    private func otdBadge(_ otd: OtdItem) -> some View {
        VStack(spacing: 2) {
            Text("ON THIS DAY")
                .font(.system(size: 8, weight: .semibold))
                .foregroundColor(goldAccent.opacity(0.7))
                .tracking(1)
            Text("\(otd.title) (\(otd.year))")
                .font(.system(size: 10))
                .foregroundColor(textSecondary)
                .lineLimit(1)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(cardBg.opacity(0.8))
        .cornerRadius(6)
    }

    // MARK: - Surprise Me button

    private var surpriseMeButton: some View {
        Button(action: { viewModel.surpriseMe() }) {
            Text("SURPRISE ME")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.white)
                .tracking(1)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(
                    LinearGradient(
                        colors: [
                            Color(red: 0.9, green: 0.15, blue: 0.15),
                            Color(red: 0.6, green: 0, blue: 0)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .cornerRadius(10)
        }
        .buttonStyle(.plain)
    }
}
