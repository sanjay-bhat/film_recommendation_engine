package com.sanjaybhat.filmrecommend.model

data class Movie(
    val index: Int,
    val title: String,
    val genres: List<String>,
    val keywords: List<String>,
    val directorName: String,
    val actor1: String,
    val actor2: String,
    val actor3: String,
    val imdbScore: Double,
    val numVotedUsers: Int,
    val titleYear: Int
)

data class Recommendation(
    val title: String,
    val year: Int,
    val imdbScore: Double,
    val score: Double
)
