package io.jenkins.plugins.buildvalue.logic;

import java.util.List;
import java.util.Locale;

/** One finished or running build, plus the stages Pipeline Stage View reported. */
public final class BuildSample {
    public final String jobFullName;
    public final String jobUrl;
    public final String buildUrl;
    public final String displayName;
    public final int number;
    public final long startMs;
    public final long durationMs;
    public final long queueMs;
    public final String result;
    public final String trigger;
    public final String actor;
    public final List<StageSample> stages;

    public BuildSample(
            String jobFullName,
            String jobUrl,
            String buildUrl,
            String displayName,
            int number,
            long startMs,
            long durationMs,
            long queueMs,
            String result,
            String trigger,
            String actor,
            List<StageSample> stages) {
        this.jobFullName = jobFullName == null ? "" : jobFullName;
        this.jobUrl = jobUrl == null ? "" : jobUrl;
        this.buildUrl = buildUrl == null ? "" : buildUrl;
        this.displayName = displayName == null ? "" : displayName;
        this.number = number;
        this.startMs = startMs;
        this.durationMs = Math.max(0, durationMs);
        this.queueMs = Math.max(0, queueMs);
        this.result = result == null ? "RUNNING" : result;
        this.trigger = trigger == null ? "other" : trigger;
        this.actor = actor == null ? "" : actor;
        this.stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public boolean running() {
        return "RUNNING".equals(result);
    }

    public String label() {
        String marker = "#" + number;
        if (displayName.isBlank() || displayName.equals(marker)) {
            return marker;
        }
        if (displayName.startsWith("#")) {
            return displayName;
        }
        return marker + " " + displayName;
    }

    public static String shortName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "";
        }
        int slash = fullName.lastIndexOf('/');
        return slash < 0 ? fullName : fullName.substring(slash + 1);
    }

    /** package = LADM 整包, test = LADM 测试包, other = 其余任务. */
    public static String group(String fullName) {
        String name = shortName(fullName).toLowerCase(Locale.ROOT);
        boolean ladm = name.contains("ladm");
        boolean test = name.contains("test");
        if (ladm && test) {
            return "test";
        }
        if (ladm) {
            return "package";
        }
        return "other";
    }
}
