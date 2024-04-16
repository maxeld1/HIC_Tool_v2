package hic.datamanagement;

import hic.util.HICData;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class TXTFileParser implements FileParser {

    private List<HICData> hicData;

    /**
     * Parse the content of a file based on keyword data
     *
     * @param content to parse
     */
    @Override
    public void parse(String content) {

        if (content == null || content.isEmpty()) {
            return;
        }

        hicData = new ArrayList<>();

        String[] lines = content.split("\n"); //split each line based off of the \n newline char

        //System.out.println(Arrays.toString(lines));

        // Specify date and time formatting
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        StringBuilder currentRecord = new StringBuilder(); //create currentRecord stringbuilder

        int IDCounter = 0;

        // Iterate over the lines of the file
        for (String line : lines) {

            // If the line starts with a hashtag, append that line to the current record
            if (line.startsWith("#")) {
                currentRecord.append(" ").append(line);
            } else {

                String[] tokens = line.split("[\t ]+"); //split the line by tab or space

                // If the number of tokens is 9 or more
                if (tokens.length >= 9) {

                    // Get the information by specified index to get HIC data
                    try {

                        // Get the request ID
                        int requestID = Integer.parseInt(tokens[0].trim());
                        IDCounter++;

                        // Get the date and time
                        LocalDateTime requestDate = LocalDateTime.parse(tokens[1] + " " + tokens[2], dateTimeFormatter);

                        // Get the name
                        StringBuilder nameBuilder = new StringBuilder(tokens[3].trim());
                        for (int i = 4; i < tokens.length; i++) {

                            if (tokens[i].contains("CD4") || tokens[i].contains("CD8") || tokens[i].contains("Total")
                                    || tokens[i].contains("Monocytes") || tokens[i].contains("PBMC")
                                    || tokens[i].contains("NK") || tokens[i].contains("B")) {
                                break;
                            }
                            nameBuilder.append(" ").append(tokens[i]);
                        }

                        String name = nameBuilder.toString();

                        // Get the cell type
                        String cellType = null;

                        for (int i = 4; i < tokens.length; i++) {
                            if (tokens[i].contains("CD4") || tokens[i].contains("CD8") || tokens[i].contains("Total")
                                    || tokens[i].contains("Monocytes") || tokens[i].contains("PBMC")
                                    || tokens[i].contains("NK") || tokens[i].contains("B")) {
                                cellType = tokens[i].replaceAll("\"", "").trim();

                                if (cellType.equalsIgnoreCase("Total")) {
                                    cellType = "Total T";
                                }
                            }
                        }
                        //System.out.println(cellType);


                        // Get the max request
                        double maxRequest = 0.0;

                        // Get the minimum request
                        double minRequest = 0.0;

                        // Iterate over the tokens
                        for (int i = 4; i < tokens.length - 1; i++) {

                            // If the token is numeric, make that token the max request
                            if (isNumeric(tokens[i])) {
                                maxRequest = Double.parseDouble(tokens[i].replaceAll("\"", "").trim());
                                i++; //increment i
                            }

                            // If the next token is numeric, make that the minimum request
                            if (isNumeric(tokens[i])) {
                                minRequest = Double.parseDouble(tokens[i].replaceAll("\"", "").trim());

                            }
                        }


                            HICData hicDataItem = new HICData(IDCounter, requestID, requestDate, name, cellType, maxRequest, minRequest); //use constructor to create HIC info
                            hicData.add(hicDataItem); //add hicDataItem to HIC data arraylist
                            //System.out.println(hicDataItem);
                        } catch (DateTimeParseException e) {
                        System.err.println("Error parsing date/time: " + e.getMessage());
                    }
                }
            }
        }
    }


    /**
     * Method to test whether string is numeric or not
     * @param str to test
     * @return true if numeric, false otherwise
     */
    private boolean isNumeric(String str) {
        return str.matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * Method to tokenize a line
     * @param line to tokenize
     * @return list of tokens
     */
    private List<String> tokenizeLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();

        boolean withinNameField = false;

        for (char c : line.toCharArray()) {
            if (Character.isWhitespace(c)) {
                if (withinNameField) {
                    // If whitespace occurs within a name field, treat it as part of the name
                    currentToken.append(c);
                } else {
                    // If whitespace occurs between fields, add the current token to the list
                    if (currentToken.length() > 0) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0); // Reset the current token
                    }
                }
            } else {
                // If a non-whitespace character is encountered, add it to the current token
                currentToken.append(c);

                // Check if the current token is within a name field
                if (!withinNameField && Character.isLetter(c)) {
                    withinNameField = true;
                }
            }
        }

        // Add the last token to the list
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return tokens;
    }




    @Override
    public List<HICData> getHICData() {
        return hicData;
    }
}
