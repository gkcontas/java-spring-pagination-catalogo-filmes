package com.gkcontas.pagination.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.gkcontas.pagination.dto.CursorPageResponse;
import com.gkcontas.pagination.dto.MovieFilter;
import com.gkcontas.pagination.dto.MovieResponse;
import com.gkcontas.pagination.dto.PageResponse;
import com.gkcontas.pagination.dto.SliceResponse;
import com.gkcontas.pagination.service.MovieQueryService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves the three strategies agree on <em>what</em> they return, which is the
 * precondition for comparing how they perform.
 */
class PaginationStrategiesIntegrationTest extends IntegrationTestBase {

    /** Narrow enough to walk to the end quickly, wide enough to span several pages. */
    private static final MovieFilter NARROW_FILTER =
            new MovieFilter(null, "Horror", 2005, 2005, new BigDecimal("9.00"));

    private static final int PAGE_SIZE = 7;

    @Autowired
    private MovieQueryService movieQueryService;

    @Test
    void theThreeStrategiesShouldReturnTheSameItemsInTheSameOrder() {
        List<Long> byOffset = walkWithOffset(NARROW_FILTER);
        List<Long> bySlice = walkWithSlice(NARROW_FILTER);
        List<Long> byKeyset = walkWithKeyset(NARROW_FILTER);

        assertThat(byOffset).isNotEmpty().hasSizeLessThan(1_000);
        assertThat(bySlice).containsExactlyElementsOf(byOffset);
        assertThat(byKeyset).containsExactlyElementsOf(byOffset);
    }

    @Test
    void offsetShouldReportTheTotalAndSliceShouldNot() {
        PageResponse<MovieResponse> page = movieQueryService.findByOffset(NARROW_FILTER, 0, PAGE_SIZE, null);
        SliceResponse<MovieResponse> slice = movieQueryService.findBySlice(NARROW_FILTER, 0, PAGE_SIZE, null);

        assertThat(page.totalElements()).isPositive();
        assertThat(page.totalPages()).isPositive();
        // SliceResponse has no total at all: that absence is the whole point of the type.
        assertThat(slice.content()).containsExactlyElementsOf(page.content());
        assertThat(slice.hasNext()).isTrue();
    }

    @Test
    void keysetShouldNotRepeatOrSkipRowsThatShareAReleaseDate() {
        // The catalog puts ~10 movies on each release date. Walking across such a group in
        // small pages is exactly where a single-column cursor breaks down, because the
        // boundary value alone cannot tell the rows apart.
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        for (int pageIndex = 0; pageIndex < 4; pageIndex++) {
            CursorPageResponse<MovieResponse> page = movieQueryService.findByKeyset(MovieFilter.empty(), cursor, 4);
            page.content().forEach(movie -> ids.add(movie.id()));
            cursor = page.nextCursor();
        }

        assertThat(ids).hasSize(16).doesNotHaveDuplicates();
    }

    @Test
    void keysetShouldStopReportingANextPageAtTheEndOfTheResult() {
        String cursor = null;
        CursorPageResponse<MovieResponse> page;
        int guard = 0;
        do {
            page = movieQueryService.findByKeyset(NARROW_FILTER, cursor, PAGE_SIZE);
            cursor = page.nextCursor();
        } while (page.hasNext() && ++guard < 500);

        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    private List<Long> walkWithOffset(MovieFilter filter) {
        List<Long> ids = new ArrayList<>();
        PageResponse<MovieResponse> page;
        int pageIndex = 0;
        do {
            page = movieQueryService.findByOffset(filter, pageIndex++, PAGE_SIZE, null);
            page.content().forEach(movie -> ids.add(movie.id()));
        } while (!page.last());
        return ids;
    }

    private List<Long> walkWithSlice(MovieFilter filter) {
        List<Long> ids = new ArrayList<>();
        SliceResponse<MovieResponse> slice;
        int pageIndex = 0;
        do {
            slice = movieQueryService.findBySlice(filter, pageIndex++, PAGE_SIZE, null);
            slice.content().forEach(movie -> ids.add(movie.id()));
        } while (slice.hasNext());
        return ids;
    }

    private List<Long> walkWithKeyset(MovieFilter filter) {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        CursorPageResponse<MovieResponse> page;
        do {
            page = movieQueryService.findByKeyset(filter, cursor, PAGE_SIZE);
            page.content().forEach(movie -> ids.add(movie.id()));
            cursor = page.nextCursor();
        } while (page.hasNext());
        return ids;
    }
}
