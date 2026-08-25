import Foundation
import Combine

@MainActor
class MovieViewModel: ObservableObject {

    @Published var query = ""
    @Published var suggestions: [(Int, String)] = []
    @Published var selectedMovie = ""
    @Published var recommendations: [Recommendation] = []
    @Published var isLoading = true
    @Published var error: String?
    @Published var searchHistory: [String] = []

    /// When true, search and recommendations go through Supabase.
    /// Falls back to the on-device engine when false.
    private(set) var useSupabase = false

    private let engine = RecommendationEngine()
    private let supabase = SupabaseClient()

    /// Titles loaded from Supabase (empty when using the on-device engine).
    private var supabaseTitles: [String] = []

    private var searchTask: Task<Void, Never>?

    // MARK: - Lifecycle

    func loadDataset() {
        Task {
            // Try Supabase first
            do {
                let titles = try await supabase.fetchAllTitles()
                supabaseTitles = titles
                useSupabase = true
                isLoading = false
            } catch {
                // Supabase unavailable — fall back to on-device engine
                await loadEngineOffline()
            }
        }
    }

    /// Load the bundled CSV dataset through the on-device KNN engine.
    private func loadEngineOffline() async {
        await Task.detached { [engine] in
            engine.load()
        }.value
        useSupabase = false
        isLoading = false
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

        if useSupabase {
            Task {
                do {
                    let recs = try await supabase.getRecommendations(title: title)
                    if recs.isEmpty {
                        await fallbackRecommend(title: title)
                    } else {
                        recommendations = recs
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
                recommendations = recs
            }
        }
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
        recommendations = recs
    }

    // MARK: - Clear

    func clearSelection() {
        query = ""
        selectedMovie = ""
        suggestions = []
        recommendations = []
    }
}
