package io.jenkins.plugins.buildvalue.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The bundle lookup the Jelly views use for every label on the page.
 *
 * <p>Worth a test of its own: if this silently returns nothing, every heading, button and column
 * title on the page renders blank, which is exactly how it failed once during development.
 */
class TextsMessageTest {
    /**
     * Without a request the bundle falls back to the JVM default locale, which on a developer's
     * machine may be any language. These tests therefore assert that a key resolves to real text
     * rather than asserting one language's wording.
     */
    @Test
    void aPlainKeyResolves() {
        for (String key : new String[] {
            "nav.overview", "BuildValueRootAction.DisplayName", "page.previous", "kpi.rate"
        }) {
            String text = Texts.message(key);
            assertFalse(text.isEmpty(), key + " resolved to nothing");
            assertFalse(text.equals(key), key + " did not resolve");
        }
    }

    @Test
    void aKeyWithPlaceholdersFormatsItsArguments() {
        // The placeholders are substituted whatever the surrounding wording is.
        String count = Texts.message("search.count", 1, 25, 100);
        assertTrue(count.contains("1") && count.contains("25") && count.contains("100"), count);
        assertFalse(count.contains("{0}"), "placeholder left unfilled: " + count);

        String position = Texts.message("page.position", 2, 4);
        assertTrue(position.contains("2") && position.contains("4"), position);
        assertFalse(position.contains("{"), position);

        String withBuilds = Texts.message("heat.withBuilds", 120);
        assertTrue(withBuilds.contains("120"), withBuilds);
        assertFalse(withBuilds.contains("{"), withBuilds);
    }

    @Test
    void everyKeyTheViewsAskForIsPresent() {
        // A representative key from each page and section. A missing one returns the key itself,
        // which is visible here rather than only on the rendered page.
        String[] keys = {
            "nav.jobs", "search.placeholder", "search.submit", "page.next",
            "tools.summary", "tools.status", "tools.range", "range.7", "range.all",
            "heat.title", "heat.period", "heat.lastYear", "heat.legendLess",
            "sign.latest", "chart.hours", "chart.stage", "kpi.time", "h.accounts",
            "col.account", "col.build", "state.ok", "state.run", "empty.builds",
            "host.title", "host.cpu", "host.now", "about.title", "about.version", "note.title",
        };
        for (String key : keys) {
            String text = Texts.message(key);
            assertFalse(text.isEmpty(), key + " resolved to nothing");
            assertFalse(text.equals(key), key + " is missing from the bundle");
        }
    }

    @Test
    void anUnknownKeyReturnsTheKeyRatherThanFailing() {
        // A missing translation should look odd on the page, not break the request.
        assertEquals("no.such.key", Texts.message("no.such.key"));
        assertEquals("", Texts.message(null));
        assertEquals("", Texts.message("  "));
    }

    @Test
    void resultWordsAndClassesCoverEveryState() {
        for (String key : new String[] {"SUCCESS", "FAILURE", "UNSTABLE", "ABORTED", "NOT_BUILT", "RUNNING"}) {
            assertFalse(Texts.stateText(key).isEmpty(), key + " has no word");
            assertFalse(Texts.stateClass(key).isEmpty(), key + " has no class");
        }
        // The English labels an older row may carry must map to the same thing as the keys.
        assertEquals(Texts.stateText("SUCCESS"), Texts.stateText("Success"));
        assertEquals(Texts.stateClass("NOT_BUILT"), Texts.stateClass("Not built"));
        // Distinct classes where the review asked for distinguishable states.
        assertTrue(!Texts.stateClass("FAILURE").equals(Texts.stateClass("UNSTABLE")));
        assertEquals("", Texts.stateText("WHATEVER"));
        assertEquals("muted", Texts.stateClass("WHATEVER"));
    }

    @Test
    void outsideARequestTheEnglishSideOfAPackedPairIsUsed() {
        // There is no Stapler request in a test, which stands in for the English fallback.
        assertEquals("5 minutes", Texts.localized("5 minutes\u001f5 分钟"));
        assertEquals("plain", Texts.localized("plain"));
        assertEquals("", Texts.localized(null));
    }
}
