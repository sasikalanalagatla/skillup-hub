package com.skillup.hub.model;

import jakarta.persistence.*;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resume_sections")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id")
    private Resume resume;

    private String sectionType;

    @Lob
    @Column(columnDefinition = "text")
    private String content;

    private Integer startLine;

    private Integer endLine;
}
