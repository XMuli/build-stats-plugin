package io.jenkins.plugins.buildvalue.preview;

import io.jenkins.plugins.buildvalue.logic.BuildSample;
import io.jenkins.plugins.buildvalue.logic.BuildValueComputer;
import io.jenkins.plugins.buildvalue.logic.Dashboard;
import io.jenkins.plugins.buildvalue.logic.PageHtml;
import io.jenkins.plugins.buildvalue.logic.StageSample;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builds preview/index.html and checks the totals against a tiny fixed history. */
public final class Generate {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String HOST = "http://127.0.0.1:8080";
    private static final String[] PACKAGE_STAGES = {
        "Init Git",
        "Verify Signing Capability",
        "Checkout",
        "Resolve Version",
        "Restore Nuget",
        "Patch PIN Mode Signing",
        "Patch Store Mode Signing",
        "Prepare Resources",
        "Compile",
        "Package Artifacts",
        "Disable Signing",
        "Build",
        "Declarative: Post Actions"
    };
    private static final long[] PACKAGE_BASE = {
        100, 15_000, 90_000, 30_000, 80_000, 40_000, 5_000, 10_000, 90_000, 10_000, 5_000, 720_000, 30_000
    };
    private static final String[] TEST_STAGES = {
        "Initialize",
        "Validate Parameters",
        "Pre check Signing",
        "Checkout Repositories",
        "Resolve Build Metadata",
        "Restore Nuget Packages",
        "Prepare UDC Packages",
        "Prepare Resources",
        "Compile Modules",
        "Package"
    };
    private static final long[] TEST_BASE = {
        2_000, 20_000, 15_000, 70_000, 40_000, 80_000, 30_000, 40_000, 280_000, 40_000
    };

    private Generate() {}

    public static void main(String[] args) throws Exception {
        check();
        long now = ZonedDateTime.of(2026, 10, 21, 16, 40, 0, 0, ZONE).toInstant().toEpochMilli();
        String banner = "这是编排出来的画面预览。任务名和阶段名是示例。安装到 Jenkins 后，同一页改从真实构建记录现算。";
        Dashboard dashboard = BuildValueComputer.compute(story(), now, 30, ZONE, 15, banner);
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
                  body { margin: 0; background: #eef2f4; }
                  main { max-width: 1180px; margin: 0 auto; padding: 24px 20px 48px; }
                  </style>
                </head>
                <body><main>
                %s
                </main></body></html>
                """.formatted(css, PageHtml.body(dashboard));
        Path out = Path.of("preview/index.html");
        Files.createDirectories(out.getParent());
        Files.writeString(out, html, StandardCharsets.UTF_8);
        System.out.println("wrote " + out.toAbsolutePath());
    }

    private static void check() {
        if (!"package".equals(BuildSample.group("folder/sample-ladm"))) {
            throw new AssertionError("整包分组");
        }
        if (!"test".equals(BuildSample.group("sample-ladm-test"))) {
            throw new AssertionError("测试包分组");
        }
        if (!"other".equals(BuildSample.group("sample-other"))) {
            throw new AssertionError("其它分组");
        }
        long now = ZonedDateTime.of(2026, 10, 21, 16, 0, 0, 0, ZONE).toInstant().toEpochMilli();
        List<StageSample> ok = List.of(
                new StageSample("Checkout", 90_000, "SUCCESS"),
                new StageSample("Build", 18 * 60_000L - 90_000, "SUCCESS"));
        List<StageSample> bad = List.of(
                new StageSample("Checkout", 90_000, "SUCCESS"),
                new StageSample("Build", 12 * 60_000L - 90_000, "FAILED"),
                new StageSample("Declarative: Post Actions", 0, "NOT_EXECUTED"));
        List<BuildSample> samples = List.of(
                sample("sample-ladm", 71, "2.0.1.19-QA-signed", now - 3_600_000, 18 * 60_000L, "SUCCESS", "scm", ok),
                sample("sample-ladm", 70, "2.0.1.18-QA-signed", now - 86_400_000L, 18 * 60_000L, "SUCCESS", "scm", ok),
                sample("sample-ladm", 64, "2.0.1.12-QA-signed", now - 2 * 86_400_000L, 12 * 60_000L, "FAILURE", "manual", bad),
                sample("sample-<ladm>", 1, "<script>", now - 4_000_000, 60_000, "SUCCESS", "scm", List.of()),
                sample("sample-other", 3, "", now - 5_000_000, 7 * 60_000L, "SUCCESS", "timer", List.of()),
                sample("sample-ladm", 10, "", now - 40 * 86_400_000L, 20 * 60_000L, "FAILURE", "timer", List.of()));
        Dashboard dashboard = BuildValueComputer.compute(samples, now, 30, ZONE, 15, null);
        String html = PageHtml.body(dashboard);
        require(html.contains("共 5 次"), html);
        require(html.contains("机器执行 56 分"), html);
        require(html.contains("失败构建占用 12 分"), html);
        require(html.contains("sample-&lt;ladm&gt;"), html);
        require(html.contains("&lt;script&gt;"), html);
        require(!html.contains("<script>"), html);
        require(!html.contains("<ladm>"), html);
        require(html.contains("sample-other"), html);
        require(html.contains("10-21 · 5"), html);
        require(html.contains("Build"), html);
    }

    private static void require(boolean ok, String html) {
        if (!ok) {
            throw new AssertionError(html.substring(0, Math.min(html.length(), 1500)));
        }
    }

    private static List<BuildSample> story() {
        Random random = new Random(20261021);
        List<BuildSample> all = new ArrayList<>();
        addPackageHistory(all, random);
        addTestHistory(all, random);
        addOthers(all, random);
        return all;
    }

    private static void addPackageHistory(List<BuildSample> all, Random random) {
        LocalDate day = LocalDate.of(2026, 8, 24);
        int number = 30;
        while (day.isBefore(LocalDate.of(2026, 10, 20)) && number < 63) {
            boolean weekend = day.getDayOfWeek().getValue() >= 6;
            if (!weekend || day.getDayOfMonth() % 5 == 0) {
                number++;
                boolean fail = number % 11 == 0;
                ZonedDateTime when = day.atTime(9 + (number % 6), 10 + random.nextInt(40)).atZone(ZONE);
                all.add(pipeline(
                        "sample-ladm", number, "2.0.1." + Math.max(1, number - 52) + "-QA-signed",
                        when, fail ? "FAILURE" : "SUCCESS", number % 8 == 0 ? "manual" : "scm",
                        packageStages(random, fail), random));
            }
            day = day.plusDays(1);
        }
        addFixedPackage(all, random, 64, "2.0.1.12-QA-signed", "2026-10-20T15:37", true, "scm");
        addFixedPackage(all, random, 65, "2.0.1.13-QA-signed", "2026-10-20T16:57", false, "scm");
        addFixedPackage(all, random, 66, "2.0.1.14-QA-signed", "2026-10-20T17:18", false, "scm");
        addFixedPackage(all, random, 67, "2.0.1.15-QA-signed", "2026-10-20T17:51", false, "timer");
        addFixedPackage(all, random, 68, "2.0.1.16-QA-signed", "2026-10-21T06:00", false, "timer");
        addFixedPackage(all, random, 69, "2.0.1.17-QA-signed", "2026-10-21T09:28", false, "scm");
        addFixedPackage(all, random, 70, "2.0.1.18-QA-signed", "2026-10-21T12:50", false, "scm");
        addFixedPackage(all, random, 71, "2.0.1.19-QA-signed", "2026-10-21T14:12", false, "manual");
    }

    private static void addFixedPackage(
            List<BuildSample> all, Random random, int number, String version, String when, boolean fail, String trigger) {
        all.add(pipeline(
                "sample-ladm", number, version, ZonedDateTime.parse(when + ":00+08:00"),
                fail ? "FAILURE" : "SUCCESS", trigger, packageStages(random, fail), random));
    }

    private static void addTestHistory(List<BuildSample> all, Random random) {
        String[] results = {"FAILURE", "ABORTED", "SUCCESS", "FAILURE", "SUCCESS", "SUCCESS", "FAILURE", "SUCCESS", "SUCCESS", "SUCCESS"};
        int[] failAt = {3, -1, -1, 4, -1, -1, 8, -1, -1, -1};
        String[] clocks = {"13:52", "14:06", "14:24", "14:38", "14:41", "14:51", "15:24", "15:29", "15:57", "16:20"};
        for (int i = 0; i < 10; i++) {
            int number = 41 + i;
            String version = "ABORTED".equals(results[i]) || "FAILURE".equals(results[i]) && failAt[i] < 5
                    ? ""
                    : "2.0.1." + (number - 24) + (number == 48 ? "-PROD-signed" : "-QA-signed");
            ZonedDateTime when = ZonedDateTime.parse("2026-10-21T" + clocks[i] + ":00+08:00");
            all.add(pipeline(
                    "sample-ladm-test", number, version, when, results[i],
                    number == 50 ? "manual" : "scm", testStages(random, failAt[i]), random));
        }
        LocalDate day = LocalDate.of(2026, 9, 1);
        int number = 20;
        while (day.isBefore(LocalDate.of(2026, 10, 21)) && number < 40) {
            if (day.getDayOfWeek().getValue() <= 5) {
                number++;
                boolean fail = number % 5 == 0;
                ZonedDateTime when = day.atTime(11, number % 50).atZone(ZONE);
                all.add(pipeline(
                        "sample-ladm-test", number, "2.0.1." + Math.max(1, number - 24) + "-QA-signed",
                        when, fail ? "FAILURE" : "SUCCESS", "scm", testStages(random, fail ? 8 : -1), random));
            }
            day = day.plusDays(1);
        }
    }

    private static void addOthers(List<BuildSample> all, Random random) {
        addPlain(all, "sample-other", 16, 7 * 60_000L + 18_000, "2026-10-21T13:10", "SUCCESS", "scm", random);
        addPlain(all, "sample-other", 15, 7 * 60_000L, "2026-10-18T10:00", "SUCCESS", "timer", random);
        addPlain(all, "sample-other", 7, 9 * 60_000L, "2026-10-16T10:00", "FAILURE", "scm", random);
        addPlain(all, "sample-sdk", 11, 3 * 60_000L + 50_000, "2026-10-21T15:30", "SUCCESS", "scm", random);
        addPlain(all, "sample-sdk", 10, 4 * 60_000L, "2026-10-15T15:00", "SUCCESS", "timer", random);
        addPlain(all, "sample-desktop", 15, 14 * 60_000L, "2026-10-19T16:20", "SUCCESS", "scm", random);
        addPlain(all, "sample-desktop", 12, 16 * 60_000L, "2026-10-16T11:00", "FAILURE", "manual", random);
        addPlain(all, "sample-lib", 26, 51_000, "2026-10-16T09:40", "FAILURE", "scm", random);
        addPlain(all, "sample-lib", 27, 48_000, "2026-10-20T09:10", "SUCCESS", "timer", random);
    }

    private static void addPlain(
            List<BuildSample> all, String job, int number, long duration, String when, String result, String trigger, Random random) {
        all.add(sample(job, number, "", ZonedDateTime.parse(when + ":00+08:00").toInstant().toEpochMilli(),
                duration, result, trigger, List.of(), 8_000 + random.nextInt(20_000)));
    }

    private static BuildSample pipeline(
            String job, int number, String version, ZonedDateTime when, String result, String trigger,
            List<StageSample> stages, Random random) {
        long duration = 0;
        for (StageSample stage : stages) {
            if (!stage.skipped()) {
                duration += stage.durationMs;
            }
        }
        return sample(job, number, version, when.toInstant().toEpochMilli(), duration, result, trigger, stages,
                4_000 + random.nextInt(30_000));
    }

    private static List<StageSample> packageStages(Random random, boolean fail) {
        List<StageSample> stages = new ArrayList<>();
        for (int i = 0; i < PACKAGE_STAGES.length; i++) {
            if (fail && "Build".equals(PACKAGE_STAGES[i])) {
                stages.add(new StageSample(PACKAGE_STAGES[i], 8 * 60_000L + random.nextInt(90_000), "FAILED"));
                for (int j = i + 1; j < PACKAGE_STAGES.length; j++) {
                    stages.add(new StageSample(PACKAGE_STAGES[j], 0, "NOT_EXECUTED"));
                }
                return stages;
            }
            stages.add(new StageSample(PACKAGE_STAGES[i], jitter(PACKAGE_BASE[i], random), "SUCCESS"));
        }
        return stages;
    }

    private static List<StageSample> testStages(Random random, int failAt) {
        List<StageSample> stages = new ArrayList<>();
        for (int i = 0; i < TEST_STAGES.length; i++) {
            if (failAt == i) {
                stages.add(new StageSample(TEST_STAGES[i], Math.max(5_000, jitter(TEST_BASE[i], random) / 2), "FAILED"));
                for (int j = i + 1; j < TEST_STAGES.length; j++) {
                    stages.add(new StageSample(TEST_STAGES[j], 0, "NOT_EXECUTED"));
                }
                return stages;
            }
            stages.add(new StageSample(TEST_STAGES[i], jitter(TEST_BASE[i], random), "SUCCESS"));
        }
        return stages;
    }

    private static long jitter(long base, Random random) {
        double scale = 0.9 + random.nextDouble() * 0.2;
        return Math.max(1, Math.round(base * scale));
    }

    private static BuildSample sample(
            String job, int number, String version, long start, long duration, String result, String trigger,
            List<StageSample> stages) {
        return sample(job, number, version, start, duration, result, trigger, stages, 0);
    }

    private static BuildSample sample(
            String job, int number, String version, long start, long duration, String result, String trigger,
            List<StageSample> stages, long queue) {
        return new BuildSample(
                job,
                HOST + "/job/" + job + "/",
                HOST + "/job/" + job + "/" + number + "/",
                version,
                number,
                start,
                duration,
                queue,
                result,
                trigger,
                "",
                stages);
    }
}
