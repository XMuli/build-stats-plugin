package io.jenkins.plugins.buildvalue;

import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * The plugin's section on the global configuration page.
 *
 * <p>{@link GlobalConfiguration#configure} only binds the submitted form onto these setters, so
 * each setter saves; otherwise a change would be lost on the next restart.
 */
@Extension
public class BuildValueConfiguration extends GlobalConfiguration {
    private int minutesPerFailure = 15;

    /** Inverted so a configuration saved before this field existed loads with the card shown. */
    private boolean hideHomeCard;

    /** Inverted so a configuration saved before this field existed keeps sampling available. */
    private boolean hideHostStats;

    public BuildValueConfiguration() {
        load();
    }

    public static BuildValueConfiguration get() {
        return GlobalConfiguration.all().get(BuildValueConfiguration.class);
    }

    public int getMinutesPerFailure() {
        if (minutesPerFailure < 0) {
            return 15;
        }
        return Math.min(480, minutesPerFailure);
    }

    @DataBoundSetter
    public void setMinutesPerFailure(int minutesPerFailure) {
        this.minutesPerFailure = Math.max(0, Math.min(480, minutesPerFailure));
        save();
    }

    /** Whether the status card is added to the Jenkins home page and to pipeline pages. */
    public boolean isShowHomeCard() {
        return !hideHomeCard;
    }

    @DataBoundSetter
    public void setShowHomeCard(boolean showHomeCard) {
        this.hideHomeCard = !showHomeCard;
        save();
    }

    /**
     * Whether This computer is offered and the controller samples its own CPU, memory, disk and
     * network. Sampling still runs only while that page is open.
     */
    public boolean isHostStats() {
        return !hideHostStats;
    }

    @DataBoundSetter
    public void setHostStats(boolean hostStats) {
        this.hideHostStats = !hostStats;
        save();
    }

    /**
     * Validate the field on the global configuration page.
     *
     * <p>Only an administrator may reach this. The method is routable by Stapler, so without the
     * check any user who can read Jenkins could call it, and without {@code @POST} it could be
     * triggered by a crafted link from another site.
     */
    @POST
    public FormValidation doCheckMinutesPerFailure(@QueryParameter String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0 || parsed > 480) {
                return FormValidation.error("Enter a number of minutes from 0 to 480");
            }
            return FormValidation.ok();
        } catch (NumberFormatException ex) {
            return FormValidation.error("Enter a number of minutes from 0 to 480");
        }
    }
}
