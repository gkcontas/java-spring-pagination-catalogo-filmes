-- The keyset index. Column order and direction must match the ORDER BY of the
-- keyset query exactly, otherwise Postgres cannot walk the index and the whole
-- point of keyset pagination is lost.
CREATE INDEX idx_movies_keyset ON movies (release_date DESC, id DESC);

CREATE INDEX idx_movies_genre ON movies (genre_id);
CREATE INDEX idx_movies_rating ON movies (rating);
CREATE INDEX idx_reviews_movie ON reviews (movie_id);

-- A plain B-tree index is useless for LIKE '%term%' because of the leading
-- wildcard: Postgres cannot seek into the tree without a known prefix. A trigram
-- GIN index is what actually makes that search indexable.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_movies_title_trgm ON movies USING GIN (LOWER(title) gin_trgm_ops);

-- Without fresh statistics the planner may still pick a sequential scan over the
-- brand new indexes, which would make the benchmark measure the wrong thing.
ANALYZE movies;
ANALYZE reviews;
