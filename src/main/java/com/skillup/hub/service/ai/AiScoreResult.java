package com.skillup.hub.service.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiScoreResult {
    @JsonProperty("overall")
    private Double overall;

    @JsonProperty("skills")
    private Double skills;

    @JsonProperty("experience")
    private Double experience;

    @JsonProperty("keywords")
    private Double keywords;

    @JsonProperty("formatting")
    private Double formatting;

    @JsonProperty("details")
    private Map<String, Object> details;

    public String detailsAsJson(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(details);
        } catch (Exception e) {
            return details != null ? details.toString() : "{}";
        }
    }
}
