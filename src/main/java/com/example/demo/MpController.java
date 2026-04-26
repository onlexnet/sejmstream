package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/mps")
public class MpController {
    private final SejmApi sejmApi;

    public MpController(SejmApi sejmApi) {
        this.sejmApi = sejmApi;
    }

    /**
     * Zwraca wszystkich posłów z aktywnej kadencji
     */
    @GetMapping
    public List<MP> getAllMps() {
        var term = sejmApi.getTerms().stream().filter(Term::current).findAny().orElseThrow();
        return sejmApi.getMPs(term.num());
    }

    /**
     * Zwraca listę posłów, którzy brali udział w ostatnim głosowaniu (ostatnia data głosowania)
     */
    @GetMapping("/last-voting")
    public List<MP> getMpsFromLastVoting() {
        // Pobierz aktywną kadencję
        var term = sejmApi.getTerms().stream().filter(Term::current).findAny().orElseThrow();
        var mps = sejmApi.getMPs(term.num());

        // Pobierz głosowania dla wszystkich posłów
        var mpToVotingStats = new java.util.HashMap<MP, List<VotingStats>>();
        for (var mp : mps) {
            var votings = sejmApi.getVotingStats(term.num(), mp.id());
            mpToVotingStats.put(mp, votings);
        }

        // Wyznacz najnowszą datę głosowania
        var maxDate = mpToVotingStats.values().stream()
                .flatMap(java.util.List::stream)
                .map(VotingStats::date)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (maxDate == null) return java.util.Collections.emptyList();

        // Zwróć posłów, którzy mają głosowanie w tej dacie
        return mpToVotingStats.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(v -> v.date().equals(maxDate)))
                .map(java.util.Map.Entry::getKey)
                .toList();
    }
}