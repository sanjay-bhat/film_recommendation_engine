#!/usr/bin/env python3
"""
Fetch YouTube trailer video IDs from TMDb API for all movies in the dataset.

Outputs docs/trailers.json mapping movie titles to YouTube video keys.
Includes rate-limit handling and checkpoint-based resumability.

Usage:
    python scripts/fetch_trailers.py --api-key YOUR_KEY
    python scripts/fetch_trailers.py --api-key YOUR_KEY --resume
"""

import argparse
import json
import os
import sys
import time

import pandas as pd
import requests


CHECKPOINT_PATH = "dataset/trailers_checkpoint.txt"
OUTPUT_PATH = "docs/trailers.json"


def fetch_trailers(tmdb_id, api_key, session):
    url = (
        f"https://api.themoviedb.org/3/movie/{tmdb_id}"
        f"/videos?api_key={api_key}"
    )
    resp = session.get(url, timeout=30)
    if resp.status_code == 404:
        return None
    if resp.status_code == 429:
        retry_after = int(resp.headers.get("Retry-After", 5))
        time.sleep(retry_after)
        return fetch_trailers(tmdb_id, api_key, session)
    resp.raise_for_status()
    return resp.json().get("results", [])


def pick_best_trailer(videos):
    if not videos:
        return None
    yt_videos = [v for v in videos if v.get("site") == "YouTube"]
    if not yt_videos:
        return None
    for v in yt_videos:
        if v.get("type") == "Trailer" and v.get("official"):
            return v["key"]
    for v in yt_videos:
        if v.get("type") == "Trailer":
            return v["key"]
    for v in yt_videos:
        if v.get("type") == "Teaser":
            return v["key"]
    return yt_videos[0]["key"]


def main():
    parser = argparse.ArgumentParser(description="Fetch YouTube trailer IDs from TMDb")
    parser.add_argument("--api-key", required=True, help="TMDb API v3 key")
    parser.add_argument("--resume", action="store_true", help="Resume from checkpoint")
    args = parser.parse_args()

    df = pd.read_csv("dataset/tmdb_5000_movies.csv", usecols=["id", "title"])
    title_map = dict(zip(df["id"], df["title"]))

    done = set()
    if args.resume and os.path.exists(CHECKPOINT_PATH):
        with open(CHECKPOINT_PATH, encoding="utf-8") as f:
            done = set(int(line.strip()) for line in f if line.strip())

    trailers = {}
    if args.resume and os.path.exists(OUTPUT_PATH):
        with open(OUTPUT_PATH, encoding="utf-8") as f:
            trailers = json.load(f)

    remaining = [tid for tid in df["id"] if tid not in done]
    total = len(df)
    print(f"Fetching trailers: {len(remaining)} remaining of {total}")

    session = requests.Session()
    batch_count = 0

    for i, tmdb_id in enumerate(remaining):
        title = title_map.get(tmdb_id, f"ID:{tmdb_id}")
        try:
            videos = fetch_trailers(tmdb_id, args.api_key, session)
            key = pick_best_trailer(videos) if videos else None
            if key:
                trailers[title] = key
            with open(CHECKPOINT_PATH, "a", encoding="utf-8") as f:
                f.write(f"{tmdb_id}\n")
            batch_count += 1
            done_count = len(done) + i + 1
            if batch_count % 50 == 0:
                print(f"  [{done_count}/{total}] {title}: {'✓ ' + key if key else '✗ none'}")
                with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
                    json.dump(trailers, f, separators=(",", ":"))
                    f.write("\n")
        except Exception as e:
            print(f"  ERROR {title} (id={tmdb_id}): {e}", file=sys.stderr)

        if batch_count % 40 == 0:
            time.sleep(0.25)

    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(trailers, f, separators=(",", ":"))
        f.write("\n")

    print(f"\nDone. {len(trailers)} trailers saved to {OUTPUT_PATH}")
    if os.path.exists(CHECKPOINT_PATH):
        os.remove(CHECKPOINT_PATH)


if __name__ == "__main__":
    main()
