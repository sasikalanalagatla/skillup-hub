package com.skillup.hub.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class AiSuggestionClient {

    private final boolean enabled;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public AiSuggestionClient(
            @Value("${ai.scoring.enabled:false}") boolean enabled,
            @Value("${ai.scoring.apiKey}") String apiKey,
            @Value("${ai.scoring.baseUrl}") String baseUrl,
            @Value("${ai.scoring.model}") String model) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
    }

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public List<SuggestionItem> generateSuggestions(String resumeText, String jobInfo,
            Map<String, Object> scoreDetails) {
        if (!isEnabled()) {
            return Collections.emptyList();
        }
        try {
            String prompt = buildPrompt(resumeText, jobInfo, scoreDetails);
            String systemPrompt = "You are a career coach providing actionable resume improvement suggestions. Return strict JSON.";
            String body = mapper.writeValueAsString(Map.of(
                    "contents", new Object[] {
                            Map.of("parts", new Object[] {
                                    Map.of("text", systemPrompt + "\n\n" + prompt)
                            })
                    },
                    "generationConfig", Map.of("temperature", 0.3)));

// ✅ working for text-bison
            String url = baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() / 100 != 2) {
                log.warn("AI suggestion API returned status {}: {}", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode contentNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                log.warn("AI suggestion API missing content");
                return Collections.emptyList();
            }

            String content = contentNode.asText();
            String json = extractJson(content);
            Map<String, Object> responseMap = mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });

            Object suggestionsObj = responseMap.get("suggestions");
            if (!(suggestionsObj instanceof List)) {
                log.warn("AI response missing suggestions array");
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> suggestionMaps = (List<Map<String, Object>>) suggestionsObj;

            List<SuggestionItem> suggestions = new ArrayList<>();
            for (Map<String, Object> sMap : suggestionMaps) {
                SuggestionItem item = new SuggestionItem();
                item.setCategory(getString(sMap, "category", "other"));
                item.setMessage(getString(sMap, "message", ""));
                item.setPriority(getString(sMap, "priority", "medium"));
                item.setRemediationSteps(getString(sMap, "remediationSteps", ""));
                item.setRecommendationType(getString(sMap, "recommendationType", null));
                item.setProgramName(getString(sMap, "programName", null));
                item.setProgramUrl(getString(sMap, "programUrl", null));
                item.setDuration(getString(sMap, "duration", null));
                item.setCostRange(getString(sMap, "costRange", null));
                suggestions.add(item);
            }

            return suggestions;
        } catch (Exception e) {
            log.warn("AI suggestion generation failed: {}", e.toString());
            return Collections.emptyList();
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    private String buildPrompt(String resumeText, String jobInfo, Map<String, Object> scoreDetails) {
        String template = "You are a resume improvement expert. Analyze the resume against the job and return ONLY valid JSON.\n\n" +
                "Output format: {\"suggestions\": [array of objects]}\n\n" +
                "Each object MUST have exactly these keys:\n" +
                "- category: 'skills' | 'experience' | 'format' | 'keywords' | 'CAREER_GROWTH' | 'other'\n" +
                "- message: string (1-2 sentences explaining the issue or recommendation)\n" +
                "- priority: 'high' | 'medium' | 'low'\n" +
                "- remediationSteps: string (2-5 lines or bullet points of clear actions)\n\n" +
                "MANDATORY RULE FOR CAREER_GROWTH:\n" +
                "If the resume has low experience, no internships, or lacks real-world projects for the job role:\n" +
                "  - ALWAYS include at least 1-2 suggestions with category = 'CAREER_GROWTH'\n" +
                "  - For EVERY 'CAREER_GROWTH' suggestion, YOU MUST fill ALL of these fields (do NOT leave null or empty):\n" +
                "    - recommendationType: 'INTERNSHIP' or 'BOOTCAMP' or 'CERTIFICATE' or 'PROJECT'\n" +
                "    - programName: full official name (e.g. 'Scaler Academy Full-Stack Bootcamp')\n" +
                "    - programUrl: real valid https link to apply or learn more\n" +
                "    - duration: time commitment (e.g. '6-9 months', 'Summer 2026')\n" +
                "    - costRange: cost info (e.g. 'Free', '₹80,000–₹1,20,000', 'Scholarships available')\n" +
                "  - Put the main benefit in 'message'\n" +
                "  - Put detailed steps/eligibility in 'remediationSteps'\n\n" +
                "Be realistic, current (2025-2026), and helpful.\n\n" +
                "JOB INFO:\n%s\n\n" +
                "RESUME TEXT:\n%s\n\n" +
                "SCORE DETAILS:\n%s";

        String scoreDetailsStr = scoreDetails != null ? scoreDetails.toString() : "{}";
        return String.format(template,
                safeTruncate(jobInfo, 5000),
                safeTruncate(resumeText, 10000),
                safeTruncate(scoreDetailsStr, 2000));
    }

    private String safeTruncate(String s, int max) {
        if (s == null)
            return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        // Fallback empty response
        return "{\"suggestions\":[]}";
    }

    public static class SuggestionItem {
        private String category;
        private String message;
        private String priority;
        private String remediationSteps;
        private String recommendationType;
        private String programName;
        private String programUrl;
        private String duration;
        private String costRange;

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getRemediationSteps() {
            return remediationSteps;
        }

        public void setRemediationSteps(String remediationSteps) {
            this.remediationSteps = remediationSteps;
        }

        public String getRecommendationType() { return recommendationType; }
        public void setRecommendationType(String recommendationType) { this.recommendationType = recommendationType; }

        public String getProgramName() { return programName; }
        public void setProgramName(String programName) { this.programName = programName; }

        public String getProgramUrl() { return programUrl; }
        public void setProgramUrl(String programUrl) { this.programUrl = programUrl; }

        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }

        public String getCostRange() { return costRange; }
        public void setCostRange(String costRange) { this.costRange = costRange; }
    }
}
