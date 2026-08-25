import SwiftUI

private let goldAccent = Color(red: 0.769, green: 0.639, blue: 0.353)
private let goldMuted = Color(red: 0.627, green: 0.502, blue: 0.314)
private let textPrimary = Color(red: 0.831, green: 0.812, blue: 0.784)
private let textSecondary = Color(red: 0.533, green: 0.533, blue: 0.533)
private let darkBg = Color(red: 0.031, green: 0.031, blue: 0.047)
private let cardBg = Color(red: 0.067, green: 0.067, blue: 0.094)
private let surfaceBg = Color(red: 0.102, green: 0.102, blue: 0.133)
private let tmdbImgBase = "https://image.tmdb.org/t/p/"

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
                        otdSection
                        searchSection
                        suggestionsSection
                        historySection
                        surpriseMeSection
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

    @ViewBuilder
    private var otdSection: some View {
        if !viewModel.otdMovies.isEmpty && !viewModel.otdDismissed && viewModel.selectedMovie.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("ON THIS DAY")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(goldAccent.opacity(0.5))
                        .tracking(0.8)
                    Spacer()
                    Button(action: { viewModel.dismissOtd() }) {
                        Text("\u{2715}")
                            .font(.system(size: 12))
                            .foregroundColor(textSecondary.opacity(0.5))
                    }
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(viewModel.otdMovies) { movie in
                            Button(action: { viewModel.onMovieSelected(index: -1, title: movie.title) }) {
                                VStack(spacing: 4) {
                                    if let path = movie.posterPath {
                                        AsyncImage(url: URL(string: "\(tmdbImgBase)w154\(path)")) { phase in
                                            if case .success(let img) = phase {
                                                img.resizable().aspectRatio(contentMode: .fill)
                                                    .frame(width: 60, height: 90)
                                                    .clipped().cornerRadius(6)
                                            } else {
                                                RoundedRectangle(cornerRadius: 6).fill(cardBg)
                                                    .frame(width: 60, height: 90)
                                            }
                                        }
                                    }
                                    Text(movie.title)
                                        .font(.system(size: 10))
                                        .foregroundColor(textPrimary)
                                        .lineLimit(2)
                                        .multilineTextAlignment(.center)
                                    if !movie.year.isEmpty {
                                        Text(movie.year)
                                            .font(.system(size: 9))
                                            .foregroundColor(textSecondary)
                                    }
                                }
                                .frame(width: 80)
                            }
                        }
                    }
                }
            }
            .padding(.bottom, 4)
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
    private var historySection: some View {
        if !viewModel.searchHistory.isEmpty && viewModel.selectedMovie.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(viewModel.searchHistory, id: \.self) { title in
                        Button(action: { viewModel.onMovieSelected(index: -1, title: title) }) {
                            Text(title)
                                .font(.system(size: 12))
                                .foregroundColor(goldAccent)
                                .lineLimit(1)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 6)
                                .background(goldAccent.opacity(0.08))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 20)
                                        .stroke(goldAccent.opacity(0.15), lineWidth: 1)
                                )
                                .cornerRadius(20)
                        }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var surpriseMeSection: some View {
        if viewModel.selectedMovie.isEmpty {
            HStack {
                Spacer()
                Button(action: { viewModel.surpriseMe() }) {
                    Text("SURPRISE\nME")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .tracking(1.5)
                        .frame(width: 140, height: 140)
                        .background(
                            RadialGradient(
                                colors: [Color(red: 1, green: 0.27, blue: 0.27), Color(red: 0.8, green: 0, blue: 0), Color(red: 0.55, green: 0, blue: 0)],
                                center: UnitPoint(x: 0.35, y: 0.3),
                                startRadius: 0,
                                endRadius: 90
                            )
                        )
                        .clipShape(Circle())
                        .shadow(color: .black.opacity(0.5), radius: 10, x: 0, y: 6)
                }
                .padding(.top, 28)
                Spacer()
            }
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

                // Industry filter bubbles
                if viewModel.industries.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            let allSelected = viewModel.selectedIndustries.count == viewModel.industries.count
                            Button(action: { viewModel.selectAllIndustries() }) {
                                Text("All")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(Color(red: 0.247, green: 0.725, blue: 0.416).opacity(allSelected ? 1 : 0.3))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 5)
                                    .background(allSelected ? Color(red: 0.247, green: 0.725, blue: 0.416).opacity(0.15) : .clear)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 20)
                                            .stroke(Color(red: 0.247, green: 0.725, blue: 0.416).opacity(0.4), lineWidth: 1.5)
                                    )
                                    .cornerRadius(20)
                            }

                            ForEach(Array(viewModel.industries.enumerated()), id: \.element) { i, name in
                                let selected = viewModel.selectedIndustries.contains(name)
                                let bubbleColor: Color = i == 0 ? goldAccent : i == 1 ? Color(red: 0.706, green: 0.706, blue: 0.745) : Color(red: 0.690, green: 0.478, blue: 0.314)

                                Button(action: { viewModel.toggleIndustry(name) }) {
                                    Text(name)
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundColor(bubbleColor.opacity(selected ? 1 : 0.3))
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 5)
                                        .background(selected ? bubbleColor.opacity(0.15) : .clear)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 20)
                                                .stroke(bubbleColor.opacity(0.4), lineWidth: 1.5)
                                        )
                                        .cornerRadius(20)
                                }
                            }
                        }
                    }
                    .padding(.bottom, 4)
                }

                CoverFlowView(recommendations: viewModel.filteredRecommendations)
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
