#!/usr/bin/env python3
"""
Pre-compute sentence embeddings from movie plot overviews.

Uses all-MiniLM-L6-v2 (384-dim) → PCA to 50 dims → L2-normalized.
Only movies from TMDb 5000 with non-empty overviews are exported.

Usage:
    python scripts/build_plot_embeddings.py
"""

import argparse
import csv
import os
import sys
import time

import numpy as np
from sentence_transformers import SentenceTransformer


def load_overviews(movies_path):
    """Load TMDb 5000 movie IDs and plot overviews."""
    entries = []
    with open(movies_path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            tmdb_id = int(row["id"])
            overview = row.get("overview", "").strip()
            if overview:
                entries.append((tmdb_id, overview))
    return entries


def reduce_dimensions(embeddings, k=50):
    """Reduce embedding dimensions via PCA."""
    mean = embeddings.mean(axis=0)
    centered = embeddings - mean
    cov = np.cov(centered, rowvar=False)
    eigenvalues, eigenvectors = np.linalg.eigh(cov)
    # Take top-k eigenvectors (sorted descending)
    idx = np.argsort(eigenvalues)[::-1][:k]
    components = eigenvectors[:, idx]
    reduced = centered @ components
    return reduced


def export_factors(tmdb_ids, reduced, output_path, k):
    """Write plot_factors.csv: tmdb_id, p0, p1, ..., p(k-1), L2-normalized."""
    # L2-normalize
    norms = np.linalg.norm(reduced, axis=1, keepdims=True)
    norms[norms == 0] = 1
    reduced = reduced / norms

    header = ["tmdb_id"] + [f"p{i}" for i in range(k)]
    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(header)
        for i, tmdb_id in enumerate(tmdb_ids):
            row = [tmdb_id] + [f"{v:.6f}" for v in reduced[i]]
            writer.writerow(row)

    file_size = os.path.getsize(output_path)
    print(f"  Wrote {len(tmdb_ids)} plot factor rows to {output_path}")
    print(f"  File size: {file_size / 1024:.0f} KB")


def main():
    parser = argparse.ArgumentParser(
        description="Build plot embedding vectors from TMDb 5000 overviews")
    parser.add_argument("--movies-csv", default="dataset/tmdb_5000_movies.csv")
    parser.add_argument("--out", default="dataset/plot_factors.csv")
    parser.add_argument("--k", type=int, default=50,
                        help="Number of reduced dimensions")
    parser.add_argument("--model", default="all-MiniLM-L6-v2",
                        help="Sentence transformer model name")
    args = parser.parse_args()

    if not os.path.exists(args.movies_csv):
        print(f"Error: Movies CSV not found at '{args.movies_csv}'")
        sys.exit(1)

    print("=== Building Plot Embedding Vectors ===\n")

    print("Step 1: Loading movie overviews...")
    entries = load_overviews(args.movies_csv)
    tmdb_ids = [e[0] for e in entries]
    overviews = [e[1] for e in entries]
    print(f"  {len(entries)} movies with overviews\n")

    print(f"Step 2: Encoding with {args.model}...")
    t0 = time.time()
    model = SentenceTransformer(args.model)
    embeddings = model.encode(overviews, show_progress_bar=True,
                              batch_size=128)
    embeddings = np.array(embeddings)
    elapsed = time.time() - t0
    print(f"  Encoded {embeddings.shape[0]} overviews → {embeddings.shape[1]}-dim vectors in {elapsed:.1f}s\n")

    print(f"Step 3: Reducing to {args.k} dimensions via PCA...")
    reduced = reduce_dimensions(embeddings, k=args.k)
    print(f"  Reduced: {embeddings.shape[1]} → {reduced.shape[1]} dims\n")

    print("Step 4: Exporting plot factors...")
    export_factors(tmdb_ids, reduced, args.out, args.k)
    print("\nDone!")


if __name__ == "__main__":
    main()
