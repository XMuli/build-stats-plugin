package io.jenkins.plugins.buildvalue;

import hudson.Extension;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jenkins.model.Jenkins;
import jenkins.util.HttpServletFilter;

/** Records artifact GETs for about 20 seconds. Does not consume the request. */
@Extension
public class ArtifactWatch implements HttpServletFilter {
    @Override
    public boolean handle(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String uri = request.getRequestURI();
        if (uri != null && uri.contains("/artifact/") && "GET".equals(request.getMethod())) {
            String name = Jenkins.getAuthentication2().getName();
            JenkinsActivity.noteDownload(name, uri);
        }
        return false;
    }
}
