package datamanagement;

import util.HICData;

import java.text.ParseException;
import java.util.List;

public interface FileParser {

    <E> void parse(String content) throws ParseException;


    List<HICData> getHICData();
}
