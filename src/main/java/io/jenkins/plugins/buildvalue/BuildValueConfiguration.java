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
                return FormValidation.error("Enter a number of minutes from 0 to 480");
            }
            return FormValidation.ok();
        } catch (NumberFormatException ex) {
            return FormValidation.error("Enter a number of minutes from 0 to 480");
        }
    }
}
