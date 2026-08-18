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

    func onQueryChanged(_ newQuery: String) {
        query = newQuery
        searchTask?.cancel()

        guard newQuery.count >= 2 else {
            suggestions = []
            return
        }

        searchTask = Task {
            let results = await Task.detached { [engine] in
                engine.searchTitles(query: newQuery)
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
