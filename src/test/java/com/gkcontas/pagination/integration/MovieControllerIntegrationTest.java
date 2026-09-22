package com.gkcontas.pagination.integration;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class MovieControllerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectASortFieldThatIsNotWhitelisted() throws Exception {
        mockMvc.perform(get("/movies/offset").param("sort", "passwordHash,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid sort"));
    }

    @Test
    void shouldRejectAMalformedCursor() throws Exception {
        mockMvc.perform(get("/movies/keyset").param("cursor", "not-a-valid-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid cursor"));
    }

    @Test
    void shouldRejectAPageSizeAboveTheAllowedMaximum() throws Exception {
        // Left unbounded, a caller could ask for one page holding the whole table.
        mockMvc.perform(get("/movies/offset").param("size", "5000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectANegativePageNumber() throws Exception {
        mockMvc.perform(get("/movies/offset").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldApplyTheFiltersConsistentlyAcrossStrategies() throws Exception {
        mockMvc.perform(get("/movies/offset")
                        .param("size", "5")
                        .param("genre", "Horror")
                        .param("minRating", "9.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].genre").value("Horror"))
                .andExpect(jsonPath("$.content[0].rating", greaterThan(9.49)));

        mockMvc.perform(get("/movies/keyset")
                        .param("size", "5")
                        .param("genre", "Horror")
                        .param("minRating", "9.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].genre").value("Horror"))
                .andExpect(jsonPath("$.nextCursor").exists());
    }

    @Test
    void collectionFetchTrapShouldReportFarMoreEntitiesThanRequested() throws Exception {
        mockMvc.perform(get("/movies/collection-fetch-trap").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedSize").value(3))
                .andExpect(jsonPath("$.returnedSize").value(3))
                // Asking for 3 movies materialises thousands of entities. If this number
                // ever drops to something sane, the trap has stopped being demonstrated.
                .andExpect(jsonPath("$.entitiesLoadedByHibernate", greaterThan(1_000)));
    }

    @Test
    void benchmarkShouldShowOffsetDegradingWithDepthWhileKeysetStaysFlat() throws Exception {
        mockMvc.perform(get("/movies/benchmark").param("size", "20").param("pages", "10", "9000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[1].rowsSkippedByOffset").value(180_000));
    }
}
