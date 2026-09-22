package com.gkcontas.pagination.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Response envelope for offset pagination.
 *
 * <p>Spring's {@code Page}/{@code PageImpl} is deliberately not serialized directly:
 * its JSON shape is an implementation detail that has changed between versions (Spring
 * Boot 3.3+ even logs a warning about relying on it), so exposing it turns an internal
 * class into a public API contract.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <E, T> PageResponse<T> of(Page<E> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
