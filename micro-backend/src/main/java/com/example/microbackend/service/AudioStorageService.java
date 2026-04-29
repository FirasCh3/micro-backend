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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class AudioStorageService {

    private static final String DEFAULT_EXTENSION = ".wav";

    private final Path uploadDirectory;
    private final Path resultDirectory;

    public AudioStorageService(UploadProperties uploadProperties) {
        this.uploadDirectory = resolveProjectDirectory(uploadProperties.getDirectory());
        this.resultDirectory = resolveProjectDirectory(uploadProperties.getResultDirectory());
    }

    public StoredAudioFile store(MultipartFile file) {
        try {
            Files.createDirectories(uploadDirectory);
            clearResultFiles();

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

    public Path getLatestResultFile() {
        try {
            if (!Files.exists(resultDirectory)) {
                throw new AudioStorageException("Result files not found");
            }

            try (Stream<Path> resultFiles = Files.list(resultDirectory)) {
                Optional<Path> latestFile = resultFiles
                        .filter(Files::isRegularFile)
                        .max(Comparator.comparing(this::lastModifiedTime));

                return latestFile.orElseThrow(() -> new AudioStorageException("Result files not found"));
            }
        } catch (IOException exception) {
            throw new AudioStorageException("Failed to read result files", exception);
        }
    }

    public Path getLatestUploadFile() {
        try {
            if (!Files.exists(uploadDirectory)) {
                throw new AudioStorageException("Upload file not found");
            }

            try (Stream<Path> uploadFiles = Files.list(uploadDirectory)) {
                Optional<Path> latestFile = uploadFiles
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav"))
                        .max(Comparator.comparing(this::lastModifiedTime));

                return latestFile.orElseThrow(() -> new AudioStorageException("Upload file not found"));
            }
        } catch (IOException exception) {
            throw new AudioStorageException("Failed to read upload files", exception);
        }
    }

    public List<Path> listResultFiles() {
        try {
            if (!Files.exists(resultDirectory)) {
                throw new AudioStorageException("Result files not found");
            }

            try (Stream<Path> resultFiles = Files.list(resultDirectory)) {
                List<Path> files = resultFiles
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(Path::getFileName))
                        .toList();

                if (files.isEmpty()) {
                    throw new AudioStorageException("Result files not found");
                }

                return new ArrayList<>(files);
            }
        } catch (IOException exception) {
            throw new AudioStorageException("Failed to read result files", exception);
        }
    }

    public Path getResultFile(String filename) {
        Path targetPath = resultDirectory.resolve(filename).normalize();

        if (!targetPath.startsWith(resultDirectory) || !Files.isRegularFile(targetPath)) {
            throw new AudioStorageException("Result files not found");
        }

        return targetPath;
    }

    private void clearResultFiles() throws IOException {
        if (!Files.exists(resultDirectory)) {
            return;
        }

        try (Stream<Path> resultFiles = Files.list(resultDirectory)) {
            for (Path resultFile : resultFiles.filter(Files::isRegularFile).toList()) {
                Files.deleteIfExists(resultFile);
            }
        }
    }

    private java.nio.file.attribute.FileTime lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException exception) {
            throw new AudioStorageException("Failed to inspect result file", exception);
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

    private Path resolveProjectDirectory(String directory) {
        Path configuredPath = Path.of(directory);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }

        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();

        Path currentDirectory = workingDirectory;
        while (currentDirectory != null) {
            candidates.add(currentDirectory.resolve(directory).normalize());
            candidates.add(currentDirectory.resolve("micro-backend").resolve(directory).normalize());
            candidates.add(currentDirectory.resolve("micro-backend").resolve("micro-backend").resolve(directory).normalize());
            currentDirectory = currentDirectory.getParent();
        }

        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(candidates.get(0));
    }
}
