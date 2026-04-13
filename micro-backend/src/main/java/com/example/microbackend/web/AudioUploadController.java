package com.example.microbackend.web;

import com.example.microbackend.service.AudioStorageException;
import com.example.microbackend.service.AudioStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestController
public class AudioUploadController {

    private final AudioStorageService audioStorageService;

    public AudioUploadController(AudioStorageService audioStorageService) {
        this.audioStorageService = audioStorageService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        audioStorageService.store(file);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Void> handleMissingFile() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(AudioStorageException.class)
    public ResponseEntity<Void> handleStorageFailure() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
