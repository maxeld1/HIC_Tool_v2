package hic.processor;

import hic.datamanagement.FileReader;
import hic.util.HICData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class Processor {

    private FileReader fileReader;

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
            } else if (Objects.equals(data.getCellType(), "NK")) {
                nkOrders++;
                nkMax += data.getMaxRequest();
                nkMin += data.getMinRequest();
            } else if (Objects.equals(data.getCellType(), "B")) {
                bOrders++;
                bMax += data.getMaxRequest();
                bMin += data.getMinRequest();
            }
        }

        // Add total orders for each cell type to totalOrders variable
        totalOrders = nkOrders + cd8Orders + cd4Orders + monocyteOrders + pbmcOrders + totalTOrders + bOrders;

        // Print out the summary
        System.out.println("Summary: ");
        System.out.println();
        System.out.println("               Total Requests" + "          Max   " + "     Min   ");
        System.out.println("B Cells" + "              " + bOrders + "                 " + bMax + "        " + bMin);
        System.out.println("NK Cells" + "             " +    nkOrders  +  "                 "  + nkMax + "        " + nkMin);
        System.out.println("CD8+ T" + "               " + cd8Orders  + "                " + cd8Max + "      " + cd8Min);
        System.out.println("CD4+ T" + "               " + cd4Orders + "                " + cd4Max + "      " + cd4Min);
        System.out.println("Monocytes" + "            " + monocyteOrders + "                " + monocytesMax + "      " + monocytesMin);
        System.out.println("PBMC" + "                 " + pbmcOrders + "                 " + pbmcMax + "      " + pbmcMin);
        System.out.println("Total T" + "              " + totalTOrders + "                 " + totalTMax + "      " + totalTMin);
        System.out.println();
        System.out.println("Total Orders: " + totalOrders);

        // Add max and min cell orders to maxAndMinOrders arraylist
        maxAndMinOrders.add(nkMax);
        maxAndMinOrders.add(nkMin);
        maxAndMinOrders.add(cd8Max);
        maxAndMinOrders.add(cd8Min);
        maxAndMinOrders.add(cd4Max);
        maxAndMinOrders.add(cd4Min);
        maxAndMinOrders.add(monocytesMax);
        maxAndMinOrders.add(monocytesMin);
        maxAndMinOrders.add(pbmcMax);
        maxAndMinOrders.add(pbmcMin);
        maxAndMinOrders.add(totalTMax);
        maxAndMinOrders.add(totalTMin);
        maxAndMinOrders.add(bMax);
        maxAndMinOrders.add(bMin);

        //System.out.println(maxAndMinOrders);
        return maxAndMinOrders;
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

        totalTMaxNeeded = (maxAndMinRequests.get(10) * 4) / 40;
        totalTMinNeeded = (maxAndMinRequests.get(11) * 4) / 40;

        bMaxNeeded = (maxAndMinRequests.get(12) * 20) / 40;
        bMinNeeded = (maxAndMinRequests.get(13) * 20) / 40;

        totalMaxNeeded += nkMaxNeeded + cd8MaxNeeded + cd4MaxNeeded + monocytesMaxNeeded + totalTMaxNeeded + bMaxNeeded;
        totalMinNeeded += nkMinNeeded + cd8MinNeeded + cd4MinNeeded + monocytesMinNeeded + totalTMinNeeded + bMinNeeded;

        // Print out the summary
        System.out.println("Summary of Apheresis Required: ");
        System.out.println();
        System.out.println("                    Max   " + "     Min   ");
        System.out.println("B Cells" + "             "  + bMaxNeeded + "        " + bMinNeeded);
        System.out.println("NK Cells" + "            "   + nkMaxNeeded + "        " + nkMinNeeded);
        System.out.println("CD8+ T" + "              "   + cd8MaxNeeded + "    " + cd8MinNeeded);
        System.out.println("CD4+ T" + "              " +  cd4MaxNeeded + "       " + cd4MinNeeded);
        System.out.println("Monocytes" + "           "  + monocytesMaxNeeded + "     " + monocytesMinNeeded);
        System.out.println("Total T" + "             " + totalTMaxNeeded + "       " + totalTMinNeeded);
        System.out.println("Total Volume:       " + totalMaxNeeded + "     " + totalMinNeeded);

    }


    public void sortByCellTypeAndDateTime(List<HICData> hicData) {
        // Define a custom comparator
        Comparator<HICData> customComparator = new Comparator<HICData>() {

            // Define order of cell types
            String[] cellTypeOrder = {"NK", "CD8+", "CD4+", "Monocytes", "PBMC", "Total T", "B"};

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
        //System.out.println(hicData);
    }


    public void sortByCellType(List<HICData> hicData) {

        // Create a custom comparator
        Comparator<HICData> customComparator = new Comparator<HICData>() {

            // Define order of cell types
            String[] cellTypeOrder = {"NK", "CD8+", "CD4+", "Monocytes", "PBMC", "Total T", "B"};

            @Override
            public int compare(HICData data1, HICData data2) {

                // Get the index of each cell type
                int index1 = getIndex(data1.getCellType());
                int index2 = getIndex(data2.getCellType());

                // Compare the indices to determine order
                return Integer.compare(index1, index2);
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

        hicData.sort(customComparator);
        System.out.println(hicData);
    }





}
