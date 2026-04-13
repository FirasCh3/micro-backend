package com.example.microbackend.service;

import com.example.microbackend.config.UploadProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class AudioStorageService {

    private static final String DEFAULT_EXTENSION = ".wav";

    private final Path uploadDirectory;

    public AudioStorageService(UploadProperties uploadProperties) {
        this.uploadDirectory = Path.of(uploadProperties.getDirectory()).toAbsolutePath().normalize();
    }

    public StoredAudioFile store(MultipartFile file) {
        try {
            Files.createDirectories(uploadDirectory);

            String storedFilename = generateStoredFilename(file.getOriginalFilename());
            Path targetPath = uploadDirectory.resolve(storedFilename).normalize();

            if (!targetPath.startsWith(uploadDirectory)) {
                throw new AudioStorageException("Invalid upload path", null);
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return new StoredAudioFile(storedFilename, file.getSize(), targetPath);
        } catch (IOException exception) {
            throw new AudioStorageException("Failed to store uploaded audio file", exception);
        }
    }

    private String generateStoredFilename(String originalFilename) {
        String cleanName = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        String extension = extractExtension(cleanName);
        return UUID.randomUUID() + extension;
    }

    private String extractExtension(String filename) {
        int extensionStart = filename.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == filename.length() - 1) {
            return DEFAULT_EXTENSION;
        }

        String extension = filename.substring(extensionStart).toLowerCase(Locale.ROOT);
        return extension.contains("/") || extension.contains("\\") ? DEFAULT_EXTENSION : extension;
    }
}
