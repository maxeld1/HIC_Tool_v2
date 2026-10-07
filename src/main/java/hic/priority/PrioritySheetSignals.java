package hic.priority;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class PrioritySheetSignals {

    public static final PrioritySheetSignals EMPTY = new PrioritySheetSignals(Set.of(), Map.of());

    private final Set<String> labMemberKeys;
    private final Map<String, Integer> missedPickupCounts;
    private final Set<String> todayRequestKeys;

    public PrioritySheetSignals(Set<String> labMemberKeys, Map<String, Integer> missedPickupCounts) {
        this(labMemberKeys, missedPickupCounts, Set.of());
    }

    public PrioritySheetSignals(Set<String> labMemberKeys, Map<String, Integer> missedPickupCounts,
                                Set<String> todayRequestKeys) {
        this.labMemberKeys = new LinkedHashSet<>(labMemberKeys);
        this.missedPickupCounts = new LinkedHashMap<>(missedPickupCounts);
        this.todayRequestKeys = new LinkedHashSet<>(todayRequestKeys);
    }

    public boolean isLabMember(String name) {
        return labMemberKeys.contains(normalizePerson(name));
    }

    public int missedPickupCount(String name) {
        return missedPickupCounts.getOrDefault(normalizePerson(name), 0);
    }

    public boolean isTodayRequest(int requestId, String name) {
        return todayRequestKeys.contains(requestKey(String.valueOf(requestId), name));
    }

    public int todayRequestCount() {
        return todayRequestKeys.size();
    }

    public boolean hasSignals() {
        return !labMemberKeys.isEmpty() || !missedPickupCounts.isEmpty() || !todayRequestKeys.isEmpty();
    }

    public static String requestKey(String requestId, String name) {
        String normalizedRequestId = normalizeRequestId(requestId);
        String normalizedName = normalizePerson(name);
        if (normalizedRequestId.isBlank() || normalizedName.isBlank()) {
            return "";
        }
        return normalizedRequestId + "|" + normalizedName;
    }

    public static String normalizeRequestId(String requestId) {
        if (requestId == null) {
            return "";
        }
        String normalized = requestId.trim();
        if (normalized.endsWith(".0")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        return normalized;
    }

    public static String normalizePerson(String name) {
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
}
