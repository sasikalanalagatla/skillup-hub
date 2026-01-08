package com.skillup.hub.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_postings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_target_id")
    private JobTarget jobTarget;

    private String provider;

    private String title;

    private String company;

    private String location;

    @Lob
    @Column(columnDefinition = "text")
    private String description;

    @Lob
    @Column(columnDefinition = "text")
    private String requiredSkills;

    @Lob
    @Column(columnDefinition = "text")
    private String preferredSkills;

    @Lob
    @Column(columnDefinition = "text")
    private String rawHtml;

    private Instant fetchedAt = Instant.now();
}
