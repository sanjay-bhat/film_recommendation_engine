# Film Recommendation Engine

## Architecture

Four-way hybrid recommender: Content (TF-IDF), Collaborative (SVD), Genome tags, Plot embeddings. Each retrieves 31 candidates independently, merged into ~120+ pool, then ranked with hybrid scoring: `(1-α) × content + α × collab` where α=0.4.

Identical algorithm implemented in four languages: Python (`src/recommend.py`), Go (`src/recommend.go`), Rust (`src/recommend.rs`), C# (`src/Recommend.cs`). All must produce the same recommendations.

## Pre-computed Offline Model

All factor CSVs ship in the repo so Go/Rust/C# can load them without Python/ML dependencies:
- `dataset/item_factors.csv` — collaborative SVD factors (k=50)
- `dataset/genome_factors.csv` — genome tag SVD factors (k=50)
- `dataset/plot_factors.csv` — plot sentence embedding PCA factors (d=50)

Factor files are rebuilt by scripts in `scripts/` and committed directly.

## Datasets

- **TMDb 5000** (`dataset/tmdb_5000_movies.csv`, `tmdb_5000_credits.csv`) — content-feature backbone (cast, crew, keywords, genres). Shipped as zipped CSVs, extracted by `make setup`.
- **MovieLens 25M** (`dataset/ml-25m/`) — ratings, genome tags, movie links. Downloaded by `make setup-collab`. Gitignored.
- **Expanded set** (`dataset/movies_expanded.csv`, `credits_expanded.csv`) — merged TMDb 5000 + MovieLens metadata (~62K movies). Gitignored, rebuilt by `make setup-expanded`.

## Build & Run

```bash
make setup            # extract CSVs, install Python deps
make setup-collab     # download MovieLens 25M, build factor CSVs
make setup-expanded   # build expanded 62K dataset + rebuild factors
make run MOVIE="Inception"
make run-all MOVIE="Inception"   # Python + Go + Rust side by side
make lint             # flake8 on recommend.py
```

## CI

GitHub Actions runs on push/PR to main: Python lint + syntax, Go build, Rust build, C# build. All four must pass.

## Conventions

- When changing the recommendation algorithm, update all four language implementations to match.
- Factor CSVs are committed to the repo (not gitignored) — they are the pre-computed model.
- Large raw datasets (MovieLens, expanded CSVs) are gitignored — only factor files are committed.
- TMDb API key is passed via CLI args, never hardcoded or committed.
- Git user email: `12857923+sanjay-bhat@users.noreply.github.com`
- Branch naming: `feature/description_HHMM` with current time.

## Scripts

| Script | Purpose |
|:---|:---|
| `scripts/build_collab_model.py` | SVD on MovieLens ratings → `item_factors.csv` |
| `scripts/build_genome_factors.py` | SVD on genome scores → `genome_factors.csv` |
| `scripts/build_plot_embeddings.py` | Sentence embeddings + PCA → `plot_factors.csv` |
| `scripts/build_expanded_dataset.py` | Merge TMDb 5000 + MovieLens → expanded CSVs |
| `scripts/fetch_overviews.py` | Async TMDb API fetch for plot overviews |
