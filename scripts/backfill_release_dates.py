#!/usr/bin/env python3
"""
Backfill release_date column in Supabase movies table.

Reads release dates from the TMDb CSV and patches them into Supabase.
Requires the service_role key.

Usage:
    python scripts/backfill_release_dates.py \
        --url https://xxxxx.supabase.co \
        --key YOUR_SERVICE_ROLE_KEY
"""

import argparse
import csv

import requests


def main():
    parser = argparse.ArgumentParser(description="Backfill release dates")
    parser.add_argument("--url", required=True, help="Supabase project URL")
    parser.add_argument("--key", required=True, help="Supabase service_role key")
    args = parser.parse_args()

    headers = {
        "apikey": args.key,
        "Authorization": f"Bearer {args.key}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal",
    }

    dates = {}
    with open("dataset/tmdb_5000_movies.csv") as f:
        for row in csv.DictReader(f):
            rd = row.get("release_date", "").strip()
            if rd and row["title"]:
                dates[row["title"]] = rd

    print(f"Loaded {len(dates)} release dates from CSV")

    updated = 0
    errors = 0
    for title, release_date in dates.items():
        resp = requests.patch(
            f"{args.url}/rest/v1/movies?title=eq.{requests.utils.quote(title)}",
            headers=headers,
            json={"release_date": release_date},
            timeout=30,
        )
        if resp.status_code in (200, 204):
            updated += 1
        else:
            errors += 1
            if errors <= 5:
                print(f"  ERROR {title}: {resp.status_code} {resp.text[:200]}")

        if updated % 500 == 0 and updated > 0:
            print(f"  Updated {updated}/{len(dates)}")

    print(f"\nDone. Updated {updated}, errors {errors}")


if __name__ == "__main__":
    main()
