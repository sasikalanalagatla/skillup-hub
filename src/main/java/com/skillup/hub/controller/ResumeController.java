package com.skillup.hub.controller;

import com.skillup.hub.model.Resume;
import com.skillup.hub.model.Score;
import com.skillup.hub.model.Suggestion;
import com.skillup.hub.model.User;
import com.skillup.hub.repository.SuggestionRepository;
import com.skillup.hub.repository.UserRepository;
import com.skillup.hub.service.ResumeService;
import com.skillup.hub.service.ScoreService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final UserRepository userRepository;
    private final ScoreService scoreService;
    private final SuggestionRepository suggestionRepository;

    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");
        List<Resume> resumes;

        if (user != null) {
            List<Resume> userResumes = resumeService.getUserResumes(user.getId());
            
            if (user.isGuest()) {
                resumes = userResumes.stream()
                        .sorted(Comparator.comparing(Resume::getUploadAt).reversed())
                        .limit(1)
                        .collect(Collectors.toList());
            } else {
                resumes = userResumes;
            }
        } else {
            resumes = Collections.emptyList();
        }

        model.addAttribute("resumes", resumes);
        return "index";
    }

    @PostMapping("/upload")
    public String uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "privacyLevel", defaultValue = "private") String privacyLevel,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            User user = (User) session.getAttribute("user");

            if (user == null) {
                String userEmail = (email != null && !email.isEmpty()) ? email : "guest@example.com";
                
                User finalUser = userRepository.findByEmail(userEmail)
                        .orElseGet(() -> {
                            User newUser = new User();
                            newUser.setEmail(userEmail);
                            newUser.setDisplayName("Guest User");
                            newUser.setGuest(true);
                            newUser.setCreatedAt(Instant.now());
                            return userRepository.save(newUser);
                        });

                if (!finalUser.isGuest()) {
                    redirectAttributes.addFlashAttribute("error",
                            "This email belongs to a registered account. Please log in.");
                    return "redirect:/login";
                }

                if (finalUser.getGuestAttemptCount() >= 3) {
                    redirectAttributes.addFlashAttribute("error",
                            "You have reached the maximum of 3 resume analysis attempts as a guest." +
                                    " Please log in or sign up.");
                    return "redirect:/login";
                }

                finalUser.setGuestAttemptCount(finalUser.getGuestAttemptCount() + 1);
                user = userRepository.save(finalUser);
                
                session.setAttribute("user", user);
            }
            Resume resume = resumeService.uploadResume(file, user, privacyLevel);

            redirectAttributes.addFlashAttribute("message", "Resume uploaded successfully!");
            redirectAttributes.addFlashAttribute("resumeId", resume.getId());

            return "redirect:/resume/" + resume.getId();

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to upload resume: " +
                    e.getMessage());
            return "redirect:/";
        }
    }

    @GetMapping("/resume/{id}")
    public String viewResume(@PathVariable String id, Model model, HttpSession session,
                             RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        
        if (user == null) {
            redirectAttributes.addFlashAttribute("error",
                    "Please provide your email or log in to view this resume.");
            return "redirect:/";
        }

        try {
            Resume resume = resumeService.getResumeById(UUID.fromString(id));
            
            if (!resume.getUser().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("error",
                        "You do not have permission to view this resume.");
                return "redirect:/";
            }
            
            if (user.isGuest()) {
                List<Resume> userResumes = resumeService.getUserResumes(user.getId());
                Resume latestResume = userResumes.stream()
                        .max(Comparator.comparing(Resume::getUploadAt))
                        .orElse(null);
                
                if (latestResume != null && !latestResume.getId().equals(resume.getId())) {
                     redirectAttributes.addFlashAttribute("error",
                             "Guests can only view their most recent resume.");
                     return "redirect:/resume/" + latestResume.getId();
                }
            }

            Score score = scoreService.getLatestScoreForResume(resume.getId());

            List<Suggestion> suggestions = (score != null)
                    ? suggestionRepository.findByScoreIdOrderByPriorityAsc(score.getId())
                    : Collections.emptyList();

            model.addAttribute("resume", resume);
            model.addAttribute("score", score);
            model.addAttribute("suggestions", suggestions);

            return "resume-detail";
        } catch (Exception e) {
             redirectAttributes.addFlashAttribute("error", "Resume not found.");
             return "redirect:/";
        }
    }

    @PostMapping("/resume/{id}/delete")
    public String deleteResume(@PathVariable String id, RedirectAttributes redirectAttributes, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";

        try {
            Resume resume = resumeService.getResumeById(UUID.fromString(id));
            if (!resume.getUser().getId().equals(user.getId())) {
                 redirectAttributes.addFlashAttribute("error", "Permission denied.");
                 return "redirect:/";
            }
            
            resumeService.deleteResume(java.util.UUID.fromString(id));
            redirectAttributes.addFlashAttribute("message", "Resume deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete resume: " + e.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/resume/{id}/suggestions")
    public String viewSuggestions(@PathVariable String id, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";
        
        Resume resume = resumeService.getResumeById(UUID.fromString(id));
        if (!resume.getUser().getId().equals(user.getId())) {
             return "redirect:/";
        }
        
        if (user.isGuest()) {
             List<Resume> userResumes = resumeService.getUserResumes(user.getId());
             Resume latestResume = userResumes.stream().max(Comparator.comparing(Resume::getUploadAt))
                     .orElse(null);
             if (latestResume != null && !latestResume.getId().equals(resume.getId())) {
                 return "redirect:/resume/" + latestResume.getId();
             }
        }

        Score score = scoreService.getLatestScoreForResume(resume.getId());

        List<Suggestion> suggestions = (score != null)
                ? suggestionRepository.findByScoreIdOrderByPriorityAsc(score.getId())
                : Collections.emptyList();

        model.addAttribute("resume", resume);
        model.addAttribute("score", score);
        model.addAttribute("suggestions", suggestions);

        return "suggestions";
    }
}
