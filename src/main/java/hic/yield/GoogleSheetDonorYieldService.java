package hic.yield;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GoogleSheetDonorYieldService {

    private static final DateTimeFormatter[] DATE_ONLY_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    };

    private static final DateTimeFormatter[] DATE_TIME_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")
    };

    public List<DonorYieldRecord> getMostRecentDonationByCellType(String sheetCsvUrl, String donorId) throws IOException {
        if (sheetCsvUrl == null || sheetCsvUrl.isBlank()) {
            return List.of();
        }

        List<DonorYieldRecord> donorRecords = fetchRecords(sheetCsvUrl).stream()
                .filter(r -> normalizeDonorId(r.getDonorId()).equals(normalizeDonorId(donorId)))
                .toList();

        if (donorRecords.isEmpty()) {
            return List.of();
        }

        LocalDate mostRecent = donorRecords.stream()
                .map(DonorYieldRecord::getDate)
                .max(LocalDate::compareTo)
                .orElse(null);

        if (mostRecent == null) {
            return List.of();
        }

        Map<String, DonorYieldRecord> latestByCell = new HashMap<>();
        for (DonorYieldRecord record : donorRecords) {
            if (!mostRecent.equals(record.getDate())) {
                continue;
            }
            String canonical = canonicalizeCellType(record.getCellType());
            if (canonical == null) {
                continue;
            }
            latestByCell.put(canonical, new DonorYieldRecord(
                    record.getDonorId(),
                    record.getDate(),
                    canonical,
                    record.getRequestedCells(),
                    record.getActualYield(),
                    record.getApheresisUsed(),
                    record.getNotes()
            ));
        }

        return new ArrayList<>(latestByCell.values());
    }

    public List<DonorYieldRecord> fetchRecords(String sheetCsvUrl) throws IOException {
        URL url = resolveCsvUrl(sheetCsvUrl);
        List<DonorYieldRecord> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return records;
            }

            List<String> headers = parseCsvRow(headerLine);
            Map<String, Integer> idx = headerIndex(headers);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> row = parseCsvRow(line);

                String donorId = get(row, idx.get("donor_id"));
                LocalDate date = parseDate(get(row, idx.get("date")));
                String cellType = get(row, idx.get("cell_type"));
                double requestedCells = parseDouble(get(row, idx.get("requested_cells")));
                double actualYield = parseDouble(get(row, idx.get("actual_yield")));
                double apheresisUsed = parseDouble(get(row, idx.get("apheresis_used")));
                String notes = get(row, idx.get("notes"));

                if (donorId.isBlank() || date == null || cellType.isBlank()) {
                    continue;
                }

                records.add(new DonorYieldRecord(donorId, date, cellType, requestedCells, actualYield, apheresisUsed, notes));
            }
        }

        return records;
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

        String csvUrl = "https://docs.google.com/spreadsheets/d/"
                + URLEncoder.encode(id, StandardCharsets.UTF_8)
                + "/export?format=csv&gid="
                + URLEncoder.encode(gid, StandardCharsets.UTF_8);

        return new URL(csvUrl);
    }

    private String extractSheetId(String url) {
        String marker = "/d/";
        int start = url.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = url.indexOf('/', start);
        if (end < 0) {
            end = url.length();
        }
        return url.substring(start, end);
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

    public String normalizeDonorId(String donorId) {
        if (donorId == null) {
            return "";
        }
        return donorId.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public String canonicalizeCellType(String raw) {
        if (raw == null) {
            return null;
        }

        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return null;
        }

        if (value.contains("cd4")) return "CD4+";
        if (value.contains("cd8")) return "CD8+";
        if (value.contains("nk")) return "NK Cells";
        if (value.equals("b") || value.contains("b cells") || value.contains("bcells")) return "B Cells";
        if (value.contains("total t") || value.equals("total")) return "Total T";
        if (value.contains("mono")) return "Monocytes";
        if (value.contains("pbmc")) return "PBMC";
        if (value.contains("unpurified")) return "Unpurified Apheresis";
        if (value.contains("top")) return "Top Layer Ficoll";
        if (value.contains("bottom")) return "Bottom Layer Ficoll";
        return null;
    }

    private Map<String, Integer> headerIndex(List<String> headers) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            idx.put(headers.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return idx;
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

    private String get(List<String> row, Integer idx) {
        if (idx == null || idx < 0 || idx >= row.size()) {
            return "";
        }
        return row.get(idx).trim();
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (DateTimeFormatter formatter : DATE_ONLY_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                return java.time.LocalDateTime.parse(value, formatter).toLocalDate();
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }
}
