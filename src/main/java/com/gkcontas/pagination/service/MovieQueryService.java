package com.gkcontas.pagination.service;

import com.gkcontas.pagination.cursor.Cursor;
import com.gkcontas.pagination.cursor.CursorCodec;
import com.gkcontas.pagination.dto.CursorPageResponse;
import com.gkcontas.pagination.dto.MovieFilter;
import com.gkcontas.pagination.dto.MovieResponse;
import com.gkcontas.pagination.dto.PageResponse;
import com.gkcontas.pagination.dto.SliceResponse;
import com.gkcontas.pagination.model.Movie;
import com.gkcontas.pagination.query.MovieSpecifications;
import com.gkcontas.pagination.query.SortFields;
import com.gkcontas.pagination.repository.MovieRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class MovieQueryService {

    private final MovieRepository movieRepository;
    private final CursorCodec cursorCodec;

    public MovieQueryService(MovieRepository movieRepository, CursorCodec cursorCodec) {
        this.movieRepository = movieRepository;
        this.cursorCodec = cursorCodec;
    }

    /**
     * Offset pagination. Two statements per request: the window plus a COUNT for the
     * totals. The deeper the page, the more rows the database scans and discards.
     */
    public PageResponse<MovieResponse> findByOffset(MovieFilter filter, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, SortFields.resolve(sort));
        Page<Movie> result = movieRepository.findAll(MovieSpecifications.matching(filter), pageable);
        List<MovieResponse> content = result.getContent().stream().map(MovieResponse::from).toList();
        return PageResponse.of(result, content);
    }

    /**
     * Offset pagination without the COUNT. One extra row is requested: if it comes back,
     * a next page exists. Same scan cost as offset, but one statement instead of two.
     */
    public SliceResponse<MovieResponse> findBySlice(MovieFilter filter, int page, int size, String sort) {
        Sort resolvedSort = SortFields.resolve(sort);
        int offset = Math.toIntExact((long) page * size);
        List<Movie> window = movieRepository
                .findWindow(MovieSpecifications.matching(filter), resolvedSort, offset, size + 1);

        boolean hasNext = window.size() > size;
        List<MovieResponse> content = window.stream().limit(size).map(MovieResponse::from).toList();
        return new SliceResponse<>(content, page, size, hasNext);
    }

    /**
     * Keyset pagination. The database seeks directly to the cursor position, so the cost
     * depends on the page size and not on how deep the page is.
     */
    public CursorPageResponse<MovieResponse> findByKeyset(MovieFilter filter, String encodedCursor, int size) {
        Cursor cursor = StringUtils.hasText(encodedCursor) ? cursorCodec.decode(encodedCursor) : null;
        List<MovieResponse> window = movieRepository.findByKeyset(filter, cursor, size + 1);

        boolean hasNext = window.size() > size;
        List<MovieResponse> content = window.stream().limit(size).toList();

        String nextCursor = null;
        if (hasNext && !content.isEmpty()) {
            MovieResponse last = content.getLast();
            nextCursor = cursorCodec.encode(new Cursor(last.releaseDate(), last.id()));
        }
        return new CursorPageResponse<>(content, size, nextCursor, hasNext);
    }
}
