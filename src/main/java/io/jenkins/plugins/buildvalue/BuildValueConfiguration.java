package io.jenkins.plugins.buildvalue;

import hudson.Extension;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

@Extension
public class BuildValueConfiguration extends GlobalConfiguration {
    private int minutesPerFailure = 15;

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
    }

    public FormValidation doCheckMinutesPerFailure(@QueryParameter String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0 || parsed > 480) {
                return FormValidation.error("填 0 到 480 之间的分钟数");
            }
            return FormValidation.ok();
        } catch (NumberFormatException ex) {
            return FormValidation.error("填 0 到 480 之间的分钟数");
        }
    }
}
