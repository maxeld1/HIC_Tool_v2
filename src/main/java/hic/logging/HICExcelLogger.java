package hic.logging;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import hic.datamanagement.FileReader;
import hic.hiccell.FulfillmentStats;
import hic.processor.HICDataNotFoundException;
import hic.processor.Processor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import hic.util.HICData;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;

import java.awt.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;

/**
 * Class to log data to excel files
 * @Author maxeldabbas
 */
public class HICExcelLogger {

    private static HICExcelLogger instance;

    /**
     * Get an instance of the Logger clas
     *
     * @return the instance
     */
    public static HICExcelLogger getInstance() {
        if (instance == null) {
            instance = new HICExcelLogger();
        }
        return instance;
    }

    /**
     * Method to export HIC data to an excel sheet specified
     *
     * @param hicData          as input
     * @param filePath         to export to
     * @param addCellTypeLabel to specify whether the sheet should have cell type labels
     */
    public void logHICData(List<HICData> hicData, String filePath, boolean addCellTypeLabel) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("HICData");
            int rowNum = 0;

            // Create headers
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"ID", "Order #", "Request Date", "Name", "Cell Type", "Max Request", "Min Request"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            String currentCellType = null;

            // Write HICData to rows
            for (HICData data : hicData) {

                // If add cell type label is true...
                if (addCellTypeLabel) {

                    // And if the iterated cell type does not equal the current cell type
                    if (!data.getCellType().equals(currentCellType)) {
                        Row cellTypeLabelRow = sheet.createRow(rowNum++);
                        Cell cellTypeLabelCell = cellTypeLabelRow.createCell(0);
                        cellTypeLabelCell.setCellValue(data.getCellType());
                        currentCellType = data.getCellType(); //set current cell type to be the current iterated cell type
                    }
                }

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(data.getID());
                row.createCell(1).setCellValue(data.getOrderNumber());
                row.createCell(2).setCellValue(data.getRequestDate().toString());
                row.createCell(3).setCellValue(data.getName());
                row.createCell(4).setCellValue(data.getCellType());
                row.createCell(5).setCellValue(data.getMaxRequest());
                row.createCell(6).setCellValue(data.getMinRequest());
            }

            // Write workbook to file
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
                System.out.println("\nHICData logged to Excel file successfully.");
            } catch (IOException e) {
                System.err.println("\nThe file could not be saved to that directory: " + e.getMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void exportCD4CD8RequestList(List<HICData> hicData, String filePath) {
        Map<String, RequesterCellOrders> requesters = new LinkedHashMap<>();

        for (HICData data : hicData) {
            if (!Objects.equals(data.getCellType(), "CD4+") && !Objects.equals(data.getCellType(), "CD8+")) {
                continue;
            }

            String rawRequesterName = data.getName() == null ? "" : data.getName().trim();
            String requesterName = rawRequesterName.isEmpty() ? "Unknown" : rawRequesterName;
            String key = requesterName.toLowerCase();
            RequesterCellOrders requester = requesters.computeIfAbsent(key, ignored -> new RequesterCellOrders(requesterName));
            OrderRequest orderRequest = new OrderRequest(data.getOrderNumber(), data.getMaxRequest(), data.getMinRequest());

            if (Objects.equals(data.getCellType(), "CD4+")) {
                requester.cd4Orders.add(orderRequest);
            } else {
                requester.cd8Orders.add(orderRequest);
            }
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("CD4 CD8 Requests");

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int rowNum = 0;
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("CD4/CD8 Requester List");
            titleCell.setCellStyle(titleStyle);

            rowNum++;
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"Category", "Name", "CD4 Orders (Max/Min)", "CD8 Orders (Max/Min)"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            rowNum = writeRequesterGroupRows(sheet, rowNum, "CD4 & CD8", requesters.values().stream()
                    .filter(RequesterCellOrders::hasBoth)
                    .toList());
            rowNum++;
            rowNum = writeRequesterGroupRows(sheet, rowNum, "Only CD4", requesters.values().stream()
                    .filter(RequesterCellOrders::hasOnlyCd4)
                    .toList());
            rowNum++;
            writeRequesterGroupRows(sheet, rowNum, "Only CD8", requesters.values().stream()
                    .filter(RequesterCellOrders::hasOnlyCd8)
                    .toList());

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
                System.out.println("\nCD4/CD8 requester list exported successfully.");
            } catch (IOException e) {
                System.err.println("\nThe file could not be saved to that directory: " + e.getMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void exportLowYieldPriorityList(List<HICData> hicData, String filePath) {
        exportLowYieldPriorityList(hicData, filePath, null);
    }

    public void exportLowYieldPriorityList(List<HICData> hicData, String filePath, FulfillmentStats fulfillmentStats) {
        List<String> cellTypeOrder = List.of(
                "CD8+", "CD4+", "B Cells", "NK Cells", "Monocytes", "PBMC", "Total T",
                "Unpurified Apheresis", "Top Layer Ficoll", "Bottom Layer Ficoll"
        );

        Map<String, List<HICData>> byCellType = hicData.stream()
                .collect(Collectors.groupingBy(HICData::getCellType, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));

        List<String> orderedCellTypes = new ArrayList<>(byCellType.keySet());
        orderedCellTypes.sort(Comparator
                .comparingInt((String cellType) -> {
                    int index = cellTypeOrder.indexOf(cellType);
                    return index >= 0 ? index : cellTypeOrder.size();
                })
                .thenComparing(Comparator.naturalOrder()));

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle groupStyle = workbook.createCellStyle();
            Font groupFont = workbook.createFont();
            groupFont.setBold(true);
            groupFont.setFontHeightInPoints((short) 12);
            groupStyle.setFont(groupFont);

            String[] headers = {
                    "Rank", "Order #", "Name", "Request Date", "Max", "Min",
                    "3-Week Fulfillment", "Filled this Week?"
            };

            writeLowYieldPrioritySheet(
                    workbook,
                    "CD4 CD8 Requests",
                    "Low Yield Priority - CD4/CD8 Requests",
                    orderedCellTypes.stream()
                            .filter(cellType -> Objects.equals(cellType, "CD4+") || Objects.equals(cellType, "CD8+"))
                            .toList(),
                    byCellType,
                    fulfillmentStats,
                    titleStyle,
                    headerStyle,
                    groupStyle,
                    headers
            );

            writeLowYieldPrioritySheet(
                    workbook,
                    "Other Requests",
                    "Low Yield Priority - Other Requests",
                    orderedCellTypes.stream()
                            .filter(cellType -> !Objects.equals(cellType, "CD4+") && !Objects.equals(cellType, "CD8+"))
                            .toList(),
                    byCellType,
                    fulfillmentStats,
                    titleStyle,
                    headerStyle,
                    groupStyle,
                    headers
            );

            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
                System.out.println("\nLow-yield priority list exported successfully.");
            } catch (IOException e) {
                System.err.println("\nThe file could not be saved to that directory: " + e.getMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeLowYieldPrioritySheet(Workbook workbook, String sheetName, String title,
                                            List<String> orderedCellTypes,
                                            Map<String, List<HICData>> byCellType,
                                            FulfillmentStats fulfillmentStats,
                                            CellStyle titleStyle,
                                            CellStyle headerStyle,
                                            CellStyle groupStyle,
                                            String[] headers) {
        Sheet sheet = workbook.createSheet(sheetName);
        int rowNum = 0;

        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(titleStyle);

        rowNum++;

        for (String cellType : orderedCellTypes) {
            List<HICData> ranked = byCellType.get(cellType).stream()
                    .sorted((left, right) -> compareLowYieldPriority(left, right, fulfillmentStats))
                    .toList();

            Row groupRow = sheet.createRow(rowNum++);
            Cell groupCell = groupRow.createCell(0);
            groupCell.setCellValue(cellType);
            groupCell.setCellStyle(groupStyle);

            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < ranked.size(); i++) {
                HICData data = ranked.get(i);
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(data.getOrderNumber());
                row.createCell(2).setCellValue(data.getName());
                row.createCell(3).setCellValue(data.getRequestDate().toString());
                row.createCell(4).setCellValue(data.getMaxRequest());
                row.createCell(5).setCellValue(data.getMinRequest());
                row.createCell(6).setCellValue(fulfillmentFraction(data, fulfillmentStats));
                row.createCell(7).setCellValue(filledThisWeek(data, fulfillmentStats));
            }

            rowNum++;
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
        sheet.createFreezePane(0, 1);
    }

    private Comparator<HICData> lowYieldPriorityTieBreaker() {
        return Comparator
                .comparing(HICData::getRequestDate)
                .thenComparingInt(HICData::getOrderNumber);
    }

    private int compareLowYieldPriority(HICData left, HICData right, FulfillmentStats fulfillmentStats) {
        double leftRate = fulfillmentRateForPriority(left, fulfillmentStats);
        double rightRate = fulfillmentRateForPriority(right, fulfillmentStats);
        int byRate = Double.compare(leftRate, rightRate);
        if (byRate != 0) {
            return byRate;
        }

        if (leftRate >= 0 && leftRate <= 1 && rightRate >= 0 && rightRate <= 1) {
            if (Double.compare(leftRate, 1.0) == 0) {
                int byLowerVolume = Integer.compare(
                        fulfillmentTotal(left, fulfillmentStats),
                        fulfillmentTotal(right, fulfillmentStats)
                );
                if (byLowerVolume != 0) {
                    return byLowerVolume;
                }
            } else {
                int byMoreCancellations = Integer.compare(
                        fulfillmentCancelled(right, fulfillmentStats),
                        fulfillmentCancelled(left, fulfillmentStats)
                );
                if (byMoreCancellations != 0) {
                    return byMoreCancellations;
                }
            }
        }

        return lowYieldPriorityTieBreaker().compare(left, right);
    }

    private double fulfillmentRateForPriority(HICData data, FulfillmentStats fulfillmentStats) {
        double rate = fulfillmentRate(data, fulfillmentStats);
        return rate < 0 ? 2.0 : rate;
    }

    private double fulfillmentRate(HICData data, FulfillmentStats fulfillmentStats) {
        if (fulfillmentStats == null) {
            return -1.0;
        }
        return fulfillmentStats.fulfillmentRate(data.getName(), data.getCellType());
    }

    private int fulfillmentTotal(HICData data, FulfillmentStats fulfillmentStats) {
        if (fulfillmentStats == null) {
            return 0;
        }
        FulfillmentStats.StatLine line = fulfillmentStats.findLine(data.getName(), data.getCellType());
        return line == null ? 0 : line.fulfilled() + line.cancelled();
    }

    private int fulfillmentCancelled(HICData data, FulfillmentStats fulfillmentStats) {
        if (fulfillmentStats == null) {
            return 0;
        }
        FulfillmentStats.StatLine line = fulfillmentStats.findLine(data.getName(), data.getCellType());
        return line == null ? 0 : line.cancelled();
    }

    private String fulfillmentFraction(HICData data, FulfillmentStats fulfillmentStats) {
        if (fulfillmentStats == null) {
            return "Fulfillment unavailable";
        }
        return fulfillmentStats.fulfillmentFraction(data.getName(), data.getCellType());
    }

    private String filledThisWeek(HICData data, FulfillmentStats fulfillmentStats) {
        if (fulfillmentStats == null) {
            return "N";
        }
        return fulfillmentStats.filledThisWeek(data.getName(), data.getCellType()) ? "Y" : "N";
    }

    /**
     * Method to export hic data into labels
     *
     * @param hicData          as input
     * @param wordTemplatePath to duplicate and write to
     * @param wordFilePath     to export to
     */
    public void exportToWord(List<HICData> hicData, String wordTemplatePath, String wordFilePath, String donor) throws IOException {
        // Open the Word document template
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(wordTemplatePath))) {
            List<CTTbl> templateTables = doc.getTables().stream()
                    .map(table -> (CTTbl) table.getCTTbl().copy())
                    .toList();

            LabelExportState state = new LabelExportState();
            List<XWPFTable> pageTables = new ArrayList<>(doc.getTables());

            while (state.dataIndex < hicData.size()) {
                boolean wroteAnyLabelCell = populateLabelPage(pageTables, hicData, state, donor);
                if (!wroteAnyLabelCell) {
                    throw new IOException("The label template does not contain usable label cells.");
                }

                if (state.dataIndex < hicData.size()) {
                    pageTables = appendTemplatePage(doc, templateTables);
                }
            }

            // Save the populated document
            try (FileOutputStream out = new FileOutputStream(wordFilePath)) {
                doc.write(out);
                System.out.println("\nHICData logged to labels Word document successfully.");
            }
        }
    }

    private boolean populateLabelPage(List<XWPFTable> tables, List<HICData> hicData, LabelExportState state, String donor) {
        boolean wroteAnyLabelCell = false;

        for (XWPFTable table : tables) {
            for (XWPFTableRow row : table.getRows()) {
                List<XWPFTableCell> nonEmptyCells = row.getTableCells().stream()
                        .filter(cell -> !cell.getText().trim().isEmpty())
                        .toList();

                for (XWPFTableCell cell : nonEmptyCells) {
                    if (state.dataIndex >= hicData.size()) {
                        return wroteAnyLabelCell;
                    }

                    HICData data = hicData.get(state.dataIndex);
                    if (!Objects.equals(data.getCellType(), state.currentCellType)) {
                        replaceMergeFieldsWithLabel(cell, data);
                        state.currentCellType = data.getCellType();
                    } else {
                        replaceMergeFields(cell, data, donor);
                        state.dataIndex++;
                    }
                    wroteAnyLabelCell = true;
                }
            }
        }

        return wroteAnyLabelCell;
    }

    private List<XWPFTable> appendTemplatePage(XWPFDocument doc, List<CTTbl> templateTables) {
        XWPFParagraph pageBreak = doc.createParagraph();
        pageBreak.createRun().addBreak(BreakType.PAGE);

        List<XWPFTable> pageTables = new ArrayList<>();
        XmlCursor cursor = pageBreak.getCTP().newCursor();
        cursor.toNextSibling();
        try {
            for (CTTbl templateTable : templateTables) {
                XWPFTable table = doc.insertNewTbl(cursor);
                table.getCTTbl().set((CTTbl) templateTable.copy());
                pageTables.add(new XWPFTable(table.getCTTbl(), doc));
                cursor = table.getCTTbl().newCursor();
                cursor.toNextSibling();
            }
        } finally {
            cursor.dispose();
        }
        return pageTables;
    }


    /**
     * Method to replace merge fields with HIC data
     *
     * @param cell for each record
     * @param data to input
     */
    private void replaceMergeFields(XWPFTableCell cell, HICData data, String donor) {

        LocalDate currentDate = LocalDate.now(); //get the local date

        // Replace merge fields with data
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            for (XWPFRun run : paragraph.getRuns()) {
                String text = run.getText(0);
                if (text != null && !text.isEmpty()) {
                    text = text.replace("<<ID>>", String.valueOf(data.getID()));
                    text = text.replace("<<Order>>", String.valueOf(data.getOrderNumber()));
                    text = text.replace("<<Name>>", String.valueOf(data.getName()));
                    text = text.replace("<<Max>>", String.valueOf((int) data.getMaxRequest()));
                    text = text.replace("<<Min>>", String.valueOf((int) data.getMinRequest()));
                    text = text.replace("<<Donor>>", donor.toUpperCase());
                    text = text.replace("<<Date>>", String.valueOf(currentDate));

                    run.setText(text, 0);
                }
            }
        }
    }

    /**
     * Method to put a label right before the next batch of cell type records
     * @param cell to insert into
     * @param data hicData
     */
    private void replaceMergeFieldsWithLabel(XWPFTableCell cell, HICData data) {

        // Replace merge fields with data
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            for (XWPFRun run : paragraph.getRuns()) {
                String text = run.getText(0);

                // If the text is not null/not empty
                if (text != null && !text.isEmpty()) {
                    text = text.replace("<<ID>>", data.getCellType());
                    text = text.replace("<<Order>>", "");
                    text = text.replace("<<Name>>", "");
                    text = text.replace("<<Max>>", "");
                    text = text.replace("<<Min>>", "");
                    text = text.replace("<<Donor>>", "");
                    text = text.replace("<<Date>>", "");

                    run.setText(text, 0); //set the position to 0
                    run.setBold(true); //set the text to bold
                    run.setFontSize(15); //set the font size

                }
            }
        }
    }


    /**
     * Calculate the font size to fit the text within the available width
     * @param run    XWPFRun object representing the text run
     * @param width  Available width to fit the text
     * @return       Font size that fits the text within the available width
     */
    private int calculateFontSizeToFitText(XWPFRun run, int width) {
        int fontSize = 16;
        String text = run.getText(0);

        // Measure the width of the text at the current font size
        int textWidth = (int) (text.length() * fontSize * 0.5); // Approximation

        // If the text width exceeds the available width, reduce the font size
        while (textWidth > width) {
            fontSize--; // Decrease font size
            textWidth = (int) (text.length() * fontSize * 0.5); // Measure again
        }

        return fontSize;
    }


    /**
     * Exports incubator and deli fridge orders to sign out sheet
     * @param hicData as input
     * @param templatePath template path for sign out sheet
     * @param outputPath path for sign out sheet export
     * @param donor number
     */
    public void exportToSignOutSheet(List<HICData> hicData, String templatePath, String outputPath, String donor) throws HICDataNotFoundException {

        FileReader fileReader = FileReader.getInstance();
        Processor processor = new Processor(fileReader);

        processor.sortByCellTypeAndDateTime(hicData);
        List<HICData> incubatorList = processor.getIncubatorCells(hicData);
        List<HICData> deliFridgeList = processor.getDeliFridgeCells(hicData);

        LocalDate currentDate = LocalDate.now(); //get the local date
        donor = donor.toUpperCase(); //set donor number to upper case

        try (FileInputStream templateStream = new FileInputStream(templatePath);
             Workbook workbook = new XSSFWorkbook(templateStream)) { //create excel workbook

            // Populate the first sheet
            Sheet incubatorSheet = workbook.getSheetAt(0); // Assuming the first sheet
            int startingRowIncubator = 4; // Start from the 5th row

            // Iterate through the incubator list
            for (HICData data : incubatorList) {

                Row dateDonorRow = incubatorSheet.createRow(2); //specify row for date and donor

                //Create Date cell
                dateDonorRow.createCell(0).setCellValue("DATE: " + currentDate.toString());

                // Create donor number cell
                dateDonorRow.createCell(4).setCellValue("Donor #: " + donor);

                Row row = incubatorSheet.createRow(startingRowIncubator++);
                row.createCell(0).setCellValue(data.getID()); //set ID number
                row.createCell(1).setCellValue(data.getOrderNumber()); //set order number
                row.createCell(2).setCellValue(data.getName()); //set the name
            }

            // Populate the second sheet
            Sheet deliFridgeSheet = workbook.getSheetAt(1); // Create a new sheet
            int startingRowDeli = 4; // Start from the 5th row

            // Iterate through the deliFridgeList
            for (HICData data : deliFridgeList) {

                Row dateDonorRow = deliFridgeSheet.createRow(2); //specify row for date and donor

                //Create Date cell
                dateDonorRow.createCell(0).setCellValue("DATE: " + currentDate.toString());

                // Create donor number cell
                dateDonorRow.createCell(4).setCellValue("Donor #: " + donor);

                Row row = deliFridgeSheet.createRow(startingRowDeli++);
                row.createCell(0).setCellValue(data.getID()); //set ID number
                row.createCell(1).setCellValue(data.getOrderNumber()); //set order number
                row.createCell(2).setCellValue(data.getName()); //set the name
            }

            // Add borders and adjust row height for both sheets
            addBordersAndAdjustRowHeight(incubatorSheet);
            addBordersAndAdjustRowHeight(deliFridgeSheet);

            // Write workbook to file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
                workbook.write(fileOut);
                System.out.println("\nHICData exported to SignOutSheet successfully.");
            } catch (IOException e) {
                System.err.println("\nThe file could not be saved to that directory: " + e.getMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addBordersAndAdjustRowHeight(Sheet sheet) {
        // Set borders for columns A to E
        for (int i = 4; i < 80; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                row = sheet.createRow(i);
            }
            for (int j = 0; j < 5; j++) {
                Cell cell = row.getCell(j);
                if (cell == null) {
                    cell = row.createCell(j);
                }
                setBorder(cell);
            }
            sheet.setColumnWidth(0, 4000); // Set width for column A
            sheet.autoSizeColumn(1); // Auto-size column B
            sheet.autoSizeColumn(2); // Auto-size column C
            sheet.setColumnWidth(3, 8000); // Set width for column D
            sheet.setColumnWidth(4, 8000); // Set width for column E
        }

        Font boldFont = sheet.getWorkbook().createFont();
        boldFont.setBold(true);
        boldFont.setFontHeightInPoints((short) 24);

        Font font24 = sheet.getWorkbook().createFont();
        font24.setFontHeight((short) 24);

        CellStyle bold24Font = sheet.getWorkbook().createCellStyle();
        bold24Font.setFont(boldFont);

        Row row2 = sheet.getRow(2);
        row2.setRowStyle(bold24Font);


        // Set row height to 25
        for (int i = 4; i < 80; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                row = sheet.createRow(i); // Create row if it doesn't exist
            }
            row.setHeightInPoints(25); // Set row height to 25 points
        }
    }

    // Method to create centered cell style
    private CellStyle createCenteredStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    // Method to set border for a cell
    private void setBorder(Cell cell) {
        Workbook workbook = cell.getSheet().getWorkbook();
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        cell.setCellStyle(style);
    }

    private int writeRequesterGroupRows(Sheet sheet, int rowNum, String category, List<RequesterCellOrders> requesters) {
        if (requesters.isEmpty()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(category);
            row.createCell(1).setCellValue("None");
            return rowNum;
        }

        for (RequesterCellOrders requester : requesters) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(category);
            row.createCell(1).setCellValue(requester.name);
            row.createCell(2).setCellValue(joinOrderRequests(requester.cd4Orders));
            row.createCell(3).setCellValue(joinOrderRequests(requester.cd8Orders));
        }
        return rowNum;
    }

    private int countRecentlyCancelledRequests(HICData data) {
        String cancellations = data.getRecentlyCancelledRequests();
        if (cancellations == null || cancellations.isBlank() || cancellations.equalsIgnoreCase("N/A")) {
            return 0;
        }
        int count = 0;
        for (String line : cancellations.split("\\R")) {
            if (line.trim().startsWith("#")) {
                count++;
            }
        }
        return count;
    }

    private String formatRecentlyCancelledRequests(String cancellations) {
        if (cancellations == null || cancellations.isBlank()) {
            return "";
        }
        if (cancellations.equalsIgnoreCase("N/A")) {
            return "";
        }
        return cancellations.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.joining("; "));
    }

    private String joinOrderRequests(List<OrderRequest> orderRequests) {
        return orderRequests.stream()
                .map(OrderRequest::toDisplayText)
                .collect(Collectors.joining(", "));
    }

    private static class RequesterCellOrders {
        private final String name;
        private final List<OrderRequest> cd4Orders = new ArrayList<>();
        private final List<OrderRequest> cd8Orders = new ArrayList<>();

        private RequesterCellOrders(String name) {
            this.name = name;
        }

        private boolean hasBoth() {
            return !cd4Orders.isEmpty() && !cd8Orders.isEmpty();
        }

        private boolean hasOnlyCd4() {
            return !cd4Orders.isEmpty() && cd8Orders.isEmpty();
        }

        private boolean hasOnlyCd8() {
            return cd4Orders.isEmpty() && !cd8Orders.isEmpty();
        }
    }

    private static class OrderRequest {
        private final int orderNumber;
        private final double maxRequest;
        private final double minRequest;

        private OrderRequest(int orderNumber, double maxRequest, double minRequest) {
            this.orderNumber = orderNumber;
            this.maxRequest = maxRequest;
            this.minRequest = minRequest;
        }

        private String toDisplayText() {
            return orderNumber + " (Max: " + formatRequest(maxRequest) + ", Min: " + formatRequest(minRequest) + ")";
        }

        private String formatRequest(double request) {
            if (request == Math.rint(request)) {
                return String.valueOf((int) request);
            }
            return String.valueOf(request);
        }
    }

    private static class LabelExportState {
        private int dataIndex = 0;
        private String currentCellType = "";
    }


}



//    public void exportToSignOutSheet(List<HICData> hicData, String templatePath, String outputPath, String donor) {
//
//        FileReader fileReader = FileReader.getInstance();
//        Processor processor = new Processor(fileReader);
//
//        processor.sortByCellTypeAndDateTime(hicData);
//        List<HICData> incubatorList = processor.getIncubatorCells(hicData);
//        List<HICData> deliFridgeList = processor.getDeliFridgeCells(hicData);
//
//        LocalDate currentDate = LocalDate.now(); //get the local date
//        donor = donor.toUpperCase(); //set donor number to upper case
//
//
//        try (FileInputStream templateStream = new FileInputStream(templatePath);
//
//             Workbook workbook = new XSSFWorkbook(templateStream)) { //create excel workbook
//
//            // Populate the first sheet
//            Sheet incubatorSheet = workbook.getSheetAt(0); // Assuming the first sheet
//            int startingRowIncubator = 4; // Start from the 5th row
//
//            // Iterate through the incubator list
//            for (HICData data : incubatorList) {
//
//                Row dateDonorRow = incubatorSheet.createRow(2); //specify row for date and donor
//
//                //Create Date cell
//                dateDonorRow.createCell(0).setCellValue(currentDate.toString());
//
//                // Create donor number cell
//                dateDonorRow.createCell(4).setCellValue(donor);
//
//                Row row = incubatorSheet.createRow(startingRowIncubator++);
//                row.createCell(0).setCellValue(data.getID()); //set ID number
//                row.createCell(1).setCellValue(data.getOrderNumber()); //set order number
//                row.createCell(2).setCellValue(data.getName()); //set the name
//            }
//
//            // Populate the second sheet
//            Sheet deliFridgeSheet = workbook.getSheetAt(1); // Create a new sheet
//            int startingRowDeli = 4; // Start from the 5th row
//
//            // Iterate through the deliFridgeList
//            for (HICData data : deliFridgeList) {
//
//                Row dateDonorRow = deliFridgeSheet.createRow(2); //specify row for date and donor
//
//                //Create Date cell
//                dateDonorRow.createCell(0).setCellValue(currentDate.toString());
//
//                // Create donor number cell
//                dateDonorRow.createCell(4).setCellValue(donor);
//
//                Row row = deliFridgeSheet.createRow(startingRowDeli++);
//                row.createCell(0).setCellValue(data.getID()); //set ID number
//                row.createCell(1).setCellValue(data.getOrderNumber()); //set order number
//                row.createCell(2).setCellValue(data.getName()); //set the name
//            }
//
//            // Write workbook to file
//            try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
//                workbook.write(fileOut);
//                System.out.println("\nHICData exported to SignOutSheet successfully.");
//            } catch (IOException e) {
//                System.err.println("\nThe file could not be saved to that directory: " + e.getMessage());
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
