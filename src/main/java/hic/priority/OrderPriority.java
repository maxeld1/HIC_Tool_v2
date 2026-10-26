package hic.priority;

import hic.hiccell.FulfillmentStats;
import hic.util.HICData;

import java.util.Comparator;

public final class OrderPriority {
    private OrderPriority() {
    }

    public static int compare(HICData left, HICData right, FulfillmentStats stats) {
        return compare(left, right, stats, PrioritySheetSignals.EMPTY);
    }

    public static int compare(HICData left, HICData right, FulfillmentStats stats, PrioritySheetSignals signals) {
        double leftRate = rate(left, stats);
        double rightRate = rate(right, stats);
        int comparison = Double.compare(leftRate, rightRate);
        if (comparison != 0) {
            return comparison;
        }
        if (leftRate <= 1) {
            FulfillmentStats.StatLine leftLine = stats.findLine(left.getName(), left.getCellType());
            FulfillmentStats.StatLine rightLine = stats.findLine(right.getName(), right.getCellType());
            comparison = leftRate == 1
                    ? Integer.compare(leftLine.fulfilled() + leftLine.cancelled(), rightLine.fulfilled() + rightLine.cancelled())
                    : Integer.compare(rightLine.cancelled(), leftLine.cancelled());
            if (comparison != 0) {
                return comparison;
            }
        }
        comparison = Integer.compare(signals.missedPickupCount(left.getName()), signals.missedPickupCount(right.getName()));
        if (comparison != 0) {
            return comparison;
        }
        return Comparator.comparing(HICData::getRequestDate)
                .thenComparingInt(HICData::getOrderNumber).compare(left, right);
    }

    private static double rate(HICData order, FulfillmentStats stats) {
        double rate = stats == null ? -1 : stats.fulfillmentRate(order.getName(), order.getCellType());
        return rate < 0 ? 2 : rate;
    }
}
