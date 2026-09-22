package com.gkcontas.pagination.cursor;

import java.time.LocalDate;

/**
 * Position of the last item of a page, used as the anchor for the next one.
 *
 * <p>The id is not decoration: release dates repeat in the catalog, and ordering by a
 * non-unique column alone produces an unstable order. Rows sharing the boundary date
 * would be skipped or repeated when the page turns. The (releaseDate, id) pair is
 * unique, which makes the order total and the page boundary exact.
 */
public record Cursor(LocalDate releaseDate, long id) {
}
