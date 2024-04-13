package hic.logging;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class ExcelFileOpener {
    public static void main(String[] args) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue("Hello, world!");

            // Write the workbook to a ByteArrayOutputStream
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            // Open the Excel file using the default application
            openExcelFile(outputStream.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void openExcelFile(byte[] excelData) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(excelData);
            Desktop.getDesktop().open(new File("temp.xlsx")); // Save to a temporary file
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
