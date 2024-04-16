import hic.datamanagement.FileReader;
import hic.logging.HICExcelLogger;
import hic.processor.Processor;
import hic.util.HICData;

import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("Please enter the donor number for today: ");

        String donor = scanner.nextLine();

        HICExcelLogger hicExcelLogger = HICExcelLogger.getInstance(); //get instance of HICExcelLogger
        FileReader fileReader = FileReader.getInstance(); //get instance of FileReader
        Processor processor = new Processor(fileReader); //initialize the processor

        List<HICData> hicData =  fileReader.parseFile("sample_hic_file.txt"); //parse the HIC file

        //System.out.println(hicData); //print out HIC data records

        List<Double> maxAndMinRequests = processor.printHICSummary(hicData); //get the max and min requests

        System.out.println();

        processor.calculateApheresisNeeded(maxAndMinRequests); //calculate the amount of apheresis needed

        System.out.println();

        // Export HIC data to excel sheet (unsorted)
        //hicExcelLogger.logHICData(hicData, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HICdoc.xlsx", false);

        // Sort the hicData by cell type and date/time
        processor.sortByCellTypeAndDateTime(hicData);

        List<HICData> cd4Cd8List = processor.getCD4CD8CellRecords(hicData);

        List<HICData> otherCellTypesList = processor.getOtherCellTypeRecords(hicData);

        // Export the sorted HIC data to excel sheet
        //hicExcelLogger.logHICData(hicData, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HICDoc2.xlsx", true);

        // Export HIC data to labels word doc
        hicExcelLogger.exportToWord(cd4Cd8List, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC_Program_Label_Template2.docx", "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\CD4CD8_Labels.docx", donor);

        // Export HIC data to labels word doc
        hicExcelLogger.exportToWord(otherCellTypesList, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC_Program_Label_Template2.docx", "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\OTHERCellTypes_Labels.docx", donor);
    }
}