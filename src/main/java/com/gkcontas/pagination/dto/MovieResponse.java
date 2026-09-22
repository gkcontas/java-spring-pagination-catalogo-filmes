package com.gkcontas.pagination.dto;

import com.gkcontas.pagination.model.Movie;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MovieResponse(
        Long id,
        String title,
        LocalDate releaseDate,
        BigDecimal rating,
        String genre) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getReleaseDate(),
                movie.getRating(),
                movie.getGenre().getName());
    }
}
