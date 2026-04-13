package com.example.microbackend.service;

import java.nio.file.Path;

public record StoredAudioFile(String filename, long size, Path path) {
}
