package com.gkcontas.pagination.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "release_date", nullable = false)
    private LocalDate releaseDate;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal rating;

    /**
     * LAZY on purpose. EAGER would silently fix the N+1 on the listing endpoints and
     * hide the problem this project is about; the fix belongs in the query (entity
     * graph / explicit join), not in the mapping.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "genre_id", nullable = false)
    private Genre genre;

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Review> reviews = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Movie(String title, LocalDate releaseDate, BigDecimal rating, Genre genre) {
        this.title = title;
        this.releaseDate = releaseDate;
        this.rating = rating;
        this.genre = genre;
        this.createdAt = Instant.now();
    }
}
