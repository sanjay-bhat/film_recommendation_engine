package com.sanjaybhat.filmrecommend.tablet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sanjaybhat.filmrecommend.tablet.model.PosterData
import com.sanjaybhat.filmrecommend.tablet.model.Recommendation
import com.sanjaybhat.filmrecommend.tablet.model.TMDB_IMG_BASE

private val GoldAccent = Color(0xFFC4A35A)
private val GoldMuted = Color(0xFFA08050)
private val TextPrimary = Color(0xFFD4CFC8)
private val TextSecondary = Color(0xFF888888)
private val DarkBg = Color(0xFF08080C)
private val CardBg = Color(0xFF111118)
private val SurfaceBg = Color(0xFF1A1A22)

private fun ratingColor(score: Double): Color = when {
    score >= 8.0 -> Color(0xFFC4A35A)
    score >= 7.0 -> Color(0xFF8A9E8A)
    else -> Color(0xFF8A7E6E)
}

@Composable
fun TabletSearchScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Left sidebar — search pane
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .background(DarkBg)
                .padding(16.dp)
        ) {
            SidebarContent(state, viewModel)
        }

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(SurfaceBg)
        )

        // Right detail — recommendations
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(DarkBg)
        ) {
            DetailContent(state, viewModel)
        }
    }
}

@Composable
private fun SidebarContent(state: UiState, viewModel: MainViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HeaderSection() }

        if (state.loading) {
            item { LoadingSection() }
        } else {
            item { SearchField(state, viewModel) }

            if (state.suggestions.isNotEmpty()) {
                items(state.suggestions, key = { it.second }) { (index, title) ->
                    SuggestionRow(title, state.query) { viewModel.onMovieSelected(index, title) }
                }
            }

            if (state.otdMovies.isNotEmpty() && !state.otdDismissed && state.selectedMovie.isEmpty()) {
                item { OtdSection(state.otdMovies) { viewModel.onMovieSelected(-1, it) } }
            }

            if (state.searchHistory.isNotEmpty() && state.selectedMovie.isEmpty()) {
                item { HistorySection(state.searchHistory) { viewModel.onMovieSelected(-1, it) } }
            }

            if (state.selectedMovie.isEmpty()) {
                item { SurpriseMeButton { viewModel.surpriseMe() } }
            }
        }
    }
}

@Composable
private fun DetailContent(state: UiState, viewModel: MainViewModel) {
    if (state.loading) {
        LoadingSection()
    } else if (state.selectedMovie.isNotEmpty() && state.filteredRecommendations.isNotEmpty()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Because you liked", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(state.selectedMovie, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = GoldAccent)
                    Spacer(Modifier.height(8.dp))
                    if (state.industries.size > 1) {
                        IndustryFilters(state.industries, state.selectedIndustries, viewModel::toggleIndustry, viewModel::selectAllIndustries)
                    }
                }
            }

            items(state.filteredRecommendations, key = { it.title }) { rec ->
                PosterCard(rec) { viewModel.drillInto(rec.title) }
            }

            state.subLevels.forEachIndexed { levelIdx, level ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SubLevelHeader(levelIdx, level) { viewModel.exitSubLevels() }
                }
                items(level.filteredRecs, key = { "${level.fromTitle}_${it.title}" }) { rec ->
                    PosterCard(rec) { viewModel.drillInto(rec.title) }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FILM RECOMMEND", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = GoldAccent)
                Spacer(Modifier.height(8.dp))
                Text("Search or tap a movie to see recommendations", fontSize = 14.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Text("FILM RECOMMEND", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = GoldAccent)
        Text("Semantic Search • TMDb 5000", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = GoldMuted, letterSpacing = 2.sp)
    }
}

@Composable
private fun LoadingSection() {
    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = GoldAccent)
            Spacer(Modifier.height(16.dp))
            Text("Loading movies...", fontSize = 14.sp, color = GoldMuted)
        }
    }
}

@Composable
private fun SearchField(state: UiState, viewModel: MainViewModel) {
    OutlinedTextField(
        value = state.query,
        onValueChange = { viewModel.onQueryChanged(it) },
        placeholder = { Text("Search for a movie", color = TextSecondary) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = GoldAccent) },
        trailingIcon = {
            if (state.query.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearSelection() }) {
                    Icon(Icons.Default.Clear, null, tint = GoldMuted)
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = CardBg,
            unfocusedContainerColor = CardBg,
            focusedBorderColor = GoldAccent,
            unfocusedBorderColor = SurfaceBg
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SuggestionRow(title: String, query: String, onClick: () -> Unit) {
    val annotated = buildAnnotatedString {
        val idx = title.lowercase().indexOf(query.lowercase())
        if (idx >= 0) {
            withStyle(SpanStyle(color = TextPrimary)) { append(title.substring(0, idx)) }
            withStyle(SpanStyle(color = GoldAccent, fontWeight = FontWeight.Bold)) { append(title.substring(idx, idx + query.length)) }
            withStyle(SpanStyle(color = TextPrimary)) { append(title.substring(idx + query.length)) }
        } else {
            withStyle(SpanStyle(color = TextPrimary)) { append(title) }
        }
    }
    Text(
        annotated,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(CardBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        fontSize = 14.sp
    )
}

@Composable
private fun OtdSection(movies: List<OtdItem>, onSelect: (String) -> Unit) {
    Column {
        Text("ON THIS DAY", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = GoldAccent.copy(alpha = 0.5f), letterSpacing = 0.8.sp)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(movies, key = { it.title }) { movie ->
                Column(
                    modifier = Modifier.width(70.dp).clickable { onSelect(movie.title) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (movie.posterPath != null) {
                        AsyncImage(
                            model = "${TMDB_IMG_BASE}w154${movie.posterPath}",
                            contentDescription = movie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(60.dp, 90.dp).clip(RoundedCornerShape(6.dp))
                        )
                    }
                    Text(movie.title, fontSize = 10.sp, color = TextPrimary, maxLines = 2, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun HistorySection(history: List<String>, onSelect: (String) -> Unit) {
    Column {
        Text("RECENT", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        history.forEach { title ->
            Text(
                title,
                fontSize = 13.sp,
                color = GoldAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(title) }
                    .background(GoldAccent.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SurpriseMeButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000)),
            modifier = Modifier.size(120.dp)
        ) {
            Text("SURPRISE\nME", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
private fun IndustryFilters(industries: List<String>, selected: Set<String>, onToggle: (String) -> Unit, onAll: () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val allSelected = selected.size == industries.size
        val greenColor = Color(0xFF3FB96A)
        FilterChip(allSelected, "All", greenColor) { onAll() }
        industries.forEachIndexed { i, name ->
            val color = when (i) { 0 -> GoldAccent; 1 -> Color(0xFFB4B4BE); else -> Color(0xFFB07A50) }
            FilterChip(selected.contains(name), name, color) { onToggle(name) }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun FilterChip(selected: Boolean, label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) color.copy(alpha = 0.15f) else Color.Transparent,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.linearGradient(listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0.4f)))
        )
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = color.copy(alpha = if (selected) 1f else 0.3f),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PosterCard(rec: Recommendation, onDrillIn: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val posterPath = rec.posterPath ?: PosterData.posters[rec.title]
        if (posterPath != null) {
            AsyncImage(
                model = "${TMDB_IMG_BASE}w342$posterPath",
                contentDescription = rec.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(160.dp, 240.dp).clip(RoundedCornerShape(12.dp))
            )
        } else {
            Box(
                Modifier.size(160.dp, 240.dp).clip(RoundedCornerShape(12.dp)).background(
                    Brush.verticalGradient(listOf(SurfaceBg, CardBg))
                ),
                contentAlignment = Alignment.Center
            ) {
                Text("🎬", fontSize = 36.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(rec.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, textAlign = TextAlign.Center, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (rec.year > 0) Text("${rec.year}", fontSize = 11.sp, color = TextSecondary)
            val rc = ratingColor(rec.imdbScore)
            Text(
                "★ ${"%.1f".format(rec.imdbScore)}",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = rc,
                modifier = Modifier.background(rc.copy(alpha = 0.2f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rec.trailerKey != null) {
                TextButton(onClick = { uriHandler.openUri("https://www.youtube.com/watch?v=${rec.trailerKey}") }, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFCC0000), modifier = Modifier.size(14.dp))
                    Text("Trailer", fontSize = 11.sp, color = Color(0xFFCC0000))
                }
            }
            TextButton(onClick = onDrillIn, contentPadding = PaddingValues(0.dp)) {
                Text("Similar →", fontSize = 11.sp, color = GoldAccent)
            }
        }
    }
}

@Composable
private fun SubLevelHeader(levelIdx: Int, level: SubLevel, onExit: () -> Unit) {
    Column {
        Divider(color = SurfaceBg, modifier = Modifier.padding(vertical = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LEVEL ${levelIdx + 2}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = GoldAccent.copy(alpha = 0.6f), letterSpacing = 2.sp)
                Row {
                    Text("from ", fontSize = 11.sp, color = TextSecondary)
                    Text(level.fromTitle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GoldAccent)
                }
            }
            TextButton(onClick = onExit) {
                Text("⬅ EXIT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE04040), letterSpacing = 2.sp)
            }
        }
    }
}
