package com.gkcontas.pagination.service;

import com.gkcontas.pagination.cursor.Cursor;
import com.gkcontas.pagination.dto.BenchmarkResponse;
import com.gkcontas.pagination.dto.BenchmarkResponse.BenchmarkRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Measures offset against keyset at increasing depths.
 *
 * <p>Both strategies run the exact same FROM/JOIN/ORDER BY and differ only in the
 * pagination clause. Benchmarking two different query implementations would measure the
 * implementations, not the strategies.
 */
@Service
@Transactional(readOnly = true)
public class BenchmarkService {

    private static final List<Integer> DEFAULT_PAGES = List.of(0, 10, 100, 1_000, 5_000, 9_000);
    private static final int MEASUREMENT_RUNS = 5;

    private static final String METHODOLOGY = """
            Both strategies run the same query and differ only in the pagination clause. \
            Each measurement runs one untimed warm-up followed by 5 timed runs, and reports the median. \
            The cursor used by the keyset query is resolved beforehand and is NOT included in its time, \
            because a real client already holds it from the previous page. \
            All runs hit a warm buffer cache, which favours offset — on a cold cache the gap is wider.""";

    private final EntityManager entityManager;

    public BenchmarkService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public BenchmarkResponse run(int pageSize, List<Integer> pages) {
        List<Integer> targetPages = (pages == null || pages.isEmpty()) ? DEFAULT_PAGES : pages;
        List<BenchmarkRow> results = new ArrayList<>();

        for (int page : targetPages) {
            long offset = (long) page * pageSize;
            // Null at page 0, where keyset starts from the top with no cursor at all.
            Cursor cursor = cursorAt(offset);
            if (cursor == null && offset > 0) {
                continue; // page is past the end of the table
            }

            double offsetMillis = measure(() -> runOffsetQuery(offset, pageSize));
            double countMillis = measure(this::runCountQuery);
            double keysetMillis = measure(() -> runKeysetQuery(cursor, pageSize));
            double offsetTotal = offsetMillis + countMillis;

            results.add(new BenchmarkRow(
                    page,
                    offset,
                    round(offsetMillis),
                    round(countMillis),
                    round(offsetTotal),
                    round(keysetMillis),
                    verdict(offsetTotal, keysetMillis)));
        }

        return new BenchmarkResponse(pageSize, METHODOLOGY, results);
    }

    /**
     * Resolves the cursor sitting at the given depth. This is itself an offset query and
     * is intentionally excluded from the measurement.
     */
    private Cursor cursorAt(long offset) {
        if (offset == 0) {
            return null;
        }
        List<?> rows = entityManager.createNativeQuery("""
                        SELECT m.release_date AS release_date, m.id AS id
                        FROM movies m
                        ORDER BY m.release_date DESC, m.id DESC
                        OFFSET :skip LIMIT 1
                        """, Tuple.class)
                .setParameter("skip", offset - 1)
                .getResultList();

        if (rows.isEmpty()) {
            return null;
        }
        Tuple row = (Tuple) rows.getFirst();
        Object releaseDate = row.get("release_date");
        LocalDate date = releaseDate instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate() : (LocalDate) releaseDate;
        return new Cursor(date, ((Number) row.get("id")).longValue());
    }

    private void runOffsetQuery(long offset, int limit) {
        entityManager.createNativeQuery("""
                        SELECT m.id, m.title, m.release_date, m.rating, g.name
                        FROM movies m
                        JOIN genres g ON g.id = m.genre_id
                        ORDER BY m.release_date DESC, m.id DESC
                        LIMIT :maxRows OFFSET :skip
                        """)
                .setParameter("maxRows", limit)
                .setParameter("skip", offset)
                .getResultList();
    }

    private void runKeysetQuery(Cursor cursor, int limit) {
        if (cursor == null) {
            entityManager.createNativeQuery("""
                            SELECT m.id, m.title, m.release_date, m.rating, g.name
                            FROM movies m
                            JOIN genres g ON g.id = m.genre_id
                            ORDER BY m.release_date DESC, m.id DESC
                            LIMIT :maxRows
                            """)
                    .setParameter("maxRows", limit)
                    .getResultList();
            return;
        }
        entityManager.createNativeQuery("""
                        SELECT m.id, m.title, m.release_date, m.rating, g.name
                        FROM movies m
                        JOIN genres g ON g.id = m.genre_id
                        WHERE (m.release_date, m.id) < (:cursorDate, :cursorId)
                        ORDER BY m.release_date DESC, m.id DESC
                        LIMIT :maxRows
                        """)
                .setParameter("cursorDate", cursor.releaseDate())
                .setParameter("cursorId", cursor.id())
                .setParameter("maxRows", limit)
                .getResultList();
    }

    private void runCountQuery() {
        entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM movies m
                        JOIN genres g ON g.id = m.genre_id
                        """)
                .getSingleResult();
    }

    private static double measure(Runnable action) {
        action.run(); // warm-up: the first execution pays for plan caching and buffer warm-up
        long[] samples = new long[MEASUREMENT_RUNS];
        for (int i = 0; i < MEASUREMENT_RUNS; i++) {
            long start = System.nanoTime();
            action.run();
            samples[i] = System.nanoTime() - start;
        }
        Arrays.sort(samples);
        // Median, not average: a single GC pause or background checkpoint would drag the
        // average somewhere the typical request never goes.
        return samples[MEASUREMENT_RUNS / 2] / 1_000_000.0;
    }

    private static String verdict(double offsetTotal, double keysetMillis) {
        if (keysetMillis <= 0) {
            return "keyset too fast to measure reliably at this depth";
        }
        // String.format with an explicit Locale, not String.formatted: the latter has no
        // locale overload and always uses the JVM default, so the same API would answer
        // "14.2" or "14,2" depending on the machine it runs on.
        return String.format(Locale.ROOT, "keyset %.1fx faster", offsetTotal / keysetMillis);
    }

    private static double round(double millis) {
        return Math.round(millis * 100.0) / 100.0;
    }
}
