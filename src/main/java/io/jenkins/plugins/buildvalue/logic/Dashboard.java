package io.jenkins.plugins.buildvalue.logic;

import java.util.List;

public final class Dashboard {
    public final int days;
    public final String banner;
    public final String headline;
    public final String wasteLine;
    public final Kpi kpi;
    public final String countChart;
    public final String hoursChart;
    public final String rateChart;
    public final String resultPie;
    public final String timePie;
    public final String triggerPie;
    public final List<JobPanel> packages;
    public final List<JobPanel> tests;
    public final String otherSummary;
    public final List<JobRow> others;
    public final String deliveryChart;
    public final String dailyHoursChart;
    public final String hourChart;
    public final String deliveryText;
    public final String wasteMachine;
    public final List<PersonRow> people;
    public final List<JobPanel> tabs;
    public final String cumSplitChart;
    public final List<CumRow> cumRows;
    public final String methodNote;
    /** Plate for the newest build in the window. */
    public final StatusSign sign;
    /** Plate for the newest build overall, used by the home card. */
    public final StatusSign home;
    public final boolean empty;

    Dashboard(
            int days,
            String banner,
            String headline,
            String wasteLine,
            Kpi kpi,
            String countChart,
            String hoursChart,
            String rateChart,
            String resultPie,
            String timePie,
            String triggerPie,
            List<JobPanel> packages,
            List<JobPanel> tests,
            String otherSummary,
            List<JobRow> others,
            String deliveryChart,
            String dailyHoursChart,
            String hourChart,
            String deliveryText,
            String wasteMachine,
            List<PersonRow> people,
            List<JobPanel> tabs,
            String cumSplitChart,
            List<CumRow> cumRows,
            String methodNote,
            StatusSign sign,
            StatusSign home,
            boolean empty) {
        this.days = days;
        this.banner = banner;
        this.headline = headline;
        this.wasteLine = wasteLine;
        this.kpi = kpi;
        this.countChart = countChart;
        this.hoursChart = hoursChart;
        this.rateChart = rateChart;
        this.resultPie = resultPie;
        this.timePie = timePie;
        this.triggerPie = triggerPie;
        this.packages = packages;
        this.tests = tests;
        this.otherSummary = otherSummary;
        this.others = others;
        this.deliveryChart = deliveryChart;
        this.dailyHoursChart = dailyHoursChart;
        this.hourChart = hourChart;
        this.deliveryText = deliveryText;
        this.wasteMachine = wasteMachine;
        this.people = people;
        this.tabs = tabs;
        this.cumSplitChart = cumSplitChart;
        this.cumRows = cumRows;
        this.methodNote = methodNote;
        this.sign = sign == null ? StatusSign.none() : sign;
        this.home = home == null ? StatusSign.none() : home;
        this.empty = empty;
    }

    public static final class Kpi {
        public final String builds;
        public final String buildsDelta;
        public final String duration;
        public final String durationDelta;
        public final String durationClass;
        public final String queue;
        public final String success;
        public final String successDelta;
        public final String successClass;

        Kpi(String builds, String buildsDelta, String duration, String durationDelta, String durationClass,
                String queue, String success, String successDelta, String successClass) {
            this.builds = builds;
            this.buildsDelta = buildsDelta;
            this.duration = duration;
            this.durationDelta = durationDelta;
            this.durationClass = durationClass;
            this.queue = queue;
            this.success = success;
            this.successDelta = successDelta;
            this.successClass = successClass;
        }
    }

    public static final class JobPanel {
        public final String title;
        public final String mark;
        public final String jobName;
        public final String jobUrl;
        public final String builds;
        public final String average;
        public final String success;
        public final String failures;
        public final String machineTime;
        public final String compare;
        public final String durationChart;
        public final String stageBars;
        public final String stagePie;
        public final String failPie;
        public final boolean hasStages;
        public final List<BuildRow> recent;
        public final String hourChart;
        public final String dailyCountsChart;
        public final String rateChart;
        public final String stageDots;
        public final String scriptChart;
        public final List<PersonRow> people;
        public final StatusSign sign;

        JobPanel(String title, String mark, String jobName, String jobUrl, String builds, String average,
                String success, String failures, String machineTime, String compare, String durationChart, String stageBars,
                String stagePie, String failPie, boolean hasStages, List<BuildRow> recent, String hourChart,
                String dailyCountsChart, String rateChart, String stageDots, String scriptChart, List<PersonRow> people,
                StatusSign sign) {
            this.title = title;
            this.mark = mark;
            this.jobName = jobName;
            this.jobUrl = jobUrl;
            this.builds = builds;
            this.average = average;
            this.success = success;
            this.failures = failures;
            this.machineTime = machineTime;
            this.compare = compare;
            this.durationChart = durationChart;
            this.stageBars = stageBars;
            this.stagePie = stagePie;
            this.failPie = failPie;
            this.hasStages = hasStages;
            this.recent = recent;
            this.hourChart = hourChart;
            this.dailyCountsChart = dailyCountsChart;
            this.rateChart = rateChart;
            this.stageDots = stageDots;
            this.scriptChart = scriptChart;
            this.people = people;
            this.sign = sign == null ? StatusSign.none() : sign;
        }

        public String getJobName() {
            return jobName;
        }

        public String getShortName() {
            return BuildSample.shortName(jobName);
        }

        public String getJobUrl() {
            return jobUrl;
        }

        public StatusSign getSign() {
            return sign;
        }

        public List<BuildRow> getRecent() {
            return recent;
        }

        public List<PersonRow> getPeople() {
            return people;
        }

        public boolean isHasStages() {
            return hasStages;
        }
    }

    public static final class BuildRow {
        public final String href;
        public final String label;
        public final String result;
        public final String resultClass;
        public final String duration;
        public final String failStage;
        public final String when;
        public final String actor;

        BuildRow(String href, String label, String result, String resultClass, String duration, String failStage,
                String when, String actor) {
            this.href = href;
            this.label = label;
            this.result = result;
            this.resultClass = resultClass;
            this.duration = duration;
            this.failStage = failStage;
            this.when = when;
            this.actor = actor == null || actor.isBlank() ? "—" : actor;
        }

        public String getHref() {
            return href;
        }

        public String getLabel() {
            return label;
        }

        public String getResult() {
            return result;
        }

        public String getResultClass() {
            return resultClass;
        }

        public String getWhen() {
            return when;
        }

        public String getActor() {
            return actor;
        }
    }

    public static final class JobRow {
        public final String href;
        public final String name;
        public final int builds;
        public final String duration;
        public final String average;
        public final String success;
        public final int failures;
        public final String lastResult;
        public final String lastClass;
        public final String lastWhen;
        public final int aborted;
        public final String waste;
        public final String topPerson;

        JobRow(String href, String name, int builds, String duration, String average, String success, int failures,
                String lastResult, String lastClass, String lastWhen, int aborted, String waste, String topPerson) {
            this.href = href;
            this.name = name;
            this.builds = builds;
            this.duration = duration;
            this.average = average;
            this.success = success;
            this.failures = failures;
            this.lastResult = lastResult;
            this.lastClass = lastClass;
            this.lastWhen = lastWhen;
            this.aborted = aborted;
            this.waste = waste;
            this.topPerson = topPerson == null || topPerson.isBlank() ? "—" : topPerson;
        }

        public String getHref() {
            return href;
        }

        /** Full Jenkins path, shown in full and used as the tooltip. */
        public String getName() {
            return name;
        }

        public String getShortName() {
            return BuildSample.shortName(name);
        }

        public int getBuilds() {
            return builds;
        }

        public String getSuccess() {
            return success;
        }

        public int getFailures() {
            return failures;
        }

        public String getTopPerson() {
            return topPerson;
        }
    }

    public static final class PersonRow {
        public final String name;
        public final int builds;
        public final int success;
        public final int failure;
        public final int aborted;
        public final String duration;
        public final String successRate;
        public final String lastWhen;
        /**
         * Jobs this account triggered, one entry each, in first-seen order. A list rather than a
         * joined string: the view renders one row per job and keeps the full path, and nothing has
         * to split a separator back out of a name that may legitimately contain one.
         */
        public final List<JobLink> jobs;

        PersonRow(String name, int builds, int success, int failure, int aborted, String duration, String successRate,
                String lastWhen, List<JobLink> jobs) {
            this.name = name;
            this.builds = builds;
            this.success = success;
            this.failure = failure;
            this.aborted = aborted;
            this.duration = duration;
            this.successRate = successRate;
            this.lastWhen = lastWhen;
            this.jobs = jobs == null ? List.of() : List.copyOf(jobs);
        }

        public String getName() {
            return name;
        }

        public List<JobLink> getJobs() {
            return jobs;
        }

        /** Every pipeline this account triggered, one full path per line, for the native tooltip. */
        public String getJobsTip() {
            StringBuilder tip = new StringBuilder();
            for (JobLink job : jobs) {
                if (tip.length() > 0) {
                    tip.append('\n');
                }
                tip.append(job.fullName);
            }
            return tip.toString();
        }

        public int getBuilds() {
            return builds;
        }

        public int getSuccess() {
            return success;
        }

        public int getFailure() {
            return failure;
        }

        public int getAborted() {
            return aborted;
        }

        public String getSuccessRate() {
            return successRate;
        }

        public String getLastWhen() {
            return lastWhen;
        }
    }

    /** One job reference in a table cell: full path, short label, and where to open it. */
    public static final class JobLink {
        public final String fullName;
        public final String shortName;

        JobLink(String fullName) {
            this.fullName = fullName == null ? "" : fullName;
            this.shortName = BuildSample.shortName(this.fullName);
        }

        public String getFullName() {
            return fullName;
        }

        public String getShortName() {
            return shortName;
        }
    }

    public static final class CumRow {
        public final String label;
        public final String period;
        public final String total;
        public final String success;
        public final String waste;

        CumRow(String label, String period, String total, String success, String waste) {
            this.label = label;
            this.period = period;
            this.total = total;
            this.success = success;
            this.waste = waste;
        }

        public String getLabel() {
            return label;
        }
    }

    public List<JobRow> getOthers() {
        return others;
    }

    public List<CumRow> getCumRows() {
        return cumRows;
    }

    public List<PersonRow> getPeople() {
        return people;
    }

    public List<JobPanel> getTabs() {
        return tabs;
    }

    public Kpi getKpi() {
        return kpi;
    }

    public StatusSign getSign() {
        return sign;
    }

    public StatusSign getHome() {
        return home;
    }

    /** Named for Jelly, where {@code empty} is a reserved word in expressions. */
    public boolean isNoBuilds() {
        return empty;
    }

    public int getDays() {
        return days;
    }
}
