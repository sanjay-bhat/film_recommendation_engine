package com.sanjaybhat.filmrecommend.wear.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import coil.compose.AsyncImage
import com.sanjaybhat.filmrecommend.wear.model.PosterData
import com.sanjaybhat.filmrecommend.wear.model.TMDB_IMG_BASE

// Cinematic gold palette — matches the design system across all platforms
private val GoldAccent = Color(0xFFC4A35A)
private val DarkBg = Color(0xFF08080C)
private val CardBg = Color(0xFF111118)
private val TextPrimary = Color(0xFFD4D0C8)
private val TextSecondary = Color(0xFF888888)

private fun ratingColor(score: Double): Color = when {
    score >= 8.0 -> GoldAccent
    score >= 7.0 -> Color(0xFF8A9E8A)
    else -> Color(0xFF8A7E6E)
}

@Composable
fun WearBrowseScreen(viewModel: WearViewModel) {
    val currentMovie by viewModel.currentMovie.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val otdMovie by viewModel.otdMovie.collectAsState()
    val context = LocalContext.current

    val listState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        if (isLoading && currentMovie == null) {
            // Initial loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        indicatorColor = GoldAccent,
                        trackColor = CardBg,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Loading...",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        } else if (currentMovie != null) {
            // Movie detail view with swipe navigation
            MovieDetailView(
                viewModel = viewModel,
                context = context
            )
        } else {
            // Home view — Surprise Me prompt
            HomeView(
                viewModel = viewModel,
                otdMovie = otdMovie,
                listState = listState
            )
        }
    }
}

@Composable
private fun HomeView(
    viewModel: WearViewModel,
    otdMovie: com.sanjaybhat.filmrecommend.wear.network.SupabaseClient.OtdMovie?,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState
) {
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            top = 32.dp,
            bottom = 48.dp,
            start = 16.dp,
            end = 16.dp
        )
    ) {
        // Title
        item {
            Text(
                text = "FILM RECOMMEND",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = GoldAccent,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }

        // On This Day card
        if (otdMovie != null) {
            item {
                OtdCard(otdMovie)
            }
        }

        // Surprise Me button
        item {
            Spacer(Modifier.height(12.dp))
            SurpriseMeButton { viewModel.surpriseMe() }
        }
    }
}

@Composable
private fun OtdCard(movie: com.sanjaybhat.filmrecommend.wear.network.SupabaseClient.OtdMovie) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "ON THIS DAY",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = GoldAccent.copy(alpha = 0.6f),
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(6.dp))

        if (movie.posterPath != null) {
            AsyncImage(
                model = "${TMDB_IMG_BASE}w154${movie.posterPath}",
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp, 90.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.height(6.dp))
        }

        Text(
            movie.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (movie.year.isNotEmpty()) {
            Text(
                movie.year,
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SurpriseMeButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Chip(
            onClick = onClick,
            colors = ChipDefaults.chipColors(
                backgroundColor = Color.Transparent
            ),
            border = ChipDefaults.chipBorder(),
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            label = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF4444),
                                    Color(0xFFCC0000),
                                    Color(0xFF8C0000)
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SURPRISE\nME",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )
                }
            }
        )
    }
}

@Composable
private fun MovieDetailView(
    viewModel: WearViewModel,
    context: Context
) {
    val currentMovie by viewModel.currentMovie.collectAsState()
    val movie = currentMovie ?: return
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    val listState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    // Swipe left to go to next, swipe right to go back
                    if (dragAmount < -40f) {
                        vibrator?.vibrate(
                            VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                        viewModel.nextMovie()
                    } else if (dragAmount > 40f) {
                        vibrator?.vibrate(
                            VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                        viewModel.previousMovie()
                    }
                }
            }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = 48.dp,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            // Title header
            item {
                Text(
                    text = "FILM RECOMMEND",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = GoldAccent.copy(alpha = 0.6f),
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Movie poster
            item {
                Spacer(Modifier.height(8.dp))
                val posterPath = movie.posterPath ?: PosterData.posters[movie.title]

                if (posterPath != null) {
                    AsyncImage(
                        model = "${TMDB_IMG_BASE}w342$posterPath",
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp, 180.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    // Fallback when no poster is available
                    Box(
                        modifier = Modifier
                            .size(120.dp, 180.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(CardBg, Color(0xFF1A1A22))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            movie.title,
                            fontSize = 11.sp,
                            color = TextPrimary.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }

            // Movie title
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = movie.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Rating chip and year
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Rating chip
                    val rc = ratingColor(movie.imdbScore)
                    Text(
                        text = String.format("%.1f", movie.imdbScore),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = rc,
                        modifier = Modifier
                            .background(
                                rc.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )

                    if (movie.year > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${movie.year}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Swipe hint
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "swipe for more",
                    fontSize = 9.sp,
                    color = TextSecondary.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Surprise Me again button
            item {
                Spacer(Modifier.height(12.dp))
                Chip(
                    onClick = { viewModel.surpriseMe() },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = Color(0xFFCC0000).copy(alpha = 0.3f)
                    ),
                    label = {
                        Text(
                            "SURPRISE ME",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF4444),
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
