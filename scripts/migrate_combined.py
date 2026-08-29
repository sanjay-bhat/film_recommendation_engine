#!/usr/bin/env python3
"""
Migrate combined movie + TV show catalog and recommendations to Supabase.

Steps:
  1. Upsert movies into movies table with type='movie'
  2. Upsert TV shows into movies table with type='tv'
  3. Fetch movie ID map
  4. Upsert recommendations from combined_recommendations.json

Usage:
    python scripts/migrate_combined.py \
        --url https://xxxxx.supabase.co \
        --key YOUR_SERVICE_ROLE_KEY
"""

import argparse
import json
import sys

import requests


def sb_request(method, url, key, path, data=None, params=None):
    headers = {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal",
    }
    endpoint = f"{url}/rest/v1/{path}"
    resp = getattr(requests, method)(
        endpoint, headers=headers, json=data, params=params, timeout=60,
    )
    return resp


def sb_insert(url, key, table, rows, batch_size=500, upsert=False):
    headers = {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal,resolution=merge-duplicates" if upsert else "return=minimal",
    }
    inserted = 0
    for i in range(0, len(rows), batch_size):
        batch = rows[i:i + batch_size]
        resp = requests.post(
            f"{url}/rest/v1/{table}",
            headers=headers, json=batch, timeout=60,
        )
        if resp.status_code not in (200, 201, 204):
            print(f"  ERROR at batch {i}: {resp.status_code} {resp.text[:300]}")
            sys.exit(1)
        inserted += len(batch)
        if inserted % 1000 == 0 or inserted == len(rows):
            print(f"  {table}: {inserted}/{len(rows)}")
    return inserted


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--key", required=True)
    args = parser.parse_args()

    with open("dataset/combined_catalog.json") as f:
        catalog = json.load(f)
    with open("dataset/combined_recommendations.json") as f:
        recommendations = json.load(f)

    tv_trailers = {}
    try:
        with open("docs/tv_trailers.json") as f:
            tv_trailers = json.load(f)
    except FileNotFoundError:
        pass

    movie_trailers = {}
    try:
        with open("docs/trailers.json") as f:
            movie_trailers = json.load(f)
    except FileNotFoundError:
        pass

    movie_items = {t: c for t, c in catalog.items() if c["type"] == "movie"}
    tv_items = {t: c for t, c in catalog.items() if c["type"] == "tv"}
    print(f"Loaded {len(catalog)} titles ({len(movie_items)} movies, {len(tv_items)} TV shows)")
    print(f"Loaded {len(recommendations)} recommendation sets")
    print(f"Trailers: {len(movie_trailers)} movies, {len(tv_trailers)} TV shows")

    print("\n[1/4] Upserting movies...")
    movie_rows = []
    for title, meta in movie_items.items():
        year = None
        if meta.get("release_date") and len(meta["release_date"]) >= 4:
            try:
                year = int(meta["release_date"][:4])
            except ValueError:
                pass
        movie_rows.append({
            "title": title,
            "year": year,
            "vote_average": meta.get("vote_average"),
            "poster_path": meta.get("poster_path") or None,
            "trailer_key": movie_trailers.get(title),
            "release_date": meta.get("release_date") or None,
            "type": "movie",
        })
    sb_insert(args.url, args.key, "movies", movie_rows, upsert=True)
    print(f"  Upserted {len(movie_rows)} movies")

    print("\n[2/4] Upserting TV shows...")
    tv_rows = []
    for title, meta in tv_items.items():
        year = None
        if meta.get("release_date") and len(meta["release_date"]) >= 4:
            try:
                year = int(meta["release_date"][:4])
            except ValueError:
                pass
        tv_rows.append({
            "title": title,
            "year": year,
            "vote_average": meta.get("vote_average"),
            "poster_path": meta.get("poster_path") or None,
            "trailer_key": tv_trailers.get(title),
            "release_date": meta.get("release_date") or None,
            "type": "tv",
        })
    sb_insert(args.url, args.key, "movies", tv_rows, upsert=True)
    print(f"  Upserted {len(tv_rows)} TV shows")

    print("\n[3/4] Fetching movie ID map...")
    headers = {
        "apikey": args.key,
        "Authorization": f"Bearer {args.key}",
    }
    title_to_id = {}
    offset = 0
    while True:
        resp = requests.get(
            f"{args.url}/rest/v1/movies?select=id,title&offset={offset}&limit=1000",
            headers=headers, timeout=30,
        )
        resp.raise_for_status()
        rows = resp.json()
        if not rows:
            break
        for row in rows:
            title_to_id[row["title"]] = row["id"]
        offset += len(rows)
    print(f"  Fetched {len(title_to_id)} movie IDs")

    print("\n[4/4] Upserting recommendations...")
    rec_rows = []
    skipped = 0
    for source_title, recs in recommendations.items():
        source_id = title_to_id.get(source_title)
        if not source_id:
            skipped += 1
            continue
        for rank, rec in enumerate(recs, 1):
            rec_id = title_to_id.get(rec["title"])
            if not rec_id:
                skipped += 1
                continue
            rec_rows.append({
                "movie_id": source_id,
                "rec_movie_id": rec_id,
                "rank": rank,
            })
    print(f"  {len(rec_rows)} recommendation pairs ({skipped} skipped)")
    sb_insert(args.url, args.key, "recommendations", rec_rows, batch_size=1000, upsert=True)

    print(f"\nDone! {len(movie_rows)} movies + {len(tv_rows)} TV shows + {len(rec_rows)} recommendations migrated.")


if __name__ == "__main__":
    main()
