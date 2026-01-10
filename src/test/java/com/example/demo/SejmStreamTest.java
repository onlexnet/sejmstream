package com.example.demo;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class SejmStreamTest {

    @Test
    public void happyPathCombined() throws Exception {
        var faceApi = Mockito.mock(FaceApi.class);
        var sejmApi = Mockito.mock(SejmApi.class);
        var repo = Mockito.mock(MpStatsRepository.class);

        when(sejmApi.getTerms()).thenReturn(List.of(new Term(true, null, 10, null)));

        when(sejmApi.getVotingStats(10, 1)).thenReturn(List.of(
                new VotingStats(false, LocalDate.of(2026, 6, 6), 0, 8, 8, 2)));

        var jk = new MP("JK", 1, null, true);
        when(sejmApi.getMPs(10)).thenReturn(List.of(jk));

        var sut = new SejmStream(faceApi, sejmApi, repo);
        sut.run();

        verify(faceApi).post("najnowsze głosowanie odbyło się dnia : 6 czerwca 2026");
        verify(faceApi).post("Lista posłów: 1, kadencja nr 10");
    }

    @Test
    public void shouldWorkingActive() throws Exception {
        var faceApi = Mockito.mock(FaceApi.class);
        var sejmApi = Mockito.mock(SejmApi.class);
        var repo = Mockito.mock(MpStatsRepository.class);

        when(sejmApi.getTerms()).thenReturn(List.of(new Term(true, null, 10, null)));

        var mp1 = new MP("JK", 1, null, true);
        var mp2 = new MP("AN", 2, null, true);
        var mp3 = new MP("CN", 3, null, true);
        var mp4 = new MP("XD", 4, null, true);

        var statDay1 = LocalDate.of(2025, 12, 18);
        var mp1Voting = new VotingStats(false, statDay1, 1, 10, 9, 2);
        var mp2Voting = new VotingStats(false, statDay1, 0, 10, 10, 2);
        var mp3Voting = new VotingStats(false, statDay1, 4, 10, 6, 2);
        var mp4Voting = new VotingStats(false, statDay1, 2, 10, 8, 2);

        when(sejmApi.getMPs(10)).thenReturn(List.of(mp1, mp2, mp3, mp4));

        when(sejmApi.getVotingStats(10, 1)).thenReturn(List.of(mp1Voting));
        when(sejmApi.getVotingStats(10, 2)).thenReturn(List.of(mp2Voting));
        when(sejmApi.getVotingStats(10, 3)).thenReturn(List.of(mp3Voting));
        when(sejmApi.getVotingStats(10, 4)).thenReturn(List.of(mp4Voting));

        var sut = new SejmStream(faceApi, sejmApi, repo);
        sut.run();

        verify(faceApi).post("top 3+ aktywni posłowie w ciągu ostatnich 30 dni :AN,JK,XD,");

    }

    @Test
    public void findMaxDateTest() throws Exception {
        var jk = new MP("JK", 0, null, true);
        var an = new MP("AN", 1, null, true);
        var jkVoting1 = new VotingStats(false, LocalDate.of(2024, 1, 1), 1, 10, 9, 1);
        var jkVoting2 = new VotingStats(false, LocalDate.of(2025, 1, 1), 0, 10, 10, 2);
        var anVoting1 = new VotingStats(false, LocalDate.of(2023, 5, 5), 2, 8, 6, 1);
        var anVoting2 = new VotingStats(false, LocalDate.of(2026, 6, 6), 0, 8, 8, 2);
        Map<MP, List<VotingStats>> votingsMap = new HashMap<>();
        votingsMap.put(jk, List.of(jkVoting1, jkVoting2));
        votingsMap.put(an, List.of(anVoting1, anVoting2));

        var maxDate = SejmStream.findMaxDate(votingsMap);
        Assertions.assertThat(LocalDate.of(2026, 6, 6)).isEqualTo(maxDate);
    }

    @Test
    public void findMaxDateWhenListIsEmpty() {

        Map<MP, List<VotingStats>> votingsMap = new HashMap<>();
        var jk = new MP("JK", 0, null, true);
        votingsMap.put(jk, List.of());

        var actual = SejmStream.findMaxDate(votingsMap);
        Assertions.assertThat(actual).isNull();

    }

}
