package hic.logging;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import hic.util.HICData;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

    public void logHICData(List<HICData> hicData, String filePath) {
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

            // Write HICData to rows
            for (HICData data : hicData) {
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

    public static void openExcelFile(byte[] excelData) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(excelData);
            Desktop.getDesktop().open(new File("temp.xlsx")); // Save to a temporary file
        } catch (IOException e) {
            System.err.println("Failed to open Excel file: " + e.getMessage());
        }
    }

}
