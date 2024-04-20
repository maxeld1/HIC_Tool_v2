package hic;

import hic.datamanagement.FileReader;
import hic.logging.HICExcelLogger;
import hic.processor.Processor;
import hic.ui.UserInterface;
import hic.util.HICData;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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

        // Get the directory where the JAR file is located
        String jarDir = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();

        // Construct the path to the .txt file based on the JAR directory
        String hicTxtFilePath = jarDir + File.separator + "HIC_FILE.txt";

        // Decode the file path
        hicTxtFilePath = URLDecoder.decode(hicTxtFilePath, StandardCharsets.UTF_8);

        List<HICData> hicData =  fileReader.parseFile(hicTxtFilePath); //parse the HIC file

        userInterface.mainMenu(hicData, donor); //start the main menu



    }
}