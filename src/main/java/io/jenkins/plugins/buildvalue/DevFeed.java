package io.jenkins.plugins.buildvalue;

import io.jenkins.plugins.buildvalue.logic.BuildSample;
import io.jenkins.plugins.buildvalue.logic.StageSample;
import io.jenkins.plugins.buildvalue.logic.StatusSign;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Local {@code hpi:run} has no jobs. When {@code preview/out/samples-159.tsv} exists,
 * the page uses that export. The controller address, if any, is read from the
 * gitignored file {@code preview/out/dev-host.txt}. A normal install never sees these files.
 */
final class DevFeed {
    private static List<BuildSample> cached = List.of();
    private static long cachedStamp = -1;

    private DevFeed() {}

    static List<BuildSample> samples() {
        if (!Boolean.getBoolean("hudson.Main.development")) {
            return List.of();
        }
        Path file = file();
        if (file == null) {
            return List.of();
        }
        try {
            long stamp = Files.getLastModifiedTime(file).toMillis();
            if (stamp == cachedStamp) {
                return cached;
            }
            cached = read(file);
            cachedStamp = stamp;
            return cached;
        } catch (IOException ex) {
            return List.of();
        }
    }

    static StatusSign sign() {
        BuildSample running = null;
        BuildSample newest = null;
        for (BuildSample sample : samples()) {
            if (sample.running() && (running == null || sample.startMs >= running.startMs)) {
                running = sample;
            }
            if (newest == null || sample.startMs >= newest.startMs) {
                newest = sample;
            }
        }
        BuildSample chosen = running != null ? running : newest;
        if (chosen == null) {
            return StatusSign.none();
        }
        return StatusSign.of(chosen.result, chosen.jobFullName, chosen.number, chosen.buildUrl);
    }

    private static Path file() {
        Path direct = Path.of("preview", "out", "samples-159.tsv");
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        String home = System.getProperty("JENKINS_HOME");
        if (home == null || home.isBlank()) {
            home = System.getenv("JENKINS_HOME");
        }
        if (home == null || home.isBlank()) {
            return null;
        }
        Path besideHome = Path.of(home).getParent().resolve(Path.of("preview", "out", "samples-159.tsv"));
        return Files.isRegularFile(besideHome) ? besideHome : null;
    }

    private static String host() {
        Path samples = file();
        if (samples == null) {
            return "";
        }
        Path hostFile = samples.resolveSibling("dev-host.txt");
        if (!Files.isRegularFile(hostFile)) {
            return "";
        }
        try {
            String line = Files.readString(hostFile, StandardCharsets.UTF_8).trim();
            while (line.endsWith("/")) {
                line = line.substring(0, line.length() - 1);
            }
            return line;
        } catch (IOException ex) {
            return "";
        }
    }

    private static List<BuildSample> read(Path file) throws IOException {
        List<BuildSample> samples = new ArrayList<>();
        String job = "";
        String display = "";
        int number = 0;
        long start = 0;
        long duration = 0;
        long queue = 0;
        String result = "SUCCESS";
        String trigger = "other";
        String actor = "";
        List<StageSample> stages = new ArrayList<>();
        boolean open = false;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] part = line.split("\t", -1);
            if ("B".equals(part[0]) && part.length >= 9) {
                if (open) {
                    samples.add(sample(job, display, number, start, duration, queue, result, trigger, actor, stages));
                }
                job = part[1];
                number = Integer.parseInt(part[2]);
                display = part[3];
                start = Long.parseLong(part[4]);
                duration = Long.parseLong(part[5]);
                queue = Long.parseLong(part[6]);
                result = part[7];
                trigger = part[8];
                actor = part.length > 9 ? part[9] : "";
                stages = new ArrayList<>();
                open = true;
            } else if ("S".equals(part[0]) && open && part.length >= 4) {
                stages.add(new StageSample(part[1], Long.parseLong(part[2]), part[3]));
            }
        }
        if (open) {
            samples.add(sample(job, display, number, start, duration, queue, result, trigger, actor, stages));
        }
        return List.copyOf(samples);
    }

    private static BuildSample sample(
            String job, String display, int number, long start, long duration, long queue, String result,
            String trigger, String actor, List<StageSample> stages) {
        String jobUrl = host() + "/job/" + job + "/";
        return new BuildSample(
                job, jobUrl, jobUrl + number + "/", display, number, start, duration, queue, result, trigger, actor,
                stages);
    }
}
