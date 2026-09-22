package com.gkcontas.pagination.repository;

import com.gkcontas.pagination.cursor.Cursor;
import com.gkcontas.pagination.dto.MovieFilter;
import com.gkcontas.pagination.dto.MovieResponse;
import com.gkcontas.pagination.model.Movie;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class MovieRepositoryCustomImpl implements MovieRepositoryCustom {

    /** Standard JPA hint name; applying it makes the listed attributes load eagerly. */
    private static final String FETCH_GRAPH_HINT = "jakarta.persistence.fetchgraph";

    private final EntityManager entityManager;

    public MovieRepositoryCustomImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<Movie> findWindow(Specification<Movie> specification, Sort sort, int offset, int limit) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Movie> criteriaQuery = criteriaBuilder.createQuery(Movie.class);
        Root<Movie> root = criteriaQuery.from(Movie.class);

        Predicate predicate = specification.toPredicate(root, criteriaQuery, criteriaBuilder);
        if (predicate != null) {
            criteriaQuery.where(predicate);
        }
        criteriaQuery.orderBy(toOrders(sort, root, criteriaBuilder));

        // Same purpose as @EntityGraph on a derived query: load the genre in the same
        // statement instead of one extra SELECT per row.
        EntityGraph<Movie> entityGraph = entityManager.createEntityGraph(Movie.class);
        entityGraph.addAttributeNodes("genre");

        return entityManager.createQuery(criteriaQuery)
                .setHint(FETCH_GRAPH_HINT, entityGraph)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public List<MovieResponse> findByKeyset(MovieFilter filter, Cursor cursor, int limit) {
        // Native SQL rather than Criteria for one reason: JPA has no way to express a
        // row value comparison, and that is precisely the construct that makes keyset
        // pagination a single index seek.
        StringBuilder sql = new StringBuilder("""
                SELECT m.id           AS id,
                       m.title        AS title,
                       m.release_date AS release_date,
                       m.rating       AS rating,
                       g.name         AS genre_name
                FROM movies m
                JOIN genres g ON g.id = m.genre_id
                WHERE 1 = 1
                """);

        // Only fixed fragments are ever appended to the SQL; every value the caller
        // supplies travels as a bound parameter, so this stays injection-safe.
        Map<String, Object> parameters = new LinkedHashMap<>();
        appendFilters(sql, parameters, filter);

        if (cursor != null) {
            // (a, b) < (x, y) lets Postgres seek straight into idx_movies_keyset and walk
            // forward. The logically equivalent "a < x OR (a = x AND b < y)" is much
            // harder for the planner to turn into a single index scan.
            sql.append("  AND (m.release_date, m.id) < (:cursorDate, :cursorId)\n");
            parameters.put("cursorDate", cursor.releaseDate());
            parameters.put("cursorId", cursor.id());
        }

        sql.append("ORDER BY m.release_date DESC, m.id DESC\nLIMIT :maxRows");
        parameters.put("maxRows", limit);

        Query query = entityManager.createNativeQuery(sql.toString(), Tuple.class);
        parameters.forEach(query::setParameter);

        @SuppressWarnings("unchecked")
        List<Tuple> rows = query.getResultList();
        return rows.stream().map(MovieRepositoryCustomImpl::toMovieResponse).toList();
    }

    private static void appendFilters(StringBuilder sql, Map<String, Object> parameters, MovieFilter filter) {
        if (StringUtils.hasText(filter.title())) {
            sql.append("  AND LOWER(m.title) LIKE :title\n");
            parameters.put("title", "%" + filter.title().toLowerCase(Locale.ROOT) + "%");
        }
        if (StringUtils.hasText(filter.genre())) {
            sql.append("  AND g.name = :genre\n");
            parameters.put("genre", filter.genre());
        }
        if (filter.yearFrom() != null) {
            sql.append("  AND m.release_date >= :dateFrom\n");
            parameters.put("dateFrom", LocalDate.of(filter.yearFrom(), 1, 1));
        }
        if (filter.yearTo() != null) {
            sql.append("  AND m.release_date <= :dateTo\n");
            parameters.put("dateTo", LocalDate.of(filter.yearTo(), 12, 31));
        }
        if (filter.minRating() != null) {
            sql.append("  AND m.rating >= :minRating\n");
            parameters.put("minRating", filter.minRating());
        }
    }

    private static MovieResponse toMovieResponse(Tuple row) {
        return new MovieResponse(
                ((Number) row.get("id")).longValue(),
                (String) row.get("title"),
                toLocalDate(row.get("release_date")),
                (BigDecimal) row.get("rating"),
                (String) row.get("genre_name"));
    }

    private static LocalDate toLocalDate(Object value) {
        // The driver may hand back either type depending on version and configuration.
        return switch (value) {
            case LocalDate localDate -> localDate;
            case java.sql.Date sqlDate -> sqlDate.toLocalDate();
            default -> throw new IllegalStateException("Unexpected release_date type: " + value.getClass());
        };
    }

    private static List<Order> toOrders(Sort sort, Root<Movie> root, CriteriaBuilder criteriaBuilder) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            orders.add(order.isAscending()
                    ? criteriaBuilder.asc(root.get(order.getProperty()))
                    : criteriaBuilder.desc(root.get(order.getProperty())));
        }
        return orders;
    }
}
