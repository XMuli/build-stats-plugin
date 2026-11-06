package io.jenkins.plugins.buildvalue;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.PageDecorator;
import hudson.model.Run;
import hudson.model.View;
import io.jenkins.plugins.buildvalue.logic.StatusSign;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;

/**
 * The status card on the Jenkins home page and on each pipeline page.
 *
 * <p>This class only decides which build the card describes. The markup is in {@code footer.jelly},
 * so the card picks up Jenkins card, button and tooltip styling instead of carrying its own.
 */
@Extension
public class HomeSignDecorator extends PageDecorator {
    public HomeSignDecorator() {
        super();
    }

    /** The plate to draw, or an absent one when this page should not show a card. */
    public StatusSign getSign() {
        try {
            Jenkins jenkins = Jenkins.get();
            if (!jenkins.hasPermission(Jenkins.READ)) {
                return StatusSign.none();
            }
            var request = Stapler.getCurrentRequest2();
            if (request == null) {
                return StatusSign.none();
            }
            Job<?, ?> current = request.findAncestorObject(Job.class);
            if (current != null) {
                return signOf(current);
            }
            if (!isHome()) {
                return StatusSign.none();
            }
            Run<?, ?> chosen = newest(jenkins);
            if (chosen == null || chosen.getParent() == null) {
                return DevFeed.sign();
            }
            return signOf(chosen);
        } catch (RuntimeException ex) {
            return StatusSign.none();
        }
    }

    /** "job" on a pipeline page, "home" on the primary view, so the card can place itself. */
    public String getPlace() {
        try {
            var request = Stapler.getCurrentRequest2();
            if (request != null && request.findAncestorObject(Job.class) != null) {
                return "job";
            }
        } catch (RuntimeException ex) {
            return "home";
        }
        return "home";
    }

    /** Where the Build Stats page lives, for the card's link. */
    public String getStatsUrl() {
        var request = Stapler.getCurrentRequest2();
        String context = request == null || request.getContextPath() == null ? "" : request.getContextPath();
        return context + "/build-stats";
    }

    /**
     * Labels for the card's own controls.
     *
     * <p>A page decorator's view has its own name, so Jelly's {@code ${%key}} would look for a
     * properties file beside this class rather than the plugin's one bundle. Reading the bundle
     * here keeps all of the plugin's wording in one file.
     */
    public String getDockLabel() {
        return io.jenkins.plugins.buildvalue.logic.Texts.message("sign.dock");
    }

    public String getCloseLabel() {
        return io.jenkins.plugins.buildvalue.logic.Texts.message("sign.close");
    }

    public String getReopenLabel() {
        return io.jenkins.plugins.buildvalue.logic.Texts.message("sign.reopen");
    }

    public String getPageTitle() {
        return Messages.BuildValueRootAction_DisplayName();
    }

    /** Where this plugin's static files live, with a trailing slash, for the plate's artwork. */
    public String getAssets() {
        var request = Stapler.getCurrentRequest2();
        String context = request == null || request.getContextPath() == null ? "" : request.getContextPath();
        return context + "/plugin/build-stats/";
    }

    /** Default logo, so the card can show a replacement saved in this browser. */
    public String getLogoUrl() {
        var request = Stapler.getCurrentRequest2();
        String context = request == null || request.getContextPath() == null ? "" : request.getContextPath();
        return context + "/plugin/build-stats/images/logo.jpg";
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
