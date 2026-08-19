#!/usr/bin/env python3
"""
Film Recommendation Engine — Content-based movie recommender using the TMDb 5000 dataset.

Usage:
    python recommend.py --movie "The Dark Knight Rises"
    python recommend.py --movie "Pirates of the Caribbean: Dead Man's Chest" --no-dedup
    python recommend.py --id 12
"""

import argparse
import csv
import json
import math
import os
import sys
from collections import defaultdict

import nltk

try:
    from thefuzz import fuzz
except ImportError:
    from fuzzywuzzy import fuzz

nltk.download("wordnet", quiet=True)
PS = nltk.stem.PorterStemmer()

COLLAB_WEIGHT = 0.4


def load_movies(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            row["genres"] = json.loads(row["genres"])
            row["keywords"] = json.loads(row["keywords"])
            row["production_countries"] = json.loads(row["production_countries"])
            row["spoken_languages"] = json.loads(row["spoken_languages"])
            row["budget"] = int(row["budget"])
            row["revenue"] = int(row["revenue"])
            row["vote_count"] = int(row["vote_count"])
            row["vote_average"] = float(row["vote_average"])
            row["popularity"] = float(row["popularity"])
            row["runtime"] = float(row["runtime"]) if row["runtime"] else 0.0
            row["id"] = int(row["id"])
            rows.append(row)
    return rows


def load_credits(path):
    rows = []
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            row["cast"] = json.loads(row["cast"])
            row["crew"] = json.loads(row["crew"])
            rows.append(row)
    return rows


def safe_access(container, indices):
    result = container
    try:
        for i in indices:
            result = result[i]
        return result
    except (IndexError, KeyError, TypeError):
        return None


def pipe_names(items):
    return "|".join(x["name"] for x in items)


def get_director(crew):
    for member in crew:
        if member.get("job") == "Director":
            return member["name"]
    return None


def build_dataframe(movies, credits):
    """Merge movies and credits into a flat list of film records."""
    films = []
    for i, m in enumerate(movies):
        c = credits[i] if i < len(credits) else {"cast": [], "crew": []}
        year = None
        if m.get("release_date"):
            try:
                year = int(m["release_date"][:4])
            except (ValueError, IndexError):
                pass
        film = {
            "tmdb_id": m["id"],
            "movie_title": m["title"],
            "genres": pipe_names(m["genres"]),
            "plot_keywords": pipe_names(m["keywords"]),
            "director_name": get_director(c["crew"]),
            "actor_1_name": safe_access(c["cast"], [0, "name"]),
            "actor_2_name": safe_access(c["cast"], [1, "name"]),
            "actor_3_name": safe_access(c["cast"], [2, "name"]),
            "title_year": year,
            "vote_average": m["vote_average"],
            "vote_count": m["vote_count"],
            "popularity": m["popularity"],
        }
        films.append(film)
    return films


def clean_keywords(films):
    """Stem, deduplicate, and filter low-frequency keywords."""
    roots = defaultdict(set)
    for film in films:
        kw = film.get("plot_keywords", "")
        if not kw:
            continue
        for token in kw.split("|"):
            token = token.lower().strip()
            if token:
                roots[PS.stem(token)].add(token)

    select = {}
    for root, variants in roots.items():
        select[root] = min(variants, key=len)

    for film in films:
        kw = film.get("plot_keywords", "")
        if not kw:
            continue
        new_tokens = []
        for token in kw.split("|"):
            token = token.lower().strip()
            if not token:
                continue
            stem = PS.stem(token)
            new_tokens.append(select.get(stem, token))
        film["plot_keywords"] = "|".join(new_tokens)

    freq = defaultdict(int)
    for film in films:
        kw = film.get("plot_keywords", "")
        if not kw:
            continue
        for token in set(kw.split("|")):
            if token:
                freq[token] += 1

    for film in films:
        kw = film.get("plot_keywords", "")
        if not kw:
            continue
        film["plot_keywords"] = "|".join(
            t for t in kw.split("|") if t and freq.get(t, 0) >= 4
        )

    return films


def get_features(film):
    """Extract all feature strings from a film record."""
    features = []
    if film.get("director_name"):
        features.append(film["director_name"])
    for key in ["actor_1_name", "actor_2_name", "actor_3_name"]:
        if film.get(key):
            features.append(film[key])
    if film.get("plot_keywords"):
        features.extend(film["plot_keywords"].split("|"))
    if film.get("genres"):
        features.extend(film["genres"].split("|"))
    return [f for f in features if f]


def euclidean_distance(vec_a, vec_b):
    return math.sqrt(sum((a - b) ** 2 for a, b in zip(vec_a, vec_b)))


def find_neighbors(films, target_idx, n=31):
    """Find the n nearest films by binary feature vectors."""
    target_features = get_features(films[target_idx])
    all_genres = set()
    for film in films:
        if film.get("genres"):
            all_genres.update(film["genres"].split("|"))
    feature_set = list(set(target_features) | all_genres)

    vectors = []
    for film in films:
        film_features = set(get_features(film))
        vec = [1 if f in film_features else 0 for f in feature_set]
        vectors.append(vec)

    target_vec = vectors[target_idx]
    distances = []
    for i, vec in enumerate(vectors):
        dist = euclidean_distance(target_vec, vec)
        distances.append((i, dist))

    distances.sort(key=lambda x: x[1])
    return [idx for idx, _ in distances[:n]]


def load_item_factors(path):
    """Load pre-computed SVD item factors → {tmdb_id: [f0..f49]}."""
    factors = {}
    if not os.path.exists(path):
        return factors
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            tmdb_id = int(row["tmdb_id"])
            vec = [float(row[k]) for k in row if k.startswith("f")]
            factors[tmdb_id] = vec
    return factors


def collab_similarity(factors, tmdb_id_a, tmdb_id_b):
    """Cosine similarity between two movies' latent factor vectors."""
    va = factors.get(tmdb_id_a)
    vb = factors.get(tmdb_id_b)
    if va is None or vb is None:
        return 0.0
    dot = sum(a * b for a, b in zip(va, vb))
    return max(0.0, dot)


def gaussian(x, y, sigma):
    if sigma == 0:
        return 0.0
    return math.exp(-((x - y) ** 2) / (2 * sigma ** 2))


def is_sequel(title1, title2):
    return fuzz.ratio(title1, title2) > 50 or fuzz.token_set_ratio(title1, title2) > 50


def score_candidate(main_title, max_votes, year_ref, title, year, imdb_score,
                    votes, collab_sim=0.0):
    if is_sequel(main_title, title):
        return 0.0
    fact1 = gaussian(year_ref, year, 20) if year_ref and year else 1.0
    fact2 = gaussian(votes, max_votes, max_votes) if votes and max_votes > 0 else 0.0
    content_score = imdb_score ** 2 * fact1 * fact2
    if collab_sim > 0:
        return (1 - COLLAB_WEIGHT) * content_score + COLLAB_WEIGHT * collab_sim * imdb_score ** 2
    return content_score


def recommend(films, target_idx, dedup_sequels=True, item_factors=None):
    """Return up to 5 recommended films."""
    neighbor_indices = find_neighbors(films, target_idx)
    target_tmdb = films[target_idx].get("tmdb_id", 0)

    candidates = []
    max_votes = 0
    for idx in neighbor_indices:
        film = films[idx]
        votes = film.get("vote_count", 0) or 0
        max_votes = max(max_votes, votes)
        candidates.append({
            "title": film["movie_title"],
            "year": film.get("title_year"),
            "score": film.get("vote_average", 0),
            "votes": votes,
            "index": idx,
            "tmdb_id": film.get("tmdb_id", 0),
        })

    main = candidates[0]
    factors = item_factors or {}
    for c in candidates:
        csim = collab_similarity(factors, target_tmdb, c["tmdb_id"])
        c["rank_score"] = score_candidate(
            main["title"], max_votes, main["year"],
            c["title"], c["year"], c["score"], c["votes"],
            collab_sim=csim,
        )
    candidates.sort(key=lambda x: x["rank_score"], reverse=True)

    selected = []
    for c in candidates:
        if len(selected) >= 5:
            break
        dominated = any(
            s["title"] == c["title"] or is_sequel(c["title"], s["title"])
            for s in selected
        )
        if dominated:
            continue
        selected.append(c)

    if dedup_sequels:
        remove_titles = set()
        for i, f1 in enumerate(selected):
            for j, f2 in enumerate(selected):
                if j <= i:
                    continue
                if is_sequel(f1["title"], f2["title"]):
                    drop = f2["title"] if (f1["year"] or 0) < (f2["year"] or 0) else f1["title"]
                    remove_titles.add(drop)
        selected = [s for s in selected if s["title"] not in remove_titles]

        for c in candidates:
            if len(selected) >= 5:
                break
            dominated = any(
                s["title"] == c["title"] or is_sequel(c["title"], s["title"])
                for s in selected
            )
            if not dominated:
                selected.append(c)

    return selected[:5]


def find_film_by_title(films, title):
    title_lower = title.lower().strip()
    for i, film in enumerate(films):
        if film["movie_title"].lower().strip() == title_lower:
            return i
    best_idx, best_score = -1, 0
    for i, film in enumerate(films):
        score = fuzz.token_set_ratio(title_lower, film["movie_title"].lower())
        if score > best_score:
            best_score = score
            best_idx = i
    if best_score >= 60:
        return best_idx
    return -1


def main():
    parser = argparse.ArgumentParser(description="Film Recommendation Engine")
    parser.add_argument("--movie", type=str, help="Movie title to get recommendations for")
    parser.add_argument("--id", type=int, help="Movie index in the dataset")
    parser.add_argument("--no-dedup", action="store_true", help="Disable sequel deduplication")
    parser.add_argument("--data-dir", type=str, default="dataset", help="Path to CSV data directory")
    args = parser.parse_args()

    if not args.movie and args.id is None:
        parser.error("Provide either --movie or --id")

    movies_path = os.path.join(args.data_dir, "tmdb_5000_movies.csv")
    credits_path = os.path.join(args.data_dir, "tmdb_5000_credits.csv")

    if not os.path.exists(movies_path) or not os.path.exists(credits_path):
        print(f"Error: Dataset not found in '{args.data_dir}/'")
        print("Download from: https://www.kaggle.com/datasets/tmdb/tmdb-movie-metadata")
        sys.exit(1)

    print("Loading dataset...")
    movies = load_movies(movies_path)
    credits = load_credits(credits_path)

    print("Building film records...")
    films = build_dataframe(movies, credits)

    print("Cleaning keywords...")
    films = clean_keywords(films)

    factors_path = os.path.join(args.data_dir, "item_factors.csv")
    item_factors = load_item_factors(factors_path)
    if item_factors:
        print(f"Loaded collaborative factors for {len(item_factors)} movies")
    else:
        print("No collaborative factors found — using content-based only")

    if args.id is not None:
        target_idx = args.id
    else:
        target_idx = find_film_by_title(films, args.movie)
        if target_idx < 0:
            print(f"Error: Could not find movie matching '{args.movie}'")
            sys.exit(1)

    target = films[target_idx]
    print(f"\nRecommendations for: {target['movie_title']} ({target.get('title_year', '?')})")
    print("=" * 60)

    results = recommend(films, target_idx, dedup_sequels=not args.no_dedup,
                        item_factors=item_factors)
    for i, r in enumerate(results, 1):
        print(f"  {i}. {r['title']} ({r['year'] or '?'}) — IMDB: {r['score']}")


if __name__ == "__main__":
    main()
