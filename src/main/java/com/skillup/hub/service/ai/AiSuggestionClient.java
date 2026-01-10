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
            @Value("${ai.scoring.apiKey:}") String apiKey,
            @Value("${ai.scoring.baseUrl:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${ai.scoring.model:gemini-1.5-pro}") String model) {
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
        String template = "Analyze this resume against the job requirements and provide 3-6 actionable suggestions for improvement.\n\n"
                +
                "Return ONLY a JSON object with key 'suggestions' containing an array of objects with:\n" +
                "- category: 'skills' | 'experience' | 'format' | 'keywords' | 'other'\n" +
                "- message: concise description of the issue (1-2 sentences)\n" +
                "- priority: 'high' | 'medium' | 'low'\n" +
                "- remediationSteps: specific actionable steps (2-4 bullet points)\n\n" +
                "Focus on gaps between the resume and job requirements. Be specific and constructive.\n\n" +
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
    }
}
