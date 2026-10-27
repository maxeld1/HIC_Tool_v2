package hic.util;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

public final class RequestIdentityOrder {
    private RequestIdentityOrder() {
    }

    public static List<HICData> sortAndAssignIds(List<HICData> requests) {
        List<HICData> sorted = requests.stream().sorted(Comparator.comparingInt(HICData::getOrderNumber)
                .thenComparingInt(request -> cellTypeRank(request.getCellType()))
                .thenComparing(HICData::getCellType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(HICData::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(HICData::getRequestDate)).toList();
        return IntStream.range(0, sorted.size()).mapToObj(index -> {
            HICData request = sorted.get(index);
            return new HICData(index + 1, request.getOrderNumber(), request.getRequestDate(), request.getName(),
                    request.getCellType(), request.getMaxRequest(), request.getMinRequest(),
                    request.getRecentlyCancelledRequests(), request.getRequestType(), request.getRequesterComment());
        }).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    private static int cellTypeRank(String cellType) {
        return switch (cellType.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)) {
            case "total t", "total t cells" -> 0;
            case "cd4", "cd4+" -> 1;
            case "cd8", "cd8+" -> 2;
            case "monocytes" -> 3;
            case "nk cells" -> 4;
            case "pbmc", "pbmcs" -> 5;
            case "unpurified apheresis", "unpurified apheresis product" -> 6;
            case "top layer ficoll", "top layer of ficoll" -> 7;
            case "bottom layer ficoll", "bottom pellet of ficoll" -> 8;
            default -> 9;
        };
    }
}
