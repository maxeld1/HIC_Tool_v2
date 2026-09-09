package hic.priority;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class PrioritySheetSignals {

    public static final PrioritySheetSignals EMPTY = new PrioritySheetSignals(Set.of(), Map.of());

    private final Set<String> labMemberKeys;
    private final Map<String, Integer> missedPickupCounts;

    public PrioritySheetSignals(Set<String> labMemberKeys, Map<String, Integer> missedPickupCounts) {
        this.labMemberKeys = new LinkedHashSet<>(labMemberKeys);
        this.missedPickupCounts = new LinkedHashMap<>(missedPickupCounts);
    }

    public boolean isLabMember(String name) {
        return labMemberKeys.contains(normalizePerson(name));
    }

    public int missedPickupCount(String name) {
        return missedPickupCounts.getOrDefault(normalizePerson(name), 0);
    }

    public boolean hasSignals() {
        return !labMemberKeys.isEmpty() || !missedPickupCounts.isEmpty();
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
