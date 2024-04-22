package hic.datamanagement;

import hic.util.HICData;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class for parsing a .txt file
 * @Author maxeldabbas
 */
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

            int indexI = line.indexOf("I");
            int indexEA = line.indexOf("E_A");

            if (indexI >= 0 || indexEA >= 0) {
                int endIndex = Math.min(indexI >= 0 ? indexI : Integer.MAX_VALUE, indexEA >= 0 ? indexEA : Integer.MAX_VALUE) + 1;
                line = line.substring(0, endIndex);
            }

            // If the line starts with a hashtag, append that line to the current record
            if (line.startsWith("#")) {
                currentRecord.append(" ").append(line);
            } else {

                // Define a regular expression pattern to match fields separated by whitespace
                String regexPattern = "(?<=\\S)\\s+(?=\\S)";

                // Tokenize the line using the regular expression pattern
                //String[] tokens = line.split(regexPattern);
                List<String> tokens = tokenizeLine(line);

                //String[] tokens = line.split("\\s+"); //split the line by tab or space
                System.out.println(tokens);

                // If the number of tokens is 9 or more
                if (tokens.size() >= 2) {

                    // Get the information by specified index to get HIC data
                    try {

                        // Get the request ID
                        String orderNum = tokens.get(0) + tokens.get(1) + tokens.get(2) + tokens.get(3) + tokens.get(4);
                        int requestID = Integer.parseInt(orderNum);
                        IDCounter++;

                        // Get the date and time
                        StringBuilder dateTimeBuilder = new StringBuilder();

                        for (int i = 5; i < 15; i++) {
                            dateTimeBuilder.append(tokens.get(i));
                        }
                        dateTimeBuilder.append(" ");

                        for (int i = 15; i < 23; i++) {
                            dateTimeBuilder.append(tokens.get(i));
                        }

                        String dateTime = dateTimeBuilder.toString();

                        LocalDateTime requestDate = LocalDateTime.parse(dateTime, dateTimeFormatter);


//                        // Get the name
//                        String name = tokens[3].trim();
//                        boolean foundSpecialKeyword = false;
//
//                        // Check for special keywords and build the name
//                        for (int i = 4; i < tokens.length; i++) {
//                            // Check for special keywords
//                            if (tokens[i].matches(".*CD[48]\\+?.*|.*Total.*|.*Monocytes.*|.*PBMC.*|.*NK.*|.*B.*|Unpurified.*")) {
//                                foundSpecialKeyword = true;
//                                break;
//                            }
//                            // Append token to the name
//                            name += " " + tokens[i];
//                        }
//
//                        // If a special keyword is found, adjust the name accordingly
//                        if (foundSpecialKeyword) {
//                            String[] nameParts = name.split("\\s+");
//                            StringBuilder adjustedName = new StringBuilder();
//                            for (String part : nameParts) {
//                                // If a part contains capital letters, consider it as part of the name
//                                if (containsCapitalLetters(part)) {
//                                    adjustedName.append(part).append(" ");
//                                } else {
//                                    break;
//                                }
//                            }
//                            name = adjustedName.toString().trim();
//                        }

                        String tokensAfterDate = tokens.get(23);
                        String[] splitTokensAfterDate = tokensAfterDate.split("(?<=\\S)\\s+(?=\\S)");
                        System.out.println(Arrays.toString(splitTokensAfterDate));
                        StringBuilder nameBuilder = new StringBuilder();

                        for (String parts : splitTokensAfterDate) {
                            if (parts.equalsIgnoreCase("CD4+") || parts.equalsIgnoreCase("CD8+") || parts.equalsIgnoreCase("Total")
                                    || parts.equalsIgnoreCase("Monocytes") || parts.equalsIgnoreCase("PBMC")
                                    || parts.equalsIgnoreCase("NK") || parts.equalsIgnoreCase("B") || parts.equalsIgnoreCase("Unpurified")) {
                                break;
                            }
                            nameBuilder.append(" ").append(parts);
                        }
                        String name = nameBuilder.toString();

//                        // Get the name
//                        StringBuilder nameBuilder = new StringBuilder(tokens.get(23).trim());
//                        for (int i = 23; i < tokens.size(); i++) {
//                            if (tokens.get(i).equalsIgnoreCase("CD4") || tokens.get(i).equalsIgnoreCase("CD8") || tokens.get(i).equalsIgnoreCase("Total")
//                                    || tokens.get(i).equalsIgnoreCase("Monocytes") || tokens.get(i).equalsIgnoreCase("PBMC")
//                                    || tokens.get(i).equalsIgnoreCase("NK") || tokens.get(i).equalsIgnoreCase("B") || tokens.get(i).equalsIgnoreCase("Unpurified")) {
//                                break;
//                            }
//                            nameBuilder.append(tokens.get(i)).append(" ");
//                        }
//
//                        String name = nameBuilder.toString().trim(); //put nameBuilder into string
//
//                        String[] firstMiddleLastName = name.split(" "); //split first middle and last name
//
//                        // If the full name is more than 2 names, only get first and middle/last
//                        if (firstMiddleLastName.length > 2) {
//                            //System.out.println(Arrays.toString(firstMiddleLastName));
//                            name = firstMiddleLastName[0] + " " + firstMiddleLastName[2];
//                        }

                        // Get the cell type
                        String cellType = null;

                        for (String parts : splitTokensAfterDate) {
                            if (parts.equalsIgnoreCase("CD4+") || parts.equalsIgnoreCase("CD8+") || parts.equalsIgnoreCase("Total")
                                    || parts.equalsIgnoreCase("Monocytes") || parts.equalsIgnoreCase("PBMC")
                                    || parts.equalsIgnoreCase("NK") || parts.equalsIgnoreCase("B") || parts.equalsIgnoreCase("Unpurified")) {

                                cellType = parts.replaceAll("\"", "").trim();

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

//                        for (int i = 23; i < tokensAfterDate.length(); i++) {
//                            if (tokens.get(i).contains("CD4") || tokens.get(i).contains("CD8") || tokens.get(i).contains("Total")
//                                    || tokens.get(i).contains("Monocytes") || tokens.get(i).contains("PBMC")
//                                    || tokens.get(i).contains("NK") || tokens.get(i).contains("B") || tokens.get(i).equalsIgnoreCase("Unpurified")) {
//                                cellType = tokens.get(i).replaceAll("\"", "").trim();
//
//                                if (cellType.equalsIgnoreCase("Total")) {
//                                    cellType = "Total T";
//                                } else if (cellType.equalsIgnoreCase("B")) {
//                                    cellType = "B Cells";
//                                } else if (cellType.equalsIgnoreCase("NK")) {
//                                    cellType = "NK Cells";
//                                } else if (cellType.equalsIgnoreCase("Unpurified")) {
//                                    cellType = "Unpurified Apheresis";
//                                }
//                            }
//                        }
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
                            System.out.println(hicDataItem);
                        } catch (DateTimeParseException e) {
                        System.err.println("Error parsing date/time: " + e.getMessage());
                    }
                }
            }
        }
    }

    // Function to check if a string contains capital letters
    private boolean containsCapitalLetters(String str) {
        return !str.equals(str.toLowerCase());
    }


    /**
     * Method to test whether string is numeric or not
     * @param str to test
     * @return true if numeric, false otherwise
     */
    private boolean isNumeric(String str) {
        return str.matches("-?\\d+(\\.\\d+)?");
    }

//    /**
//     * Method to tokenize a line
//     * @param line to tokenize
//     * @return list of tokens
//     */
//    private List<String> tokenizeLine(String line) {
//        List<String> tokens = new ArrayList<>();
//        StringBuilder currentToken = new StringBuilder();
//
//        boolean withinNameField = false;
//
//        for (char c : line.toCharArray()) {
//            if (Character.isWhitespace(c)) {
//                if (withinNameField) {
//                    // If whitespace occurs within a name field, treat it as part of the name
//                    currentToken.append(c);
//                } else {
//                    // If whitespace occurs between fields, add the current token to the list
//                    if (currentToken.length() > 0) {
//                        tokens.add(currentToken.toString());
//                        currentToken.setLength(0); // Reset the current token
//                    }
//                }
//            } else {
//                // If a non-whitespace character is encountered, add it to the current token
//                currentToken.append(c);
//
//                // Check if the current token is within a name field
//                if (!withinNameField && Character.isLetter(c)) {
//                    withinNameField = true;
//                }
//            }
//        }
//
//        // Add the last token to the list
//        if (currentToken.length() > 0) {
//            tokens.add(currentToken.toString());
//        }
//
//        return tokens;
//    }


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

        // Handle all capital letter tokens as separate tokens
        List<String> finalTokens = new ArrayList<>();
        for (String token : tokens) {
            if (token.equals(token.toUpperCase())) {
                for (char c : token.toCharArray()) {
                    finalTokens.add(String.valueOf(c));
                }
            } else {
                finalTokens.add(token);
            }
        }

        return finalTokens;
    }




    @Override
    public List<HICData> getHICData() {
        return hicData;
    }
}
