package com.skillup.hub.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.skillup.hub.model.Resume;
import com.skillup.hub.model.Score;
import com.skillup.hub.model.Suggestion;
import com.skillup.hub.model.User;
import com.skillup.hub.repository.ScoreRepository;
import com.skillup.hub.repository.SuggestionRepository;
import com.skillup.hub.service.ResumeService;
import com.skillup.hub.service.ScoreService;
import com.skillup.hub.service.ai.AiScoreResult;
import com.skillup.hub.service.ai.AiScoringClient;
import com.skillup.hub.service.ai.AiSuggestionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScoreServiceImpl implements ScoreService {

    private final ScoreRepository scoreRepository;
    private final ResumeService resumeService;
    private final ActivityService activityService;
    private final AiScoringClient aiScoringClient;
    private final AiSuggestionClient aiSuggestionClient;
    private final SuggestionRepository suggestionRepository;

    private static final String ENGINE_VERSION_AI = "v2.0-ai";
    private static final String ENGINE_VERSION_BASIC = "v1.0-basic";

    @Override
    public Score scoreResume(UUID resumeId, UUID jobTargetId, String jobInfo) throws JsonProcessingException {
        Resume resume = resumeService.getResumeById(resumeId);
        User user = resume.getUser();
        String resumeText = resume.getTextExtracted();

        if (resumeText == null || resumeText.trim().isEmpty()) {
            throw new IllegalArgumentException("Resume has no extracted text");
        }

        // Try AI-based scoring first, fallback to basic heuristics
        Double overallScore;
        Double skillsScore;
        Double experienceScore;
        Double keywordsScore;
        Double formattingScore;
        String details;
        String engineVersionUsed;

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
            // AI disabled or failed: use basic heuristic scoring
            skillsScore = calculateBasicSkillsScore(resumeText, jobInfo);
            experienceScore = calculateBasicExperienceScore(resumeText);
            keywordsScore = calculateBasicKeywordsScore(resumeText);
            formattingScore = calculateBasicFormattingScore(resumeText);
            overallScore = computeOverall(skillsScore, experienceScore, keywordsScore, formattingScore);

            Map<String, Object> detailsMap = new HashMap<>();
            detailsMap.put("engine", "basic-heuristic");
            detailsMap.put("wordCount", resumeText.split("\\s+").length);
            details = detailsMap.toString();
            engineVersionUsed = ENGINE_VERSION_BASIC;
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

        Score savedScore = scoreRepository.save(score);
        activityService.logActivity(
                user,
                "RESUME_SCORED",
                Map.of(
                        "resumeId",      resumeId.toString(),
                        "scoreId",       savedScore.getId().toString(),
                        "overallScore",  savedScore.getOverallScore(),
                        "skillsScore",   savedScore.getSkillsScore(),
                        "engineVersion", savedScore.getEngineVersion() != null ? savedScore.getEngineVersion() : "unknown",
                        "usedAI",        aiResultOpt.isPresent() ? "true" : "false",
                        "jobInfoLength", jobInfo != null ? jobInfo.length() : 0
                )
        );
        // Generate and persist suggestions (AI-only)
        generateAndSaveSuggestions(savedScore, resumeText, jobInfo, aiResultOpt);


        return savedScore;
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

    private void generateAndSaveSuggestions(Score score, String resumeText, String jobInfo,
            Optional<AiScoreResult> aiResultOpt) {
        Map<String, Object> scoreDetailsMap = new HashMap<>();
        scoreDetailsMap.put("overallScore", score.getOverallScore());
        scoreDetailsMap.put("skillsScore", score.getSkillsScore());
        scoreDetailsMap.put("experienceScore", score.getExperienceScore());
        scoreDetailsMap.put("keywordsScore", score.getKeywordsScore());
        scoreDetailsMap.put("formattingScore", score.getFormattingScore());

        if (aiResultOpt.isPresent() && aiResultOpt.get().getDetails() != null) {
            scoreDetailsMap.put("aiDetails", aiResultOpt.get().getDetails());
        }

        List<AiSuggestionClient.SuggestionItem> items = aiSuggestionClient.generateSuggestions(resumeText, jobInfo,
                scoreDetailsMap);

        if (items == null || items.isEmpty()) {
            return;
        }

        List<Suggestion> toSave = new ArrayList<>();
        for (AiSuggestionClient.SuggestionItem item : items) {
            Suggestion suggestion = new Suggestion();
            suggestion.setScore(score);
            suggestion.setCategory(item.getCategory());
            suggestion.setMessage(item.getMessage());
            suggestion.setPriority(item.getPriority());
            suggestion.setRemediationSteps(item.getRemediationSteps());
            suggestion.setRecommendationType(item.getRecommendationType());   // e.g. "INTERNSHIP", "BOOTCAMP"
            suggestion.setProgramName(item.getProgramName());                // e.g. "Scaler Academy"
            suggestion.setProgramUrl(item.getProgramUrl());                  // e.g. "https://scaler.com/..."
            suggestion.setDuration(item.getDuration());                      // e.g. "6-9 months"
            suggestion.setCostRange(item.getCostRange());
            toSave.add(suggestion);
        }

        if (!toSave.isEmpty()) {
            suggestionRepository.saveAll(toSave);
        }
    }

    private double calculateBasicSkillsScore(String resumeText, String jobInfo) {
        String resumeLower = resumeText.toLowerCase(Locale.ROOT);
        String jobLower = jobInfo == null ? "" : jobInfo.toLowerCase(Locale.ROOT);
        Set<String> tokens = new HashSet<>();
        for (String t : jobLower.split("[^a-z0-9+#/.]+")) {
            if (t.length() >= 3)
                tokens.add(t);
        }
        if (tokens.isEmpty())
            return 55.0; // neutral baseline
        int hits = 0;
        for (String t : tokens) {
            if (resumeLower.contains(t))
                hits++;
        }
        double coverage = (hits * 1.0) / tokens.size();
        return safeBound(40 + coverage * 60);
    }

    private double calculateBasicExperienceScore(String resumeText) {
        String lower = resumeText.toLowerCase(Locale.ROOT);
        int bestYears = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})\\s*\n?(\\+)?\\s*(years|yrs)")
                .matcher(lower);
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            bestYears = Math.max(bestYears, y);
        }
        double base = bestYears > 0 ? Math.min(100, 30 + bestYears * 7) : 55;
        if (lower.contains("lead") || lower.contains("senior"))
            base += 5;
        return safeBound(base);
    }

    private double calculateBasicKeywordsScore(String resumeText) {
        String lower = resumeText.toLowerCase(Locale.ROOT);
        String[] kw = { "designed", "implemented", "optimized", "delivered", "migrated", "microservice", "cloud", "aws",
                "gcp", "azure" };
        int count = 0;
        for (String k : kw)
            if (lower.contains(k))
                count++;
        return safeBound(50.0 + Math.min(25.0, count * 5.0));
    }

    private double calculateBasicFormattingScore(String resumeText) {
        String lower = resumeText.toLowerCase(Locale.ROOT);
        boolean hasExp = lower.contains("experience") || lower.contains("employment");
        boolean hasEdu = lower.contains("education");
        boolean hasSkills = lower.contains("skills");
        boolean hasContact = lower.contains("email") || lower.contains("phone");
        boolean hasBullets = resumeText.contains("•") || resumeText.contains("-") || resumeText.contains("*");
        int score = 60;
        if (hasExp)
            score += 8;
        if (hasEdu)
            score += 8;
        if (hasSkills)
            score += 8;
        if (hasContact)
            score += 8;
        if (hasBullets)
            score += 8;
        return safeBound((double) score);
    }
}
