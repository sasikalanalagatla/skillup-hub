package com.skillup.hub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.skillup.hub.model.Resume;
import com.skillup.hub.model.User;
import org.apache.tika.exception.TikaException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface ResumeService {

    Resume uploadResume(MultipartFile file, User user, String privacyLevel) throws IOException, TikaException;

    List<Resume> getUserResumes(UUID userId);

    Resume getResumeById(UUID id);

    List<Resume> getAllActiveResumes();

    void deleteResume(UUID id) throws JsonProcessingException;
}
