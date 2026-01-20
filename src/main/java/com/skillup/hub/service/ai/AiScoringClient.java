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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class AiScoringClient {
    private final boolean enabled;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    public AiScoringClient(
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

    public Optional<AiScoreResult> score(String resumeText, String jobInfo) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            String prompt = buildPrompt(resumeText, jobInfo);
            String systemPrompt = "You are a precise resume-job matching and scoring engine. Return strict JSON.";
            String body = mapper.writeValueAsString(Map.of(
                    "contents", new Object[] {
                            Map.of("parts", new Object[] {
                                    Map.of("text", systemPrompt + "\n\n" + prompt)
                            })
                    },
                    "generationConfig", Map.of("temperature", 0)));

// ✅ working for text-bison
            String url = baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + apiKey;
// instead of /v1beta/
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() / 100 != 2) {
                log.warn("AI scoring API returned status {}: {}", response.statusCode(), response.body());
                return Optional.empty();
            }

            // Extract Gemini response content
            JsonNode root = mapper.readTree(response.body());
            JsonNode contentNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                log.warn("AI scoring API missing content");
                return Optional.empty();
            }

            String content = contentNode.asText();
            // Try to locate a JSON object in the content
            String json = extractJson(content);
            Map<String, Object> map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });

            AiScoreResult result = new AiScoreResult();
            result.setOverall(asDouble(map.get("overall")));
            result.setSkills(asDouble(map.get("skills")));
            result.setExperience(asDouble(map.get("experience")));
            result.setKeywords(asDouble(map.get("keywords")));
            result.setFormatting(asDouble(map.get("formatting")));
            Object details = map.get("details");
            if (details instanceof Map) {
                // noinspection unchecked
                result.setDetails((Map<String, Object>) details);
            } else {
                Map<String, Object> fallback = new HashMap<>();
                fallback.put("raw", map);
                result.setDetails(fallback);
            }
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("AI scoring failed: {}", e.toString());
            return Optional.empty();
        }
    }

    private double asDouble(Object v) {
        if (v == null)
            return 0.0;
        if (v instanceof Number n)
            return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private String buildPrompt(String resumeText, String jobInfo) {
        String jobSection = "JOB INFO:\n" + safeTruncate(jobInfo, 6000);
// in buildPrompt()
        String resumeSection = "RESUME TEXT (first 8000 chars):\n" + safeTruncate(resumeText, 8000);
        return "You will score how well a resume matches a job.\n" +
                "Return ONLY a compact JSON object with keys: overall, skills, experience, keywords, formatting, details.\n"
                +
                "- Each score must be a number between 0 and 100.\n" +
                "- overall is a weighted mix: skills 35%, experience 30%, keywords 20%, formatting 15%.\n" +
                "- details should include: matchedSkills[], missingSkills[], inferredRole, experienceYears, sectionsFound[], wordCount, briefNotes.\n\n"
                +
                jobSection + "\n\n" + resumeSection;
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
        // If not found, wrap as minimal JSON
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("overall", 0);
        fallback.put("skills", 0);
        fallback.put("experience", 0);
        fallback.put("keywords", 0);
        fallback.put("formatting", 0);
        fallback.put("details", Map.of("raw", content));
        try {
            return mapper.writeValueAsString(fallback);
        } catch (Exception e) {
            return "{\"overall\":0,\"skills\":0,\"experience\":0,\"keywords\":0,\"formatting\":0,\"details\":{}}";
        }
    }
}
