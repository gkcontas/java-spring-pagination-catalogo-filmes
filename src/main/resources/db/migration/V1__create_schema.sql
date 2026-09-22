CREATE TABLE genres (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(60) NOT NULL UNIQUE
);

CREATE TABLE movies (
    id           BIGSERIAL    PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    release_date DATE         NOT NULL,
    rating       NUMERIC(3, 2) NOT NULL,
    genre_id     BIGINT       NOT NULL REFERENCES genres (id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Reviews exist only to demonstrate the collection JOIN FETCH pagination trap.
-- They are deliberately kept out of the main listing queries.
CREATE TABLE reviews (
    id       BIGSERIAL    PRIMARY KEY,
    movie_id BIGINT       NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    author   VARCHAR(120) NOT NULL,
    score    SMALLINT     NOT NULL,
    body     VARCHAR(500)
);
