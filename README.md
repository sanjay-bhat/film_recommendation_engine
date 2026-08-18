<p align="center">
  <img src="assets/banner.svg" alt="Film Recommendation Engine — Synthwave Banner" width="100%">
</p>

# Film Recommendation Engine

[![CI](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/ci.yml/badge.svg)](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/ci.yml)
[![CodeQL](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/codeql.yml/badge.svg)](https://github.com/sanjay-bhat/film_recommendation_engine/actions/workflows/codeql.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![release](https://img.shields.io/badge/release-v0.4.8-orange.svg)](https://github.com/sanjay-bhat/film_recommendation_engine/releases)
[![go report](https://img.shields.io/badge/go%20report-retired-lightgrey.svg)](https://goreportcard.com/report/github.com/sanjay-bhat/film_recommendation_engine)

A content-based movie recommendation system built on the **TMDb 5000 dataset**. Give it a movie you love, and it returns 5 films you'll probably love too — by matching directors, actors, plot keywords, and genres through nearest-neighbor search, then ranking candidates by popularity and release proximity.

## How It Works

The engine combines three recommendation strategies:

| Strategy | What It Does |
|:---|:---|
| **Content-Based** | Builds a binary feature matrix from director, cast, keywords, and genres. Finds the 31 nearest neighbors using Euclidean distance. |
| **Popularity-Weighted** | Scores neighbors using `IMDB² × φ(votes) × φ(year)` — a Gaussian weighting that favors well-rated, well-known films from the same era. |
| **Sequel Detection** | Uses fuzzy string matching to deduplicate franchise entries, so you don't get three Pirates of the Caribbean films back. |

## Quick Start

```bash
# Clone and set up
git clone https://github.com/sanjay-bhat/film_recommendation_engine.git
cd film_recommendation_engine
make setup          # installs Python deps + NLTK data, extracts CSVs

# Run
make run MOVIE="The Dark Knight Rises"

# Or run all language implementations side by side
make run-all MOVIE="Inception"
```

### Docker

```bash
make docker-build
make docker-run MOVIE="Inception"
```

### Terraform (local Docker)

```bash
cd terraform
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform apply
```

## Genre Distribution

The dataset spans 20 genres across 4,803 films. Drama and Comedy dominate, while Western and TV Movie sit at the long tail.

<p align="center">
  <img src="assets/genre_distribution.svg" alt="Genre Distribution Bar Chart" width="100%">
</p>

## Project Structure

```
film_recommendation_engine/
├── .github/
│   ├── ISSUE_TEMPLATE/      # Bug report & feature request forms
│   ├── workflows/           # CI + CodeQL pipelines
│   └── dependabot.yml       # Automated dependency updates
├── assets/                  # Banner, charts, visual assets
├── dataset/                 # TMDb 5000 CSVs (zipped)
├── docs/                    # GitHub Pages site
├── src/
│   ├── recommend.py         # Python implementation
│   ├── recommend.rs         # Rust implementation
│   ├── Recommend.cs         # C# implementation
│   └── recommend.go         # Go implementation
├── terraform/               # Local Docker deployment via Terraform
├── Dockerfile               # Containerized Python engine
├── Makefile                 # Build, run, lint, clean
├── FinalFilmRecommendationEngineCode-BigData.ipynb
└── README.md
```

## Dataset

Uses the [TMDb 5000 Movie Dataset](https://www.kaggle.com/datasets/tmdb/tmdb-movie-metadata) containing:
- **4,803 movies** with budget, revenue, genres, keywords, and ratings
- **4,803 credit entries** with full cast and crew data
- Keywords cleaned via NLTK stemming and WordNet synonym merging (9,474 → 2,121 unique keywords)

## Implementations

The core recommendation algorithm is available in four languages:

| Language | File | Notes |
|:---|:---|:---|
| Python | `src/recommend.py` | Reference implementation, uses scikit-learn |
| Rust | `src/recommend.rs` | Zero-dependency, compiles to a fast CLI binary |
| C# | `src/Recommend.cs` | .NET 8, clean OOP structure |
| Go | `src/recommend.go` | Single-file, standard library only |

## License

MIT
