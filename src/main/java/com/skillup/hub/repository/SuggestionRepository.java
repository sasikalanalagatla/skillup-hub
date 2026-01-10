package com.skillup.hub.repository;

import com.skillup.hub.model.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SuggestionRepository extends JpaRepository<Suggestion, UUID> {
    List<Suggestion> findByScoreId(UUID scoreId);

    List<Suggestion> findByScoreIdOrderByPriorityAsc(UUID scoreId);
}
