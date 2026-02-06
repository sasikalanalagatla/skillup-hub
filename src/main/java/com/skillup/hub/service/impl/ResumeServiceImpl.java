package com.skillup.hub.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.skillup.hub.model.Resume;
import com.skillup.hub.model.User;
import com.skillup.hub.repository.ResumeRepository;
import com.skillup.hub.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeRepository resumeRepository;
    private final Tika tika = new Tika();

    @Override
    public Resume uploadResume(MultipartFile file, User user, String privacyLevel) throws IOException, TikaException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String filename = file.getOriginalFilename();

        String extractedText = tika.parseToString(file.getInputStream());

        String s3Key = "uploads/" + UUID.randomUUID() + "_" + filename;

        Resume resume = new Resume();
        resume.setUser(user);
        resume.setFilename(filename);
        resume.setS3Key(s3Key);
        resume.setTextExtracted(extractedText);
        resume.setPrivacyLevel(privacyLevel != null ? privacyLevel : "private");
        resume.setUploadAt(Instant.now());

        Resume savedResume = resumeRepository.save(resume);

        return savedResume;
    }

    @Override
    public List<Resume> getUserResumes(UUID userId) {
        return resumeRepository.findByUserIdOrderByUploadAtDesc(userId);
    }

    @Override
    public Resume getResumeById(UUID id) {
        return resumeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found: " + id));
    }

    @Override
    public List<Resume> getAllActiveResumes() {
        return resumeRepository.findByDeletedAtIsNullOrderByUploadAtDesc();
    }

    @Override
    public void deleteResume(UUID id) throws JsonProcessingException {
        Resume resume = getResumeById(id);
        resume.setDeletedAt(Instant.now());
        resumeRepository.save(resume);
    }
}
