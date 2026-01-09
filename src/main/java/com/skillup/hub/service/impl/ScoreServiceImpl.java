package com.skillup.hub.service.impl;

import com.skillup.hub.model.*;
import com.skillup.hub.repository.ScoreRepository;
import com.skillup.hub.service.ResumeService;
import com.skillup.hub.service.ScoreService;
import com.skillup.hub.service.ai.AiScoreResult;
import com.skillup.hub.service.ai.AiScoringClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScoreServiceImpl implements ScoreService {

    private final ScoreRepository scoreRepository;
    private final ResumeService resumeService;
    private final AiScoringClient aiScoringClient;

    private static final String ENGINE_VERSION_AI = "v2.0-ai";

    @Override
    public Score scoreResume(UUID resumeId, UUID jobTargetId, String jobInfo) {
        Resume resume = resumeService.getResumeById(resumeId);
        String resumeText = resume.getTextExtracted();

        if (resumeText == null || resumeText.trim().isEmpty()) {
            throw new IllegalArgumentException("Resume has no extracted text");
        }

        // Try AI-based scoring first; no heuristic fallback
        Double overallScore;
        Double skillsScore;
        Double experienceScore;
        Double keywordsScore;
        Double formattingScore;
        String details;
        String engineVersionUsed = ENGINE_VERSION_AI;

        Optional<AiScoreResult> aiResultOpt = aiScoringClient.score(resumeText, jobInfo);
        if (aiResultOpt.isPresent()) {
            AiScoreResult r = aiResultOpt.get();
            skillsScore = safeBound(r.getSkills());
            experienceScore = safeBound(r.getExperience());
            keywordsScore = safeBound(r.getKeywords());
            formattingScore = safeBound(r.getFormatting());
            // If overall missing, compute weighted
            overallScore = r.getOverall() != null && r.getOverall() > 0
                    ? safeBound(r.getOverall())
                    : computeOverall(skillsScore, experienceScore, keywordsScore, formattingScore);
            details = r.detailsAsJson(new com.fasterxml.jackson.databind.ObjectMapper());
            engineVersionUsed = ENGINE_VERSION_AI;
        } else {
            // AI disabled or failed: return zeroed scores with reason in details
            skillsScore = 0.0;
            experienceScore = 0.0;
            keywordsScore = 0.0;
            formattingScore = 0.0;
            overallScore = 0.0;

            Map<String, Object> detailsMap = new HashMap<>();
            detailsMap.put("reason", "AI scoring disabled or unavailable");
            detailsMap.put("jobInfoProvided", jobInfo != null && !jobInfo.isBlank());
            detailsMap.put("resumeWordCount", resumeText.split("\\s+").length);
            details = detailsMap.toString();
        }

        // Create and save score
        Score score = new Score();
        score.setResume(resume);
        score.setOverallScore(overallScore);
        score.setSkillsScore(skillsScore);
        score.setExperienceScore(experienceScore);
        score.setKeywordsScore(keywordsScore);
        score.setFormattingScore(formattingScore);
        score.setEngineVersion(engineVersionUsed);
        score.setDetails(details);
        score.setCreatedAt(Instant.now());

        return scoreRepository.save(score);
    }

    @Override
    public Score getScoreById(UUID scoreId) {
        return scoreRepository.findById(scoreId)
                .orElseThrow(() -> new IllegalArgumentException("Score not found: " + scoreId));
    }

    private double computeOverall(double skillsScore, double experienceScore, double keywordsScore,
            double formattingScore) {
        double weighted = (skillsScore * 0.35) +
                (experienceScore * 0.30) +
                (keywordsScore * 0.20) +
                (formattingScore * 0.15);
        return Math.min(Math.max(weighted, 0.0), 100.0);
    }

    private double safeBound(Double v) {
        if (v == null)
            return 0.0;
        if (v.isNaN() || v.isInfinite())
            return 0.0;
        return Math.min(Math.max(v, 0.0), 100.0);
    }

    @Override
    public Score getLatestScoreForResume(UUID resumeId) {
        return scoreRepository.findFirstByResumeIdOrderByCreatedAtDesc(resumeId)
                .orElse(null);
    }
}
