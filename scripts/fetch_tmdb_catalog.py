#!/usr/bin/env python3
"""
Fetch movie metadata from TMDb API for all MovieLens 25M movies.

Uses append_to_response=credits,keywords to get everything in one call.
Saves progress to a checkpoint file for resumability.

Usage:
    python scripts/fetch_tmdb_catalog.py --api-key YOUR_KEY
    python scripts/fetch_tmdb_catalog.py --api-key YOUR_KEY --resume
"""

import argparse
import csv
import json
import os
import sys
import time


def load_tmdb_ids(links_path):
    """Read all TMDb IDs from MovieLens links.csv."""
    ids = []
    with open(links_path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            tmdb_id = row.get("tmdbId", "").strip()
            if tmdb_id:
                ids.append(int(tmdb_id))
    return ids


def load_checkpoint(checkpoint_path):
    """Load set of already-fetched TMDb IDs."""
    if not os.path.exists(checkpoint_path):
        return set()
    with open(checkpoint_path, encoding="utf-8") as f:
        return set(int(line.strip()) for line in f if line.strip())


def save_checkpoint(checkpoint_path, tmdb_id):
    """Append a fetched TMDb ID to the checkpoint file."""
    with open(checkpoint_path, "a", encoding="utf-8") as f:
        f.write(f"{tmdb_id}\n")


def fetch_movie(tmdb_id, api_key, session):
    """Fetch movie data with credits and keywords in a single call."""
    url = (
        f"https://api.themoviedb.org/3/movie/{tmdb_id}"
        f"?api_key={api_key}&append_to_response=credits,keywords"
    )
    resp = session.get(url, timeout=30)
    if resp.status_code == 404:
        return None
    if resp.status_code == 429:
        retry_after = int(resp.headers.get("Retry-After", 5))
        time.sleep(retry_after)
        return fetch_movie(tmdb_id, api_key, session)
    resp.raise_for_status()
    return resp.json()


def format_json_array(items, fields=None):
    """Format API objects into JSON arrays matching the CSV format."""
    if not items:
        return "[]"
    if fields:
        items = [{k: item.get(k) for k in fields if k in item} for item in items]
    return json.dumps(items, ensure_ascii=False)


def extract_movie_row(data):
    """Extract a movie CSV row from API response."""
    genres = [{"id": g["id"], "name": g["name"]} for g in data.get("genres", [])]
    keywords_data = data.get("keywords", {}).get("keywords", [])
    keywords = [{"id": k["id"], "name": k["name"]} for k in keywords_data]
    prod_companies = [
        {"name": c.get("name", ""), "id": c.get("id", 0)}
        for c in data.get("production_companies", [])
    ]
    prod_countries = [
        {"iso_3166_1": c.get("iso_3166_1", ""), "name": c.get("name", "")}
        for c in data.get("production_countries", [])
    ]
    spoken_langs = [
        {"iso_639_1": l.get("iso_639_1", ""), "name": l.get("name", "")}
        for l in data.get("spoken_languages", [])
    ]

    return {
        "budget": data.get("budget", 0),
        "genres": json.dumps(genres, ensure_ascii=False),
        "homepage": data.get("homepage", "") or "",
        "id": data["id"],
        "keywords": json.dumps(keywords, ensure_ascii=False),
        "original_language": data.get("original_language", ""),
        "original_title": data.get("original_title", ""),
        "overview": data.get("overview", "") or "",
        "popularity": data.get("popularity", 0),
        "production_companies": json.dumps(prod_companies, ensure_ascii=False),
        "production_countries": json.dumps(prod_countries, ensure_ascii=False),
        "release_date": data.get("release_date", "") or "",
        "revenue": data.get("revenue", 0),
        "runtime": data.get("runtime", 0) or 0,
        "spoken_languages": json.dumps(spoken_langs, ensure_ascii=False),
        "status": data.get("status", ""),
        "tagline": data.get("tagline", "") or "",
        "title": data.get("title", ""),
        "vote_average": data.get("vote_average", 0),
        "vote_count": data.get("vote_count", 0),
    }


def extract_credit_row(data):
    """Extract a credits CSV row from API response."""
    credits = data.get("credits", {})
    cast_fields = ["cast_id", "character", "credit_id", "gender", "id", "name", "order", "profile_path"]
    crew_fields = ["credit_id", "department", "gender", "id", "job", "name", "profile_path"]

    cast = []
    for c in credits.get("cast", []):
        cast.append({k: c.get(k) for k in cast_fields if k in c})

    crew = []
    for c in credits.get("crew", []):
        crew.append({k: c.get(k) for k in crew_fields if k in c})

    return {
        "movie_id": data["id"],
        "title": data.get("title", ""),
        "cast": json.dumps(cast, ensure_ascii=False),
        "crew": json.dumps(crew, ensure_ascii=False),
    }


def main():
    parser = argparse.ArgumentParser(description="Fetch TMDb catalog for MovieLens movies")
    parser.add_argument("--api-key", required=True, help="TMDb API v3 key")
    parser.add_argument("--ml-dir", default="dataset/ml-25m", help="MovieLens 25M directory")
    parser.add_argument("--out-dir", default="dataset", help="Output directory")
    parser.add_argument("--resume", action="store_true", help="Resume from checkpoint")
    parser.add_argument("--batch-size", type=int, default=35, help="Requests per 10-second window")
    args = parser.parse_args()

    import requests
    session = requests.Session()

    links_path = os.path.join(args.ml_dir, "links.csv")
    if not os.path.exists(links_path):
        print(f"Error: {links_path} not found")
        sys.exit(1)

    movies_path = os.path.join(args.out_dir, "movies_expanded.csv")
    credits_path = os.path.join(args.out_dir, "credits_expanded.csv")
    checkpoint_path = os.path.join(args.out_dir, "fetch_checkpoint.txt")

    print("=== TMDb Catalog Fetch ===\n")

    print("Loading MovieLens TMDb IDs...")
    all_ids = load_tmdb_ids(links_path)
    print(f"  {len(all_ids)} movies with TMDb IDs")

    done = set()
    if args.resume:
        done = load_checkpoint(checkpoint_path)
        print(f"  {len(done)} already fetched (resuming)")

    remaining = [tid for tid in all_ids if tid not in done]
    print(f"  {len(remaining)} to fetch\n")

    if not remaining:
        print("Nothing to fetch — all done!")
        return

    movie_headers = [
        "budget", "genres", "homepage", "id", "keywords", "original_language",
        "original_title", "overview", "popularity", "production_companies",
        "production_countries", "release_date", "revenue", "runtime",
        "spoken_languages", "status", "tagline", "title", "vote_average", "vote_count",
    ]
    credit_headers = ["movie_id", "title", "cast", "crew"]

    write_movie_header = not args.resume or not os.path.exists(movies_path)
    write_credit_header = not args.resume or not os.path.exists(credits_path)

    movies_file = open(movies_path, "a" if args.resume else "w", newline="", encoding="utf-8")
    credits_file = open(credits_path, "a" if args.resume else "w", newline="", encoding="utf-8")

    movie_writer = csv.DictWriter(movies_file, fieldnames=movie_headers)
    credit_writer = csv.DictWriter(credits_file, fieldnames=credit_headers)

    if write_movie_header:
        movie_writer.writeheader()
    if write_credit_header:
        credit_writer.writeheader()

    fetched = 0
    skipped = 0
    errors = 0
    window_count = 0
    window_start = time.time()
    total = len(remaining)
    t0 = time.time()

    try:
        for i, tmdb_id in enumerate(remaining):
            if window_count >= args.batch_size:
                elapsed = time.time() - window_start
                if elapsed < 10:
                    time.sleep(10 - elapsed)
                window_count = 0
                window_start = time.time()

            try:
                data = fetch_movie(tmdb_id, args.api_key, session)
                window_count += 1

                if data is None:
                    skipped += 1
                    save_checkpoint(checkpoint_path, tmdb_id)
                    continue

                movie_writer.writerow(extract_movie_row(data))
                credit_writer.writerow(extract_credit_row(data))
                movies_file.flush()
                credits_file.flush()
                save_checkpoint(checkpoint_path, tmdb_id)
                fetched += 1

            except Exception as e:
                errors += 1
                print(f"  Error fetching {tmdb_id}: {e}")
                save_checkpoint(checkpoint_path, tmdb_id)

            if (i + 1) % 500 == 0:
                elapsed = time.time() - t0
                rate = (i + 1) / elapsed
                eta = (total - i - 1) / rate if rate > 0 else 0
                print(
                    f"  [{i+1}/{total}] fetched={fetched} skipped={skipped} "
                    f"errors={errors} rate={rate:.1f}/s ETA={eta/3600:.1f}h"
                )

    except KeyboardInterrupt:
        print(f"\n  Interrupted at {fetched + skipped + errors}/{total}")
    finally:
        movies_file.close()
        credits_file.close()

    elapsed = time.time() - t0
    print(f"\nDone! fetched={fetched} skipped={skipped} errors={errors} in {elapsed/60:.0f}m")
    print(f"  Movies: {movies_path}")
    print(f"  Credits: {credits_path}")


if __name__ == "__main__":
    main()
