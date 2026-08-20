#!/usr/bin/env python3
"""
Build an expanded movie dataset by merging TMDb 5000 with MovieLens 25M metadata.

TMDb 5000 movies keep their full metadata (cast, crew, keywords, overview).
MovieLens-only movies get title, year, genres from movies.csv, and rating
stats computed from ratings.csv. No API calls needed.

Usage:
    python scripts/build_expanded_dataset.py
"""

import argparse
import csv
import json
import os
import re
import sys
import time


def load_links(ml_dir):
    """MovieLens movieId → tmdbId mapping."""
    path = os.path.join(ml_dir, "links.csv")
    ml_to_tmdb = {}
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            tmdb_str = row.get("tmdbId", "").strip()
            if tmdb_str:
                ml_to_tmdb[int(row["movieId"])] = int(tmdb_str)
    return ml_to_tmdb


def load_tmdb5000_ids(movies_path):
    """Set of TMDb IDs in the TMDb 5000 dataset."""
    ids = set()
    with open(movies_path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            ids.add(int(row["id"]))
    return ids


def parse_ml_title(raw):
    """Parse 'Movie Title (1995)' → (title, year)."""
    m = re.match(r"^(.+?)\s*\((\d{4})\)\s*$", raw)
    if m:
        return m.group(1).strip(), int(m.group(2))
    return raw.strip(), 0


def ml_genres_to_json(genres_str):
    """Convert 'Adventure|Animation|Comedy' → JSON array like TMDb format."""
    if not genres_str or genres_str == "(no genres listed)":
        return "[]"
    genres = [{"id": 0, "name": g.strip()} for g in genres_str.split("|") if g.strip()]
    return json.dumps(genres, ensure_ascii=False)


def compute_rating_stats(ml_dir, ml_to_tmdb):
    """Compute average rating and count per TMDb ID from MovieLens ratings."""
    path = os.path.join(ml_dir, "ratings.csv")
    sums = {}
    counts = {}

    t0 = time.time()
    n = 0
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            ml_id = int(row["movieId"])
            tmdb_id = ml_to_tmdb.get(ml_id)
            if tmdb_id is None:
                continue
            rating = float(row["rating"])
            sums[tmdb_id] = sums.get(tmdb_id, 0.0) + rating
            counts[tmdb_id] = counts.get(tmdb_id, 0) + 1
            n += 1
            if n % 5_000_000 == 0:
                print(f"  ...processed {n:,} ratings in {time.time()-t0:.0f}s")

    print(f"  Processed {n:,} ratings for {len(counts):,} movies in {time.time()-t0:.0f}s")

    stats = {}
    for tmdb_id in counts:
        avg = sums[tmdb_id] / counts[tmdb_id]
        stats[tmdb_id] = (round(avg * 2, 1), counts[tmdb_id])
    return stats


def load_ml_movies(ml_dir, ml_to_tmdb, existing_ids):
    """Load MovieLens movies NOT in existing_ids."""
    path = os.path.join(ml_dir, "movies.csv")
    movies = {}
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            ml_id = int(row["movieId"])
            tmdb_id = ml_to_tmdb.get(ml_id)
            if tmdb_id is None or tmdb_id in existing_ids:
                continue
            title, year = parse_ml_title(row["title"])
            movies[tmdb_id] = {
                "title": title,
                "year": year,
                "genres_json": ml_genres_to_json(row.get("genres", "")),
            }
    return movies


def main():
    parser = argparse.ArgumentParser(description="Build expanded movie dataset")
    parser.add_argument("--ml-dir", default="dataset/ml-25m")
    parser.add_argument("--tmdb-movies", default="dataset/tmdb_5000_movies.csv")
    parser.add_argument("--tmdb-credits", default="dataset/tmdb_5000_credits.csv")
    parser.add_argument("--out-dir", default="dataset")
    args = parser.parse_args()

    if not os.path.exists(args.ml_dir):
        print(f"Error: {args.ml_dir} not found")
        sys.exit(1)

    movies_out = os.path.join(args.out_dir, "movies_expanded.csv")
    credits_out = os.path.join(args.out_dir, "credits_expanded.csv")

    print("=== Building Expanded Dataset ===\n")

    print("Step 1: Loading MovieLens links...")
    ml_to_tmdb = load_links(args.ml_dir)
    print(f"  {len(ml_to_tmdb)} movies with TMDb IDs\n")

    print("Step 2: Loading TMDb 5000 IDs...")
    tmdb5000_ids = load_tmdb5000_ids(args.tmdb_movies)
    print(f"  {len(tmdb5000_ids)} movies in TMDb 5000\n")

    print("Step 3: Loading MovieLens-only movies...")
    ml_movies = load_ml_movies(args.ml_dir, ml_to_tmdb, tmdb5000_ids)
    print(f"  {len(ml_movies)} additional movies from MovieLens\n")

    print("Step 4: Computing rating stats from 25M ratings...")
    rating_stats = compute_rating_stats(args.ml_dir, ml_to_tmdb)
    print()

    # Copy TMDb 5000 movies + append MovieLens-only movies
    print("Step 5: Writing expanded movies CSV...")
    movie_headers = [
        "budget", "genres", "homepage", "id", "keywords", "original_language",
        "original_title", "overview", "popularity", "production_companies",
        "production_countries", "release_date", "revenue", "runtime",
        "spoken_languages", "status", "tagline", "title", "vote_average", "vote_count",
    ]

    count_tmdb = 0
    count_ml = 0
    with open(movies_out, "w", newline="", encoding="utf-8") as out:
        writer = csv.DictWriter(out, fieldnames=movie_headers)
        writer.writeheader()

        with open(args.tmdb_movies, encoding="utf-8") as f:
            for row in csv.DictReader(f):
                tmdb_id = int(row["id"])
                if tmdb_id in rating_stats:
                    avg, cnt = rating_stats[tmdb_id]
                    row["vote_average"] = avg
                    row["vote_count"] = cnt
                writer.writerow({h: row.get(h, "") for h in movie_headers})
                count_tmdb += 1

        for tmdb_id, info in ml_movies.items():
            avg, cnt = rating_stats.get(tmdb_id, (0.0, 0))
            release_date = f"{info['year']}-01-01" if info["year"] > 0 else ""
            row = {
                "budget": 0,
                "genres": info["genres_json"],
                "homepage": "",
                "id": tmdb_id,
                "keywords": "[]",
                "original_language": "en",
                "original_title": info["title"],
                "overview": "",
                "popularity": 0,
                "production_companies": "[]",
                "production_countries": "[]",
                "release_date": release_date,
                "revenue": 0,
                "runtime": 0,
                "spoken_languages": "[]",
                "status": "Released",
                "tagline": "",
                "title": info["title"],
                "vote_average": avg,
                "vote_count": cnt,
            }
            writer.writerow(row)
            count_ml += 1

    print(f"  Wrote {count_tmdb + count_ml} movies ({count_tmdb} TMDb 5000 + {count_ml} MovieLens)")
    size_mb = os.path.getsize(movies_out) / 1024 / 1024
    print(f"  File size: {size_mb:.1f} MB\n")

    # Copy TMDb 5000 credits + append empty credits for MovieLens-only
    print("Step 6: Writing expanded credits CSV...")
    credit_headers = ["movie_id", "title", "cast", "crew"]

    count_credits = 0
    with open(credits_out, "w", newline="", encoding="utf-8") as out:
        writer = csv.DictWriter(out, fieldnames=credit_headers)
        writer.writeheader()

        with open(args.tmdb_credits, encoding="utf-8") as f:
            for row in csv.DictReader(f):
                writer.writerow({h: row.get(h, "") for h in credit_headers})
                count_credits += 1

        for tmdb_id, info in ml_movies.items():
            writer.writerow({
                "movie_id": tmdb_id,
                "title": info["title"],
                "cast": "[]",
                "crew": "[]",
            })
            count_credits += 1

    print(f"  Wrote {count_credits} credit entries")
    size_mb = os.path.getsize(credits_out) / 1024 / 1024
    print(f"  File size: {size_mb:.1f} MB\n")

    print("Done!")


if __name__ == "__main__":
    main()
