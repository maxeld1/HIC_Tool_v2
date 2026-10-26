package hic.cancellation;

import hic.hiccell.FulfillmentStats;
import hic.priority.OrderPriority;
import hic.priority.PrioritySheetSignals;
import hic.util.HICData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CancellationPlanner {
    private static final double EPSILON = 0.000001;

    public enum Status { PENDING, FULFILLED, CANCELLED }

    public record Order(HICData request, String group, boolean protectedOrder, boolean donorSpecific,
                        boolean filledThisWeek, Status status, double delivered) {
    }

    public record Decision(Order order, String action, double allocation, String reason) {
    }

    public record Plan(List<Decision> decisions, Map<String, Double> remaining, List<String> issues) {
    }

    public Plan plan(List<Order> orders, Map<String, Double> totalYields, FulfillmentStats stats,
                     boolean minimumFirst) {
        return plan(orders, totalYields, stats, minimumFirst, PrioritySheetSignals.EMPTY);
    }

    public Plan plan(List<Order> orders, Map<String, Double> totalYields, FulfillmentStats stats,
                     boolean minimumFirst, PrioritySheetSignals signals) {
        Set<String> served = orders.stream()
                .filter(order -> order.filledThisWeek() || order.status() == Status.FULFILLED)
                .map(order -> personKey(order.request().getName())).collect(java.util.stream.Collectors.toSet());
        orders = orders.stream().map(order -> new Order(order.request(), order.group(), order.protectedOrder(),
                order.donorSpecific(), served.contains(personKey(order.request().getName())), order.status(), order.delivered())).toList();
        Map<String, Double> available = new LinkedHashMap<>();
        totalYields.forEach((cell, amount) -> {
            requireQuantity(amount, "Yield for " + cell);
            available.put(cell, amount);
        });
        Map<String, List<Order>> groups = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order.group() == null || order.group().isBlank()) {
                throw new IllegalArgumentException("Each order must have a nonempty linked group.");
            }
            requireQuantity(order.request().getMinRequest(), "Minimum request");
            requireQuantity(order.request().getMaxRequest(), "Maximum request");
            requireQuantity(order.delivered(), "Delivered amount");
            if (order.status() != Status.FULFILLED && order.delivered() > 0) {
                throw new IllegalArgumentException("Delivered amounts require a recorded FULFILLED status for " + order.request().getName());
            }
            if (order.status() == Status.PENDING && order.request().getMaxRequest() <= 0) {
                throw new IllegalArgumentException("Enter a positive maximum request for " + order.request().getName());
            }
            if (order.request().getMinRequest() > order.request().getMaxRequest()) {
                throw new IllegalArgumentException("Minimum exceeds maximum for " + order.request().getName());
            }
            if (order.status() == Status.FULFILLED) {
                requireQuantity(order.delivered(), "Delivered amount");
                if (order.delivered() <= 0) {
                    throw new IllegalArgumentException("Enter the delivered amount for " + order.request().getName());
                }
                available.computeIfPresent(order.request().getCellType(), (cell, amount) -> amount - order.delivered());
            }
            groups.computeIfAbsent(order.group(), ignored -> new ArrayList<>()).add(order);
        }
        List<String> issues = new ArrayList<>();
        Map<Order, Decision> results = new LinkedHashMap<>();
        Set<List<Order>> removed = new LinkedHashSet<>();
        List<List<Order>> active = new ArrayList<>();
        Comparator<Order> priority = Comparator.comparing(Order::filledThisWeek)
                .thenComparing(Order::donorSpecific, Comparator.reverseOrder())
                .thenComparing((left, right) -> OrderPriority.compare(left.request(), right.request(), stats, signals));
        Comparator<List<Order>> groupPriority = (left, right) -> priority.compare(
                left.stream().min(priority).orElseThrow(), right.stream().min(priority).orElseThrow());

        for (List<Order> group : groups.values()) {
            boolean cancelled = group.stream().anyMatch(order -> order.status() == Status.CANCELLED);
            boolean locked = locked(group);
            if (cancelled) {
                removed.add(group);
                if (locked) {
                    issues.add("Conflicting linked outcomes/protection for " + group.get(0).request().getName()
                            + ": a linked order is cancelled. Review this group manually.");
                }
            } else {
                active.add(group);
            }
        }
        active.sort(groupPriority);
        while (true) {
            Set<String> shortages = shortages(active, available, minimumFirst);
            if (shortages.isEmpty()) {
                break;
            }
            List<Order> candidate = null;
            int lowestWeeklyLoss = Integer.MAX_VALUE;
            for (int index = active.size() - 1; index >= 0; index--) {
                List<Order> group = active.get(index);
                if (!locked(group) && group.stream().anyMatch(order -> order.status() == Status.PENDING
                        && shortages.contains(order.request().getCellType()) && required(order, minimumFirst) > 0)) {
                    int weeklyLoss = weeklyCoverageLoss(group, active);
                    if (weeklyLoss < lowestWeeklyLoss) {
                        candidate = group;
                        lowestWeeklyLoss = weeklyLoss;
                    }
                }
            }
            if (candidate == null) {
                for (String cell : shortages) {
                    double demand = demand(active, cell, minimumFirst);
                    issues.add(cell + ": protected/committed demand exceeds remaining yield by "
                            + format(demand - available.get(cell)) + ". No protected order was recommended for cancellation.");
                }
                break;
            }
            active.remove(candidate);
            removed.add(candidate);
        }
        List<List<Order>> reconsider = new ArrayList<>(removed.stream()
                .filter(group -> group.stream().noneMatch(order -> order.status() == Status.CANCELLED))
                .toList());
        while (!reconsider.isEmpty()) {
            reconsider.sort((left, right) -> {
                int byCoverage = Integer.compare(weeklyCoverageLoss(right, active), weeklyCoverageLoss(left, active));
                return byCoverage == 0 ? groupPriority.compare(left, right) : byCoverage;
            });
            List<Order> group = reconsider.remove(0);
            active.add(group);
            if (shortages(active, available, minimumFirst).isEmpty()) {
                removed.remove(group);
            } else {
                active.remove(group);
            }
        }
        active.sort(groupPriority);
        Map<String, Double> remaining = new LinkedHashMap<>(available);
        for (List<Order> group : active) {
            for (Order order : group) {
                if (order.status() == Status.PENDING) {
                    remaining.computeIfPresent(order.request().getCellType(),
                            (cell, amount) -> amount - required(order, minimumFirst));
                }
            }
        }
        Map<Order, Double> allocations = new LinkedHashMap<>();
        for (List<Order> group : active) {
            boolean unknown = group.stream().anyMatch(order -> order.status() == Status.PENDING
                    && !totalYields.containsKey(order.request().getCellType()));
            boolean shortage = group.stream().anyMatch(order ->
                    remaining.getOrDefault(order.request().getCellType(), 0.0) < -EPSILON);
            for (Order order : group) {
                if (order.status() != Status.PENDING || !totalYields.containsKey(order.request().getCellType())) {
                    continue;
                }
                String cell = order.request().getCellType();
                double allocation = required(order, minimumFirst);
                if (minimumFirst && !unknown && !shortage) {
                    double extra = Math.min(Math.max(0, remaining.getOrDefault(cell, 0.0)),
                            order.request().getMaxRequest() - allocation);
                    allocation += extra;
                    remaining.computeIfPresent(cell, (key, value) -> value - extra);
                }
                allocations.put(order, allocation);
            }
        }
        for (List<Order> group : groups.values()) {
            boolean pendingYield = group.stream().anyMatch(order -> order.status() == Status.PENDING
                    && !totalYields.containsKey(order.request().getCellType()));
            for (Order order : group) {
                String action;
                double allocation = 0;
                String reason;
                if (order.status() != Status.PENDING) {
                    action = "RECORDED " + order.status();
                    allocation = order.status() == Status.FULFILLED ? order.delivered() : 0;
                    reason = "Recorded outcome; not changed by this plan.";
                } else if (removed.contains(group)) {
                    boolean cancelledPartner = group.stream().anyMatch(partner -> partner.status() == Status.CANCELLED);
                    action = locked(group) ? "REVIEW" : "CANCEL";
                    reason = cancelledPartner ? "A linked order is already cancelled."
                            : "Removed to resolve a shortage while preserving weekly access and priority; linked orders stay together.";
                } else {
                    boolean groupShortage = group.stream().anyMatch(partner ->
                            remaining.getOrDefault(partner.request().getCellType(), 0.0) < -EPSILON);
                    action = groupShortage ? "REVIEW" : pendingYield ? "HOLD" : "FULFILL";
                    allocation = allocations.getOrDefault(order, 0.0);
                    reason = groupShortage ? "Insufficient yield for protected or committed linked orders."
                            : pendingYield ? "Provisional reservation only: a linked cell yield is not entered."
                            : order.protectedOrder() ? "Protected lab member or today's priority request."
                            : !order.filledThisWeek() ? "No fulfillment recorded this week; priority preserved."
                            : "Kept according to priority and available yield.";
                }
                int missed = signals.missedPickupCount(order.request().getName());
                if (order.status() == Status.PENDING && missed > 0) {
                    reason += " Missed pickups: " + missed + " (" + "*".repeat(missed)
                            + "); considered only after weekly access, donor specificity, and fulfillment-history factors."
                            + " This is a late tie-breaker, not an automatic cancellation.";
                }
                results.put(order, new Decision(order, action, allocation, reason));
            }
        }
        return new Plan(orders.stream().map(results::get).toList(), remaining, List.copyOf(issues));
    }

    private static boolean locked(List<Order> group) {
        return group.stream().anyMatch(order -> order.protectedOrder() || order.status() == Status.FULFILLED);
    }

    private static int weeklyCoverageLoss(List<Order> group, List<List<Order>> active) {
        Set<String> requesters = group.stream().filter(order -> !order.filledThisWeek()
                        && order.status() == Status.PENDING)
                .map(order -> personKey(order.request().getName())).collect(java.util.stream.Collectors.toSet());
        for (List<Order> other : active) {
            if (other != group) {
                other.stream().filter(order -> order.status() == Status.PENDING)
                        .map(order -> personKey(order.request().getName())).forEach(requesters::remove);
            }
        }
        return requesters.size();
    }

    private static double required(Order order, boolean minimumFirst) {
        return minimumFirst && order.request().getMinRequest() > 0
                ? order.request().getMinRequest() : order.request().getMaxRequest();
    }

    private static String personKey(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static Set<String> shortages(List<List<Order>> groups, Map<String, Double> available, boolean minimumFirst) {
        Set<String> result = new LinkedHashSet<>();
        available.forEach((cell, amount) -> {
            if (demand(groups, cell, minimumFirst) > amount + EPSILON) {
                result.add(cell);
            }
        });
        return result;
    }

    private static double demand(List<List<Order>> groups, String cell, boolean minimumFirst) {
        return groups.stream().flatMap(List::stream)
                .filter(order -> order.status() == Status.PENDING && order.request().getCellType().equals(cell))
                .mapToDouble(order -> required(order, minimumFirst)).sum();
    }

    private static void requireQuantity(double amount, String label) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException(label + " must be a finite, nonnegative number.");
        }
    }

    public static String format(double value) {
        return String.format(Locale.US, "%.3f", value);
    }
}
