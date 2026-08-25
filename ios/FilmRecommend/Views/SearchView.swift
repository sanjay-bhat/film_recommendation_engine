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

struct SearchView: View {
    @ObservedObject var viewModel: MovieViewModel

    var body: some View {
        ZStack {
            darkBg.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 16) {
                    headerSection
                    if viewModel.isLoading {
                        loadingSection
                    } else {
                        searchSection
                        suggestionsSection
                        recommendationsSection
                    }
                }
                .padding()
            }
        }
        .preferredColorScheme(.dark)
    }

    private var headerSection: some View {
        VStack(spacing: 4) {
            Text("FILM RECOMMEND")
                .font(.system(size: 28, weight: .bold, design: .serif))
                .foregroundColor(goldAccent)

            Text("Semantic Search \u{2022} TMDb 5000")
                .font(.system(size: 12, design: .monospaced))
                .foregroundColor(goldMuted)
                .tracking(2)
        }
        .padding(.top, 20)
        .padding(.bottom, 8)
    }

    private var loadingSection: some View {
        VStack(spacing: 16) {
            Spacer(minLength: 100)
            ProgressView()
                .tint(goldAccent)
                .scaleEffect(1.5)
            Text("Loading movies...")
                .font(.subheadline)
                .foregroundColor(goldMuted)
            Spacer()
        }
    }

    private var searchSection: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(goldAccent)

            TextField("Search for a movie", text: Binding(
                get: { viewModel.query },
                set: { viewModel.onQueryChanged($0) }
            ))
            .foregroundColor(textPrimary)
            .autocorrectionDisabled()

            if !viewModel.query.isEmpty {
                Button(action: { viewModel.clearSelection() }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(goldMuted)
                }
            }
        }
        .padding(12)
        .background(cardBg)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(surfaceBg, lineWidth: 1)
        )
    }

    @ViewBuilder
    private var suggestionsSection: some View {
        if !viewModel.suggestions.isEmpty {
            VStack(spacing: 0) {
                ForEach(viewModel.suggestions, id: \.0) { index, title in
                    Button(action: { viewModel.onMovieSelected(index: index, title: title) }) {
                        Text(title)
                            .foregroundColor(textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                    }
                    if index != viewModel.suggestions.last?.0 {
                        Divider().background(surfaceBg)
                    }
                }
            }
            .background(cardBg)
            .cornerRadius(12)
        }
    }

    @ViewBuilder
    private var recommendationsSection: some View {
        if !viewModel.selectedMovie.isEmpty && !viewModel.recommendations.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                Text("Because you liked")
                    .font(.caption)
                    .foregroundColor(textSecondary)
                    .tracking(1)

                Text(viewModel.selectedMovie)
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(goldAccent)
                    .padding(.bottom, 4)

                CoverFlowView(recommendations: viewModel.recommendations)
            }
        }
    }
}

struct RecommendationCard: View {
    let recommendation: Recommendation

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(recommendation.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(textPrimary)

                Text(recommendation.year > 0 ? "\(recommendation.year)" : "Unknown year")
                    .font(.caption)
                    .foregroundColor(textSecondary)
            }

            Spacer()

            Text("\u{2605} \(recommendation.imdbScore, specifier: "%.1f")")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(ratingColor(recommendation.imdbScore))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(ratingColor(recommendation.imdbScore).opacity(0.2))
                .cornerRadius(8)
        }
        .padding(16)
        .background(
            LinearGradient(
                colors: [surfaceBg.opacity(0.3), goldAccent.opacity(0.05)],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
        .background(cardBg)
        .cornerRadius(16)
    }
}
