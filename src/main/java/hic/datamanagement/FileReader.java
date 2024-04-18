package hic.datamanagement;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

public class FileReader {

    private static FileReader instance = new FileReader();

    public static FileReader getInstance() {
        return instance;
    }

    /**
     * This method will read in a file when given the file name
     * @param fileName name of the file
     * @return all lines from the file
     * @throws IOException e
     */
    public String readFile(String fileName) throws IOException {

        StringBuilder lines = new StringBuilder(); //where lines of the file will be stored

        // Try to read in a file using buffered reader and file reader
        try (BufferedReader bufferedReader = new BufferedReader(new java.io.FileReader(fileName))) {
            String line; //variable to hold each separate line

            // While the line is not null
            while ((line = bufferedReader.readLine()) != null) {
                lines.append(line).append("\n"); //append the line to lines string builder along with newline
            }
        }
        return lines.toString();
    }


    /**
     * Parses the given file
     * @param fileName name of the file
     */
    public <E> List<E> parseFile(String fileName) {
        FileParser fileParser = null;

        // If the file name ends with .txt, proceed by creating new TXTFileParser
        // If the filename ends with .json, proceed by creating new JSONFileParser
        if (fileName.endsWith(".txt")) {
            fileParser = new TXTFileParser();
        } else if (fileName.endsWith(".TXT")) {
            fileParser = new TXTFileParser();
        } else {
            System.out.println("Unknown file extension detected.");
            return null;
        }

        try {
            String lines = readFile(fileName); //read all the lines from the file

            // If the fileparser is not null
            if (fileParser != null) {
                fileParser.parse(lines); //parse the lines of the file based on "data" parameter
                //System.out.println(lines);

                if (fileParser instanceof TXTFileParser) {
                    return (List<E>) ((TXTFileParser) fileParser).getHICData();
                }

            }
        } catch (IOException e) {
            System.err.println("An error has occurred when reading file: " + e.getMessage());
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        System.out.println();
        return null;
    }
}
