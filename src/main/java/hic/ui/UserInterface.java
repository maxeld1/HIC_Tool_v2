package hic.ui;//package ui;

import hic.datamanagement.FileReader;
import hic.logging.HICExcelLogger;
import hic.processor.Processor;
import hic.util.HICData;

import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class UserInterface {

    private FileReader fileReader;
    private Processor processor;
    HICExcelLogger hicExcelLogger;

    public UserInterface() {
        fileReader = FileReader.getInstance();
        processor = new Processor(fileReader);
        hicExcelLogger = HICExcelLogger.getInstance();
    }

    public void mainMenu(List<HICData> hicData, String donor) {

        Scanner scanner = new Scanner(System.in);
        boolean quit = false;

        while (!quit) {
            printMainMenu();
            int choice = scanner.nextInt();

            switch (choice) {
                case 0:
                    exitProgram();
                    quit = true;
                    break;
                case 1:
                    getHICSummary(hicData);
                    break;
                case 2:
                    exportToExcelUnsorted(hicData);
                    break;
                case 3:
                    exportToExcelSorted(hicData);
                    break;
                case 4:
                    makeLabels(hicData, donor);
                    break;
                case 5:
                    exportToSignOutSheet(hicData, donor);
                case 6:
                    performAllActions(hicData, donor);
                    break;
                default:
                    System.out.println("Invalid selection. Please try again.\n");
            }
        }



    }


    private void printMainMenu() {
        System.out.println("\n=======================");
        System.out.println("      HIC Menu: ");
        System.out.println("=======================\n");
        System.out.println("0. Exit the program");
        System.out.println("1. Get HIC Summary and Calculate Apheresis");
        System.out.println("2. Export unsorted HIC data to Excel file");
        System.out.println("3. Export sorted HIC data to Excel file");
        System.out.println("4. MAKE LABELS");
        System.out.println("5. Export to Sign Out Sheet");
        System.out.println("6. ALL OF THE ABOVE (Will run everything)");
    }


    private void exitProgram() {
        System.out.println("Exiting the program...");
        System.exit(0);
    }


    private void getHICSummary(List<HICData> hicData) {

        List<Double> maxAndMinRequests = processor.printHICSummary(hicData); //get the max and min requests

        processor.calculateApheresisNeeded(maxAndMinRequests); //calculate the amount of apheresis needed
    }



    private void exportToExcelUnsorted(List<HICData> hicData) {

        // Export HIC data to excel sheet (unsorted)
        hicExcelLogger.logHICData(hicData, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Exports\\Unsorted_HICdoc.xlsx", false);

    }


    private void exportToExcelSorted(List<HICData> hicData) {


        // Sort the hicData by cell type and date/time
        processor.sortByCellTypeAndDateTime(hicData);

        // Export the sorted HIC data to excel sheet
        hicExcelLogger.logHICData(hicData, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Exports\\Sorted_Labeled_HICDoc.xlsx", true);

    }


    private void makeLabels(List<HICData> hicData, String donor) {

        processor.sortByCellTypeAndDateTime(hicData); //sort the hicData

        // Get the CD4 and CD8 cell types in a separate list
        List<HICData> cd4Cd8List = processor.getCD4CD8CellRecords(hicData);

        // Get the other cell types in a separate list
        List<HICData> otherCellTypesList = processor.getOtherCellTypeRecords(hicData);

        // Export HIC data to labels word doc
        hicExcelLogger.exportToWord(cd4Cd8List, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC_Program_Label_Template2.docx", "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Exports\\CD4CD8_Labels.docx", donor);


        // Export HIC data to labels word doc
        hicExcelLogger.exportToWord(otherCellTypesList, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC_Program_Label_Template2.docx", "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Exports\\OTHERCellTypes_Labels.docx", donor);



    }

    private void exportToSignOutSheet(List<HICData> hicData, String donor) {

        hicExcelLogger.exportToSignOutSheet(hicData, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Sign-out Sheet Template.xlsx", "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Exports\\HIC Sign-out Sheet.xlsx", donor);
    }

    private void performAllActions(List<HICData> hicData, String donor) {

        //System.out.println(hicData); //print out HIC data records

        List<Double> maxAndMinRequests = processor.printHICSummary(hicData); //get the max and min requests

        System.out.println();

        processor.calculateApheresisNeeded(maxAndMinRequests); //calculate the amount of apheresis needed

        System.out.println();

        // Export HIC data to excel sheet (unsorted)
        hicExcelLogger.logHICData(hicData, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Exports\\Unsorted_HICdoc.xlsx", false);

        // Sort the hicData by cell type and date/time
        processor.sortByCellTypeAndDateTime(hicData);

        // Get the CD4 and CD8 cell types in a separate list
        List<HICData> cd4Cd8List = processor.getCD4CD8CellRecords(hicData);

        // Get the other cell types in a separate list
        List<HICData> otherCellTypesList = processor.getOtherCellTypeRecords(hicData);

        // Export the sorted HIC data to excel sheet
        hicExcelLogger.logHICData(hicData, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Exports\\Sorted_Labeled_HICDoc.xlsx", true);

        // Export HIC data to labels word doc
        hicExcelLogger.exportToWord(cd4Cd8List, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC_Program_Label_Template2.docx", "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Exports\\CD4CD8_Labels.docx", donor);


        // Export HIC data to labels word doc
        hicExcelLogger.exportToWord(otherCellTypesList, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC_Program_Label_Template2.docx", "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Exports\\OTHERCellTypes_Labels.docx", donor);

        hicExcelLogger.exportToSignOutSheet(hicData, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Sign-out Sheet Template.xlsx", "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Exports\\HIC Sign-out Sheet.xlsx", donor);
    }














}
