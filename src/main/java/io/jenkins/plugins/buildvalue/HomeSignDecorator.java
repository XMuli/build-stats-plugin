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

/** The status card on the Jenkins home page and on each pipeline page. */
@Extension
public class HomeSignDecorator extends PageDecorator {
    public HomeSignDecorator() {
        super();
    }

    public String getMarkup() {
        try {
            Jenkins jenkins = Jenkins.get();
            if (!jenkins.hasPermission(Jenkins.READ)) {
                return "";
            }
            var request = Stapler.getCurrentRequest2();
            if (request == null) {
                return "";
            }
            Job<?, ?> current = request.findAncestorObject(Job.class);
            if (current != null) {
                return PageHtml.homeMarkup(signOf(current), "job");
            }
            if (!isHome()) {
                return "";
            }
            Run<?, ?> chosen = newest(jenkins);
            if (chosen == null || chosen.getParent() == null) {
                return PageHtml.homeMarkup(DevFeed.sign(), "home");
            }
            return PageHtml.homeMarkup(signOf(chosen), "home");
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static Run<?, ?> newest(Jenkins jenkins) {
        Run<?, ?> running = null;
        Run<?, ?> newest = null;
        for (Job<?, ?> job : jenkins.getAllItems(Job.class)) {
            if (!job.hasPermission(Item.READ)) {
                continue;
            }
            Run<?, ?> last = lastBuild(job);
            if (last == null) {
                continue;
            }
            long when = last.getStartTimeInMillis();
            if (live(last) && (running == null || when >= running.getStartTimeInMillis())) {
                running = last;
            }
            if (newest == null || when >= newest.getStartTimeInMillis()) {
                newest = last;
            }
        }
        return running != null ? running : newest;
    }

    private static StatusSign signOf(Job<?, ?> job) {
        if (!job.hasPermission(Item.READ)) {
            return StatusSign.none();
        }
        return signOf(lastBuild(job));
    }

    private static StatusSign signOf(Run<?, ?> run) {
        if (run == null || run.getParent() == null) {
            return StatusSign.none();
        }
        String result = live(run) ? "RUNNING" : String.valueOf(run.getResult());
        return StatusSign.of(result, run.getParent().getFullName(), run.getNumber(), buildHref(run));
    }

    private static Run<?, ?> lastBuild(Job<?, ?> job) {
        try {
            return job.getLastBuild();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean live(Run<?, ?> run) {
        return run.isBuilding() || run.getResult() == null;
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
