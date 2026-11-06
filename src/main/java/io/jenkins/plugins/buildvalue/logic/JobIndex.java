package io.jenkins.plugins.buildvalue.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Search and paging over job names, done before anything is rendered.
 *
 * <p>The sidebar on a controller with tens of thousands of jobs must not put one entry per job in
 * the page, so matching and paging happen here and the view only ever sees {@link #PAGE_SIZE}
 * entries. Input comes from the already permission-filtered build samples, so a job the user cannot
 * read never reaches the index.
 */
public final class JobIndex {
    /** Entries per sidebar page. */
    public static final int PAGE_SIZE = 25;

    private final List<JobPage.Entry> all;

    private JobIndex(List<JobPage.Entry> all) {
        this.all = all;
    }

    /**
     * Index the jobs that appear in these samples, sorted by full path so paging is stable across
     * requests. Case-insensitive on the full path, matching how the search filters.
     */
    public static JobIndex of(List<BuildSample> samples) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (BuildSample sample : samples) {
            counts.computeIfAbsent(sample.jobFullName, key -> new int[1])[0]++;
        }
        List<JobPage.Entry> entries = new ArrayList<>(counts.size());
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            entries.add(new JobPage.Entry(
                    entry.getKey(), BuildSample.shortName(entry.getKey()), entry.getValue()[0]));
        }
        entries.sort(Comparator.comparing(
                (JobPage.Entry entry) -> entry.fullName.toLowerCase(Locale.ROOT)).thenComparing(entry -> entry.fullName));
        return new JobIndex(List.copyOf(entries));
    }

    /** Every indexed job, in full-path order. */
    public List<JobPage.Entry> all() {
        return all;
    }

    public int size() {
        return all.size();
    }

    /** True when this exact full path is in the index, so a stale selection can be dropped. */
    public boolean contains(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return false;
        }
        for (JobPage.Entry entry : all) {
            if (entry.fullName.equals(fullName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * One page of matches. {@code query} matches anywhere in the full path, ignoring case.
     * {@code requestedPage} is 1-based and clamped, so a page number left over from a wider search
     * or a longer time window lands on a real page instead of an empty one.
     */
    public JobPage page(String query, int requestedPage) {
        return page(query, requestedPage, PAGE_SIZE);
    }

    JobPage page(String query, int requestedPage, int size) {
        int pageSize = Math.max(1, size);
        String trimmed = query == null ? "" : query.trim();
        String needle = trimmed.toLowerCase(Locale.ROOT);
        List<JobPage.Entry> matched;
        if (needle.isEmpty()) {
            matched = all;
        } else {
            matched = new ArrayList<>();
            for (JobPage.Entry entry : all) {
                if (entry.fullName.toLowerCase(Locale.ROOT).contains(needle)) {
                    matched.add(entry);
                }
            }
        }
        int pages = Math.max(1, (matched.size() + pageSize - 1) / pageSize);
        int page = Math.min(Math.max(1, requestedPage), pages);
        int from = Math.min((page - 1) * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());
        return new JobPage(matched.subList(from, to), matched.size(), page, pages, pageSize, trimmed);
    }

    /**
     * The 1-based page that holds this job under the current search, or 1 when it does not match.
     * Used so a link to a job detail lands on the page that shows it as selected.
     */
    public int pageOf(String fullName, String query) {
        if (fullName == null || fullName.isEmpty()) {
            return 1;
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int position = 0;
        for (JobPage.Entry entry : all) {
            if (!needle.isEmpty() && !entry.fullName.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            if (entry.fullName.equals(fullName)) {
                return position / PAGE_SIZE + 1;
            }
            position++;
        }
        return 1;
    }
}
