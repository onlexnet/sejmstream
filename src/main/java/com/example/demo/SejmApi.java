package com.example.demo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

public interface SejmApi {
    List<Term> getTerms();

    List<MP> getMPs(int termNumber);

    List<VotingStats> getVotingStats(int termNumber, int mpId);

}

@Component
class SejmApiImpl implements SejmApi {

    private final RestClient restClient = RestClient.create("https://api.sejm.gov.pl");

    private final VotingStatsDbRepository votingStatsRepo;

    SejmApiImpl(VotingStatsDbRepository votingStatsRepo) {
        this.votingStatsRepo = votingStatsRepo;
    }

    @Override
    @Cacheable("terms")
    public List<Term> getTerms() {
        var termType = new ParameterizedTypeReference<List<Term>>() {
        };
        return restClient.get().uri("sejm/term").retrieve().body(termType);
    }

    @Override
    @Cacheable("mps")
    public List<MP> getMPs(int termNumber) {
        var type = new ParameterizedTypeReference<List<MP>>() {
        };
        return restClient.get().uri("sejm/term{termNo}/MP", termNumber).retrieve().body(type);
    }

    @Override
    @Cacheable("votingStats")
    public List<VotingStats> getVotingStats(int termNumber, int mpId) {
        var type = new ParameterizedTypeReference<List<VotingStats>>() {
        };
        var result = restClient.get()
                .uri("sejm/term{termNo}/MP/{mpId}/votings/stats", termNumber, mpId)
                .retrieve()
                .body(type);

        List<VotingStatsDb> array = new ArrayList<>();
        for (var it : result) {
            var db = new VotingStatsDb();
            db.setCurrentTerm(termNumber);
            db.setAbsenceExcuse(it.absenceExcuse());
            db.setMpId(mpId);
            db.setNumMissed(it.numMissed());
            db.setNumVoted(it.numVoted());
            db.setNumVotings(it.numVotings());
            db.setSitting(it.sitting());
            db.setTermDate(it.date());
            array.add(db);
            votingStatsRepo.save(db);
        }

        votingStatsRepo.saveAll(array);
        return result;
    }
}
