package hic.hiccell;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CompleteFulfillmentReport {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final List<FulfillmentReport> reports;

    public CompleteFulfillmentReport(LocalDate startDate, LocalDate endDate, List<FulfillmentReport> reports) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.reports = new ArrayList<>(reports);
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public List<FulfillmentReport> reports() {
        return new ArrayList<>(reports);
    }

    public int totalLineCount() {
        int total = 0;
        for (FulfillmentReport report : reports) {
            total += report.sortedLines().size();
        }
        return total;
    }
}
