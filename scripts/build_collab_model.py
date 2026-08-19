#!/usr/bin/env python3
"""
Pre-compute collaborative filtering item factors from MovieLens 25M.

Reads ml-25m/{ratings,links}.csv, cross-references with TMDb 5000 movie IDs,
runs truncated SVD on the user-item rating matrix, and writes item_factors.csv
to the dataset/ directory for use by all language implementations.

Usage:
    python scripts/build_collab_model.py
    python scripts/build_collab_model.py --ml-dir dataset/ml-25m --out dataset/item_factors.csv --k 50
"""

import argparse
import csv
import json
import os
import sys
import time

import numpy as np
from scipy.sparse import csr_matrix
from scipy.sparse.linalg import svds


def load_links(ml_dir):
    """Load MovieLens links.csv → {movieId: tmdbId}."""
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
    """Load movie IDs from a movies CSV."""
    ids = set()
    with open(movies_path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            ids.add(int(row["id"]))
    return ids


def load_ratings(ml_dir, ml_to_tmdb, tmdb_ids=None):
    """Load ratings for movies that have TMDb IDs (optionally filtered to a set)."""
    path = os.path.join(ml_dir, "ratings.csv")

    if tmdb_ids is not None:
        valid_ml_ids = {ml_id for ml_id, tmdb_id in ml_to_tmdb.items() if tmdb_id in tmdb_ids}
    else:
        valid_ml_ids = set(ml_to_tmdb.keys())

    user_ids = {}
    item_ids = {}
    rows_u, rows_i, rows_v = [], [], []

    t0 = time.time()
    count = 0
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            ml_id = int(row["movieId"])
            if ml_id not in valid_ml_ids:
                continue

            uid = int(row["userId"])
            rating = float(row["rating"])

            if uid not in user_ids:
                user_ids[uid] = len(user_ids)
            if ml_id not in item_ids:
                item_ids[ml_id] = len(item_ids)

            rows_u.append(user_ids[uid])
            rows_i.append(item_ids[ml_id])
            rows_v.append(rating)
            count += 1

            if count % 5_000_000 == 0:
                elapsed = time.time() - t0
                print(f"  ...loaded {count:,} ratings in {elapsed:.1f}s")

    elapsed = time.time() - t0
    print(f"  Loaded {count:,} ratings ({len(user_ids):,} users, {len(item_ids):,} items) in {elapsed:.1f}s")

    # Build ml_id → item_idx mapping
    ml_id_to_idx = item_ids
    idx_to_ml_id = {v: k for k, v in ml_id_to_idx.items()}

    return rows_u, rows_i, rows_v, len(user_ids), len(item_ids), idx_to_ml_id


def build_factors(rows_u, rows_i, rows_v, n_users, n_items, k):
    """Build user-item matrix and compute truncated SVD."""
    print(f"Building sparse matrix ({n_users:,} × {n_items:,})...")
    R = csr_matrix((rows_v, (rows_u, rows_i)), shape=(n_users, n_items))

    # Mean-center per item (subtract column means)
    col_means = np.array(R.sum(axis=0)).flatten()
    col_counts = np.array((R > 0).sum(axis=0)).flatten()
    col_counts[col_counts == 0] = 1
    col_means = col_means / col_counts

    # For SVD we work with the raw sparse matrix (mean-centering a sparse
    # matrix would make it dense; SVD on the raw ratings works well enough
    # for item-item similarity since we only use the V factor)
    print(f"Running truncated SVD with k={k}...")
    t0 = time.time()
    U, sigma, Vt = svds(R.astype(np.float32), k=k)
    elapsed = time.time() - t0
    print(f"  SVD completed in {elapsed:.1f}s")

    # Item factors = Vt.T @ diag(sigma) → each row is an item's latent vector
    item_factors = Vt.T * sigma[np.newaxis, :]

    # L2-normalize each row so cosine similarity = dot product
    norms = np.linalg.norm(item_factors, axis=1, keepdims=True)
    norms[norms == 0] = 1
    item_factors = item_factors / norms

    return item_factors


def export_factors(item_factors, idx_to_ml_id, ml_to_tmdb, output_path, k):
    """Write item_factors.csv: tmdb_id, f0, f1, ..., f(k-1)."""
    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)

    header = ["tmdb_id"] + [f"f{i}" for i in range(k)]
    written = 0

    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(header)

        for idx in range(item_factors.shape[0]):
            ml_id = idx_to_ml_id.get(idx)
            if ml_id is None:
                continue
            tmdb_id = ml_to_tmdb.get(ml_id)
            if tmdb_id is None:
                continue

            row = [tmdb_id] + [f"{v:.6f}" for v in item_factors[idx]]
            writer.writerow(row)
            written += 1

    print(f"Wrote {written} item factor rows to {output_path}")
    file_size = os.path.getsize(output_path)
    print(f"File size: {file_size / 1024:.0f} KB")


def main():
    parser = argparse.ArgumentParser(description="Build collaborative filtering model from MovieLens 25M")
    parser.add_argument("--ml-dir", default="dataset/ml-25m", help="Path to extracted MovieLens 25M directory")
    parser.add_argument("--movies-csv", default=None, help="Path to movies CSV (omit to include all MovieLens movies)")
    parser.add_argument("--out", default="dataset/item_factors.csv", help="Output path for item factors")
    parser.add_argument("--k", type=int, default=50, help="Number of latent factors for SVD")
    args = parser.parse_args()

    if not os.path.exists(args.ml_dir):
        print(f"Error: MovieLens directory not found at '{args.ml_dir}'")
        print("Download from: https://grouplens.org/datasets/movielens/25m/")
        sys.exit(1)

    print("=== Building Collaborative Filtering Model ===\n")

    tmdb_ids = None
    if args.movies_csv:
        if not os.path.exists(args.movies_csv):
            print(f"Error: Movies CSV not found at '{args.movies_csv}'")
            sys.exit(1)
        print("Step 1: Loading movie IDs from CSV filter...")
        tmdb_ids = load_tmdb_ids(args.movies_csv)
        print(f"  {len(tmdb_ids)} movies loaded\n")
    else:
        print("Step 1: No movie filter — including all MovieLens movies\n")

    print("Step 2: Loading MovieLens → TMDb ID mapping...")
    ml_to_tmdb = load_links(args.ml_dir)
    if tmdb_ids:
        overlap = sum(1 for tmdb_id in ml_to_tmdb.values() if tmdb_id in tmdb_ids)
        print(f"  {len(ml_to_tmdb)} MovieLens movies mapped, {overlap} overlap with filter\n")
    else:
        print(f"  {len(ml_to_tmdb)} MovieLens movies mapped\n")

    print("Step 3: Loading ratings...")
    rows_u, rows_i, rows_v, n_users, n_items, idx_to_ml_id = load_ratings(
        args.ml_dir, ml_to_tmdb, tmdb_ids
    )
    print()

    print(f"Step 4: Computing SVD (k={args.k})...")
    item_factors = build_factors(rows_u, rows_i, rows_v, n_users, n_items, args.k)
    print()

    print("Step 5: Exporting item factors...")
    export_factors(item_factors, idx_to_ml_id, ml_to_tmdb, args.out, args.k)
    print("\nDone!")


if __name__ == "__main__":
    main()
