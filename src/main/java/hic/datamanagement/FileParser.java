package hic.datamanagement;

import hic.util.HICData;

import java.text.ParseException;
import java.util.List;

public interface FileParser {

    <E> void parse(String content) throws ParseException;


    List<HICData> getHICData();
}
