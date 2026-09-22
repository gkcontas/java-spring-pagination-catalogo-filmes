package com.gkcontas.pagination.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gkcontas.pagination.exception.InvalidSortException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class SortFieldsTest {

    @Test
    void shouldFallBackToTheKeysetOrderWhenNoSortIsGiven() {
        Sort sort = SortFields.resolve(null);

        assertThat(sort).containsExactly(
                new Sort.Order(Sort.Direction.DESC, "releaseDate"),
                new Sort.Order(Sort.Direction.DESC, "id"));
    }

    @Test
    void shouldResolveAnAllowedFieldWithTheRequestedDirection() {
        Sort sort = SortFields.resolve("rating,desc");

        assertThat(sort.getOrderFor("rating")).isNotNull();
        assertThat(sort.getOrderFor("rating").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void shouldAppendTheIdAsTieBreaker() {
        Sort sort = SortFields.resolve("title,asc");

        // Without this, rows sharing a title come back in an order the database is free
        // to change between executions, which makes page boundaries non-deterministic.
        assertThat(sort).last().isEqualTo(new Sort.Order(Sort.Direction.ASC, "id"));
    }

    @Test
    void shouldNotDuplicateTheIdWhenSortingByItDirectly() {
        Sort sort = SortFields.resolve("id,desc");

        assertThat(sort).containsExactly(new Sort.Order(Sort.Direction.DESC, "id"));
    }

    @Test
    void shouldDefaultToAscendingWhenTheDirectionIsMissingOrUnknown() {
        assertThat(SortFields.resolve("title").getOrderFor("title").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(SortFields.resolve("title,sideways").getOrderFor("title").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void shouldRejectAFieldThatIsNotWhitelisted() {
        // An unknown property would otherwise reach Spring Data and surface as a 500,
        // and a real-but-private one would leak the internal entity model.
        assertThatThrownBy(() -> SortFields.resolve("passwordHash,desc"))
                .isInstanceOf(InvalidSortException.class)
                .hasMessageContaining("passwordHash");
    }
}
