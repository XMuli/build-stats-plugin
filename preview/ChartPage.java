package io.jenkins.plugins.buildvalue.preview;

import io.jenkins.plugins.buildvalue.logic.Dashboard;
import java.util.ArrayList;
import java.util.List;

/**
 * A chart-only sheet for the standalone harness. NOT the plugin's page.
 *
 * <p>The plugin's page is Jelly now
 * ({@code io/jenkins/plugins/buildvalue/BuildValueRootAction/*.jelly} and
 * {@code lib/buildstats/*.jelly}), and Jelly needs a real Stapler request to render, so no harness
 * outside Jenkins can reproduce it. What this writes is the list of chart SVGs that
 * {@link io.jenkins.plugins.buildvalue.logic.SvgCharts} produced, in a wrapper the harness owns:
 * no cards, no sidebar, no tables, no status plate, no app bar. Use it to eyeball the charts
 * themselves. To see the page, run {@code mvn hpi:run} and open Build Stats.
 *
 * <p>The wrapper deliberately does not imitate the Jenkins card markup the Jelly emits. Copying
 * that markup here is what the move to Jelly removed, and a second copy would drift from it.
 */
final class ChartPage {
    /** The real stylesheet, so the classes inside each SVG resolve the way they do in Jenkins. */
    static final String CSS_FILE = "src/main/webapp/css/build-stats.css";

    private ChartPage() {}

    /** One chart and the heading the harness gives it. The SVG is already escaped by SvgCharts. */
    record Chart(String title, String svg) {}

    /**
     * Every chart the overview holds, in the order the overview lists them. Job-level charts are
     * reached through {@link Dashboard#tabs}; this takes the window-level ones only.
     */
    static List<Chart> overviewCharts(Dashboard dashboard) {
        List<Chart> charts = new ArrayList<>();
        add(charts, "Cumulative builds", dashboard.countChart);
        add(charts, "Machine hours", dashboard.hoursChart);
        add(charts, "Success rate", dashboard.rateChart);
        add(charts, "Results", dashboard.resultPie);
        add(charts, "Machine time by job", dashboard.timePie);
        add(charts, "Triggers", dashboard.triggerPie);
        add(charts, "Builds per day", dashboard.deliveryChart);
        add(charts, "Machine hours per day", dashboard.dailyHoursChart);
        add(charts, "Time of day", dashboard.hourChart);
        add(charts, "Cumulative split", dashboard.cumSplitChart);
        return charts;
    }

    /** The charts of one job panel, named after the job so the sheet stays readable. */
    static List<Chart> jobCharts(Dashboard.JobPanel panel) {
        String name = panel.getShortName();
        List<Chart> charts = new ArrayList<>();
        add(charts, name + " · duration", panel.durationChart);
        add(charts, name + " · stage time", panel.stageBars);
        add(charts, name + " · stage share", panel.stagePie);
        add(charts, name + " · failures by stage", panel.failPie);
        add(charts, name + " · builds per day", panel.dailyCountsChart);
        add(charts, name + " · success rate", panel.rateChart);
        add(charts, name + " · time of day", panel.hourChart);
        add(charts, name + " · stages", panel.stageDots);
        add(charts, name + " · script changes", panel.scriptChart);
        return charts;
    }

    private static void add(List<Chart> charts, String title, String svg) {
        if (svg != null && !svg.isBlank()) {
            charts.add(new Chart(title, svg));
        }
    }

    /** One labelled group of charts: a window, or a job inside it. */
    static String section(String heading, String note, List<Chart> charts) {
        StringBuilder out = new StringBuilder();
        out.append("<section class=\"sheet-group\">\n  <h2>").append(escape(heading)).append("</h2>\n");
        if (note != null && !note.isBlank()) {
            out.append("  <p class=\"sheet-note\">").append(escape(note)).append("</p>\n");
        }
        if (charts.isEmpty()) {
            out.append("  <p class=\"sheet-note\">No charts for this window.</p>\n");
        }
        out.append("  <div class=\"sheet-grid\">\n");
        for (Chart chart : charts) {
            out.append("    <figure class=\"sheet-chart\">\n      <figcaption>")
                    .append(escape(chart.title()))
                    .append("</figcaption>\n      <div class=\"bv-chart__plot\">")
                    .append(chart.svg())
                    .append("</div>\n    </figure>\n");
        }
        out.append("  </div>\n</section>\n");
        return out.toString();
    }

    /** The whole sheet. {@code css} is the contents of {@link #CSS_FILE}. */
    static String document(String title, String css, String body) {
        return """
                <!DOCTYPE html>
                <!--
                  Chart-only preview written by the preview/ harness. This is NOT the plugin's page.

                  The plugin's page is rendered by Jelly inside Jenkins and cannot be produced out
                  here; only the chart SVGs below come from the plugin's own code. Run
                  `mvn hpi:run` and open Build Stats to see the real page.
                -->
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                %s
                  /* Wrapper styling owned by the harness. The plugin's cards are Jelly, not this. */
                  body { background: #f4f6f8; color: #1f2328; font-family: system-ui, sans-serif; margin: 0; }
                  main { margin: 0 auto; max-width: 1180px; padding: 24px 20px 48px; }
                  .sheet-banner { background: #fff5d6; border: 1px solid #e3c975; border-radius: 6px;
                    font-size: 13px; line-height: 1.6; margin: 0 0 24px; padding: 12px 14px; }
                  .sheet-group { margin: 0 0 32px; }
                  .sheet-group h2 { font-size: 15px; margin: 0 0 4px; }
                  .sheet-note { color: #59636e; font-size: 12px; margin: 0 0 12px; }
                  .sheet-grid { display: grid; gap: 16px;
                    grid-template-columns: repeat(auto-fit, minmax(22rem, 1fr)); }
                  .sheet-chart { background: #fff; border: 1px solid #d6dade; border-radius: 6px;
                    margin: 0; padding: 12px; }
                  .sheet-chart figcaption { font-size: 12px; font-weight: 600; margin-bottom: 8px; }
                  .bv-chart__plot svg { display: block; height: auto; max-width: 100%%; }
                  </style>
                </head>
                <body><main>
                <p class="sheet-banner">Chart-only preview. These are the plugin's chart SVGs in a
                wrapper this harness wrote; the page around them (cards, sidebar, tables, status
                plate) is Jelly and only renders inside Jenkins. Run <code>mvn hpi:run</code> to see
                the real page.</p>
                %s
                </main></body></html>
                """
                .formatted(escape(title), css, body);
    }

    private static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
