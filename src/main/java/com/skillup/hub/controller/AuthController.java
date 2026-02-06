package com.skillup.hub.controller;

import com.skillup.hub.model.User;
import com.skillup.hub.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            if (user.getPasswordHash() != null && passwordEncoder.matches(password, user.getPasswordHash())) {
                if (user.isGuest()) {
                    user.setGuest(false);
                    userRepository.save(user);
                }
                
                user.setLastLogin(Instant.now());
                userRepository.save(user);
                
                session.setAttribute("user", user);
                redirectAttributes.addFlashAttribute("message", "Logged in successfully!");
                return "redirect:/";
            } else {
                redirectAttributes.addFlashAttribute("error", "Invalid email or password.");
                return "redirect:/login";
            }
        } else {
            redirectAttributes.addFlashAttribute("error", "User not found. Please sign up.");
            return "redirect:/login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
    
    @GetMapping("/signup")
    public String signupPage() {
        return "signup";
    }
    
    @PostMapping("/signup")
    public String signup(@RequestParam String email, @RequestParam String displayName, @RequestParam String password, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        
        User user;
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if (existingUser.isGuest()) {
                user = existingUser;
                user.setDisplayName(displayName);
                user.setPasswordHash(passwordEncoder.encode(password));
                user.setGuest(false);
                user.setLastLogin(Instant.now());
            } else {
                redirectAttributes.addFlashAttribute("error", "Email already in use. Please log in.");
                return "redirect:/signup";
            }
        } else {
            user = new User();
            user.setEmail(email);
            user.setDisplayName(displayName);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setGuest(false);
            user.setCreatedAt(Instant.now());
            user.setLastLogin(Instant.now());
        }
        
        userRepository.save(user);
        session.setAttribute("user", user);
        
        redirectAttributes.addFlashAttribute("message", "Account created successfully!");
        return "redirect:/";
    }
}
