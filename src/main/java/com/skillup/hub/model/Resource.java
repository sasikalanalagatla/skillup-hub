package com.skillup.hub.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resources")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    private String title;

    private String type; // internship | bootcamp | workshop | course

    private String provider;

    private String url;

    @Column(columnDefinition = "TEXT")
    private String skillsTags;

    private String location;

    private Instant startDate;

    private Instant endDate;

    private Instant fetchedAt = Instant.now();
}
