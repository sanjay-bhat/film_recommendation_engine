#!/usr/bin/env python3
"""
Pre-compute genome-based content vectors from MovieLens 25M genome scores.

Loads 1128-dimensional tag relevance vectors, reduces to 50 dimensions via
truncated SVD, L2-normalizes so cosine similarity = dot product. Only movies
overlapping with TMDb 5000 are exported.

Usage:
    python scripts/build_genome_factors.py
"""

import argparse
import csv
import math
import os
import sys
import time

import numpy as np
from scipy.sparse.linalg import svds


def load_links(ml_dir):
    path = os.path.join(ml_dir, "links.csv")
    ml_to_tmdb = {}
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            ml_id = int(row["movieId"])
            tmdb_str = row.get("tmdbId", "").strip()
            if tmdb_str:
                ml_to_tmdb[ml_id] = int(tmdb_str)
    return ml_to_tmdb


def load_tmdb_ids(movies_path):
    ids = set()
    with open(movies_path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            ids.add(int(row["id"]))
    return ids


def load_genome_scores(ml_dir, ml_to_tmdb, tmdb_ids):
    """Load genome scores for TMDb 5000 overlap movies."""
    path = os.path.join(ml_dir, "genome-scores.csv")
    valid_ml_ids = {ml_id for ml_id, tmdb_id in ml_to_tmdb.items()
                    if tmdb_id in tmdb_ids}

    vectors = {}
    t0 = time.time()
    count = 0

    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            ml_id = int(row["movieId"])
            if ml_id not in valid_ml_ids:
                continue
            tag_id = int(row["tagId"])
            relevance = float(row["relevance"])

            if ml_id not in vectors:
                vectors[ml_id] = {}
            vectors[ml_id][tag_id] = relevance
            count += 1

            if count % 2_000_000 == 0:
                elapsed = time.time() - t0
                print(f"  ...loaded {count:,} scores in {elapsed:.1f}s")

    elapsed = time.time() - t0
    print(f"  Loaded {count:,} genome scores for {len(vectors)} movies in {elapsed:.1f}s")
    return vectors


def load_tag_count(ml_dir):
    path = os.path.join(ml_dir, "genome-tags.csv")
    count = 0
    with open(path, encoding="utf-8") as f:
        for _ in csv.DictReader(f):
            count += 1
    return count


def reduce_and_export(vectors, ml_to_tmdb, n_tags, output_path, k=50):
    """Reduce 1128-dim genome vectors to k dims via SVD, L2-normalize, export."""
    ml_ids = sorted(vectors.keys())
    n_movies = len(ml_ids)
    ml_id_to_row = {ml_id: i for i, ml_id in enumerate(ml_ids)}

    print(f"  Building {n_movies} × {n_tags} matrix...")
    matrix = np.zeros((n_movies, n_tags), dtype=np.float32)
    for ml_id, tag_scores in vectors.items():
        row = ml_id_to_row[ml_id]
        for tag_id, relevance in tag_scores.items():
            matrix[row, tag_id - 1] = relevance

    print(f"  Running truncated SVD with k={k}...")
    t0 = time.time()
    U, sigma, Vt = svds(matrix, k=k)
    elapsed = time.time() - t0
    print(f"  SVD completed in {elapsed:.1f}s")

    # Reduced factors: U @ diag(sigma) gives k-dimensional representation
    reduced = U * sigma[np.newaxis, :]

    # L2-normalize
    norms = np.linalg.norm(reduced, axis=1, keepdims=True)
    norms[norms == 0] = 1
    reduced = reduced / norms

    header = ["tmdb_id"] + [f"g{i}" for i in range(k)]
    written = 0

    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(header)

        for ml_id in ml_ids:
            tmdb_id = ml_to_tmdb.get(ml_id)
            if tmdb_id is None:
                continue

            row_idx = ml_id_to_row[ml_id]
            row = [tmdb_id] + [f"{v:.6f}" for v in reduced[row_idx]]
            writer.writerow(row)
            written += 1

    print(f"  Wrote {written} genome factor rows to {output_path}")
    file_size = os.path.getsize(output_path)
    print(f"  File size: {file_size / 1024:.0f} KB")


def main():
    parser = argparse.ArgumentParser(
        description="Build genome content vectors from MovieLens 25M")
    parser.add_argument("--ml-dir", default="dataset/ml-25m")
    parser.add_argument("--movies-csv", default="dataset/tmdb_5000_movies.csv")
    parser.add_argument("--out", default="dataset/genome_factors.csv")
    parser.add_argument("--k", type=int, default=50,
                        help="Number of reduced dimensions")
    args = parser.parse_args()

    if not os.path.exists(args.ml_dir):
        print(f"Error: MovieLens directory not found at '{args.ml_dir}'")
        sys.exit(1)

    print("=== Building Genome Content Vectors ===\n")

    print("Step 1: Loading TMDb 5000 movie IDs...")
    tmdb_ids = load_tmdb_ids(args.movies_csv)
    print(f"  {len(tmdb_ids)} TMDb movies loaded\n")

    print("Step 2: Loading MovieLens → TMDb ID mapping...")
    ml_to_tmdb = load_links(args.ml_dir)
    overlap = sum(1 for t in ml_to_tmdb.values() if t in tmdb_ids)
    print(f"  {overlap} movies overlap with TMDb 5000\n")

    print("Step 3: Loading genome tag count...")
    n_tags = load_tag_count(args.ml_dir)
    print(f"  {n_tags} genome tags\n")

    print("Step 4: Loading genome scores...")
    vectors = load_genome_scores(args.ml_dir, ml_to_tmdb, tmdb_ids)
    print()

    print(f"Step 5: Reducing to {args.k} dimensions and exporting...")
    reduce_and_export(vectors, ml_to_tmdb, n_tags, args.out, k=args.k)
    print("\nDone!")


if __name__ == "__main__":
    main()
