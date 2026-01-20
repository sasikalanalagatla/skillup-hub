package com.skillup.hub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "suggestions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "score_id")
    @JsonIgnore
    private Score score;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String message;

    private String priority;

    @Column(columnDefinition = "TEXT")
    private String remediationSteps;

    private String recommendationType;

    private String programName;

    private String programUrl;

    private String duration;

    private String costRange;

    private Instant createdAt = Instant.now();
}