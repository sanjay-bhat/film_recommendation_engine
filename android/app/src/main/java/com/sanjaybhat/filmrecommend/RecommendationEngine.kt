package com.sanjaybhat.filmrecommend

import android.content.Context
import com.sanjaybhat.filmrecommend.model.Movie
import com.sanjaybhat.filmrecommend.model.Recommendation
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.exp
import kotlin.math.sqrt

class RecommendationEngine(private val context: Context) {

    private var movies = listOf<Movie>()
    private var loaded = false

    fun load() {
        if (loaded) return
        val creditMap = loadCredits()
        movies = loadMovies(creditMap)
        loaded = true
    }

    fun isLoaded() = loaded

    fun allTitles(): List<String> = movies.map { it.title }

    fun searchTitles(query: String): List<Pair<Int, String>> {
        if (query.isBlank()) return emptyList()
        val lower = query.lowercase()
        return movies
            .filter { it.title.lowercase().contains(lower) }
            .take(20)
            .map { it.index to it.title }
    }

    fun recommend(movieIndex: Int, dedup: Boolean = true): List<Recommendation> {
        val entry = movies[movieIndex]
        val neighbors = findNeighbors(entry)
        val params = extractParameters(neighbors)
        var selected = addToSelection(emptyList(), params, entry.title)
        if (dedup) selected = removeSequels(selected)
        selected = addToSelection(selected, params, entry.title)
        return selected.take(5)
    }

    fun recommendByTitle(title: String, dedup: Boolean = true): List<Recommendation> {
        val idx = movies.indexOfFirst { it.title.equals(title, ignoreCase = true) }
        if (idx < 0) return emptyList()
        return recommend(idx, dedup)
    }

    private fun findNeighbors(entry: Movie): List<Movie> {
        val features = mutableSetOf<String>()
        features.addAll(entry.genres)
        if (entry.directorName.isNotBlank()) features.add("d:${entry.directorName}")
        if (entry.actor1.isNotBlank()) features.add("a:${entry.actor1}")
        if (entry.actor2.isNotBlank()) features.add("a:${entry.actor2}")
        if (entry.actor3.isNotBlank()) features.add("a:${entry.actor3}")
        features.addAll(entry.keywords.map { "k:$it" })

        val featureList = features.toList()

        fun vectorize(m: Movie): DoubleArray {
            val vec = DoubleArray(featureList.size)
            for ((i, f) in featureList.withIndex()) {
                val has = when {
                    f.startsWith("d:") -> m.directorName == f.removePrefix("d:")
                    f.startsWith("a:") -> f.removePrefix("a:") in listOf(m.actor1, m.actor2, m.actor3)
                    f.startsWith("k:") -> f.removePrefix("k:") in m.keywords
                    else -> f in m.genres
                }
                vec[i] = if (has) 1.0 else 0.0
            }
            return vec
        }

        val entryVec = vectorize(entry)
        val distances = movies.map { m ->
            val v = vectorize(m)
            var sum = 0.0
            for (i in v.indices) {
                val diff = entryVec[i] - v[i]
                sum += diff * diff
            }
            m to sqrt(sum)
        }

        return distances.sortedBy { it.second }.take(31).map { it.first }
    }

    private fun extractParameters(neighbors: List<Movie>): List<Recommendation> {
        if (neighbors.isEmpty()) return emptyList()
        val main = neighbors[0]
        val maxVotes = neighbors.maxOf { it.numVotedUsers }.toDouble()

        return neighbors.map { m ->
            val yearFactor = if (main.titleYear > 0 && m.titleYear > 0)
                gaussian(main.titleYear.toDouble(), m.titleYear.toDouble(), 20.0) else 1.0
            val voteFactor = if (maxVotes > 0)
                gaussian(m.numVotedUsers.toDouble(), maxVotes, maxVotes) else 0.0
            val score = if (isSequel(main.title, m.title)) 0.0
            else m.imdbScore * m.imdbScore * yearFactor * voteFactor

            Recommendation(m.title, m.titleYear, m.imdbScore, score)
        }.sortedByDescending { it.score }
    }

    private fun addToSelection(
        current: List<Recommendation>,
        candidates: List<Recommendation>,
        mainTitle: String
    ): List<Recommendation> {
        val result = current.toMutableList()
        for (c in candidates) {
            if (result.size >= 5) break
            if (c.title.equals(mainTitle, ignoreCase = true)) continue
            val isDuplicate = result.any { it.title == c.title || isSequel(it.title, c.title) }
            if (!isDuplicate) result.add(c)
        }
        return result
    }

    private fun removeSequels(selection: List<Recommendation>): List<Recommendation> {
        val toRemove = mutableSetOf<String>()
        for (i in selection.indices) {
            for (j in i + 1 until selection.size) {
                if (isSequel(selection[i].title, selection[j].title)) {
                    toRemove.add(
                        if (selection[i].year < selection[j].year) selection[j].title
                        else selection[i].title
                    )
                }
            }
        }
        return selection.filter { it.title !in toRemove }
    }

    private fun gaussian(x: Double, center: Double, sigma: Double): Double {
        val diff = x - center
        return exp(-(diff * diff) / (2.0 * sigma * sigma))
    }

    private fun isSequel(a: String, b: String): Boolean {
        return fuzzyRatio(a, b) > 50 || tokenSetRatio(a, b) > 50
    }

    private fun fuzzyRatio(a: String, b: String): Int {
        val s1 = a.lowercase()
        val s2 = b.lowercase()
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 100
        val dist = levenshtein(s1, s2)
        return ((1.0 - dist.toDouble() / maxLen) * 100).toInt()
    }

    private fun tokenSetRatio(a: String, b: String): Int {
        val t1 = a.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }.toSet()
        val t2 = b.lowercase().split(Regex("\\W+")).filter { it.isNotBlank() }.toSet()
        val intersection = t1.intersect(t2).sorted().joinToString(" ")
        val rest1 = (t1 - t2).sorted().joinToString(" ")
        val rest2 = (t2 - t1).sorted().joinToString(" ")
        val combined1 = "$intersection $rest1".trim()
        val combined2 = "$intersection $rest2".trim()
        return maxOf(
            fuzzyRatio(intersection, combined1),
            fuzzyRatio(intersection, combined2),
            fuzzyRatio(combined1, combined2)
        )
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    private fun loadMovies(creditMap: Map<Int, Triple<String, String, String>>): List<Movie> {
        val result = mutableListOf<Movie>()
        val reader = BufferedReader(InputStreamReader(context.assets.open("tmdb_5000_movies.csv")))
        val header = parseCsvLine(reader.readLine())
        val col = header.withIndex().associate { (i, v) -> v to i }

        var idx = 0
        reader.forEachLine { line ->
            val fields = parseCsvLine(line)
            if (fields.size > (col["vote_average"] ?: 0)) {
                val id = fields[col["id"]!!].toIntOrNull() ?: return@forEachLine
                val title = fields[col["title"]!!]
                val genres = parseJsonNames(fields[col["genres"]!!])
                val keywords = parseJsonNames(fields[col["keywords"]!!])
                val score = fields[col["vote_average"]!!].toDoubleOrNull() ?: 0.0
                val votes = fields[col["vote_count"]!!].toIntOrNull() ?: 0
                val year = fields[col["release_date"]!!].take(4).toIntOrNull() ?: 0
                val credit = creditMap[id]

                result.add(
                    Movie(
                        index = idx,
                        title = title,
                        genres = genres,
                        keywords = keywords,
                        directorName = credit?.first ?: "",
                        actor1 = credit?.second ?: "",
                        actor2 = credit?.third ?: "",
                        imdbScore = score,
                        numVotedUsers = votes,
                        titleYear = year,
                        actor3 = ""
                    )
                )
                idx++
            }
        }
        reader.close()
        return result
    }

    private fun loadCredits(): Map<Int, Triple<String, String, String>> {
        val map = mutableMapOf<Int, Triple<String, String, String>>()
        val reader = BufferedReader(InputStreamReader(context.assets.open("tmdb_5000_credits.csv")))
        val header = parseCsvLine(reader.readLine())
        val col = header.withIndex().associate { (i, v) -> v to i }

        reader.forEachLine { line ->
            val fields = parseCsvLine(line)
            if (fields.size > (col["crew"] ?: 0)) {
                val id = fields[col["movie_id"]!!].toIntOrNull() ?: return@forEachLine
                val cast = fields[col["cast"]!!]
                val crew = fields[col["crew"]!!]
                val director = extractDirector(crew)
                val actors = extractActors(cast, 2)
                map[id] = Triple(director, actors.getOrElse(0) { "" }, actors.getOrElse(1) { "" })
            }
        }
        reader.close()
        return map
    }

    private fun parseJsonNames(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractDirector(crewJson: String): String {
        return try {
            val arr = JSONArray(crewJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("job") == "Director") return obj.getString("name")
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractActors(castJson: String, count: Int): List<String> {
        return try {
            val arr = JSONArray(castJson)
            (0 until minOf(count, arr.length())).map { arr.getJSONObject(it).getString("name") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
