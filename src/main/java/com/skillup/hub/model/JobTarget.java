package com.skillup.hub.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_targets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String source; // url | manual | search

    private String title;

    private String company;

    private String url;

    @Lob
    @Column(columnDefinition = "text")
    private String rawText;

    private Instant fetchedAt = Instant.now();
}
