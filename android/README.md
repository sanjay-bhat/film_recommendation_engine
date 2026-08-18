# Film Recommend — Android

A standalone Android app for movie recommendations, built with Jetpack Compose and Material 3.

## Setup

1. Copy the dataset CSVs into the assets directory:
   ```bash
   mkdir -p app/src/main/assets
   cd ../dataset
   for z in *.zip; do unzip -o "$z"; done
   cp *.csv ../android/app/src/main/assets/
   ```

2. Open the `android/` directory in Android Studio

3. Sync Gradle and run on an emulator or device (minSdk 26 / Android 8.0+)

## Architecture

- **RecommendationEngine** — core algorithm: CSV loading, KNN search, Gaussian scoring, fuzzy dedup
- **MainViewModel** — manages UI state, runs engine on background thread via coroutines
- **MovieSearchScreen** — Jetpack Compose UI with synthwave theme (neon cyan/pink/purple on dark)

## Features

- Search across 4,803 movie titles with real-time suggestions
- Get 5 recommendations based on content similarity
- Sequel deduplication via Levenshtein distance
- Fully offline — dataset bundled in app assets
