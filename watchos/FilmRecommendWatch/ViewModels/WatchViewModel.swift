import Foundation

struct OtdItem: Identifiable {
    let id = UUID()
    let title: String
    let year: String
    let posterPath: String?
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

private func genreForLanguage(_ lang: String) -> String {
    industryMap[lang] ?? (lang.isEmpty ? "Other" : lang.prefix(1).uppercased() + lang.dropFirst())
}

@MainActor
class WatchViewModel: ObservableObject {

    @Published var currentMovie: Recommendation?
    @Published var isLoading = true
    @Published var otdMovie: OtdItem?

    private let supabase = SupabaseClient()
    private var allTitles: [String] = []
    private var recentGenres: [String] = []

    // Pre-fetched batch for Digital Crown scrolling
    private var batch: [Recommendation] = []
    private var batchIndex = 0
    private let batchSize = 20

    // MARK: - Initial load

    func loadData() {
        Task {
            do {
                allTitles = try await supabase.fetchAllTitles()
                await loadOnThisDay()
                isLoading = false
                await prefetchBatch()
                surpriseMe()
            } catch {
                isLoading = false
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
            if let first = movies.first {
                otdMovie = OtdItem(title: first.title, year: first.year, posterPath: first.posterPath)
            }
        } catch {}
    }

    // MARK: - Batch pre-fetching

    private func prefetchBatch() async {
        guard !allTitles.isEmpty else { return }

        var newBatch: [Recommendation] = []
        var attempts = 0

        // Pick random titles and fetch one recommendation from each
        while newBatch.count < batchSize && attempts < batchSize * 3 {
            attempts += 1
            guard let randomTitle = allTitles.randomElement() else { break }
            do {
                let recs = try await supabase.getRecommendations(title: randomTitle)
                if let first = recs.first {
                    // Skip if this genre was recently shown (for diversity)
                    let genre = genreForLanguage(first.originalLanguage)
                    if recentGenres.count >= 5 && recentGenres.contains(genre) && newBatch.count < batchSize - 2 {
                        continue
                    }
                    newBatch.append(first)
                }
            } catch {
                continue
            }
        }

        batch = newBatch
        batchIndex = 0
    }

    // MARK: - Surprise Me

    func surpriseMe() {
        guard !allTitles.isEmpty else { return }

        Task {
            // Pick a random title, avoiding recently shown genres for diversity
            var pick: String?
            var attempts = 0
            while pick == nil && attempts < 50 {
                attempts += 1
                guard let candidate = allTitles.randomElement() else { break }
                pick = candidate
            }

            guard let title = pick else { return }

            do {
                let recs = try await supabase.getRecommendations(title: title)
                if let first = recs.first {
                    let genre = genreForLanguage(first.originalLanguage)
                    trackGenre(genre)
                    currentMovie = first
                }
            } catch {}
        }
    }

    // MARK: - Digital Crown navigation

    func nextMovie() {
        guard !batch.isEmpty else {
            surpriseMe()
            return
        }

        batchIndex += 1

        // If batch exhausted, refetch in background and use surpriseMe as fallback
        if batchIndex >= batch.count {
            Task {
                await prefetchBatch()
            }
            surpriseMe()
            return
        }

        let movie = batch[batchIndex]
        let genre = genreForLanguage(movie.originalLanguage)
        trackGenre(genre)
        currentMovie = movie
    }

    // MARK: - Genre diversity tracking

    private func trackGenre(_ genre: String) {
        recentGenres.append(genre)
        if recentGenres.count > 5 {
            recentGenres.removeFirst()
        }
        // If all genres in the map have been exhausted, reset
        let uniqueRecent = Set(recentGenres)
        if uniqueRecent.count >= industryMap.count {
            recentGenres = []
        }
    }
}
