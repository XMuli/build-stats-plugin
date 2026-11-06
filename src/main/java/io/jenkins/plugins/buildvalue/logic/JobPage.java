package io.jenkins.plugins.buildvalue.logic;

import java.util.List;

/**
 * One page of the job sidebar. The list is already filtered by permission, matched against the
 * search term and cut to the page, so the view renders a bounded number of entries no matter how
 * many jobs the controller holds.
 */
public final class JobPage {
    /** Entries on this page, in stable full-path order. */
    public final List<Entry> entries;
    /** How many jobs matched the search across every page. */
    public final int matches;
    /** 1-based page number, clamped into range. */
    public final int page;
    /** How many pages the matches fill, at least 1 so the pager always has a label. */
    public final int pages;
    /** Entries per page. */
    public final int size;
    /** 1-based index of the first entry shown, or 0 when nothing matched. */
    public final int firstShown;
    /** 1-based index of the last entry shown, or 0 when nothing matched. */
    public final int lastShown;
    /** The search term that produced this page, trimmed; empty when unfiltered. */
    public final String query;

    JobPage(List<Entry> entries, int matches, int page, int pages, int size, String query) {
        this.entries = List.copyOf(entries);
        this.matches = matches;
        this.page = page;
        this.pages = pages;
        this.size = size;
        this.query = query == null ? "" : query;
        this.firstShown = entries.isEmpty() ? 0 : (page - 1) * size + 1;
        this.lastShown = entries.isEmpty() ? 0 : this.firstShown + entries.size() - 1;
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < pages;
    }

    /**
     * True when the search matched nothing.
     *
     * <p>Not named {@code isEmpty}: {@code empty} is a reserved word in Jelly's expression
     * language, so {@code ${page.empty}} fails to parse.
     */
    public boolean isNoMatches() {
        return entries.isEmpty();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** One job in the sidebar. {@code fullName} is the Jenkins full path, shown in full. */
    public static final class Entry {
        public final String fullName;
        public final String shortName;
        public final int builds;

        public Entry(String fullName, String shortName, int builds) {
            this.fullName = fullName == null ? "" : fullName;
            this.shortName = shortName == null ? "" : shortName;
            this.builds = builds;
        }

        public String getFullName() {
            return fullName;
        }

        public String getShortName() {
            return shortName;
        }

        public int getBuilds() {
            return builds;
        }
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public int getMatches() {
        return matches;
    }

    public int getPage() {
        return page;
    }

    public int getPages() {
        return pages;
    }

    public int getFirstShown() {
        return firstShown;
    }

    public int getLastShown() {
        return lastShown;
    }

    public String getQuery() {
        return query;
    }
}
