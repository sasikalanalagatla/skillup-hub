package com.skillup.hub.repository;

import com.skillup.hub.model.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    List<Activity> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
