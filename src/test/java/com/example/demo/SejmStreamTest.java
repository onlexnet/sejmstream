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
