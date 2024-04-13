package hic.logging;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Logger {

    private static final String DEFAULT_FILE = "log.txt"; //will vary based on what the file name is
    private static Logger instance;
    private String logFileTitle;
    private PrintWriter printWriter;

    public Logger() {
        this.logFileTitle = DEFAULT_FILE;
        try {
            printWriter = new PrintWriter(new FileWriter(DEFAULT_FILE, true));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Get an instance of the Logger clas
     * @return the instance
     */
    public static Logger getInstance() {
        if (instance == null) {
            instance = new Logger();
        }
        return instance;
    }

    /**
     * Method to log an event
     * @param event to log
     */
    public void logEvent(String event) {
        if (printWriter != null) {
            printWriter.println(event); //print the event to the log file
            printWriter.flush(); //flush the data
        } else {
            System.err.println("PrintWriter has not been set up properly.");
        }

    }

    /**
     * Method used to change/set an output destination
     * @param logFileTitle the name of the log file
     */
    public void changeOutputDestination(String logFileTitle) {
        closeFile(); //close out the current file
        this.logFileTitle = logFileTitle; //the new file name is the one specified in the argument

        try {
            // Initialize the PrintWriter with the new log file
            printWriter = new PrintWriter(new FileWriter(logFileTitle, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //System.out.println("Output destination successfully set to: " + logFileTitle);
        //System.out.println();
    }

    /**
     * Method used to close a given file
     */
    private void closeFile() {
        if (printWriter != null) {
            printWriter.close();
        }
    }


}
