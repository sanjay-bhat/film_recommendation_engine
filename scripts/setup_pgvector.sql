-- Run this in the Supabase SQL Editor (https://supabase.com/dashboard → SQL Editor)
-- Step 1: Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Step 2: Add embedding column to movies table
ALTER TABLE movies ADD COLUMN IF NOT EXISTS embedding vector(384);

-- Step 3: Create HNSW index for fast cosine similarity search
-- (Run AFTER embeddings are uploaded — index builds on existing data)
-- CREATE INDEX movies_embedding_hnsw ON movies USING hnsw (embedding vector_cosine_ops);

-- Step 4: Create RPC function for query-time recommendations
CREATE OR REPLACE FUNCTION get_recommendations(query_title text, num_recs int DEFAULT 7)
RETURNS TABLE (title text, year int, vote_average double precision, poster_path text, trailer_key text, type text)
LANGUAGE sql STABLE
AS $$
  SELECT m.title, m.year, m.vote_average, m.poster_path, m.trailer_key, m.type
  FROM movies m
  WHERE m.title != query_title
    AND m.embedding IS NOT NULL
  ORDER BY m.embedding <=> (SELECT embedding FROM movies WHERE title = query_title LIMIT 1)
  LIMIT num_recs;
$$;

-- Grant access to the anon role (PostgREST uses this for client-side queries)
GRANT EXECUTE ON FUNCTION get_recommendations TO anon;

-- Step 5 (after verifying everything works): Drop recommendations table
-- DROP TABLE IF EXISTS recommendations;
