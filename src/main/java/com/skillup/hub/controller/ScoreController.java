package com.skillup.hub.controller;

import com.skillup.hub.model.Score;
import com.skillup.hub.service.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    @PostMapping("/resume/{resumeId}/score")
    public String scoreResume(
            @PathVariable String resumeId,
            RedirectAttributes redirectAttributes) {

        try {
            // Score without job target (general scoring)
            Score score = scoreService.scoreResume(UUID.fromString(resumeId), null);

            redirectAttributes.addFlashAttribute("message",
                    "Resume scored! Overall: " + String.format("%.1f", score.getOverallScore()) + "/100");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to score resume: " + e.getMessage());
        }

        return "redirect:/resume/" + resumeId;
    }
}
