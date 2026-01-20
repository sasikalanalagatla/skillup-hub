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
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeRepository resumeRepository;
    private final Tika tika = new Tika();
    private final ActivityService activityService;

    @Override
    public Resume uploadResume(MultipartFile file, User user, String privacyLevel) throws IOException, TikaException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String filename = file.getOriginalFilename();

        // Extract text from PDF/DOCX using Apache Tika
        String extractedText = tika.parseToString(file.getInputStream());

        // TODO: Upload to S3 or save locally - for now just store filename
        String s3Key = "uploads/" + UUID.randomUUID() + "_" + filename;

        Resume resume = new Resume();
        resume.setUser(user);
        resume.setFilename(filename);
        resume.setS3Key(s3Key);
        resume.setTextExtracted(extractedText);
        resume.setPrivacyLevel(privacyLevel != null ? privacyLevel : "private");
        resume.setUploadAt(Instant.now());

        Resume savedResume = resumeRepository.save(resume);

        activityService.logActivity(
                user,
                "RESUME_UPLOADED",
                Map.of(
                        "resumeId", savedResume.getId().toString(),
                        "filename", filename,
                        "fileSizeBytes", file.getSize(),
                        "privacyLevel", privacyLevel != null ? privacyLevel : "private"
                )
        );

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
        User user = resume.getUser();
        resume.setDeletedAt(Instant.now());
        resumeRepository.save(resume);
        activityService.logActivity(
                user,
                "RESUME_DELETED",
                Map.of(
                        "resumeId", id.toString(),
                        "filename", resume.getFilename()
                )
        );
    }
}
