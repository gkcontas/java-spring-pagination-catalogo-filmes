package com.gkcontas.pagination.dto;

import java.util.List;

/**
 * Response envelope for offset pagination without the COUNT query.
 *
 * <p>There is no {@code totalElements} or {@code totalPages} here, and that absence is
 * the whole point: on a large table the COUNT can cost more than fetching the page
 * itself. When the UI only needs "is there more?", paying for the total is waste.
 */
public record SliceResponse<T>(
        List<T> content,
        int page,
        int size,
        boolean hasNext) {
}
