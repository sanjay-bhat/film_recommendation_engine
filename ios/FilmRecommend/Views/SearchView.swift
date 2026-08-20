import SwiftUI

private let neonCyan = Color(red: 0, green: 0.898, blue: 1)
private let neonPink = Color(red: 1, green: 0.176, blue: 0.584)
private let neonPurple = Color(red: 0.482, green: 0.38, blue: 1)
private let darkBg = Color(red: 0.039, green: 0, blue: 0.082)
private let cardBg = Color(red: 0.102, green: 0.039, blue: 0.18)

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
                .font(.system(size: 28, weight: .bold, design: .default))
                .foregroundColor(neonCyan)

            Text("Content-Based • TMDb 5000")
                .font(.system(size: 12, design: .monospaced))
                .foregroundColor(neonPink)
                .tracking(2)
        }
        .padding(.top, 20)
        .padding(.bottom, 8)
    }

    private var loadingSection: some View {
        VStack(spacing: 16) {
            Spacer(minLength: 100)
            ProgressView()
                .tint(neonCyan)
                .scaleEffect(1.5)
            Text("Loading 4,803 movies...")
                .font(.subheadline)
                .foregroundColor(neonPink)
            Spacer()
        }
    }

    private var searchSection: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(neonCyan)

            TextField("Search for a movie", text: Binding(
                get: { viewModel.query },
                set: { viewModel.onQueryChanged($0) }
            ))
            .foregroundColor(.white)
            .autocorrectionDisabled()

            if !viewModel.query.isEmpty {
                Button(action: { viewModel.clearSelection() }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(neonPink)
                }
            }
        }
        .padding(12)
        .background(cardBg)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(neonPurple.opacity(0.5), lineWidth: 1)
        )
    }

    @ViewBuilder
    private var suggestionsSection: some View {
        if !viewModel.suggestions.isEmpty {
            VStack(spacing: 0) {
                ForEach(viewModel.suggestions, id: \.0) { index, title in
                    Button(action: { viewModel.onMovieSelected(index: index, title: title) }) {
                        Text(title)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                    }
                    if index != viewModel.suggestions.last?.0 {
                        Divider().background(neonPurple.opacity(0.2))
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
                    .foregroundColor(neonPink.opacity(0.7))
                    .tracking(1)

                Text(viewModel.selectedMovie)
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(neonCyan)
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
                    .foregroundColor(.white)

                Text(recommendation.year > 0 ? "\(recommendation.year)" : "Unknown year")
                    .font(.caption)
                    .foregroundColor(neonPurple.opacity(0.7))
            }

            Spacer()

            Text("★ \(recommendation.imdbScore, specifier: "%.1f")")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(recommendation.imdbScore >= 7.0 ? neonCyan : neonPurple)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    (recommendation.imdbScore >= 7.0 ? neonCyan : neonPurple).opacity(0.2)
                )
                .cornerRadius(8)
        }
        .padding(16)
        .background(
            LinearGradient(
                colors: [neonPurple.opacity(0.1), neonCyan.opacity(0.05)],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
        .background(cardBg)
        .cornerRadius(16)
    }
}
