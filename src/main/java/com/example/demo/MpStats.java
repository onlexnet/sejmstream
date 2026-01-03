package com.example.demo;

import jakarta.persistence.*;

@Entity
@Table(name = "mp_stats")
public class MpStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "mp_id", nullable = false)
    private int mpId;
    
    @Column(name = "first_last_name", nullable = false)
    private String firstLastName;
    
    @Column(name = "total_votings", nullable = false)
    private int totalVotings = 0;
    
    @Column(name = "present_count", nullable = false)
    private int presentCount = 0;

    public MpStats() {
        // JPA requires no-arg constructor
    }

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

    public Long getId() { return id; }
    public int getMpId() { return mpId; }
    public String getFirstLastName() { return firstLastName; }
    public int getTotalVotings() { return totalVotings; }
    public int getPresentCount() { return presentCount; }
}
