#!/usr/bin/env python3
"""
Fetch plot overviews from TMDb API for movies missing them.

Uses asyncio + aiohttp with concurrent requests (40 at a time).
Only fetches for genome-tagged movies not in TMDb 5000.
Writes overviews incrementally to a CSV: tmdb_id,overview

Usage:
    python scripts/fetch_overviews.py --api-key YOUR_KEY
    python scripts/fetch_overviews.py --api-key YOUR_KEY --resume
"""

import argparse
import asyncio
import csv
import os
import time

import aiohttp


def get_target_ids(ml_dir, tmdb_movies_path):
    """Get TMDb IDs of genome-tagged movies not in TMDb 5000."""
    ml_to_tmdb = {}
    with open(os.path.join(ml_dir, "links.csv"), encoding="utf-8") as f:
        for row in csv.DictReader(f):
            t = row.get("tmdbId", "").strip()
            if t:
                ml_to_tmdb[int(row["movieId"])] = int(t)

    genome_ml_ids = set()
    with open(os.path.join(ml_dir, "genome-scores.csv"), encoding="utf-8") as f:
        for row in csv.DictReader(f):
            genome_ml_ids.add(int(row["movieId"]))

    genome_tmdb = {ml_to_tmdb[m] for m in genome_ml_ids if m in ml_to_tmdb}

    tmdb5000 = set()
    with open(tmdb_movies_path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            tmdb5000.add(int(row["id"]))

    return sorted(genome_tmdb - tmdb5000)


def load_done(out_path):
    """Load already-fetched IDs from output CSV."""
    done = set()
    if not os.path.exists(out_path):
        return done
    with open(out_path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            done.add(int(row["tmdb_id"]))
    return done


async def run_fetch(target_ids, api_key, out_path, concurrency=40):
    """Fetch overviews concurrently with a semaphore-based rate limiter."""
    semaphore = asyncio.Semaphore(concurrency)
    timeout = aiohttp.ClientTimeout(total=5)
    lock = asyncio.Lock()
    counter = {"done": 0, "total": len(target_ids)}
    t0 = time.time()

    async def fetch_one(session, tmdb_id):
        async with semaphore:
            url = f"https://api.themoviedb.org/3/movie/{tmdb_id}?api_key={api_key}"
            overview = ""
            try:
                async with session.get(url, timeout=timeout) as resp:
                    if resp.status == 429:
                        retry = int(resp.headers.get("Retry-After", 2))
                        await asyncio.sleep(retry)
                        async with session.get(url, timeout=timeout) as resp2:
                            if resp2.status == 200:
                                data = await resp2.json()
                                overview = data.get("overview", "") or ""
                    elif resp.status == 200:
                        data = await resp.json()
                        overview = data.get("overview", "") or ""
            except Exception:
                pass

            async with lock:
                with open(out_path, "a", newline="", encoding="utf-8") as f:
                    csv.writer(f).writerow([tmdb_id, overview])
                counter["done"] += 1
                done = counter["done"]
                if done % 500 == 0 or done == counter["total"]:
                    elapsed = time.time() - t0
                    rate = done / elapsed if elapsed > 0 else 0
                    remaining = counter["total"] - done
                    eta = remaining / rate if rate > 0 else 0
                    print(f"  [{done}/{counter['total']}] "
                          f"rate={rate:.1f}/s ETA={eta/60:.1f}min", flush=True)

    connector = aiohttp.TCPConnector(limit=concurrency)
    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = [fetch_one(session, tid) for tid in target_ids]
        await asyncio.gather(*tasks)

    return counter["done"]


def main():
    parser = argparse.ArgumentParser(description="Fetch plot overviews from TMDb API")
    parser.add_argument("--api-key", required=True)
    parser.add_argument("--ml-dir", default="dataset/ml-25m")
    parser.add_argument("--tmdb-movies", default="dataset/tmdb_5000_movies.csv")
    parser.add_argument("--out", default="dataset/overviews_extra.csv")
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()

    print("=== Fetching Plot Overviews ===\n")

    print("Identifying movies needing overviews...")
    target_ids = get_target_ids(args.ml_dir, args.tmdb_movies)
    print(f"  {len(target_ids)} genome-tagged movies outside TMDb 5000")

    if args.resume:
        done = load_done(args.out)
        target_ids = [t for t in target_ids if t not in done]
        print(f"  {len(done)} already fetched, {len(target_ids)} remaining")

    if not target_ids:
        print("Nothing to fetch!")
        return

    write_header = not args.resume or not os.path.exists(args.out)
    if write_header:
        with open(args.out, "w", newline="", encoding="utf-8") as f:
            csv.writer(f).writerow(["tmdb_id", "overview"])

    concurrency = 40
    print(f"\nFetching overviews ({len(target_ids)} movies, {concurrency} concurrent)...")
    fetched = asyncio.run(run_fetch(target_ids, args.api_key, args.out, concurrency))
    print(f"\nDone! Fetched {fetched} overviews -> {args.out}")


if __name__ == "__main__":
    main()
