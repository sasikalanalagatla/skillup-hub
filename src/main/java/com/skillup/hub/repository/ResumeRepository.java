package com.skillup.hub.repository;

import com.skillup.hub.model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findByUserIdOrderByUploadAtDesc(UUID userId);

    List<Resume> findByDeletedAtIsNullOrderByUploadAtDesc();
}
