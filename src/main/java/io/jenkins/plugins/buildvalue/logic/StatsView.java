package io.jenkins.plugins.buildvalue.logic;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything one request needs to render, assembled once in Java so the Jelly views only read
 * values.
 *
 * <p>Queries, aggregation and permission filtering happen before this object exists. The sidebar
 * carries one page of jobs rather than all of them, and the job detail is built only for the job
 * actually selected, so neither the DOM nor the work per request grows with the number of jobs on
 * the controller.
 */
public final class StatsView {
    private final Dashboard dashboard;
    private final JobIndex index;
    private final JobPage jobPage;
    private final Heatmap heatmap;
    private final Heatmap.Grid heatGrid;
    private final Dashboard.JobPanel selected;
    private final String selectedName;
    private final int days;
    private final String query;
    private final ZoneId zone;

    StatsView(
            Dashboard dashboard,
            JobIndex index,
            JobPage jobPage,
            Heatmap heatmap,
            Heatmap.Grid heatGrid,
            Dashboard.JobPanel selected,
            String selectedName,
            int days,
            String query,
            ZoneId zone) {
        this.dashboard = dashboard;
        this.index = index;
        this.jobPage = jobPage;
        this.heatmap = heatmap;
        this.heatGrid = heatGrid;
        this.selected = selected;
        this.selectedName = selectedName == null ? "" : selectedName;
        this.days = days;
        this.query = query == null ? "" : query;
        this.zone = zone;
    }

    /**
     * Build the view for one request.
     *
     * @param samples builds the user may read, already collected
     * @param now wall clock for this request
     * @param days time window in days, 0 for all time
     * @param zone zone the statistics are computed in
     * @param minutesPerFailure configured cost of one failure
     * @param banner optional notice, may be null
     * @param jobName full path of the job to detail, empty or unknown for the overview
     * @param query sidebar search term
     * @param page requested 1-based sidebar page
     * @param periodId heatmap period id, null for the default
     */
    public static StatsView of(
            List<BuildSample> samples,
            long now,
            int days,
            ZoneId zone,
            int minutesPerFailure,
            String banner,
            String jobName,
            String query,
            int page,
            String periodId) {
        Dashboard dashboard = BuildValueComputer.compute(samples, now, days, zone, minutesPerFailure, banner);
        JobIndex index = JobIndex.of(samples);
        // A job selected before the window changed may no longer have builds in range. Drop the
        // selection rather than show an empty detail for a job that is not in this window.
        String wanted = jobName == null ? "" : jobName;
        Dashboard.JobPanel selected = null;
        for (Dashboard.JobPanel panel : dashboard.tabs) {
            if (panel.jobName.equals(wanted)) {
                selected = panel;
                break;
            }
        }
        String selectedName = selected == null ? "" : selected.jobName;
        // Land on the page that shows the selected job, so a shared URL highlights it.
        int wantedPage = selected != null && page <= 0 ? index.pageOf(selectedName, query) : Math.max(1, page);
        JobPage jobPage = index.page(query, wantedPage);
        // The heatmap reads every visible build, not just the window, so its own period controls it.
        Heatmap heatmap = Heatmap.of(samples, now, zone);
        Heatmap.Grid grid = heatmap.grid(heatmap.period(periodId));
        return new StatsView(
                dashboard, index, jobPage, heatmap, grid, selected, selectedName, days, query, zone);
    }

    public Dashboard getDashboard() {
        return dashboard;
    }

    public JobPage getJobPage() {
        return jobPage;
    }

    public int getJobCount() {
        return index.size();
    }

    public Heatmap getHeatmap() {
        return heatmap;
    }

    public Heatmap.Grid getHeat() {
        return heatGrid;
    }

    /** The job detail to render, or null on the overview. */
    public Dashboard.JobPanel getSelected() {
        return selected;
    }

    public boolean isJobSelected() {
        return selected != null;
    }

    public String getSelectedName() {
        return selectedName;
    }

    public int getDays() {
        return days;
    }

    public String getQuery() {
        return query;
    }

    public String getZoneId() {
        return zone.getId();
    }

    /**
     * True when the window holds no visible builds.
     *
     * <p>Not named {@code isEmpty}: {@code empty} is a reserved word in Jelly's expression
     * language, so {@code ${view.empty}} fails to parse.
     */
    public boolean isNoBuilds() {
        return dashboard.empty;
    }

    /** The plate for the current page: the selected job's, or the whole window's. */
    public StatusSign getSign() {
        return selected == null ? dashboard.sign : selected.sign;
    }

    /**
     * The metric tiles for the current page.
     *
     * <p>Built here rather than listed in the view, so the page is driven by data and a tile is
     * added or reworded in one place.
     */
    public List<Panel.Metric> getMetrics() {
        List<Panel.Metric> metrics = new ArrayList<>(4);
        if (selected != null) {
            metrics.add(new Panel.Metric(
                    Texts.message("kpi.time"), Texts.localized(selected.machineTime), ""));
            metrics.add(new Panel.Metric(
                    Texts.message("kpi.count"),
                    selected.builds,
                    Texts.message("kpi.accounts", selected.people.size())));
            metrics.add(new Panel.Metric(
                    Texts.message("kpi.rate"),
                    selected.success,
                    Texts.message("kpi.average", Texts.localized(selected.average))));
            metrics.add(new Panel.Metric(Texts.message("kpi.fail"), selected.failures, ""));
            return List.copyOf(metrics);
        }
        Dashboard.Kpi kpi = dashboard.kpi;
        metrics.add(new Panel.Metric(
                Texts.message("kpi.count"), kpi.builds, Texts.localized(kpi.buildsDelta)));
        metrics.add(new Panel.Metric(Texts.message("kpi.rate"), kpi.success, ""));
        metrics.add(new Panel.Metric(
                Texts.message("kpi.time"), Texts.localized(kpi.duration), Texts.localized(kpi.durationDelta)));
        metrics.add(new Panel.Metric(Texts.message("kpi.ok"), dashboard.deliveryText, ""));
        return List.copyOf(metrics);
    }

    /**
     * The charts for the current page, in reading order.
     *
     * <p>Every chart the previous version rendered is here: for the whole window the cumulative
     * machine hours, the cumulative result split, build counts, time of day, success rate and the
     * result, machine-time and trigger rings; for one job its duration, counts, time of day,
     * success rate and, where the job has stages, the stage status grid, stage name changes,
     * average stage time and the failed-stage split.
     */
    public List<Panel.Chart> getCharts() {
        List<Panel.Chart> charts = new ArrayList<>(9);
        if (selected != null) {
            charts.add(chart("chart.trend", selected.durationChart));
            charts.add(chart("chart.count", selected.dailyCountsChart));
            charts.add(chart("chart.hour", selected.hourChart));
            charts.add(chart("chart.rate", selected.rateChart));
            if (selected.hasStages) {
                charts.add(new Panel.Chart(
                        Texts.message("chart.stage"), selected.stageDots, Texts.message("chart.stageNote")));
                charts.add(new Panel.Chart(
                        Texts.message("chart.names"), selected.scriptChart, Texts.message("chart.namesNote")));
                charts.add(chart("chart.stageTime", selected.stageBars));
                charts.add(chart("chart.failStage", selected.failPie));
            }
            return List.copyOf(charts);
        }
        charts.add(chart("chart.hours", dashboard.hoursChart));
        charts.add(chart("chart.split", dashboard.cumSplitChart));
        charts.add(chart("chart.count", dashboard.deliveryChart));
        charts.add(chart("chart.hour", dashboard.hourChart));
        charts.add(chart("chart.rate", dashboard.rateChart));
        charts.add(chart("chart.result", dashboard.resultPie));
        charts.add(chart("chart.mtime", dashboard.timePie));
        charts.add(chart("chart.trigger", dashboard.triggerPie));
        return List.copyOf(charts);
    }

    private static Panel.Chart chart(String titleKey, String svg) {
        return new Panel.Chart(Texts.message(titleKey), svg, "");
    }

    public boolean hasSign() {
        return getSign().present();
    }
}
