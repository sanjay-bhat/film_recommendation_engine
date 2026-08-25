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

    private let supabase = SupabaseClient()
    private var supabaseTitles: [String] = []
    private var allRecs: [Recommendation] = []
    private var searchTask: Task<Void, Never>?

    func loadDataset() {
        Task {
            do {
                supabaseTitles = try await supabase.fetchAllTitles()
                isLoading = false
                await loadOnThisDay()
            } catch {
                isLoading = false
                self.error = "Failed to connect"
            }
        }
    }

    private func loadOnThisDay() async {
        do {
            let cal = Calendar.current
            let now = Date()
            let movies = try await supabase.moviesOnThisDay(
                month: cal.component(.month, from: now),
                day: cal.component(.day, from: now)
            )
            otdMovies = movies.map { OtdItem(title: $0.title, year: $0.year, posterPath: $0.posterPath) }
        } catch {}
    }

    func dismissOtd() { otdDismissed = true }

    private func sanitizeQuery(_ input: String) -> String {
        let cleaned = input.unicodeScalars.filter { $0.value >= 0x20 && $0.value != 0x7f }
        return String(String.UnicodeScalarView(cleaned)).prefix(100).description
    }

    func onQueryChanged(_ newQuery: String) {
        query = sanitizeQuery(newQuery)
        searchTask?.cancel()
        guard query.count >= 2 else { suggestions = []; return }
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            let lower = query.lowercased()
            suggestions = supabaseTitles
                .enumerated()
                .filter { $0.element.lowercased().contains(lower) }
                .prefix(20)
                .map { ($0.offset, $0.element) }
        }
    }

    func surpriseMe() {
        guard let pick = supabaseTitles.randomElement() else { return }
        onMovieSelected(index: -1, title: pick)
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

        Task {
            do {
                let recs = try await supabase.getRecommendations(title: title)
                applyRecs(recs)
            } catch {
                self.error = "Recommendation failed"
            }
        }
    }

    private func applyRecs(_ recs: [Recommendation]) {
        allRecs = recs
        recommendations = recs
        filteredRecommendations = recs
        var seen = Set<String>()
        var unique = [String]()
        for name in recs.map({ getIndustryName($0.originalLanguage) }) {
            if seen.insert(name).inserted { unique.append(name) }
        }
        industries = unique
        selectedIndustries = Set(unique)
    }

    func toggleIndustry(_ industry: String) {
        if selectedIndustries.contains(industry) { selectedIndustries.remove(industry) }
        else { selectedIndustries.insert(industry) }
        if selectedIndustries.isEmpty { selectedIndustries = Set(industries) }
        let filtered = allRecs.filter { selectedIndustries.contains(getIndustryName($0.originalLanguage)) }
        filteredRecommendations = filtered.isEmpty ? allRecs : filtered
    }

    func selectAllIndustries() {
        selectedIndustries = Set(industries)
        filteredRecommendations = allRecs
    }

    func drillInto(title: String) {
        Task {
            do {
                let recs = try await supabase.getRecommendations(title: title)
                guard !recs.isEmpty else { return }
                var seen = Set<String>()
                var unique = [String]()
                for name in recs.map({ getIndustryName($0.originalLanguage) }) {
                    if seen.insert(name).inserted { unique.append(name) }
                }
                subLevels.append(SubLevel(fromTitle: title, allRecs: recs, filteredRecs: recs, industries: unique, selectedIndustries: Set(unique)))
            } catch {}
        }
    }

    func exitSubLevels() { subLevels = [] }

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
