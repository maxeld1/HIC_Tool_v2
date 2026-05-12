package hic.hiccell;

import java.util.List;

public record HicCellMonthViewRows(
        List<HicCellOrderRecord> liveCells,
        List<HicCellOrderRecord> cancelled
) {
}
