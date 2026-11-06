package io.jenkins.plugins.buildvalue.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The default heatmap period and the grid it produces, pinned to fixed dates so a leap day, a
 * daylight-saving change and a year boundary are checked rather than assumed.
 */
class HeatmapTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static long at(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    private static List<BuildSample> buildsOn(ZoneId zone, LocalDate... days) {
        List<BuildSample> samples = new ArrayList<>();
        int number = 1;
        for (LocalDate day : days) {
            samples.add(new BuildSample(
                    "folder/job",
                    "/job/folder/job/",
                    "/job/folder/job/" + number + "/",
                    "#" + number,
                    number,
                    // Midday, so a one-hour daylight-saving shift cannot move the local date.
                    day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                    60_000L,
                    0L,
                    "SUCCESS",
                    "manual",
                    "alice",
                    List.of()));
            number++;
        }
        return samples;
    }

    @Test
    void defaultPeriodIsTwelveMonthsBackPlusOneDayThroughToday() {
        // The example from the specification: on 2026-11-06 the window is 2025-11-07..2026-11-06.
        Heatmap heatmap = Heatmap.of(List.of(), at(LocalDate.of(2026, 11, 6), UTC), UTC);
        Heatmap.Period period = heatmap.getDefaultPeriod();
        assertEquals("2025-11-07", period.getStart());
        assertEquals("2026-11-06", period.getEnd());
        assertTrue(period.isRolling());
        // Both ends included: 365 days in that span.
        assertEquals(365, period.getDays());
    }

    @Test
    void defaultPeriodIncludesTodayAndSpansTheYearBoundary() {
        Heatmap heatmap = Heatmap.of(List.of(), at(LocalDate.of(2026, 1, 3), UTC), UTC);
        Heatmap.Period period = heatmap.getDefaultPeriod();
        assertEquals("2025-01-04", period.getStart());
        assertEquals("2026-01-03", period.getEnd());
        // The grid runs straight through 31 December into 1 January.
        Heatmap.Grid grid = heatmap.grid(period);
        List<String> dates = new ArrayList<>();
        for (Heatmap.Day day : grid.getDays()) {
            dates.add(day.getDate());
        }
        int newYearsEve = dates.indexOf("2025-12-31");
        assertTrue(newYearsEve > 0);
        assertEquals("2026-01-01", dates.get(newYearsEve + 1));
    }

    @Test
    void leapDayIsOneDayInTheWindow() {
        // A window ending 2024-03-01 contains 2024-02-29 exactly once.
        Heatmap heatmap = Heatmap.of(List.of(), at(LocalDate.of(2024, 3, 1), UTC), UTC);
        Heatmap.Grid grid = heatmap.grid(heatmap.getDefaultPeriod());
        long leapDays = grid.getDays().stream().filter(day -> "2024-02-29".equals(day.getDate())).count();
        assertEquals(1, leapDays);
        // 2024 is a leap year, so twelve months back plus one day gives 366 days.
        assertEquals(366, heatmap.getDefaultPeriod().getDays());
    }

    @Test
    void aBuildJustAfterLocalMidnightCountsOnTheNewDay() {
        // 00:30 local on 1 March is still 23:30 UTC on 29 February. The count must follow the zone
        // the statistics use, not UTC.
        long justAfterMidnight = LocalDate.of(2026, 3, 1).atTime(0, 30).atZone(BERLIN).toInstant().toEpochMilli();
        BuildSample sample = new BuildSample(
                "a/b", "/job/a/b/", "/job/a/b/1/", "#1", 1, justAfterMidnight, 1000L, 0L,
                "SUCCESS", "manual", "alice", List.of());
        Heatmap heatmap = Heatmap.of(List.of(sample), at(LocalDate.of(2026, 3, 2), BERLIN), BERLIN);
        assertEquals(1, heatmap.countOn(LocalDate.of(2026, 3, 1)));
        assertEquals(0, heatmap.countOn(LocalDate.of(2026, 2, 28)));
    }

    @Test
    void daylightSavingChangeDoesNotDropOrDuplicateADay() {
        // Central European summer time starts on 2026-03-29; that day is 23 hours long.
        ZonedDateTime springForward = LocalDate.of(2026, 3, 29).atStartOfDay(BERLIN);
        assertEquals(23, java.time.Duration.between(springForward, springForward.plusDays(1)).toHours());
        Heatmap heatmap = Heatmap.of(List.of(), at(LocalDate.of(2026, 4, 1), BERLIN), BERLIN);
        Heatmap.Grid grid = heatmap.grid(heatmap.getDefaultPeriod());
        long shortDay = grid.getDays().stream().filter(day -> "2026-03-29".equals(day.getDate())).count();
        assertEquals(1, shortDay, "the short day appears exactly once");
        // And the autumn 25-hour day likewise.
        long longDay = grid.getDays().stream().filter(day -> "2025-10-26".equals(day.getDate())).count();
        assertEquals(1, longDay, "the long day appears exactly once");
    }

    @Test
    void calendarYearsWithBuildsAreSelectableNewestFirst() {
        Heatmap heatmap = Heatmap.of(
                buildsOn(UTC, LocalDate.of(2024, 5, 1), LocalDate.of(2026, 5, 1), LocalDate.of(2025, 5, 1)),
                at(LocalDate.of(2026, 11, 6), UTC),
                UTC);
        List<String> ids = new ArrayList<>();
        for (Heatmap.Period period : heatmap.getPeriods()) {
            ids.add(period.getId());
        }
        assertEquals(List.of("last", "2026", "2025", "2024"), ids);
        Heatmap.Period year = heatmap.period("2025");
        assertEquals("2025-01-01", year.getStart());
        assertEquals("2025-12-31", year.getEnd());
        assertEquals(365, year.getDays());
    }

    @Test
    void anUnknownPeriodFallsBackToTheDefault() {
        Heatmap heatmap = Heatmap.of(List.of(), at(LocalDate.of(2026, 11, 6), UTC), UTC);
        assertEquals("last", heatmap.period("1999").getId());
        assertEquals("last", heatmap.period(null).getId());
        assertEquals("last", heatmap.period("").getId());
    }

    @Test
    void emptyDataKeepsTheDefaultPeriod() {
        // No builds must not shrink the window to nothing or move it off today.
        Heatmap heatmap = Heatmap.of(List.of(), at(LocalDate.of(2026, 11, 6), UTC), UTC);
        Heatmap.Grid grid = heatmap.grid(heatmap.getDefaultPeriod());
        assertEquals(365, grid.getDays().size());
        assertEquals(0, grid.getTotal());
        assertEquals(365, grid.getBlank());
        assertEquals(0, grid.getWithBuilds());
        assertEquals(0, grid.getMax());
        // Every day renders as the empty shade rather than the darkest one.
        assertEquals(0, grid.level(0));
    }

    @Test
    void emptyDaysCountAsZeroAndShadesScaleToTheBusiestDay() {
        List<BuildSample> samples = new ArrayList<>(buildsOn(UTC, LocalDate.of(2026, 11, 2)));
        // Four builds on one day, one on another, nothing in between.
        samples.addAll(buildsOn(
                UTC,
                LocalDate.of(2026, 11, 4),
                LocalDate.of(2026, 11, 4),
                LocalDate.of(2026, 11, 4),
                LocalDate.of(2026, 11, 4)));
        Heatmap heatmap = Heatmap.of(samples, at(LocalDate.of(2026, 11, 6), UTC), UTC);
        Heatmap.Grid grid = heatmap.grid(heatmap.getDefaultPeriod());
        assertEquals(4, grid.getMax());
        assertEquals(5, grid.getTotal());
        assertEquals(2, grid.getWithBuilds());
        assertEquals(0, heatmap.countOn(LocalDate.of(2026, 11, 3)), "a day with no builds is zero");
        assertEquals(0, grid.level(0));
        assertEquals(1, grid.level(1));
        assertEquals(4, grid.level(4));
    }

    @Test
    void gridRowsAreSevenWeekdaysWithPaddingOutsideThePeriod() {
        Heatmap heatmap = Heatmap.of(List.of(), at(LocalDate.of(2026, 11, 6), UTC), UTC);
        Heatmap.Grid grid = heatmap.grid(heatmap.getDefaultPeriod());
        List<Heatmap.Row> rows = grid.rows();
        assertEquals(7, rows.size());
        int width = rows.get(0).getCells().size();
        for (Heatmap.Row row : rows) {
            assertEquals(width, row.getCells().size(), "every row is the same number of weeks");
        }
        // 2025-11-07 is a Friday, so Monday through Thursday of that first column fall outside the
        // period and are padding rather than cells for other dates.
        assertEquals(LocalDate.of(2025, 11, 7).getDayOfWeek().getValue(), 5);
        for (int weekday = 0; weekday < 4; weekday++) {
            assertEquals(null, rows.get(weekday).getCells().get(0), "leading slot is padding");
        }
        assertEquals("2025-11-07", rows.get(4).getCells().get(0).getDate());
        // The last cell in range is today.
        Heatmap.Day last = null;
        for (Heatmap.Row row : rows) {
            Heatmap.Day cell = row.getCells().get(width - 1);
            if (cell != null) {
                last = cell;
            }
        }
        assertEquals("2026-11-06", last.getDate());
    }

    @Test
    void monthHeadingsCoverEveryWeekColumn() {
        Heatmap heatmap = Heatmap.of(List.of(), at(LocalDate.of(2026, 11, 6), UTC), UTC);
        Heatmap.Grid grid = heatmap.grid(heatmap.getDefaultPeriod());
        int weeks = grid.rows().get(0).getCells().size();
        int covered = 0;
        for (Heatmap.MonthLabel label : grid.months()) {
            covered += label.getWeeks();
        }
        assertEquals(weeks, covered, "headings span exactly the grid width");
        // A twelve-month window shows thirteen month headings: the first and last are partial.
        assertEquals(13, grid.months().size());
    }

    @Test
    void weekdayAverageIgnoresWeekends() {
        // Two builds a weekday over one week, plus one on the Saturday, which must not count.
        // The window is the whole default year, so the mean is over its weekdays.
        List<BuildSample> samples = new ArrayList<>(buildsOn(
                UTC,
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 10, 31)));
        Heatmap heatmap = Heatmap.of(samples, at(LocalDate.of(2026, 11, 6), UTC), UTC);
        Heatmap.Grid grid = heatmap.grid(heatmap.getDefaultPeriod());

        // 2026-10-31 is a Saturday: in the data, but excluded from the average.
        assertEquals(6, LocalDate.of(2026, 10, 31).getDayOfWeek().getValue());
        assertEquals(1, heatmap.countOn(LocalDate.of(2026, 10, 31)));
        assertEquals(3, grid.getTotal(), "all three builds are in the window");

        int weekdays = 0;
        for (Heatmap.Day day : grid.getDays()) {
            if (day.date.getDayOfWeek().getValue() < 6) {
                weekdays++;
            }
        }
        // Only the two weekday builds reach the numerator.
        double expected = 2.0 / weekdays;
        assertEquals(
                String.format(java.util.Locale.ROOT, "%.2f", expected), grid.weekdayAverage());
    }
}
