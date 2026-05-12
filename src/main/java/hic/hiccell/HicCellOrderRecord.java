package hic.hiccell;

import java.time.LocalDate;

public record HicCellOrderRecord(
        int requestId,
        LocalDate collectionDate,
        String orderedBy,
        String labOwner,
        String cellType,
        double requested,
        double minimum,
        double delivered,
        boolean cancelled,
        String cancellationReason
) {
}
