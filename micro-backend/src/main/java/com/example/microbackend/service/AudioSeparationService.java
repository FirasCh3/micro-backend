package com.example.microbackend.service;

import com.example.microbackend.config.SeparationProperties;
import com.example.microbackend.config.UploadProperties;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class AudioSeparationService {

    private final SeparationProperties separationProperties;
    private final Path resultDirectory;

    public AudioSeparationService(SeparationProperties separationProperties, UploadProperties uploadProperties) {
        this.separationProperties = separationProperties;
        this.resultDirectory = Path.of(uploadProperties.getResultDirectory()).toAbsolutePath().normalize();
    }

    public void process(Path inputFile) {
        if (!separationProperties.isEnabled()) {
            return;
        }

        Path scriptPath = Path.of(separationProperties.getScriptPath()).toAbsolutePath().normalize();
        Path cacheDirectory = Path.of(separationProperties.getModelCacheDirectory()).toAbsolutePath().normalize();

        List<String> command = new ArrayList<>();
        command.add(separationProperties.getPythonExecutable());
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
}
