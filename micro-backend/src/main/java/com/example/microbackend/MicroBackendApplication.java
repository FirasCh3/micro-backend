package com.example.microbackend;

import com.example.microbackend.config.UploadProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(UploadProperties.class)
public class MicroBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicroBackendApplication.class, args);
    }

}
