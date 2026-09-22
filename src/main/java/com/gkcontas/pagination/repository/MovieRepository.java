package com.gkcontas.pagination.repository;

import com.gkcontas.pagination.model.Movie;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MovieRepository
        extends JpaRepository<Movie, Long>, JpaSpecificationExecutor<Movie>, MovieRepositoryCustom {

    /**
     * Overridden only to attach the entity graph. Without it every row in the page
     * triggers an extra SELECT for its genre — the classic N+1 — and paginating makes
     * it worse, since the cost scales with the page size on every single request.
     */
    @Override
    @EntityGraph(attributePaths = "genre")
    Page<Movie> findAll(org.springframework.data.jpa.domain.Specification<Movie> specification, Pageable pageable);

    /**
     * The collection fetch trap, kept in its own method so it is never used by accident.
     *
     * <p>Joining a one-to-many multiplies rows, so LIMIT would cut the result in the
     * middle of a movie's reviews and return an incomplete object. Hibernate refuses to
     * do that: it drops the LIMIT from the SQL, loads <em>every</em> matching row, and
     * paginates the de-duplicated list in memory, logging HHH90003004. The query looks
     * paginated and is not.
     */
    @Query("SELECT m FROM Movie m LEFT JOIN FETCH m.reviews WHERE m.id <= :maxId")
    List<Movie> findAllWithReviews(@Param("maxId") long maxId, Pageable pageable);
}
