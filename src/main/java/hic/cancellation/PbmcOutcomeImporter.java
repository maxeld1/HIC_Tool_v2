package hic.cancellation;

import hic.hiccell.HicCellMonthViewRows;
import hic.hiccell.HicCellOrderRecord;
import hic.util.HICData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PbmcOutcomeImporter {
    public record Outcome(HicCellOrderRecord source, CancellationPlanner.Status status, double delivered) {
    }

    public record Result(List<Outcome> outcomes, List<String> warnings) {
    }

    public Result match(List<HICData> requests, HicCellMonthViewRows rows, LocalDate date) {
        Set<String> requestKeys = new LinkedHashSet<>();
        requests.forEach(request -> requestKeys.add(key(request.getOrderNumber(), request.getName())));
        Map<String, Set<Outcome>> candidates = new LinkedHashMap<>();
        collect(rows.liveCells(), false, date, requestKeys, candidates);
        collect(rows.cancelled(), true, date, requestKeys, candidates);
        List<Outcome> outcomes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> people = new LinkedHashSet<>();
        requests.forEach(request -> people.add(key(0, request.getName())));
        Set<String> unmatched = new LinkedHashSet<>();
        for (HicCellOrderRecord row : java.util.stream.Stream.concat(rows.liveCells().stream(), rows.cancelled().stream()).toList()) {
            if (date.equals(row.collectionDate()) && isPbmc(row.cellType()) && people.contains(key(0, row.orderedBy()))
                    && !requestKeys.contains(key(row.requestId(), row.orderedBy()))) {
                unmatched.add("PBMC #" + row.requestId() + " for " + row.orderedBy()
                        + " has a different request ID from the input. Include the original PBMC row and verify its donor/linked group; it was not matched by name alone.");
            }
        }
        warnings.addAll(unmatched);
        candidates.forEach((identity, matches) -> {
            if (matches.size() != 1) {
                warnings.add("Ambiguous PBMC records for " + identity + ": multiple outcomes or quantities. Review Month View and enter the outcome manually.");
                return;
            }
            Outcome outcome = matches.iterator().next();
            if (outcome.status() == CancellationPlanner.Status.FULFILLED
                    && (!Double.isFinite(outcome.delivered()) || outcome.delivered() <= 0)) {
                warnings.add("PBMC #" + outcome.source().requestId() + " for " + outcome.source().orderedBy()
                        + " appears in Live Cells without a positive delivered amount. Its outcome was not inferred.");
                return;
            }
            if (outcome.status() == CancellationPlanner.Status.CANCELLED
                    && (!Double.isFinite(outcome.source().delivered()) || outcome.source().delivered() != 0)) {
                warnings.add("Cancelled PBMC #" + outcome.source().requestId()
                        + " has a delivered amount. Review this conflicting outcome manually.");
                return;
            }
            outcomes.add(outcome);
        });
        return new Result(List.copyOf(outcomes), List.copyOf(warnings));
    }

    private static void collect(List<HicCellOrderRecord> rows, boolean cancelled, LocalDate date,
                                Set<String> requestKeys, Map<String, Set<Outcome>> candidates) {
        for (HicCellOrderRecord row : rows) {
            String identity = key(row.requestId(), row.orderedBy());
            if (!date.equals(row.collectionDate()) || !isPbmc(row.cellType()) || !requestKeys.contains(identity)) {
                continue;
            }
            candidates.computeIfAbsent(identity, ignored -> new LinkedHashSet<>()).add(new Outcome(row,
                    cancelled ? CancellationPlanner.Status.CANCELLED : CancellationPlanner.Status.FULFILLED,
                    cancelled ? 0 : row.delivered()));
        }
    }

    public static boolean isPbmc(String cellType) {
        return cellType != null && cellType.trim().equalsIgnoreCase("PBMC");
    }

    public static String key(int requestId, String name) {
        return requestId + "|" + (name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }
}
