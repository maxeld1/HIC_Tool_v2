package hic.hiccell;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FulfillmentReport {

    public enum GroupBy {
        ORDERED_BY("Ordered By"),
        LAB_OWNER("Lab Owner");

        private final String label;

        GroupBy(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final GroupBy groupBy;
    private final boolean separateByCellType;
    private final List<ReportLine> lines;
    private final int scrapedFulfilledRows;
    private final int scrapedCancelledRows;

    public FulfillmentReport(LocalDate startDate, LocalDate endDate, GroupBy groupBy, boolean separateByCellType,
                             List<ReportLine> lines, int scrapedFulfilledRows, int scrapedCancelledRows) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.groupBy = groupBy;
        this.separateByCellType = separateByCellType;
        this.lines = new ArrayList<>(lines);
        this.scrapedFulfilledRows = scrapedFulfilledRows;
        this.scrapedCancelledRows = scrapedCancelledRows;
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public GroupBy groupBy() {
        return groupBy;
    }

    public boolean separateByCellType() {
        return separateByCellType;
    }

    public List<ReportLine> sortedLines() {
        List<ReportLine> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator
                .comparing(ReportLine::groupName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ReportLine::cellType, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    public int scrapedFulfilledRows() {
        return scrapedFulfilledRows;
    }

    public int scrapedCancelledRows() {
        return scrapedCancelledRows;
    }

    public record ReportLine(String groupName, String cellType, String labOwners, int fulfilled, int cancelled) {
        public int total() {
            return fulfilled + cancelled;
        }

        public double fulfillmentRate() {
            return total() == 0 ? 0.0 : (double) fulfilled / total();
        }
    }
}
