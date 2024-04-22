package hic.ui;//package ui;

import hic.Main;
import hic.datamanagement.FileReader;
import hic.logging.HICExcelLogger;
import hic.processor.Processor;
import hic.util.HICData;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.InputMismatchException;
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
            try {
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
                        break;
                    case 6:
                        performAllActions(hicData, donor);
                        break;
                    default:
                        System.out.println("Invalid selection. Please try again.\n");
                }
            } catch (InputMismatchException e) {
                System.out.println("Invalid input. Please enter a number.\n");
                // Clear scanner buffer
                scanner.nextLine();
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
        System.out.print("> ");
        System.out.flush();
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

        // Get the directory where the JAR file is located
        String jarDir = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();

        // Construct the path to the .txt file based on the JAR directory
        String hicTxtFilePath = jarDir + File.separator + "Output Files\\UnsortedHICList.xlsx";

        // Decode the file path
        hicTxtFilePath = URLDecoder.decode(hicTxtFilePath, StandardCharsets.UTF_8);

        // Export HIC data to excel sheet (unsorted)
        hicExcelLogger.logHICData(hicData, hicTxtFilePath, false);

    }


    private void exportToExcelSorted(List<HICData> hicData) {

        // Get the directory where the JAR file is located
        String jarDir = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();

        // Construct the path to the .txt file based on the JAR directory
        String hicTxtFilePath = jarDir + File.separator + "Output Files\\SortedHICList.xlsx";

        // Decode the file path
        hicTxtFilePath = URLDecoder.decode(hicTxtFilePath, StandardCharsets.UTF_8);


        // Sort the hicData by cell type and date/time
        processor.sortByCellTypeAndDateTime(hicData);

        // Export the sorted HIC data to excel sheet
        hicExcelLogger.logHICData(hicData, hicTxtFilePath, true);

    }


    private void makeLabels(List<HICData> hicData, String donor) {

        // Get the directory where the JAR file is located
        String jarDir = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();

        // Construct the path to the hic label template file based on the JAR directory
        String hicLabelTemplate = jarDir + File.separator + "Templates\\HIC_Program_Label_Template2.docx";

        String hicLabelCD4CD8 = jarDir + File.separator + "Output Files\\CD4CD8_Labels.docx";

        String hicLabelOthers = jarDir + File.separator + "Output Files\\OTHERCellTypes_Labels.docx";

        // Decode the file path
        hicLabelTemplate = URLDecoder.decode(hicLabelTemplate, StandardCharsets.UTF_8);
        hicLabelCD4CD8 = URLDecoder.decode(hicLabelCD4CD8, StandardCharsets.UTF_8);
        hicLabelOthers = URLDecoder.decode(hicLabelOthers, StandardCharsets.UTF_8);


        processor.sortByCellTypeAndDateTime(hicData); //sort the hicData

        // Get the CD4 and CD8 cell types in a separate list
        List<HICData> cd4Cd8List = processor.getCD4CD8CellRecords(hicData);

        // Get the other cell types in a separate list
        List<HICData> otherCellTypesList = processor.getOtherCellTypeRecords(hicData);

        // Export HIC data to labels word doc
        hicExcelLogger.exportToWord(cd4Cd8List, hicLabelTemplate, hicLabelCD4CD8, donor);


        // Export HIC data to labels word doc
        hicExcelLogger.exportToWord(otherCellTypesList, hicLabelTemplate, hicLabelOthers, donor);



    }

    private void exportToSignOutSheet(List<HICData> hicData, String donor) {

        // Get the directory where the JAR file is located
        String jarDir = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();

        // Construct the path to the hic label template file based on the JAR directory
        String hicSignOutTemplate = jarDir + File.separator + "Templates\\HIC Sign-out Sheet Template.xlsx";

        String hicSignOut = jarDir + File.separator + "Output Files\\HIC Sign-out Sheet.xlsx";

        hicSignOutTemplate = URLDecoder.decode(hicSignOutTemplate, StandardCharsets.UTF_8);
        hicSignOut = URLDecoder.decode(hicSignOut, StandardCharsets.UTF_8);

        hicExcelLogger.exportToSignOutSheet(hicData, hicSignOutTemplate, hicSignOut, donor);
    }

    private void performAllActions(List<HICData> hicData, String donor) {

        getHICSummary(hicData);

        exportToExcelUnsorted(hicData);

        exportToExcelSorted(hicData);

        makeLabels(hicData, donor);

        exportToSignOutSheet(hicData, donor);


//        //System.out.println(hicData); //print out HIC data records
//
//        List<Double> maxAndMinRequests = processor.printHICSummary(hicData); //get the max and min requests
//
//        System.out.println();
//
//        processor.calculateApheresisNeeded(maxAndMinRequests); //calculate the amount of apheresis needed
//
//        System.out.println();
//
//        // Export HIC data to excel sheet (unsorted)
//        hicExcelLogger.logHICData(hicData, "C:\\HIC Program\\Output Files\\UnsortedHICList.xlsx", false);
//
//        // Sort the hicData by cell type and date/time
//        processor.sortByCellTypeAndDateTime(hicData);
//
//        // Get the CD4 and CD8 cell types in a separate list
//        List<HICData> cd4Cd8List = processor.getCD4CD8CellRecords(hicData);
//
//        // Get the other cell types in a separate list
//        List<HICData> otherCellTypesList = processor.getOtherCellTypeRecords(hicData);
//
//        // Export the sorted HIC data to excel sheet
//        hicExcelLogger.logHICData(hicData, "C:\\HIC Program\\Output Files\\SortedHICList.xlsx", true);
//
//        // Export HIC data to labels word doc
//        hicExcelLogger.exportToWord(cd4Cd8List, "C:\\HIC Program\\Templates\\HIC_Program_Label_Template2.docx", "C:\\HIC Program\\Output Files\\CD4CD8_Labels.docx", donor);
//
//
//        // Export HIC data to labels word doc
//        hicExcelLogger.exportToWord(otherCellTypesList, "C:\\HIC Program\\Templates\\HIC_Program_Label_Template2.docx", "C:\\HIC Program\\Output Files\\OTHERCellTypes_Labels.docx", donor);
//
//        hicExcelLogger.exportToSignOutSheet(hicData, "C:\\HIC Program\\Templates\\HIC Sign-out Sheet Template.xlsx", "C:\\HIC Program\\Output Files\\HIC Sign-out Sheet.xlsx", donor);
    }














}
