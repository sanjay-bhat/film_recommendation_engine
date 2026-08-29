<p align="center">
  <img src="assets/banner.svg" alt="Film Recommendation Engine — Cinematic Banner" width="100%">
</p>

# Film Recommendation Engine

[![CI](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/ci.yml/badge.svg)](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/ci.yml)
[![CodeQL](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/codeql.yml/badge.svg)](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/codeql.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![release](https://img.shields.io/badge/release-v0.10.0-orange.svg)](https://github.com/sanjay-bhat/film_recommendation_engine/releases)

A semantic recommendation engine spanning **6,707 movies & TV shows**. Give it a title you love, and it returns 20 you'll probably love too — powered by sentence-transformer embeddings (all-MiniLM-L6-v2) and pgvector HNSW search across TMDb's top-rated catalog, backed by Supabase Postgres. Double-click any recommendation to drill into a recursive sub-tree of similar titles, each level rendered in the same 3D Cover Flow carousel.

## Preview

<p align="center">
  <a href="https://sanjay-bhat.github.io/film_recommendation_engine/">
    <img src="assets/preview_website.gif" alt="Website — Laptop" width="100%">
  </a>
</p>

<p align="center">
  <img src="assets/preview_iphone.gif" alt="iPhone" width="220">
  &nbsp;&nbsp;
  <img src="assets/preview_ipad.gif" alt="iPad" width="480">
</p>

<p align="center">
  <img src="assets/preview_appletv.gif" alt="Apple TV" width="100%">
</p>

<p align="center">
  <a href="android/">
    <img src="https://img.shields.io/badge/View_source-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android source">
  </a>
  &nbsp;
  <a href="ios/">
    <img src="https://img.shields.io/badge/View_source-iOS-007AFF?style=for-the-badge&logo=apple&logoColor=white" alt="iOS source">
  </a>
  &nbsp;
  <a href="ipad/">
    <img src="https://img.shields.io/badge/View_source-iPad-007AFF?style=for-the-badge&logo=apple&logoColor=white" alt="iPad source">
  </a>
  &nbsp;
  <a href="tablet/">
    <img src="https://img.shields.io/badge/View_source-Tablet-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Tablet source">
  </a>
  &nbsp;
  <a href="googletv/">
    <img src="https://img.shields.io/badge/View_source-Google_TV-4285F4?style=for-the-badge&logo=googletv&logoColor=white" alt="Google TV source">
  </a>
  &nbsp;
  <a href="appletv/">
    <img src="https://img.shields.io/badge/View_source-Apple_TV-000000?style=for-the-badge&logo=appletv&logoColor=white" alt="Apple TV source">
  </a>
  &nbsp;
  <a href="watchos/">
    <img src="https://img.shields.io/badge/View_source-watchOS-000000?style=for-the-badge&logo=apple&logoColor=white" alt="watchOS source">
  </a>
  &nbsp;
  <a href="wearos/">
    <img src="https://img.shields.io/badge/View_source-Wear_OS-4285F4?style=for-the-badge&logo=wearos&logoColor=white" alt="Wear OS source">
  </a>
</p>

## Architecture

<p align="center">
  <img src="assets/architecture.svg" alt="System Architecture Diagram" width="100%">
</p>

## Platform Architectures

<details>
<summary><strong>Website</strong> — HTML / CSS / JavaScript</summary>
<p align="center">
  <img src="assets/architecture_web.svg" alt="Website Architecture" width="100%">
</p>
</details>

<details>
<summary><strong>iOS</strong> — Swift + SwiftUI</summary>
<p align="center">
  <img src="assets/architecture_ios.svg" alt="iOS Architecture" width="100%">
</p>
</details>

<details>
<summary><strong>iPad</strong> — Swift + SwiftUI</summary>
<p align="center">
  <img src="assets/architecture_ipad.svg" alt="iPad Architecture" width="100%">
</p>
</details>

<details>
<summary><strong>Android</strong> — Kotlin + Jetpack Compose</summary>
<p align="center">
  <img src="assets/architecture_android.svg" alt="Android Architecture" width="100%">
</p>
</details>

<details>
<summary><strong>Android Tablet</strong> — Kotlin + Jetpack Compose</summary>
<p align="center">
  <img src="assets/architecture_tablet.svg" alt="Android Tablet Architecture" width="100%">
</p>
</details>

<details>
<summary><strong>Apple TV</strong> — Swift + tvOS SwiftUI</summary>
<p align="center">
  <img src="assets/architecture_appletv.svg" alt="Apple TV Architecture" width="100%">
</p>
</details>

<details>
<summary><strong>Google TV</strong> — Kotlin + Compose for TV</summary>
<p align="center">
  <img src="assets/architecture_googletv.svg" alt="Google TV Architecture" width="100%">
</p>
</details>

<details>
<summary><strong>watchOS</strong> — Swift + SwiftUI</summary>
<p align="center">
  <img src="assets/architecture_watchos.svg" alt="watchOS Architecture" width="100%">
</p>
</details>

<details>
<summary><strong>Wear OS</strong> — Kotlin + Compose for Wear</summary>
<p align="center">
  <img src="assets/architecture_wearos.svg" alt="Wear OS Architecture" width="100%">
</p>
</details>

## How It Works

Every title in the catalog is encoded into a 384-dimensional vector using **all-MiniLM-L6-v2**, a sentence-transformer model that captures semantic meaning from plot overviews, genres, cast, and keywords. These vectors are indexed with **pgvector HNSW** for sub-10ms approximate nearest-neighbor lookups.

| Step | What happens |
|:---|:---|
| **1. Embed** | Each title's metadata (overview, genres, cast, keywords) is encoded into a 384-dim vector |
| **2. Index** | pgvector builds an HNSW index over all 6,707 vectors in Supabase Postgres |
| **3. Search** | Given a title, pgvector finds the 20 nearest neighbors by cosine similarity |
| **4. Serve** | Recommendations are computed at query time via PostgREST RPC — no pre-computed pairs |
| **5. Cache** | In-memory memoization Map + speculative pre-fetch for instant sub-level loading |

The frontend is a single-page app on GitHub Pages with a **3D Cover Flow** carousel, **recursive sub-tree recommendations** (double-click to drill deeper), **industry filter bubbles**, **WebGL bokeh** particles, and full **PWA** offline support.

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

**6,707 titles** sourced from [TMDb](https://www.themoviedb.org/) — the top-rated movies and TV shows by vote count. Each title carries its overview, genres, cast, keywords, poster, trailer, release date, and original language. Recommendations are computed at query time using pgvector HNSW nearest-neighbor search over 384-dimensional sentence-transformer embeddings stored in Supabase Postgres.

## Implementations

| Platform | Language | Directory | Notes |
|:---|:---|:---|:---|
| Web | HTML/CSS/JS | `docs/` | GitHub Pages, WebGL bokeh, 3D Cover Flow, recursive sub-tree, PWA |
| Android | Kotlin | `android/` | Jetpack Compose, Material 3, fully offline |
| iOS | Swift | `ios/` | SwiftUI, async/await, fully offline |
| iPad | Swift | `ipad/` | SwiftUI, NavigationSplitView two-pane layout |
| Android Tablet | Kotlin | `tablet/` | Jetpack Compose, two-pane master-detail layout |
| Google TV | Kotlin | `googletv/` | Compose for TV, D-pad/Siri Remote focus navigation |
| Apple TV | Swift | `appletv/` | tvOS SwiftUI, focus-based Siri Remote navigation |
| watchOS | Swift | `watchos/` | SwiftUI, Digital Crown browsing, Surprise Me haptics |
| Wear OS | Kotlin | `wearos/` | Jetpack Compose, swipe navigation, Surprise Me haptics |
| CLI | Python | `src/recommend.py` | Reference implementation |
| CLI | Rust | `src/recommend.rs` | Zero-dependency, fast CLI binary |
| CLI | C# | `src/Recommend.cs` | .NET 8, clean OOP structure |
| CLI | Go | `src/recommend.go` | Single-file, standard library only |

## License

MIT
