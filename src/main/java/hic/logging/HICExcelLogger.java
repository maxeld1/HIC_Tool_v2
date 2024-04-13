package hic.logging;

import com.jacob.activeX.ActiveXComponent;
import com.jacob.com.Dispatch;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import hic.util.HICData;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.awt.*;
import java.io.*;
import java.util.List;

public class HICExcelLogger {

    private static HICExcelLogger instance;

    /**
     * Get an instance of the Logger clas
     * @return the instance
     */
    public static HICExcelLogger getInstance() {
        if (instance == null) {
            instance = new HICExcelLogger();
        }
        return instance;
    }

    public void logHICData(List<HICData> hicData, String filePath, boolean addCellTypeLabel) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("HICData");
            int rowNum = 0;

            // Create headers
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"ID" ,"Order #", "Request Date", "Name", "Cell Type", "Max Request", "Min Request"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            String currentCellType = null;

            // Write HICData to rows
            for (HICData data : hicData) {

                if (addCellTypeLabel) {
                    if (!data.getCellType().equals(currentCellType)) {
                        Row cellTypeLabelRow = sheet.createRow(rowNum++);
                        Cell cellTypeLabelCell = cellTypeLabelRow.createCell(0);
                        cellTypeLabelCell.setCellValue(data.getCellType());
                        currentCellType = data.getCellType();
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
                System.out.println("HICData logged to Excel file successfully.");
            } catch (IOException e) {
                System.err.println("The file could not be saved to that directory: " + e.getMessage());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void convertToWordLabels(String excelDocName, String wordDocName) {
        try (FileInputStream excelInputStream = new FileInputStream(new File(excelDocName));
             Workbook workbook = WorkbookFactory.create(excelInputStream);
             FileOutputStream wordOutputStream = new FileOutputStream(new File(wordDocName))) {

            // Get the first sheet of the workbook
            Sheet sheet = workbook.getSheetAt(0);

            // Create a new Word document
            XWPFDocument document = new XWPFDocument();

            // Iterate through the rows of the sheet
            for (Row row : sheet) {
                // Check if the row contains a cell type label
                Cell cell = row.getCell(0);
                if (cell != null && cell.getCellType() == CellType.STRING) {
                    String cellTypeLabel = cell.getStringCellValue();
                    // Insert the cell type label into the Word document
                    XWPFParagraph paragraph = document.createParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setText(cellTypeLabel);
                }
            }

            // Write the Word document to file
            document.write(wordOutputStream);
            System.out.println("Labels inserted into Word document successfully.");

            // Perform Microsoft Word automation for mail merge
            performWordMailMerge(wordDocName);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void performWordMailMerge(String wordDocName) {
        // Initialize Jacob
        ActiveXComponent word = new ActiveXComponent("Word.Application");

        try {
            // Make Word visible
            word.setProperty("Visible", true);

            // Open the Word document
            Dispatch documents = word.getProperty("Documents").toDispatch();
            Dispatch document = Dispatch.call(documents, "Open", wordDocName).toDispatch();

            // Access the MailMerge interface
            Dispatch mailMerge = Dispatch.get(document, "MailMerge").toDispatch();

            // Navigate to the "Mailings" tab
            Dispatch activeWindow = Dispatch.get(word, "ActiveWindow").toDispatch();
            Dispatch tabControl = Dispatch.get(activeWindow, "TabControl").toDispatch();
            Dispatch.call(tabControl, "Select", 5); // Index 5 corresponds to the Mailings tab

            // Start mail merge for labels
            Dispatch.call(mailMerge, "EditDataSource");
            Dispatch.call(mailMerge, "CreateDataSource", wordDocName, "Table1");
            Dispatch.call(mailMerge, "OpenDataSource", wordDocName, "Table1");

            // Select labels option
            Dispatch.call(mailMerge, "ViewMailMergeFieldCodes");
            Dispatch.call(mailMerge, "SetMailMergeDocType", 3); // 3 corresponds to labels
            Dispatch.call(mailMerge, "ViewMergedData");

            // Select Avery US Letter 5167 Return Address Labels
            Dispatch.call(mailMerge, "SetupDialog");

            // Close Word document
            Dispatch.call(document, "Close", false);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Close Word application
            Dispatch.call(word, "Quit", false);
        }
    }

    public static void openExcelFile(byte[] excelData) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(excelData);
            Desktop.getDesktop().open(new File("temp.xlsx")); // Save to a temporary file
        } catch (IOException e) {
            System.err.println("Failed to open Excel file: " + e.getMessage());
        }
    }

}
