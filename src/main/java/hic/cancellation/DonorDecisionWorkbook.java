package hic.cancellation;

import hic.hiccell.FulfillmentStats;
import hic.hiccell.FulfillmentStatsService;
import hic.priority.PrioritySheetSignals;
import hic.util.HICData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DonorDecisionWorkbook {
    public static final String FILE_NAME = "Low_Yield_Order_Priority.xlsx";
    private static final String SHEET_NAME = "Cancellation Inputs";

    public record Snapshot(String donor, LocalDate date, LocalDateTime generatedAt, List<HICData> orders,
                           PrioritySheetSignals signals, FulfillmentStats stats) {
    }

    private DonorDecisionWorkbook() {
    }

    public static void write(Workbook workbook, List<HICData> orders, FulfillmentStats stats,
                             PrioritySheetSignals signals, String donor, LocalDate date, boolean protectionsVerified) {
        Sheet sheet = workbook.createSheet(SHEET_NAME);
        Row metadata = sheet.createRow(0);
        metadata.createCell(0).setCellValue("HIC cancellation inputs");
        metadata.createCell(1).setCellValue(2);
        metadata.createCell(2).setCellValue(donor.trim().toUpperCase(Locale.ROOT));
        metadata.createCell(3).setCellValue(date.toString());
        metadata.createCell(4).setCellValue(LocalDateTime.now().toString());
        metadata.createCell(5).setCellValue(protectionsVerified);
        metadata.createCell(6).setCellValue(stats != null);
        Row header = sheet.createRow(1);
        String[] headers = {"ID", "Order #", "Request Date", "Name", "Cell Type", "Max", "Min",
                "Recently Cancelled", "Request Type", "Requester Comment", "Lab Member", "Today Request",
                "Fulfilled", "Cancelled", "Cell Filled This Week", "Requester Filled This Week", "Has Stats", "Missed Pickup Count"};
        for (int index = 0; index < headers.length; index++) {
            header.createCell(index).setCellValue(headers[index]);
        }
        for (int index = 0; index < orders.size(); index++) {
            HICData order = orders.get(index);
            Row row = sheet.createRow(index + 2);
            row.createCell(0).setCellValue(order.getID());
            row.createCell(1).setCellValue(order.getOrderNumber());
            row.createCell(2).setCellValue(order.getRequestDate().toString());
            row.createCell(3).setCellValue(order.getName());
            row.createCell(4).setCellValue(order.getCellType());
            row.createCell(5).setCellValue(order.getMaxRequest());
            row.createCell(6).setCellValue(order.getMinRequest());
            row.createCell(7).setCellValue(order.getRecentlyCancelledRequests());
            row.createCell(8).setCellValue(order.getRequestType());
            row.createCell(9).setCellValue(order.getRequesterComment());
            row.createCell(10).setCellValue(signals.isLabMember(order.getName()));
            row.createCell(11).setCellValue(signals.isTodayRequest(order.getOrderNumber(), order.getName()));
            FulfillmentStats.StatLine line = stats == null ? null : stats.findLine(order.getName(), order.getCellType());
            row.createCell(12).setCellValue(line == null ? 0 : line.fulfilled());
            row.createCell(13).setCellValue(line == null ? 0 : line.cancelled());
            row.createCell(14).setCellValue(line != null && line.filledThisWeek());
            row.createCell(15).setCellValue(stats != null && stats.requesterFilledThisWeek(order.getName()));
            row.createCell(16).setCellValue(line != null);
            row.createCell(17).setCellValue(signals.missedPickupCount(order.getName()));
        }
        workbook.setSheetVisibility(workbook.getSheetIndex(sheet), SheetVisibility.VERY_HIDDEN);
    }

    public static Snapshot read(Path folder) throws IOException {
        Path file = folder.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Select the donor/date folder containing " + FILE_NAME + ".");
        }
        try (var input = Files.newInputStream(file); Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new IOException("This priority workbook predates saved cancellation inputs. Run Perform All Actions or Make Labels once with the updated program, then select the new donor folder.");
            }
            Row metadata = sheet.getRow(0);
            int version = integer(metadata, 1);
            if (!"HIC cancellation inputs".equals(text(metadata, 0)) || (version != 1 && version != 2)) {
                throw new IOException("Unsupported cancellation input format. Regenerate the priority workbook.");
            }
            String donor = requiredText(metadata, 2);
            LocalDate date = LocalDate.parse(requiredText(metadata, 3));
            LocalDateTime generatedAt = LocalDateTime.parse(requiredText(metadata, 4));
            if (!flag(metadata, 5)) {
                throw new IOException("Lab member and Today's Requests protections were not verified when this workbook was generated. Fix the sheet settings and regenerate it before planning cancellations.");
            }
            boolean historyAvailable = flag(metadata, 6);
            List<HICData> orders = new ArrayList<>();
            Set<String> identities = new HashSet<>();
            Set<String> members = new LinkedHashSet<>();
            Set<String> todayRequests = new LinkedHashSet<>();
            Set<String> weeklyRequesters = new LinkedHashSet<>();
            Map<String, FulfillmentStats.StatLine> lines = new LinkedHashMap<>();
            Map<String, Integer> missedPickups = version == 1 ? readLegacyMissedPickups(workbook) : new LinkedHashMap<>();
            for (int index = 2; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) {
                    throw new IOException("Missing order row in saved cancellation inputs.");
                }
                HICData order = new HICData(integer(row, 0), integer(row, 1),
                        LocalDateTime.parse(requiredText(row, 2)), requiredText(row, 3), requiredText(row, 4),
                        quantity(row, 5), quantity(row, 6), text(row, 7), text(row, 8), text(row, 9));
                if (order.getMinRequest() > order.getMaxRequest()) {
                    throw new IOException("Invalid saved request range for #" + order.getOrderNumber());
                }
                String identity = order.getOrderNumber() + "|" + fullName(order.getName()) + "|" + order.getCellType();
                if (!identities.add(identity)) {
                    throw new IOException("Duplicate order in saved cancellation inputs: " + identity);
                }
                orders.add(order);
                String person = PrioritySheetSignals.normalizePerson(order.getName());
                if (version == 2) {
                    int missed = integer(row, 17);
                    Integer previous = missedPickups.putIfAbsent(person, missed);
                    if (previous != null && previous != missed) {
                        throw new IOException("Conflicting saved missed-pickup counts for " + order.getName());
                    }
                }
                if (flag(row, 10)) {
                    members.add(person);
                }
                if (flag(row, 11)) {
                    todayRequests.add(PrioritySheetSignals.requestKey(String.valueOf(order.getOrderNumber()), order.getName()));
                }
                if (flag(row, 15)) {
                    weeklyRequesters.add(person);
                }
                if (flag(row, 16)) {
                    String key = person + "|" + FulfillmentStatsService.normalizeCellType(order.getCellType()).toLowerCase(Locale.ROOT);
                    FulfillmentStats.StatLine line = new FulfillmentStats.StatLine(order.getName(), order.getCellType(),
                            integer(row, 12), integer(row, 13), flag(row, 14));
                    FulfillmentStats.StatLine previous = lines.putIfAbsent(key, line);
                    if (previous != null && (previous.fulfilled() != line.fulfilled() || previous.cancelled() != line.cancelled()
                            || previous.filledThisWeek() != line.filledThisWeek())) {
                        throw new IOException("Conflicting saved fulfillment statistics for " + order.getName());
                    }
                }
            }
            if (orders.isEmpty()) {
                throw new IOException("The selected workbook has no saved orders.");
            }
            FulfillmentStats stats = historyAvailable
                    ? new FulfillmentStats(date.minusDays(21), date, lines, 0, 0, weeklyRequesters) : null;
            return new Snapshot(donor, date, generatedAt, List.copyOf(orders),
                    new PrioritySheetSignals(members, missedPickups, todayRequests), stats);
        } catch (RuntimeException exception) {
            throw new IOException("Could not read saved cancellation inputs from " + file + ". Regenerate the workbook if it was edited or damaged.", exception);
        }
    }

    private static Map<String, Integer> readLegacyMissedPickups(Workbook workbook) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : List.of("CD4 CD8 Requests", "Other Requests")) {
            Sheet sheet = workbook.getSheet(name);
            if (sheet == null) continue;
            int nameColumn = -1;
            int missedColumn = -1;
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() != CellType.STRING) continue;
                    if (cell.getStringCellValue().equals("Name")) nameColumn = cell.getColumnIndex();
                    if (cell.getStringCellValue().equals("Missed Pickups")) missedColumn = cell.getColumnIndex();
                }
                if (nameColumn < 0 || missedColumn < 0) continue;
                Cell requester = row.getCell(nameColumn);
                Cell missed = row.getCell(missedColumn);
                if (requester == null || missed == null || requester.getCellType() != CellType.STRING
                        || missed.getCellType() != CellType.STRING) continue;
                int stars = (int) missed.getStringCellValue().chars().filter(character -> character == '*').count();
                if (stars > 0) counts.merge(PrioritySheetSignals.normalizePerson(requester.getStringCellValue()), stars, Math::max);
            }
        }
        return counts;
    }

    private static String text(Row row, int column) throws IOException {
        Cell cell = row == null ? null : row.getCell(column);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        if (cell.getCellType() != CellType.STRING) {
            throw new IOException("Expected text in saved cancellation inputs, column " + column);
        }
        return cell.getStringCellValue();
    }

    private static String requiredText(Row row, int column) throws IOException {
        String value = text(row, column);
        if (value.isBlank()) {
            throw new IOException("Missing required text in saved cancellation inputs, column " + column);
        }
        return value;
    }

    private static boolean flag(Row row, int column) throws IOException {
        Cell cell = row == null ? null : row.getCell(column);
        if (cell == null || cell.getCellType() != CellType.BOOLEAN) {
            throw new IOException("Missing boolean flag in saved cancellation inputs, column " + column);
        }
        return cell.getBooleanCellValue();
    }

    private static double quantity(Row row, int column) throws IOException {
        Cell cell = row == null ? null : row.getCell(column);
        if (cell == null || cell.getCellType() != CellType.NUMERIC) {
            throw new IOException("Missing numeric value in saved cancellation inputs, column " + column);
        }
        double value = cell.getNumericCellValue();
        if (!Double.isFinite(value) || value < 0) {
            throw new IOException("Invalid quantity in saved cancellation inputs, column " + column);
        }
        return value;
    }

    private static int integer(Row row, int column) throws IOException {
        double value = quantity(row, column);
        if (value != Math.rint(value) || value > Integer.MAX_VALUE) {
            throw new IOException("Invalid integer in saved cancellation inputs, column " + column);
        }
        return (int) value;
    }

    private static String fullName(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

}
