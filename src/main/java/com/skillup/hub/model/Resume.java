package com.skillup.hub.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resumes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String filename;

    private String s3Key;

    @Lob
    @Column(columnDefinition = "text")
    private String textExtracted;

    @Lob
    @Column(columnDefinition = "text")
    private String parsedJson;

    private Instant uploadAt = Instant.now();

    private String privacyLevel;

    private Instant deletedAt;
}
