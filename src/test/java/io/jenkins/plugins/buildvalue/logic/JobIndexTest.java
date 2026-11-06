package io.jenkins.plugins.buildvalue.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Sidebar search and paging, at the scale a real controller reaches. */
class JobIndexTest {
    private static BuildSample build(String fullName, int number) {
        return new BuildSample(
                fullName,
                "/job/" + fullName + "/",
                "/job/" + fullName + "/" + number + "/",
                "#" + number,
                number,
                1_700_000_000_000L + number,
                1000L,
                0L,
                "SUCCESS",
                "manual",
                "alice",
                List.of());
    }

    /** 120 jobs across several folders, each with one build. */
    private static JobIndex manyJobs() {
        List<BuildSample> samples = new ArrayList<>();
        samples.add(build("acme-engineering/platform-delivery/release/2026.11", 1));
        samples.add(build("acme-engineering/platform-delivery/PR-1842", 2));
        samples.add(build("infrastructure/windows-agents/nightly", 3));
        for (int i = 1; i <= 117; i++) {
            samples.add(build(String.format("services/team-%02d/integration/main", i), i));
        }
        return JobIndex.of(samples);
    }

    @Test
    void pagesAreTwentyFiveLongAndCoverEveryJobExactlyOnce() {
        JobIndex index = manyJobs();
        assertEquals(120, index.size());
        JobPage first = index.page("", 1);
        assertEquals(25, first.entries.size());
        assertEquals(120, first.matches);
        assertEquals(5, first.pages);
        assertEquals(1, first.firstShown);
        assertEquals(25, first.lastShown);
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());

        List<String> seen = new ArrayList<>();
        for (int page = 1; page <= first.pages; page++) {
            for (JobPage.Entry entry : index.page("", page).entries) {
                seen.add(entry.fullName);
            }
        }
        assertEquals(120, seen.size());
        assertEquals(120, seen.stream().distinct().count(), "no job appears on two pages");
    }

    @Test
    void theLastPageHoldsTheRemainderAndHasNoNext() {
        JobPage last = manyJobs().page("", 5);
        assertEquals(20, last.entries.size());
        assertEquals(101, last.firstShown);
        assertEquals(120, last.lastShown);
        assertTrue(last.hasPrevious());
        assertFalse(last.hasNext());
    }

    @Test
    void pagingIsStableAcrossRequests() {
        // Two separate indexes over the same jobs must page identically, or a shared URL would
        // point at a different job later.
        JobPage once = manyJobs().page("", 3);
        JobPage twice = manyJobs().page("", 3);
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        once.entries.forEach(entry -> a.add(entry.fullName));
        twice.entries.forEach(entry -> b.add(entry.fullName));
        assertEquals(a, b);
    }

    @Test
    void sortIsByFullPathIgnoringCase() {
        JobIndex index = JobIndex.of(List.of(
                build("Zebra/job", 1), build("alpha/job", 2), build("Beta/job", 3)));
        List<String> names = new ArrayList<>();
        index.page("", 1).entries.forEach(entry -> names.add(entry.fullName));
        assertEquals(List.of("alpha/job", "Beta/job", "Zebra/job"), names);
    }

    @Test
    void searchMatchesAnywhereInTheFullPathIgnoringCase() {
        JobIndex index = manyJobs();
        // A folder name part way through the path.
        assertEquals(2, index.page("platform-delivery", 1).matches);
        // Case-insensitive on the whole path.
        assertEquals(2, index.page("PLATFORM-DELIVERY", 1).matches);
        // A leaf name.
        assertEquals(1, index.page("nightly", 1).matches);
        // Matching many: one page of 25 out of 117.
        JobPage teams = index.page("integration", 1);
        assertEquals(117, teams.matches);
        assertEquals(25, teams.entries.size());
        assertEquals(5, teams.pages);
    }

    @Test
    void searchIsTrimmedAndSurfacedOnThePage() {
        JobPage page = manyJobs().page("  nightly  ", 1);
        assertEquals("nightly", page.query);
        assertEquals(1, page.matches);
    }

    @Test
    void noMatchGivesAnEmptyPageWithOnePageOfNothing() {
        JobPage page = manyJobs().page("does-not-exist", 1);
        assertTrue(page.isEmpty());
        assertEquals(0, page.matches);
        assertEquals(1, page.pages, "the pager still has a label to show");
        assertEquals(1, page.page);
        assertEquals(0, page.firstShown);
        assertEquals(0, page.lastShown);
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    void aPageBeyondTheEndIsClampedToTheLastPage() {
        // A page number left over from a wider search, or from a longer time window, must land on a
        // real page rather than an empty one.
        JobPage page = manyJobs().page("", 99);
        assertEquals(5, page.page);
        assertEquals(20, page.entries.size());
        // Likewise after a search narrows the result to a single page.
        JobPage narrowed = manyJobs().page("nightly", 4);
        assertEquals(1, narrowed.page);
        assertEquals(1, narrowed.entries.size());
    }

    @Test
    void aPageBelowOneIsClampedUp() {
        assertEquals(1, manyJobs().page("", 0).page);
        assertEquals(1, manyJobs().page("", -5).page);
    }

    @Test
    void pageOfFindsThePageHoldingAJob() {
        JobIndex index = manyJobs();
        // services/team-01 onwards sort after the acme and infrastructure entries.
        String onPageThree = index.page("", 3).entries.get(0).fullName;
        assertEquals(3, index.pageOf(onPageThree, ""));
        // Under a search the position is counted among the matches only.
        String firstMatch = index.page("integration", 2).entries.get(0).fullName;
        assertEquals(2, index.pageOf(firstMatch, "integration"));
        // A job that does not match the search falls back to the first page.
        assertEquals(1, index.pageOf("infrastructure/windows-agents/nightly", "integration"));
        assertEquals(1, index.pageOf("", ""));
    }

    @Test
    void containsDistinguishesAnExactPath() {
        JobIndex index = manyJobs();
        assertTrue(index.contains("infrastructure/windows-agents/nightly"));
        // A prefix is not a member: a stale selection must be dropped, not loosely matched.
        assertFalse(index.contains("infrastructure/windows-agents"));
        assertFalse(index.contains("nightly"));
        assertFalse(index.contains(""));
        assertFalse(index.contains(null));
    }

    @Test
    void buildCountsAreAggregatedPerJob() {
        JobIndex index = JobIndex.of(List.of(
                build("a/b", 1), build("a/b", 2), build("a/b", 3), build("c/d", 1)));
        assertEquals(2, index.size());
        for (JobPage.Entry entry : index.page("", 1).entries) {
            assertEquals("a/b".equals(entry.fullName) ? 3 : 1, entry.builds);
        }
    }

    @Test
    void namesNeedingEscapingAreCarriedThroughUntouched() {
        // The index must not sanitize names; escaping belongs to whoever writes HTML or a URL.
        String awkward = "team/release &amp; hotfix <2026>/main";
        JobIndex index = JobIndex.of(List.of(build(awkward, 1)));
        assertTrue(index.contains(awkward));
        assertEquals(awkward, index.page("", 1).entries.get(0).fullName);
        assertEquals(1, index.page("release &amp; hotfix", 1).matches);
    }

    @Test
    void shortNameIsTheLastSegment() {
        JobIndex index = JobIndex.of(
                List.of(build("acme-engineering/platform-delivery/release/2026.11", 1)));
        JobPage.Entry entry = index.page("", 1).entries.get(0);
        assertEquals("2026.11", entry.shortName);
        assertEquals("acme-engineering/platform-delivery/release/2026.11", entry.fullName);
    }

    @Test
    void tenThousandJobsPageWithoutScanningEverything() {
        List<BuildSample> samples = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            samples.add(build(String.format("org-%03d/project-%02d/main", i / 100, i % 100), 1));
        }
        long startedBuilding = System.nanoTime();
        JobIndex index = JobIndex.of(samples);
        long built = System.nanoTime() - startedBuilding;
        assertEquals(10_000, index.size());

        long startedPaging = System.nanoTime();
        JobPage page = index.page("project-07", 2);
        long paged = System.nanoTime() - startedPaging;

        assertEquals(100, page.matches);
        assertEquals(25, page.entries.size());
        assertEquals(4, page.pages);
        // Generous bounds: the point is that neither step is accidentally quadratic.
        assertTrue(built < 5_000_000_000L, "indexing 10k jobs took " + built / 1_000_000 + "ms");
        assertTrue(paged < 2_000_000_000L, "paging 10k jobs took " + paged / 1_000_000 + "ms");
    }
}
