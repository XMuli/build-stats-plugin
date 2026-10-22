package io.jenkins.plugins.buildvalue;

import hudson.Extension;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Run;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import io.jenkins.plugins.buildvalue.logic.BuildSample;
import io.jenkins.plugins.buildvalue.logic.StageSample;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

final class BuildValueCollector {
    private BuildValueCollector() {}

    static List<BuildSample> collect(long now, int days) {
        Jenkins jenkins = Jenkins.get();
        jenkins.checkPermission(Jenkins.READ);
        String root = contextPath();
        long start = days > 0 ? now - 2L * days * 86_400_000L : 0L;
        List<BuildSample> samples = new ArrayList<>();
        for (Job<?, ?> job : jenkins.getAllItems(Job.class)) {
            if (!job.hasPermission(hudson.model.Item.READ)) {
                continue;
            }
            try {
                collectJob(job, root, start, now, samples);
            } catch (RuntimeException ex) {
                // One broken job should not blank the page.
            }
        }
        return List.copyOf(samples);
    }

    private static void collectJob(Job<?, ?> job, String root, long start, long now, List<BuildSample> samples) {
        boolean stagesWanted = !"other".equals(BuildSample.group(job.getFullName()));
        String jobUrl = root + "/" + job.getUrl();
        Iterable<? extends Run<?, ?>> runs = start <= 0 ? job.getBuilds() : job.getBuilds().byTimestamp(start, now + 1);
        for (Run<?, ?> run : runs) {
            long enqueued = run.getTimeInMillis();
            long started = run.getStartTimeInMillis();
            long when = started > 0 ? started : enqueued;
            if (when <= 0 || when > now) {
                continue;
            }
            if (start > 0 && enqueued < start && when < start) {
                continue;
            }
            boolean running = run.isBuilding() || run.getResult() == null;
            long queue = started > enqueued && enqueued > 0 ? started - enqueued : 0L;
            long duration = running ? 0L : Math.max(0L, run.getDuration());
            List<StageSample> stages = stagesWanted ? StageReader.read(run) : List.of();
            samples.add(new BuildSample(
                    job.getFullName(),
                    jobUrl,
                    root + "/" + run.getUrl(),
                    run.getDisplayName(),
                    run.getNumber(),
                    when,
                    duration,
                    queue,
                    running ? "RUNNING" : run.getResult().toString(),
                    triggerOf(run.getCauses()),
                    actorOf(run.getCauses()),
                    stages));
        }
    }

    static String triggerOf(List<Cause> causes) {
        boolean scm = false;
        boolean timer = false;
        boolean upstream = false;
        if (causes != null) {
            for (Cause cause : causes) {
                if (cause instanceof Cause.UserIdCause) {
                    return "manual";
                }
                if (cause instanceof SCMTrigger.SCMTriggerCause) {
                    scm = true;
                } else if (cause instanceof TimerTrigger.TimerTriggerCause) {
                    timer = true;
                } else if (cause instanceof Cause.UpstreamCause) {
                    upstream = true;
                }
            }
        }
        if (scm) {
            return "scm";
        }
        if (timer) {
            return "timer";
        }
        if (upstream) {
            return "upstream";
        }
        return "other";
    }

    static String actorOf(List<Cause> causes) {
        if (causes == null) {
            return "";
        }
        for (Cause cause : causes) {
            if (cause instanceof Cause.UserIdCause user) {
                String id = user.getUserId();
                if (id != null && !id.isBlank()) {
                    return id;
                }
            }
        }
        return "";
    }

    private static String contextPath() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request == null || request.getContextPath() == null) {
            return "";
        }
        return request.getContextPath();
    }
}
