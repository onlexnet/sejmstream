package onlexnet.app.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import onlexnet.app.ports.in.publish.PublishDailyDigestCommand;
import onlexnet.app.ports.in.publish.PublishDailyDigestOutcome;
import onlexnet.app.ports.out.FacebookPublisher;
import onlexnet.app.ports.out.SejmDailyDigestPersistence;
import onlexnet.sejmapi.SejmDigestService;

class DefaultPublishDailyDigestUseCaseTest {

    @Test
    void givenAlreadyPublishedToday_whenPublishing_thenSkipsWithoutDigestBuild() {
        var date = LocalDate.of(2026, 6, 30);
        var publisher = mock(FacebookPublisher.class);
        var digestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestPersistence.class);
        when(repository.alreadyPublishedToday(date)).thenReturn(true);

        var useCase = new DefaultPublishDailyDigestUseCase(publisher, digestService, repository);

        var outcome = useCase.publish(new PublishDailyDigestCommand(date));

        assertThat(outcome).isEqualTo(new PublishDailyDigestOutcome.SkippedAlreadyPublished(date));
        verify(digestService, never()).buildDigest(any(LocalDate.class));
        verify(publisher, never()).publish(any());
        verify(repository, never()).insertPublishLog(any(LocalDate.class), any(), eq(true), any());
    }

    @Test
    void givenDigestAvailable_whenPublishing_thenPublishesAndLogsSuccess() {
        var date = LocalDate.of(2026, 6, 30);
        var publisher = mock(FacebookPublisher.class);
        var digestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestPersistence.class);
        when(repository.alreadyPublishedToday(date)).thenReturn(false);
        when(digestService.buildDigest(date)).thenReturn(Optional.of("digest msg"));

        var useCase = new DefaultPublishDailyDigestUseCase(publisher, digestService, repository);

        var outcome = useCase.publish(new PublishDailyDigestCommand(date));

        assertThat(outcome).isEqualTo(new PublishDailyDigestOutcome.Published(date, "digest msg"));
        verify(publisher).publish("digest msg");
        verify(repository).insertPublishLog(date, "digest msg", true, null);
    }

    @Test
    void givenNoDigestAvailable_whenPublishing_thenSkipsWithoutPublish() {
        var date = LocalDate.of(2026, 6, 30);
        var publisher = mock(FacebookPublisher.class);
        var digestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestPersistence.class);
        when(repository.alreadyPublishedToday(date)).thenReturn(false);
        when(digestService.buildDigest(date)).thenReturn(Optional.empty());

        var useCase = new DefaultPublishDailyDigestUseCase(publisher, digestService, repository);

        var outcome = useCase.publish(new PublishDailyDigestCommand(date));

        assertThat(outcome).isEqualTo(new PublishDailyDigestOutcome.SkippedNoDigest(date));
        verify(publisher, never()).publish(any());
        verify(repository, never()).insertPublishLog(any(LocalDate.class), any(), eq(true), any());
    }

    @Test
    void givenPublisherThrows_whenPublishing_thenReturnsFailureAndWritesFailureLog() {
        var date = LocalDate.of(2026, 6, 30);
        var publisher = mock(FacebookPublisher.class);
        var digestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestPersistence.class);
        when(repository.alreadyPublishedToday(date)).thenReturn(false);
        when(digestService.buildDigest(date)).thenReturn(Optional.of("digest msg"));
        var publishFailure = new IllegalStateException("boom");
        doThrow(publishFailure).when(publisher).publish("digest msg");

        var useCase = new DefaultPublishDailyDigestUseCase(publisher, digestService, repository);

        var outcome = useCase.publish(new PublishDailyDigestCommand(date));

        assertThat(outcome)
                .isInstanceOfSatisfying(PublishDailyDigestOutcome.Failed.class, failed -> {
                    assertThat(failed.date()).isEqualTo(date);
                    assertThat(failed.exception()).isSameAs(publishFailure);
                });
        verify(repository).insertPublishLog(date, null, false, "boom");
    }

    @Test
    void givenDigestServiceThrows_whenPublishing_thenReturnsFailureAndWritesFailureLog() {
        var date = LocalDate.of(2026, 6, 30);
        var publisher = mock(FacebookPublisher.class);
        var digestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestPersistence.class);
        when(repository.alreadyPublishedToday(date)).thenReturn(false);
        var digestFailure = new RuntimeException("digest build failed");
        doThrow(digestFailure).when(digestService).buildDigest(date);

        var useCase = new DefaultPublishDailyDigestUseCase(publisher, digestService, repository);

        var outcome = useCase.publish(new PublishDailyDigestCommand(date));

        assertThat(outcome)
                .isInstanceOfSatisfying(PublishDailyDigestOutcome.Failed.class, failed -> {
                    assertThat(failed.date()).isEqualTo(date);
                    assertThat(failed.exception()).isSameAs(digestFailure);
                });
        verify(repository).insertPublishLog(date, null, false, "digest build failed");
        verify(publisher, never()).publish(any());
    }

    @Test
    void givenPublisherAndFailureLogThrow_whenPublishing_thenOriginalExceptionKeepsSuppressedLogError() {
        var date = LocalDate.of(2026, 6, 30);
        var publisher = mock(FacebookPublisher.class);
        var digestService = mock(SejmDigestService.class);
        var repository = mock(SejmDailyDigestPersistence.class);
        when(repository.alreadyPublishedToday(date)).thenReturn(false);
        when(digestService.buildDigest(date)).thenReturn(Optional.of("digest msg"));
        var publishFailure = new IllegalStateException("publish failed");
        var logFailure = new UnsupportedOperationException("Log insertion failed");
        doThrow(publishFailure).when(publisher).publish("digest msg");
        doThrow(logFailure).when(repository).insertPublishLog(date, null, false, "publish failed");

        var useCase = new DefaultPublishDailyDigestUseCase(publisher, digestService, repository);

        var outcome = useCase.publish(new PublishDailyDigestCommand(date));

        assertThat(outcome)
                .isInstanceOfSatisfying(PublishDailyDigestOutcome.Failed.class, failed -> {
                    assertThat(failed.exception()).isSameAs(publishFailure);
                    assertThat(failed.exception().getSuppressed()).containsExactly(logFailure);
                });
    }
}