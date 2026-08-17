package com.friopolar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "climapolar.media")
public class MediaStorageProperties {
    private Path uploadDir = Path.of("./data/media");

    public Path getUploadDir() { return uploadDir; }
    public void setUploadDir(Path uploadDir) { this.uploadDir = uploadDir; }
}
