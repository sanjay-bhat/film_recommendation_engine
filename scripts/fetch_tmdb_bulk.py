#!/usr/bin/env python3
"""
Fast bulk fetch of TMDb movies/TV via /discover (page-level, no detail calls).

100x faster than per-title fetching: /discover returns overview, genres,
ratings, and poster in each page (20 results). No individual /movie/{id}
calls needed for v1 embeddings.

Splits by year to work around TMDb's 500-page cap, then fetches all years
concurrently within the rate limit.

Performance: ~100K movies in ~25 minutes (vs ~8 hours with detail calls).

Outputs:
  dataset/movies_bulk.csv   — same column layout as generate_recommendations.py expects
  dataset/tv_bulk.csv       — same column layout as tv_shows.csv

Usage:
    python scripts/fetch_tmdb_bulk.py --api-key YOUR_KEY --type movie --min-votes 10
    python scripts/fetch_tmdb_bulk.py --api-key YOUR_KEY --type tv --min-votes 10
    python scripts/fetch_tmdb_bulk.py --api-key YOUR_KEY --type movie --min-votes 10 --limit 100000
"""

import argparse
import asyncio
import csv
import json
import os
import sys
import time

import aiohttp

RATE_LIMIT = 35  # requests per 10-second window
RATE_WINDOW = 10.0


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
                print(f"  Error: {e}", file=sys.stderr)
                return None
            await asyncio.sleep(2)
    return None


async def fetch_genre_map(session, api_key, media_type, limiter):
    url = f"https://api.themoviedb.org/3/genre/{media_type}/list"
    data = await fetch_json(session, url, {"api_key": api_key}, limiter)
    if not data:
        return {}
    return {g["id"]: g["name"] for g in data.get("genres", [])}


async def discover_year(session, api_key, media_type, year, min_votes, genre_map, limiter):
    """Fetch all results for a single year from /discover."""
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
        if media_type == "movie":
            params["include_adult"] = "false"

        data = await fetch_json(session, endpoint, params, limiter)
        if not data:
            break

        results = data.get("results", [])
        if not results:
            break

        for r in results:
            genre_names = [genre_map.get(gid, "") for gid in r.get("genre_ids", []) if gid in genre_map]

            if media_type == "movie":
                items.append({
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
                })
            else:
                items.append({
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
                })

        total_pages = min(data.get("total_pages", 1), 500)
        page += 1
        if page > total_pages:
            break

    return items


async def main_async(args):
    limiter = RateLimiter(RATE_LIMIT, RATE_WINDOW)
    media = args.type
    current_year = int(time.strftime("%Y"))

    async with aiohttp.ClientSession() as session:
        # Fetch genre mapping
        print("[1/3] Fetching genre map...")
        genre_map = await fetch_genre_map(session, args.api_key, media, limiter)
        print(f"  {len(genre_map)} genres loaded")

        # Discover by year — run multiple years concurrently
        print(f"\n[2/3] Discovering {media}s by year (vote_count >= {args.min_votes})...")
        years = list(range(current_year, 1899, -1))

        all_items = []
        seen_ids = set()
        batch_size = 5  # concurrent years at a time

        t0 = time.time()
        for i in range(0, len(years), batch_size):
            batch_years = years[i:i + batch_size]
            tasks = [
                discover_year(session, args.api_key, media, y, args.min_votes, genre_map, limiter)
                for y in batch_years
            ]
            results = await asyncio.gather(*tasks)

            for items in results:
                for item in items:
                    if item["id"] not in seen_ids:
                        seen_ids.add(item["id"])
                        all_items.append(item)

            if args.limit and len(all_items) >= args.limit:
                all_items = all_items[:args.limit]
                break

            elapsed = time.time() - t0
            rate = len(all_items) / elapsed if elapsed > 0 else 0
            yr_range = f"{batch_years[-1]}-{batch_years[0]}"
            print(f"  Years {yr_range}: {len(all_items):,} total ({rate:.0f} titles/sec)")

        elapsed = time.time() - t0
        print(f"\n  Discovered {len(all_items):,} unique {media}s in {elapsed:.0f}s")

        # Write output
        print(f"\n[3/3] Writing CSV...")
        out_dir = args.out_dir
        if media == "movie":
            out_path = os.path.join(out_dir, "movies_bulk.csv")
            headers = [
                "id", "title", "overview", "genres", "keywords",
                "original_language", "original_title", "poster_path",
                "release_date", "vote_average", "vote_count", "popularity",
            ]
        else:
            out_path = os.path.join(out_dir, "tv_bulk.csv")
            headers = [
                "id", "title", "overview", "genres", "keywords", "cast",
                "first_air_date", "vote_average", "vote_count", "popularity",
                "poster_path", "original_language",
            ]

        with open(out_path, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=headers, extrasaction="ignore")
            writer.writeheader()
            writer.writerows(all_items)

        size_mb = os.path.getsize(out_path) / (1024 * 1024)
        print(f"  {out_path}: {len(all_items):,} rows, {size_mb:.1f} MB")

        # Quality stats
        has_overview = sum(1 for item in all_items if len((item.get("overview") or "").strip()) > 10)
        print(f"\n  With overview (>10 chars): {has_overview:,} ({has_overview*100//len(all_items)}%)")
        print(f"  Without overview: {len(all_items) - has_overview:,}")


def main():
    parser = argparse.ArgumentParser(description="Fast bulk TMDb fetch via /discover")
    parser.add_argument("--api-key", required=True, help="TMDb API v3 key")
    parser.add_argument("--type", choices=["movie", "tv"], default="movie")
    parser.add_argument("--min-votes", type=int, default=10)
    parser.add_argument("--limit", type=int, default=None, help="Max titles (default: all)")
    parser.add_argument("--out-dir", default="dataset")
    args = parser.parse_args()

    print(f"=== TMDb Bulk Fetch ({args.type}) ===")
    print(f"  Min votes: {args.min_votes}")
    if args.limit:
        print(f"  Limit: {args.limit:,}")
    print()

    asyncio.run(main_async(args))
    print("\nDone!")


if __name__ == "__main__":
    main()
