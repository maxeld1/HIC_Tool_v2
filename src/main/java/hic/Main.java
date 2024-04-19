package hic;

import hic.datamanagement.FileReader;
import hic.logging.HICExcelLogger;
import hic.processor.Processor;
import hic.ui.UserInterface;
import hic.util.HICData;

import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/**
 * HIC Program for set up
 * @Author: Max Eldabbas
 */
public class Main {
    public static void main(String[] args) {

        UserInterface userInterface = new UserInterface();
        Scanner scanner = new Scanner(System.in);

        System.out.println("Please enter the donor number for today: ");

        String donor = scanner.nextLine(); //get user input for donor

        HICExcelLogger hicExcelLogger = HICExcelLogger.getInstance(); //get instance of HICExcelLogger
        FileReader fileReader = FileReader.getInstance(); //get instance of FileReader
        Processor processor = new Processor(fileReader); //initialize the processor

        List<HICData> hicData =  fileReader.parseFile("HIC_FILE.txt"); //parse the HIC file

        hicExcelLogger.exportToSignOutSheet(hicData, "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Sign-out Sheet Template.xlsx", "C:\\Users\\maxeld\\IdeaProjects\\HIC_Tool_v2\\HIC Exports\\HIC Sign-out Sheet.xlsx", donor);

        //userInterface.mainMenu(hicData, donor); //start the main menu





    }
}