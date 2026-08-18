package com.sanjaybhat.filmrecommend.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanjaybhat.filmrecommend.model.Recommendation

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
                text = "Content-Based • TMDb 5000",
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

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.recommendations) { rec ->
                                RecommendationCard(rec)
                            }
                        }
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
