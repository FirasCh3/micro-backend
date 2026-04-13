package com.example.microbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.separation")
public class SeparationProperties {

    private boolean enabled = true;
    private String pythonExecutable = "python";
    private String scriptPath = "scripts/separate_audio.py";
    private String modelId = "speechbrain/sepformer-libri3mix";
    private String modelCacheDirectory = "model-cache/sepformer-libri3mix";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPythonExecutable() {
        return pythonExecutable;
    }

    public void setPythonExecutable(String pythonExecutable) {
        this.pythonExecutable = pythonExecutable;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getModelCacheDirectory() {
        return modelCacheDirectory;
    }

    public void setModelCacheDirectory(String modelCacheDirectory) {
        this.modelCacheDirectory = modelCacheDirectory;
    }
}
