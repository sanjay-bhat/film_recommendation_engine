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

struct IPadBrowseView: View {
    @ObservedObject var viewModel: MovieViewModel
    @StateObject private var speechManager = SpeechManager()

    var body: some View {
        NavigationSplitView {
            sidebarContent
                .navigationTitle("")
                .toolbar(.hidden, for: .navigationBar)
        } detail: {
            detailContent
        }
        .navigationSplitViewStyle(.balanced)
        .background(darkBg)
        .preferredColorScheme(.dark)
    }

    // MARK: - Sidebar

    private var sidebarContent: some View {
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
                        otdSection
                        historySection
                        surpriseMeSection
                    }
                }
                .padding()
            }
        }
    }

    // MARK: - Detail

    private var detailContent: some View {
        ZStack {
            darkBg.ignoresSafeArea()

            if viewModel.isLoading {
                loadingSection
            } else if !viewModel.selectedMovie.isEmpty && !viewModel.recommendations.isEmpty {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        recommendationsSection
                    }
                    .padding(24)
                }
            } else {
                VStack(spacing: 16) {
                    Text("FILM RECOMMEND")
                        .font(.system(size: 36, weight: .bold, design: .serif))
                        .foregroundColor(goldAccent)
                    Text("Search or tap a movie to see recommendations")
                        .font(.system(size: 16))
                        .foregroundColor(textSecondary)
                }
            }
        }
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(spacing: 4) {
            Text("FILM RECOMMEND")
                .font(.system(size: 24, weight: .bold, design: .serif))
                .foregroundColor(goldAccent)
            Text("Semantic Search \u{2022} TMDb 5000")
                .font(.system(size: 11, design: .monospaced))
                .foregroundColor(goldMuted)
                .tracking(2)
        }
        .padding(.top, 16)
        .padding(.bottom, 8)
    }

    // MARK: - Loading

    private var loadingSection: some View {
        VStack(spacing: 16) {
            Spacer(minLength: 80)
            ProgressView()
                .tint(goldAccent)
                .scaleEffect(1.5)
            Text("Loading movies...")
                .font(.subheadline)
                .foregroundColor(goldMuted)
            Spacer()
        }
    }

    // MARK: - Search

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

            Button(action: {
                if speechManager.isListening {
                    speechManager.stopListening()
                } else {
                    speechManager.requestPermission { granted in
                        guard granted else { return }
                        speechManager.startListening { text in
                            viewModel.onQueryChanged(text)
                        }
                    }
                }
            }) {
                Image(systemName: speechManager.isListening ? "mic.fill" : "mic")
                    .foregroundColor(speechManager.isListening ? .red : goldAccent)
            }
        }
        .padding(12)
        .background(cardBg)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(speechManager.isListening ? Color.red.opacity(0.5) : surfaceBg, lineWidth: 1)
        )
    }

    // MARK: - Suggestions

    @ViewBuilder
    private var suggestionsSection: some View {
        if !viewModel.suggestions.isEmpty {
            VStack(spacing: 0) {
                ForEach(viewModel.suggestions, id: \.0) { index, title in
                    Button(action: { viewModel.onMovieSelected(index: index, title: title) }) {
                        highlightedTitle(title, query: viewModel.query)
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

    // MARK: - On This Day

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

    // MARK: - History

    @ViewBuilder
    private var historySection: some View {
        if !viewModel.searchHistory.isEmpty && viewModel.selectedMovie.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text("RECENT")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(textSecondary)
                    .tracking(1)

                ForEach(viewModel.searchHistory, id: \.self) { title in
                    Button(action: { viewModel.onMovieSelected(index: -1, title: title) }) {
                        Text(title)
                            .font(.system(size: 14))
                            .foregroundColor(goldAccent)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(goldAccent.opacity(0.08))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(goldAccent.opacity(0.15), lineWidth: 1)
                            )
                            .cornerRadius(10)
                    }
                }
            }
        }
    }

    // MARK: - Surprise Me

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
                        .frame(width: 120, height: 120)
                        .background(
                            RadialGradient(
                                colors: [Color(red: 1, green: 0.27, blue: 0.27), Color(red: 0.8, green: 0, blue: 0), Color(red: 0.55, green: 0, blue: 0)],
                                center: UnitPoint(x: 0.35, y: 0.3),
                                startRadius: 0,
                                endRadius: 80
                            )
                        )
                        .clipShape(Circle())
                        .shadow(color: .black.opacity(0.5), radius: 10, x: 0, y: 6)
                }
                .padding(.top, 20)
                Spacer()
            }
        }
    }

    // MARK: - Recommendations

    @ViewBuilder
    private var recommendationsSection: some View {
        if !viewModel.selectedMovie.isEmpty && !viewModel.recommendations.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                Text("Because you liked")
                    .font(.caption)
                    .foregroundColor(textSecondary)
                    .tracking(1)

                Text(viewModel.selectedMovie)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(goldAccent)
                    .padding(.bottom, 4)

                if viewModel.industries.count > 1 {
                    industryFilters
                }

                posterGrid
                subLevelsSection
            }
        }
    }

    // MARK: - Industry Filters

    private var industryFilters: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                let allSelected = viewModel.selectedIndustries.count == viewModel.industries.count
                Button(action: { viewModel.selectAllIndustries() }) {
                    Text("All")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color(red: 0.247, green: 0.725, blue: 0.416).opacity(allSelected ? 1 : 0.3))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
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
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(bubbleColor.opacity(selected ? 1 : 0.3))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 6)
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

    // MARK: - Poster Grid (iPad uses grid instead of Cover Flow)

    private var posterGrid: some View {
        let columns = [
            GridItem(.adaptive(minimum: 160, maximum: 200), spacing: 16)
        ]
        return LazyVGrid(columns: columns, spacing: 20) {
            ForEach(viewModel.filteredRecommendations) { rec in
                posterCard(rec)
            }
        }
    }

    private func posterCard(_ rec: Recommendation) -> some View {
        VStack(spacing: 8) {
            if let posterPath = rec.posterPath ?? PosterData.posters[rec.title] {
                AsyncImage(url: URL(string: "\(PosterData.tmdbImgBase)w342\(posterPath)")) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().aspectRatio(contentMode: .fill)
                            .frame(width: 160, height: 240)
                            .clipped().cornerRadius(12)
                            .shadow(color: goldAccent.opacity(0.2), radius: 8, x: 0, y: 4)
                    case .failure:
                        placeholderPoster(rec)
                    case .empty:
                        ZStack {
                            RoundedRectangle(cornerRadius: 12).fill(cardBg)
                                .frame(width: 160, height: 240)
                            ProgressView().tint(goldAccent)
                        }
                    @unknown default:
                        placeholderPoster(rec)
                    }
                }
            } else {
                placeholderPoster(rec)
            }

            Text(rec.title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(textPrimary)
                .lineLimit(2)
                .multilineTextAlignment(.center)

            HStack(spacing: 8) {
                if rec.year > 0 {
                    Text("\(rec.year)")
                        .font(.system(size: 12))
                        .foregroundColor(textSecondary)
                }
                Text("\u{2605} \(rec.imdbScore, specifier: "%.1f")")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(ratingColor(rec.imdbScore))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(ratingColor(rec.imdbScore).opacity(0.2))
                    .cornerRadius(6)
            }

            HStack(spacing: 12) {
                if let trailerKey = rec.trailerKey,
                   let url = URL(string: "https://www.youtube.com/watch?v=\(trailerKey)") {
                    Link(destination: url) {
                        HStack(spacing: 4) {
                            Image(systemName: "play.fill").font(.system(size: 10))
                            Text("Trailer").font(.system(size: 11, weight: .semibold))
                        }
                        .foregroundColor(Color(red: 0.8, green: 0, blue: 0))
                    }
                }

                if let drillIn = viewModel as MovieViewModel? {
                    Button(action: { drillIn.drillInto(title: rec.title) }) {
                        Text("Similar \u{2192}")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(goldAccent)
                    }
                }
            }
        }
        .frame(width: 160)
    }

    private func placeholderPoster(_ rec: Recommendation) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(LinearGradient(colors: [surfaceBg, cardBg], startPoint: .top, endPoint: .bottom))
                .frame(width: 160, height: 240)
                .shadow(color: goldMuted.opacity(0.15), radius: 6, x: 0, y: 3)
            VStack(spacing: 8) {
                Text("\u{1F3AC}").font(.system(size: 36))
                Text(rec.title)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(textPrimary.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                    .padding(.horizontal, 8)
            }
        }
    }

    // MARK: - Sub-levels

    @ViewBuilder
    private var subLevelsSection: some View {
        ForEach(Array(viewModel.subLevels.enumerated()), id: \.element.id) { levelIdx, level in
            Divider().background(surfaceBg).padding(.vertical, 12)

            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("LEVEL \(levelIdx + 2)")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(goldAccent.opacity(0.6))
                        .tracking(2)
                    HStack(spacing: 0) {
                        Text("from ").font(.system(size: 11)).foregroundColor(textSecondary)
                        Text(level.fromTitle).font(.system(size: 11, weight: .semibold)).foregroundColor(goldAccent)
                    }
                }
                Spacer()
                Button(action: { viewModel.exitSubLevels() }) {
                    Text("\u{2B05} EXIT")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Color(red: 0.878, green: 0.251, blue: 0.251))
                        .tracking(2)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(Color(red: 0.878, green: 0.251, blue: 0.251).opacity(0.15))
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color(red: 0.878, green: 0.251, blue: 0.251).opacity(0.5), lineWidth: 1.5))
                        .cornerRadius(4)
                }
            }

            let subColumns = [GridItem(.adaptive(minimum: 160, maximum: 200), spacing: 16)]
            LazyVGrid(columns: subColumns, spacing: 20) {
                ForEach(level.filteredRecs) { rec in
                    posterCard(rec)
                }
            }
        }
    }
}
