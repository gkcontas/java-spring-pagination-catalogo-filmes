package com.gkcontas.pagination.dto;

import java.util.List;

/**
 * Deliberately verbose: the numbers are the demonstration. Asking for a handful of
 * movies and seeing thousands of entities loaded is what makes the trap concrete.
 */
public record CollectionFetchTrapResponse(
        int requestedSize,
        int returnedSize,
        long entitiesLoadedByHibernate,
        String explanation,
        List<MovieWithReviewsResponse> content) {

    public record MovieWithReviewsResponse(Long id, String title, List<ReviewResponse> reviews) {
    }

    public record ReviewResponse(String author, Short score) {
    }
}
