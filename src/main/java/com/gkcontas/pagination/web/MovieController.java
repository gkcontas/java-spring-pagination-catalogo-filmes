package com.gkcontas.pagination.web;

import com.gkcontas.pagination.dto.BenchmarkResponse;
import com.gkcontas.pagination.dto.CollectionFetchTrapResponse;
import com.gkcontas.pagination.dto.CursorPageResponse;
import com.gkcontas.pagination.dto.MovieFilter;
import com.gkcontas.pagination.dto.MovieResponse;
import com.gkcontas.pagination.dto.PageResponse;
import com.gkcontas.pagination.dto.SliceResponse;
import com.gkcontas.pagination.service.BenchmarkService;
import com.gkcontas.pagination.service.CollectionFetchTrapService;
import com.gkcontas.pagination.service.MovieQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The three strategies are exposed as three separate endpoints on purpose, sharing the
 * same filters and the same data, so they can be compared directly.
 */
@RestController
@RequestMapping("/movies")
public class MovieController {

    private final MovieQueryService movieQueryService;
    private final BenchmarkService benchmarkService;
    private final CollectionFetchTrapService collectionFetchTrapService;

    public MovieController(MovieQueryService movieQueryService,
                           BenchmarkService benchmarkService,
                           CollectionFetchTrapService collectionFetchTrapService) {
        this.movieQueryService = movieQueryService;
        this.benchmarkService = benchmarkService;
        this.collectionFetchTrapService = collectionFetchTrapService;
    }

    @GetMapping("/offset")
    public PageResponse<MovieResponse> byOffset(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(required = false) BigDecimal minRating) {

        MovieFilter filter = new MovieFilter(title, genre, yearFrom, yearTo, minRating);
        return movieQueryService.findByOffset(filter, page, size, sort);
    }

    @GetMapping("/slice")
    public SliceResponse<MovieResponse> bySlice(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(required = false) BigDecimal minRating) {

        MovieFilter filter = new MovieFilter(title, genre, yearFrom, yearTo, minRating);
        return movieQueryService.findBySlice(filter, page, size, sort);
    }

    /**
     * The sort order is fixed here (release date descending, id descending) because it
     * has to match the cursor key and the index behind it. Letting the client sort by an
     * arbitrary column would require a cursor per sort key.
     */
    @GetMapping("/keyset")
    public CursorPageResponse<MovieResponse> byKeyset(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(required = false) BigDecimal minRating) {

        MovieFilter filter = new MovieFilter(title, genre, yearFrom, yearTo, minRating);
        return movieQueryService.findByKeyset(filter, cursor, size);
    }

    @GetMapping("/benchmark")
    public BenchmarkResponse benchmark(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) List<Integer> pages) {

        return benchmarkService.run(size, pages);
    }

    @GetMapping("/collection-fetch-trap")
    public CollectionFetchTrapResponse collectionFetchTrap(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(100) int size) {

        return collectionFetchTrapService.demonstrate(page, size);
    }
}
