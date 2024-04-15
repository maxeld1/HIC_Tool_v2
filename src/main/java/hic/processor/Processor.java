package hic.processor;

import hic.datamanagement.FileReader;
import hic.util.HICData;

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
    public void printSummary(List<HICData> hicData) {

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

        totalOrders = nkOrders + cd8Orders + cd4Orders + monocyteOrders + pbmcOrders + totalTOrders + bOrders;

        System.out.println("Summary: ");
        System.out.println();
        System.out.println("               Total Requests" + "          Max   " + "     Min   ");
        System.out.println("NK Cells" + "             " +    nkOrders  +  "                 "  + nkMax + "        " + nkMin);
        System.out.println("CD8+ T" + "               " + cd8Orders  + "                " + cd8Max + "      " + cd8Min);
        System.out.println("CD4+ T" + "               " + cd4Orders + "                " + cd4Max + "      " + cd4Min);
        System.out.println("Monocytes" + "            " + monocyteOrders + "                " + monocytesMax + "      " + monocytesMin);
        System.out.println("PBMC" + "                 " + pbmcOrders + "                 " + pbmcMax + "      " + pbmcMin);
        System.out.println("Total T" + "              " + totalTOrders + "                 " + totalTMax + "      " + totalTMin);
        System.out.println("B Cells" + "              " + bOrders + "                 " + bMax + "        " + bMin);
        System.out.println();
        System.out.println("Total Orders: " + totalOrders);

    }

    public void calculateApheresisNeeded(List<HICData> hicData) {

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
