package com.example.demo;

import java.time.LocalDate;
import java.util.List;

// https://api.sejm.gov.pl/sejm/term10/MP/1
record MP(String firstLastName, int id, String club, boolean active) {
}

record Term(boolean current, LocalDate from, int num, LocalDate to) {
}

record Vote(int mpId, boolean present) {
}

record VotingStats(
        // czy jest usprawiedliwienie nieobecności
        boolean absenceExcuse,
        // data posiedzenia
        LocalDate date,
        // liczba opuszczonych głosowań
        int numMissed,
        // liczba głosowań w danym dniu posiedzenia
        int numVotings,
        // liczba oddanych głosów
        int numVoted,
        // numer posiedzenia
        int sitting
) {
}


