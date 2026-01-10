package com.example.demo;

public class MpStats {

    private int totalAbsent;
    private int totalPresent;

    public void addVotingStats(VotingStats votingStats) {
        totalPresent += votingStats.numVoted();
        totalAbsent += votingStats.numMissed();
    }

    public double getAttendance() {
        var count = totalPresent + totalAbsent;

        return count == 0
                ? 100
                : totalPresent / count;
    }
}
