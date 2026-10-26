package hic.cancellation;

import hic.util.HICData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CancellationRecommendationWorkbook {
    private CancellationRecommendationWorkbook() {
    }

    public static List<CancellationPlanner.Decision> cancellations(CancellationPlanner.Plan plan, Map<String, Double> yields) {
        return plan.decisions().stream().filter(decision -> decision.action().equals("CANCEL")
                        && decision.order().status() == CancellationPlanner.Status.PENDING
                        && !decision.order().protectedOrder()
                        && yields.containsKey(decision.order().request().getCellType()))
                .sorted(Comparator.comparing((CancellationPlanner.Decision decision) -> decision.order().request().getCellType())
                        .thenComparing(decision -> decision.order().request().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(decision -> decision.order().request().getOrderNumber())).toList();
    }

    public static synchronized Path write(Path folder, String donor, LocalDate date, CancellationPlanner.Plan plan,
                              Map<String, Double> yields) throws IOException {
        Map<String, CancellationPlanner.Decision> decisions = new LinkedHashMap<>();
        plan.decisions().forEach(decision -> decisions.put(key(decision.order().request().getOrderNumber(),
                decision.order().request().getName(), decision.order().request().getCellType()), decision));
        Path output = folder.resolve("Cancellation_Priority_" + donor.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9._-]", "_") + "_" + date + ".xlsx");
        Path temporary = null;
        try (var input = Files.newInputStream(folder.resolve(DonorDecisionWorkbook.FILE_NAME));
             Workbook workbook = new XSSFWorkbook(input)) {
            for (String name : List.of("CD4 CD8 Requests", "Other Requests")) {
                Sheet sheet = workbook.getSheet(name);
                if (sheet == null) {
                    throw new IOException("The source priority workbook is missing sheet: " + name);
                }
                decorate(sheet, workbook, decisions, yields);
            }
            int iteration = appendHistory(workbook, output, donor, date, plan, yields);
            writeSummary(workbook, donor, date, plan, yields);
            workbook.getSheet("Cancellation Summary").getRow(1).getCell(0).setCellValue(
                    "Iteration " + iteration + " — " + LocalDateTime.now() + ". Earlier decisions are retained in Decision History. Recommendations only.");
            temporary = Files.createTempFile(folder, "cancellation-save-", ".tmp");
            try (var stream = Files.newOutputStream(temporary)) {
                workbook.write(stream);
            }
            try {
                Files.move(temporary, output, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                if (Files.exists(output)) {
                    throw new IOException("This folder does not support safely replacing the cancellation workbook. Use a local donor folder; existing history was preserved.", exception);
                }
                Files.move(temporary, output);
            }
            return output;
        } catch (java.nio.file.AccessDeniedException exception) {
            throw new IOException("Close the cancellation workbook in Excel and try again. The existing workbook was preserved: " + output, exception);
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    private static int appendHistory(Workbook workbook, Path output, String donor, LocalDate date,
                                     CancellationPlanner.Plan plan, Map<String, Double> yields) throws IOException {
        Sheet history = workbook.createSheet("Decision History");
        String[] headers = {"Iteration", "Calculated At", "Donor", "Collection Date", "Cell Type", "Entered Yield",
                "Order #", "Requester", "Decision", "Allocation", "Minimum", "Maximum", "Requester Comment",
                "Reason", "Linked Group", "Protected", "Unresolved Issues"};
        Row header = history.createRow(0);
        for (int column = 0; column < headers.length; column++) header.createCell(column).setCellValue(headers[column]);
        int iteration = 1;
        if (Files.exists(output)) {
            try (var input = Files.newInputStream(output); Workbook previous = new XSSFWorkbook(input)) {
                Sheet metadata = previous.getSheet("Cancellation Output");
                Sheet previousHistory = previous.getSheet("Decision History");
                if (metadata == null || metadata.getRow(0) == null || previousHistory == null) {
                    throw new IOException("The existing cancellation workbook has no appendable decision history. Keep it as an archive and rename it before retrying: " + output);
                }
                Row values = metadata.getRow(0);
                DataFormatter formatter = new DataFormatter(Locale.US);
                if (!formatter.formatCellValue(values.getCell(0)).equals("HIC cancellation history v1")
                        || !formatter.formatCellValue(values.getCell(1)).equals(donor.trim().toUpperCase(Locale.ROOT))
                        || !formatter.formatCellValue(values.getCell(2)).equals(date.toString())
                        || values.getCell(3) == null || values.getCell(3).getCellType() != CellType.NUMERIC
                        || values.getCell(3).getNumericCellValue() < 1
                        || values.getCell(3).getNumericCellValue() != Math.rint(values.getCell(3).getNumericCellValue())
                        || values.getCell(3).getNumericCellValue() >= Integer.MAX_VALUE) {
                    throw new IOException("The existing cancellation workbook's donor, date, or history metadata does not match. It was not overwritten.");
                }
                iteration = (int) values.getCell(3).getNumericCellValue() + 1;
                for (int column = 0; column < headers.length; column++) {
                    if (previousHistory.getRow(0) == null
                            || !formatter.formatCellValue(previousHistory.getRow(0).getCell(column)).equals(headers[column])) {
                        throw new IOException("The existing decision history columns were changed. The workbook was not overwritten.");
                    }
                }
                for (int index = 1; index <= previousHistory.getLastRowNum(); index++) {
                    Row source = previousHistory.getRow(index);
                    if (source == null) throw new IOException("An existing decision history row is missing.");
                    Row target = history.createRow(index);
                    for (Cell cell : source) {
                        Cell copied = target.createCell(cell.getColumnIndex());
                        switch (cell.getCellType()) {
                            case STRING -> copied.setCellValue(cell.getStringCellValue());
                            case NUMERIC -> copied.setCellValue(cell.getNumericCellValue());
                            case BOOLEAN -> copied.setCellValue(cell.getBooleanCellValue());
                            case BLANK -> copied.setBlank();
                            default -> throw new IOException("Unsupported cell in existing decision history. The workbook was not overwritten.");
                        }
                    }
                }
            }
        }
        String calculatedAt = LocalDateTime.now().toString();
        for (CancellationPlanner.Decision decision : plan.decisions()) {
            HICData request = decision.order().request();
            String action = !yields.containsKey(request.getCellType())
                    ? decision.order().status() != CancellationPlanner.Status.PENDING ? "RECORDED " + decision.order().status()
                        : decision.action().equals("CANCEL") ? "LINKED — NO YIELD ENTERED" : "NO YIELD ENTERED"
                    : decision.action().equals("CANCEL") && !decision.order().protectedOrder()
                        && decision.order().status() == CancellationPlanner.Status.PENDING ? "SHOULD CANCEL" : decision.action();
            Object[] values = {iteration, calculatedAt, donor.trim().toUpperCase(Locale.ROOT), date.toString(), request.getCellType(),
                    yields.get(request.getCellType()), request.getOrderNumber(), request.getName(), action, decision.allocation(),
                    request.getMinRequest(), request.getMaxRequest(), request.getRequesterComment(), decision.reason(),
                    decision.order().group(), decision.order().protectedOrder(), String.join(" | ", plan.issues())};
            Row row = history.createRow(history.getLastRowNum() + 1);
            for (int column = 0; column < values.length; column++) {
                Cell cell = row.createCell(column);
                if (values[column] instanceof Number number) cell.setCellValue(number.doubleValue());
                else if (values[column] instanceof Boolean flag) cell.setCellValue(flag);
                else if (values[column] != null) cell.setCellValue(values[column].toString());
            }
        }
        CellStyle wrap = workbook.createCellStyle();
        wrap.setWrapText(true);
        CellStyle cancelled = workbook.createCellStyle();
        cancelled.cloneStyleFrom(wrap);
        cancelled.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        cancelled.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        for (Row row : history) {
            row.setHeightInPoints(row.getRowNum() == 0 ? 25 : 45);
            boolean cancel = row.getCell(8) != null && row.getCell(8).getCellType() == CellType.STRING
                    && "SHOULD CANCEL".equals(row.getCell(8).getStringCellValue());
            for (Cell cell : row) cell.setCellStyle(cancel ? cancelled : wrap);
        }
        for (int column = 0; column < headers.length; column++) history.setColumnWidth(column, (column >= 12 ? 55 : 25) * 256);
        history.createFreezePane(0, 1);
        history.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, history.getLastRowNum(), 0, headers.length - 1));
        Sheet metadata = workbook.createSheet("Cancellation Output");
        Row values = metadata.createRow(0);
        values.createCell(0).setCellValue("HIC cancellation history v1");
        values.createCell(1).setCellValue(donor.trim().toUpperCase(Locale.ROOT));
        values.createCell(2).setCellValue(date.toString());
        values.createCell(3).setCellValue(iteration);
        workbook.setSheetVisibility(workbook.getSheetIndex(metadata), SheetVisibility.VERY_HIDDEN);
        return iteration;
    }

    private static void decorate(Sheet sheet, Workbook workbook, Map<String, CancellationPlanner.Decision> decisions,
                                 Map<String, Double> yields) throws IOException {
        String cellType = "";
        int actionColumn = -1;
        Map<String, CellStyle> styles = new LinkedHashMap<>();
        CellStyle reasonStyle = workbook.createCellStyle();
        reasonStyle.setWrapText(true);
        DataFormatter formatter = new DataFormatter(Locale.US);
        for (Row row : sheet) {
            String first = formatter.formatCellValue(row.getCell(0));
            if (first.equals("Rank")) {
                actionColumn = row.getLastCellNum();
                String[] headers = {"Decision", "Planned Allocation", "Decision Reason", "Requester Comment"};
                for (int index = 0; index < headers.length; index++) {
                    Cell cell = row.createCell(actionColumn + index);
                    cell.setCellValue(headers[index]);
                    cell.setCellStyle(row.getCell(0).getCellStyle());
                }
                continue;
            }
            if (row.getLastCellNum() == 1) {
                cellType = first;
                continue;
            }
            if (actionColumn < 0 || row.getCell(0) == null || row.getCell(0).getCellType() != CellType.NUMERIC
                    || row.getCell(1) == null || row.getCell(1).getCellType() != CellType.NUMERIC) {
                continue;
            }
            int requestId = (int) row.getCell(1).getNumericCellValue();
            CancellationPlanner.Decision decision = decisions.get(key(requestId, formatter.formatCellValue(row.getCell(2)), cellType));
            if (decision == null) {
                throw new IOException("Priority workbook no longer matches the current plan: #" + requestId + " " + cellType
                        + ". Reopen the donor folder and recalculate.");
            }
            boolean entered = yields.containsKey(cellType);
            boolean cancel = entered && decision.action().equals("CANCEL")
                    && decision.order().status() == CancellationPlanner.Status.PENDING && !decision.order().protectedOrder();
            HICData request = decision.order().request();
            if (!formatter.formatCellValue(row.getCell(3)).equals(request.getRequestDate().toString())
                    || row.getCell(4) == null || row.getCell(4).getCellType() != CellType.NUMERIC
                    || row.getCell(5) == null || row.getCell(5).getCellType() != CellType.NUMERIC
                    || Math.abs(row.getCell(4).getNumericCellValue() - request.getMaxRequest()) > 0.000001
                    || Math.abs(row.getCell(5).getNumericCellValue() - request.getMinRequest()) > 0.000001) {
                throw new IOException("Priority workbook quantities or date changed for #" + requestId + ". Reopen the donor folder before recalculating.");
            }
            String action = entered ? cancel ? "SHOULD CANCEL" : decision.action()
                    : decision.order().status() != CancellationPlanner.Status.PENDING ? "RECORDED " + decision.order().status()
                    : decision.action().equals("CANCEL") ? "LINKED — NO YIELD ENTERED" : "NO YIELD ENTERED";
            row.createCell(actionColumn).setCellValue(action);
            if (entered || decision.order().status() == CancellationPlanner.Status.FULFILLED) {
                row.createCell(actionColumn + 1).setCellValue(decision.allocation());
            }
            row.createCell(actionColumn + 2).setCellValue(entered ? decision.reason()
                    : "Not included in the cancellation list: yield was not entered. " + decision.reason());
            row.getCell(actionColumn + 2).setCellStyle(reasonStyle);
            row.createCell(actionColumn + 3).setCellValue(request.getRequesterComment());
            row.getCell(actionColumn + 3).setCellStyle(reasonStyle);
            row.setHeightInPoints(45);
            if (cancel || (entered && decision.action().equals("REVIEW"))) {
                short color = cancel ? IndexedColors.ROSE.getIndex() : IndexedColors.LIGHT_YELLOW.getIndex();
                for (Cell cell : row) {
                    String styleKey = cell.getCellStyle().getIndex() + ":" + color;
                    CellStyle style = styles.computeIfAbsent(styleKey, ignored -> {
                        CellStyle created = workbook.createCellStyle();
                        created.cloneStyleFrom(cell.getCellStyle());
                        created.setFillForegroundColor(color);
                        created.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        return created;
                    });
                    cell.setCellStyle(style);
                }
            }
        }
        if (actionColumn >= 0) {
            sheet.setColumnWidth(actionColumn, 30 * 256);
            sheet.setColumnWidth(actionColumn + 1, 20 * 256);
            sheet.setColumnWidth(actionColumn + 2, 65 * 256);
            sheet.setColumnWidth(actionColumn + 3, 65 * 256);
        }
    }

    private static void writeSummary(Workbook workbook, String donor, LocalDate date,
                                      CancellationPlanner.Plan plan, Map<String, Double> yields) {
        Sheet summary = workbook.createSheet("Cancellation Summary");
        workbook.setSheetOrder(summary.getSheetName(), 0);
        workbook.setActiveSheet(0);
        for (Sheet sheet : workbook) sheet.setSelected(false);
        summary.setSelected(true);
        summary.createRow(0).createCell(0).setCellValue("Recommended cancellations — donor " + donor + " — " + date);
        summary.createRow(1).createCell(0).setCellValue("Generated: " + LocalDateTime.now() + ". Recommendations only; no website cancellations were submitted.");
        summary.createRow(2).createCell(0).setCellValue("Pink = SHOULD CANCEL. Check the Decision column for REVIEW; original priority highlights are preserved. Cancellation list includes entered yields only.");
        CellStyle wrap = workbook.createCellStyle();
        wrap.setWrapText(true);
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle titleStyle = workbook.createCellStyle();
        titleStyle.cloneStyleFrom(wrap);
        titleStyle.setFont(titleFont);
        for (int row = 0; row < 3; row++) {
            summary.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(row, row, 0, 6));
            summary.getRow(row).getCell(0).setCellStyle(row == 0 ? titleStyle : wrap);
            summary.getRow(row).setHeightInPoints(32);
        }
        int index = 4;
        CellStyle warningStyle = workbook.createCellStyle();
        warningStyle.cloneStyleFrom(wrap);
        warningStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        warningStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        for (String issue : plan.issues()) {
            Row row = summary.createRow(index++);
            row.createCell(0).setCellValue("UNRESOLVED — " + issue);
            row.getCell(0).setCellStyle(warningStyle);
            row.setHeightInPoints(36);
            summary.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 6));
        }
        for (var entry : new java.util.TreeMap<>(yields).entrySet()) {
            Row row = summary.createRow(index++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue("Yield: " + CancellationPlanner.format(entry.getValue()));
            row.createCell(2).setCellValue("Remaining: " + CancellationPlanner.format(plan.remaining().getOrDefault(entry.getKey(), 0.0)));
        }
        Row header = summary.createRow(++index);
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        String[] headers = {"Cell Type", "Order #", "Requester", "Minimum", "Maximum", "Reason", "Requester Comment"};
        for (int column = 0; column < headers.length; column++) {
            header.createCell(column).setCellValue(headers[column]);
            header.getCell(column).setCellStyle(headerStyle);
        }
        List<CancellationPlanner.Decision> cancellations = cancellations(plan, yields);
        CellStyle cancelStyle = workbook.createCellStyle();
        cancelStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        cancelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cancelStyle.setWrapText(true);
        for (CancellationPlanner.Decision decision : cancellations) {
            HICData request = decision.order().request();
            Row row = summary.createRow(++index);
            row.setHeightInPoints(45);
            row.createCell(0).setCellValue(request.getCellType());
            row.createCell(1).setCellValue(request.getOrderNumber());
            row.createCell(2).setCellValue(request.getName());
            row.createCell(3).setCellValue(request.getMinRequest());
            row.createCell(4).setCellValue(request.getMaxRequest());
            row.createCell(5).setCellValue(decision.reason());
            row.createCell(6).setCellValue(request.getRequesterComment());
            for (Cell cell : row) cell.setCellStyle(cancelStyle);
        }
        if (cancellations.isEmpty()) summary.createRow(++index).createCell(0).setCellValue("No cancellations recommended for the entered cell types.");
        summary.createRow(index + 2).createCell(0).setCellValue("Linked orders without entered yields are not cancellation instructions; review their dependency notes in the priority tabs / detailed report.");
        summary.createFreezePane(0, header.getRowNum() + 1);
        for (int column = 0; column < 7; column++) summary.setColumnWidth(column, (column >= 5 ? 65 : 25) * 256);
    }

    private static String key(int requestId, String name, String cellType) {
        return requestId + "|" + name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT) + "|" + cellType;
    }
}
