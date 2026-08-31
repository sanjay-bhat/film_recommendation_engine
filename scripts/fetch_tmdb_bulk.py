#!/usr/bin/env python3
"""
Fast bulk fetch of TMDb movies/TV via /discover (page-level, no detail calls).

Supports vote-count ranges and append mode for batched expansion:
  Batch 1: --min-votes 10              → ~105K movies, ~19K TV
  Batch 2: --min-votes 5 --max-votes 9 → ~80K more
  Batch 3: --min-votes 2 --max-votes 4 → ~150K more
  Batch 4: --min-votes 1 --max-votes 1 → ~150K+ more

Each batch appends to the same CSV (--append), skipping IDs already present.

Performance: ~100K titles in ~25 min (rate-limited to 35 req/10s).

Outputs:
  dataset/movies_bulk.csv   — same column layout as generate_recommendations.py expects
  dataset/tv_bulk.csv       — same column layout as tv_shows.csv

Usage:
    # Initial fetch
    python scripts/fetch_tmdb_bulk.py --api-key KEY --type movie --min-votes 10

    # Expand in batches (append mode skips existing IDs)
    python scripts/fetch_tmdb_bulk.py --api-key KEY --type movie --min-votes 5 --max-votes 9 --append
    python scripts/fetch_tmdb_bulk.py --api-key KEY --type movie --min-votes 2 --max-votes 4 --append
    python scripts/fetch_tmdb_bulk.py --api-key KEY --type movie --min-votes 1 --max-votes 1 --append
"""

import argparse
import asyncio
import csv
import json
import os
import sys
import time

import aiohttp

RATE_LIMIT = 35
RATE_WINDOW = 10.0

MOVIE_HEADERS = [
    "id", "title", "overview", "genres", "keywords",
    "original_language", "original_title", "poster_path",
    "release_date", "vote_average", "vote_count", "popularity",
]
TV_HEADERS = [
    "id", "title", "overview", "genres", "keywords", "cast",
    "first_air_date", "vote_average", "vote_count", "popularity",
    "poster_path", "original_language",
]


class RateLimiter:
    def __init__(self, max_requests, window):
        self.max_requests = max_requests
        self.window = window
        self.timestamps = []
        self.lock = asyncio.Lock()

    async def acquire(self):
        async with self.lock:
            now = time.monotonic()
            self.timestamps = [t for t in self.timestamps if now - t < self.window]
            if len(self.timestamps) >= self.max_requests:
                sleep_until = self.timestamps[0] + self.window
                await asyncio.sleep(sleep_until - now)
                now = time.monotonic()
                self.timestamps = [t for t in self.timestamps if now - t < self.window]
            self.timestamps.append(now)


async def fetch_json(session, url, params, limiter, retries=3):
    for attempt in range(retries):
        await limiter.acquire()
        try:
            async with session.get(url, params=params, timeout=aiohttp.ClientTimeout(total=30)) as resp:
                if resp.status == 429:
                    wait = int(resp.headers.get("Retry-After", 5))
                    await asyncio.sleep(wait)
                    continue
                if resp.status == 404:
                    return None
                resp.raise_for_status()
                return await resp.json()
        except (aiohttp.ClientError, asyncio.TimeoutError) as e:
            if attempt == retries - 1:
                print(f"  Error: {e}", file=sys.stderr, flush=True)
                return None
            await asyncio.sleep(2)
    return None


async def fetch_genre_map(session, api_key, media_type, limiter):
    url = f"https://api.themoviedb.org/3/genre/{media_type}/list"
    data = await fetch_json(session, url, {"api_key": api_key}, limiter)
    if not data:
        return {}
    return {g["id"]: g["name"] for g in data.get("genres", [])}


def parse_result(r, media_type, genre_map):
    if media_type == "movie":
        return {
            "id": r["id"],
            "title": r.get("title", ""),
            "overview": r.get("overview", "") or "",
            "genres": json.dumps([{"id": gid, "name": genre_map.get(gid, "")} for gid in r.get("genre_ids", [])]),
            "keywords": "[]",
            "original_language": r.get("original_language", ""),
            "original_title": r.get("original_title", ""),
            "poster_path": r.get("poster_path", "") or "",
            "release_date": r.get("release_date", "") or "",
            "vote_average": r.get("vote_average", 0),
            "vote_count": r.get("vote_count", 0),
            "popularity": r.get("popularity", 0),
        }
    else:
        genre_names = [genre_map.get(gid, "") for gid in r.get("genre_ids", []) if gid in genre_map]
        return {
            "id": r["id"],
            "title": r.get("name", ""),
            "overview": r.get("overview", "") or "",
            "genres": json.dumps(genre_names),
            "keywords": "[]",
            "cast": "[]",
            "first_air_date": r.get("first_air_date", "") or "",
            "vote_average": r.get("vote_average", 0),
            "vote_count": r.get("vote_count", 0),
            "popularity": r.get("popularity", 0),
            "poster_path": r.get("poster_path", "") or "",
            "original_language": r.get("original_language", ""),
        }


async def discover_year(session, api_key, media_type, year, min_votes, max_votes, genre_map, limiter):
    date_field = "primary_release_date" if media_type == "movie" else "first_air_date"
    endpoint = f"https://api.themoviedb.org/3/discover/{media_type}"
    items = []
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
        if max_votes is not None:
            params["vote_count.lte"] = max_votes
        if media_type == "movie":
            params["include_adult"] = "false"

        data = await fetch_json(session, endpoint, params, limiter)
        if not data:
            break

        results = data.get("results", [])
        if not results:
            break

        for r in results:
            items.append(parse_result(r, media_type, genre_map))

        total_pages = min(data.get("total_pages", 1), 500)
        page += 1
        if page > total_pages:
            break

    return items


def load_existing_ids(out_path):
    ids = set()
    if not os.path.exists(out_path):
        return ids
    with open(out_path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            try:
                ids.add(int(row["id"]))
            except (KeyError, ValueError):
                pass
    return ids


async def main_async(args):
    limiter = RateLimiter(RATE_LIMIT, RATE_WINDOW)
    media = args.type
    current_year = int(time.strftime("%Y"))

    out_dir = args.out_dir
    headers = MOVIE_HEADERS if media == "movie" else TV_HEADERS
    out_path = os.path.join(out_dir, f"{'movies' if media == 'movie' else 'tv'}_bulk.csv")

    existing_ids = set()
    if args.append:
        existing_ids = load_existing_ids(out_path)
        print(f"[0/3] Append mode: {len(existing_ids):,} existing IDs loaded from {out_path}", flush=True)

    async with aiohttp.ClientSession() as session:
        print("[1/3] Fetching genre map...", flush=True)
        genre_map = await fetch_genre_map(session, args.api_key, media, limiter)
        print(f"  {len(genre_map)} genres loaded", flush=True)

        vote_desc = f"vote_count {args.min_votes}"
        if args.max_votes is not None:
            vote_desc += f"-{args.max_votes}"
        else:
            vote_desc += "+"
        print(f"\n[2/3] Discovering {media}s by year ({vote_desc})...", flush=True)

        years = list(range(current_year, 1899, -1))
        new_items = []
        seen_ids = set(existing_ids)
        batch_size = 5

        t0 = time.time()
        for i in range(0, len(years), batch_size):
            batch_years = years[i:i + batch_size]
            tasks = [
                discover_year(session, args.api_key, media, y, args.min_votes, args.max_votes, genre_map, limiter)
                for y in batch_years
            ]
            results = await asyncio.gather(*tasks)

            for items in results:
                for item in items:
                    if item["id"] not in seen_ids:
                        seen_ids.add(item["id"])
                        new_items.append(item)

            if args.limit and len(new_items) >= args.limit:
                new_items = new_items[:args.limit]
                break

            elapsed = time.time() - t0
            rate = len(new_items) / elapsed if elapsed > 0 else 0
            yr_range = f"{batch_years[-1]}-{batch_years[0]}"
            print(f"  Years {yr_range}: {len(new_items):,} new ({rate:.0f} titles/sec)", flush=True)

        elapsed = time.time() - t0
        print(f"\n  Discovered {len(new_items):,} new {media}s in {elapsed:.0f}s", flush=True)

        print(f"\n[3/3] Writing CSV...", flush=True)
        if args.append and os.path.exists(out_path):
            with open(out_path, "a", newline="", encoding="utf-8") as f:
                writer = csv.DictWriter(f, fieldnames=headers, extrasaction="ignore")
                writer.writerows(new_items)
            total = len(existing_ids) + len(new_items)
        else:
            with open(out_path, "w", newline="", encoding="utf-8") as f:
                writer = csv.DictWriter(f, fieldnames=headers, extrasaction="ignore")
                writer.writeheader()
                writer.writerows(new_items)
            total = len(new_items)

        size_mb = os.path.getsize(out_path) / (1024 * 1024)
        print(f"  {out_path}: {total:,} total rows ({len(new_items):,} new), {size_mb:.1f} MB", flush=True)

        has_overview = sum(1 for item in new_items if len((item.get("overview") or "").strip()) > 10)
        if new_items:
            print(f"\n  New titles with overview (>10 chars): {has_overview:,} ({has_overview*100//len(new_items)}%)", flush=True)
            print(f"  New titles without overview: {len(new_items) - has_overview:,}", flush=True)


def main():
    parser = argparse.ArgumentParser(description="Fast bulk TMDb fetch via /discover")
    parser.add_argument("--api-key", required=True, help="TMDb API v3 key")
    parser.add_argument("--type", choices=["movie", "tv"], default="movie")
    parser.add_argument("--min-votes", type=int, default=10)
    parser.add_argument("--max-votes", type=int, default=None, help="Upper vote_count bound (for range batches)")
    parser.add_argument("--limit", type=int, default=None, help="Max new titles to fetch")
    parser.add_argument("--append", action="store_true", help="Append to existing CSV, skip known IDs")
    parser.add_argument("--out-dir", default="dataset")
    args = parser.parse_args()

    print(f"=== TMDb Bulk Fetch ({args.type}) ===", flush=True)
    print(f"  Vote range: {args.min_votes}", end="", flush=True)
    if args.max_votes is not None:
        print(f"-{args.max_votes}")
    else:
        print("+")
    if args.limit:
        print(f"  Limit: {args.limit:,}")
    if args.append:
        print(f"  Mode: append")
    print(flush=True)

    asyncio.run(main_async(args))
    print("\nDone!", flush=True)


if __name__ == "__main__":
    main()
