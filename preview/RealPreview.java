package io.jenkins.plugins.buildvalue.preview;

import io.jenkins.plugins.buildvalue.logic.BuildSample;
import io.jenkins.plugins.buildvalue.logic.BuildValueComputer;
import io.jenkins.plugins.buildvalue.logic.PageHtml;
import io.jenkins.plugins.buildvalue.logic.StageSample;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Regenerates preview/index.html from exports listed in the gitignored file
 * {@code preview/out/sources.tsv}. Each line is {@code id}, address, banner, and tsv path.
 * Job and account names can be renamed by {@code preview/out/job-aliases.tsv} and
 * {@code preview/out/actor-aliases.tsv}. None of those files belong in the repository.
 */
public final class RealPreview {
    private static String hostBase = "http://127.0.0.1:8080";
    private static final Map<String, String> JOB_ALIASES = aliases(Path.of("preview/out/job-aliases.tsv"));
    private static final Map<String, String> ACTOR_ALIASES = aliases(Path.of("preview/out/actor-aliases.tsv"));

    private RealPreview() {}

    public static void main(String[] args) throws Exception {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        int[] ranges = {7, 30, 90, 0};
        StringBuilder pages = new StringBuilder();
        Path list = Path.of("preview/out/sources.tsv");
        if (!Files.isRegularFile(list)) {
            System.out.println("missing " + list.toAbsolutePath());
            return;
        }
        for (String line : Files.readAllLines(list, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] part = line.split("\t", -1);
            if (part.length < 4) {
                continue;
            }
            pages.append(source(part[0], part[1], part[2], Path.of(part[3]), ranges, zone));
        }
        String css = Files.readString(Path.of("src/main/webapp/css/build-stats.css"), StandardCharsets.UTF_8);
        String html = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>构建统计</title>
                  <style>
                %s
                  body { margin: 0; background: #f4f6f8; }
                  main { margin: 0; padding: 0; max-width: none; }
                  .bv-range[hidden] { display: none !important; }
                  </style>
                </head>
                <body><main>
                %s
                </main>
                <script>
                (function () {
                  var q = new URLSearchParams(location.search);
                  var sources = Array.prototype.slice.call(document.querySelectorAll('.bv-source'));
                  var known = sources.map(function (el) { return el.getAttribute('data-host'); });
                  var host = q.get('host');
                  if (known.indexOf(host) < 0) host = known[0] || '';
                  var days = q.get('days');
                  if (days !== '7' && days !== '30' && days !== '90' && days !== '0') days = '0';
                  document.querySelectorAll('.bv-source').forEach(function (el) {
                    el.hidden = el.getAttribute('data-host') !== host;
                  });
                  document.querySelectorAll('.bv-source:not([hidden]) .bv-range').forEach(function (el) {
                    el.hidden = el.getAttribute('data-days') !== days;
                  });
                })();
                </script>
                </body></html>
                """.formatted(css, pages);
        html = html.replace("/plugin/build-stats/images/", "signs/");
        Path out = Path.of("preview/index.html");
        Files.writeString(out, html, StandardCharsets.UTF_8);
        System.out.println("wrote " + out.toAbsolutePath());
    }

    private static String source(String host, String address, String banner, Path file, int[] ranges, ZoneId zone) throws Exception {
        hostBase = "http://" + address + ":8080";
        List<BuildSample> samples = read(file);
        long now = System.currentTimeMillis();
        long newestEnd = -1;
        BuildSample newest = null;
        for (BuildSample sample : samples) {
            now = Math.max(now, sample.startMs);
            long end = sample.startMs + sample.durationMs;
            if (end >= newestEnd) {
                newestEnd = end;
                newest = sample;
            }
        }
        String last = "";
        if (newest != null) {
            last = "距上次构建 " + ago(Math.max(0, System.currentTimeMillis() - newestEnd))
                    + "  " + newest.jobFullName + " #" + newest.number;
        }
        StringBuilder pages = new StringBuilder();
        pages.append("<div class=\"bv-source\" data-host=\"").append(host).append("\" data-last=\"")
                .append(escape(last)).append("\">");
        for (int days : ranges) {
            var dashboard = BuildValueComputer.compute(samples, now, days, zone, 15, banner + " 地址 " + address + "。");
            pages.append("<div class=\"bv-range\" data-days=\"").append(days).append("\">")
                    .append(PageHtml.body(dashboard, host + "-" + days, host))
                    .append("</div>");
        }
        pages.append("</div>");
        System.out.println(host + " builds " + samples.size());
        return pages.toString();
    }

    private static String ago(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        if (days > 0) {
            return hours == 0 ? days + " 天" : days + " 天 " + hours + " 小时";
        }
        if (hours > 0) {
            return minutes == 0 ? hours + " 小时" : hours + " 小时 " + minutes + " 分";
        }
        return Math.max(0, minutes) + " 分";
    }

    private static String escape(String raw) {
        return raw.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private static List<BuildSample> read(Path file) throws Exception {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
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
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] part = line.split("\t", -1);
            if ("B".equals(part[0])) {
                if (open) {
                    samples.add(sample(job, display, number, start, duration, queue, result, trigger, actor, stages));
                }
                job = aliasJob(part[1]);
                number = Integer.parseInt(part[2]);
                display = part[3];
                start = Long.parseLong(part[4]);
                duration = Long.parseLong(part[5]);
                queue = Long.parseLong(part[6]);
                result = part[7];
                trigger = part[8];
                actor = aliasActor(part.length > 9 ? part[9] : "");
                stages = new ArrayList<>();
                open = true;
            } else if ("S".equals(part[0]) && open) {
                stages.add(new StageSample(part[1], Long.parseLong(part[2]), part[3]));
            }
        }
        if (open) {
            samples.add(sample(job, display, number, start, duration, queue, result, trigger, actor, stages));
        }
        return samples;
    }

    private static BuildSample sample(
            String job, String display, int number, long start, long duration, long queue, String result,
            String trigger, String actor, List<StageSample> stages) {
        String jobUrl = hostBase + "/job/" + job + "/";
        String buildUrl = jobUrl + number + "/";
        return new BuildSample(
                job, jobUrl, buildUrl, display, number, start, duration, queue, result, trigger, actor, stages);
    }

    private static Map<String, String> aliases(Path file) {
        Map<String, String> map = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return map;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] part = line.split("\t", -1);
                if (part.length >= 2) {
                    map.put(part[0], part[1]);
                }
            }
        } catch (IOException ex) {
            return map;
        }
        return map;
    }

    /** Example names for the local preview. The saved build records stay unchanged. */
    private static String aliasJob(String name) {
        return JOB_ALIASES.getOrDefault(name, name);
    }

    private static String aliasActor(String name) {
        return ACTOR_ALIASES.getOrDefault(name, name);
    }
}
