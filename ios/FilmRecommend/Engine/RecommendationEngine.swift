import Foundation

class RecommendationEngine {

    private var movies: [Movie] = []
    private(set) var isLoaded = false

    func load() {
        guard !isLoaded else { return }
        let creditMap = loadCredits()
        movies = loadMovies(creditMap: creditMap)
        isLoaded = true
    }

    func allTitles() -> [String] {
        movies.map { $0.title }
    }

    func searchTitles(query: String) -> [(Int, String)] {
        guard query.count >= 2 else { return [] }
        let lower = query.lowercased()
        return movies
            .filter { $0.title.lowercased().contains(lower) }
            .prefix(20)
            .map { ($0.index, $0.title) }
    }

    func recommend(movieIndex: Int, dedup: Bool = true) -> [Recommendation] {
        let entry = movies[movieIndex]
        let neighbors = findNeighbors(entry: entry)
        let params = extractParameters(neighbors: neighbors, mainTitle: entry.title, mainYear: entry.titleYear)
        var selected = addToSelection(current: [], candidates: params, mainTitle: entry.title)
        if dedup { selected = removeSequels(selection: selected) }
        selected = addToSelection(current: selected, candidates: params, mainTitle: entry.title)
        return Array(selected.prefix(5))
    }

    // MARK: - KNN

    private func findNeighbors(entry: Movie) -> [Movie] {
        var features: [String] = []
        features.append(contentsOf: entry.genres)
        if !entry.directorName.isEmpty { features.append("d:\(entry.directorName)") }
        if !entry.actor1.isEmpty { features.append("a:\(entry.actor1)") }
        if !entry.actor2.isEmpty { features.append("a:\(entry.actor2)") }
        if !entry.actor3.isEmpty { features.append("a:\(entry.actor3)") }
        features.append(contentsOf: entry.keywords.map { "k:\($0)" })

        func vectorize(_ m: Movie) -> [Double] {
            features.map { f -> Double in
                let has: Bool
                if f.hasPrefix("d:") {
                    has = m.directorName == String(f.dropFirst(2))
                } else if f.hasPrefix("a:") {
                    let name = String(f.dropFirst(2))
                    has = [m.actor1, m.actor2, m.actor3].contains(name)
                } else if f.hasPrefix("k:") {
                    has = m.keywords.contains(String(f.dropFirst(2)))
                } else {
                    has = m.genres.contains(f)
                }
                return has ? 1.0 : 0.0
            }
        }

        let entryVec = vectorize(entry)
        let distances: [(Movie, Double)] = movies.map { m in
            let v = vectorize(m)
            let sum = zip(entryVec, v).reduce(0.0) { acc, pair in
                let diff = pair.0 - pair.1
                return acc + diff * diff
            }
            return (m, sum.squareRoot())
        }

        return distances.sorted { $0.1 < $1.1 }.prefix(31).map { $0.0 }
    }

    // MARK: - Scoring

    private func extractParameters(neighbors: [Movie], mainTitle: String, mainYear: Int) -> [Recommendation] {
        guard !neighbors.isEmpty else { return [] }
        let maxVotes = Double(neighbors.map { $0.numVotedUsers }.max() ?? 1)

        return neighbors.map { m in
            let yearFactor = (mainYear > 0 && m.titleYear > 0)
                ? gaussian(x: Double(mainYear), center: Double(m.titleYear), sigma: 20.0) : 1.0
            let voteFactor = maxVotes > 0
                ? gaussian(x: Double(m.numVotedUsers), center: maxVotes, sigma: maxVotes) : 0.0
            let score = isSequel(mainTitle, m.title)
                ? 0.0 : m.imdbScore * m.imdbScore * yearFactor * voteFactor

            return Recommendation(title: m.title, year: m.titleYear, imdbScore: m.imdbScore, score: score)
        }.sorted { $0.score > $1.score }
    }

    private func addToSelection(current: [Recommendation], candidates: [Recommendation], mainTitle: String) -> [Recommendation] {
        var result = current
        for c in candidates {
            guard result.count < 5 else { break }
            if c.title.caseInsensitiveCompare(mainTitle) == .orderedSame { continue }
            let isDuplicate = result.contains { $0.title == c.title || isSequel($0.title, c.title) }
            if !isDuplicate { result.append(c) }
        }
        return result
    }

    private func removeSequels(selection: [Recommendation]) -> [Recommendation] {
        var toRemove = Set<String>()
        for i in selection.indices {
            for j in (i + 1)..<selection.count {
                if isSequel(selection[i].title, selection[j].title) {
                    toRemove.insert(selection[i].year < selection[j].year ? selection[j].title : selection[i].title)
                }
            }
        }
        return selection.filter { !toRemove.contains($0.title) }
    }

    // MARK: - Fuzzy matching

    private func gaussian(x: Double, center: Double, sigma: Double) -> Double {
        let diff = x - center
        return exp(-(diff * diff) / (2.0 * sigma * sigma))
    }

    private func isSequel(_ a: String, _ b: String) -> Bool {
        fuzzyRatio(a, b) > 50 || tokenSetRatio(a, b) > 50
    }

    private func fuzzyRatio(_ a: String, _ b: String) -> Int {
        let s1 = a.lowercased(), s2 = b.lowercased()
        let maxLen = max(s1.count, s2.count)
        guard maxLen > 0 else { return 100 }
        let dist = levenshtein(s1, s2)
        return Int((1.0 - Double(dist) / Double(maxLen)) * 100)
    }

    private func tokenSetRatio(_ a: String, _ b: String) -> Int {
        let t1 = Set(a.lowercased().components(separatedBy: .alphanumerics.inverted).filter { !$0.isEmpty })
        let t2 = Set(b.lowercased().components(separatedBy: .alphanumerics.inverted).filter { !$0.isEmpty })
        let intersection = t1.intersection(t2).sorted().joined(separator: " ")
        let rest1 = (t1.subtracting(t2)).sorted().joined(separator: " ")
        let rest2 = (t2.subtracting(t1)).sorted().joined(separator: " ")
        let combined1 = "\(intersection) \(rest1)".trimmingCharacters(in: .whitespaces)
        let combined2 = "\(intersection) \(rest2)".trimmingCharacters(in: .whitespaces)
        return max(fuzzyRatio(intersection, combined1),
                   fuzzyRatio(intersection, combined2),
                   fuzzyRatio(combined1, combined2))
    }

    private func levenshtein(_ a: String, _ b: String) -> Int {
        let a = Array(a), b = Array(b)
        var dp = Array(repeating: Array(repeating: 0, count: b.count + 1), count: a.count + 1)
        for i in 0...a.count { dp[i][0] = i }
        for j in 0...b.count { dp[0][j] = j }
        for i in 1...a.count {
            for j in 1...b.count {
                let cost = a[i - 1] == b[j - 1] ? 0 : 1
                dp[i][j] = min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.count][b.count]
    }

    // MARK: - CSV Loading

    private func loadMovies(creditMap: [Int: (String, String, String)]) -> [Movie] {
        guard let url = Bundle.main.url(forResource: "tmdb_5000_movies", withExtension: "csv"),
              let data = try? String(contentsOf: url, encoding: .utf8) else { return [] }

        let lines = data.components(separatedBy: .newlines).filter { !$0.isEmpty }
        guard let headerLine = lines.first else { return [] }
        let header = parseCsvLine(headerLine)
        let col = Dictionary(uniqueKeysWithValues: header.enumerated().map { ($1, $0) })

        var result: [Movie] = []
        for (idx, line) in lines.dropFirst().enumerated() {
            let fields = parseCsvLine(line)
            guard fields.count > (col["vote_average"] ?? 0) else { continue }
            guard let id = Int(fields[col["id"]!]) else { continue }

            let title = fields[col["title"]!]
            let genres = parseJsonNames(fields[col["genres"]!])
            let keywords = parseJsonNames(fields[col["keywords"]!])
            let score = Double(fields[col["vote_average"]!]) ?? 0
            let votes = Int(fields[col["vote_count"]!]) ?? 0
            let year = Int(String(fields[col["release_date"]!].prefix(4))) ?? 0
            let credit = creditMap[id]

            result.append(Movie(
                index: idx, title: title, genres: genres, keywords: keywords,
                directorName: credit?.0 ?? "", actor1: credit?.1 ?? "",
                actor2: credit?.2 ?? "", actor3: "",
                imdbScore: score, numVotedUsers: votes, titleYear: year
            ))
        }
        return result
    }

    private func loadCredits() -> [Int: (String, String, String)] {
        guard let url = Bundle.main.url(forResource: "tmdb_5000_credits", withExtension: "csv"),
              let data = try? String(contentsOf: url, encoding: .utf8) else { return [:] }

        let lines = data.components(separatedBy: .newlines).filter { !$0.isEmpty }
        guard let headerLine = lines.first else { return [:] }
        let header = parseCsvLine(headerLine)
        let col = Dictionary(uniqueKeysWithValues: header.enumerated().map { ($1, $0) })

        var map: [Int: (String, String, String)] = [:]
        for line in lines.dropFirst() {
            let fields = parseCsvLine(line)
            guard fields.count > (col["crew"] ?? 0),
                  let id = Int(fields[col["movie_id"]!]) else { continue }

            let director = extractDirector(fields[col["crew"]!])
            let actors = extractActors(fields[col["cast"]!], count: 2)
            map[id] = (director, actors.count > 0 ? actors[0] : "", actors.count > 1 ? actors[1] : "")
        }
        return map
    }

    private func parseJsonNames(_ json: String) -> [String] {
        guard let data = json.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return arr.compactMap { $0["name"] as? String }
    }

    private func extractDirector(_ json: String) -> String {
        guard let data = json.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return "" }
        return arr.first { ($0["job"] as? String) == "Director" }?["name"] as? String ?? ""
    }

    private func extractActors(_ json: String, count: Int) -> [String] {
        guard let data = json.data(using: .utf8),
              let arr = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return arr.prefix(count).compactMap { $0["name"] as? String }
    }

    private func parseCsvLine(_ line: String) -> [String] {
        var fields: [String] = []
        var current = ""
        var inQuotes = false
        let chars = Array(line)
        var i = 0
        while i < chars.count {
            let c = chars[i]
            if c == "\"" && !inQuotes {
                inQuotes = true
            } else if c == "\"" && inQuotes {
                if i + 1 < chars.count && chars[i + 1] == "\"" {
                    current.append("\"")
                    i += 1
                } else {
                    inQuotes = false
                }
            } else if c == "," && !inQuotes {
                fields.append(current)
                current = ""
            } else {
                current.append(c)
            }
            i += 1
        }
        fields.append(current)
        return fields
    }
}
