package hic.priority;

import hic.hiccell.FulfillmentStats;
import hic.util.HICData;

import java.util.Locale;

public final class OrderPriority {
    public record Comparison(int result, String factor, String detail) {
    }
    private OrderPriority() {
    }

    public static int compare(HICData left, HICData right, FulfillmentStats stats) {
        return compare(left, right, stats, PrioritySheetSignals.EMPTY);
    }

    public static int compare(HICData left, HICData right, FulfillmentStats stats, PrioritySheetSignals signals) {
        Comparison history = compareHistory(left, right, stats);
        return history.result() != 0 ? history.result() : compareTieBreakers(left, right, signals).result();
    }

    public static Comparison compareHistory(HICData left, HICData right, FulfillmentStats stats) {
        double leftRate = rate(left, stats);
        double rightRate = rate(right, stats);
        int comparison = Double.compare(leftRate, rightRate);
        if (comparison != 0) {
            return new Comparison(comparison, "Historical fulfillment rate", label(left) + ": " + displayHistory(left, stats, leftRate)
                    + "; " + label(right) + ": " + displayHistory(right, stats, rightRate) + ". Lower known fulfillment is favored; unknown history ranks last.");
        }
        if (leftRate <= 1) {
            FulfillmentStats.StatLine leftLine = stats.findLine(left.getName(), left.getCellType());
            FulfillmentStats.StatLine rightLine = stats.findLine(right.getName(), right.getCellType());
            comparison = leftRate == 1
                    ? Integer.compare(leftLine.fulfilled() + leftLine.cancelled(), rightLine.fulfilled() + rightLine.cancelled())
                    : Integer.compare(rightLine.cancelled(), leftLine.cancelled());
            if (comparison != 0) {
                return new Comparison(comparison, leftRate == 1 ? "Historical request count" : "Historical cancellation count",
                        label(left) + ": " + (leftRate == 1 ? leftLine.fulfilled() + leftLine.cancelled() : leftLine.cancelled())
                                + "; " + label(right) + ": " + (leftRate == 1 ? rightLine.fulfilled() + rightLine.cancelled() : rightLine.cancelled())
                                + (leftRate == 1 ? ". At 100% fulfillment, fewer total requests are favored." : ". At equal fulfillment rates, more prior cancellations are favored."));
            }
        }
        return new Comparison(0, "Equal fulfillment history", "");
    }

    public static Comparison compareTieBreakers(HICData left, HICData right, PrioritySheetSignals signals) {
        int comparison = Integer.compare(signals.missedPickupCount(left.getName()), signals.missedPickupCount(right.getName()));
        if (comparison != 0) {
            return new Comparison(comparison, "Missed pickups", label(left) + ": " + signals.missedPickupCount(left.getName())
                    + "; " + label(right) + ": " + signals.missedPickupCount(right.getName()) + ". Fewer missed pickups are favored.");
        }
        comparison = left.getRequestDate().compareTo(right.getRequestDate());
        if (comparison != 0) {
            return new Comparison(comparison, "Request date/time", label(left) + ": " + left.getRequestDate()
                    + "; " + label(right) + ": " + right.getRequestDate() + ". Earlier requests are favored.");
        }
        comparison = Integer.compare(left.getOrderNumber(), right.getOrderNumber());
        return new Comparison(comparison, comparison == 0 ? "Stable input order" : "Request number",
                comparison == 0 ? "All configured priority factors tied; stable group/input order broke the tie."
                        : label(left) + " versus " + label(right) + ". Lower request numbers are favored.");
    }

    private static String label(HICData order) {
        return order.getName() + " #" + order.getOrderNumber() + " " + order.getCellType();
    }

    private static String displayHistory(HICData order, FulfillmentStats stats, double rate) {
        return rate > 1 ? "unknown" : stats.fulfillmentFraction(order.getName(), order.getCellType())
                + String.format(Locale.US, " (%.1f%%)", rate * 100);
    }

    private static double rate(HICData order, FulfillmentStats stats) {
        double rate = stats == null ? -1 : stats.fulfillmentRate(order.getName(), order.getCellType());
        return rate < 0 ? 2 : rate;
    }
}
