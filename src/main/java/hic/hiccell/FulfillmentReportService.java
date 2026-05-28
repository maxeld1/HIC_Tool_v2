package hic.hiccell;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FulfillmentReportService {

    private final HicCellMonthViewScraper scraper;

    public FulfillmentReportService(HicCellMonthViewScraper scraper) {
        this.scraper = scraper;
    }

    public FulfillmentReport calculate(LocalDate startDate, LocalDate endDate,
                                       FulfillmentReport.GroupBy groupBy,
                                       boolean separateByCellType) {
        HicCellMonthViewRows rows = scraper.fetchLiveCellsAndCancelled();
        return calculate(rows, startDate, endDate, groupBy, separateByCellType);
    }

    public CompleteFulfillmentReport calculateComplete(LocalDate startDate, LocalDate endDate) {
        HicCellMonthViewRows rows = scraper.fetchLiveCellsAndCancelled();
        List<FulfillmentReport> reports = List.of(
                calculate(rows, startDate, endDate, FulfillmentReport.GroupBy.LAB_OWNER, false),
                calculate(rows, startDate, endDate, FulfillmentReport.GroupBy.LAB_OWNER, true),
                calculate(rows, startDate, endDate, FulfillmentReport.GroupBy.ORDERED_BY, false),
                calculate(rows, startDate, endDate, FulfillmentReport.GroupBy.ORDERED_BY, true)
        );
        return new CompleteFulfillmentReport(startDate, endDate, reports);
    }

    private FulfillmentReport calculate(HicCellMonthViewRows rows, LocalDate startDate, LocalDate endDate,
                                        FulfillmentReport.GroupBy groupBy,
                                        boolean separateByCellType) {
        Map<String, MutableReportLine> stats = new LinkedHashMap<>();

        countRows(stats, rows.liveCells(), startDate, endDate, groupBy, separateByCellType, false);
        countRows(stats, rows.cancelled(), startDate, endDate, groupBy, separateByCellType, true);

        List<FulfillmentReport.ReportLine> reportLines = new ArrayList<>();
        for (MutableReportLine line : stats.values()) {
            reportLines.add(new FulfillmentReport.ReportLine(
                    line.groupName,
                    line.cellType,
                    String.join(", ", line.labOwners),
                    line.fulfilled,
                    line.cancelled
            ));
        }

        return new FulfillmentReport(startDate, endDate, groupBy, separateByCellType,
                reportLines, rows.liveCells().size(), rows.cancelled().size());
    }

    private void countRows(Map<String, MutableReportLine> stats, List<HicCellOrderRecord> rows,
                           LocalDate startDate, LocalDate endDate,
                           FulfillmentReport.GroupBy groupBy, boolean separateByCellType,
                           boolean cancelled) {
        for (HicCellOrderRecord row : rows) {
            if (cancelled && CancellationReasonFilters.isExcludedFromFulfillmentCounts(row.cancellationReason())) {
                continue;
            }

            LocalDate date = row.collectionDate();
            if (date == null || date.isBefore(startDate) || date.isAfter(endDate)) {
                continue;
            }

            String groupName = groupName(row, groupBy);
            String cellType = separateByCellType ? FulfillmentStatsService.normalizeCellType(row.cellType()) : "All Cell Types";
            String key = groupName.toLowerCase() + "|" + cellType.toLowerCase();
            MutableReportLine line = stats.computeIfAbsent(key, ignored -> new MutableReportLine(groupName, cellType));
            line.labOwners.add(cleanValue(row.labOwner()));
            if (cancelled) {
                line.cancelled++;
            } else {
                line.fulfilled++;
            }
        }
    }

    private String groupName(HicCellOrderRecord row, FulfillmentReport.GroupBy groupBy) {
        String value = groupBy == FulfillmentReport.GroupBy.LAB_OWNER ? row.labOwner() : row.orderedBy();
        if (value == null || value.trim().isEmpty()) {
            return "Unknown";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String cleanValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Unknown";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static class MutableReportLine {
        private final String groupName;
        private final String cellType;
        private final Set<String> labOwners = new LinkedHashSet<>();
        private int fulfilled;
        private int cancelled;

        private MutableReportLine(String groupName, String cellType) {
            this.groupName = groupName;
            this.cellType = cellType;
        }
    }
}
