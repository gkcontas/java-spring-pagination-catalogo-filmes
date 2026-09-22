package com.gkcontas.pagination.service;

import com.gkcontas.pagination.dto.CollectionFetchTrapResponse;
import com.gkcontas.pagination.dto.CollectionFetchTrapResponse.MovieWithReviewsResponse;
import com.gkcontas.pagination.dto.CollectionFetchTrapResponse.ReviewResponse;
import com.gkcontas.pagination.model.Movie;
import com.gkcontas.pagination.repository.MovieRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the collection fetch query and reports how many entities Hibernate actually had
 * to materialise, which is the only way to make the cost of this trap visible: the
 * response looks perfectly normal, and only the entity load count gives it away.
 */
@Service
@Transactional(readOnly = true)
public class CollectionFetchTrapService {

    private static final long MAX_MOVIE_ID_WITH_REVIEWS = 2_000L;

    private final MovieRepository movieRepository;
    private final Statistics statistics;

    public CollectionFetchTrapService(MovieRepository movieRepository, EntityManagerFactory entityManagerFactory) {
        this.movieRepository = movieRepository;
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    public CollectionFetchTrapResponse demonstrate(int page, int size) {
        long entitiesBefore = statistics.getEntityLoadCount();

        List<Movie> movies = movieRepository.findAllWithReviews(
                MAX_MOVIE_ID_WITH_REVIEWS, PageRequest.of(page, size));

        long entitiesLoaded = statistics.getEntityLoadCount() - entitiesBefore;

        List<MovieWithReviewsResponse> content = movies.stream()
                .map(movie -> new MovieWithReviewsResponse(
                        movie.getId(),
                        movie.getTitle(),
                        movie.getReviews().stream()
                                .map(review -> new ReviewResponse(review.getAuthor(), review.getScore()))
                                .toList()))
                .toList();

        String explanation = """
                LIMIT never reached the database. Because the query fetches a collection \
                (m.reviews), applying LIMIT in SQL would cut a movie in the middle of its \
                reviews and return an incomplete object, so Hibernate loaded every matching \
                row and paginated the de-duplicated list in memory — see HHH90003004 in the \
                application log. You asked for %d movies and Hibernate materialised %d \
                entities to answer. Fix: paginate the ids first, then fetch the collection \
                for that page (two queries), or load the collection with a separate \
                @BatchSize / subselect fetch.""".formatted(size, entitiesLoaded);

        return new CollectionFetchTrapResponse(size, content.size(), entitiesLoaded, explanation, content);
    }
}
