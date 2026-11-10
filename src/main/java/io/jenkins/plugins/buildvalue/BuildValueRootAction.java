package io.jenkins.plugins.buildvalue;

import hudson.Extension;
import hudson.model.RootAction;
import io.jenkins.plugins.buildvalue.logic.BuildSample;
import io.jenkins.plugins.buildvalue.logic.JobIndex;
import io.jenkins.plugins.buildvalue.logic.MachineMetrics;
import io.jenkins.plugins.buildvalue.logic.StatsView;
import io.jenkins.plugins.buildvalue.logic.Texts;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * The Build Stats page.
 *
 * <p>One request builds one {@link StatsView} and the Jelly views read it. Every piece of state the
 * page can be in — the time window, the selected job, the sidebar search and page, the heatmap
 * period, and which of the three sub-pages is showing — lives in the query string, so any view can
 * be linked to, bookmarked, and reached with the browser's back and forward buttons.
 */
@Extension
public class BuildValueRootAction implements RootAction {
    private static final String CACHE = "build-stats-view";

    @Override
    public String getIconFileName() {
        return "symbol-analytics plugin-ionicons-api";
    }

    @Override
    public String getDisplayName() {
        return Messages.BuildValueRootAction_DisplayName();
    }

    @Override
    public boolean isPrimaryAction() {
        return true;
    }

    @Override
    public String getUrlName() {
        return "build-stats";
    }

    /**
     * The view for this request, computed once and remembered on the request so the layout and the
     * sidebar do not recompute it.
     */
    public StatsView getView() {
        Jenkins.get().checkPermission(Jenkins.READ);
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request != null) {
            Object cached = request.getAttribute(CACHE);
            if (cached instanceof StatsView view) {
                return view;
            }
        }
        int days = days(request);
        int minutes = 15;
        BuildValueConfiguration configuration = BuildValueConfiguration.get();
        if (configuration != null) {
            minutes = configuration.getMinutesPerFailure();
        }
        long now = System.currentTimeMillis();
        List<BuildSample> samples = BuildValueCollector.collect(now, days);
        String banner = null;
        if (samples.isEmpty()) {
            List<BuildSample> remote = DevFeed.samples();
            if (!remote.isEmpty()) {
                samples = remote;
                for (BuildSample sample : remote) {
                    now = Math.max(now, sample.startMs);
                }
                banner = "Build records exported for local development.";
            }
        }
        StatsView view = StatsView.of(
                samples,
                now,
                days,
                ZoneId.systemDefault(),
                minutes,
                banner,
                parameter(request, "job"),
                parameter(request, "q"),
                pageNumber(request),
                parameter(request, "period"));
        if (request != null) {
            request.setAttribute(CACHE, view);
        }
        return view;
    }

    /** Which sub-page to render: overview, computer or about. */
    public String getPage() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        String raw = parameter(request, "page");
        if ("computer".equals(raw) && MachineMetrics.supported()) {
            return "computer";
        }
        if ("about".equals(raw)) {
            return "about";
        }
        return "overview";
    }

    public boolean isMachineSupported() {
        return MachineMetrics.supported();
    }

    /**
     * One prose value in the request's language.
     *
     * <p>Durations and comparison sentences are computed with both languages packed together,
     * because the same number is phrased differently in each. This picks the right one for the
     * locale Jenkins resolved, so the value is already correct in the first response.
     */
    public String text(String packed) {
        return Texts.localized(packed);
    }

    /** Localized result word, so the badge text carries the state as well as the colour. */
    public String stateText(String result) {
        return Texts.stateText(result);
    }

    /** Badge modifier for a result word or key. */
    public String stateClass(String result) {
        return Texts.stateClass(result);
    }

    /**
     * Page text for the request's locale.
     *
     * <p>Jelly's own {@code ${%key}} reads a properties file named after each individual view, so a
     * string used by more than one view would have to be repeated in each. These pages share most
     * of their wording, so the text lives in one bundle — the same one the Java side uses — and the
     * views ask for it through here. Jenkins resolves the locale from the user's choice, falling
     * back to the request's {@code Accept-Language}, and the lookup happens while the response is
     * being written, so the first HTML a browser receives is already in the right language.
     *
     * <p>The overloads are deliberate. Jelly's expression language resolves a method by its exact
     * argument count and will not collapse arguments into a varargs parameter, so a single
     * {@code msg(String, Object...)} silently fails to bind and every label renders blank.
     *
     * @param key a key from {@code Messages.properties}
     */
    public String msg(String key) {
        return Texts.message(key);
    }

    /** True when this response is in Chinese: the visitor's choice, else Jenkins' locale. */
    public boolean isChinese() {
        var request = org.kohsuke.stapler.Stapler.getCurrentRequest2();
        return request != null
                && request.getLocale() != null
                && "zh".equals(request.getLocale().getLanguage());
    }

    /** Probe: same body under a different name, to see whether the name is what fails. */
    public String label(String key) {
        return Texts.message(key);
    }

    /**
     * {@link #msg(String)} for a key with placeholders.
     *
     * <p>Separate names rather than overloads of {@code msg}: Jelly's expression language picks a
     * method by name alone and then applies the arguments, so two methods called {@code msg} are
     * ambiguous to it and the call silently yields nothing.
     */
    public String msg1(String key, Object first) {
        return Texts.message(key, first);
    }

    /** {@link #msg1} for two placeholders. */
    public String msg2(String key, Object first, Object second) {
        return Texts.message(key, first, second);
    }

    /** {@link #msg1} for three placeholders. */
    public String msg3(String key, Object first, Object second, Object third) {
        return Texts.message(key, first, second, third);
    }

    /** Where this plugin's static files live, with a trailing slash. */
    public String getAssets() {
        return contextPath() + "/plugin/build-stats/";
    }

    /** Version Jenkins reports for this plugin, empty when it cannot be read. */
    public String getPluginVersion() {
        try {
            hudson.Plugin plugin = Jenkins.get().getPlugin("build-stats");
            if (plugin == null || plugin.getWrapper() == null) {
                return "";
            }
            String version = plugin.getWrapper().getVersion();
            return version == null ? "" : version;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    /**
     * Splits {@code 999999-SNAPSHOT (private-ee3ab155-Venn)} into the version and the qualifier.
     * A version with no parenthetical qualifier comes back with an empty second line.
     */
    static String[] versionLines(String version) {
        if (version == null || version.isEmpty()) {
            return new String[] {"", ""};
        }
        int open = version.lastIndexOf(" (");
        if (open > 0 && version.endsWith(")")) {
            return new String[] {
                version.substring(0, open),
                version.substring(open + 2, version.length() - 1)
            };
        }
        return new String[] {version, ""};
    }

    /** Installed version without a parenthetical build qualifier. */
    public String getPluginVersionName() {
        return versionLines(getPluginVersion())[0];
    }

    /** Build qualifier Jenkins appends in parentheses, or empty when there is none. */
    public String getPluginVersionBuild() {
        return versionLines(getPluginVersion())[1];
    }

    /** The heatmap period in the query string, or null when the default applies. */
    public String getPeriodParameter() {
        String raw = parameter(Stapler.getCurrentRequest2(), "period");
        return raw.isEmpty() ? null : raw;
    }

    /** Form target for the sidebar search: this page, with no query of its own. */
    public String getSearchAction() {
        return contextPath() + "/" + getUrlName() + "/";
    }

    /** This page with one sub-page selected, keeping the window, search and page. */
    public String pageUrl(String page) {
        return url(page, parameter(Stapler.getCurrentRequest2(), "job"), currentQuery(), currentPage(), currentPeriod());
    }

    /** The overview with one job selected. */
    public String jobUrl(String fullName) {
        return url("overview", fullName, currentQuery(), currentPage(), currentPeriod());
    }

    /** The current view on another sidebar page. */
    public String pagerUrl(int page) {
        return url(getPage(), parameter(Stapler.getCurrentRequest2(), "job"), currentQuery(), page, currentPeriod());
    }

    /** The current view with the search cleared, back on page one. */
    public String getClearSearchUrl() {
        return url(getPage(), parameter(Stapler.getCurrentRequest2(), "job"), "", 1, currentPeriod());
    }

    /** The current view over another time window, back on page one of the sidebar. */
    public String rangeUrl(int days) {
        return url(getPage(), parameter(Stapler.getCurrentRequest2(), "job"), currentQuery(), 1, currentPeriod(), days);
    }

    /** The current view with another heatmap period. */
    public String periodUrl(String period) {
        return url(getPage(), parameter(Stapler.getCurrentRequest2(), "job"), currentQuery(), currentPage(), period);
    }

    private String url(String page, String job, String query, int pageNumber, String period) {
        return url(page, job, query, pageNumber, period, days(Stapler.getCurrentRequest2()));
    }

    /**
     * Build one of this page's URLs. Every value is percent-encoded for a query string, which is a
     * different escaping from the HTML escaping the views apply to the same job names.
     */
    private String url(String page, String job, String query, int pageNumber, String period, int days) {
        List<String> parts = new ArrayList<>(5);
        if (page != null && !page.isEmpty() && !"overview".equals(page)) {
            parts.add("page=" + encode(page));
        }
        if (job != null && !job.isEmpty()) {
            parts.add("job=" + encode(job));
        }
        if (query != null && !query.isEmpty()) {
            parts.add("q=" + encode(query));
        }
        if (pageNumber > 1) {
            parts.add("p=" + pageNumber);
        }
        if (period != null && !period.isEmpty()) {
            parts.add("period=" + encode(period));
        }
        if (days > 0) {
            parts.add("days=" + days);
        }
        // Plain "&" here. These URLs are emitted through Jelly with escape-by-default, which turns
        // it into &amp; in the HTML; writing &amp; here would escape twice.
        String base = contextPath() + "/" + getUrlName() + "/";
        return parts.isEmpty() ? base : base + "?" + String.join("&", parts);
    }

    private String currentQuery() {
        return parameter(Stapler.getCurrentRequest2(), "q");
    }

    private String currentPeriod() {
        return parameter(Stapler.getCurrentRequest2(), "period");
    }

    private int currentPage() {
        return pageNumber(Stapler.getCurrentRequest2());
    }

    public HttpResponse doMachine() {
        Jenkins.get().checkPermission(Jenkins.READ);
        if (!MachineMetrics.supported()) {
            return json("{\"ok\":false,\"supported\":false}");
        }
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request != null) {
            MachineMetrics.pace(request.getParameter("pace"));
        }
        return json(MachineMetrics.json());
    }

    public HttpResponse doActivity() {
        Jenkins.get().checkPermission(Jenkins.READ);
        return json(JenkinsActivity.json());
    }

    /** Jenkins 2.516 has no HttpResponses.okJSON(String). Write the body directly. */
    private static HttpResponse json(String body) {
        String payload = body == null ? "{}" : body;
        return new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest2 request, StaplerResponse2 response, Object node)
                    throws IOException {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(payload);
            }
        };
    }

    private static String encode(String raw) {
        try {
            return URLEncoder.encode(raw, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException ex) {
            return "";
        }
    }

    private static String parameter(StaplerRequest2 request, String name) {
        if (request == null) {
            return "";
        }
        String raw = request.getParameter(name);
        return raw == null ? "" : raw;
    }

    /** 1-based sidebar page. Out-of-range values are clamped later, against the real match count. */
    private static int pageNumber(StaplerRequest2 request) {
        String raw = parameter(request, "p");
        if (raw.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(1, Math.min(JobIndex.PAGE_SIZE * 10_000, Integer.parseInt(raw)));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static int days(StaplerRequest2 request) {
        if (request == null) {
            return 0;
        }
        String raw = request.getParameter("days");
        if ("7".equals(raw) || "30".equals(raw) || "90".equals(raw) || "0".equals(raw)) {
            return Integer.parseInt(raw);
        }
        return 0;
    }

    private static String contextPath() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request == null || request.getContextPath() == null) {
            return "";
        }
        return request.getContextPath();
    }
}
