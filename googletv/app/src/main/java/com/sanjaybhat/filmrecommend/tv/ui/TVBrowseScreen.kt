package com.sanjaybhat.filmrecommend.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
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
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import coil.compose.AsyncImage
import com.sanjaybhat.filmrecommend.tv.model.PosterData
import com.sanjaybhat.filmrecommend.tv.model.Recommendation
import com.sanjaybhat.filmrecommend.tv.model.TMDB_IMG_BASE

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
fun TVBrowseScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrEmpty()) {
            viewModel.onQueryChanged(text)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GoldAccent)
                    Spacer(Modifier.height(16.dp))
                    Text("Loading movies...", fontSize = 18.sp, color = GoldMuted)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 48.dp, vertical = 32.dp)
            ) {
                // Header
                Text("FILM RECOMMEND", fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = GoldAccent)
                Text("Semantic Search • TMDb 5000", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = GoldMuted, letterSpacing = 2.sp)
                Spacer(Modifier.height(24.dp))

                // Search
                TVSearchField(state, viewModel, speechLauncher)
                Spacer(Modifier.height(16.dp))

                // Suggestions
                if (state.suggestions.isNotEmpty()) {
                    TVSuggestions(state.suggestions, state.query) { idx, title -> viewModel.onMovieSelected(idx, title) }
                    Spacer(Modifier.height(16.dp))
                }

                // On This Day
                if (state.otdMovies.isNotEmpty() && !state.otdDismissed && state.selectedMovie.isEmpty()) {
                    TVOtdRow(state.otdMovies) { viewModel.onMovieSelected(-1, it) }
                    Spacer(Modifier.height(24.dp))
                }

                // History chips
                if (state.searchHistory.isNotEmpty() && state.selectedMovie.isEmpty()) {
                    TVHistoryRow(state.searchHistory) { viewModel.onMovieSelected(-1, it) }
                    Spacer(Modifier.height(24.dp))
                }

                // Surprise Me
                if (state.selectedMovie.isEmpty()) {
                    TVSurpriseMe { viewModel.surpriseMe() }
                    Spacer(Modifier.height(24.dp))
                }

                // Recommendations
                if (state.selectedMovie.isNotEmpty() && state.filteredRecommendations.isNotEmpty()) {
                    TVRecommendations(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun TVSearchField(
    state: UiState,
    viewModel: MainViewModel,
    speechLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    TextField(
        value = state.query,
        onValueChange = { viewModel.onQueryChanged(it) },
        placeholder = { Text("Search for a movie...", color = TextSecondary, fontSize = 18.sp) },
        trailingIcon = {
            Row {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChanged("") }) {
                        Text("✕", color = TextSecondary, fontSize = 18.sp)
                    }
                }
                IconButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a movie name")
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    speechLauncher.launch(intent)
                }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice search", tint = GoldAccent)
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = CardBg,
            unfocusedContainerColor = CardBg,
            focusedIndicatorColor = GoldAccent,
            unfocusedIndicatorColor = SurfaceBg
        ),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    )
}

@Composable
private fun TVSuggestions(suggestions: List<Pair<Int, String>>, query: String, onSelect: (Int, String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        suggestions.forEach { (index, title) ->
            var focused by remember { mutableStateOf(false) }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .background(if (focused) GoldAccent.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .then(
                        if (focused) Modifier.border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(annotated, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TVOtdRow(movies: List<OtdItem>, onSelect: (String) -> Unit) {
    Text("ON THIS DAY", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GoldAccent.copy(alpha = 0.5f), letterSpacing = 1.sp)
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(movies, key = { it.title }) { movie ->
            var focused by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .width(100.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .focusable(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (movie.posterPath != null) {
                    AsyncImage(
                        model = "${TMDB_IMG_BASE}w154${movie.posterPath}",
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp, 120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (focused) Modifier.border(2.dp, GoldAccent, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                    )
                }
                Text(movie.title, fontSize = 11.sp, color = TextPrimary, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun TVHistoryRow(history: List<String>, onSelect: (String) -> Unit) {
    Text("RECENT", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, letterSpacing = 1.sp)
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(history) { title ->
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .background(
                        if (focused) GoldAccent.copy(alpha = 0.2f) else GoldAccent.copy(alpha = 0.08f),
                        RoundedCornerShape(20.dp)
                    )
                    .then(
                        if (focused) Modifier.border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        else Modifier
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(title, fontSize = 14.sp, color = GoldAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TVSurpriseMe(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFF4444), Color(0xFFCC0000), Color(0xFF8C0000))
                    )
                )
                .then(
                    if (focused) Modifier.border(3.dp, Color.White, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("SURPRISE\nME", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
private fun TVRecommendations(state: UiState, viewModel: MainViewModel) {
    val uriHandler = LocalUriHandler.current

    Text("Because you liked", fontSize = 14.sp, color = TextSecondary, letterSpacing = 1.sp)
    Text(state.selectedMovie, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GoldAccent)
    Spacer(Modifier.height(16.dp))

    // Industry filters
    if (state.industries.size > 1) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                TVFilterChip(
                    "All",
                    state.selectedIndustries.size == state.industries.size,
                    Color(0xFF3FB96A)
                ) { viewModel.selectAllIndustries() }
            }
            items(state.industries) { name ->
                val color = when (state.industries.indexOf(name)) { 0 -> GoldAccent; 1 -> Color(0xFFB4B4BE); else -> Color(0xFFB07A50) }
                TVFilterChip(name, state.selectedIndustries.contains(name), color) { viewModel.toggleIndustry(name) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    // Poster row
    TVPosterRow(state.filteredRecommendations) { rec ->
        viewModel.drillInto(rec.title)
    }

    // Sub-levels
    state.subLevels.forEachIndexed { levelIdx, level ->
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LEVEL ${levelIdx + 2}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GoldAccent.copy(alpha = 0.6f), letterSpacing = 2.sp)
                Row {
                    Text("from ", fontSize = 12.sp, color = TextSecondary)
                    Text(level.fromTitle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GoldAccent)
                }
            }
            var exitFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .onFocusChanged { exitFocused = it.isFocused }
                    .focusable()
                    .background(
                        if (exitFocused) Color(0xFFE04040).copy(alpha = 0.3f) else Color(0xFFE04040).copy(alpha = 0.15f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("⬅ EXIT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE04040), letterSpacing = 2.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        TVPosterRow(level.filteredRecs) { rec -> viewModel.drillInto(rec.title) }
    }
}

@Composable
private fun TVFilterChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .background(
                if (selected || focused) color.copy(alpha = 0.15f) else Color.Transparent,
                RoundedCornerShape(20.dp)
            )
            .border(1.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .then(
                if (focused) Modifier.border(2.dp, color, RoundedCornerShape(20.dp))
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color.copy(alpha = if (selected) 1f else 0.3f))
    }
}

@Composable
private fun TVPosterRow(recommendations: List<Recommendation>, onDrillIn: (Recommendation) -> Unit) {
    val uriHandler = LocalUriHandler.current

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(recommendations, key = { it.title }) { rec ->
            var focused by remember { mutableStateOf(false) }
            val posterPath = rec.posterPath ?: PosterData.posters[rec.title]
            val scale = if (focused) 1.1f else 1f

            Column(
                modifier = Modifier
                    .width(200.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .focusable(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size((200 * scale).dp, (300 * scale).dp)
                        .then(
                            if (focused) Modifier.shadow(12.dp, RoundedCornerShape(12.dp), spotColor = GoldAccent)
                            else Modifier
                        )
                ) {
                    if (posterPath != null) {
                        AsyncImage(
                            model = "${TMDB_IMG_BASE}w342$posterPath",
                            contentDescription = rec.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (focused) Modifier.border(2.dp, GoldAccent, RoundedCornerShape(12.dp))
                                    else Modifier
                                )
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(
                                Brush.verticalGradient(listOf(SurfaceBg, CardBg))
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🎬", fontSize = 40.sp)
                                Text(rec.title, fontSize = 12.sp, color = TextPrimary.copy(alpha = 0.7f), textAlign = TextAlign.Center, maxLines = 3)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(rec.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, textAlign = TextAlign.Center, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (rec.year > 0) Text("${rec.year}", fontSize = 13.sp, color = TextSecondary)
                    val rc = ratingColor(rec.imdbScore)
                    Text(
                        "★ ${"%.1f".format(rec.imdbScore)}",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = rc,
                        modifier = Modifier.background(rc.copy(alpha = 0.2f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (focused) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (rec.trailerKey != null) {
                            Text("▶ Trailer", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCC0000))
                        }
                        Text("Similar →", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GoldAccent)
                    }
                }
            }
        }
    }
}
