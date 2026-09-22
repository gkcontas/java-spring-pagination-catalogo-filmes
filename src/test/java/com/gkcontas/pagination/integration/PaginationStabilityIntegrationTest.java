package com.gkcontas.pagination.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.pagination.dto.CursorPageResponse;
import com.gkcontas.pagination.dto.MovieFilter;
import com.gkcontas.pagination.dto.MovieResponse;
import com.gkcontas.pagination.dto.PageResponse;
import com.gkcontas.pagination.model.Genre;
import com.gkcontas.pagination.model.Movie;
import com.gkcontas.pagination.service.MovieQueryService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reason keyset pagination exists.
 *
 * <p>Both tests run the same scenario — read a page, have a row inserted above the page
 * boundary, then read the next page. Offset gets it wrong and keyset gets it right, and
 * the first test is written to fail if offset ever stops being wrong, because the whole
 * argument for keyset rests on that flaw being real rather than theoretical.
 */
class PaginationStabilityIntegrationTest extends IntegrationTestBase {

    private static final int PAGE_SIZE = 3;

    /** Later than every seeded release date, so the new row lands at the top. */
    private static final LocalDate FUTURE_RELEASE = LocalDate.of(2030, 1, 1);

    @Autowired
    private MovieQueryService movieQueryService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void offsetShouldRepeatAnItemWhenARowIsInsertedAboveThePageBoundary() {
        PageResponse<MovieResponse> firstPage =
                movieQueryService.findByOffset(MovieFilter.empty(), 0, PAGE_SIZE, null);

        insertMovieAtTopOfOrder();

        PageResponse<MovieResponse> secondPage =
                movieQueryService.findByOffset(MovieFilter.empty(), 1, PAGE_SIZE, null);

        List<Long> firstPageIds = idsOf(firstPage.content());
        List<Long> secondPageIds = idsOf(secondPage.content());

        // The insert shifted every row down by one position, so OFFSET 3 now points at a
        // row the client has already seen. Nothing is corrupt — the window simply means
        // something different than it did a moment ago.
        assertThat(secondPageIds)
                .as("offset pagination shifts its window when rows are inserted above it")
                .containsAnyElementsOf(firstPageIds);
    }

    @Test
    @Transactional
    void keysetShouldNotRepeatAnItemWhenARowIsInsertedAboveThePageBoundary() {
        CursorPageResponse<MovieResponse> firstPage =
                movieQueryService.findByKeyset(MovieFilter.empty(), null, PAGE_SIZE);

        insertMovieAtTopOfOrder();

        CursorPageResponse<MovieResponse> secondPage =
                movieQueryService.findByKeyset(MovieFilter.empty(), firstPage.nextCursor(), PAGE_SIZE);

        List<Long> firstPageIds = idsOf(firstPage.content());
        List<Long> secondPageIds = idsOf(secondPage.content());

        // The cursor anchors on a value, not a position, so an insert anywhere above it
        // is irrelevant: "the rows after this one" is still the same set of rows.
        assertThat(secondPageIds)
                .as("keyset pagination is anchored to a value, so inserts above it cannot shift it")
                .doesNotContainAnyElementsOf(firstPageIds);
    }

    private void insertMovieAtTopOfOrder() {
        Genre anyGenre = entityManager.createQuery("SELECT g FROM Genre g", Genre.class)
                .setMaxResults(1)
                .getSingleResult();

        entityManager.persist(new Movie("Newly Released", FUTURE_RELEASE, new BigDecimal("8.50"), anyGenre));

        // Explicit flush: the keyset query is native SQL, and Hibernate cannot infer
        // which tables a native statement touches, so automatic flushing is not
        // guaranteed to happen before it runs.
        entityManager.flush();
    }

    private static List<Long> idsOf(List<MovieResponse> movies) {
        return movies.stream().map(MovieResponse::id).toList();
    }
}
