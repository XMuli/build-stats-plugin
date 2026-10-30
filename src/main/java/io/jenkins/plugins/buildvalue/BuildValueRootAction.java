package io.jenkins.plugins.buildvalue;

import hudson.Extension;
import hudson.model.RootAction;
import io.jenkins.plugins.buildvalue.logic.BuildSample;
import io.jenkins.plugins.buildvalue.logic.BuildValueComputer;
import io.jenkins.plugins.buildvalue.logic.MachineMetrics;
import io.jenkins.plugins.buildvalue.logic.PageHtml;
import java.io.IOException;
import java.time.ZoneId;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

@Extension
public class BuildValueRootAction implements RootAction {
    private static final String CACHE = "build-value-body";

    @Override
    public String getIconFileName() {
        return "symbol-analytics plugin-ionicons-api";
    }

    @Override
    public String getDisplayName() {
        return "Build Stats";
    }

    @Override
    public boolean isPrimaryAction() {
        return true;
    }

    @Override
    public String getUrlName() {
        return "build-stats";
    }

    public String getBody() {
        Jenkins.get().checkPermission(Jenkins.READ);
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request != null) {
            Object cached = request.getAttribute(CACHE);
            if (cached instanceof String html) {
                return html;
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
                banner = "Build records exported for local development.\u001f本地开发导出的构建记录。";
            }
        }
        String html = PageHtml.body(BuildValueComputer.compute(
                samples, now, days, ZoneId.systemDefault(), minutes, banner));
        if (request != null) {
            request.setAttribute(CACHE, html);
        }
        return html;
    }

    public HttpResponse doMachine() {
        Jenkins.get().checkPermission(Jenkins.READ);
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

    private static int days(StaplerRequest2 request) {
        if (request == null) {
            return 30;
        }
        String raw = request.getParameter("days");
        if ("7".equals(raw) || "30".equals(raw) || "90".equals(raw) || "0".equals(raw)) {
            return Integer.parseInt(raw);
        }
        return 30;
    }
}
