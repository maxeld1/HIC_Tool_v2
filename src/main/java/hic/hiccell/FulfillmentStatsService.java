package hic.hiccell;

import hic.util.HICData;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FulfillmentStatsService {

    private final HicCellMonthViewScraper scraper;
    private final Clock clock;

    public FulfillmentStatsService(HicCellMonthViewScraper scraper) {
        this(scraper, Clock.systemDefaultZone());
    }

    FulfillmentStatsService(HicCellMonthViewScraper scraper, Clock clock) {
        this.scraper = scraper;
        this.clock = clock;
    }

    public FulfillmentStats calculateForTodayOrders(List<HICData> todayOrders) {
        LocalDate endDate = LocalDate.now(clock);
        LocalDate startDate = endDate.minusDays(21);
        LocalDate orderWeekAnchor = latestOrderDate(todayOrders, endDate);
        LocalDate orderWeekStart = orderWeekAnchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate orderWeekEnd = orderWeekAnchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        Map<String, MutableStatLine> stats = new LinkedHashMap<>();
        Set<String> requestedKeys = new HashSet<>();
        for (HICData order : todayOrders) {
            String normalizedCellType = normalizeCellType(order.getCellType());
            String key = key(order.getName(), normalizedCellType);
            requestedKeys.add(key);
            stats.putIfAbsent(key, new MutableStatLine(order.getName(), normalizedCellType));
        }

        HicCellMonthViewRows monthViewRows = scraper.fetchLiveCellsAndCancelled();
        List<HicCellOrderRecord> fulfilled = monthViewRows.liveCells();
        List<HicCellOrderRecord> cancelled = monthViewRows.cancelled();

        countRows(stats, requestedKeys, fulfilled, startDate, endDate, orderWeekStart, orderWeekEnd, false);
        countRows(stats, requestedKeys, cancelled, startDate, endDate, orderWeekStart, orderWeekEnd, true);

        Map<String, FulfillmentStats.StatLine> output = new LinkedHashMap<>();
        for (MutableStatLine line : stats.values()) {
            output.put(key(line.orderedBy, line.cellType),
                    new FulfillmentStats.StatLine(line.orderedBy, line.cellType, line.fulfilled, line.cancelled, line.filledThisWeek));
        }

        return new FulfillmentStats(startDate, endDate, output, fulfilled.size(), cancelled.size());
    }

    private void countRows(Map<String, MutableStatLine> stats, Set<String> requestedKeys,
                           List<HicCellOrderRecord> rows, LocalDate startDate, LocalDate endDate,
                           LocalDate orderWeekStart, LocalDate orderWeekEnd,
                           boolean cancelled) {
        for (HicCellOrderRecord row : rows) {
            if (cancelled && CancellationReasonFilters.isExcludedFromFulfillmentCounts(row.cancellationReason())) {
                continue;
            }

            LocalDate date = row.collectionDate();
            if (date == null || date.isBefore(startDate) || date.isAfter(endDate)) {
                continue;
            }

            String normalizedCellType = normalizeCellType(row.cellType());
            String key = key(row.orderedBy(), normalizedCellType);
            if (!requestedKeys.contains(key)) {
                continue;
            }

            MutableStatLine line = stats.computeIfAbsent(key, ignored -> new MutableStatLine(row.orderedBy(), normalizedCellType));
            if (cancelled) {
                line.cancelled++;
            } else {
                line.fulfilled++;
                if (!date.isBefore(orderWeekStart) && !date.isAfter(orderWeekEnd)) {
                    line.filledThisWeek = true;
                }
            }
        }
    }

    private LocalDate latestOrderDate(List<HICData> todayOrders, LocalDate fallback) {
        LocalDate latest = null;
        for (HICData order : todayOrders) {
            if (order.getRequestDate() == null) {
                continue;
            }
            LocalDate date = order.getRequestDate().toLocalDate();
            if (latest == null || date.isAfter(latest)) {
                latest = date;
            }
        }
        return latest == null ? fallback : latest;
    }

    static String normalizeCellType(String cellType) {
        if (cellType == null) {
            return "";
        }
        String normalized = cellType.trim().replaceAll("\\s+", " ");
        if (normalized.equalsIgnoreCase("CD4+ T")) {
            return "CD4+";
        }
        if (normalized.equalsIgnoreCase("CD8+ T")) {
            return "CD8+";
        }
        if (normalized.equalsIgnoreCase("Total T Cells")) {
            return "Total T";
        }
        return normalized;
    }

    private String key(String orderedBy, String cellType) {
        return normalizePerson(orderedBy) + "|" + normalizeCellType(cellType).toLowerCase();
    }

    private String normalizePerson(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.trim().replaceAll("\\s+", " ").toLowerCase();
        if (normalized.isBlank()) {
            return "";
        }
        String[] parts = normalized.split(" ");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1];
    }

    private static class MutableStatLine {
        private final String orderedBy;
        private final String cellType;
        private int fulfilled;
        private int cancelled;
        private boolean filledThisWeek;

        private MutableStatLine(String orderedBy, String cellType) {
            this.orderedBy = orderedBy;
            this.cellType = cellType;
        }
    }
}
