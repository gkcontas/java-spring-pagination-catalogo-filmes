package com.gkcontas.pagination.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.pagination.dto.MovieFilter;
import com.gkcontas.pagination.dto.MovieResponse;
import com.gkcontas.pagination.dto.PageResponse;
import com.gkcontas.pagination.dto.SliceResponse;
import com.gkcontas.pagination.service.MovieQueryService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Counts statements instead of trusting the code to be right.
 *
 * <p>N+1 never shows up in the response — the JSON is identical either way. The only
 * way to keep a regression from slipping in is to assert on how many statements
 * Hibernate actually prepared.
 */
class NPlusOneIntegrationTest extends IntegrationTestBase {

    private static final int PAGE_SIZE = 50;

    @Autowired
    private MovieQueryService movieQueryService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void resetStatistics() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void offsetPageShouldNotRunOneQueryPerRowToResolveTheGenre() {
        PageResponse<MovieResponse> page = movieQueryService.findByOffset(MovieFilter.empty(), 0, PAGE_SIZE, null);

        assertThat(page.content()).hasSize(PAGE_SIZE);
        // Two statements: the window and the COUNT. Without the entity graph this would
        // be 52, because mapping each row reads movie.getGenre().getName().
        assertThat(statistics.getPrepareStatementCount())
                .as("offset page should cost the window plus the count, nothing more")
                .isEqualTo(2);
    }

    @Test
    void sliceShouldCostASingleStatement() {
        SliceResponse<MovieResponse> slice = movieQueryService.findBySlice(MovieFilter.empty(), 0, PAGE_SIZE, null);

        assertThat(slice.content()).hasSize(PAGE_SIZE);
        assertThat(statistics.getPrepareStatementCount())
                .as("dropping the COUNT should leave exactly one statement")
                .isEqualTo(1);
    }

    @Test
    void keysetShouldCostASingleStatement() {
        movieQueryService.findByKeyset(MovieFilter.empty(), null, PAGE_SIZE);

        assertThat(statistics.getPrepareStatementCount())
                .as("the keyset query projects the genre name directly, so nothing is lazy")
                .isEqualTo(1);
    }
}
