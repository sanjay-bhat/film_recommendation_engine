<p align="center">
  <img src="assets/banner.svg" alt="Film Recommendation Engine — Cinematic Banner" width="100%">
</p>

# Film Recommendation Engine

[![CI](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/ci.yml/badge.svg)](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/ci.yml)
[![CodeQL](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/codeql.yml/badge.svg)](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/codeql.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![release](https://img.shields.io/badge/release-v0.9.0-orange.svg)](https://github.com/sanjay-bhat/film_recommendation_engine/releases)

A semantic recommendation engine spanning **6,707 movies & TV shows**. Give it a title you love, and it returns 7 you'll probably love too — powered by sentence-transformer embeddings (all-MiniLM-L6-v2) and FAISS nearest-neighbor search across TMDb's top-rated catalog, backed by Supabase Postgres.

## Preview

<p align="center">
  <a href="https://sanjay-bhat.github.io/film_recommendation_engine/">
    <img src="assets/demo_website.gif" alt="Website Demo" width="100%">
  </a>
</p>

<p align="center">
  <img src="assets/demo_android.gif" alt="Android App Demo" width="320">
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="assets/demo_ios.gif" alt="iOS App Demo" width="320">
</p>

<p align="center">
  <a href="android/">
    <img src="https://img.shields.io/badge/View_source-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android source">
  </a>
  &nbsp;
  <a href="ios/">
    <img src="https://img.shields.io/badge/View_source-iOS-007AFF?style=for-the-badge&logo=apple&logoColor=white" alt="iOS source">
  </a>
</p>

## Architecture

<p align="center">
  <img src="assets/architecture.svg" alt="System Architecture Diagram" width="100%">
</p>

## How It Works

Every title in the catalog is encoded into a 384-dimensional vector using **all-MiniLM-L6-v2**, a sentence-transformer model that captures semantic meaning from plot overviews, genres, cast, and keywords. These vectors are indexed with **FAISS** for sub-millisecond nearest-neighbor lookups.

| Step | What happens |
|:---|:---|
| **1. Embed** | Each title's metadata (overview, genres, cast, keywords) is encoded into a 384-dim vector |
| **2. Index** | FAISS builds an optimized search index over all 6,707 vectors |
| **3. Search** | Given a title, FAISS finds the 7 nearest neighbors by cosine similarity |
| **4. Serve** | Pre-computed recommendations are stored in Supabase Postgres and served via PostgREST |

The frontend is a single-page app on GitHub Pages with a **Cover Flow** carousel, **WebGL bokeh** particles, and full **PWA** offline support.

## Quick Start

### Try it live

👉 **[sanjay-bhat.github.io/film_recommendation_engine](https://sanjay-bhat.github.io/film_recommendation_engine/)**

### Run locally

```bash
git clone https://github.com/sanjay-bhat/film_recommendation_engine.git
cd film_recommendation_engine
python3 -m http.server 8765 -d docs
# Open http://localhost:8765
```

### Rebuild recommendations from scratch

```bash
pip install -r requirements.txt

# Fetch catalog from TMDb API
python scripts/fetch_tmdb_catalog.py --api-key YOUR_TMDB_KEY
python scripts/fetch_tv_shows.py --api-key YOUR_TMDB_KEY

# Generate embeddings + recommendations
python scripts/generate_recommendations.py

# Migrate to Supabase
python scripts/migrate_combined.py --url https://YOUR_PROJECT.supabase.co --key YOUR_SERVICE_ROLE_KEY
```

## Genre Distribution

The catalog spans 20+ genres across 4,800 movies and 1,907 TV shows. Drama and Comedy dominate, while TV-specific genres like Sci-Fi & Fantasy and Action & Adventure (pink bars) reflect TMDb's separate taxonomy for television.

<p align="center">
  <img src="assets/genre_distribution.svg" alt="Genre Distribution Bar Chart" width="100%">
</p>

## Dataset

**6,707 titles** sourced from [TMDb](https://www.themoviedb.org/) — the top-rated movies and TV shows by vote count. Each title carries its overview, genres, cast, keywords, poster, trailer, and release date. Recommendations are pre-computed offline using sentence-transformer embeddings and stored as **46,949 recommendation pairs** in Supabase Postgres.

## Implementations

| Platform | Language | Directory | Notes |
|:---|:---|:---|:---|
| Web | HTML/CSS/JS | `docs/` | GitHub Pages, WebGL bokeh particles, Cover Flow UI, PWA |
| Android | Kotlin | `android/` | Jetpack Compose, Material 3, fully offline |
| iOS | Swift | `ios/` | SwiftUI, async/await, fully offline |
| CLI | Python | `src/recommend.py` | Reference implementation |
| CLI | Rust | `src/recommend.rs` | Zero-dependency, fast CLI binary |
| CLI | C# | `src/Recommend.cs` | .NET 8, clean OOP structure |
| CLI | Go | `src/recommend.go` | Single-file, standard library only |

## License

MIT
