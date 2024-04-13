import hic.datamanagement.FileReader;
import hic.logging.HICExcelLogger;
import hic.processor.Processor;
import hic.util.HICData;

import java.sql.SQLOutput;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        HICExcelLogger hicExcelLogger = HICExcelLogger.getInstance();
        FileReader fileReader = FileReader.getInstance();
        Processor processor = new Processor(fileReader);

        List<HICData> hicData =  fileReader.parseFile("sample_hic_file.txt");

        //System.out.println(hicData);

        processor.printSummary(hicData);

        System.out.println();

        hicExcelLogger.logHICData(hicData, "C:\\Users\\maxel\\OneDrive\\Documents\\HICdoc.xlsx");

        processor.sortByCellTypeAndDateTime(hicData);

        //hicExcelLogger.logHICData(hicData, "C:\\Users\\maxel\\OneDrive\\Documents\\HICdoc2.xlsx");


    }
}