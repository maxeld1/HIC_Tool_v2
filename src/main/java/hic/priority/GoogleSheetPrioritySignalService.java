package hic.priority;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class GoogleSheetPrioritySignalService {

    private static final int MISSED_PICKUP_LOOKBACK_DAYS = 28;

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            new DateTimeFormatterBuilder()
                    .appendPattern("M/d/")
                    .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
                    .toFormatter(Locale.US),
            new DateTimeFormatterBuilder()
                    .appendPattern("MM/dd/")
                    .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
                    .toFormatter(Locale.US)
    };

    public PrioritySheetSignals fetchSignals(String labMembersSheetUrl, String missedPickupsSheetUrl, LocalDate endDate) throws IOException {
        LocalDate startDate = endDate.minusDays(MISSED_PICKUP_LOOKBACK_DAYS);
        Set<String> labMembers = fetchLabMembers(labMembersSheetUrl);
        Map<String, Integer> missedPickups = fetchMissedPickups(missedPickupsSheetUrl, startDate, endDate);
        return new PrioritySheetSignals(labMembers, missedPickups);
    }

    private Set<String> fetchLabMembers(String sheetUrl) throws IOException {
        if (sheetUrl == null || sheetUrl.isBlank()) {
            return Set.of();
        }

        CsvTable table = fetchTable(sheetUrl);
        Set<String> members = new LinkedHashSet<>();
        boolean hasNameHeader = hasAnyHeader(table.headers(),
                "name", "names", "member", "members", "lab member", "lab members", "lab_member", "lab_members");

        if (!hasNameHeader) {
            for (String headerValue : table.originalHeaders()) {
                addName(members, headerValue);
            }
        }

        for (Map<String, String> row : table.rows()) {
            if (!isActive(row.get("active"))) {
                continue;
            }

            String name = firstPresent(row, "name", "names", "member", "members",
                    "lab member", "lab members", "lab_member", "lab_members");
            if (name.isBlank() && !hasNameHeader) {
                name = firstNonEmptyValue(row);
            }
            addName(members, name);
            addAliases(members, firstPresent(row, "alias", "aliases"));
        }
        return members;
    }

    private Map<String, Integer> fetchMissedPickups(String sheetUrl, LocalDate startDate, LocalDate endDate) throws IOException {
        if (sheetUrl == null || sheetUrl.isBlank()) {
            return Map.of();
        }

        List<Map<String, String>> rows = fetchRows(sheetUrl);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String name = firstPresent(row, "name", "ordered by", "ordered_by", "requester");
            LocalDate date = parseDate(firstPresent(row, "date of order", "date_of_order", "date", "order date", "order_date"));
            if (name.isBlank() || date == null || date.isBefore(startDate) || date.isAfter(endDate)) {
                continue;
            }
            if (isPickedUp(row.get("status"))) {
                continue;
            }

            String key = PrioritySheetSignals.normalizePerson(name);
            if (!key.isBlank()) {
                counts.merge(key, 1, Integer::sum);
            }
        }
        return counts;
    }

    private List<Map<String, String>> fetchRows(String sheetUrl) throws IOException {
        return fetchTable(sheetUrl).rows();
    }

    private CsvTable fetchTable(String sheetUrl) throws IOException {
        URL url = resolveCsvUrl(sheetUrl);
        List<Map<String, String>> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return new CsvTable(List.of(), List.of(), rows);
            }

            List<String> originalHeaders = parseCsvRow(headerLine);
            List<String> headers = originalHeaders.stream()
                    .map(this::normalizeHeader)
                    .toList();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = parseCsvRow(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? values.get(i).trim() : "");
                }
                rows.add(row);
            }

            return new CsvTable(originalHeaders, headers, rows);
        }
    }

    private URL resolveCsvUrl(String rawUrl) throws MalformedURLException {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new MalformedURLException("Google Sheet URL is empty.");
        }

        String trimmed = rawUrl.trim();
        if (trimmed.contains("/export?format=csv")) {
            return new URL(trimmed);
        }

        String id = extractSheetId(trimmed);
        if (id == null || id.isBlank()) {
            return new URL(trimmed);
        }

        String gid = extractQueryParam(trimmed, "gid");
        if (gid == null || gid.isBlank()) {
            gid = "0";
        }

        return new URL("https://docs.google.com/spreadsheets/d/"
                + URLEncoder.encode(id, StandardCharsets.UTF_8)
                + "/export?format=csv&gid="
                + URLEncoder.encode(gid, StandardCharsets.UTF_8));
    }

    private String extractSheetId(String url) {
        String marker = "/d/";
        int start = url.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = url.indexOf('/', start);
        return url.substring(start, end < 0 ? url.length() : end);
    }

    private String extractQueryParam(String url, String key) {
        int qIndex = url.indexOf('?');
        if (qIndex < 0 || qIndex >= url.length() - 1) {
            return null;
        }

        String query = url.substring(qIndex + 1);
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equalsIgnoreCase(key)) {
                return kv[1];
            }
        }
        return null;
    }

    private String firstPresent(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(normalizeHeader(key));
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String firstNonEmptyValue(Map<String, String> row) {
        for (String value : row.values()) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasAnyHeader(List<String> headers, String... candidates) {
        Set<String> normalizedCandidates = new LinkedHashSet<>();
        for (String candidate : candidates) {
            normalizedCandidates.add(normalizeHeader(candidate));
        }
        for (String header : headers) {
            if (normalizedCandidates.contains(normalizeHeader(header))) {
                return true;
            }
        }
        return false;
    }

    private void addName(Set<String> members, String name) {
        String normalized = PrioritySheetSignals.normalizePerson(name);
        if (!normalized.isBlank()) {
            members.add(normalized);
        }
    }

    private void addAliases(Set<String> members, String aliases) {
        if (aliases == null || aliases.isBlank()) {
            return;
        }
        for (String alias : aliases.split("[;,]")) {
            addName(members, alias);
        }
    }

    private boolean isActive(String active) {
        if (active == null || active.isBlank()) {
            return true;
        }
        String value = active.trim().toLowerCase(Locale.ROOT);
        return !value.equals("n")
                && !value.equals("no")
                && !value.equals("false")
                && !value.equals("inactive");
    }

    private boolean isPickedUp(String status) {
        if (status == null) {
            return false;
        }
        String value = status.trim().toLowerCase(Locale.ROOT);
        return value.equals("picked up")
                || value.equals("complete")
                || value.equals("completed");
    }

    private String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private List<String> parseCsvRow(String row) {
        List<String> values = new ArrayList<>();
        if (row == null) {
            return values;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < row.length(); i++) {
            char ch = row.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private record CsvTable(List<String> originalHeaders, List<String> headers, List<Map<String, String>> rows) {
    }
}
