package com.skillup.hub.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "scores")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Score {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id")
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    private Double overallScore;

    private Double skillsScore;

    private Double experienceScore;

    private Double keywordsScore;

    private Double formattingScore;

    private String engineVersion;

    @Column(columnDefinition = "TEXT")
    private String details;

    private Instant createdAt = Instant.now();
}
