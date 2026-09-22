-- Seeding happens BEFORE the indexes are created (V3) on purpose: building the
-- indexes once over the finished table is much faster than maintaining them
-- across 200k individual inserts.

INSERT INTO genres (name) VALUES
    ('Action'), ('Adventure'), ('Animation'), ('Comedy'),
    ('Crime'), ('Documentary'), ('Drama'), ('Fantasy'),
    ('Horror'), ('Mystery'), ('Romance'), ('Science Fiction');

-- 200k movies spread over 20k distinct release dates, which means ~10 movies share
-- each date. Those ties are what makes a single-column cursor unsafe and force the
-- keyset pagination to use a composite (release_date, id) key.
INSERT INTO movies (title, release_date, rating, genre_id, created_at)
SELECT
    (ARRAY['The', 'A', 'Last', 'Silent', 'Broken',
           'Eternal', 'Hidden', 'Crimson', 'Northern', 'Final'])[1 + (s % 10)]
        || ' ' ||
    (ARRAY['Echo', 'Horizon', 'Divide', 'Protocol', 'Garden',
           'Machine', 'Harbor', 'Signal', 'Kingdom', 'Mirror'])[1 + ((s / 10) % 10)]
        || ' #' || s,
    DATE '1970-01-01' + (s % 20000),
    ROUND((1 + (s % 900) / 100.0)::NUMERIC, 2),
    1 + (s % 12),
    NOW() - ((s % 365) * INTERVAL '1 day')
FROM generate_series(1, 200000) AS s;

-- Only the first 2000 movies get reviews: the trap endpoint needs a one-to-many
-- association, but there is no reason to pay for 600k rows to demonstrate it.
INSERT INTO reviews (movie_id, author, score, body)
SELECT
    m.id,
    'reviewer_' || r,
    1 + ((m.id + r) % 10),
    'Review ' || r || ' for movie ' || m.id
FROM movies m
CROSS JOIN generate_series(1, 3) AS r
WHERE m.id <= 2000;
