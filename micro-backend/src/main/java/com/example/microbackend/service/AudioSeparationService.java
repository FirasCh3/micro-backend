package com.example.microbackend.service;

import com.example.microbackend.config.SeparationProperties;
import com.example.microbackend.config.UploadProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class AudioSeparationService {

    private final SeparationProperties separationProperties;
    private final Path projectDirectory;
    private final Path resultDirectory;

    public AudioSeparationService(SeparationProperties separationProperties, UploadProperties uploadProperties) {
        this.separationProperties = separationProperties;
        this.projectDirectory = resolveProjectDirectory();
        this.resultDirectory = resolvePath(uploadProperties.getResultDirectory(), projectDirectory);
    }

    public void process(Path inputFile) {
        if (!separationProperties.isEnabled()) {
            return;
        }

        Path pythonExecutable = resolvePath(separationProperties.getPythonExecutable(), projectDirectory);
        Path scriptPath = resolvePath(separationProperties.getScriptPath(), projectDirectory);
        Path cacheDirectory = resolvePath(separationProperties.getModelCacheDirectory(), projectDirectory);

        List<String> command = new ArrayList<>();
        command.add(pythonExecutable.toString());
        command.add(scriptPath.toString());
        command.add("--input");
        command.add(inputFile.toString());
        command.add("--output-dir");
        command.add(resultDirectory.toString());
        command.add("--model-id");
        command.add(separationProperties.getModelId());
        command.add("--cache-dir");
        command.add(cacheDirectory.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        if (scriptPath.getParent() != null) {
            processBuilder.directory(scriptPath.getParent().toFile());
        }

        try {
            Process process = processBuilder.start();
            String output = readProcessOutput(process);
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new AudioProcessingException("Audio separation failed: " + output);
            }
        } catch (IOException exception) {
            throw new AudioProcessingException("Unable to start audio separation", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AudioProcessingException("Audio separation was interrupted", exception);
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!output.isEmpty()) {
                    output.append(System.lineSeparator());
                }
                output.append(line);
            }
        }

        return output.toString();
    }

    private Path resolvePath(String path, Path baseDirectory) {
        Path configuredPath = Path.of(path);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }

        return baseDirectory.resolve(path).normalize();
    }

    private Path resolveProjectDirectory() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();

        Path currentDirectory = workingDirectory;
        while (currentDirectory != null) {
            candidates.add(currentDirectory.normalize());
            candidates.add(currentDirectory.resolve("micro-backend").normalize());
            candidates.add(currentDirectory.resolve("micro-backend").resolve("micro-backend").normalize());
            currentDirectory = currentDirectory.getParent();
        }

        return candidates.stream()
                .filter(this::isBackendProjectDirectory)
                .findFirst()
                .orElse(workingDirectory);
    }

    private boolean isBackendProjectDirectory(Path candidate) {
        return Files.isRegularFile(candidate.resolve("pom.xml"))
                && Files.isDirectory(candidate.resolve("src").resolve("main").resolve("java"));
    }
}
