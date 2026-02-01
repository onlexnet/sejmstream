package com.example.demo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedMap;
import java.util.SortedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SejmStream implements CommandLineRunner {
    @Value("${FB_TOKEN}")
    String fbToken;

    private final static Logger log = LoggerFactory.getLogger(SejmStream.class);
    private final FaceApi faceApi;
    private final SejmApi sejmApi;
    private final MpStatsRepository mpStatsRepository;

    public SejmStream(FaceApi faceApi, SejmApi sejmApi, MpStatsRepository mpStatsRepository) {
        this.faceApi = faceApi;
        this.sejmApi = sejmApi;
        this.mpStatsRepository = mpStatsRepository;
    }

    @Override
    public void run(String... args) throws Exception {

        // 1️⃣ Pobieramy aktywną kadencję
        var termInfo = sejmApi.getTerms();
        var activeTerm = termInfo.stream().filter(Term::current).findAny().orElseThrow();

        // 2️⃣ Pobieramy listę posłów
        var listMP = sejmApi.getMPs(activeTerm.num());

        // 4️⃣ Iterujemy po posłach i ich głosowaniach, liczymy frekwencję
        var votingStats = new HashMap<MP, List<VotingStats>>();
        for (var mp : listMP) {
            log.info("Wczytujemy dane posła {}", mp.firstLastName());
            List<VotingStats> votings = sejmApi.getVotingStats(activeTerm.num(), mp.id());
            votingStats.put(mp, votings);
        }

        var statsMap = new HashMap<MP, MpStats>();
        for (var kv : votingStats.entrySet()) {
            var mp = kv.getKey();
            var vStats = kv.getValue();
            var mpStats = new MpStats();
            statsMap.put(mp, mpStats);
            for (var it : vStats) {
                mpStats.addVotingStats(it);
            }
        }

        faceApi.deleteAllPost();

        var sortedStatMap = byAttendanceDesc(statsMap);
        var sb = new StringBuilder();
        sb.append("top 3+ aktywni posłowie w ciągu ostatnich 30 dni :");
        for (var kv : sortedStatMap.entrySet()) {
            var mp = kv.getKey();
            // var mpStats = kv.getValue();
            sb.append(mp.firstLastName());
            sb.append(",");
        }
        var message3 = sb.toString();
        faceApi.post(message3);

        var activeCount = listMP.stream().filter(MP::active).count();
        var message = String.format("Lista posłów: %s, kadencja nr %s", activeCount, activeTerm.num());
        faceApi.post(message);
        log.info(message);

        var maxDate = findMaxDate(votingStats);
        var locale = Locale.forLanguageTag("pl-PL");
        var formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale);
        var maxDateString = formatter.format(maxDate);
        var message2 = String.format("najnowsze głosowanie odbyło się dnia : %s", maxDateString);
        faceApi.post(message2);
        log.info(message2);
    }

    // returns top3+ the most active MPs
    static Map<MP, MpStats> byAttendanceDesc(Map<MP, MpStats> input) {
        return input;
    }

    static LocalDate findMaxDate(Map<MP, List<VotingStats>> votingsMap) {
        return votingsMap.values().stream()
                .flatMap(List::stream)
                .map(VotingStats::date)
                .max(LocalDate::compareTo)
                .orElse(null);
    }
}
