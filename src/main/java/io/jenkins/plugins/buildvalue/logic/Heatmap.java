package io.jenkins.plugins.buildvalue.logic;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Build counts per calendar day, and the periods the year selector offers.
 *
 * <p>The default period is the last 12 months <em>ending today</em>: today minus twelve calendar
 * months plus one day, through today, both ends included. On 2026-11-06 that is 2025-11-07 through
 * 2026-11-06. Working in {@link LocalDate} in the zone the rest of the statistics use means leap
 * days and daylight-saving changes are handled by the calendar rather than by counting
 * milliseconds, and a day with no builds is simply absent from the counts and read as zero.
 */
public final class Heatmap {
    private final Map<LocalDate, Integer> counts;
    private final LocalDate today;
    private final List<Period> periods;

    private Heatmap(Map<LocalDate, Integer> counts, LocalDate today, List<Period> periods) {
        this.counts = counts;
        this.today = today;
        this.periods = periods;
    }

    public static Heatmap of(List<BuildSample> samples, long now, ZoneId zone) {
        Map<LocalDate, Integer> counts = new TreeMap<>();
        for (BuildSample sample : samples) {
            if (sample.startMs <= 0) {
                continue;
            }
            counts.merge(Instant.ofEpochMilli(sample.startMs).atZone(zone).toLocalDate(), 1, Integer::sum);
        }
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        List<Period> periods = new ArrayList<>();
        periods.add(Period.lastTwelveMonths(today));
        // Whole calendar years that actually hold builds, newest first, so history stays reachable.
        List<Integer> years = new ArrayList<>();
        for (LocalDate day : counts.keySet()) {
            if (!years.contains(day.getYear())) {
                years.add(day.getYear());
            }
        }
        years.sort((left, right) -> Integer.compare(right, left));
        for (int year : years) {
            periods.add(Period.calendarYear(year));
        }
        return new Heatmap(counts, today, List.copyOf(periods));
    }

    /** Periods the selector offers. The first is the default, "last 12 months". */
    public List<Period> getPeriods() {
        return periods;
    }

    public Period getDefaultPeriod() {
        return periods.get(0);
    }

    /** The period with this id, or the default when the id is unknown or missing. */
    public Period period(String id) {
        if (id != null && !id.isBlank()) {
            for (Period candidate : periods) {
                if (candidate.id.equals(id)) {
                    return candidate;
                }
            }
        }
        return getDefaultPeriod();
    }

    public int countOn(LocalDate day) {
        Integer value = counts.get(day);
        return value == null ? 0 : value;
    }

    public LocalDate getToday() {
        return today;
    }

    /** The grid for one period: every day in range with its count, oldest first. */
    public Grid grid(Period period) {
        List<Day> days = new ArrayList<>();
        int max = 0;
        int total = 0;
        int blank = 0;
        for (LocalDate day = period.start; !day.isAfter(period.end); day = day.plusDays(1)) {
            int count = countOn(day);
            days.add(new Day(day, count));
            total += count;
            if (count == 0) {
                blank++;
            }
            if (count > max) {
                max = count;
            }
        }
        return new Grid(period, List.copyOf(days), max, total, blank, counts);
    }

    /** A selectable heatmap period. */
    public static final class Period {
        public final String id;
        public final LocalDate start;
        public final LocalDate end;
        /** True for the rolling 12-month default, false for a calendar year. */
        public final boolean rolling;

        private Period(String id, LocalDate start, LocalDate end, boolean rolling) {
            this.id = id;
            this.start = start;
            this.end = end;
            this.rolling = rolling;
        }

        /**
         * Today minus twelve calendar months plus one day, through today. Both ends are included,
         * so the window is one day longer than the bare month arithmetic.
         */
        static Period lastTwelveMonths(LocalDate today) {
            return new Period("last", today.minusMonths(12).plusDays(1), today, true);
        }

        static Period calendarYear(int year) {
            return new Period(
                    Integer.toString(year), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), false);
        }

        public String getId() {
            return id;
        }

        public boolean isRolling() {
            return rolling;
        }

        public String getStart() {
            return start.toString();
        }

        public String getEnd() {
            return end.toString();
        }

        public long getDays() {
            return ChronoUnit.DAYS.between(start, end) + 1;
        }
    }

    /** One day cell. */
    public static final class Day {
        public final LocalDate date;
        public final int count;

        Day(LocalDate date, int count) {
            this.date = date;
            this.count = count;
        }

        public String getDate() {
            return date.toString();
        }

        public int getCount() {
            return count;
        }
    }

    /** Every day of one period, plus the figures the side panel shows. */
    public static final class Grid {
        public final Period period;
        public final List<Day> days;
        public final int max;
        public final int total;
        public final int blank;
        private final Map<LocalDate, Integer> counts;

        Grid(Period period, List<Day> days, int max, int total, int blank, Map<LocalDate, Integer> counts) {
            this.period = period;
            this.days = days;
            this.max = max;
            this.total = total;
            this.blank = blank;
            this.counts = counts;
        }

        public Period getPeriod() {
            return period;
        }

        public List<Day> getDays() {
            return days;
        }

        public int getMax() {
            return max;
        }

        public int getTotal() {
            return total;
        }

        public int getBlank() {
            return blank;
        }

        public int getWithBuilds() {
            return days.size() - blank;
        }

        /** Shade 0-4 for a count, 0 meaning no builds. */
        public int level(int count) {
            if (count <= 0) {
                return 0;
            }
            if (max <= 1) {
                return 4;
            }
            return Math.min(4, Math.max(1, (int) Math.ceil(count * 4.0 / max)));
        }

        /**
         * The grid as seven weekday rows, Monday first, each holding one cell per week. Leading and
         * trailing slots outside the period are null, so the first and last weeks line up with the
         * real weekdays instead of shifting the whole grid.
         */
        public List<Row> rows() {
            LocalDate first = period.start;
            // Monday of the week the period starts in.
            LocalDate gridStart = first.minusDays(first.getDayOfWeek().getValue() - 1L);
            int weeks = (int) (ChronoUnit.DAYS.between(gridStart, period.end) / 7) + 1;
            List<Row> rows = new ArrayList<>(7);
            String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            for (int weekday = 0; weekday < 7; weekday++) {
                List<Day> cells = new ArrayList<>(weeks);
                for (int week = 0; week < weeks; week++) {
                    LocalDate date = gridStart.plusDays(week * 7L + weekday);
                    cells.add(date.isBefore(period.start) || date.isAfter(period.end)
                            ? null
                            : new Day(date, counts.getOrDefault(date, 0)));
                }
                // Only alternate rows are labelled, matching how Jenkins and similar grids read.
                rows.add(new Row(weekday % 2 == 0 ? names[weekday] : "", cells));
            }
            return List.copyOf(rows);
        }

        /** Month headings with how many week columns each spans. */
        public List<MonthLabel> months() {
            LocalDate first = period.start;
            LocalDate gridStart = first.minusDays(first.getDayOfWeek().getValue() - 1L);
            int weeks = (int) (ChronoUnit.DAYS.between(gridStart, period.end) / 7) + 1;
            List<MonthLabel> labels = new ArrayList<>();
            int current = -1;
            for (int week = 0; week < weeks; week++) {
                // A column belongs to the month of its Thursday, so a month is labelled on the
                // first column where it holds most of the days.
                LocalDate marker = gridStart.plusDays(week * 7L + 3);
                int month = marker.getMonthValue();
                if (month != current) {
                    current = month;
                    labels.add(new MonthLabel(marker.getMonth().getDisplayName(
                            java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()), 1));
                } else if (!labels.isEmpty()) {
                    MonthLabel last = labels.get(labels.size() - 1);
                    labels.set(labels.size() - 1, new MonthLabel(last.text, last.weeks + 1));
                }
            }
            return List.copyOf(labels);
        }

        /** Mean builds per weekday across the period, ignoring Saturdays and Sundays. */
        public String weekdayAverage() {
            int counted = 0;
            int sum = 0;
            for (Day day : days) {
                int dow = day.date.getDayOfWeek().getValue();
                if (dow >= 6) {
                    continue;
                }
                counted++;
                sum += day.count;
            }
            if (counted == 0) {
                return "0";
            }
            return String.format(java.util.Locale.ROOT, "%.2f", sum / (double) counted);
        }
    }

    /** One weekday row of the grid. Null cells fall outside the period. */
    public static final class Row {
        public final String label;
        public final List<Day> cells;

        Row(String label, List<Day> cells) {
            this.label = label;
            this.cells = cells;
        }

        public String getLabel() {
            return label;
        }

        public List<Day> getCells() {
            return cells;
        }
    }

    /** A month heading and the number of week columns it covers. */
    public static final class MonthLabel {
        public final String text;
        public final int weeks;

        MonthLabel(String text, int weeks) {
            this.text = text;
            this.weeks = weeks;
        }

        public String getText() {
            return text;
        }

        public int getWeeks() {
            return weeks;
        }
    }
}
