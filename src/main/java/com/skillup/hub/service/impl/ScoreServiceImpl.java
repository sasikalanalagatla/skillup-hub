package com.skillup.hub.service.impl;

import com.skillup.hub.model.*;
import com.skillup.hub.repository.ScoreRepository;
import com.skillup.hub.service.ResumeService;
import com.skillup.hub.service.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ScoreServiceImpl implements ScoreService {

    private final ScoreRepository scoreRepository;
    private final ResumeService resumeService;

    private static final String ENGINE_VERSION = "v1.0-basic";

    // Common tech skills and keywords
    private static final Set<String> TECH_SKILLS = Set.of(
            "java", "python", "javascript", "typescript", "react", "angular", "vue",
            "spring", "springboot", "nodejs", "express", "django", "flask",
            "sql", "postgresql", "mysql", "mongodb", "redis",
            "docker", "kubernetes", "aws", "azure", "gcp",
            "git", "ci/cd", "jenkins", "github", "gitlab",
            "rest", "api", "microservices", "agile", "scrum",
            "html", "css", "bootstrap", "tailwind",
            "junit", "testing", "tdd", "maven", "gradle");

    @Override
    public Score scoreResume(UUID resumeId, UUID jobTargetId) {
        Resume resume = resumeService.getResumeById(resumeId);
        String resumeText = resume.getTextExtracted();

        if (resumeText == null || resumeText.trim().isEmpty()) {
            throw new IllegalArgumentException("Resume has no extracted text");
        }

        // Calculate individual scores
        double skillsScore = calculateSkillsScore(resumeText);
        double experienceScore = calculateExperienceScore(resumeText);
        double keywordsScore = calculateKeywordsScore(resumeText);
        double formattingScore = calculateFormattingScore(resumeText);

        // Calculate overall score (weighted average)
        double overallScore = (skillsScore * 0.35) +
                (experienceScore * 0.30) +
                (keywordsScore * 0.20) +
                (formattingScore * 0.15);

        // Build details JSON
        Map<String, Object> detailsMap = new HashMap<>();
        detailsMap.put("skillsBreakdown", analyzeSkills(resumeText));
        detailsMap.put("experienceYears", extractExperienceYears(resumeText));
        detailsMap.put("sectionsFound", identifySections(resumeText));
        detailsMap.put("wordCount", resumeText.split("\\s+").length);
        String details = detailsMap.toString();

        // Create and save score
        Score score = new Score();
        score.setResume(resume);
        score.setOverallScore(overallScore);
        score.setSkillsScore(skillsScore);
        score.setExperienceScore(experienceScore);
        score.setKeywordsScore(keywordsScore);
        score.setFormattingScore(formattingScore);
        score.setEngineVersion(ENGINE_VERSION);
        score.setDetails(details);
        score.setCreatedAt(Instant.now());

        return scoreRepository.save(score);
    }

    @Override
    public Score getScoreById(UUID scoreId) {
        return scoreRepository.findById(scoreId)
                .orElseThrow(() -> new IllegalArgumentException("Score not found: " + scoreId));
    }

    @Override
    public Score getLatestScoreForResume(UUID resumeId) {
        return scoreRepository.findFirstByResumeIdOrderByCreatedAtDesc(resumeId)
                .orElse(null);
    }

    // Skills scoring: percentage of common tech skills found
    private double calculateSkillsScore(String text) {
        String lowerText = text.toLowerCase();
        long foundSkills = TECH_SKILLS.stream()
                .filter(lowerText::contains)
                .count();

        // Score based on number of skills found
        double rawScore = (foundSkills / 10.0) * 100; // 10+ skills = 100%
        return Math.min(rawScore, 100.0);
    }

    // Experience scoring: extract years of experience
    private double calculateExperienceScore(String text) {
        int years = extractExperienceYears(text);

        // Score based on years (0-2 years = low, 3-5 = medium, 6+ = high)
        if (years >= 6)
            return 100.0;
        if (years >= 3)
            return 75.0;
        if (years >= 1)
            return 50.0;
        return 25.0;
    }

    // Keywords scoring: presence of action verbs and professional terms
    private double calculateKeywordsScore(String text) {
        String lowerText = text.toLowerCase();
        String[] actionVerbs = { "developed", "designed", "implemented", "created", "built",
                "managed", "led", "collaborated", "achieved", "improved" };

        long foundVerbs = Arrays.stream(actionVerbs)
                .filter(lowerText::contains)
                .count();

        double rawScore = (foundVerbs / 5.0) * 100; // 5+ verbs = 100%
        return Math.min(rawScore, 100.0);
    }

    // Formatting scoring: check for sections and structure
    private double calculateFormattingScore(String text) {
        List<String> sections = identifySections(text);
        int wordCount = text.split("\\s+").length;

        double score = 0.0;

        // Check for key sections
        if (sections.contains("experience"))
            score += 30;
        if (sections.contains("education"))
            score += 25;
        if (sections.contains("skills"))
            score += 25;
        if (sections.contains("contact"))
            score += 10;

        // Check word count (300-800 words is ideal)
        if (wordCount >= 300 && wordCount <= 800)
            score += 10;

        return Math.min(score, 100.0);
    }

    // Extract years of experience from text
    private int extractExperienceYears(String text) {
        // Look for patterns like "5 years", "3+ years", "2-4 years"
        Pattern pattern = Pattern.compile("(\\d+)\\+?\\s*years?\\s*(of\\s*)?experience", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        // Count date ranges (rough estimate)
        Pattern datePattern = Pattern.compile("(20\\d{2})\\s*[-–—]\\s*(20\\d{2}|present)", Pattern.CASE_INSENSITIVE);
        Matcher dateMatcher = datePattern.matcher(text);

        int totalYears = 0;
        while (dateMatcher.find()) {
            int startYear = Integer.parseInt(dateMatcher.group(1));
            String endStr = dateMatcher.group(2).toLowerCase();
            int endYear = endStr.equals("present") ? 2026 : Integer.parseInt(endStr);
            totalYears += (endYear - startYear);
        }

        return totalYears;
    }

    // Identify resume sections
    private List<String> identifySections(String text) {
        String lowerText = text.toLowerCase();
        List<String> sections = new ArrayList<>();

        if (lowerText.contains("experience") || lowerText.contains("employment"))
            sections.add("experience");
        if (lowerText.contains("education"))
            sections.add("education");
        if (lowerText.contains("skills") || lowerText.contains("technical"))
            sections.add("skills");
        if (lowerText.contains("projects"))
            sections.add("projects");
        if (lowerText.contains("contact") || lowerText.contains("email") || lowerText.contains("phone"))
            sections.add("contact");
        if (lowerText.contains("summary") || lowerText.contains("objective"))
            sections.add("summary");

        return sections;
    }

    // Analyze which skills are present
    private Map<String, Boolean> analyzeSkills(String text) {
        String lowerText = text.toLowerCase();
        Map<String, Boolean> skillsFound = new HashMap<>();

        TECH_SKILLS.forEach(skill -> skillsFound.put(skill, lowerText.contains(skill)));

        return skillsFound;
    }
}
