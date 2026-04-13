package com.example.microbackend.web;

import com.example.microbackend.service.AudioProcessingException;
import com.example.microbackend.service.AudioStorageException;
import com.example.microbackend.service.ResultFileResponse;
import com.example.microbackend.service.AudioStorageService;
import com.example.microbackend.service.AudioSeparationService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@RestController
public class AudioUploadController {

    private final AudioStorageService audioStorageService;
    private final AudioSeparationService audioSeparationService;

    public AudioUploadController(
            AudioStorageService audioStorageService,
            AudioSeparationService audioSeparationService
    ) {
        this.audioStorageService = audioStorageService;
        this.audioSeparationService = audioSeparationService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        Path storedFile = audioStorageService.store(file).path();
        audioSeparationService.process(storedFile);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/result")
    public ResponseEntity<List<ResultFileResponse>> listResults() {
        List<ResultFileResponse> results = audioStorageService.listResultFiles()
                .stream()
                .map(path -> new ResultFileResponse(
                        path.getFileName().toString(),
                        ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/result/")
                                .path(path.getFileName().toString())
                                .toUriString()
                ))
                .toList();

        return ResponseEntity.ok(results);
    }

    @GetMapping("/result/{filename:.+}")
    public ResponseEntity<Resource> streamResult(@PathVariable String filename) {
        Path resultFile = audioStorageService.getResultFile(filename);
        Resource resource = new FileSystemResource(resultFile);
        long contentLength;
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        try {
            contentLength = resource.contentLength();
        } catch (IOException exception) {
            throw new AudioStorageException("Failed to read result files", exception);
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(contentLength)
                .body(resource);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Void> handleMissingFile() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(AudioProcessingException.class)
    public ResponseEntity<String> handleProcessingFailure() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("processing error");
    }

    @ExceptionHandler(AudioStorageException.class)
    public ResponseEntity<String> handleStorageFailure(AudioStorageException exception) {
        if ("Result files not found".equals(exception.getMessage())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("files not found");
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("storage error");
    }
}
