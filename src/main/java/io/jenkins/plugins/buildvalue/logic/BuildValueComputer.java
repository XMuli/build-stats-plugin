package io.jenkins.plugins.buildvalue.logic;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class BuildValueComputer {
    private static final String[] JOB_COLORS = {
        "#50A040", "#3479B0", "#F08048", "#3C74A3", "#A2C7E4", "#9DA0A5"
    };

    private BuildValueComputer() {}

    public static Dashboard compute(
            List<BuildSample> samples, long now, int days, ZoneId zone, int minutesPerFailure, String banner) {
        int safeMinutes = Math.max(0, Math.min(480, minutesPerFailure));
        boolean compare = days > 0;
        long from = compare ? now - days * 86_400_000L : 0L;
        long prevFrom = compare ? from - days * 86_400_000L : 0L;

        List<BuildSample> current = new ArrayList<>();
        List<BuildSample> previous = new ArrayList<>();
        for (BuildSample sample : samples) {
            if (sample.startMs > now) {
                continue;
            }
            if (sample.startMs >= from) {
                current.add(sample);
            } else if (compare && sample.startMs >= prevFrom) {
                previous.add(sample);
            }
        }
        current.sort(Comparator.comparingLong(sample -> sample.startMs));

        Totals all = totals(current);
        Totals prev = totals(previous);
        Totals pack = totals(filter(current, "package"));
        Totals test = totals(filter(current, "test"));
        Totals other = totals(filter(current, "other"));
        boolean empty = current.isEmpty();

        String wasteLine = wasteLine(all, safeMinutes);
        Dashboard.Kpi kpi = kpi(days, all, prev, compare);

        List<String> labels = new ArrayList<>();
        List<Double> counts = new ArrayList<>();
        List<Double> hours = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        List<Double> delivered = new ArrayList<>();
        fillTrend(current, now, zone, labels, counts, hours, rates, delivered);

        Map<String, List<BuildSample>> byJob = new LinkedHashMap<>();
        for (BuildSample sample : current) {
            byJob.computeIfAbsent(sample.jobFullName, key -> new ArrayList<>()).add(sample);
        }

        List<Dashboard.JobPanel> tabs = allPanels(byJob, previous, now, days, zone, compare);
        List<Dashboard.JobRow> rows = pipelineRows(byJob, zone);
        Ledger ledger = timeLedger(current, now, zone);
        List<Dashboard.PersonRow> people = peopleOf(current, zone, true);
        String effort = Texts.both(
                headline(days, all, pack, test, prev, false) + " Triggered by " + people.size() + " accounts.",
                headline(days, all, pack, test, prev, true) + "这些构建由 " + people.size() + " 个账号触发。");
        Sign sign = signOf(current);
        Sign home = signOf(samples);

        return new Dashboard(
                days,
                banner,
                effort,
                wasteLine,
                kpi,
                SvgCharts.line("Cumulative builds", labels, counts, "#3479B0", true, ""),
                SvgCharts.line("Machine hours", labels, hours, "#3C74A3", true, "h"),
                SvgCharts.line("Success rate", labels, rates, "#50A040", false, "%"),
                resultPie(all),
                timePie(current),
                triggerPie(all),
                List.of(),
                List.of(),
                otherSummary(other, byJob),
                rows,
                dailyCountsChart(current, now, zone),
                dailyHoursChart(current, now, zone),
                SvgCharts.columns("Time of day", hourLabels(current, zone), hourValues(current, zone), "#3479B0"),
                Integer.toString(all.success),
                Texts.durations(all.failedDuration + all.abortedDuration),
                people,
                tabs,
                ledger.chart,
                ledger.rows,
                methodNote(days, safeMinutes),
                heatJson(samples, zone),
                sign.image,
                sign.alt,
                sign.caption,
                sign.live,
                home.image,
                home.alt,
                home.caption,
                home.live,
                empty);
    }

    private static final class Sign {
        final String image;
        final String alt;
        final String caption;
        final boolean live;

        Sign(StatusSign status) {
            this.image = status.image;
            this.alt = status.alt;
            this.caption = status.caption;
            this.live = status.live;
        }
    }

    /** Newest build in the window. A running build wins over a newer finished one. */
    private static Sign signOf(List<BuildSample> samples) {
        BuildSample running = null;
        BuildSample newest = null;
        for (BuildSample sample : samples) {
            if (sample.running() && (running == null || sample.startMs >= running.startMs)) {
                running = sample;
            }
            if (newest == null || sample.startMs >= newest.startMs) {
                newest = sample;
            }
        }
        BuildSample sample = running != null ? running : newest;
        if (sample == null) {
            return new Sign(StatusSign.none());
        }
        String result = sample.running() ? "RUNNING" : sample.result;
        return new Sign(StatusSign.of(result, sample.jobFullName, sample.number));
    }

    private static String heatJson(List<BuildSample> samples, ZoneId zone) {
        Map<LocalDate, Integer> counts = new TreeMap<>();
        for (BuildSample sample : samples) {
            if (sample.startMs <= 0) {
                continue;
            }
            LocalDate day = Instant.ofEpochMilli(sample.startMs).atZone(zone).toLocalDate();
            counts.merge(day, 1, Integer::sum);
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<LocalDate, Integer> entry : counts.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey()).append("\":").append(entry.getValue());
        }
        json.append('}');
        return json.toString();
    }

    private static void fillTrend(
            List<BuildSample> current,
            long now,
            ZoneId zone,
            List<String> labels,
            List<Double> counts,
            List<Double> hours,
            List<Double> rates,
            List<Double> delivered) {
        if (current.isEmpty()) {
            return;
        }
        LocalDate start = Instant.ofEpochMilli(current.get(0).startMs).atZone(zone).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        long span = ChronoUnit.DAYS.between(start, end) + 1;
        int step = span > 120 ? 7 : 1;
        for (LocalDate bucket = start; !bucket.isAfter(end); bucket = bucket.plusDays(step)) {
            long bucketStart = bucket.atStartOfDay(zone).toInstant().toEpochMilli();
            long bucketEnd = bucket.plusDays(step).atStartOfDay(zone).toInstant().toEpochMilli();
            int count = 0;
            long duration = 0;
            int bucketDone = 0;
            int bucketOk = 0;
            for (BuildSample sample : current) {
                if (sample.startMs < bucketEnd) {
                    count++;
                    if (!sample.running()) {
                        duration += sample.durationMs;
                    }
                }
                if (sample.startMs >= bucketStart && sample.startMs < bucketEnd && !sample.running()) {
                    bucketDone++;
                    if ("SUCCESS".equals(sample.result)) {
                        bucketOk++;
                    }
                }
            }
            labels.add(Texts.day(bucket));
            counts.add((double) count);
            hours.add(duration / 3_600_000d);
            rates.add(bucketDone == 0 ? null : (100d * bucketOk) / bucketDone);
            delivered.add((double) bucketOk);
        }
    }

    private static Ledger timeLedger(List<BuildSample> samples, long now, ZoneId zone) {
        Ledger ledger = new Ledger();
        if (samples.isEmpty()) {
            ledger.chart = SvgCharts.lines("Cumulative time", List.of(), List.of(), "h");
            ledger.rows = List.of();
            return ledger;
        }
        long oldest = Long.MAX_VALUE;
        for (BuildSample sample : samples) {
            oldest = Math.min(oldest, sample.startMs);
        }
        LocalDate start = Instant.ofEpochMilli(oldest).atZone(zone).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        int step = ChronoUnit.DAYS.between(start, end) + 1 > 120 ? 7 : 1;
        List<String> labels = new ArrayList<>();
        List<Double> totalHours = new ArrayList<>();
        List<Double> successHours = new ArrayList<>();
        List<Double> wasteHours = new ArrayList<>();
        List<Dashboard.CumRow> rows = new ArrayList<>();
        long cum = 0;
        long cumSuccess = 0;
        long cumWaste = 0;
        for (LocalDate bucket = start; !bucket.isAfter(end); bucket = bucket.plusDays(step)) {
            long from = bucket.atStartOfDay(zone).toInstant().toEpochMilli();
            long to = bucket.plusDays(step).atStartOfDay(zone).toInstant().toEpochMilli();
            long period = 0;
            long success = 0;
            long waste = 0;
            for (BuildSample sample : samples) {
                if (sample.running() || sample.startMs < from || sample.startMs >= to || sample.startMs > now) {
                    continue;
                }
                period += sample.durationMs;
                if ("SUCCESS".equals(sample.result)) {
                    success += sample.durationMs;
                } else if ("FAILURE".equals(sample.result) || "ABORTED".equals(sample.result)) {
                    waste += sample.durationMs;
                }
            }
            cum += period;
            cumSuccess += success;
            cumWaste += waste;
            String label = Texts.day(bucket);
            labels.add(label);
            totalHours.add(cum / 3_600_000d);
            successHours.add(cumSuccess / 3_600_000d);
            wasteHours.add(cumWaste / 3_600_000d);
            rows.add(new Dashboard.CumRow(label, Texts.durations(period), Texts.durations(cum), Texts.durations(cumSuccess),
                    Texts.durations(cumWaste)));
        }
        Collections.reverse(rows);
        ledger.rows = List.copyOf(rows);
        ledger.chart = SvgCharts.lines("Cumulative time", labels, List.of(
                new SvgCharts.Series("Total", "#3C74A3", totalHours),
                new SvgCharts.Series("Success", "#50A040", successHours),
                new SvgCharts.Series("Failure and abort", "#F08048", wasteHours)), "h");
        return ledger;
    }

    private static final class Ledger {
        String chart;
        List<Dashboard.CumRow> rows;
    }

    private static List<Dashboard.JobPanel> panels(
            String group,
            String title,
            String mark,
            Map<String, List<BuildSample>> byJob,
            List<BuildSample> previous,
            long now,
            int days,
            ZoneId zone,
            boolean compare) {
        List<String> names = new ArrayList<>();
        for (String name : byJob.keySet()) {
            if (group.equals(BuildSample.group(name))) {
                names.add(name);
            }
        }
        names.sort(Comparator.comparingLong((String name) -> totals(byJob.get(name)).duration).reversed());
        List<Dashboard.JobPanel> panels = new ArrayList<>();
        for (String name : names) {
            List<BuildSample> builds = byJob.get(name);
            List<BuildSample> prevBuilds = new ArrayList<>();
            for (BuildSample sample : previous) {
                if (name.equals(sample.jobFullName)) {
                    prevBuilds.add(sample);
                }
            }
            panels.add(panel(title, mark, name, builds, prevBuilds, now, days, zone, compare));
        }
        return List.copyOf(panels);
    }

    private static Dashboard.JobPanel panel(
            String title,
            String mark,
            String jobName,
            List<BuildSample> builds,
            List<BuildSample> previous,
            long now,
            int days,
            ZoneId zone,
            boolean compare) {
        Totals cur = totals(builds);
        Totals prev = totals(previous);
        List<String> labels = new ArrayList<>();
        List<Double> minutes = new ArrayList<>();
        List<BuildSample> newestFirst = new ArrayList<>(builds);
        newestFirst.sort(Comparator.comparingLong((BuildSample sample) -> sample.startMs).reversed());
        List<BuildSample> finished = new ArrayList<>();
        for (BuildSample sample : builds) {
            if (!sample.running()) {
                finished.add(sample);
            }
        }
        String durationTitle = BuildSample.shortName(jobName) + " duration";
        if (finished.size() > 48) {
            durationTitle = BuildSample.shortName(jobName) + " weekly average";
            Map<LocalDate, long[]> weeks = new LinkedHashMap<>();
            for (BuildSample sample : finished) {
                LocalDate day = Instant.ofEpochMilli(sample.startMs).atZone(zone).toLocalDate();
                LocalDate week = day.minusDays(day.getDayOfWeek().getValue() - 1L);
                long[] agg = weeks.computeIfAbsent(week, key -> new long[2]);
                agg[0] += sample.durationMs;
                agg[1] += 1;
            }
            for (Map.Entry<LocalDate, long[]> entry : weeks.entrySet()) {
                labels.add(Texts.day(entry.getKey()));
                minutes.add((entry.getValue()[0] / (double) entry.getValue()[1]) / 60_000d);
            }
        } else {
            for (BuildSample sample : finished) {
                labels.add("#" + sample.number);
                minutes.add(sample.durationMs / 60_000d);
            }
        }
        boolean hasStages = false;
        Map<String, long[]> stageAgg = new LinkedHashMap<>();
        Map<String, Integer> failAgg = new LinkedHashMap<>();
        for (BuildSample sample : builds) {
            String failedStage = null;
            for (StageSample stage : sample.stages) {
                if (stage.skipped()) {
                    continue;
                }
                if (stage.durationMs > 0) {
                    hasStages = true;
                    long[] agg = stageAgg.computeIfAbsent(stage.name, key -> new long[2]);
                    agg[0] += stage.durationMs;
                    agg[1] += 1;
                }
                if (failedStage == null && stage.failed()) {
                    failedStage = stage.name;
                }
            }
            if ("FAILURE".equals(sample.result)) {
                String key = failedStage == null ? "Unassigned stage" : failedStage;
                failAgg.merge(key, 1, Integer::sum);
            }
        }
        List<SvgCharts.Bar> bars = new ArrayList<>();
        List<SvgCharts.Slice> stageSlices = new ArrayList<>();
        List<Map.Entry<String, long[]>> ordered = new ArrayList<>(stageAgg.entrySet());
        ordered.sort(Comparator.comparingLong((Map.Entry<String, long[]> entry) -> entry.getValue()[0]).reversed());
        int colorIndex = 0;
        long stageTotal = 0;
        for (Map.Entry<String, long[]> entry : ordered) {
            stageTotal += entry.getValue()[0];
        }
        for (Map.Entry<String, long[]> entry : ordered) {
            if (bars.size() == 10) {
                break;
            }
            long average = entry.getValue()[0] / Math.max(1, entry.getValue()[1]);
            bars.add(new SvgCharts.Bar(
                    entry.getKey(),
                    entry.getKey(),
                    average / 60_000d,
                    "avg " + Texts.duration(average) + " · " + entry.getValue()[1]));
        }
        long folded = 0;
        int shown = 0;
        for (Map.Entry<String, long[]> entry : ordered) {
            if (shown < 8) {
                stageSlices.add(new SvgCharts.Slice(
                        entry.getKey(),
                        entry.getValue()[0],
                        JOB_COLORS[colorIndex % JOB_COLORS.length],
                        Texts.duration(entry.getValue()[0])));
                colorIndex++;
                shown++;
            } else {
                folded += entry.getValue()[0];
            }
        }
        if (folded > 0) {
            stageSlices.add(new SvgCharts.Slice("Other stages", folded, "#9DA0A5", Texts.duration(folded)));
        }
        List<SvgCharts.Slice> failSlices = new ArrayList<>();
        String[] failColors = {"#F08048", "#D07848", "#3479B0", "#3C74A3", "#9DA0A5"};
        int failIndex = 0;
        for (Map.Entry<String, Integer> entry : failAgg.entrySet()) {
            failSlices.add(new SvgCharts.Slice(
                    entry.getKey(), entry.getValue(), failColors[failIndex % failColors.length], entry.getValue() + " runs"));
            failIndex++;
        }
        List<Dashboard.BuildRow> recent = new ArrayList<>();
        int rowLimit = Math.min(12, newestFirst.size());
        for (int i = 0; i < rowLimit; i++) {
            BuildSample sample = newestFirst.get(i);
            String failed = "—";
            if ("FAILURE".equals(sample.result)) {
                failed = "Unassigned stage";
                for (StageSample stage : sample.stages) {
                    if (stage.failed()) {
                        failed = stage.name;
                        break;
                    }
                }
            }
            recent.add(new Dashboard.BuildRow(
                    sample.buildUrl,
                    sample.label(),
                    Texts.resultLabel(sample.result),
                    Texts.resultClass(sample.result),
                    sample.running() ? Texts.both("Running", "进行中") : Texts.durations(sample.durationMs),
                    failed,
                    Texts.when(sample.startMs, zone),
                    sample.actor));
        }
        String jobUrl = builds.isEmpty() ? "" : builds.get(0).jobUrl;
        String compareText = jobCompare(days, cur, prev, compare);
        String center = stageTotal == 0 ? "0" : Texts.num(stageTotal / 3_600_000d);
        Sign sign = signOf(builds);
        return new Dashboard.JobPanel(
                title,
                mark,
                BuildSample.shortName(jobName),
                jobUrl,
                Integer.toString(cur.builds),
                cur.completed == 0 ? "—" : Texts.durations(cur.duration / cur.completed),
                Texts.percent(cur.success, cur.completed),
                Integer.toString(cur.failure),
                Texts.durations(cur.duration),
                compareText,
                SvgCharts.line(durationTitle, labels, minutes, "#3479B0", false, "m"),
                SvgCharts.bars(BuildSample.shortName(jobName) + " stage average", bars),
                SvgCharts.donut("Stage time", center, "h", stageSlices),
                SvgCharts.donut("Failed stage", Integer.toString(cur.failure), "failed", failSlices),
                hasStages,
                List.copyOf(recent),
                SvgCharts.columns("Time of day", hourLabels(builds, zone), hourValues(builds, zone), "#3479B0"),
                dailyCountsChart(builds, now, zone),
                rateSeries(finished, zone),
                stageDots(builds),
                scriptChanges(builds, zone),
                peopleOf(builds, zone, false),
                sign.image,
                sign.alt,
                sign.caption,
                sign.live);
    }

    private static String rateSeries(List<BuildSample> finished, ZoneId zone) {
        List<String> labels = new ArrayList<>();
        List<Double> rates = new ArrayList<>();
        if (finished.size() > 48) {
            Map<LocalDate, int[]> weeks = new LinkedHashMap<>();
            for (BuildSample sample : finished) {
                LocalDate day = Instant.ofEpochMilli(sample.startMs).atZone(zone).toLocalDate();
                LocalDate week = day.minusDays(day.getDayOfWeek().getValue() - 1L);
                int[] agg = weeks.computeIfAbsent(week, key -> new int[2]);
                agg[1]++;
                if ("SUCCESS".equals(sample.result)) {
                    agg[0]++;
                }
            }
            for (Map.Entry<LocalDate, int[]> entry : weeks.entrySet()) {
                labels.add(Texts.day(entry.getKey()));
                rates.add(100.0 * entry.getValue()[0] / entry.getValue()[1]);
            }
        } else {
            for (BuildSample sample : finished) {
                labels.add("#" + sample.number);
                rates.add("SUCCESS".equals(sample.result) ? 100d : 0d);
            }
        }
        return SvgCharts.line("Success rate", labels, rates, "#50A040", false, "%");
    }

    private static String stageDots(List<BuildSample> builds) {
        List<BuildSample> ordered = new ArrayList<>();
        for (BuildSample sample : builds) {
            if (!sample.stages.isEmpty()) {
                ordered.add(sample);
            }
        }
        ordered.sort(Comparator.comparingLong(sample -> sample.startMs));
        if (ordered.isEmpty()) {
            return SvgCharts.emptyPublic("Stage status");
        }
        if (ordered.size() > 36) {
            ordered = new ArrayList<>(ordered.subList(ordered.size() - 36, ordered.size()));
        }
        List<String> names = new ArrayList<>();
        for (BuildSample sample : ordered) {
            for (StageSample stage : sample.stages) {
                if (!names.contains(stage.name) && names.size() < 14) {
                    names.add(stage.name);
                }
            }
        }
        List<String> labels = new ArrayList<>();
        List<List<String>> cells = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            cells.add(new ArrayList<>());
        }
        for (BuildSample sample : ordered) {
            labels.add("#" + sample.number);
            Map<String, String> status = new LinkedHashMap<>();
            for (StageSample stage : sample.stages) {
                status.put(stage.name, stage.status);
            }
            for (int i = 0; i < names.size(); i++) {
                cells.get(i).add(status.getOrDefault(names.get(i), ""));
            }
        }
        return SvgCharts.statusDots("Stage status", labels, names, cells);
    }

    private static String scriptChanges(List<BuildSample> builds, ZoneId zone) {
        List<BuildSample> ordered = new ArrayList<>(builds);
        ordered.sort(Comparator.comparingLong(sample -> sample.startMs));
        Map<LocalDate, Integer> weeks = new LinkedHashMap<>();
        Set<String> previous = null;
        for (BuildSample sample : ordered) {
            if (sample.stages.isEmpty()) {
                continue;
            }
            Set<String> names = new LinkedHashSet<>();
            for (StageSample stage : sample.stages) {
                names.add(stage.name);
            }
            LocalDate day = Instant.ofEpochMilli(sample.startMs).atZone(zone).toLocalDate();
            LocalDate week = day.minusDays(day.getDayOfWeek().getValue() - 1L);
            weeks.putIfAbsent(week, 0);
            if (previous != null && !previous.equals(names)) {
                weeks.merge(week, 1, Integer::sum);
            }
            previous = names;
        }
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> entry : weeks.entrySet()) {
            labels.add(Texts.day(entry.getKey()));
            values.add(entry.getValue().doubleValue());
        }
        if (values.stream().allMatch(value -> value == 0d)) {
            return SvgCharts.emptyPublic("Stage name changes");
        }
        return SvgCharts.columns("Stage name changes", labels, values, "#F08048");
    }

    private static List<Dashboard.JobPanel> allPanels(
            Map<String, List<BuildSample>> byJob,
            List<BuildSample> previous,
            long now,
            int days,
            ZoneId zone,
            boolean compare) {
        List<String> names = new ArrayList<>(byJob.keySet());
        names.sort(Comparator.comparingLong((String name) -> totals(byJob.get(name)).duration).reversed());
        List<Dashboard.JobPanel> panels = new ArrayList<>();
        for (String name : names) {
            String group = BuildSample.group(name);
            String title = "package".equals(group) ? "LADM package" : "test".equals(group) ? "LADM test" : "Pipeline";
            String mark = "package".equals(group) ? "is-pack" : "test".equals(group) ? "is-test" : "is-other";
            List<BuildSample> prevBuilds = new ArrayList<>();
            for (BuildSample sample : previous) {
                if (name.equals(sample.jobFullName)) {
                    prevBuilds.add(sample);
                }
            }
            panels.add(panel(title, mark, name, byJob.get(name), prevBuilds, now, days, zone, compare));
        }
        return List.copyOf(panels);
    }

    private static List<Dashboard.JobRow> pipelineRows(Map<String, List<BuildSample>> byJob, ZoneId zone) {
        List<Dashboard.JobRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<BuildSample>> entry : byJob.entrySet()) {
            List<BuildSample> builds = entry.getValue();
            Totals cur = totals(builds);
            BuildSample last = builds.get(builds.size() - 1);
            rows.add(new Dashboard.JobRow(
                    last.jobUrl,
                    BuildSample.shortName(entry.getKey()),
                    cur.builds,
                    Texts.durations(cur.duration),
                    cur.completed == 0 ? "—" : Texts.durations(cur.duration / cur.completed),
                    Texts.percent(cur.success, cur.completed),
                    cur.failure,
                    Texts.resultLabel(last.result),
                    Texts.resultClass(last.result),
                    Texts.when(last.startMs, zone),
                    cur.aborted,
                    Texts.durations(cur.failedDuration + cur.abortedDuration),
                    topPerson(builds)));
        }
        rows.sort(Comparator.comparingInt((Dashboard.JobRow row) -> row.builds).reversed());
        return List.copyOf(rows);
    }

    private static String dailyCountsChart(List<BuildSample> samples, long now, ZoneId zone) {
        DayCounts days = byDay(samples, now, zone);
        boolean weekly = days.labels.size() > 45;
        if (weekly) {
            days = foldWeeks(days);
        }
        return SvgCharts.stacked(weekly ? "Builds per week" : "Builds per day", days.labels, List.of(
                new SvgCharts.Series("Success", "#50A040", days.success),
                new SvgCharts.Series("Failure", "#F08048", days.failure),
                new SvgCharts.Series("Aborted", "#9DA0A5", days.aborted)), "");
    }

    private static DayCounts foldWeeks(DayCounts days) {
        DayCounts folded = new DayCounts();
        for (int i = 0; i < days.labels.size(); i += 7) {
            int end = Math.min(days.labels.size(), i + 7);
            double all = 0;
            double success = 0;
            double failure = 0;
            double aborted = 0;
            double hours = 0;
            for (int j = i; j < end; j++) {
                all += days.all.get(j);
                success += days.success.get(j);
                failure += days.failure.get(j);
                aborted += days.aborted.get(j);
                hours += days.hours.get(j);
            }
            folded.labels.add(days.labels.get(i));
            folded.all.add(all);
            folded.success.add(success);
            folded.failure.add(failure);
            folded.aborted.add(aborted);
            folded.hours.add(hours);
        }
        return folded;
    }

    private static String dailyHoursChart(List<BuildSample> samples, long now, ZoneId zone) {
        DayCounts days = byDay(samples, now, zone);
        return SvgCharts.line("Machine hours per day", days.labels, days.hours, "#3479B0", true, "h");
    }

    private static List<String> hourLabels(List<BuildSample> samples, ZoneId zone) {
        List<String> labels = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            labels.add(String.format("%02d", hour));
        }
        return labels;
    }

    private static List<Double> hourValues(List<BuildSample> samples, ZoneId zone) {
        int[] counts = new int[24];
        for (BuildSample sample : samples) {
            counts[Instant.ofEpochMilli(sample.startMs).atZone(zone).getHour()]++;
        }
        List<Double> values = new ArrayList<>();
        for (int count : counts) {
            values.add((double) count);
        }
        return values;
    }

    private static String peopleBars(List<Dashboard.PersonRow> people) {
        List<SvgCharts.Bar> bars = new ArrayList<>();
        int limit = Math.min(people.size(), 12);
        for (int i = 0; i < limit; i++) {
            Dashboard.PersonRow person = people.get(i);
            bars.add(new SvgCharts.Bar(person.name, person.name, person.builds, person.builds + " runs"));
        }
        return SvgCharts.bars("Triggers", bars);
    }

    private static DayCounts byDay(List<BuildSample> samples, long now, ZoneId zone) {
        DayCounts days = new DayCounts();
        if (samples.isEmpty()) {
            return days;
        }
        long oldest = Long.MAX_VALUE;
        for (BuildSample sample : samples) {
            oldest = Math.min(oldest, sample.startMs);
        }
        LocalDate start = Instant.ofEpochMilli(oldest).atZone(zone).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        Map<LocalDate, int[]> counts = new LinkedHashMap<>();
        Map<LocalDate, Long> duration = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            counts.put(day, new int[4]);
            duration.put(day, 0L);
        }
        for (BuildSample sample : samples) {
            if (sample.startMs > now) {
                continue;
            }
            LocalDate day = Instant.ofEpochMilli(sample.startMs).atZone(zone).toLocalDate();
            int[] row = counts.get(day);
            if (row == null) {
                continue;
            }
            row[0]++;
            if ("SUCCESS".equals(sample.result)) {
                row[1]++;
            } else if ("FAILURE".equals(sample.result)) {
                row[2]++;
            } else if ("ABORTED".equals(sample.result)) {
                row[3]++;
            }
            if (!sample.running()) {
                duration.put(day, duration.get(day) + sample.durationMs);
            }
        }
        for (Map.Entry<LocalDate, int[]> entry : counts.entrySet()) {
            int[] row = entry.getValue();
            days.labels.add(Texts.day(entry.getKey()));
            days.all.add((double) row[0]);
            days.success.add((double) row[1]);
            days.failure.add((double) row[2]);
            days.aborted.add((double) row[3]);
            days.hours.add(duration.get(entry.getKey()) / 3_600_000d);
        }
        return days;
    }

    private static final class DayCounts {
        final List<String> labels = new ArrayList<>();
        final List<Double> all = new ArrayList<>();
        final List<Double> success = new ArrayList<>();
        final List<Double> failure = new ArrayList<>();
        final List<Double> aborted = new ArrayList<>();
        final List<Double> hours = new ArrayList<>();
    }

    private static String hourChart(List<BuildSample> samples, ZoneId zone, String title) {
        int[] counts = new int[24];
        for (BuildSample sample : samples) {
            counts[Instant.ofEpochMilli(sample.startMs).atZone(zone).getHour()]++;
        }
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            labels.add(String.format("%02d", hour));
            values.add((double) counts[hour]);
        }
        return SvgCharts.columns(title, labels, values, "#3479B0");
    }

    private static List<Dashboard.PersonRow> peopleOf(List<BuildSample> samples, ZoneId zone, boolean withJobs) {
        Map<String, long[]> agg = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> jobs = new LinkedHashMap<>();
        for (BuildSample sample : samples) {
            String name = sample.actor.isBlank() ? Texts.triggerLabel(sample.trigger) : sample.actor;
            long[] row = agg.computeIfAbsent(name, key -> new long[6]);
            row[0]++;
            if ("SUCCESS".equals(sample.result)) {
                row[1]++;
            } else if ("FAILURE".equals(sample.result)) {
                row[2]++;
            } else if ("ABORTED".equals(sample.result)) {
                row[3]++;
            }
            if (!sample.running()) {
                row[4] += sample.durationMs;
            }
            row[5] = Math.max(row[5], sample.startMs);
            if (withJobs) {
                jobs.computeIfAbsent(name, key -> new LinkedHashSet<>()).add(BuildSample.shortName(sample.jobFullName));
            }
        }
        List<String> names = new ArrayList<>(agg.keySet());
        names.sort(Comparator.comparingLong((String name) -> agg.get(name)[0]).reversed());
        List<Dashboard.PersonRow> rows = new ArrayList<>();
        for (String name : names) {
            long[] row = agg.get(name);
            int finished = (int) (row[1] + row[2] + row[3]);
            String touched = "";
            if (withJobs && jobs.get(name) != null) {
                touched = String.join("、", jobs.get(name));
            }
            rows.add(new Dashboard.PersonRow(
                    name,
                    (int) row[0],
                    (int) row[1],
                    (int) row[2],
                    (int) row[3],
                    Texts.durations(row[4]),
                    Texts.percent((int) row[1], finished),
                    Texts.when(row[5], zone),
                    touched));
        }
        return List.copyOf(rows);
    }

    private static String topPerson(List<BuildSample> samples) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BuildSample sample : samples) {
            if (!sample.actor.isBlank()) {
                counts.merge(sample.actor, 1, Integer::sum);
            }
        }
        String best = "";
        int count = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > count) {
                count = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private static String resultPie(Totals all) {
        List<SvgCharts.Slice> slices = List.of(
                new SvgCharts.Slice("Success", all.success, "#50A040", all.success + " runs"),
                new SvgCharts.Slice("Failure", all.failure, "#F08048", all.failure + " runs"),
                new SvgCharts.Slice("Unstable", all.unstable, "#D07848", all.unstable + " runs"),
                new SvgCharts.Slice("Aborted", all.aborted, "#9DA0A5", all.aborted + " runs"),
                new SvgCharts.Slice("Running", all.running, "#3479B0", all.running + " runs"));
        return SvgCharts.donut("Results", Integer.toString(all.builds), "runs", slices);
    }

    private static String timePie(List<BuildSample> current) {
        Map<String, Long> byJob = new LinkedHashMap<>();
        Map<String, String> fullByShort = new LinkedHashMap<>();
        for (BuildSample sample : current) {
            if (sample.running()) {
                continue;
            }
            String shortName = BuildSample.shortName(sample.jobFullName);
            byJob.merge(shortName, sample.durationMs, Long::sum);
            fullByShort.putIfAbsent(shortName, sample.jobFullName);
        }
        List<Map.Entry<String, Long>> ordered = new ArrayList<>(byJob.entrySet());
        ordered.sort(Comparator.comparingLong((Map.Entry<String, Long> entry) -> entry.getValue()).reversed());
        List<SvgCharts.Slice> slices = new ArrayList<>();
        long folded = 0;
        int palette = 0;
        long total = 0;
        for (Map.Entry<String, Long> entry : ordered) {
            total += entry.getValue();
        }
        for (int i = 0; i < ordered.size(); i++) {
            Map.Entry<String, Long> entry = ordered.get(i);
            if (i < 6) {
                slices.add(new SvgCharts.Slice(
                        entry.getKey(),
                        entry.getValue(),
                        colorFor(fullByShort.get(entry.getKey()), palette++),
                        Texts.duration(entry.getValue())));
            } else {
                folded += entry.getValue();
            }
        }
        if (folded > 0) {
            slices.add(new SvgCharts.Slice("Other jobs", folded, "#9DA0A5", Texts.duration(folded)));
        }
        String center = total == 0 ? "0" : Texts.num(total / 3_600_000d);
        return SvgCharts.donut("Machine time", center, "h", slices);
    }

    private static String triggerPie(Totals all) {
        List<SvgCharts.Slice> slices = List.of(
                new SvgCharts.Slice("Manual", all.manual, "#3479B0", all.manual + " runs"),
                new SvgCharts.Slice("Commit", all.scm, "#50A040", all.scm + " runs"),
                new SvgCharts.Slice("Timer", all.timer, "#F08048", all.timer + " runs"),
                new SvgCharts.Slice("Upstream", all.upstream, "#3C74A3", all.upstream + " runs"),
                new SvgCharts.Slice("Other", all.otherTrigger, "#9DA0A5", all.otherTrigger + " runs"));
        return SvgCharts.donut("Trigger", Integer.toString(all.builds), "runs", slices);
    }

    private static String colorFor(String fullName, int index) {
        String group = BuildSample.group(fullName);
        if ("package".equals(group)) {
            return "#3C74A3";
        }
        if ("test".equals(group)) {
            return "#F08048";
        }
        return JOB_COLORS[index % JOB_COLORS.length];
    }

    private static String headline(int days, Totals all, Totals pack, Totals test, Totals prev, boolean chinese) {
        StringBuilder text = new StringBuilder();
        if (days <= 0) {
            text.append(chinese ? "全部可见构建" : "All visible builds");
        } else if (chinese) {
            text.append("近 ").append(days).append(" 天");
        } else {
            text.append("Last ").append(days).append(" days");
        }
        if (chinese) {
            text.append("共 ").append(all.builds).append(" 次，机器执行 ").append(Texts.duration(all.duration, true))
                    .append("，排队 ").append(Texts.duration(all.queue, true)).append("。");
        } else {
            text.append(": ").append(all.builds).append(" runs, machine time ").append(Texts.duration(all.duration))
                    .append(", queue ").append(Texts.duration(all.queue)).append(". ");
        }
        if (pack.builds > 0) {
            String average = pack.completed == 0 ? "—" : Texts.duration(pack.duration / pack.completed, chinese);
            if (chinese) {
                text.append("LADM 2.0 整包 ").append(pack.builds).append(" 次，平均 ").append(average)
                        .append("，成功率 ").append(Texts.percent(pack.success, pack.completed)).append("。");
            } else {
                text.append("LADM 2.0 package ").append(pack.builds).append(" runs, average ").append(average)
                        .append(", success rate ").append(Texts.percent(pack.success, pack.completed)).append(". ");
            }
        }
        if (test.builds > 0) {
            if (chinese) {
                text.append("测试包 ").append(test.builds).append(" 次，成功率 ")
                        .append(Texts.percent(test.success, test.completed)).append("。");
            } else {
                text.append("Test package ").append(test.builds).append(" runs, success rate ")
                        .append(Texts.percent(test.success, test.completed)).append(". ");
            }
        }
        if (chinese) {
            text.append("成功 ").append(all.success).append(" 次。");
            text.append("失败耗时 ").append(Texts.duration(all.failedDuration, true)).append("。");
        } else {
            text.append("Success ").append(all.success).append(". ");
            text.append("Failure time ").append(Texts.duration(all.failedDuration)).append(".");
        }
        if (days > 0) {
            text.append(rateSentence(days, all, prev, chinese));
        }
        return text.toString();
    }

    private static String wasteLine(Totals all, int minutes) {
        return Texts.both(
                "Failure time " + Texts.duration(all.failedDuration) + ", abort time " + Texts.duration(all.abortedDuration) + ".",
                "失败耗时 " + Texts.duration(all.failedDuration, true) + "，中止耗时 " + Texts.duration(all.abortedDuration, true) + "。");
    }

    private static Dashboard.Kpi kpi(int days, Totals all, Totals prev, boolean compare) {
        String durationClass = "";
        String successClass = "";
        if (compare && all.completed > 0 && prev.completed > 0) {
            long curAvg = all.duration / all.completed;
            long prevAvg = prev.duration / prev.completed;
            if (curAvg < prevAvg) {
                durationClass = "is-ok";
            } else if (curAvg > prevAvg) {
                durationClass = "is-bad";
            }
            double curRate = (double) all.success / all.completed;
            double prevRate = (double) prev.success / prev.completed;
            if (curRate > prevRate + 0.0005) {
                successClass = "is-ok";
            } else if (curRate < prevRate - 0.0005) {
                successClass = "is-bad";
            }
        }
        return new Dashboard.Kpi(
                Integer.toString(all.builds),
                countSentence(days, all.builds, prev.builds, compare),
                Texts.durations(all.duration),
                Texts.both(
                        avgSentence(days, all, prev, compare, false).stripLeading(),
                        avgSentence(days, all, prev, compare, true).stripLeading()),
                durationClass,
                Texts.duration(all.queue),
                Texts.percent(all.success, all.completed),
                rateSentence(days, all, prev, false),
                successClass);
    }

    private static String jobCompare(int days, Totals cur, Totals prev, boolean compare) {
        if (!compare) {
            return Texts.both("All time. Not compared with the previous window.", "全部时间，不和上一窗口对比。");
        }
        if (prev.completed == 0 || cur.completed == 0) {
            return Texts.both(
                    "The previous " + days + " days do not have enough finished builds to compare.",
                    "前 " + days + " 天没有足够的完成记录，暂不对比。");
        }
        String english = (rateSentence(days, cur, prev, false) + avgSentence(days, cur, prev, true, false)).stripLeading();
        String chinese = (rateSentence(days, cur, prev, true) + avgSentence(days, cur, prev, true, true)).stripLeading();
        return Texts.both(english, chinese);
    }

    private static String countSentence(int days, int current, int previous, boolean compare) {
        if (!compare) {
            return Texts.both("Not compared with the previous window", "不和上一窗口对比");
        }
        if (previous == 0) {
            return Texts.both(
                    "No builds in the previous " + days + " days",
                    "前 " + days + " 天没有记录");
        }
        int delta = current - previous;
        if (delta == 0) {
            return Texts.both(
                    "Same as the previous " + days + " days",
                    "与前 " + days + " 天相同");
        }
        if (delta > 0) {
            return Texts.both(
                    delta + " more than the previous " + days + " days",
                    "比前 " + days + " 天多 " + delta + " 次");
        }
        return Texts.both(
                (-delta) + " fewer than the previous " + days + " days",
                "比前 " + days + " 天少 " + (-delta) + " 次");
    }

    private static String rateSentence(int days, Totals current, Totals previous, boolean chinese) {
        if (days <= 0) {
            return "";
        }
        if (current.completed == 0 || previous.completed == 0) {
            return chinese
                    ? "前 " + days + " 天没有足够的完成记录，暂不对比成功率。"
                    : " The previous " + days + " days do not have enough finished builds to compare success rate.";
        }
        double pp = (100d * current.success / current.completed) - (100d * previous.success / previous.completed);
        String points = Texts.points(pp);
        if (Math.abs(pp) < 0.05) {
            return chinese
                    ? "成功率与前 " + days + " 天持平。"
                    : " Success rate is level with the previous " + days + " days.";
        }
        if (pp > 0) {
            return chinese
                    ? "成功率比前 " + days + " 天高 " + points + " 个百分点。"
                    : " Success rate is " + points + " points above the previous " + days + " days.";
        }
        return chinese
                ? "成功率比前 " + days + " 天低 " + points + " 个百分点。"
                : " Success rate is " + points + " points below the previous " + days + " days.";
    }

    private static String avgSentence(int days, Totals current, Totals previous, boolean compare, boolean chinese) {
        if (!compare || current.completed == 0 || previous.completed == 0) {
            return "";
        }
        long curAvg = current.duration / current.completed;
        long prevAvg = previous.duration / previous.completed;
        long diff = Math.abs(curAvg - prevAvg);
        if (diff < 1000) {
            return chinese
                    ? "平均耗时与前 " + days + " 天持平。"
                    : " Average duration is level with the previous " + days + " days.";
        }
        String duration = Texts.duration(diff, chinese);
        if (curAvg < prevAvg) {
            return chinese
                    ? "平均耗时比前 " + days + " 天少 " + duration + "。"
                    : " Average duration is " + duration + " shorter than the previous " + days + " days.";
        }
        return chinese
                ? "平均耗时比前 " + days + " 天多 " + duration + "。"
                : " Average duration is " + duration + " longer than the previous " + days + " days.";
    }

    private static String otherSummary(Totals other, Map<String, List<BuildSample>> byJob) {
        int jobs = 0;
        for (String name : byJob.keySet()) {
            if ("other".equals(BuildSample.group(name))) {
                jobs++;
            }
        }
        if (jobs == 0) {
            return Texts.both("Outside package and test jobs: 0 jobs.", "整包、测试包以外：0 个任务。");
        }
        String rate = Texts.percent(other.success, other.completed);
        return Texts.both(
                "Other jobs: " + jobs + ", " + other.builds + " runs, machine time " + Texts.duration(other.duration)
                        + ", success rate " + rate + ".",
                "其它任务 " + jobs + " 个，构建 " + other.builds + " 次，机器执行 " + Texts.duration(other.duration, true)
                        + "，成功率 " + rate + "。");
    }

    private static String methodNote(int days, int minutes) {
        String english = days <= 0
                ? "All time starts at the earliest visible build."
                : "The last " + days + " days use build start time.";
        String chinese = days <= 0
                ? "选择全部时，从最早一条可见构建算起。"
                : "近 " + days + " 天按构建开始时间。";
        english += " Times use the Jenkins controller time zone. Duration excludes queue time. Queue time is from entering the queue until the build starts."
                + " Success rate = successes / finished builds. A running build is not finished. Successes are builds whose result is SUCCESS."
                + " Counts are calendar days, and a day with no builds is 0. Aborted means result ABORTED."
                + " When the span is longer than 120 days, cumulative charts use weeks."
                + " Trigger account is the userId on the build cause."
                + " A package job name contains ladm and not test. A test job name contains both."
                + " Stage time comes from Pipeline: Stage View. Stages that did not run are omitted. Failed stage is the first failed stage of that build.";
        chinese += "时区为 Jenkins 所在时区。执行耗时不含排队。排队是从进入队列到开始。"
                + "成功率 = 成功 / 已完成。构建中不计入已完成。成功是结果为 SUCCESS 的构建。"
                + "次数按自然日，没有构建的日子记 0。中止是结果为 ABORTED。"
                + "跨度超过 120 天时，累计图按周。"
                + "触发账号是构建原因里的 userId。"
                + "整包是任务名包含 ladm 且不含 test。测试包是两者都包含。"
                + "阶段耗时来自 Pipeline: Stage View。未执行的阶段不计。失败阶段是该次第一个失败阶段。";
        return Texts.both(english, chinese);
    }

    private static List<BuildSample> filter(List<BuildSample> samples, String group) {
        List<BuildSample> out = new ArrayList<>();
        for (BuildSample sample : samples) {
            if (group.equals(BuildSample.group(sample.jobFullName))) {
                out.add(sample);
            }
        }
        return out;
    }

    private static Totals totals(List<BuildSample> samples) {
        Totals totals = new Totals();
        for (BuildSample sample : samples) {
            totals.builds++;
            totals.queue += sample.queueMs;
            switch (sample.trigger) {
                case "manual" -> totals.manual++;
                case "scm" -> totals.scm++;
                case "timer" -> totals.timer++;
                case "upstream" -> totals.upstream++;
                default -> totals.otherTrigger++;
            }
            if (sample.running()) {
                totals.running++;
                continue;
            }
            totals.completed++;
            totals.duration += sample.durationMs;
            switch (sample.result) {
                case "SUCCESS" -> totals.success++;
                case "FAILURE" -> {
                    totals.failure++;
                    totals.failedDuration += sample.durationMs;
                }
                case "UNSTABLE" -> totals.unstable++;
                case "ABORTED" -> {
                    totals.aborted++;
                    totals.abortedDuration += sample.durationMs;
                }
                default -> {
                    // NOT_BUILT stays in the completed denominator via completed++, but not a named slice.
                }
            }
        }
        return totals;
    }

    private static final class Totals {
        int builds;
        int completed;
        int success;
        int failure;
        int unstable;
        int aborted;
        int running;
        long duration;
        long queue;
        long failedDuration;
        long abortedDuration;
        int manual;
        int scm;
        int timer;
        int upstream;
        int otherTrigger;
    }
}
