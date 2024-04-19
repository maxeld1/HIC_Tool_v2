package hic.datamanagement;

import hic.util.HICData;

import java.util.Comparator;

public class HICDataComparator implements Comparator<HICData> {

    @Override
    public int compare(HICData data1, HICData data2) {
        // Compare the names of HICData objects
        return data1.getName().compareTo(data2.getName());
    }
}
