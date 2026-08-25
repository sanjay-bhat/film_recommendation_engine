package com.sanjaybhat.filmrecommend.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sanjaybhat.filmrecommend.model.PosterData
import com.sanjaybhat.filmrecommend.model.Recommendation
import com.sanjaybhat.filmrecommend.model.TMDB_IMG_BASE
import kotlinx.coroutines.launch

private val GoldAccent = Color(0xFFC4A35A)
private val GoldMuted = Color(0xFFA08050)
private val TextPrimary = Color(0xFFD4CFC8)
private val TextSecondary = Color(0xFF888888)
private val DarkBg = Color(0xFF08080C)
private val CardBg = Color(0xFF111118)
private val SurfaceBg = Color(0xFF1A1A22)
private val RatingGold = Color(0xFFC4A35A)
private val RatingGreen = Color(0xFF8A9E8A)
private val RatingBrown = Color(0xFF8A7E6E)

private fun ratingColor(score: Double): Color = when {
    score >= 8.0 -> RatingGold
    score >= 7.0 -> RatingGreen
    else -> RatingBrown
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieSearchScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "FILM RECOMMEND",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = GoldAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Semantic Search • TMDb 5000",
                fontSize = 12.sp,
                color = GoldMuted,
                textAlign = TextAlign.Center,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            if (state.loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = GoldAccent)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(state.loadingMessage, color = GoldMuted, fontSize = 14.sp)
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { viewModel.onQueryChanged(it) },
                    label = { Text("Search for a movie", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Default.Search, "Search", tint = GoldAccent) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Clear, "Clear", tint = GoldMuted)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = SurfaceBg,
                        cursorColor = GoldAccent
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                AnimatedVisibility(visible = state.suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(state.suggestions) { (index, title) ->
                                Text(
                                    text = title,
                                    color = TextPrimary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.onMovieSelected(index, title) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    fontSize = 14.sp
                                )
                                if (index != state.suggestions.last().first) {
                                    HorizontalDivider(color = SurfaceBg)
                                }
                            }
                        }
                    }
                }

                // Search history chips
                AnimatedVisibility(visible = state.searchHistory.isNotEmpty() && state.selectedMovie.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.searchHistory.forEach { title ->
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                color = GoldAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(GoldAccent.copy(alpha = 0.08f))
                                    .border(1.dp, GoldAccent.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                    .clickable {
                                        val index = -1
                                        viewModel.onMovieSelected(index, title)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .widthIn(max = 180.dp)
                            )
                        }
                    }
                }

                // Surprise Me button
                AnimatedVisibility(visible = state.selectedMovie.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { viewModel.surpriseMe() },
                            modifier = Modifier
                                .size(140.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFCC0000)
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 8.dp,
                                pressedElevation = 2.dp
                            )
                        ) {
                            Text(
                                text = "SURPRISE\nME",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                letterSpacing = 1.5.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(visible = state.selectedMovie.isNotEmpty() && state.recommendations.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Because you liked",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = state.selectedMovie,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldAccent,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        CoverFlowCarousel(recommendations = state.recommendations)
                    }
                }

                state.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverFlowCarousel(recommendations: List<Recommendation>) {
    val scope = rememberCoroutineScope()
    val animatable = remember { Animatable(0f) }
    val currentIndex by remember { derivedStateOf { animatable.value.toInt().coerceIn(0, (recommendations.size - 1).coerceAtLeast(0)) } }
    val density = LocalDensity.current

    LaunchedEffect(recommendations) {
        animatable.snapTo(0f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .pointerInput(recommendations.size) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = animatable.value
                                    .toInt()
                                    .coerceIn(0, recommendations.size - 1)
                                    .toFloat()
                                animatable.animateTo(
                                    targetValue = target,
                                    animationSpec = spring(
                                        dampingRatio = 0.7f,
                                        stiffness = 300f
                                    )
                                )
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            scope.launch {
                                val newValue = (animatable.value - dragAmount / 300f)
                                    .coerceIn(0f, (recommendations.size - 1).toFloat())
                                animatable.snapTo(newValue)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val indices = recommendations.indices.toList()
            val sortedIndices = indices.sortedBy { index ->
                val offset = index - animatable.value
                -kotlin.math.abs(offset)
            }

            sortedIndices.forEach { index ->
                val offset = index - animatable.value

                if (kotlin.math.abs(offset) <= 3f) {
                    val absOffset = kotlin.math.abs(offset)

                    val angle = when {
                        absOffset < 0.01f -> 0f
                        offset < 0 -> 45f
                        else -> -45f
                    } * absOffset.coerceAtMost(1f)

                    val scale = when {
                        absOffset < 0.01f -> 1.0f
                        absOffset <= 1f -> 1.0f - 0.2f * absOffset
                        else -> 0.7f + 0.1f * (1f - (absOffset - 1f).coerceAtMost(1f))
                    }

                    val translationXDp = when {
                        absOffset < 0.01f -> 0f
                        absOffset <= 1f -> offset * 170f
                        else -> {
                            val sign = if (offset > 0) 1f else -1f
                            sign * (170f + (absOffset - 1f) * 110f)
                        }
                    }

                    val alpha = when {
                        absOffset < 0.01f -> 1f
                        absOffset <= 1f -> 1f - 0.2f * absOffset
                        absOffset <= 2f -> 0.6f - 0.2f * (absOffset - 1f)
                        else -> 0.3f
                    }

                    val rec = recommendations[index]
                    val posterPath = rec.posterPath ?: PosterData.posters[rec.title]

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                this.rotationY = angle
                                this.scaleX = scale
                                this.scaleY = scale
                                this.translationX = translationXDp * density.density
                                this.alpha = alpha
                                this.cameraDistance = 12f * density.density
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (posterPath != null) {
                            AsyncImage(
                                model = "${TMDB_IMG_BASE}w342${posterPath}",
                                contentDescription = rec.title,
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(270.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(270.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                SurfaceBg,
                                                CardBg
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🎬",
                                    fontSize = 48.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (recommendations.isNotEmpty()) {
            val rec = recommendations[currentIndex]
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = rec.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (rec.year > 0) "${rec.year}" else "Unknown",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ratingColor(rec.imdbScore).copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "★ ${rec.imdbScore}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ratingColor(rec.imdbScore)
                    )
                }
            }

            // Trailer button
            if (rec.trailerKey != null) {
                val context = LocalContext.current
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.youtube.com/watch?v=${rec.trailerKey}")
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Watch Trailer",
                        tint = Color(0xFFCC0000),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Watch Trailer",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCC0000)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${currentIndex + 1} / ${recommendations.size}",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RecommendationCard(rec: Recommendation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(SurfaceBg.copy(alpha = 0.3f), GoldAccent.copy(alpha = 0.05f))
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rec.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (rec.year > 0) "${rec.year}" else "Unknown year",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ratingColor(rec.imdbScore).copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "★ ${rec.imdbScore}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ratingColor(rec.imdbScore)
                    )
                }
            }
        }
    }
}
