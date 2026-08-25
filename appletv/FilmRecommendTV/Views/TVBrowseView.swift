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

struct TVBrowseView: View {
    @ObservedObject var viewModel: MovieViewModel

    var body: some View {
        ZStack {
            darkBg.ignoresSafeArea()

            if viewModel.isLoading {
                VStack(spacing: 16) {
                    ProgressView().tint(goldAccent).scaleEffect(2)
                    Text("Loading movies...").font(.title3).foregroundColor(goldMuted)
                }
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        headerSection
                        searchSection
                        if !viewModel.suggestions.isEmpty { suggestionsSection }
                        if !viewModel.otdMovies.isEmpty && !viewModel.otdDismissed && viewModel.selectedMovie.isEmpty { otdSection }
                        if !viewModel.searchHistory.isEmpty && viewModel.selectedMovie.isEmpty { historySection }
                        if viewModel.selectedMovie.isEmpty { surpriseMeSection }
                        if !viewModel.selectedMovie.isEmpty && !viewModel.recommendations.isEmpty { recommendationsSection }
                    }
                    .padding(48)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private var headerSection: some View {
        VStack(spacing: 4) {
            Text("FILM RECOMMEND")
                .font(.system(size: 48, weight: .bold, design: .serif))
                .foregroundColor(goldAccent)
            Text("Semantic Search \u{2022} TMDb 5000")
                .font(.system(size: 16, design: .monospaced))
                .foregroundColor(goldMuted)
                .tracking(2)
        }
        .frame(maxWidth: .infinity)
    }

    private var searchSection: some View {
        HStack(spacing: 12) {
            TextField("Search for a movie...", text: Binding(
                get: { viewModel.query },
                set: { viewModel.onQueryChanged($0) }
            ))
            .textFieldStyle(.plain)
            .font(.title3)
            .foregroundColor(textPrimary)

            Image(systemName: "mic.fill")
                .font(.title3)
                .foregroundColor(goldAccent.opacity(0.5))
        }
        .padding(16)
        .background(cardBg)
        .cornerRadius(12)
    }

    @ViewBuilder
    private var suggestionsSection: some View {
        VStack(spacing: 0) {
            ForEach(viewModel.suggestions.prefix(10), id: \.0) { index, title in
                Button(action: { viewModel.onMovieSelected(index: index, title: title) }) {
                    highlightedTitle(title, query: viewModel.query)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.plain)
            }
        }
        .background(cardBg)
        .cornerRadius(12)
    }

    private func highlightedTitle(_ title: String, query: String) -> Text {
        guard let range = title.range(of: query, options: .caseInsensitive) else {
            return Text(title).foregroundColor(textPrimary)
        }
        let before = String(title[title.startIndex..<range.lowerBound])
        let match = String(title[range])
        let after = String(title[range.upperBound...])
        return Text(before).foregroundColor(textPrimary)
            + Text(match).foregroundColor(goldAccent).bold()
            + Text(after).foregroundColor(textPrimary)
    }

    private var otdSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("ON THIS DAY")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(goldAccent.opacity(0.5))
                .tracking(1)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 20) {
                    ForEach(viewModel.otdMovies) { movie in
                        Button(action: { viewModel.onMovieSelected(index: -1, title: movie.title) }) {
                            VStack(spacing: 6) {
                                if let path = movie.posterPath {
                                    AsyncImage(url: URL(string: "\(PosterData.tmdbImgBase)w154\(path)")) { phase in
                                        if case .success(let img) = phase {
                                            img.resizable().aspectRatio(contentMode: .fill)
                                                .frame(width: 100, height: 150).clipped().cornerRadius(8)
                                        } else {
                                            RoundedRectangle(cornerRadius: 8).fill(cardBg)
                                                .frame(width: 100, height: 150)
                                        }
                                    }
                                }
                                Text(movie.title)
                                    .font(.system(size: 13))
                                    .foregroundColor(textPrimary)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.center)
                            }
                            .frame(width: 110)
                        }
                        .buttonStyle(.card)
                    }
                }
            }
        }
    }

    private var historySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("RECENT")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(textSecondary)
                .tracking(1)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(viewModel.searchHistory, id: \.self) { title in
                        Button(action: { viewModel.onMovieSelected(index: -1, title: title) }) {
                            Text(title)
                                .font(.system(size: 16))
                                .foregroundColor(goldAccent)
                                .lineLimit(1)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.card)
                    }
                }
            }
        }
    }

    private var surpriseMeSection: some View {
        HStack {
            Spacer()
            Button(action: { viewModel.surpriseMe() }) {
                Text("SURPRISE ME")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                    .tracking(2)
                    .frame(width: 240, height: 80)
                    .background(
                        LinearGradient(
                            colors: [Color(red: 0.9, green: 0.15, blue: 0.15), Color(red: 0.6, green: 0, blue: 0)],
                            startPoint: .top, endPoint: .bottom
                        )
                    )
                    .cornerRadius(16)
            }
            .buttonStyle(.card)
            Spacer()
        }
    }

    @ViewBuilder
    private var recommendationsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Because you liked")
                .font(.system(size: 16))
                .foregroundColor(textSecondary)
                .tracking(1)
            Text(viewModel.selectedMovie)
                .font(.system(size: 36, weight: .bold))
                .foregroundColor(goldAccent)

            if viewModel.industries.count > 1 {
                industryFilters
            }

            posterRow(viewModel.filteredRecommendations)

            ForEach(Array(viewModel.subLevels.enumerated()), id: \.element.id) { levelIdx, level in
                Divider().background(surfaceBg).padding(.vertical, 16)
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("LEVEL \(levelIdx + 2)")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(goldAccent.opacity(0.6))
                            .tracking(2)
                        HStack(spacing: 0) {
                            Text("from ").font(.system(size: 14)).foregroundColor(textSecondary)
                            Text(level.fromTitle).font(.system(size: 14, weight: .semibold)).foregroundColor(goldAccent)
                        }
                    }
                    Spacer()
                    Button(action: { viewModel.exitSubLevels() }) {
                        Text("\u{2B05} EXIT")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(red: 0.878, green: 0.251, blue: 0.251))
                            .tracking(2)
                    }
                    .buttonStyle(.card)
                }
                posterRow(level.filteredRecs)
            }
        }
    }

    private var industryFilters: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                let allSelected = viewModel.selectedIndustries.count == viewModel.industries.count
                Button(action: { viewModel.selectAllIndustries() }) {
                    Text("All")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Color(red: 0.247, green: 0.725, blue: 0.416).opacity(allSelected ? 1 : 0.3))
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.card)

                ForEach(Array(viewModel.industries.enumerated()), id: \.element) { i, name in
                    let selected = viewModel.selectedIndustries.contains(name)
                    let bubbleColor: Color = i == 0 ? goldAccent : i == 1 ? Color(red: 0.706, green: 0.706, blue: 0.745) : Color(red: 0.690, green: 0.478, blue: 0.314)
                    Button(action: { viewModel.toggleIndustry(name) }) {
                        Text(name)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(bubbleColor.opacity(selected ? 1 : 0.3))
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                    }
                    .buttonStyle(.card)
                }
            }
        }
        .padding(.bottom, 4)
    }

    private func posterRow(_ recs: [Recommendation]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 24) {
                ForEach(recs) { rec in
                    TVPosterCard(rec: rec, onDrillIn: { viewModel.drillInto(title: rec.title) })
                }
            }
            .padding(.vertical, 8)
        }
    }
}

struct TVPosterCard: View {
    let rec: Recommendation
    let onDrillIn: () -> Void
    @Environment(\.isFocused) var isFocused

    var body: some View {
        Button(action: onDrillIn) {
            VStack(spacing: 10) {
                posterImage
                    .frame(width: 240, height: 360)

                Text(rec.title)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(textPrimary)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)

                HStack(spacing: 10) {
                    if rec.year > 0 {
                        Text("\(rec.year)")
                            .font(.system(size: 14))
                            .foregroundColor(textSecondary)
                    }
                    Text("\u{2605} \(rec.imdbScore, specifier: "%.1f")")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(ratingColor(rec.imdbScore))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(ratingColor(rec.imdbScore).opacity(0.2))
                        .cornerRadius(6)
                }

                if rec.trailerKey != nil {
                    HStack(spacing: 4) {
                        Image(systemName: "play.fill").font(.system(size: 12))
                        Text("Watch Trailer").font(.system(size: 13, weight: .semibold))
                    }
                    .foregroundColor(Color(red: 0.8, green: 0, blue: 0))
                }
            }
            .frame(width: 240)
        }
        .buttonStyle(.card)
    }

    @ViewBuilder
    private var posterImage: some View {
        if let posterPath = rec.posterPath ?? PosterData.posters[rec.title] {
            AsyncImage(url: URL(string: "\(PosterData.tmdbImgBase)w342\(posterPath)")) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().aspectRatio(contentMode: .fill)
                        .frame(width: 240, height: 360).clipped().cornerRadius(12)
                case .failure:
                    placeholderPoster
                case .empty:
                    ZStack {
                        RoundedRectangle(cornerRadius: 12).fill(cardBg)
                        ProgressView().tint(goldAccent)
                    }
                @unknown default:
                    placeholderPoster
                }
            }
        } else {
            placeholderPoster
        }
    }

    private var placeholderPoster: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(LinearGradient(colors: [surfaceBg, cardBg], startPoint: .top, endPoint: .bottom))
            VStack(spacing: 8) {
                Text("\u{1F3AC}").font(.system(size: 48))
                Text(rec.title)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(textPrimary.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                    .padding(.horizontal, 12)
            }
        }
    }
}
