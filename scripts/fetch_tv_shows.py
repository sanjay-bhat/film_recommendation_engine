#!/usr/bin/env python3
"""
Fetch top-rated TV shows from TMDb API with full metadata.

Collects: title, overview, genres, cast, keywords, first_air_date,
vote_average, poster_path, trailer_key, number_of_seasons, networks.

Usage:
    python scripts/fetch_tv_shows.py --api-key YOUR_KEY
    python scripts/fetch_tv_shows.py --api-key YOUR_KEY --resume --limit 2000
"""

import argparse
import csv
import json
import os
import sys
import time

import requests

CHECKPOINT_PATH = "dataset/tv_checkpoint.txt"
OUTPUT_CSV = "dataset/tv_shows.csv"
OUTPUT_JSON = "dataset/tv_shows.json"
TRAILERS_JSON = "docs/tv_trailers.json"

CSV_FIELDS = [
    "id", "title", "overview", "genres", "keywords", "cast",
    "first_air_date", "vote_average", "vote_count", "popularity",
    "poster_path", "number_of_seasons", "number_of_episodes",
    "status", "networks", "created_by", "original_language",
]

session = requests.Session()


def tmdb_get(url, api_key, retries=3):
    for attempt in range(retries):
        resp = session.get(url, params={"api_key": api_key}, timeout=30)
        if resp.status_code == 429:
            wait = int(resp.headers.get("Retry-After", 2))
            time.sleep(wait)
            continue
        if resp.status_code == 404:
            return None
        resp.raise_for_status()
        return resp.json()
    return None


def fetch_top_rated_ids(api_key, limit):
    ids = []
    page = 1
    while len(ids) < limit:
        data = tmdb_get(
            "https://api.themoviedb.org/3/tv/top_rated",
            api_key,
        )
        if not data:
            break
        data = session.get(
            "https://api.themoviedb.org/3/tv/top_rated",
            params={"api_key": api_key, "page": page},
            timeout=30,
        ).json()
        results = data.get("results", [])
        if not results:
            break
        for r in results:
            ids.append(r["id"])
        total_pages = data.get("total_pages", 1)
        page += 1
        if page > total_pages:
            break
        if page % 40 == 0:
            time.sleep(0.25)
    return ids[:limit]


def fetch_show_details(show_id, api_key):
    base = f"https://api.themoviedb.org/3/tv/{show_id}"
    details = tmdb_get(base, api_key)
    if not details:
        return None

    credits = tmdb_get(f"{base}/credits", api_key) or {}
    keywords_data = tmdb_get(f"{base}/keywords", api_key) or {}
    videos = tmdb_get(f"{base}/videos", api_key) or {}

    cast_list = credits.get("cast", [])[:10]
    cast_names = [c["name"] for c in cast_list]

    genre_list = [g["name"] for g in details.get("genres", [])]
    keyword_list = [k["name"] for k in keywords_data.get("results", [])]
    network_list = [n["name"] for n in details.get("networks", [])]
    created_list = [c["name"] for c in details.get("created_by", [])]

    yt = [v for v in videos.get("results", []) if v.get("site") == "YouTube"]
    trailer_key = None
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
        "id": details["id"],
        "title": details.get("name", ""),
        "overview": details.get("overview", ""),
        "genres": json.dumps(genre_list),
        "keywords": json.dumps(keyword_list),
        "cast": json.dumps(cast_names),
        "first_air_date": details.get("first_air_date", ""),
        "vote_average": details.get("vote_average", 0),
        "vote_count": details.get("vote_count", 0),
        "popularity": details.get("popularity", 0),
        "poster_path": details.get("poster_path", ""),
        "number_of_seasons": details.get("number_of_seasons", 0),
        "number_of_episodes": details.get("number_of_episodes", 0),
        "status": details.get("status", ""),
        "networks": json.dumps(network_list),
        "created_by": json.dumps(created_list),
        "original_language": details.get("original_language", ""),
        "trailer_key": trailer_key,
    }


def main():
    parser = argparse.ArgumentParser(description="Fetch TV shows from TMDb")
    parser.add_argument("--api-key", required=True, help="TMDb API v3 key")
    parser.add_argument("--limit", type=int, default=2000, help="Max shows to fetch")
    parser.add_argument("--resume", action="store_true", help="Resume from checkpoint")
    args = parser.parse_args()

    print(f"[1/3] Fetching top-rated TV show IDs (limit={args.limit})...")
    all_ids = fetch_top_rated_ids(args.api_key, args.limit)
    print(f"  Found {len(all_ids)} show IDs")

    done = set()
    if args.resume and os.path.exists(CHECKPOINT_PATH):
        with open(CHECKPOINT_PATH, encoding="utf-8") as f:
            done = set(int(line.strip()) for line in f if line.strip())
        print(f"  Resuming — {len(done)} already fetched")

    shows = []
    if args.resume and os.path.exists(OUTPUT_CSV):
        with open(OUTPUT_CSV, encoding="utf-8") as f:
            reader = csv.DictReader(f)
            shows = list(reader)
        print(f"  Loaded {len(shows)} existing shows from CSV")

    trailers = {}
    if args.resume and os.path.exists(TRAILERS_JSON):
        with open(TRAILERS_JSON, encoding="utf-8") as f:
            trailers = json.load(f)

    remaining = [sid for sid in all_ids if sid not in done]
    print(f"\n[2/3] Fetching details for {len(remaining)} shows...")

    for i, show_id in enumerate(remaining):
        try:
            row = fetch_show_details(show_id, args.api_key)
            if row:
                trailer_key = row.pop("trailer_key", None)
                shows.append(row)
                if trailer_key:
                    trailers[row["title"]] = trailer_key

                with open(CHECKPOINT_PATH, "a", encoding="utf-8") as f:
                    f.write(f"{show_id}\n")
                done.add(show_id)

                if (i + 1) % 50 == 0:
                    print(f"  [{len(done)}/{len(all_ids)}] {row['title']}: "
                          f"{'✓ trailer' if trailer_key else '✗ no trailer'}")
                    save_outputs(shows, trailers)

        except Exception as e:
            print(f"  ERROR id={show_id}: {e}", file=sys.stderr)

        if (i + 1) % 40 == 0:
            time.sleep(0.25)

    save_outputs(shows, trailers)

    if os.path.exists(CHECKPOINT_PATH):
        os.remove(CHECKPOINT_PATH)

    print(f"\n[3/3] Done!")
    print(f"  {len(shows)} TV shows saved to {OUTPUT_CSV}")
    print(f"  {len(trailers)} trailers saved to {TRAILERS_JSON}")


def save_outputs(shows, trailers):
    with open(OUTPUT_CSV, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(shows)

    shows_json = {}
    for s in shows:
        shows_json[s["title"]] = {
            "id": int(s["id"]),
            "overview": s["overview"],
            "genres": json.loads(s["genres"]) if isinstance(s["genres"], str) else s["genres"],
            "keywords": json.loads(s["keywords"]) if isinstance(s["keywords"], str) else s["keywords"],
            "cast": json.loads(s["cast"]) if isinstance(s["cast"], str) else s["cast"],
            "vote_average": float(s["vote_average"]),
            "poster_path": s["poster_path"],
            "first_air_date": s["first_air_date"],
        }
    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(shows_json, f, separators=(",", ":"))
        f.write("\n")

    with open(TRAILERS_JSON, "w", encoding="utf-8") as f:
        json.dump(trailers, f, separators=(",", ":"))
        f.write("\n")


if __name__ == "__main__":
    main()
