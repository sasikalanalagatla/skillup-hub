package com.skillup.hub.controller;

import com.skillup.hub.model.Resume;
import com.skillup.hub.model.Score;
import com.skillup.hub.model.Suggestion;
import com.skillup.hub.model.User;
import com.skillup.hub.repository.SuggestionRepository;
import com.skillup.hub.repository.UserRepository;
import com.skillup.hub.service.ResumeService;
import com.skillup.hub.service.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final UserRepository userRepository;
    private final ScoreService scoreService;
    private final SuggestionRepository suggestionRepository;

    @GetMapping("/")
    public String index(Model model) {
        List<Resume> resumes = resumeService.getAllActiveResumes();
        model.addAttribute("resumes", resumes);
        return "index";
    }

    @PostMapping("/upload")
    public String uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "privacyLevel", defaultValue = "private") String privacyLevel,
            RedirectAttributes redirectAttributes) {

        try {
            // For MVP, create or get user by email (simplified auth)
            User user = userRepository.findByEmail(email != null ? email : "guest@example.com")
                    .orElseGet(() -> {
                        User newUser = new User();
                        newUser.setEmail(email != null ? email : "guest@example.com");
                        newUser.setDisplayName("Guest User");
                        newUser.setCreatedAt(Instant.now());
                        return userRepository.save(newUser);
                    });

            Resume resume = resumeService.uploadResume(file, user, privacyLevel);

            redirectAttributes.addFlashAttribute("message", "Resume uploaded successfully!");
            redirectAttributes.addFlashAttribute("resumeId", resume.getId());

            return "redirect:/resume/" + resume.getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to upload resume: " + e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/resume/{id}")
    public String viewResume(@PathVariable String id, Model model) {
        Resume resume = resumeService.getResumeById(UUID.fromString(id));
        Score score = scoreService.getLatestScoreForResume(resume.getId());

        List<Suggestion> suggestions = (score != null)
                ? suggestionRepository.findByScoreIdOrderByPriorityAsc(score.getId())
                : Collections.emptyList();

        model.addAttribute("resume", resume);
        model.addAttribute("score", score);
        model.addAttribute("suggestions", suggestions);

        return "resume-detail";
    }

    @PostMapping("/resume/{id}/delete")
    public String deleteResume(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            resumeService.deleteResume(java.util.UUID.fromString(id));
            redirectAttributes.addFlashAttribute("message", "Resume deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete resume: " + e.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/resume/{id}/suggestions")
    public String viewSuggestions(@PathVariable String id, Model model) {
        Resume resume = resumeService.getResumeById(UUID.fromString(id));
        Score score = scoreService.getLatestScoreForResume(resume.getId());

        List<Suggestion> suggestions = (score != null)
                ? suggestionRepository.findByScoreIdOrderByPriorityAsc(score.getId())
                : Collections.emptyList();

        model.addAttribute("resume", resume);
        model.addAttribute("score", score);
        model.addAttribute("suggestions", suggestions);

        return "suggestions";  // → templates/suggestions.html
    }
}
