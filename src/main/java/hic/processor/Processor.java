package hic.processor;

import hic.datamanagement.FileReader;
import hic.datamanagement.HICDataComparator;
import hic.util.HICData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class Processor {

    private FileReader fileReader;
    final String HEADER_FORMAT = "%-30s%-20s%-10s%-10s%n";
    final String HEADER_FORMAT_APHERESIS = "%-20s%-10s%-10s%n";
    final String ROW_FORMAT = "%-30s%-20d%-10.2f%-10.2f%n";
    final String ROW_FORMAT_APHERESIS = "%-20s%-10.2f%-10.2f%n";
    final String BOLD_START = "\033[1m";
    final String BOLD_END = "\033[0m";

    public Processor(FileReader fileReader) {
        this.fileReader = fileReader;
    }

    /**
     * Method to calculate total cell counts and print summary of orders
     * @param hicData to investigate
     */
    public List<Double> printHICSummary(List<HICData> hicData) {

        List<Double> maxAndMinOrders = new ArrayList<>();

        // Initialize variables to store order #, max requests, min requests
        int totalOrders = 0;

        int cd4Orders = 0;
        int cd8Orders = 0;
        int totalTOrders = 0;
        int pbmcOrders = 0;
        int monocyteOrders = 0;
        int nkOrders = 0;
        int bOrders = 0;
        int apheresisOrders = 0;

        double cd4Max = 0;
        double cd4Min = 0;
        double cd8Max = 0;
        double cd8Min = 0;
        double totalTMax = 0;
        double totalTMin = 0;
        double monocytesMax = 0;
        double monocytesMin = 0;
        double pbmcMax = 0;
        double pbmcMin = 0;
        double nkMax = 0;
        double nkMin = 0;
        double bMax = 0;
        double bMin = 0;
        double apheresisMax = 0;
        double apheresisMin = 0;

        // Iterate over the hic data to extract the max and min requests
        for (HICData data : hicData) {

            if (Objects.equals(data.getCellType(), "CD4+")) {
                cd4Orders++;
                cd4Max += data.getMaxRequest();
                cd4Min += data.getMinRequest();
            } else if (Objects.equals(data.getCellType(), "CD8+")) {
                cd8Orders++;
                cd8Max += data.getMaxRequest();
                cd8Min += data.getMinRequest();
            } else if (Objects.equals(data.getCellType(), "Total T")) {
                totalTOrders++;
                totalTMax += data.getMaxRequest();
                totalTMin += data.getMinRequest();
            } else if (Objects.equals(data.getCellType(), "Monocytes")) {
                monocyteOrders++;
                monocytesMax += data.getMaxRequest();
                monocytesMin += data.getMinRequest();
            } else if (Objects.equals(data.getCellType(), "PBMC")) {
                pbmcOrders++;
                pbmcMax += data.getMaxRequest();
                pbmcMin += data.getMinRequest();
            } else if (Objects.equals(data.getCellType(), "NK Cells")) {
                nkOrders++;
                nkMax += data.getMaxRequest();
                nkMin += data.getMinRequest();
            } else if (Objects.equals(data.getCellType(), "B Cells")) {
                bOrders++;
                bMax += data.getMaxRequest();
                bMin += data.getMinRequest();
            } else if (Objects.equals(data.getCellType(), "Unpurified Apheresis")) {
                apheresisOrders++;
                apheresisMax += data.getMaxRequest();
                apheresisMin += data.getMinRequest();
            }
        }

        // Add total orders for each cell type to totalOrders variable
        totalOrders = nkOrders + cd8Orders + cd4Orders + monocyteOrders + pbmcOrders + totalTOrders + bOrders + apheresisOrders;

        // Print out the summary
        System.out.println("\n----------------------------------------------------------------------");
        System.out.println("Summary:\n");
        //System.out.print(BOLD_START); // Start bold formatting
        System.out.printf(HEADER_FORMAT, "Cell Type", "Total Requests", "Max", "Min");
        //System.out.print(BOLD_END); // End bold formatting
        System.out.println();

        // Print individual rows of cells/apheresis
        printRow("B Cells", bOrders, bMax, bMin);
        printRow("NK Cells", nkOrders, nkMax, nkMin);
        printRow("CD8+ T", cd8Orders, cd8Max, cd8Min);
        printRow("CD4+ T", cd4Orders, cd4Max, cd4Min);
        printRow("Monocytes", monocyteOrders, monocytesMax, monocytesMin);
        printRow("PBMC", pbmcOrders, pbmcMax, pbmcMin);
        printRow("Total T", totalTOrders, totalTMax, totalTMin);
        printRow("Unpurified Apheresis", apheresisOrders, apheresisMax, apheresisMin);

        // Print total orders
        System.out.println();
        System.out.println("Total Orders: " + totalOrders);
        System.out.println("----------------------------------------------------------------------");

        // Add max and min cell orders to maxAndMinOrders arraylist
        maxAndMinOrders.add(nkMax);
        maxAndMinOrders.add(nkMin);
        maxAndMinOrders.add(cd8Max);
        maxAndMinOrders.add(cd8Min);
        maxAndMinOrders.add(cd4Max);
        maxAndMinOrders.add(cd4Min);
        maxAndMinOrders.add(monocytesMax);
        maxAndMinOrders.add(monocytesMin);
        maxAndMinOrders.add(totalTMax);
        maxAndMinOrders.add(totalTMin);
        maxAndMinOrders.add(bMax);
        maxAndMinOrders.add(bMin);

        //System.out.println(maxAndMinOrders);
        return maxAndMinOrders;
    }

    /**
     * Method to print a single row for cell orders summary
     * @param cellType to print
     * @param totalRequests to print
     * @param max requests to print
     * @param min requests to print
     */
    private void printRow(String cellType, int totalRequests, double max, double min) {
        System.out.printf(ROW_FORMAT, cellType, totalRequests, max, min);
    }



    public void calculateApheresisNeeded(List<Double> maxAndMinRequests) {

        double nkMaxNeeded;
        double nkMinNeeded;
        double cd8MaxNeeded;
        double cd8MinNeeded;
        double cd4MaxNeeded;
        double cd4MinNeeded;
        double monocytesMaxNeeded;
        double monocytesMinNeeded;
        double totalTMaxNeeded;
        double totalTMinNeeded;
        double bMaxNeeded;
        double bMinNeeded;

        double totalMaxNeeded = 0;
        double totalMinNeeded = 0;

        // Calculate apheresis needed for each cell type/request type
        nkMaxNeeded = (maxAndMinRequests.get(0) * 20) / 40;
        nkMinNeeded = (maxAndMinRequests.get(1) * 20) / 40;

        cd8MaxNeeded = (maxAndMinRequests.get(2) * 17) / 40;
        cd8MinNeeded = (maxAndMinRequests.get(3) * 17) / 40;

        cd4MaxNeeded = (maxAndMinRequests.get(4) * 5) / 40;
        cd4MinNeeded = (maxAndMinRequests.get(5) * 5) / 40;

        monocytesMaxNeeded = (maxAndMinRequests.get(6) * 7) / 40;
        monocytesMinNeeded = (maxAndMinRequests.get(7) * 7) / 40;

        totalTMaxNeeded = (maxAndMinRequests.get(8) * 4) / 40;
        totalTMinNeeded = (maxAndMinRequests.get(9) * 4) / 40;

        bMaxNeeded = (maxAndMinRequests.get(10) * 20) / 40;
        bMinNeeded = (maxAndMinRequests.get(11) * 20) / 40;

        totalMaxNeeded += nkMaxNeeded + cd8MaxNeeded + cd4MaxNeeded + monocytesMaxNeeded + totalTMaxNeeded + bMaxNeeded;
        totalMinNeeded += nkMinNeeded + cd8MinNeeded + cd4MinNeeded + monocytesMinNeeded + totalTMinNeeded + bMinNeeded;

        // Print out the summary
        System.out.println("Summary of Apheresis Required: ");
        System.out.println();
        //System.out.print(BOLD_START); // Start bold formatting
        System.out.printf(HEADER_FORMAT_APHERESIS, "Cell Type", "Max", "Min");
        //System.out.print(BOLD_END); // End bold formatting
        System.out.println();

        // Print individual rows
        printRowApheresis("B Cells", bMaxNeeded, bMinNeeded);
        printRowApheresis("NK Cells", nkMaxNeeded, nkMinNeeded);
        printRowApheresis("CD8+ T", cd8MaxNeeded, cd8MinNeeded);
        printRowApheresis("CD4+ T", cd4MaxNeeded, cd4MinNeeded);
        printRowApheresis("Monocytes", monocytesMaxNeeded, monocytesMinNeeded);
        printRowApheresis("Total T", totalTMaxNeeded, totalTMinNeeded);
        printRowApheresis("Total Volume:", totalMaxNeeded, totalMinNeeded);
        System.out.println("----------------------------------------------------------------------");

    }

    /**
     * Method to print a single row for apheresis summary
     * @param cellType to print
     * @param max request apheresis
     * @param min request apheresis
     */
    private void printRowApheresis(String cellType, double max, double min) {
        System.out.printf(ROW_FORMAT_APHERESIS, cellType, max, min);
    }


    public void sortByCellTypeAndDateTime(List<HICData> hicData) {
        // Define a custom comparator
        Comparator<HICData> customComparator = new Comparator<HICData>() {

            // Define order of cell types
            String[] cellTypeOrder = {"B Cells", "NK Cells", "CD8+", "CD4+", "Monocytes", "PBMC", "Total T", "Unpurified Apheresis"};

            @Override
            public int compare(HICData data1, HICData data2) {

                // Get the index of each cell type
                int index1 = getIndex(data1.getCellType());
                int index2 = getIndex(data2.getCellType());

                // Compare the indices to determine order
                int cellTypeComparison = Integer.compare(index1, index2);

                if (cellTypeComparison != 0) {
                    // If cell types are different, return the comparison result
                    return cellTypeComparison;
                } else {
                    // If cell types are the same, compare dates and times
                    return data1.getRequestDate().compareTo(data2.getRequestDate());
                }
            }

            private int getIndex(String cellType) {

                for (int i = 0; i < cellTypeOrder.length; i++) {
                    if (cellTypeOrder[i].equalsIgnoreCase(cellType)) {
                        return i;
                    }
                }
                // If the cell type is not found in the order, consider it as last
                return cellTypeOrder.length;
            }

        };

        // Sort the list using the custom comparator
        hicData.sort(customComparator);

//        for (HICData data : hicData) {
//            System.out.println(data);
//        }
    }

    /**
     * Method to extract CD4 and CD8 records
     * @param hicData input
     * @return list of CD4 and CD8 records
     */
    public List<HICData> getCD4CD8CellRecords(List<HICData> hicData) {

        List<HICData> cd4Cd8Records = new ArrayList<>();

        for (HICData data : hicData) {

            if (Objects.equals(data.getCellType(), "CD4+") || Objects.equals(data.getCellType(), "CD8+")) {
                cd4Cd8Records.add(data);
            }
        }

        return cd4Cd8Records;
    }

    /**
     * Method to extract non CD4 and CD8 records
     * @param hicData input
     * @return list of records that dont have CD4 or CD8
     */
    public List<HICData> getOtherCellTypeRecords(List<HICData> hicData) {

        List<HICData> otherCellTypeRecords = new ArrayList<>();

        for (HICData data : hicData) {

            if (!Objects.equals(data.getCellType(), "CD4+") && !Objects.equals(data.getCellType(), "CD8+")) {
                otherCellTypeRecords.add(data);
                //System.out.println(data);
            }
        }

        return otherCellTypeRecords;

    }

    /**
     * Method to extract incubator cell types
     * @param hicData list
     * @return incubator cells list
     */
    public List<HICData> getIncubatorCells(List<HICData> hicData) {

        List<HICData> incubatorCells = new ArrayList<>();

        for (HICData data : hicData) {

            if (Objects.equals(data.getCellType(), "CD4+") || Objects.equals(data.getCellType(), "CD8+" ) || Objects.equals(data.getCellType(), "Total T" ) || Objects.equals(data.getCellType(), "NK Cells" ) || Objects.equals(data.getCellType(), "B Cells" )) {
                incubatorCells.add(data);
            }
        }

        incubatorCells.sort(new HICDataComparator()); //sort list by name

        return incubatorCells;
    }

    /**
     * Method to extract deli fridge cell types
     * @param hicData list
     * @return deli fridge list
     */
    public List<HICData> getDeliFridgeCells(List<HICData> hicData) {

        List<HICData> deliFridgeCells = new ArrayList<>();

        System.out.println();

        for (HICData data : hicData) {

            if (!Objects.equals(data.getCellType(), "CD4+") && !Objects.equals(data.getCellType(), "CD8+" ) && !Objects.equals(data.getCellType(), "Total T" ) && !Objects.equals(data.getCellType(), "NK Cells" ) && !Objects.equals(data.getCellType(), "B Cells" )) {
                deliFridgeCells.add(data);
            }
        }

        deliFridgeCells.sort(new HICDataComparator()); //sort list by name

        return deliFridgeCells;
    }





}
