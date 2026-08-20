import SwiftUI

private let neonCyan = Color(red: 0, green: 0.898, blue: 1)
private let neonPink = Color(red: 1, green: 0.176, blue: 0.584)
private let neonPurple = Color(red: 0.482, green: 0.38, blue: 1)
private let darkBg = Color(red: 0.039, green: 0, blue: 0.082)
private let cardBg = Color(red: 0.102, green: 0.039, blue: 0.18)

struct CoverFlowView: View {
    let recommendations: [Recommendation]

    @State private var currentIndex: Int = 0
    @GestureState private var dragOffset: CGFloat = 0

    private let posterWidth: CGFloat = 180
    private let posterHeight: CGFloat = 270
    private let spacing: CGFloat = 60

    var body: some View {
        VStack(spacing: 20) {
            // Cover Flow carousel
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

            // Movie info below the carousel
            if recommendations.indices.contains(currentIndex) {
                let rec = recommendations[currentIndex]
                VStack(spacing: 8) {
                    Text(rec.title)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)

                    HStack(spacing: 16) {
                        Text(rec.year > 0 ? "\(rec.year)" : "Unknown")
                            .font(.system(size: 14))
                            .foregroundColor(neonPurple)

                        Text("★ \(rec.imdbScore, specifier: "%.1f")")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(rec.imdbScore >= 7.0 ? neonCyan : neonPurple)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(
                                (rec.imdbScore >= 7.0 ? neonCyan : neonPurple).opacity(0.2)
                            )
                            .cornerRadius(6)
                    }

                    Text("\(currentIndex + 1) / \(recommendations.count)")
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(neonPink.opacity(0.6))
                        .padding(.top, 4)
                }
                .animation(.easeInOut(duration: 0.2), value: currentIndex)
            }
        }
    }

    @ViewBuilder
    private func posterCard(for rec: Recommendation) -> some View {
        if let posterPath = PosterData.posters[rec.title] {
            AsyncImage(url: URL(string: "\(PosterData.tmdbImgBase)w342\(posterPath)")) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: posterWidth, height: posterHeight)
                        .clipped()
                        .cornerRadius(12)
                        .shadow(color: neonCyan.opacity(0.3), radius: 8, x: 0, y: 4)
                case .failure:
                    placeholderPoster(for: rec)
                case .empty:
                    ZStack {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(cardBg)
                        ProgressView()
                            .tint(neonCyan)
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
                        colors: [neonPurple.opacity(0.3), cardBg],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .shadow(color: neonPurple.opacity(0.2), radius: 6, x: 0, y: 3)

            VStack(spacing: 8) {
                Text("\u{1F3AC}")
                    .font(.system(size: 40))

                Text(rec.title)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                    .padding(.horizontal, 8)
            }
        }
    }
}
