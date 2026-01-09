package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        var faceApiMock = Mockito.mock(FaceApi.class);
        var sejmApiMock = Mockito.mock(SejmApi.class);
        var mpStatsRepositoryMock = Mockito.mock(MpStatsRepository.class);

        Mockito.when(sejmApiMock.getTerms()).thenReturn(List.of(new Term(true, null, 1, null)));

        var jk = new MP("JK", 0, null, true);
        var an = new MP("AN", 1, null, true);
        Mockito.when(sejmApiMock.getMPs(1)).thenReturn(List.of(jk, an));


        var jkVoting1 = new VotingStats(false, LocalDate.of(2024, 1, 1), 1, 10, 9, 1);
        var jkVoting2 = new VotingStats(false, LocalDate.of(2025, 1, 1), 0, 10, 10, 2);
        var anVoting1 = new VotingStats(false, LocalDate.of(2023, 5, 5), 2, 8, 6, 1);
        var anVoting2 = new VotingStats(false, LocalDate.of(2026, 6, 6), 0, 8, 8, 2);

        Mockito.when(sejmApiMock.getVotingStats(1, 0)).thenReturn(List.of(jkVoting1, jkVoting2));
        Mockito.when(sejmApiMock.getVotingStats(1, 1)).thenReturn(List.of(anVoting1, anVoting2));

        var sut = new SejmStream(faceApiMock, sejmApiMock, mpStatsRepositoryMock);
        sut.run();

     
        Map<MP, List<VotingStats>> votingsMap = new HashMap<>();
        votingsMap.put(jk, List.of(jkVoting1, jkVoting2));
        votingsMap.put(an, List.of(anVoting1, anVoting2));

      
        var maxDate = SejmStream.findMaxDate(votingsMap);
        assertEquals(LocalDate.of(2026, 6, 6), maxDate);

       
        Mockito.verify(faceApiMock).post("Lista posłów: 2, kadencja nr 1");
    }

 

}
