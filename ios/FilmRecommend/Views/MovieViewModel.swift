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

    private let engine = RecommendationEngine()
    private var searchTask: Task<Void, Never>?

    func loadDataset() {
        Task.detached { [engine] in
            engine.load()
            await MainActor.run {
                self.isLoading = false
            }
        }
    }

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
            let results = await Task.detached { [engine, q = self.query] in
                engine.searchTitles(query: q)
            }.value

            if !Task.isCancelled {
                suggestions = results
            }
        }
    }

    func onMovieSelected(index: Int, title: String) {
        query = title
        selectedMovie = title
        suggestions = []
        recommendations = []

        Task {
            let recs = await Task.detached { [engine] in
                engine.recommend(movieIndex: index)
            }.value
            recommendations = recs
        }
    }

    func clearSelection() {
        query = ""
        selectedMovie = ""
        suggestions = []
        recommendations = []
    }
}
