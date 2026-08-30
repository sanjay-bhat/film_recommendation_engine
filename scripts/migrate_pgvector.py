#!/usr/bin/env python3
"""
Generate sentence-transformer embeddings and upload to Supabase pgvector.

Prerequisites:
  1. Run setup_pgvector.sql in the Supabase SQL Editor first
  2. pip install sentence-transformers requests

Usage:
    python scripts/migrate_pgvector.py \
        --url https://xxxxx.supabase.co \
        --key YOUR_SERVICE_ROLE_KEY
"""

import argparse
import csv
import json
import sys
import time

import numpy as np
import requests


def load_items(movies_csv, tv_csv):
    items = []

    with open(movies_csv, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            title = row.get("title", "").strip()
            if not title:
                continue
            genres, keywords, cast = [], [], []
            for field, target in [("genres", genres), ("keywords", keywords), ("cast", cast)]:
                try:
                    raw = json.loads(row.get(field, "[]"))
                    for x in (raw if isinstance(raw, list) else []):
                        if isinstance(x, dict):
                            target.append(x.get("name", ""))
                        elif isinstance(x, str):
                            target.append(x)
                except (json.JSONDecodeError, TypeError):
                    pass
            items.append({"title": title, "overview": row.get("overview", ""),
                          "genres": genres, "keywords": keywords[:15], "cast": cast[:10]})

    try:
        with open(tv_csv, encoding="utf-8") as f:
            for row in csv.DictReader(f):
                title = row.get("title", "").strip()
                if not title:
                    continue
                genres = json.loads(row.get("genres", "[]")) if row.get("genres") else []
                keywords = json.loads(row.get("keywords", "[]")) if row.get("keywords") else []
                cast = json.loads(row.get("cast", "[]")) if row.get("cast") else []
                items.append({"title": title, "overview": row.get("overview", ""),
                              "genres": genres, "keywords": keywords[:15], "cast": cast[:10]})
    except FileNotFoundError:
        print("  No TV shows CSV found, movies only")

    seen = set()
    unique = []
    for item in items:
        if item["title"] not in seen:
            seen.add(item["title"])
            unique.append(item)
    return unique


def build_text(item):
    parts = []
    if item["overview"]:
        parts.append(item["overview"])
    if item["genres"]:
        parts.append("Genres: " + ", ".join(item["genres"]))
    if item["cast"]:
        parts.append("Cast: " + ", ".join(item["cast"][:5]))
    if item["keywords"]:
        parts.append("Keywords: " + ", ".join(item["keywords"][:10]))
    return " ".join(parts)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--key", required=True)
    parser.add_argument("--movies-csv", default="dataset/movies_bulk.csv")
    parser.add_argument("--tv-csv", default="dataset/tv_bulk.csv")
    parser.add_argument("--batch-size", type=int, default=128)
    args = parser.parse_args()

    print("[1/4] Loading datasets...")
    items = load_items(args.movies_csv, args.tv_csv)
    print(f"  {len(items)} unique titles")

    print("\n[2/4] Computing embeddings with all-MiniLM-L6-v2...")
    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer("all-MiniLM-L6-v2")
    texts = [build_text(item) for item in items]

    t0 = time.time()
    embeddings = model.encode(texts, batch_size=args.batch_size,
                              show_progress_bar=True, normalize_embeddings=True)
    print(f"  {embeddings.shape[0]} embeddings in {time.time() - t0:.1f}s")

    title_to_vec = {items[i]["title"]: embeddings[i] for i in range(len(items))}

    print("\n[3/4] Fetching movie IDs from Supabase...")
    headers = {"apikey": args.key, "Authorization": f"Bearer {args.key}"}
    title_to_id = {}
    offset = 0
    while True:
        resp = requests.get(
            f"{args.url}/rest/v1/movies?select=id,title&offset={offset}&limit=1000",
            headers=headers, timeout=30)
        resp.raise_for_status()
        rows = resp.json()
        if not rows:
            break
        for row in rows:
            title_to_id[row["title"]] = row["id"]
        offset += len(rows)
    print(f"  {len(title_to_id)} movies in database")

    print("\n[4/4] Uploading embeddings to Supabase...")
    headers_patch = {
        "apikey": args.key,
        "Authorization": f"Bearer {args.key}",
        "Content-Type": "application/json",
        "Prefer": "return=minimal",
    }

    session = requests.Session()
    session.headers.update(headers_patch)

    matched = 0
    skipped = 0

    for title, db_id in title_to_id.items():
        vec = title_to_vec.get(title)
        if vec is None:
            skipped += 1
            continue
        vec_f16 = vec.astype(np.float16)
        vec_str = "[" + ",".join(f"{x:.4f}" for x in vec_f16.tolist()) + "]"
        resp = session.patch(
            f"{args.url}/rest/v1/movies?id=eq.{db_id}",
            json={"embedding": vec_str}, timeout=30)
        if resp.status_code not in (200, 204):
            print(f"  ERROR on {title}: {resp.status_code} {resp.text[:200]}")
            sys.exit(1)
        matched += 1
        if matched % 500 == 0:
            print(f"  {matched}/{len(title_to_id)} uploaded...")

    print(f"  {matched} embeddings uploaded, {skipped} titles not found in CSVs")

    print("\n--- Next steps ---")
    print("1. Run this in the Supabase SQL Editor to build the HNSW index:")
    print("   CREATE INDEX movies_embedding_hnsw ON movies USING hnsw (embedding halfvec_cosine_ops);")
    print("2. Test: SELECT title FROM movies ORDER BY embedding <=> (SELECT embedding FROM movies WHERE title = 'Inception') LIMIT 8;")
    print("3. Once verified, drop the old table: DROP TABLE IF EXISTS recommendations;")


if __name__ == "__main__":
    main()
