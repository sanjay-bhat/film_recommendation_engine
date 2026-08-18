# Film Recommend — iOS

A standalone iOS app for movie recommendations, built with SwiftUI.

## Setup

1. Copy the dataset CSVs into the app bundle resources:
   ```bash
   cd ../dataset
   for z in *.zip; do unzip -o "$z"; done
   ```

2. Open Xcode and create a new iOS App project:
   - Product Name: `FilmRecommend`
   - Interface: SwiftUI
   - Language: Swift
   - Minimum Deployment: iOS 16.0

3. Replace the generated source files with the files from `FilmRecommend/`

4. Add `tmdb_5000_movies.csv` and `tmdb_5000_credits.csv` to the Xcode project (drag into navigator, check "Copy items if needed")

5. Build and run on a simulator or device

## Architecture

- **RecommendationEngine** — core algorithm: CSV loading, KNN search, Gaussian scoring, fuzzy dedup
- **MovieViewModel** — ObservableObject managing UI state, async loading via Swift concurrency
- **SearchView** — SwiftUI interface with synthwave theme (neon cyan/pink/purple on dark)

## Features

- Search across 4,803 movie titles with real-time suggestions
- Get 5 recommendations based on content similarity
- Sequel deduplication via Levenshtein distance
- Fully offline — dataset bundled in app resources
