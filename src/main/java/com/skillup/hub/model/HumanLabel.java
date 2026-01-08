package com.skillup.hub.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "human_labels")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HumanLabel {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "labeler_id")
    private User labeler;

    private Double score;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private Instant createdAt = Instant.now();
}
