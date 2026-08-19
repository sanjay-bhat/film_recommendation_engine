#!/usr/bin/env python3
"""
Fetch plot overviews from TMDb API for movies missing them.

Uses asyncio + aiohttp with rate limiting (35 req/10s window).
Only fetches for genome-tagged movies not in TMDb 5000.
Writes overviews to a simple CSV: tmdb_id,overview

Usage:
    python scripts/fetch_overviews.py --api-key YOUR_KEY
    python scripts/fetch_overviews.py --api-key YOUR_KEY --resume
"""

import argparse
import asyncio
import csv
import os
import sys
import time


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


async def fetch_batch(session, ids, api_key, results, semaphore):
    """Fetch a batch of movie overviews concurrently."""
    async def fetch_one(tmdb_id):
        async with semaphore:
            url = f"https://api.themoviedb.org/3/movie/{tmdb_id}?api_key={api_key}"
            try:
                async with session.get(url, timeout=asyncio.timeout(15)) as resp:
                    if resp.status == 429:
                        retry = int(resp.headers.get("Retry-After", 5))
                        await asyncio.sleep(retry)
                        async with session.get(url, timeout=asyncio.timeout(15)) as resp2:
                            if resp2.status == 200:
                                data = await resp2.json()
                                results[tmdb_id] = data.get("overview", "") or ""
                    elif resp.status == 200:
                        data = await resp.json()
                        results[tmdb_id] = data.get("overview", "") or ""
                    elif resp.status == 404:
                        results[tmdb_id] = ""
            except Exception:
                results[tmdb_id] = ""

    tasks = [fetch_one(tid) for tid in ids]
    await asyncio.gather(*tasks)


async def run_fetch(target_ids, api_key, out_path, batch_size=35):
    """Fetch overviews with rate limiting."""
    import aiohttp

    results = {}
    semaphore = asyncio.Semaphore(batch_size)

    async with aiohttp.ClientSession() as session:
        total = len(target_ids)
        t0 = time.time()

        for i in range(0, total, batch_size):
            batch = target_ids[i:i + batch_size]
            window_start = time.time()

            await fetch_batch(session, batch, api_key, results, semaphore)

            elapsed_window = time.time() - window_start
            if elapsed_window < 10 and i + batch_size < total:
                await asyncio.sleep(10 - elapsed_window)

            done = i + len(batch)
            if done % 500 < batch_size or done >= total:
                elapsed = time.time() - t0
                rate = done / elapsed if elapsed > 0 else 0
                eta = (total - done) / rate if rate > 0 else 0
                print(f"  [{done}/{total}] rate={rate:.1f}/s ETA={eta/60:.0f}min")

    with open(out_path, "a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        for tmdb_id, overview in sorted(results.items()):
            writer.writerow([tmdb_id, overview])

    return len(results)


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

    print(f"\nFetching overviews (35 req/10s window)...")
    fetched = asyncio.run(run_fetch(target_ids, args.api_key, args.out))
    print(f"\nDone! Fetched {fetched} overviews → {args.out}")


if __name__ == "__main__":
    main()
