#!/usr/bin/env python3
"""
Fetch movies and TV shows from TMDb using the /discover endpoint.

Unlike fetch_tmdb_catalog.py (which reads IDs from MovieLens links.csv),
this script discovers titles directly from TMDb's catalog filtered by
minimum vote count. This removes the MovieLens dependency and gives
access to TMDb's full ~900K movie + ~170K TV show catalog.

Two phases:
  1. Paginate /discover/movie (or /tv) to collect all matching IDs
  2. Fetch full details for each ID (credits, keywords, videos)

Outputs (same format as the existing pipeline):
  - dataset/movies_discovered.csv     (same columns as movies_expanded.csv)
  - dataset/credits_discovered.csv    (same columns as credits_expanded.csv)
  - dataset/tv_discovered.csv         (same columns as tv_shows.csv)

Usage:
    # Movies with 20+ votes (~120K titles, ~10 hours)
    python scripts/fetch_tmdb_discover.py --api-key YOUR_KEY --type movie --min-votes 20

    # TV shows with 20+ votes
    python scripts/fetch_tmdb_discover.py --api-key YOUR_KEY --type tv --min-votes 20

    # Resume after interruption
    python scripts/fetch_tmdb_discover.py --api-key YOUR_KEY --type movie --min-votes 20 --resume

    # Smaller test run
    python scripts/fetch_tmdb_discover.py --api-key YOUR_KEY --type movie --min-votes 100 --limit 500
"""

import argparse
import csv
import json
import os
import sys
import time

import requests

BATCH_SIZE = 35  # requests per 10-second window (TMDb allows ~40)


def discover_ids(api_key, session, media_type, min_votes, limit=None):
    """Paginate /discover/movie or /discover/tv to collect IDs."""
    endpoint = f"https://api.themoviedb.org/3/discover/{media_type}"
    ids = []
    page = 1
    total_pages = None

    while True:
        params = {
            "api_key": api_key,
            "page": page,
            "sort_by": "vote_count.desc",
            "vote_count.gte": min_votes,
        }
        if media_type == "movie":
            params["include_adult"] = "false"

        resp = session.get(endpoint, params=params, timeout=30)
        if resp.status_code == 429:
            wait = int(resp.headers.get("Retry-After", 5))
            time.sleep(wait)
            continue
        resp.raise_for_status()
        data = resp.json()

        results = data.get("results", [])
        if not results:
            break

        for r in results:
            ids.append(r["id"])

        if total_pages is None:
            total_pages = min(data.get("total_pages", 1), 500)  # TMDb caps at 500 pages
            total_results = data.get("total_results", 0)
            print(f"  TMDb reports {total_results} results across {data.get('total_pages', 1)} pages (capped at 500)")

        if page % 20 == 0:
            print(f"  Page {page}/{total_pages} — {len(ids)} IDs so far")

        page += 1
        if page > total_pages:
            break
        if limit and len(ids) >= limit:
            ids = ids[:limit]
            break

        if page % 40 == 0:
            time.sleep(0.25)

    return ids


def discover_ids_full(api_key, session, media_type, min_votes, limit=None):
    """Collect all IDs, working around TMDb's 500-page cap with year ranges."""
    ids = discover_ids(api_key, session, media_type, min_votes, limit)

    if limit and len(ids) >= limit:
        return ids[:limit]

    # If we hit exactly 10,000 (500 pages × 20), there are more results.
    # Split by year ranges to get around the cap.
    if len(ids) < 10000:
        return ids

    print(f"\n  Hit 500-page cap ({len(ids)} IDs). Splitting by year to get full catalog...")
    all_ids = set(ids)
    date_field = "primary_release_date" if media_type == "movie" else "first_air_date"

    current_year = int(time.strftime("%Y"))
    for year in range(current_year, 1899, -1):
        if limit and len(all_ids) >= limit:
            break

        endpoint = f"https://api.themoviedb.org/3/discover/{media_type}"
        page = 1

        while True:
            params = {
                "api_key": api_key,
                "page": page,
                "sort_by": "vote_count.desc",
                "vote_count.gte": min_votes,
                f"{date_field}.gte": f"{year}-01-01",
                f"{date_field}.lte": f"{year}-12-31",
            }
            if media_type == "movie":
                params["include_adult"] = "false"

            resp = session.get(endpoint, params=params, timeout=30)
            if resp.status_code == 429:
                wait = int(resp.headers.get("Retry-After", 5))
                time.sleep(wait)
                continue
            resp.raise_for_status()
            data = resp.json()

            results = data.get("results", [])
            if not results:
                break

            new_count = 0
            for r in results:
                if r["id"] not in all_ids:
                    all_ids.add(r["id"])
                    new_count += 1

            total_pages = min(data.get("total_pages", 1), 500)
            page += 1
            if page > total_pages:
                break
            if page % 40 == 0:
                time.sleep(0.25)

        if year % 10 == 0:
            print(f"  Year {year}: {len(all_ids)} total IDs")

    return list(all_ids)[:limit] if limit else list(all_ids)


def load_checkpoint(path):
    if not os.path.exists(path):
        return set()
    with open(path, encoding="utf-8") as f:
        return set(int(line.strip()) for line in f if line.strip())


def save_checkpoint(path, tmdb_id):
    with open(path, "a", encoding="utf-8") as f:
        f.write(f"{tmdb_id}\n")


def fetch_details(tmdb_id, api_key, session, media_type):
    """Fetch full details with credits, keywords, and videos."""
    append = "credits,keywords,videos" if media_type == "tv" else "credits,keywords"
    url = (
        f"https://api.themoviedb.org/3/{media_type}/{tmdb_id}"
        f"?api_key={api_key}&append_to_response={append}"
    )
    resp = session.get(url, timeout=30)
    if resp.status_code == 404:
        return None
    if resp.status_code == 429:
        wait = int(resp.headers.get("Retry-After", 5))
        time.sleep(wait)
        return fetch_details(tmdb_id, api_key, session, media_type)
    resp.raise_for_status()
    return resp.json()


def extract_movie_row(data):
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
    credits = data.get("credits", {})
    cast_fields = ["cast_id", "character", "credit_id", "gender", "id", "name", "order", "profile_path"]
    crew_fields = ["credit_id", "department", "gender", "id", "job", "name", "profile_path"]

    cast = [{k: c.get(k) for k in cast_fields if k in c} for c in credits.get("cast", [])]
    crew = [{k: c.get(k) for k in crew_fields if k in c} for c in credits.get("crew", [])]

    return {
        "movie_id": data["id"],
        "title": data.get("title") or data.get("name", ""),
        "cast": json.dumps(cast, ensure_ascii=False),
        "crew": json.dumps(crew, ensure_ascii=False),
    }


def extract_tv_row(data):
    cast_list = data.get("credits", {}).get("cast", [])[:10]
    cast_names = [c["name"] for c in cast_list]
    genre_list = [g["name"] for g in data.get("genres", [])]
    keyword_results = data.get("keywords", {}).get("results", [])
    keyword_list = [k["name"] for k in keyword_results]
    network_list = [n["name"] for n in data.get("networks", [])]
    created_list = [c["name"] for c in data.get("created_by", [])]

    trailer_key = None
    videos = data.get("videos", {}).get("results", [])
    yt = [v for v in videos if v.get("site") == "YouTube"]
    for v in yt:
        if v.get("type") == "Trailer" and v.get("official"):
            trailer_key = v["key"]
            break
    if not trailer_key:
        for v in yt:
            if v.get("type") == "Trailer":
                trailer_key = v["key"]
                break
    if not trailer_key:
        for v in yt:
            if v.get("type") in ("Teaser", "Opening Credits"):
                trailer_key = v["key"]
                break
    if not trailer_key and yt:
        trailer_key = yt[0]["key"]

    return {
        "id": data["id"],
        "title": data.get("name", ""),
        "overview": data.get("overview", ""),
        "genres": json.dumps(genre_list),
        "keywords": json.dumps(keyword_list),
        "cast": json.dumps(cast_names),
        "first_air_date": data.get("first_air_date", ""),
        "vote_average": data.get("vote_average", 0),
        "vote_count": data.get("vote_count", 0),
        "popularity": data.get("popularity", 0),
        "poster_path": data.get("poster_path", ""),
        "number_of_seasons": data.get("number_of_seasons", 0),
        "number_of_episodes": data.get("number_of_episodes", 0),
        "status": data.get("status", ""),
        "networks": json.dumps(network_list),
        "created_by": json.dumps(created_list),
        "original_language": data.get("original_language", ""),
        "trailer_key": trailer_key,
    }


MOVIE_HEADERS = [
    "budget", "genres", "homepage", "id", "keywords", "original_language",
    "original_title", "overview", "popularity", "production_companies",
    "production_countries", "release_date", "revenue", "runtime",
    "spoken_languages", "status", "tagline", "title", "vote_average", "vote_count",
]
CREDIT_HEADERS = ["movie_id", "title", "cast", "crew"]
TV_HEADERS = [
    "id", "title", "overview", "genres", "keywords", "cast",
    "first_air_date", "vote_average", "vote_count", "popularity",
    "poster_path", "number_of_seasons", "number_of_episodes",
    "status", "networks", "created_by", "original_language",
]


def main():
    parser = argparse.ArgumentParser(description="Fetch TMDb catalog via /discover endpoint")
    parser.add_argument("--api-key", required=True, help="TMDb API v3 key")
    parser.add_argument("--type", choices=["movie", "tv"], default="movie", help="Media type to fetch")
    parser.add_argument("--min-votes", type=int, default=20, help="Minimum vote count filter")
    parser.add_argument("--limit", type=int, default=None, help="Max titles to fetch (default: all)")
    parser.add_argument("--out-dir", default="dataset", help="Output directory")
    parser.add_argument("--resume", action="store_true", help="Resume from checkpoint")
    args = parser.parse_args()

    session = requests.Session()
    media = args.type

    suffix = "discovered"
    if media == "movie":
        data_path = os.path.join(args.out_dir, f"movies_{suffix}.csv")
        credits_path = os.path.join(args.out_dir, f"credits_{suffix}.csv")
    else:
        data_path = os.path.join(args.out_dir, f"tv_{suffix}.csv")
        credits_path = None

    checkpoint_path = os.path.join(args.out_dir, f"discover_{media}_checkpoint.txt")

    print(f"=== TMDb Discover Fetch ({media}) ===")
    print(f"  Min votes: {args.min_votes}")
    if args.limit:
        print(f"  Limit: {args.limit}")
    print()

    # Phase 1: Discover IDs
    print("[1/2] Discovering IDs...")
    all_ids = discover_ids_full(args.api_key, session, media, args.min_votes, args.limit)
    print(f"  Total IDs discovered: {len(all_ids)}")

    done = set()
    if args.resume:
        done = load_checkpoint(checkpoint_path)
        print(f"  Already fetched: {len(done)} (resuming)")

    remaining = [tid for tid in all_ids if tid not in done]
    print(f"  Remaining to fetch: {len(remaining)}")

    if not remaining:
        print("\nNothing to fetch — all done!")
        return

    est_hours = len(remaining) / BATCH_SIZE * 10 / 3600
    print(f"  Estimated time: {est_hours:.1f} hours")

    # Phase 2: Fetch details
    print(f"\n[2/2] Fetching details...")

    write_header = not args.resume or not os.path.exists(data_path)
    data_file = open(data_path, "a" if args.resume else "w", newline="", encoding="utf-8")

    if media == "movie":
        data_writer = csv.DictWriter(data_file, fieldnames=MOVIE_HEADERS)
        credits_write_header = not args.resume or not os.path.exists(credits_path)
        credits_file = open(credits_path, "a" if args.resume else "w", newline="", encoding="utf-8")
        credits_writer = csv.DictWriter(credits_file, fieldnames=CREDIT_HEADERS)
        if credits_write_header:
            credits_writer.writeheader()
    else:
        data_writer = csv.DictWriter(data_file, fieldnames=TV_HEADERS)
        credits_file = None
        credits_writer = None

    if write_header:
        data_writer.writeheader()

    trailers = {}
    if media == "tv" and args.resume:
        trailers_path = os.path.join("docs", "tv_trailers_discovered.json")
        if os.path.exists(trailers_path):
            with open(trailers_path, encoding="utf-8") as f:
                trailers = json.load(f)

    fetched = 0
    skipped = 0
    errors = 0
    window_count = 0
    window_start = time.time()
    total = len(remaining)
    t0 = time.time()

    try:
        for i, tmdb_id in enumerate(remaining):
            if window_count >= BATCH_SIZE:
                elapsed = time.time() - window_start
                if elapsed < 10:
                    time.sleep(10 - elapsed)
                window_count = 0
                window_start = time.time()

            try:
                data = fetch_details(tmdb_id, args.api_key, session, media)
                window_count += 1

                if data is None:
                    skipped += 1
                    save_checkpoint(checkpoint_path, tmdb_id)
                    continue

                if media == "movie":
                    data_writer.writerow(extract_movie_row(data))
                    credits_writer.writerow(extract_credit_row(data))
                    credits_file.flush()
                else:
                    row = extract_tv_row(data)
                    trailer_key = row.pop("trailer_key", None)
                    data_writer.writerow(row)
                    if trailer_key:
                        trailers[row["title"]] = trailer_key

                data_file.flush()
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
                if media == "tv" and trailers:
                    trailers_path = os.path.join("docs", "tv_trailers_discovered.json")
                    with open(trailers_path, "w", encoding="utf-8") as f:
                        json.dump(trailers, f, separators=(",", ":"))

    except KeyboardInterrupt:
        print(f"\n  Interrupted at {fetched + skipped + errors}/{total}")
    finally:
        data_file.close()
        if credits_file:
            credits_file.close()

    if media == "tv" and trailers:
        trailers_path = os.path.join("docs", "tv_trailers_discovered.json")
        with open(trailers_path, "w", encoding="utf-8") as f:
            json.dump(trailers, f, separators=(",", ":"))

    elapsed = time.time() - t0
    print(f"\nDone! fetched={fetched} skipped={skipped} errors={errors} in {elapsed/60:.0f}m")
    print(f"  Output: {data_path}")
    if credits_path:
        print(f"  Credits: {credits_path}")
    if trailers:
        print(f"  Trailers: {len(trailers)}")


if __name__ == "__main__":
    main()
