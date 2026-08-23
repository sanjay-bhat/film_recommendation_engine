#!/usr/bin/env python3
"""
Backfill null poster_path values by fetching from TMDb API.

Usage:
    python scripts/backfill_posters.py \
        --tmdb-key YOUR_TMDB_API_KEY \
        --supabase-url https://xxxxx.supabase.co \
        --supabase-key YOUR_SERVICE_ROLE_KEY
"""

import argparse
import time

import requests


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tmdb-key", required=True)
    parser.add_argument("--supabase-url", required=True)
    parser.add_argument("--supabase-key", required=True)
    args = parser.parse_args()

    sb_headers = {
        "apikey": args.supabase_key,
        "Authorization": f"Bearer {args.supabase_key}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal",
    }

    print("[1/3] Fetching titles with null poster_path...")
    rows = []
    offset = 0
    while True:
        resp = requests.get(
            f"{args.supabase_url}/rest/v1/movies?poster_path=is.null&select=id,title,tmdb_id,type&offset={offset}&limit=1000",
            headers={"apikey": args.supabase_key, "Authorization": f"Bearer {args.supabase_key}"},
            timeout=30,
        )
        resp.raise_for_status()
        batch = resp.json()
        if not batch:
            break
        rows.extend(batch)
        offset += len(batch)
    print(f"  {len(rows)} titles need posters")

    print("\n[2/3] Fetching poster paths from TMDb...")
    session = requests.Session()
    updated = 0
    skipped = 0
    failed = 0

    for i, row in enumerate(rows):
        tmdb_id = row.get("tmdb_id")
        if not tmdb_id:
            skipped += 1
            continue

        media_type = "tv" if row.get("type") == "tv" else "movie"
        try:
            resp = session.get(
                f"https://api.themoviedb.org/3/{media_type}/{tmdb_id}?api_key={args.tmdb_key}",
                timeout=10,
            )
            if resp.status_code == 429:
                time.sleep(2)
                resp = session.get(
                    f"https://api.themoviedb.org/3/{media_type}/{tmdb_id}?api_key={args.tmdb_key}",
                    timeout=10,
                )
            if resp.status_code != 200:
                skipped += 1
                continue

            poster = resp.json().get("poster_path")
            if not poster:
                skipped += 1
                continue

            patch = session.patch(
                f"{args.supabase_url}/rest/v1/movies?id=eq.{row['id']}",
                json={"poster_path": poster},
                headers=sb_headers,
                timeout=10,
            )
            if patch.status_code in (200, 204):
                updated += 1
            else:
                failed += 1

        except requests.RequestException:
            failed += 1

        if (i + 1) % 50 == 0:
            print(f"  {i + 1}/{len(rows)} processed ({updated} updated)")
            time.sleep(0.3)

    print(f"\n[3/3] Done: {updated} posters backfilled, {skipped} skipped (no TMDb poster), {failed} failed")


if __name__ == "__main__":
    main()
