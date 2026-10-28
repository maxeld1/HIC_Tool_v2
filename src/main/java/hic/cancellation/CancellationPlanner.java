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
        return plan(orders, totalYields, stats, minimumFirst, signals, Set.of());
    }

    public Plan plan(List<Order> orders, Map<String, Double> totalYields, FulfillmentStats stats,
                     boolean minimumFirst, PrioritySheetSignals signals, Set<String> notIsolated) {
        Map<String, Double> effectiveYields = new LinkedHashMap<>(totalYields);
        for (String cell : notIsolated) {
            if (orders.stream().anyMatch(order -> order.request().getCellType().equals(cell)
                    && order.status() == Status.FULFILLED && order.delivered() > 0)) {
                throw new IllegalArgumentException(cell + " already has recorded deliveries. Use Ready and enter its total usable yield instead of Not isolating today.");
            }
            effectiveYields.put(cell, 0.0);
        }
        Set<String> served = orders.stream()
                .filter(order -> order.filledThisWeek() || order.status() == Status.FULFILLED)
                .map(order -> personKey(order.request().getName())).collect(java.util.stream.Collectors.toSet());
        orders = orders.stream().map(order -> new Order(order.request(), order.group(), order.protectedOrder(),
                order.donorSpecific(), served.contains(personKey(order.request().getName())), order.status(), order.delivered())).toList();
        Map<String, Double> available = new LinkedHashMap<>();
        effectiveYields.forEach((cell, amount) -> {
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
        Map<List<Order>, String> removalReasons = new LinkedHashMap<>();
        List<List<Order>> active = new ArrayList<>();
        Comparator<Order> priority = (left, right) -> priorityComparison(left, right, stats, signals).result();
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
            List<List<Order>> eligible = new ArrayList<>();
            Map<List<Order>, Integer> weeklyLosses = new LinkedHashMap<>();
            int lowestWeeklyLoss = Integer.MAX_VALUE;
            for (int index = active.size() - 1; index >= 0; index--) {
                List<Order> group = active.get(index);
                if (!locked(group) && group.stream().anyMatch(order -> order.status() == Status.PENDING
                        && shortages.contains(order.request().getCellType()) && required(order, minimumFirst) > 0)) {
                    int weeklyLoss = weeklyCoverageLoss(group, active, available, minimumFirst);
                    eligible.add(group);
                    weeklyLosses.put(group, weeklyLoss);
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
            List<Order> alternative = null;
            int alternativeLoss = Integer.MAX_VALUE;
            for (List<Order> group : eligible) {
                if (group != candidate && weeklyLosses.get(group) < alternativeLoss) {
                    alternative = group;
                    alternativeLoss = weeklyLosses.get(group);
                }
            }
            List<Order> selected = candidate;
            String shortageDetails = shortages.stream().sorted().filter(cell -> groupDemand(selected, cell, minimumFirst) > 0)
                    .map(cell -> cell + " demand " + format(demand(active, cell, minimumFirst))
                            + ", available " + format(available.get(cell))).collect(java.util.stream.Collectors.joining("; "));
            removalReasons.put(candidate, explainRemoval(candidate, alternative, weeklyLosses, available, minimumFirst,
                    notIsolated, stats, signals, priority, active) + " Shortage at removal: " + shortageDetails + ".");
            active.remove(candidate);
            removed.add(candidate);
        }
        List<List<Order>> reconsider = new ArrayList<>(removed.stream()
                .filter(group -> group.stream().noneMatch(order -> order.status() == Status.CANCELLED))
                .toList());
        while (!reconsider.isEmpty()) {
            reconsider.sort((left, right) -> {
                int byCoverage = Integer.compare(weeklyCoverageLoss(right, active, available, minimumFirst),
                        weeklyCoverageLoss(left, active, available, minimumFirst));
                return byCoverage == 0 ? groupPriority.compare(left, right) : byCoverage;
            });
            List<Order> group = reconsider.remove(0);
            active.add(group);
            if (shortages(active, available, minimumFirst).isEmpty()) {
                removed.remove(group);
                removalReasons.remove(group);
            } else {
                String blockers = shortages(active, available, minimumFirst).stream().sorted()
                        .map(cell -> cell + " demand " + format(demand(active, cell, minimumFirst))
                                + ", available " + format(available.get(cell))).collect(java.util.stream.Collectors.joining("; "));
                removalReasons.computeIfPresent(group, (key, reason) -> reason
                        + " Reconsidered but not restored: the resulting plan still exceeds supply (" + blockers
                        + "). These blockers can include unresolved shortages in other cell types.");
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
                    && !effectiveYields.containsKey(order.request().getCellType()));
            boolean shortage = group.stream().anyMatch(order ->
                    remaining.getOrDefault(order.request().getCellType(), 0.0) < -EPSILON);
            for (Order order : group) {
                if (order.status() != Status.PENDING || !effectiveYields.containsKey(order.request().getCellType())) {
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
                    && !effectiveYields.containsKey(order.request().getCellType()));
            List<String> unavailableCells = group.stream().filter(order -> order.status() == Status.PENDING)
                    .map(order -> order.request().getCellType()).filter(notIsolated::contains).distinct().sorted().toList();
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
                    reason = cancelledPartner ? "Deciding factor: recorded linked cancellation. "
                            + group.stream().filter(partner -> partner.status() == Status.CANCELLED)
                            .map(partner -> orderLabel(partner)).collect(java.util.stream.Collectors.joining(", "))
                            + " is already recorded CANCELLED."
                            : removalReasons.get(group);
                    if (group.size() > 1) reason += " All-or-none group " + order.group()
                            + " stays together; this row follows the group decision even if its own cell yield is sufficient.";
                    if (locked(group)) reason += " Protection or an existing fulfilled commitment conflicts with cancellation; manual review is required.";
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
                if (order.status() == Status.PENDING && !unavailableCells.isEmpty() && !removed.contains(group)) {
                    reason += " Not isolating today: " + String.join(", ", unavailableCells)
                            + ". This all-or-none group cannot be fulfilled; protections or existing commitments require manual review rather than automatic cancellation.";
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

    private static OrderPriority.Comparison priorityComparison(Order left, Order right, FulfillmentStats stats,
                                                               PrioritySheetSignals signals) {
        int weekly = Boolean.compare(left.filledThisWeek(), right.filledThisWeek());
        if (weekly != 0) {
            return new OrderPriority.Comparison(weekly, "Recorded weekly fulfillment",
                    orderLabel(left) + ": filled this week = " + left.filledThisWeek() + "; " + orderLabel(right)
                            + ": filled this week = " + right.filledThisWeek() + ". A requester not yet served is favored.");
        }
        OrderPriority.Comparison history = OrderPriority.compareHistory(left.request(), right.request(), stats);
        if (history.result() != 0) return history;
        int donor = Boolean.compare(right.donorSpecific(), left.donorSpecific());
        if (donor != 0) {
            return new OrderPriority.Comparison(donor, "Donor-specific tie-breaker",
                    orderLabel(left) + ": donor-specific = " + left.donorSpecific() + "; " + orderLabel(right)
                            + ": donor-specific = " + right.donorSpecific()
                            + ". This preference applies only after weekly access and fulfillment history tie.");
        }
        return OrderPriority.compareTieBreakers(left.request(), right.request(), signals);
    }

    private static String explainRemoval(List<Order> group, List<Order> alternative, Map<List<Order>, Integer> losses,
                                         Map<String, Double> available, boolean minimumFirst, Set<String> notIsolated,
                                         FulfillmentStats stats, PrioritySheetSignals signals, Comparator<Order> priority,
                                         List<List<Order>> active) {
        List<String> unavailable = group.stream().filter(order -> order.status() == Status.PENDING)
                .map(order -> order.request().getCellType()).filter(notIsolated::contains).distinct().sorted().toList();
        if (!unavailable.isEmpty()) {
            return "Deciding factor: not isolating today — " + String.join(", ", unavailable)
                    + ". The request/linked group needs an unavailable cell type.";
        }
        for (String cell : available.keySet()) {
            double required = groupDemand(group, cell, minimumFirst);
            if (required > available.get(cell) + EPSILON) {
                return "Deciding factor: insufficient yield to fit this group. " + cell + " needs " + format(required)
                        + " (" + (minimumFirst ? "minimum-first" : "maximum-first") + "), but only " + format(available.get(cell))
                        + " is available after recorded deliveries, even before allocating other requests.";
            }
        }
        if (alternative == null) {
            return "Deciding factor: protection/commitment constraints. This was the only unprotected, uncommitted group"
                    + " eligible to relieve the shortage; other groups were locked or did not contribute to that shortage.";
        }
        int loss = losses.get(group);
        int otherLoss = losses.get(alternative);
        String comparison = " Comparison at this removal step: " + groupLabel(group) + " versus " + groupLabel(alternative)
                + " (the compared group may also be cancelled later).";
        if (loss != otherLoss) {
            return "Deciding factor: weekly access. Removing this group loses " + loss
                    + " requester's first feasible weekly allocation, versus " + otherLoss
                    + " for the next cancellation candidate. " + weeklyCoverageContext(group, active, available, minimumFirst)
                    + " Unknown or unresolved alternatives are not counted." + comparison;
        }
        Order left = group.stream().min(priority).orElseThrow();
        Order right = alternative.stream().min(priority).orElseThrow();
        OrderPriority.Comparison deciding = priorityComparison(left, right, stats, signals);
        return "Deciding factor: " + deciding.factor() + ". " + deciding.detail()
                + " The immediate weekly-access losses tied at " + loss + "." + comparison;
    }

    private static String orderLabel(Order order) {
        return order.request().getName() + " #" + order.request().getOrderNumber() + " " + order.request().getCellType();
    }

    private static String groupLabel(List<Order> group) {
        return group.stream().map(CancellationPlanner::orderLabel).collect(java.util.stream.Collectors.joining(" + "));
    }

    private static double groupDemand(List<Order> group, String cell, boolean minimumFirst) {
        return group.stream().filter(order -> order.status() == Status.PENDING && order.request().getCellType().equals(cell))
                .mapToDouble(order -> required(order, minimumFirst)).sum();
    }

    private static int weeklyCoverageLoss(List<Order> group, List<List<Order>> active,
                                          Map<String, Double> available, boolean minimumFirst) {
        Set<String> requesters = group.stream().filter(order -> !order.filledThisWeek()
                        && order.status() == Status.PENDING)
                .map(order -> personKey(order.request().getName())).collect(java.util.stream.Collectors.toSet());
        Map<String, Double> demandAfterRemoval = pendingDemandAfterRemoval(group, active, minimumFirst);
        for (List<Order> other : active) {
            if (other != group && providesKnownWeeklyCoverage(other, available, demandAfterRemoval)) {
                other.stream().filter(order -> order.status() == Status.PENDING)
                        .map(order -> personKey(order.request().getName())).forEach(requesters::remove);
            }
        }
        return requesters.size();
    }

    private static Map<String, Double> pendingDemandAfterRemoval(List<Order> group, List<List<Order>> active,
                                                                boolean minimumFirst) {
        Map<String, Double> demand = new LinkedHashMap<>();
        active.stream().filter(other -> other != group).flatMap(List::stream)
                .filter(order -> order.status() == Status.PENDING)
                .forEach(order -> demand.merge(order.request().getCellType(), required(order, minimumFirst), Double::sum));
        return demand;
    }

    private static String weeklyCoverageContext(List<Order> group, List<List<Order>> active,
                                                 Map<String, Double> available, boolean minimumFirst) {
        Set<String> people = group.stream().map(order -> personKey(order.request().getName())).collect(java.util.stream.Collectors.toSet());
        String served = group.stream().filter(Order::filledThisWeek).map(order -> order.request().getName()).distinct()
                .collect(java.util.stream.Collectors.joining(", "));
        Map<String, Double> demand = pendingDemandAfterRemoval(group, active, minimumFirst);
        String alternatives = active.stream().filter(other -> other != group && providesKnownWeeklyCoverage(other, available, demand))
                .flatMap(List::stream).filter(order -> order.status() == Status.PENDING && people.contains(personKey(order.request().getName())))
                .map(CancellationPlanner::orderLabel).distinct().collect(java.util.stream.Collectors.joining(", "));
        return (served.isEmpty() ? "" : "Already served this week: " + served + ". ")
                + (alternatives.isEmpty() ? "No other known feasible allocation for this group was counted."
                : "Other known feasible allocations counted: " + alternatives + ".");
    }

    private static boolean providesKnownWeeklyCoverage(List<Order> alternative, Map<String, Double> available,
                                                       Map<String, Double> demandAfterRemoval) {
        for (Order order : alternative) {
            if (order.status() != Status.PENDING) continue;
            String cell = order.request().getCellType();
            Double supply = available.get(cell);
            if (supply == null) return false;
            if (demandAfterRemoval.getOrDefault(cell, 0.0) > supply + EPSILON) return false;
        }
        return true;
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
