package io.jenkins.plugins.buildvalue;

import hudson.model.Run;
import io.jenkins.plugins.buildvalue.logic.StageSample;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads stage timings from Pipeline: Stage View when that plugin is installed.
 * The business Jenkins already shows these stages, so the charts can name Checkout and Build.
 * Missing the plugin leaves stage charts empty and does not stop the rest of the page.
 */
final class StageReader {
    private static final Logger LOG = Logger.getLogger(StageReader.class.getName());
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private StageReader() {}

    static List<StageSample> read(Run<?, ?> run) {
        try {
            Class<?> workflowRun = Class.forName("org.jenkinsci.plugins.workflow.job.WorkflowRun");
            if (!workflowRun.isInstance(run)) {
                return List.of();
            }
            Class<?> runExt = Class.forName("com.cloudbees.workflow.rest.external.RunExt");
            Method create = runExt.getMethod("create", workflowRun);
            Object ext = create.invoke(null, run);
            if (ext == null) {
                return List.of();
            }
            Object stages = ext.getClass().getMethod("getStages").invoke(ext);
            if (!(stages instanceof List<?> list)) {
                return List.of();
            }
            List<StageSample> out = new ArrayList<>();
            for (Object stage : list) {
                if (stage == null) {
                    continue;
                }
                String name = String.valueOf(stage.getClass().getMethod("getName").invoke(stage));
                Object duration = stage.getClass().getMethod("getDurationMillis").invoke(stage);
                Object status = stage.getClass().getMethod("getStatus").invoke(stage);
                long millis = duration instanceof Number number ? number.longValue() : 0L;
                out.add(new StageSample(name, millis, status == null ? "OTHER" : status.toString()));
            }
            return List.copyOf(out);
        } catch (ClassNotFoundException ex) {
            report("阶段耗时未读取：没有 Pipeline: Stage View", ex);
            return List.of();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            report("阶段耗时未读取", ex);
            return List.of();
        }
    }

    private static void report(String message, Exception ex) {
        if (REPORTED.compareAndSet(false, true)) {
            LOG.log(Level.INFO, message, ex);
        }
    }
}
