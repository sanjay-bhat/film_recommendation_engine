package com.sanjaybhat.filmrecommend.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sanjaybhat.filmrecommend.model.PosterData
import com.sanjaybhat.filmrecommend.model.Recommendation
import com.sanjaybhat.filmrecommend.model.TMDB_IMG_BASE
import kotlinx.coroutines.launch

private val NeonCyan = Color(0xFF00E5FF)
private val NeonPink = Color(0xFFFF2D95)
private val NeonPurple = Color(0xFF7B61FF)
private val DarkBg = Color(0xFF0A0015)
private val CardBg = Color(0xFF1A0A2E)
private val SurfaceBg = Color(0xFF2D1B4E)

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
                color = NeonCyan,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Semantic Search • TMDb 5000",
                fontSize = 12.sp,
                color = NeonPink,
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
                        CircularProgressIndicator(color = NeonCyan)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading 4,803 movies...", color = NeonPink, fontSize = 14.sp)
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { viewModel.onQueryChanged(it) },
                    label = { Text("Search for a movie", color = NeonPurple.copy(alpha = 0.7f)) },
                    leadingIcon = { Icon(Icons.Default.Search, "Search", tint = NeonCyan) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Clear, "Clear", tint = NeonPink)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = NeonPurple.copy(alpha = 0.5f),
                        cursorColor = NeonCyan
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
                                    color = Color.White,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.onMovieSelected(index, title) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    fontSize = 14.sp
                                )
                                if (index != state.suggestions.last().first) {
                                    HorizontalDivider(color = NeonPurple.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(visible = state.selectedMovie.isNotEmpty() && state.recommendations.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Because you liked",
                            fontSize = 12.sp,
                            color = NeonPink.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = state.selectedMovie,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
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

    // Reset animation when recommendations change
    LaunchedEffect(recommendations) {
        animatable.snapTo(0f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Cover Flow area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .pointerInput(recommendations.size) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                // Snap to nearest index
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
            // Render posters from back to front for proper z-ordering
            val indices = recommendations.indices.toList()
            val sortedIndices = indices.sortedBy { index ->
                val offset = index - animatable.value
                -kotlin.math.abs(offset)
            }

            sortedIndices.forEach { index ->
                val offset = index - animatable.value

                // Only render nearby posters for performance
                if (kotlin.math.abs(offset) <= 3f) {
                    val absOffset = kotlin.math.abs(offset)

                    // Calculate 3D transform parameters
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
                    val posterPath = PosterData.posters[rec.title]

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
                            // Placeholder when no poster exists
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(270.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                NeonPurple.copy(alpha = 0.4f),
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

        // Movie info below the carousel
        if (recommendations.isNotEmpty()) {
            val rec = recommendations[currentIndex]
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = rec.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
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
                    color = NeonPurple.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (rec.imdbScore >= 7.0) NeonCyan.copy(alpha = 0.2f)
                            else NeonPurple.copy(alpha = 0.2f)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "★ ${rec.imdbScore}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rec.imdbScore >= 7.0) NeonCyan else NeonPurple
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${currentIndex + 1} / ${recommendations.size}",
                fontSize = 12.sp,
                color = NeonPink.copy(alpha = 0.6f),
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
                        colors = listOf(NeonPurple.copy(alpha = 0.1f), NeonCyan.copy(alpha = 0.05f))
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
                        color = Color.White
                    )
                    Text(
                        text = if (rec.year > 0) "${rec.year}" else "Unknown year",
                        fontSize = 12.sp,
                        color = NeonPurple.copy(alpha = 0.7f)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (rec.imdbScore >= 7.0) NeonCyan.copy(alpha = 0.2f)
                            else NeonPurple.copy(alpha = 0.2f)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "★ ${rec.imdbScore}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rec.imdbScore >= 7.0) NeonCyan else NeonPurple
                    )
                }
            }
        }
    }
}
