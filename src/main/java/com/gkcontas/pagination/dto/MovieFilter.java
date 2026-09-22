package com.gkcontas.pagination.dto;

import java.math.BigDecimal;

/**
 * Filters shared by every pagination strategy. Keeping them identical across the three
 * endpoints is what makes the comparison between the strategies meaningful.
 */
public record MovieFilter(
        String title,
        String genre,
        Integer yearFrom,
        Integer yearTo,
        BigDecimal minRating) {

    public static MovieFilter empty() {
        return new MovieFilter(null, null, null, null, null);
    }
}
