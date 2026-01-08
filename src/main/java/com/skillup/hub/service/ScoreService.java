package com.skillup.hub.service;

import com.skillup.hub.model.Score;

import java.util.UUID;

public interface ScoreService {
    Score scoreResume(UUID resumeId, UUID jobTargetId);

    Score getScoreById(UUID scoreId);

    Score getLatestScoreForResume(UUID resumeId);
}
