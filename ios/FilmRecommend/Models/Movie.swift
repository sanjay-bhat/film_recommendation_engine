import Foundation

struct Movie {
    let index: Int
    let title: String
    let genres: [String]
    let keywords: [String]
    let directorName: String
    let actor1: String
    let actor2: String
    let actor3: String
    let imdbScore: Double
    let numVotedUsers: Int
    let titleYear: Int
}

struct Recommendation: Identifiable {
    let id = UUID()
    let title: String
    let year: Int
    let imdbScore: Double
    let score: Double
}
