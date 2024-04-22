package hic.datamanagement;

import hic.util.HICData;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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

                String[] token = line.split("[\t ]+"); //split the line by tab or space
                List<String> tokens = new ArrayList<>();

                // Skip the token if its length is 0
                if (token.length == 0) {
                    continue;
                }

//                // If the iteration hits an I or E_A, stop processing past this char/string
//                for (String part : token) {
//
//                    if (Objects.equals(part, "I") || Objects.equals(part, "E_A")) {
//                        break;
//                    }
//                    tokens.add(part); //add everything up to I or E_A into tokens list
//                }

                // If the iteration hits an I or E_A, stop processing past this char/string
                for (int i = 0; i < token.length; i++) {
                    if (i < 5) {
                        tokens.add(token[i]); // add everything from the beginning up to index 6 into tokens list
                    } else {
                        String part = token[i];
                        if (Objects.equals(part, "I") || Objects.equals(part, "E_A")) {
                            break;
                        }
                        tokens.add(part); // add everything starting from index 7 up to I or E_A into tokens list
                    }
                }


                //System.out.println(tokens);

                // If the number of tokens is 6 or more
                if (tokens.size() >= 6) {

                    // Get the information by specified index to get HIC data
                    try {
                        // Get the request ID
                        int requestID = Integer.parseInt(tokens.get(0).trim());
                        IDCounter++;

                        // Get the date and time
                        LocalDateTime requestDate = LocalDateTime.parse(tokens.get(1) + " " + tokens.get(2), dateTimeFormatter);

                        // Get the name
                        StringBuilder nameBuilder = new StringBuilder(tokens.get(3).trim());
                        for (int i = 4; i < tokens.size(); i++) {

                            if (tokens.get(i).contains("CD4") || tokens.get(i).contains("CD8") || tokens.get(i).contains("Total")
                                    || tokens.get(i).contains("Monocytes") || tokens.get(i).equalsIgnoreCase("PBMC")
                                    || tokens.get(i).contains("NK") || tokens.get(i).contains("B") || tokens.get(i).equalsIgnoreCase("Unpurified")) {
                                break;
                            }
                            nameBuilder.append(" ").append(tokens.get(i));
                        }

                        String name = nameBuilder.toString(); //put nameBuilder into string

                        String[] firstMiddleLastName = name.split(" "); //split first middle and last name

                        // If the full name is more than 2 names, only get first and middle/last
                        if (firstMiddleLastName.length > 2) {
                            //System.out.println(Arrays.toString(firstMiddleLastName));
                            name = firstMiddleLastName[0] + " " + firstMiddleLastName[2];
                        }

                        // Get the cell type
                        String cellType = null;

                        //System.out.println(tokens);

                        for (String parts : tokens) {
                            if (parts.equalsIgnoreCase("CD4+") || parts.equalsIgnoreCase("CD8+") || parts.equalsIgnoreCase("Total")
                                    || parts.trim().equalsIgnoreCase("Monocytes") || parts.equalsIgnoreCase("PBMC")
                                    || parts.equalsIgnoreCase("NK") || parts.equalsIgnoreCase("B") || parts.equalsIgnoreCase("Unpurified")) {
                                cellType = parts.trim();

                                if (cellType.equalsIgnoreCase("Total")) {
                                    cellType = "Total T";
                                } else if (cellType.equalsIgnoreCase("B")) {
                                    cellType = "B Cells";
                                } else if (cellType.equalsIgnoreCase("NK")) {
                                    cellType = "NK Cells";
                                } else if (cellType.equalsIgnoreCase("Unpurified")) {
                                    cellType = "Unpurified Apheresis";
                                }
                            }
                        }
                        //System.out.println(cellType);


                        // Get the max request
                        double maxRequest = 0.0;

                        // Get the minimum request
                        double minRequest = 0.0;

                        // Iterate over the tokens
                        for (int i = 4; i < tokens.size() - 1; i++) {

                            // If the token is numeric and the next token is strictly numeric, make the current token the max request and the next token the min request
                            if (isNumeric(tokens.get(i)) && i + 1 < tokens.size() && isNumeric(tokens.get(i + 1))) {
                                maxRequest = Double.parseDouble(tokens.get(i).replaceAll("\"", "").trim());
                                minRequest = Double.parseDouble(tokens.get(i + 1).replaceAll("\"", "").trim());
                                break; // Exit the loop since we found both max and min request
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
