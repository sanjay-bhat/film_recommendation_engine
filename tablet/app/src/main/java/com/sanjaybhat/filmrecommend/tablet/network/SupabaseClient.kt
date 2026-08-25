package com.sanjaybhat.filmrecommend.tablet.network

import com.sanjaybhat.filmrecommend.tablet.model.Recommendation
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SupabaseClient {

    private const val BASE_URL = "https://labwvnsunfhswkmlvisl.supabase.co"
    private const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxhYnd2bnN1bmZoc3drbWx2aXNsIiwi" +
        "cm9sZSI6ImFub24iLCJpYXQiOjE3ODczNjk5NDksImV4cCI6MjEwMjk0NTk0OX0." +
        "bmaEevB0AP-GgSy3LPX2eorNLxSzTLHWQpD4Veuyg9U"
    private const val PAGE_SIZE = 1000

    fun fetchAllTitles(): List<String> {
        val titles = mutableListOf<String>()
        var offset = 0
        while (true) {
            val url = URL("$BASE_URL/rest/v1/movies?select=title&order=title&limit=$PAGE_SIZE&offset=$offset")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", ANON_KEY)
                setRequestProperty("Authorization", "Bearer $ANON_KEY")
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            try {
                val code = conn.responseCode
                if (code != 200) throw RuntimeException("HTTP $code")
                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val arr = JSONArray(body)
                if (arr.length() == 0) break
                for (i in 0 until arr.length()) {
                    titles.add(arr.getJSONObject(i).getString("title"))
                }
                if (arr.length() < PAGE_SIZE) break
                offset += PAGE_SIZE
            } finally {
                conn.disconnect()
            }
        }
        return titles
    }

    fun getRecommendations(title: String): List<Recommendation> {
        val url = URL("$BASE_URL/rest/v1/rpc/get_recommendations")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("apikey", ANON_KEY)
            setRequestProperty("Authorization", "Bearer $ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
        }
        try {
            OutputStreamWriter(conn.outputStream).use {
                it.write(JSONObject().apply { put("query_title", title) }.toString())
            }
            val code = conn.responseCode
            if (code != 200) throw RuntimeException("HTTP $code")
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val arr = JSONArray(body)
            val results = mutableListOf<Recommendation>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val posterPath = if (obj.isNull("poster_path")) null else obj.getString("poster_path")
                if (posterPath == null) continue
                results.add(
                    Recommendation(
                        title = obj.getString("title"),
                        year = obj.optInt("year", 0),
                        imdbScore = obj.optDouble("vote_average", 0.0),
                        score = obj.optDouble("vote_average", 0.0),
                        posterPath = posterPath,
                        trailerKey = if (obj.isNull("trailer_key")) null else obj.getString("trailer_key"),
                        originalLanguage = obj.optString("original_language", "en")
                    )
                )
            }
            return results
        } finally {
            conn.disconnect()
        }
    }

    data class OtdMovie(val title: String, val year: String, val posterPath: String?)

    fun moviesOnThisDay(month: Int, day: Int): List<OtdMovie> {
        val url = URL("$BASE_URL/rest/v1/rpc/movies_on_this_day")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("apikey", ANON_KEY)
            setRequestProperty("Authorization", "Bearer $ANON_KEY")
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
        }
        try {
            OutputStreamWriter(conn.outputStream).use {
                it.write(JSONObject().apply { put("mm", month); put("dd", day) }.toString())
            }
            val code = conn.responseCode
            if (code != 200) return emptyList()
            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            val arr = JSONArray(body)
            val results = mutableListOf<OtdMovie>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val releaseDate = obj.optString("release_date", "")
                results.add(
                    OtdMovie(
                        title = obj.getString("title"),
                        year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else "",
                        posterPath = if (obj.isNull("poster_path")) null else obj.getString("poster_path")
                    )
                )
            }
            return results
        } finally {
            conn.disconnect()
        }
    }
}
