package com.skillup.hub.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillup.hub.model.Activity;
import com.skillup.hub.model.User;
import com.skillup.hub.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;

    public void logActivity(User user, String action, Map<String, Object> metadata) throws JsonProcessingException {
        Activity activity = new Activity();
        activity.setUser(user);
        activity.setAction(action);
        activity.setMetadata(new ObjectMapper().writeValueAsString(metadata)); // or use JSONB if Postgres
        activity.setCreatedAt(Instant.now());
        activityRepository.save(activity);
    }

    public List<Activity> getUserActivities(UUID userId) {
        return activityRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
