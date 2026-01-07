package com.example.demo;

import jakarta.persistence.*;
import lombok.Getter;


public class MpStats {
   

    @Getter
    private String firstLastName;
    @Getter
    private int mpId;

    private int totalVotings;
    private int presentCount;

    public MpStats(int mpId, String firstLastName) {
        this.mpId = mpId;
        this.firstLastName = firstLastName;
    }

    public void addVote(boolean wasPresent) {
        totalVotings++;
        if (wasPresent) presentCount++;
    }

    public double getAttendance() {
        return totalVotings == 0 ? 100.0 : (double) presentCount / totalVotings * 100.0;
    }
}
