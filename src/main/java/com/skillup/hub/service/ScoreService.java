package com.skillup.hub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.skillup.hub.model.Score;

import java.util.UUID;

public interface ScoreService {
    Score scoreResume(UUID resumeId, UUID jobTargetId, String jobInfo) throws JsonProcessingException;

    Score getScoreById(UUID scoreId);

    Score getLatestScoreForResume(UUID resumeId);
}
