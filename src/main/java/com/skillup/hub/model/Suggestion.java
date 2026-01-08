package com.skillup.hub.model;

import jakarta.persistence.*;

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
    private Score score;

    private String category; // skills | experience | format | other

    @Lob
    @Column(columnDefinition = "text")
    private String message;

    private String priority; // high | medium | low

    @Lob
    @Column(columnDefinition = "text")
    private String remediationSteps;
}
