package hic.yield;

import java.time.LocalDate;

public class DonorYieldRecord {
    private final String donorId;
    private final LocalDate date;
    private final String cellType;
    private final double requestedCells;
    private final double actualYield;
    private final double apheresisUsed;
    private final String notes;

    public DonorYieldRecord(String donorId, LocalDate date, String cellType, double requestedCells, double actualYield, double apheresisUsed, String notes) {
        this.donorId = donorId;
        this.date = date;
        this.cellType = cellType;
        this.requestedCells = requestedCells;
        this.actualYield = actualYield;
        this.apheresisUsed = apheresisUsed;
        this.notes = notes;
    }

    public String getDonorId() {
        return donorId;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getCellType() {
        return cellType;
    }

    public double getRequestedCells() {
        return requestedCells;
    }

    public double getActualYield() {
        return actualYield;
    }

    public double getApheresisUsed() {
        return apheresisUsed;
    }

    public String getNotes() {
        return notes;
    }
}
