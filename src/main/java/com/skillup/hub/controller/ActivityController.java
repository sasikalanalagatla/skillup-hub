package com.skillup.hub.controller;

import com.skillup.hub.model.Activity;
import com.skillup.hub.model.User;
import com.skillup.hub.repository.UserRepository;
import com.skillup.hub.service.impl.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final UserRepository userRepository;  // or use SecurityContext / current user

    @GetMapping("/activity")
    public String showActivityLog(Model model, Principal principal) {
        // Get current user (adjust according to your auth setup)
        String email = principal.getName();  // if using Spring Security with username = email
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        List<Activity> activities = activityService.getUserActivities(user.getId());

        model.addAttribute("activities", activities);
        model.addAttribute("user", user);

        return "user/activity";  // → templates/user/activity.html
    }
}