package hic.logging;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import hic.util.HICData;
import org.apache.poi.xwpf.usermodel.*;

import java.awt.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    /**
     * Method to export hic data into labels
     *
     * @param hicData          as input
     * @param wordTemplatePath to duplicate and write to
     * @param wordFilePath     to export to
     */
    public void exportToWord(List<HICData> hicData, String wordTemplatePath, String wordFilePath, String donor) {
        try {
            // Open the Word document template
            try (XWPFDocument doc = new XWPFDocument(new FileInputStream(wordTemplatePath))) {
                String currentCellType = ""; // Initialize currentCellType
                int dataIndex = 0; // Initialize dataIndex to track the index of hicData

                // Iterate over the document tables
                for (XWPFTable table : doc.getTables()) {
                    List<XWPFTableRow> rows = table.getRows();

                    // Iterate over each row
                    for (XWPFTableRow row : rows) {
                        List<XWPFTableCell> cells = row.getTableCells();

                        // Filter out empty cells
                        List<XWPFTableCell> nonEmptyCells = cells.stream()
                                .filter(cell -> !cell.getText().trim().isEmpty())
                                .toList();

                        // Iterate over each non-empty cell
                        for (XWPFTableCell cell : nonEmptyCells) {
                            // Check if the dataIndex is within bounds
                            if (dataIndex < hicData.size()) {
                                // Check if the cell type has changed
                                if (!Objects.equals(hicData.get(dataIndex).getCellType(), currentCellType)) {
                                    // Print the cell type label in the current cell
                                    replaceMergeFieldsWithLabel(cell, hicData.get(dataIndex));
                                    currentCellType = hicData.get(dataIndex).getCellType();
                                } else {
                                    // Replace merge fields in the current cell with hicData
                                    replaceMergeFields(cell, hicData.get(dataIndex), donor);
                                    dataIndex++; // Increment dataIndex after processing each cell
                                }
                            } else {
                                // If all data has been processed, exit the loop
                                break;
                            }
                        }
                    }
                }

                // Save the populated document
                try (FileOutputStream out = new FileOutputStream(wordFilePath)) {
                    doc.write(out);
                    System.out.println("\nHICData logged to labels Word document successfully.");
                } catch (IOException e) {
                    System.err.println("\nError saving the Word file: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                if (text != null && !text.isEmpty()) {
                    text = text.replace("<<ID>>", data.getCellType());
                    text = text.replace("<<Order>>", "");
                    text = text.replace("<<Name>>", "");
                    text = text.replace("<<Max>>", "");
                    text = text.replace("<<Min>>", "");
                    text = text.replace("<<Donor>>", "");
                    text = text.replace("<<Date>>", "");

                    run.setText(text, 0);
                }
            }
        }
    }






}


