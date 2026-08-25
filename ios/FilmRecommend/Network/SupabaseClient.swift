import Foundation

enum SupabaseError: Error, LocalizedError {
    case invalidURL
    case httpError(Int)
    case decodingError(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid Supabase URL"
        case .httpError(let code):
            return "HTTP error \(code)"
        case .decodingError(let detail):
            return "Decoding error: \(detail)"
        }
    }
}

/// Lightweight Supabase REST client using URLSession (no third-party deps).
struct SupabaseClient {

    private let baseURL = "https://labwvnsunfhswkmlvisl.supabase.co"
    private let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxhYnd2bnN1bmZoc3drbWx2aXNsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODczNjk5NDksImV4cCI6MjEwMjk0NTk0OX0.bmaEevB0AP-GgSy3LPX2eorNLxSzTLHWQpD4Veuyg9U"
    private let pageSize = 1000

    // MARK: - Shared helpers

    /// Build a URLRequest with the required Supabase auth headers.
    private func makeRequest(url: URL, method: String = "GET") -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(anonKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        return request
    }

    /// Execute a request and return the raw Data, throwing on non-2xx status.
    private func execute(_ request: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw SupabaseError.httpError(0)
        }
        guard (200..<300).contains(http.statusCode) else {
            throw SupabaseError.httpError(http.statusCode)
        }
        return data
    }

    // MARK: - Public API

    /// Paginate through all titles from the movies table.
    func fetchAllTitles() async throws -> [String] {
        var allTitles: [String] = []
        var offset = 0

        while true {
            guard let url = URL(string: "\(self.baseURL)/rest/v1/movies?select=title&order=title&limit=\(self.pageSize)&offset=\(offset)") else {
                throw SupabaseError.invalidURL
            }

            let request = self.makeRequest(url: url)
            let data = try await self.execute(request)

            guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
                throw SupabaseError.decodingError("Expected array of objects")
            }

            let titles = rows.compactMap { $0["title"] as? String }
            allTitles.append(contentsOf: titles)

            // Stop when we get fewer rows than the page size
            if rows.count < self.pageSize { break }
            offset += self.pageSize
        }

        return allTitles
    }

    struct OtdMovie {
        let title: String
        let year: String
        let posterPath: String?
    }

    func moviesOnThisDay(month: Int, day: Int) async throws -> [OtdMovie] {
        guard let url = URL(string: "\(self.baseURL)/rest/v1/rpc/movies_on_this_day") else {
            throw SupabaseError.invalidURL
        }

        var request = self.makeRequest(url: url, method: "POST")
        let body: [String: Int] = ["mm": month, "dd": day]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let data = try await self.execute(request)

        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }

        return rows.map { row in
            let releaseDate = row["release_date"] as? String ?? ""
            let year = releaseDate.count >= 4 ? String(releaseDate.prefix(4)) : ""
            return OtdMovie(
                title: row["title"] as? String ?? "",
                year: year,
                posterPath: row["poster_path"] as? String
            )
        }
    }

    /// Call the get_recommendations RPC and return filtered Recommendation objects.
    /// Entries without a poster_path are filtered out.
    func getRecommendations(title: String) async throws -> [Recommendation] {
        guard let url = URL(string: "\(self.baseURL)/rest/v1/rpc/get_recommendations") else {
            throw SupabaseError.invalidURL
        }

        var request = self.makeRequest(url: url, method: "POST")
        let body: [String: String] = ["query_title": title]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let data = try await self.execute(request)

        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            throw SupabaseError.decodingError("Expected array of objects")
        }

        return rows.compactMap { row -> Recommendation? in
            guard let title = row["title"] as? String,
                  let posterPath = row["poster_path"] as? String else {
                // Filter out entries without poster_path
                return nil
            }

            let year = row["year"] as? Int ?? 0
            let voteAverage = row["vote_average"] as? Double ?? 0.0
            let trailerKey = row["trailer_key"] as? String
            let originalLanguage = row["original_language"] as? String ?? "en"

            return Recommendation(
                title: title,
                year: year,
                imdbScore: voteAverage,
                score: voteAverage,
                posterPath: posterPath,
                trailerKey: trailerKey,
                originalLanguage: originalLanguage
            )
        }
    }
}
