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
    public void happyPath() throws Exception {
        var faceApiMock = Mockito.mock(FaceApi.class);
        var sejmApiMock = Mockito.mock((SejmApi.class));
        var mpStatsRepositoryMock = Mockito.mock(MpStatsRepository.class);

        Mockito.when(sejmApiMock.getTerms()).thenReturn(List.of(new Term(true, null, 1, null)));

        Mockito.when(sejmApiMock.getMPs(1)).thenReturn(List.of(
                new MP(null, 0, null, true),
                new MP(null, 0, null, true)));

        var sut = new SejmStream(faceApiMock, sejmApiMock, mpStatsRepositoryMock);

        sut.run();

        Mockito.verify(faceApiMock).post("Lista posłów: 2, kadencja nr 1");
    }

    @Test
    public void happyPath2() throws Exception {

        MP mp1 = new MP("Jan Kowalski", 1, "Klub A", true);
        MP mp2 = new MP("Anna Nowak", 2, "Klub B", true);

        VotingStats v1 = new VotingStats(false, LocalDate.of(2023, 1, 10), 0, 0, 0, 0);
        VotingStats v2 = new VotingStats(false, LocalDate.of(2023, 3, 5), 0, 0, 0, 0);
        VotingStats v3 = new VotingStats(false, LocalDate.of(2022, 12, 20), 0, 0, 0, 0);

        Map<MP, List<VotingStats>> map = new HashMap<>();
        map.put(mp1, List.of(v1, v2));
        map.put(mp2, List.of(v3));

        LocalDate result = findMaxDate(map);

        Assertions.assertThat(result).isEqualTo(LocalDate.of(2023, 3, 5));
    }

    private LocalDate findMaxDate(Map<MP, List<VotingStats>> map) {
        return map.values().stream()
                .flatMap(List::stream)
                .map(VotingStats::date)
                .max(LocalDate::compareTo)
                .orElse(null);
    }
}
