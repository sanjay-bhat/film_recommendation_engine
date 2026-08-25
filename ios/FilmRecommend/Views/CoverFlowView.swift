import SwiftUI

private let goldAccent = Color(red: 0.769, green: 0.639, blue: 0.353)
private let goldMuted = Color(red: 0.627, green: 0.502, blue: 0.314)
private let textPrimary = Color(red: 0.831, green: 0.812, blue: 0.784)
private let textSecondary = Color(red: 0.533, green: 0.533, blue: 0.533)
private let darkBg = Color(red: 0.031, green: 0.031, blue: 0.047)
private let cardBg = Color(red: 0.067, green: 0.067, blue: 0.094)
private let surfaceBg = Color(red: 0.102, green: 0.102, blue: 0.133)

private func ratingColor(_ score: Double) -> Color {
    if score >= 8.0 { return goldAccent }
    if score >= 7.0 { return Color(red: 0.541, green: 0.620, blue: 0.541) }
    return Color(red: 0.541, green: 0.494, blue: 0.431)
}

struct CoverFlowView: View {
    let recommendations: [Recommendation]
    var onDrillIn: ((String) -> Void)?

    @State private var currentIndex: Int = 0
    @GestureState private var dragOffset: CGFloat = 0

    private let posterWidth: CGFloat = 180
    private let posterHeight: CGFloat = 270
    private let spacing: CGFloat = 60

    var body: some View {
        VStack(spacing: 20) {
            GeometryReader { geometry in
                let centerX = geometry.size.width / 2

                ZStack {
                    ForEach(Array(recommendations.enumerated()), id: \.element.id) { index, rec in
                        let offset = CGFloat(index - currentIndex) * spacing + dragOffset
                        let normalizedOffset = offset / spacing
                        let isCurrent = index == currentIndex && abs(dragOffset) < spacing / 2
                        let angle: Double = isCurrent ? 0 : (normalizedOffset < 0 ? 45 : -45)
                        let xPosition = centerX + offset * (isCurrent ? 1 : 0.8)
                        let scale: CGFloat = isCurrent ? 1.0 : 0.7
                        let opacity: Double = abs(normalizedOffset) > 3 ? 0 : 1.0 - abs(normalizedOffset) * 0.15

                        posterCard(for: rec)
                            .frame(width: posterWidth, height: posterHeight)
                            .scaleEffect(scale)
                            .rotation3DEffect(
                                .degrees(angle),
                                axis: (x: 0, y: 1, z: 0),
                                perspective: 0.5
                            )
                            .position(x: xPosition, y: posterHeight / 2 + 10)
                            .opacity(opacity)
                            .zIndex(isCurrent ? 10 : 10 - abs(normalizedOffset))
                    }
                }
                .gesture(
                    DragGesture()
                        .updating($dragOffset) { value, state, _ in
                            state = value.translation.width
                        }
                        .onEnded { value in
                            let threshold: CGFloat = spacing / 2
                            var newIndex = currentIndex
                            if value.translation.width < -threshold {
                                newIndex = min(currentIndex + 1, recommendations.count - 1)
                            } else if value.translation.width > threshold {
                                newIndex = max(currentIndex - 1, 0)
                            }
                            withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                                currentIndex = newIndex
                            }
                        }
                )
            }
            .frame(height: posterHeight + 20)

            if recommendations.indices.contains(currentIndex) {
                let rec = recommendations[currentIndex]
                VStack(spacing: 8) {
                    Text(rec.title)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(textPrimary)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)

                    HStack(spacing: 16) {
                        Text(rec.year > 0 ? "\(rec.year)" : "Unknown")
                            .font(.system(size: 14))
                            .foregroundColor(textSecondary)

                        Text("\u{2605} \(rec.imdbScore, specifier: "%.1f")")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(ratingColor(rec.imdbScore))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(ratingColor(rec.imdbScore).opacity(0.2))
                            .cornerRadius(6)
                    }

                    if let trailerKey = rec.trailerKey,
                       let url = URL(string: "https://www.youtube.com/watch?v=\(trailerKey)") {
                        Button(action: { UIApplication.shared.open(url) }) {
                            HStack(spacing: 4) {
                                Image(systemName: "play.fill")
                                    .font(.system(size: 12))
                                Text("Watch Trailer")
                                    .font(.system(size: 13, weight: .semibold))
                            }
                            .foregroundColor(Color(red: 0.8, green: 0, blue: 0))
                        }
                        .padding(.top, 4)
                    }

                    if let drillIn = onDrillIn {
                        Button(action: { drillIn(rec.title) }) {
                            Text("See Similar \u{2192}")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(goldAccent)
                        }
                        .padding(.top, 2)
                    }

                    Text("\(currentIndex + 1) / \(recommendations.count)")
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(textSecondary)
                        .padding(.top, 4)
                }
                .animation(.easeInOut(duration: 0.2), value: currentIndex)
            }
        }
    }

    @ViewBuilder
    private func posterCard(for rec: Recommendation) -> some View {
        if let posterPath = rec.posterPath ?? PosterData.posters[rec.title] {
            AsyncImage(url: URL(string: "\(PosterData.tmdbImgBase)w342\(posterPath)")) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: posterWidth, height: posterHeight)
                        .clipped()
                        .cornerRadius(12)
                        .shadow(color: goldAccent.opacity(0.2), radius: 8, x: 0, y: 4)
                case .failure:
                    placeholderPoster(for: rec)
                case .empty:
                    ZStack {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(cardBg)
                        ProgressView()
                            .tint(goldAccent)
                    }
                @unknown default:
                    placeholderPoster(for: rec)
                }
            }
        } else {
            placeholderPoster(for: rec)
        }
    }

    private func placeholderPoster(for rec: Recommendation) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(
                    LinearGradient(
                        colors: [surfaceBg, cardBg],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .shadow(color: goldMuted.opacity(0.15), radius: 6, x: 0, y: 3)

            VStack(spacing: 8) {
                Text("\u{1F3AC}")
                    .font(.system(size: 40))

                Text(rec.title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(textPrimary.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                    .padding(.horizontal, 8)
            }
        }
    }
}
