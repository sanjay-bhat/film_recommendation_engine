import Foundation

enum SupabaseError: Error, LocalizedError {
    case invalidURL
    case httpError(Int)
    case decodingError(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid Supabase URL"
        case .httpError(let code): return "HTTP error \(code)"
        case .decodingError(let detail): return "Decoding error: \(detail)"
        }
    }
}

struct SupabaseClient {

    private let baseURL = "https://labwvnsunfhswkmlvisl.supabase.co"
    private let anonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxhYnd2bnN1bmZoc3drbWx2aXNsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODczNjk5NDksImV4cCI6MjEwMjk0NTk0OX0.bmaEevB0AP-GgSy3LPX2eorNLxSzTLHWQpD4Veuyg9U"
    private let pageSize = 1000

    private func makeRequest(url: URL, method: String = "GET") -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("Bearer \(anonKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        return request
    }

    private func execute(_ request: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw SupabaseError.httpError(0) }
        guard (200..<300).contains(http.statusCode) else { throw SupabaseError.httpError(http.statusCode) }
        return data
    }

    func fetchAllTitles() async throws -> [String] {
        var allTitles: [String] = []
        var offset = 0
        while true {
            guard let url = URL(string: "\(baseURL)/rest/v1/movies?select=title&order=title&limit=\(pageSize)&offset=\(offset)") else {
                throw SupabaseError.invalidURL
            }
            let data = try await execute(makeRequest(url: url))
            guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
                throw SupabaseError.decodingError("Expected array")
            }
            allTitles.append(contentsOf: rows.compactMap { $0["title"] as? String })
            if rows.count < pageSize { break }
            offset += pageSize
        }
        return allTitles
    }

    struct OtdMovie {
        let title: String
        let year: String
        let posterPath: String?
    }

    func moviesOnThisDay(month: Int, day: Int) async throws -> [OtdMovie] {
        guard let url = URL(string: "\(baseURL)/rest/v1/rpc/movies_on_this_day") else { throw SupabaseError.invalidURL }
        var request = makeRequest(url: url, method: "POST")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["mm": month, "dd": day])
        let data = try await execute(request)
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return rows.map { row in
            let releaseDate = row["release_date"] as? String ?? ""
            return OtdMovie(
                title: row["title"] as? String ?? "",
                year: releaseDate.count >= 4 ? String(releaseDate.prefix(4)) : "",
                posterPath: row["poster_path"] as? String
            )
        }
    }

    func getRecommendations(title: String) async throws -> [Recommendation] {
        guard let url = URL(string: "\(baseURL)/rest/v1/rpc/get_recommendations") else { throw SupabaseError.invalidURL }
        var request = makeRequest(url: url, method: "POST")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["query_title": title])
        let data = try await execute(request)
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            throw SupabaseError.decodingError("Expected array")
        }
        return rows.compactMap { row -> Recommendation? in
            guard let title = row["title"] as? String,
                  let posterPath = row["poster_path"] as? String else { return nil }
            return Recommendation(
                title: title,
                year: row["year"] as? Int ?? 0,
                imdbScore: row["vote_average"] as? Double ?? 0.0,
                score: row["vote_average"] as? Double ?? 0.0,
                posterPath: posterPath,
                trailerKey: row["trailer_key"] as? String,
                originalLanguage: row["original_language"] as? String ?? "en"
            )
        }
    }
}
