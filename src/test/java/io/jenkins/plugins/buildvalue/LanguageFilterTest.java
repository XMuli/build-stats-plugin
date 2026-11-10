package io.jenkins.plugins.buildvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class LanguageFilterTest {
    @Test
    void onlyChineseAndEnglishAreOverrides() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, LanguageFilter.localeOf("zh"));
        assertEquals(Locale.ENGLISH, LanguageFilter.localeOf("en"));
        assertNull(LanguageFilter.localeOf(null));
        assertNull(LanguageFilter.localeOf("fr"));
        assertNull(LanguageFilter.localeOf(""));
    }
}
