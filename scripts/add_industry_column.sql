-- Run this in the Supabase SQL Editor (https://supabase.com/dashboard → SQL Editor)

-- Step 1: Add original_language column
ALTER TABLE movies ADD COLUMN IF NOT EXISTS original_language text;

-- Step 2: Update get_recommendations to return more results with language
CREATE OR REPLACE FUNCTION get_recommendations(query_title text, num_recs int DEFAULT 20)
RETURNS TABLE (title text, year int, vote_average double precision, poster_path text, trailer_key text, type text, original_language text)
LANGUAGE sql STABLE
AS $$
  SELECT m.title, m.year, m.vote_average, m.poster_path, m.trailer_key, m.type, m.original_language
  FROM movies m
  WHERE m.title != query_title
    AND m.embedding IS NOT NULL
  ORDER BY m.embedding <=> (SELECT embedding FROM movies WHERE title = query_title LIMIT 1)
  LIMIT num_recs;
$$;

GRANT EXECUTE ON FUNCTION get_recommendations TO anon;
