package com.gkcontas.pagination.query;

import com.gkcontas.pagination.dto.MovieFilter;
import com.gkcontas.pagination.model.Movie;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Builds the dynamic filter shared by the offset and slice strategies.
 *
 * <p>Specifications are used here instead of a JPQL query with {@code :param IS NULL}
 * guards: those guards force the database to parse dead branches on every call and make
 * the query unreadable as the number of optional filters grows.
 */
public final class MovieSpecifications {

    private MovieSpecifications() {
    }

    public static Specification<Movie> matching(MovieFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(filter.title())) {
                String pattern = "%" + filter.title().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), pattern));
            }
            if (StringUtils.hasText(filter.genre())) {
                predicates.add(criteriaBuilder.equal(root.get("genre").get("name"), filter.genre()));
            }
            if (filter.yearFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.<LocalDate>get("releaseDate"), LocalDate.of(filter.yearFrom(), 1, 1)));
            }
            if (filter.yearTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.<LocalDate>get("releaseDate"), LocalDate.of(filter.yearTo(), 12, 31)));
            }
            if (filter.minRating() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.<BigDecimal>get("rating"), filter.minRating()));
            }

            return predicates.isEmpty() ? null : criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
