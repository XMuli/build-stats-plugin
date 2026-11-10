package io.jenkins.plugins.buildvalue;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** The two lines the About page shows for the installed version. */
class VersionLinesTest {
    @Test
    void aQualifierInParenthesesDropsToTheSecondLine() {
        assertArrayEquals(
                new String[] {"999999-SNAPSHOT", "private-ee3ab155-Venn"},
                BuildValueRootAction.versionLines("999999-SNAPSHOT (private-ee3ab155-Venn)"));
    }

    @Test
    void aPlainVersionStaysOnOneLine() {
        assertArrayEquals(new String[] {"1.2.3", ""}, BuildValueRootAction.versionLines("1.2.3"));
    }

    @Test
    void aMissingVersionIsBlank() {
        assertArrayEquals(new String[] {"", ""}, BuildValueRootAction.versionLines(null));
        assertArrayEquals(new String[] {"", ""}, BuildValueRootAction.versionLines(""));
    }
}
