package com.gkcontas.pagination.query;

import com.gkcontas.pagination.exception.InvalidSortException;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

/**
 * Whitelist translating an API sort field into the entity property it maps to.
 *
 * <p>Accepting the client's sort string directly has two problems: an unknown property
 * blows up with a {@code PropertyReferenceException} (a 500 caused by client input),
 * and a valid one lets the caller probe the internal entity model. A whitelist closes
 * both, and decouples the public field names from the mapping underneath.
 */
public final class SortFields {

    private static final Map<String, String> ALLOWED = Map.of(
            "id", "id",
            "title", "title",
            "releaseDate", "releaseDate",
            "rating", "rating");

    private static final String TIE_BREAKER = "id";

    private SortFields() {
    }

    /**
     * Resolves a {@code field,direction} string (e.g. {@code rating,desc}).
     *
     * <p>The id is always appended as a tie breaker. Without it, rows sharing the sort
     * value come back in whatever order the database happens to produce, which differs
     * between executions — so the same row can appear on two consecutive pages while
     * another never appears at all.
     */
    public static Sort resolve(String sort) {
        if (!StringUtils.hasText(sort)) {
            return Sort.by(Sort.Direction.DESC, "releaseDate", TIE_BREAKER);
        }

        String[] parts = sort.split(",", 2);
        String field = parts[0].trim();
        String property = ALLOWED.get(field);
        if (property == null) {
            throw new InvalidSortException(field, allowedFields());
        }

        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        if (TIE_BREAKER.equals(property)) {
            return Sort.by(direction, property);
        }
        return Sort.by(direction, property, TIE_BREAKER);
    }

    public static Set<String> allowedFields() {
        return ALLOWED.keySet();
    }
}
