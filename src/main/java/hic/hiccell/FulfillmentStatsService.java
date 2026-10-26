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
        return calculateForOrders(todayOrders, endDate, endDate);
    }

    public FulfillmentStats calculateForOrdersOnDate(List<HICData> orders, LocalDate collectionDate) {
        return calculateForOrders(orders, collectionDate, collectionDate);
    }

    public FulfillmentStats calculateForOrdersOnDate(List<HICData> orders, LocalDate collectionDate,
                                                    HicCellMonthViewRows rows) {
        return calculateForOrders(orders, collectionDate, collectionDate, rows);
    }

    private FulfillmentStats calculateForOrders(List<HICData> todayOrders, LocalDate endDate, LocalDate orderWeekAnchor) {
        return calculateForOrders(todayOrders, endDate, orderWeekAnchor, scraper.fetchLiveCellsAndCancelled());
    }

    private FulfillmentStats calculateForOrders(List<HICData> todayOrders, LocalDate endDate,
                                               LocalDate orderWeekAnchor, HicCellMonthViewRows monthViewRows) {
        LocalDate startDate = endDate.minusDays(21);
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

        List<HicCellOrderRecord> fulfilled = monthViewRows.liveCells();
        List<HicCellOrderRecord> cancelled = monthViewRows.cancelled();

        countRows(stats, requestedKeys, fulfilled, startDate, endDate, orderWeekStart, orderWeekEnd, false);
        countRows(stats, requestedKeys, cancelled, startDate, endDate, orderWeekStart, orderWeekEnd, true);

        Map<String, FulfillmentStats.StatLine> output = new LinkedHashMap<>();
        for (MutableStatLine line : stats.values()) {
            output.put(key(line.orderedBy, line.cellType),
                    new FulfillmentStats.StatLine(line.orderedBy, line.cellType, line.fulfilled, line.cancelled, line.filledThisWeek));
        }

        Set<String> weeklyRequesters = new HashSet<>();
        for (HicCellOrderRecord row : fulfilled) {
            if (row.collectionDate() != null && row.delivered() > 0
                    && !row.collectionDate().isBefore(orderWeekStart)
                    && !row.collectionDate().isAfter(orderWeekEnd)
                    && !row.collectionDate().isAfter(endDate)) {
                weeklyRequesters.add(hic.priority.PrioritySheetSignals.normalizePerson(row.orderedBy()));
            }
        }
        return new FulfillmentStats(startDate, endDate, output, fulfilled.size(), cancelled.size(), weeklyRequesters);
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

    public static String normalizeCellType(String cellType) {
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
        if (normalized.equalsIgnoreCase("Unpurified")
                || normalized.equalsIgnoreCase("Unpurified Apheresis Product")
                || normalized.equalsIgnoreCase("Unpurified Apheresis Products")) {
            return "Unpurified Apheresis";
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
