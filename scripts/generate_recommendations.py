#!/usr/bin/env python3
"""
Generate recommendations using sentence-transformers + FAISS.

Combines the existing movie dataset with TV shows, computes semantic
embeddings from overview + genres + cast + keywords, and finds nearest
neighbors for each title.

Outputs:
  - dataset/combined_recommendations.json  (title -> [{title, score}])
  - dataset/combined_catalog.json          (title -> metadata)

Usage:
    python scripts/generate_recommendations.py
    python scripts/generate_recommendations.py --top-k 7 --batch-size 128
"""

import argparse
import csv
import json
import os
import sys
import time

import faiss
import numpy as np


def load_credits(credits_path):
    """Load cast names keyed by movie_id from a credits CSV."""
    credits = {}
    if not credits_path or not os.path.exists(credits_path):
        return credits
    with open(credits_path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            movie_id = row.get("movie_id", "")
            try:
                raw = json.loads(row.get("cast", "[]"))
                names = []
                for c in raw:
                    if isinstance(c, dict):
                        names.append(c.get("name", ""))
                    elif isinstance(c, str):
                        names.append(c)
                if names:
                    credits[movie_id] = names[:10]
            except (json.JSONDecodeError, TypeError):
                pass
    return credits


def has_enough_signal(overview, keywords):
    """A movie needs overview (>10 chars) or 3+ keywords for a useful embedding."""
    return (overview and len(overview.strip()) > 10) or len(keywords) >= 3


def load_movies(csv_path, credits_map=None):
    items = []
    skipped = 0
    with open(csv_path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            genres = []
            try:
                raw = json.loads(row.get("genres", "[]"))
                if isinstance(raw, list):
                    for g in raw:
                        if isinstance(g, dict):
                            genres.append(g["name"])
                        elif isinstance(g, str):
                            genres.append(g)
            except (json.JSONDecodeError, TypeError):
                pass

            keywords = []
            try:
                raw = json.loads(row.get("keywords", "[]"))
                if isinstance(raw, list):
                    for k in raw:
                        if isinstance(k, dict):
                            keywords.append(k["name"])
                        elif isinstance(k, str):
                            keywords.append(k)
            except (json.JSONDecodeError, TypeError):
                pass

            title = row.get("title") or row.get("name", "")
            if not title:
                continue

            overview = row.get("overview", "")

            if not has_enough_signal(overview, keywords):
                skipped += 1
                continue

            cast = []
            movie_id = str(row.get("id", ""))
            if credits_map and movie_id in credits_map:
                cast = credits_map[movie_id]
            else:
                try:
                    raw = json.loads(row.get("cast", "[]"))
                    if isinstance(raw, list):
                        for c in raw:
                            if isinstance(c, dict):
                                cast.append(c.get("name") or c.get("character", ""))
                            elif isinstance(c, str):
                                cast.append(c)
                except (json.JSONDecodeError, TypeError):
                    pass

            release_date = row.get("release_date") or row.get("first_air_date", "")
            year = release_date[:4] if release_date else ""

            items.append({
                "title": title,
                "overview": overview,
                "genres": genres,
                "keywords": keywords[:15],
                "cast": cast[:10],
                "year": year,
                "vote_average": float(row.get("vote_average", 0) or 0),
                "poster_path": row.get("poster_path", ""),
                "release_date": release_date,
                "type": "movie",
            })
    if skipped:
        print(f"  Skipped {skipped} movies with insufficient signal")
    return items


def load_tv_shows(csv_path):
    items = []
    with open(csv_path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            title = row.get("title", "")
            if not title:
                continue

            genres = json.loads(row.get("genres", "[]")) if row.get("genres") else []
            keywords = json.loads(row.get("keywords", "[]")) if row.get("keywords") else []
            cast = json.loads(row.get("cast", "[]")) if row.get("cast") else []

            items.append({
                "title": title,
                "overview": row.get("overview", ""),
                "genres": genres,
                "keywords": keywords[:15],
                "cast": cast[:10],
                "year": row.get("first_air_date", "")[:4] if row.get("first_air_date") else "",
                "vote_average": float(row.get("vote_average", 0) or 0),
                "poster_path": row.get("poster_path", ""),
                "release_date": row.get("first_air_date", ""),
                "type": "tv",
            })
    return items


def build_text(item):
    parts = []
    if item["overview"]:
        parts.append(item["overview"])
    if item["genres"]:
        parts.append("Genres: " + ", ".join(item["genres"]))
    if item["cast"]:
        parts.append("Cast: " + ", ".join(item["cast"][:5]))
    if item["keywords"]:
        parts.append("Keywords: " + ", ".join(item["keywords"][:10]))
    return " ".join(parts)


def main():
    parser = argparse.ArgumentParser(description="Generate recommendations")
    parser.add_argument("--top-k", type=int, default=7)
    parser.add_argument("--batch-size", type=int, default=128)
    parser.add_argument("--movies-csv", default="dataset/movies_expanded.csv")
    parser.add_argument("--credits-csv", default="dataset/credits_expanded.csv")
    parser.add_argument("--tv-csv", default="dataset/tv_shows.csv")
    args = parser.parse_args()

    print("[1/5] Loading datasets...")
    credits_map = load_credits(args.credits_csv)
    if credits_map:
        print(f"  Credits: {len(credits_map)} movies with cast data")
    movies = load_movies(args.movies_csv, credits_map)
    print(f"  Movies: {len(movies)}")

    tv_shows = []
    try:
        tv_shows = load_tv_shows(args.tv_csv)
        print(f"  TV shows: {len(tv_shows)}")
    except FileNotFoundError:
        print("  No TV shows CSV found, movies only")

    all_items = movies + tv_shows

    seen = set()
    unique = []
    for item in all_items:
        if item["title"] not in seen:
            seen.add(item["title"])
            unique.append(item)
    all_items = unique
    print(f"  Combined unique titles: {len(all_items)}")

    print("\n[2/5] Building text representations...")
    texts = [build_text(item) for item in all_items]
    empty = sum(1 for t in texts if len(t.strip()) < 10)
    print(f"  {empty} items with very short text (will get weaker embeddings)")

    print("\n[3/5] Computing embeddings with sentence-transformers...")
    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer("all-MiniLM-L6-v2")

    t0 = time.time()
    embeddings = model.encode(
        texts,
        batch_size=args.batch_size,
        show_progress_bar=True,
        normalize_embeddings=True,
    )
    elapsed = time.time() - t0
    print(f"  {embeddings.shape[0]} embeddings in {elapsed:.1f}s "
          f"({embeddings.shape[0]/elapsed:.0f} items/sec)")

    print(f"\n[4/5] Building FAISS index and finding top-{args.top_k} neighbors...")
    dim = embeddings.shape[1]
    index = faiss.IndexFlatIP(dim)
    index.add(embeddings.astype(np.float32))

    k = args.top_k + 1
    distances, indices = index.search(embeddings.astype(np.float32), k)

    recommendations = {}
    catalog = {}

    for i, item in enumerate(all_items):
        recs = []
        for j in range(k):
            idx = indices[i][j]
            if idx == i:
                continue
            neighbor = all_items[idx]
            recs.append({
                "title": neighbor["title"],
                "score": round(float(distances[i][j]), 4),
            })
        recommendations[item["title"]] = recs[:args.top_k]

        catalog[item["title"]] = {
            "year": item["year"],
            "vote_average": item["vote_average"],
            "poster_path": item["poster_path"],
            "type": item["type"],
            "genres": item["genres"],
            "release_date": item["release_date"],
        }

    print(f"  Generated recommendations for {len(recommendations)} titles")

    print("\n[5/5] Saving outputs...")
    with open("dataset/combined_recommendations.json", "w", encoding="utf-8") as f:
        json.dump(recommendations, f, separators=(",", ":"))
        f.write("\n")
    print(f"  dataset/combined_recommendations.json")

    with open("dataset/combined_catalog.json", "w", encoding="utf-8") as f:
        json.dump(catalog, f, separators=(",", ":"))
        f.write("\n")
    print(f"  dataset/combined_catalog.json")

    print("\nSample recommendations:")
    samples = ["Breaking Bad", "The Sopranos", "The Wire", "Inception", "The Dark Knight"]
    for title in samples:
        if title in recommendations:
            recs = recommendations[title]
            rec_str = ", ".join(f"{r['title']} ({r['score']:.3f})" for r in recs[:3])
            print(f"  {title} → {rec_str}")

    print(f"\nDone! {len(all_items)} titles processed.")


if __name__ == "__main__":
    main()
