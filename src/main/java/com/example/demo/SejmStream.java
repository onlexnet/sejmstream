package com.example.demo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        // 3️⃣ Tworzymy listę statystyk posłów
        List<MpStats> statsList = new ArrayList<>();
        for (var mp : listMP) {
            statsList.add(new MpStats(mp.id(), mp.firstLastName()));
        }

        // 4️⃣ Iterujemy po posłach i ich głosowaniach, liczymy frekwencję
        var votingStats = new HashMap<MP, List<VotingStats>>();
        for (var mp : listMP) {
            log.info("Wczytujemy dane posła {}", mp.firstLastName());
            List<VotingStats> votings = sejmApi.getVotingStats(activeTerm.num(), mp.id());
            votingStats.put(mp, votings);
            for (VotingStats v : votings) {
                boolean wasPresent = (v.numVotings() - v.numMissed()) > 0;

                // aktualizujemy statystyki posła
                statsList.stream()
                        .filter(s -> s.getMpId() == mp.id())
                        .findFirst()
                        .ifPresent(s -> s.addVote(wasPresent));
            }

        }

        // List<MpStatsDb> statsDbList = new ArrayList<>();
        // for (var it : statsList) {
        // var dbo = new MpStatsDb();
        // dbo.setFirstLastName(it.getFirstLastName());
        // dbo.setMpId(it.getMpId());
        // // dbo.setPresentCount(0);
        // // dbo.setTotalVotings(0);
        // statsDbList.add(dbo);
        // }

        // Zapisujemy statystyki do bazy danych
        // mpStatsRepository.saveAll(statsDbList);
        // log.info("Zapisano {} statystyk posłów do bazy danych", statsList.size());

        // 5️⃣ Wyświetlamy frekwencję i oznaczamy posłów widm
        double threshold = 0.2; // 20%
        for (MpStats s : statsList) {
            double attendance = s.getAttendance();
            if (attendance < threshold) {
                log.warn("{} – frekwencja: {}% – poseł widmo!",
                        s.getFirstLastName(), attendance);
            } else {
                log.info("{} – frekwencja: {}%", s.getFirstLastName(), attendance);
            }
        }

        var activeCount = listMP.stream().filter(MP::active).count();

        faceApi.deleteAllPost();

        var message = String.format("Lista posłów: %s, kadencja nr %s", activeCount, activeTerm.num());
        faceApi.post(message);
        log.info(message);

        var maxDate = findMaxDate(votingStats);
        Locale locale = Locale.forLanguageTag("pl-PL");
        var formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", locale);
        var maxDateString = formatter.format(maxDate);
        var message2 = String.format("najnowsze głosowanie odbyło się dnia : %s", maxDateString);
        faceApi.post(message2);
        log.info(message2);

    }

    static LocalDate findMaxDate(Map<MP, List<VotingStats>> votingsMap) {
        return votingsMap.values().stream()
                .flatMap(List::stream)
                .map(VotingStats::date)
                .max(LocalDate::compareTo)
                .orElse(null);
    }
}
