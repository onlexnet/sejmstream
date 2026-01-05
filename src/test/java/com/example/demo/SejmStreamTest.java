package com.example.demo;

import java.util.List;

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
}
