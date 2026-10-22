package io.jenkins.plugins.buildvalue;

import hudson.Extension;
import jenkins.security.SecurityListener;
import org.springframework.security.core.userdetails.UserDetails;

@Extension
public class LoginTracker extends SecurityListener {
    @Override
    protected void loggedIn(String username) {
        JenkinsActivity.login(username);
    }

    @Override
    protected void loggedOut(String username) {
        JenkinsActivity.logout(username);
    }

    @Override
    protected void authenticated2(UserDetails details) {
        if (details != null) {
            JenkinsActivity.login(details.getUsername());
        }
    }
}
