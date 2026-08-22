#!/usr/bin/env python3
"""
Migrate static JSON data to Supabase Postgres.

Loads movies, posters, trailers, and recommendations into the Supabase
database via REST API. Requires the service_role key (bypasses RLS).

Usage:
    python scripts/migrate_to_supabase.py \
        --url https://xxxxx.supabase.co \
        --key YOUR_SERVICE_ROLE_KEY

Run the schema SQL in the Supabase SQL Editor first.
"""

import argparse
import json
import sys

import pandas as pd
import requests


def supabase_insert(url, key, table, rows, batch_size=500):
    endpoint = f"{url}/rest/v1/{table}"
    headers = {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Prefer": "return=representation",
    }
    inserted = 0
    for i in range(0, len(rows), batch_size):
        batch = rows[i : i + batch_size]
        resp = requests.post(endpoint, headers=headers, json=batch, timeout=60)
        if resp.status_code not in (200, 201):
            print(f"  ERROR inserting {table} batch {i}: {resp.status_code}")
            print(f"  {resp.text[:500]}")
            sys.exit(1)
        inserted += len(batch)
        print(f"  {table}: {inserted}/{len(rows)}")
    return inserted


def main():
    parser = argparse.ArgumentParser(description="Migrate data to Supabase")
    parser.add_argument("--url", required=True, help="Supabase project URL")
    parser.add_argument("--key", required=True, help="Supabase service_role key")
    args = parser.parse_args()

    df = pd.read_csv(
        "dataset/tmdb_5000_movies.csv",
        usecols=["id", "title", "release_date", "vote_average"],
    )
    df["year"] = pd.to_datetime(df["release_date"], errors="coerce").dt.year
    tmdb_lookup = dict(zip(df["title"], df["id"]))
    year_lookup = dict(zip(df["title"], df["year"]))
    vote_lookup = dict(zip(df["title"], df["vote_average"]))

    with open("docs/demo_db.json") as f:
        db = json.load(f)
    with open("docs/posters.json") as f:
        posters = json.load(f)

    trailers = {}
    try:
        with open("docs/trailers.json") as f:
            trailers = json.load(f)
        print(f"Loaded {len(trailers)} trailers")
    except FileNotFoundError:
        print("No trailers.json found — skipping trailer data")

    all_titles = set(db.keys())
    for recs in db.values():
        for r in recs:
            all_titles.add(r["title"])

    print(f"\n[1/3] Inserting {len(all_titles)} movies...")
    movie_rows = []
    for title in sorted(all_titles):
        movie_rows.append({
            "title": title,
            "year": int(year_lookup[title]) if title in year_lookup and pd.notna(year_lookup.get(title)) else None,
            "tmdb_id": int(tmdb_lookup[title]) if title in tmdb_lookup else None,
            "vote_average": float(vote_lookup[title]) if title in vote_lookup and pd.notna(vote_lookup.get(title)) else None,
            "poster_path": posters.get(title),
            "trailer_key": trailers.get(title),
        })
    supabase_insert(args.url, args.key, "movies", movie_rows)

    print("\n[2/3] Fetching movie ID map from Supabase...")
    headers = {
        "apikey": args.key,
        "Authorization": f"Bearer {args.key}",
    }
    title_to_id = {}
    offset = 0
    while True:
        resp = requests.get(
            f"{args.url}/rest/v1/movies?select=id,title&offset={offset}&limit=1000",
            headers=headers,
            timeout=30,
        )
        resp.raise_for_status()
        rows = resp.json()
        if not rows:
            break
        for row in rows:
            title_to_id[row["title"]] = row["id"]
        offset += len(rows)
    print(f"  Fetched {len(title_to_id)} movie IDs")

    print(f"\n[3/3] Inserting recommendations...")
    rec_rows = []
    skipped = 0
    for source_title, recs in db.items():
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
    supabase_insert(args.url, args.key, "recommendations", rec_rows, batch_size=1000)

    print(f"\nDone! {len(movie_rows)} movies and {len(rec_rows)} recommendations migrated.")


if __name__ == "__main__":
    main()
