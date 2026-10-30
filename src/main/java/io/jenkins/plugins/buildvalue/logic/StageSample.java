package io.jenkins.plugins.buildvalue.logic;

public final class StageSample {
    public final String name;
    public final long durationMs;
    public final String status;

    public StageSample(String name, long durationMs, String status) {
        this.name = name == null || name.isBlank() ? "Unnamed stage" : name;
        this.durationMs = Math.max(0, durationMs);
        this.status = status == null ? "OTHER" : status;
    }

    public boolean skipped() {
        return "NOT_EXECUTED".equals(status) || "SKIPPED".equals(status) || "NOT_BUILT".equals(status);
    }

    public boolean failed() {
        return "FAILED".equals(status) || "FAILURE".equals(status);
    }
}
