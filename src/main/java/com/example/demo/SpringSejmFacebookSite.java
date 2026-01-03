package com.example.demo;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SpringSejmFacebookSite implements CommandLineRunner {
    @Value("${FB_TOKEN}")
    String fbToken;
    
    private final static Logger log = LoggerFactory.getLogger(SpringSejmFacebookSite.class);
    private final FaceApi faceApi;
    private final SejmApi sejmApi;
    private final MpStatsRepository mpStatsRepository;

    public SpringSejmFacebookSite(FaceApi faceApi, SejmApi sejmApi, MpStatsRepository mpStatsRepository) {
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
        for (var mp : listMP) {
            log.info("Wczytujemy dane posła {}", mp.firstLastName());
            List<VotingStats> votings = sejmApi.getVotingStats(activeTerm.num(), mp.id());
            for (VotingStats v : votings) {
                boolean wasPresent = (v.numVotings() - v.numMissed()) > 0;
                
                // aktualizujemy statystyki posła
                statsList.stream()
                        .filter(s -> s.getMpId() == mp.id())
                        .findFirst()
                        .ifPresent(s -> s.addVote(wasPresent));
            }
        }

        // Zapisujemy statystyki do bazy danych
        mpStatsRepository.saveAll(statsList);
        log.info("Zapisano {} statystyk posłów do bazy danych", statsList.size());

        // 5️⃣ Wyświetlamy frekwencję i oznaczamy posłów widm
        double threshold = 0.2; // 20%
        for (MpStats s : statsList) {
            double attendance = s.getAttendance();
            if (attendance < threshold) {
                log.warn("{} – frekwencja: {}% – poseł widmo!", 
                         s.getFirstLastName(), attendance );
            } else {
                log.info("{} – frekwencja: {}%", s.getFirstLastName(), attendance );
            }
        }

        // 6️⃣ Opcjonalnie – post na Facebook
        var activeCount = listMP.stream().filter(MP::active).count();
        var message = String.format("Lista posłów: %s, kadencja nr %s", activeCount, activeTerm.num());
        faceApi.post(message);
        log.info(message);
    }
}
