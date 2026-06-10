
package com.example.demo;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "voting_stats")
public class VotingStatsDb {
    @Column(nullable = false)
    private int mpId;

    @Column(nullable = false)
    private int currentTerm;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean absenceExcuse;

    @Column(name = "term_date", nullable = false)
    private LocalDate termDate;

    @Column(nullable = false)
    private int numMissed;

    @Column(nullable = false)
    private int numVotings;

    @Column(nullable = false)
    private int numVoted;

    @Column(nullable = false)
    private int sitting;

    public VotingStatsDb() {
    }

    public VotingStatsDb(boolean absenceExcuse, LocalDate termDate, int numMissed, int numVotings, int numVoted,
            int sitting, int mpId, int currentTerm) {
        this.absenceExcuse = absenceExcuse;
        this.termDate = termDate;
        this.numMissed = numMissed;
        this.numVotings = numVotings;
        this.numVoted = numVoted;
        this.sitting = sitting;
        this.mpId = mpId;
        this.currentTerm = currentTerm;
    }
    public int getMpId() {
        return mpId;
    }

    public void setMpId(int mpId) {
        this.mpId = mpId;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public void setCurrentTerm(int currentTerm) {
        this.currentTerm = currentTerm;
    }

    public Long getId() {
        return id;
    }

    public boolean isAbsenceExcuse() {
        return absenceExcuse;
    }

    public void setAbsenceExcuse(boolean absenceExcuse) {
        this.absenceExcuse = absenceExcuse;
    }

    public LocalDate getTermDate() {
        return termDate;
    }

    public void setTermDate(LocalDate termDate) {
        this.termDate = termDate;
    }

    public int getNumMissed() {
        return numMissed;
    }

    public void setNumMissed(int numMissed) {
        this.numMissed = numMissed;
    }

    public int getNumVotings() {
        return numVotings;
    }

    public void setNumVotings(int numVotings) {
        this.numVotings = numVotings;
    }

    public int getNumVoted() {
        return numVoted;
    }

    public void setNumVoted(int numVoted) {
        this.numVoted = numVoted;
    }

    public int getSitting() {
        return sitting;
    }

    public void setSitting(int sitting) {
        this.sitting = sitting;
    }
}
