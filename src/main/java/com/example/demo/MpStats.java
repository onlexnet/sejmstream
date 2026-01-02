package com.example.demo;

public class MpStats {
    private int mpId;
    private String firstLastName;
    private int totalVotings = 0;
    private int presentCount = 0;

    public MpStats(int mpId, String firstLastName) {
        this.mpId = mpId;
        this.firstLastName = firstLastName;
    }

    public void addVote(boolean wasPresent) {
        totalVotings++;
        if (wasPresent) presentCount++;
    }

    public double getAttendance() {
        return totalVotings == 0 ? 0.0 : (double) presentCount / totalVotings;
    }

    public int getMpId() { return mpId; }
    public String getFirstLastName() { return firstLastName; }
}
