package util;

import java.time.LocalDateTime;

public class HICData {

    private int requestID;
    private LocalDateTime requestDate;
    private String name;
    private String cellType;
    private double maxRequest;
    private double minRequest;
    private String requestType;
    private String requesterComment;
    private String recentlyCancelledRequests;



    public HICData(int requestID, LocalDateTime requestDate, String name, String cellType, double maxRequest, double minRequest) {

        this.requestID = requestID;
        this.requestDate = requestDate;
        this.name = name;
        this.cellType = cellType;
        this.maxRequest = maxRequest;
        this.minRequest = minRequest;


    }

    // Getters
    public int getRequestID() {
        return requestID;
    }

    public LocalDateTime getRequestDate() {
        return requestDate;
    }

    public String getName() {
        return name;
    }

    public String getCellType() {
        return cellType;
    }

    public double getMaxRequest() {
        return maxRequest;
    }

    public double getMinRequest() {
        return minRequest;
    }

    public String getRequestType() {
        return requestType;
    }

    public String getRequesterComment() {
        return requesterComment;
    }

    public String getRecentlyCancelledRequests() {
        return recentlyCancelledRequests;
    }

    @Override
    public String toString() {
        return "[Request ID: " + requestID + ", Request Date: " + requestDate + ", Name: "
                + name + ", Cell Type: " + cellType + ", Max Request: " + maxRequest +
                ", Min Request: " + minRequest  + "]";
    }
}
