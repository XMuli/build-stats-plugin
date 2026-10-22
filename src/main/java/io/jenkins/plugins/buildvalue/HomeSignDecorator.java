package io.jenkins.plugins.buildvalue;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.PageDecorator;
import hudson.model.Run;
import hudson.model.View;
import io.jenkins.plugins.buildvalue.logic.PageHtml;
import io.jenkins.plugins.buildvalue.logic.StatusSign;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;

/** One status plate under the Jenkins home job list. It follows the newest real build. */
@Extension
public class HomeSignDecorator extends PageDecorator {
    public HomeSignDecorator() {
        super();
    }

    public String getMarkup() {
        try {
            if (!isHome()) {
                return "";
            }
            Jenkins jenkins = Jenkins.get();
            if (!jenkins.hasPermission(Jenkins.READ)) {
                return "";
            }
            Run<?, ?> running = null;
            Run<?, ?> newest = null;
            for (Job<?, ?> job : jenkins.getAllItems(Job.class)) {
                if (!job.hasPermission(Item.READ)) {
                    continue;
                }
                Run<?, ?> last;
                try {
                    last = job.getLastBuild();
                } catch (RuntimeException ex) {
                    continue;
                }
                if (last == null) {
                    continue;
                }
                long when = last.getStartTimeInMillis();
                boolean building = last.isBuilding() || last.getResult() == null;
                if (building && (running == null || when >= running.getStartTimeInMillis())) {
                    running = last;
                }
                if (newest == null || when >= newest.getStartTimeInMillis()) {
                    newest = last;
                }
            }
            Run<?, ?> chosen = running != null ? running : newest;
            if (chosen == null || chosen.getParent() == null) {
                return PageHtml.homeMarkup(DevFeed.sign());
            }
            boolean live = chosen.isBuilding() || chosen.getResult() == null;
            String result = live ? "RUNNING" : String.valueOf(chosen.getResult());
            StatusSign sign = StatusSign.of(
                    result, chosen.getParent().getFullName(), chosen.getNumber(), buildHref(chosen));
            return PageHtml.homeMarkup(sign);
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static boolean isHome() {
        var request = Stapler.getCurrentRequest2();
        if (request == null) {
            return false;
        }
        View view = request.findAncestorObject(View.class);
        View primary = Jenkins.get().getPrimaryView();
        return view != null && primary != null && view == primary;
    }

    private static String buildHref(Run<?, ?> run) {
        String path = run.getUrl();
        if (path == null || path.isBlank()) {
            return "";
        }
        var request = Stapler.getCurrentRequest2();
        String context = request == null || request.getContextPath() == null ? "" : request.getContextPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return context + path;
    }
}
