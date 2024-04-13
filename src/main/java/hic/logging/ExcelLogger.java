package hic.logging;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;

public class ExcelLogger {

    private static final String DEFAULT_FILE = "log.xlsx";
    private static ExcelLogger instance;
    private String logFileName;
    private Workbook workbook;
    private Sheet sheet;
    private int rowNum;

    public ExcelLogger() {
        this.logFileName = DEFAULT_FILE;
        this.rowNum = 0;
        this.workbook = new XSSFWorkbook();
        this.sheet = workbook.createSheet("Log");
    }

    /**
     * Get an instance of the Logger class
     * @return the instance
     */
    public static ExcelLogger getInstance() {
        if (instance == null) {
            instance = new ExcelLogger();
        }
        return instance;
    }



    /**
     * Method to log an event
     * @param events to log
     */
    public void logEvent(String... events) {
        Row row = sheet.createRow(rowNum++);
        int cellNum = 0;

        for (String event : events) {
            Cell cell = row.createCell(cellNum++);
            cell.setCellValue(event);
        }

    }

    /**
     * Method used to change/set an output destination
     * @param logFileName the name of the log file
     */
    public void changeOutputDestination(String logFileName) {
        this.logFileName = logFileName;

    }

    /**
     * Method used to close a given file
     */
    private void closeFile() {
        try {
            workbook.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
