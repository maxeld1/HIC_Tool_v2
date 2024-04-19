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

        String donor = scanner.nextLine();

        HICExcelLogger hicExcelLogger = HICExcelLogger.getInstance(); //get instance of HICExcelLogger
        FileReader fileReader = FileReader.getInstance(); //get instance of FileReader
        Processor processor = new Processor(fileReader); //initialize the processor

        List<HICData> hicData =  fileReader.parseFile("HIC_FILE.txt"); //parse the HIC file

        userInterface.mainMenu(hicData, donor);





    }
}