package com.gkcontas.pagination.dto;

import java.util.List;

/**
 * Response envelope for keyset pagination.
 *
 * <p>There is no page number, and there cannot be one: keyset navigation only knows
 * "the next page after this item". That is the price of the constant-time, stable
 * behaviour — random access to page 57 is not available.
 */
public record CursorPageResponse<T>(
        List<T> content,
        int size,
        String nextCursor,
        boolean hasNext) {
}
