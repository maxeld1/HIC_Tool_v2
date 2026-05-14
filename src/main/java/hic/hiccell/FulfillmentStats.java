package hic.hiccell;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FulfillmentStats {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Map<String, StatLine> lines;
    private final int scrapedFulfilledRows;
    private final int scrapedCancelledRows;

    public FulfillmentStats(LocalDate startDate, LocalDate endDate, Map<String, StatLine> lines,
                            int scrapedFulfilledRows, int scrapedCancelledRows) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.lines = new LinkedHashMap<>(lines);
        this.scrapedFulfilledRows = scrapedFulfilledRows;
        this.scrapedCancelledRows = scrapedCancelledRows;
    }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("3-Week Fulfillment by Requester and Cell Type\n");
        sb.append("Date Range: ").append(startDate).append(" to ").append(endDate).append("\n");
        sb.append("Source Rows: ").append(scrapedFulfilledRows).append(" Live Cells, ")
                .append(scrapedCancelledRows).append(" Cancelled\n\n");
        sb.append(String.format("%-28s %-18s %-12s %-12s %-12s %-8s%n",
                "Ordered By", "Cell Type", "Fulfilled", "Cancelled", "Fraction", "Week"));
        sb.append("--------------------------------------------------------------------------------\n");

        List<StatLine> sorted = new ArrayList<>(lines.values());
        sorted.sort(Comparator
                .comparing(StatLine::orderedBy, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StatLine::cellType, String.CASE_INSENSITIVE_ORDER));

        if (sorted.isEmpty()) {
            sb.append("No matching fulfilled or cancelled HIC Cell rows found for today's requesters/cell types.\n");
        }

        for (StatLine line : sorted) {
            int total = line.fulfilled() + line.cancelled();
            String fraction = total == 0 ? "0/0" : line.fulfilled() + "/" + total;
            sb.append(String.format("%-28s %-18s %-12d %-12d %-12s %-8s%n",
                    line.orderedBy(), line.cellType(), line.fulfilled(), line.cancelled(), fraction, line.filledThisWeek() ? "Y" : "N"));
        }

        sb.append("\nNote: Total equals Live Cells plus Cancelled rows scraped from the Month View data available to this session.");
        return sb.toString();
    }

    public String toDisplayTextByCellType() {
        StringBuilder sb = new StringBuilder();
        sb.append("3-Week Fulfillment by Cell Type\n");
        sb.append("Date Range: ").append(startDate).append(" to ").append(endDate).append("\n");
        sb.append("Source Rows: ").append(scrapedFulfilledRows).append(" Live Cells, ")
                .append(scrapedCancelledRows).append(" Cancelled\n\n");

        List<StatLine> sorted = new ArrayList<>(lines.values());
        sorted.sort(Comparator
                .comparing(StatLine::cellType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StatLine::orderedBy, String.CASE_INSENSITIVE_ORDER));

        if (sorted.isEmpty()) {
            sb.append("No matching fulfilled or cancelled HIC Cell rows found for today's requesters/cell types.\n");
            return sb.toString();
        }

        String currentCellType = "";
        for (StatLine line : sorted) {
            if (!line.cellType().equals(currentCellType)) {
                if (!currentCellType.isEmpty()) {
                    sb.append("\n");
                }
                currentCellType = line.cellType();
                sb.append(currentCellType).append("\n");
                sb.append("-".repeat(Math.max(8, currentCellType.length()))).append("\n");
                sb.append(String.format("%-28s %-12s %-12s %-12s %-8s%n", "Ordered By", "Fulfilled", "Cancelled", "Fraction", "Week"));
            }

            int total = line.fulfilled() + line.cancelled();
            String fraction = total == 0 ? "0/0" : line.fulfilled() + "/" + total;
            sb.append(String.format("%-28s %-12d %-12d %-12s %-8s%n",
                    line.orderedBy(), line.fulfilled(), line.cancelled(), fraction, line.filledThisWeek() ? "Y" : "N"));
        }

        return sb.toString();
    }

    public StatLine findLine(String orderedBy, String cellType) {
        return lines.get(key(orderedBy, cellType));
    }

    public double fulfillmentRate(String orderedBy, String cellType) {
        StatLine line = findLine(orderedBy, cellType);
        if (line == null) {
            return -1.0;
        }
        int total = line.fulfilled() + line.cancelled();
        if (total == 0) {
            return -1.0;
        }
        return (double) line.fulfilled() / total;
    }

    public String fulfillmentFraction(String orderedBy, String cellType) {
        StatLine line = findLine(orderedBy, cellType);
        if (line == null) {
            return "Not checked";
        }
        int total = line.fulfilled() + line.cancelled();
        return line.fulfilled() + "/" + total;
    }

    public boolean filledThisWeek(String orderedBy, String cellType) {
        StatLine line = findLine(orderedBy, cellType);
        return line != null && line.filledThisWeek();
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

    private String normalizeCellType(String cellType) {
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

    public record StatLine(String orderedBy, String cellType, int fulfilled, int cancelled, boolean filledThisWeek) {
    }
}
