package io.jenkins.plugins.buildvalue.logic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The sampler keeps working only while the computer page is still asking for samples. */
class MachineMetricsWatchTest {
    @Test
    void samplingStopsWhenThePageStopsAsking() {
        long now = 10_000_000_000L;
        assertFalse(MachineMetrics.watching(0, now));
        assertTrue(MachineMetrics.watching(now - 1_000_000_000L, now));
        assertFalse(MachineMetrics.watching(now - MachineMetrics.IDLE_NANOS, now));
        assertFalse(MachineMetrics.watching(now - MachineMetrics.IDLE_NANOS - 1, now));
    }
}
