package hic.hiccell;

public final class CancellationReasonFilters {

    private CancellationReasonFilters() {
    }

    public static boolean isExcludedFromFulfillmentCounts(String cancellationReason) {
        if (cancellationReason == null) {
            return false;
        }
        String normalized = cancellationReason
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return false;
        }

        boolean duplicateOrder = normalized.contains("duplicate order")
                || normalized.contains("duplicated order")
                || normalized.contains("duplicate request")
                || normalized.contains("duplicated request");
        boolean retractedRequest = normalized.contains("retracted")
                || normalized.contains("retract request")
                || normalized.contains("request retraction")
                || normalized.contains("withdrawn")
                || normalized.contains("withdraw request")
                || normalized.contains("cancelled by requester")
                || normalized.contains("canceled by requester");

        return duplicateOrder || retractedRequest;
    }
}
