package io.jenkins.plugins.buildvalue;

import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Run;
import java.lang.management.ManagementFactory;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import jenkins.model.Jenkins;

/** Logins since this plugin loaded, builds in progress, and recent artifact requests. */
public final class JenkinsActivity {
    private static final long DOWNLOAD_WINDOW_MS = 20_000;
    private static final Map<String, Long> USERS = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedDeque<Download> DOWNLOADS = new ConcurrentLinkedDeque<>();

    private JenkinsActivity() {}

    public static void login(String username) {
        if (usable(username)) {
            USERS.putIfAbsent(username, System.currentTimeMillis());
        }
    }

    public static void logout(String username) {
        if (username != null) {
            USERS.remove(username);
        }
    }

    public static void noteDownload(String username, String path) {
        if (!usable(username) || path == null || path.isBlank()) {
            return;
        }
        DOWNLOADS.addFirst(new Download(username, path, System.currentTimeMillis()));
        while (DOWNLOADS.size() > 40) {
            DOWNLOADS.removeLast();
        }
    }

    public static String json() {
        long now = System.currentTimeMillis();
        StringBuilder json = new StringBuilder(512);
        json.append("{\"ok\":true,\"preview\":false");
        json.append(",\"uptime\":").append(quote(ago(ManagementFactory.getRuntimeMXBean().getUptime())));
        long newestEnd = -1;
        String newestJob = "";
        int newestNumber = 0;
        boolean newestBuilding = false;
        json.append(",\"users\":[");
        boolean first = true;
        for (Map.Entry<String, Long> entry : USERS.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"name\":").append(quote(entry.getKey()))
                    .append(",\"since\":").append(quote(ago(now - entry.getValue()))).append('}');
        }
        json.append("],\"builds\":[");
        first = true;
        Jenkins jenkins = Jenkins.get();
        for (Job<?, ?> job : jenkins.getAllItems(Job.class)) {
            if (!job.hasPermission(hudson.model.Item.READ)) {
                continue;
            }
            Run<?, ?> run = job.getLastBuild();
            if (run == null) {
                continue;
            }
            long end = run.isBuilding() ? now : run.getStartTimeInMillis() + Math.max(0, run.getDuration());
            if (end >= newestEnd) {
                newestEnd = end;
                newestJob = job.getFullDisplayName();
                newestNumber = run.getNumber();
                newestBuilding = run.isBuilding();
            }
            if (!run.isBuilding()) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"job\":").append(quote(job.getFullDisplayName()))
                    .append(",\"number\":").append(run.getNumber())
                    .append(",\"user\":").append(quote(actor(run))).append('}');
        }
        json.append("],\"downloads\":[");
        first = true;
        Iterator<Download> downloads = DOWNLOADS.iterator();
        while (downloads.hasNext()) {
            Download download = downloads.next();
            long age = now - download.at;
            if (age > DOWNLOAD_WINDOW_MS) {
                downloads.remove();
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"user\":").append(quote(download.user))
                    .append(",\"path\":").append(quote(download.path))
                    .append(",\"ago\":").append(quote(ago(age))).append('}');
        }
        json.append("]");
        json.append(",\"lastRun\":{");
        if (newestEnd < 0) {
            json.append("\"ago\":\"\"");
        } else {
            json.append("\"job\":").append(quote(newestJob))
                    .append(",\"number\":").append(newestNumber)
                    .append(",\"building\":").append(newestBuilding)
                    .append(",\"ago\":").append(quote(ago(Math.max(0, now - newestEnd))));
        }
        json.append("}}");
        return json.toString();
    }

    private static String actor(Run<?, ?> run) {
        for (Cause cause : run.getCauses()) {
            if (cause instanceof Cause.UserIdCause user && user.getUserId() != null) {
                return user.getUserId();
            }
        }
        return "—";
    }

    private static boolean usable(String username) {
        return username != null && !username.isBlank()
                && !"anonymous".equals(username) && !"SYSTEM".equals(username);
    }

    private static String ago(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) {
            return seconds + "s";
        }
        return (seconds / 60) + "m";
    }

    private static String quote(String raw) {
        if (raw == null) {
            return "\"\"";
        }
        StringBuilder out = new StringBuilder(raw.length() + 2);
        out.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\', '"' -> out.append('\\').append(c);
                case '\n', '\r' -> out.append(' ');
                default -> out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }

    private record Download(String user, String path, long at) {}
}
