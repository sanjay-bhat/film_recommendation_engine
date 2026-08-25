import Foundation
import Combine

struct OtdItem: Identifiable {
    let id = UUID()
    let title: String
    let year: String
    let posterPath: String?
}

struct SubLevel: Identifiable {
    let id = UUID()
    let fromTitle: String
    var allRecs: [Recommendation]
    var filteredRecs: [Recommendation]
    var industries: [String]
    var selectedIndustries: Set<String>
}

private let industryMap: [String: String] = [
    "en": "Hollywood", "hi": "Bollywood", "ta": "Kollywood", "te": "Tollywood",
    "ja": "Japanese", "ko": "Korean", "fr": "French", "de": "German",
    "es": "Spanish", "zh": "Chinese", "it": "Italian", "pt": "Portuguese",
    "ml": "Malayalam", "bn": "Bengali", "ru": "Russian", "th": "Thai",
    "tr": "Turkish", "pl": "Polish", "nl": "Dutch", "sv": "Swedish",
    "da": "Danish", "no": "Norwegian", "fi": "Finnish", "id": "Indonesian",
    "ar": "Arabic", "he": "Hebrew", "uk": "Ukrainian", "cs": "Czech",
    "ro": "Romanian", "hu": "Hungarian", "cn": "Chinese", "is": "Icelandic",
    "af": "Afrikaans", "ca": "Catalan", "el": "Greek"
]

func getIndustryName(_ lang: String) -> String {
    industryMap[lang] ?? (lang.isEmpty ? "Other" : lang.prefix(1).uppercased() + lang.dropFirst())
}

@MainActor
class MovieViewModel: ObservableObject {

    @Published var query = ""
    @Published var suggestions: [(Int, String)] = []
    @Published var selectedMovie = ""
    @Published var recommendations: [Recommendation] = []
    @Published var filteredRecommendations: [Recommendation] = []
    @Published var isLoading = true
    @Published var error: String?
    @Published var searchHistory: [String] = []
    @Published var industries: [String] = []
    @Published var selectedIndustries: Set<String> = []
    @Published var subLevels: [SubLevel] = []
    @Published var otdMovies: [OtdItem] = []
    @Published var otdDismissed = false

    private(set) var useSupabase = false

    private let engine = RecommendationEngine()
    private let supabase = SupabaseClient()

    private var supabaseTitles: [String] = []
    private var allRecs: [Recommendation] = []

    private var searchTask: Task<Void, Never>?

    // MARK: - Lifecycle

    func loadDataset() {
        Task {
            do {
                let titles = try await supabase.fetchAllTitles()
                supabaseTitles = titles
                useSupabase = true
                isLoading = false
                await loadOnThisDay()
            } catch {
                await loadEngineOffline()
            }
        }
    }

    private func loadEngineOffline() async {
        await Task.detached { [engine] in
            engine.load()
        }.value
        useSupabase = false
        isLoading = false
    }

    private func loadOnThisDay() async {
        guard useSupabase else { return }
        do {
            let cal = Calendar.current
            let now = Date()
            let month = cal.component(.month, from: now)
            let day = cal.component(.day, from: now)
            let movies = try await supabase.moviesOnThisDay(month: month, day: day)
            otdMovies = movies.map { OtdItem(title: $0.title, year: $0.year, posterPath: $0.posterPath) }
        } catch {
            // Silently ignore OTD errors
        }
    }

    func dismissOtd() {
        otdDismissed = true
    }

    // MARK: - Search

    private func sanitizeQuery(_ input: String) -> String {
        let cleaned = input.unicodeScalars.filter { $0.value >= 0x20 && $0.value != 0x7f }
        return String(String.UnicodeScalarView(cleaned)).prefix(100).description
    }

    func onQueryChanged(_ newQuery: String) {
        query = sanitizeQuery(newQuery)
        searchTask?.cancel()

        guard query.count >= 2 else {
            suggestions = []
            return
        }

        searchTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }

            if useSupabase {
                let lower = query.lowercased()
                suggestions = supabaseTitles
                    .enumerated()
                    .filter { $0.element.lowercased().contains(lower) }
                    .prefix(20)
                    .map { ($0.offset, $0.element) }
            } else {
                let results = await Task.detached { [engine, q = self.query] in
                    engine.searchTitles(query: q)
                }.value

                if !Task.isCancelled {
                    suggestions = results
                }
            }
        }
    }

    // MARK: - Selection

    func surpriseMe() {
        let titles = useSupabase ? supabaseTitles : engine.allTitles()
        guard let pick = titles.randomElement() else { return }
        let index = useSupabase ? -1 : (titles.firstIndex(of: pick) ?? 0)
        onMovieSelected(index: index, title: pick)
    }

    func onMovieSelected(index: Int, title: String) {
        searchHistory.removeAll { $0 == title }
        searchHistory.insert(title, at: 0)
        if searchHistory.count > 8 { searchHistory = Array(searchHistory.prefix(8)) }

        query = title
        selectedMovie = title
        suggestions = []
        recommendations = []
        filteredRecommendations = []
        industries = []
        selectedIndustries = []
        subLevels = []

        if useSupabase {
            Task {
                do {
                    let recs = try await supabase.getRecommendations(title: title)
                    if recs.isEmpty {
                        await fallbackRecommend(title: title)
                    } else {
                        applyRecs(recs)
                    }
                } catch {
                    await fallbackRecommend(title: title)
                }
            }
        } else {
            Task {
                let recs = await Task.detached { [engine] in
                    engine.recommend(movieIndex: index)
                }.value
                applyRecs(recs)
            }
        }
    }

    private func applyRecs(_ recs: [Recommendation]) {
        allRecs = recs
        recommendations = recs
        filteredRecommendations = recs

        let industryNames = recs.map { getIndustryName($0.originalLanguage) }
        var seen = Set<String>()
        var unique = [String]()
        for name in industryNames {
            if seen.insert(name).inserted { unique.append(name) }
        }
        industries = unique
        selectedIndustries = Set(unique)
    }

    func toggleIndustry(_ industry: String) {
        if selectedIndustries.contains(industry) {
            selectedIndustries.remove(industry)
        } else {
            selectedIndustries.insert(industry)
        }
        if selectedIndustries.isEmpty {
            selectedIndustries = Set(industries)
        }
        let filtered = allRecs.filter { selectedIndustries.contains(getIndustryName($0.originalLanguage)) }
        filteredRecommendations = filtered.isEmpty ? allRecs : filtered
    }

    func selectAllIndustries() {
        selectedIndustries = Set(industries)
        filteredRecommendations = allRecs
    }

    // MARK: - Recursive Sub-levels

    func drillInto(title: String) {
        Task {
            var recs: [Recommendation] = []
            if useSupabase {
                do {
                    let sbRecs = try await supabase.getRecommendations(title: title)
                    recs = sbRecs
                } catch {}
            }
            if recs.isEmpty {
                if !engine.isLoaded {
                    await Task.detached { [engine] in engine.load() }.value
                }
                recs = await Task.detached { [engine, title] in
                    engine.recommendByTitle(title)
                }.value
            }
            guard !recs.isEmpty else { return }

            let industryNames = recs.map { getIndustryName($0.originalLanguage) }
            var seen = Set<String>()
            var unique = [String]()
            for name in industryNames {
                if seen.insert(name).inserted { unique.append(name) }
            }

            let level = SubLevel(
                fromTitle: title,
                allRecs: recs,
                filteredRecs: recs,
                industries: unique,
                selectedIndustries: Set(unique)
            )
            subLevels.append(level)
        }
    }

    func toggleSubIndustry(levelIndex: Int, industry: String) {
        guard levelIndex < subLevels.count else { return }
        if subLevels[levelIndex].selectedIndustries.contains(industry) {
            subLevels[levelIndex].selectedIndustries.remove(industry)
        } else {
            subLevels[levelIndex].selectedIndustries.insert(industry)
        }
        if subLevels[levelIndex].selectedIndustries.isEmpty {
            subLevels[levelIndex].selectedIndustries = Set(subLevels[levelIndex].industries)
        }
        let filtered = subLevels[levelIndex].allRecs.filter {
            subLevels[levelIndex].selectedIndustries.contains(getIndustryName($0.originalLanguage))
        }
        subLevels[levelIndex].filteredRecs = filtered.isEmpty ? subLevels[levelIndex].allRecs : filtered
    }

    func selectAllSubIndustries(levelIndex: Int) {
        guard levelIndex < subLevels.count else { return }
        subLevels[levelIndex].selectedIndustries = Set(subLevels[levelIndex].industries)
        subLevels[levelIndex].filteredRecs = subLevels[levelIndex].allRecs
    }

    func exitSubLevels() {
        subLevels = []
    }

    private func fallbackRecommend(title: String) async {
        if !engine.isLoaded {
            await Task.detached { [engine] in
                engine.load()
            }.value
        }
        let recs = await Task.detached { [engine, title] in
            engine.recommendByTitle(title)
        }.value
        applyRecs(recs)
    }

    // MARK: - Clear

    func clearSelection() {
        query = ""
        selectedMovie = ""
        suggestions = []
        recommendations = []
        filteredRecommendations = []
        industries = []
        selectedIndustries = []
        subLevels = []
    }
}
