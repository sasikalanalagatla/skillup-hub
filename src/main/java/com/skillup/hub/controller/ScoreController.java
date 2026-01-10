package com.skillup.hub.controller;

import com.skillup.hub.model.Score;
import com.skillup.hub.model.Suggestion;
import com.skillup.hub.repository.SuggestionRepository;
import com.skillup.hub.service.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;
    private final SuggestionRepository suggestionRepository;

    @PostMapping("/resume/{resumeId}/score")
    public String scoreResume(
            @PathVariable String resumeId,
            @RequestParam(value = "jobTitle", required = false) String jobTitle,
            @RequestParam(value = "jobUrl", required = false) String jobUrl,
            @RequestParam(value = "jobDescription", required = false) String jobDescription,
            RedirectAttributes redirectAttributes) {

        try {
            String jobInfo = (jobDescription != null && !jobDescription.trim().isEmpty())
                    ? jobDescription
                    : (jobTitle != null ? jobTitle : jobUrl);
            if (jobInfo == null || jobInfo.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please provide a job title or URL");
                return "redirect:/resume/" + resumeId;
            }

            // Score against job info
            Score score = scoreService.scoreResume(UUID.fromString(resumeId), null, jobInfo);

            redirectAttributes.addFlashAttribute("message",
                    "Resume scored! Overall: " + String.format("%.1f", score.getOverallScore()) + "/100");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to score resume: " + e.getMessage());
        }

        return "redirect:/resume/" + resumeId;
    }

    @GetMapping("/api/score/{scoreId}/suggestions")
    @ResponseBody
    public List<Suggestion> getSuggestions(@PathVariable String scoreId) {
        return suggestionRepository.findByScoreIdOrderByPriorityAsc(UUID.fromString(scoreId));
    }
}
