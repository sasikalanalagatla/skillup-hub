package com.skillup.hub.controller;

import com.skillup.hub.model.Resume;
import com.skillup.hub.model.Score;
import com.skillup.hub.model.User;
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
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final UserRepository userRepository;
    private final ScoreService scoreService;

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
        try {
            Resume resume = resumeService.getResumeById(java.util.UUID.fromString(id));
            Score latestScore = scoreService.getLatestScoreForResume(resume.getId());

            model.addAttribute("resume", resume);
            model.addAttribute("score", latestScore);
            return "resume-detail";
        } catch (Exception e) {
            model.addAttribute("error", "Resume not found");
            return "redirect:/";
        }
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
}
