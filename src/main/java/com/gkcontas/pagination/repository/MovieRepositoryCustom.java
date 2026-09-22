package com.gkcontas.pagination.repository;

import com.gkcontas.pagination.cursor.Cursor;
import com.gkcontas.pagination.dto.MovieFilter;
import com.gkcontas.pagination.dto.MovieResponse;
import com.gkcontas.pagination.model.Movie;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

public interface MovieRepositoryCustom {

    /**
     * Reads a plain window of rows with no COUNT query — the primitive behind {@code Slice}.
     */
    List<Movie> findWindow(Specification<Movie> specification, Sort sort, int offset, int limit);

    /**
     * Reads the rows positioned after the cursor, using keyset pagination.
     */
    List<MovieResponse> findByKeyset(MovieFilter filter, Cursor cursor, int limit);
}
