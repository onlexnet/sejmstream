package onlexnet.app.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishCommand;
import onlexnet.app.ports.in.interpellation.ProcessInterpellationPublishOutcome;
import onlexnet.app.ports.out.AttachmentMetadata;
import onlexnet.app.ports.out.FacebookPublisher;
import onlexnet.app.ports.out.InterpellationPublishQueueMessage;
import onlexnet.app.ports.out.InterpellationPublishQueuePort;
import onlexnet.app.ports.out.InterpellationPublishStatePort;
import onlexnet.app.ports.out.ProjectOwnerNotifier;
import onlexnet.app.ports.out.SejmApiClient;

class DefaultProcessInterpellationPublishUseCaseTest {

    @Test
    void givenUnpublishedMessage_whenPublishingSucceeds_thenMarksAsPublished() {
        var publisher = mock(FacebookPublisher.class);
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
                when(statePort.tryClaimForPublish(sampleMessage(1))).thenReturn(true);
        var retryPolicy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 900);
        var useCase = newUseCase(publisher, queuePort, statePort, retryPolicy);

        var outcome = useCase.process(new ProcessInterpellationPublishCommand(sampleMessage(1)));

        assertThat(outcome)
                .isEqualTo(new ProcessInterpellationPublishOutcome.Published("term-10-interpellation-42", 10, 42));
        verify(publisher).publish(any(String.class));
        verify(statePort).markPublished(eq(sampleMessage(1)), any(String.class));
        verify(queuePort, never()).enqueue(any(), any(Duration.class));
        verify(queuePort, never()).enqueueDeadLetter(any());
    }

    @Test
    void givenPublishFailureBelowMaxAttempts_whenProcessing_thenSchedulesRetry() {
        var publisher = mock(FacebookPublisher.class);
        doThrow(new IllegalStateException("fb outage")).when(publisher).publish(any(String.class));
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
                when(statePort.tryClaimForPublish(sampleMessage(2))).thenReturn(true);
        var retryPolicy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 900);
        var useCase = newUseCase(publisher, queuePort, statePort, retryPolicy);

        var outcome = useCase.process(new ProcessInterpellationPublishCommand(sampleMessage(2)));

        assertThat(outcome).isEqualTo(
                new ProcessInterpellationPublishOutcome.RetryScheduled("term-10-interpellation-42", 10, 42, 3));
        verify(queuePort).enqueue(eq(sampleMessage(2).withAttempt(3).withLastError("fb outage")), eq(Duration.ofSeconds(120)));
        verify(statePort).markRetryScheduled(eq(sampleMessage(2).withAttempt(3).withLastError("fb outage")), eq("fb outage"));
        verify(queuePort, never()).enqueueDeadLetter(any());
    }

    @Test
    void givenPublishFailureAtMaxAttempts_whenProcessing_thenSendsDeadLetter() {
        var publisher = mock(FacebookPublisher.class);
        doThrow(new IllegalStateException("permanent error")).when(publisher).publish(any(String.class));
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
                when(statePort.tryClaimForPublish(sampleMessage(5))).thenReturn(true);
        var retryPolicy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 900);
        var useCase = newUseCase(publisher, queuePort, statePort, retryPolicy);

        var outcome = useCase.process(new ProcessInterpellationPublishCommand(sampleMessage(5)));

        assertThat(outcome).isEqualTo(
                new ProcessInterpellationPublishOutcome.DeadLettered("term-10-interpellation-42", 10, 42, 5));
        verify(queuePort).enqueueDeadLetter(eq(sampleMessage(5).withLastError("permanent error")));
        verify(statePort).markDeadLetter(eq(sampleMessage(5).withLastError("permanent error")), eq("permanent error"));
        verify(queuePort, never()).enqueue(any(), any(Duration.class));
    }

    @Test
        void givenMessageClaimNotGranted_whenProcessing_thenSkipsWithoutPublish() {
        var publisher = mock(FacebookPublisher.class);
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
                when(statePort.tryClaimForPublish(sampleMessage(2))).thenReturn(false);
        var retryPolicy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 900);
        var useCase = newUseCase(publisher, queuePort, statePort, retryPolicy);

        var outcome = useCase.process(new ProcessInterpellationPublishCommand(sampleMessage(2)));

        assertThat(outcome).isEqualTo(
                new ProcessInterpellationPublishOutcome.SkippedAlreadyPublished("term-10-interpellation-42", 10, 42));
        verify(publisher, never()).publish(any(String.class));
        verify(queuePort, never()).enqueue(any(), any(Duration.class));
        verify(queuePort, never()).enqueueDeadLetter(any());
        verify(statePort, never()).markRetryScheduled(any(), any(String.class));
    }

    @Test
    void givenAttemptOne_whenFormattingPost_thenIncludesCoreInterpellationFields() {
        var publisher = mock(FacebookPublisher.class);
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
                when(statePort.tryClaimForPublish(sampleMessage(1))).thenReturn(true);
        var retryPolicy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 900);
        var useCase = newUseCase(publisher, queuePort, statePort, retryPolicy);

        useCase.process(new ProcessInterpellationPublishCommand(sampleMessage(1)));

        verify(publisher).publish(eq(
                "Interpelacja nr 42 (kadencja 10)\n"
                        + "Przykladowa interpelacja\n"
                        + "Adresaci: Minister Finansow\n"
                        + "Data zlozenia: 2026-07-01"));
    }

    @Test
    void givenAttachmentTextAvailable_whenFormattingPost_thenIncludesAttachmentSummary() {
        var publisher = mock(FacebookPublisher.class);
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var messageWithAttachment = sampleMessage(1).withAttachments(List.of(
                new AttachmentMetadata("reply-key", "Budżet", "https://example.com/budget.pdf", null, "budget.pdf")));
        when(statePort.tryClaimForPublish(messageWithAttachment)).thenReturn(true);
        when(sejmApiClient.fetchAttachmentText(10, "reply-key", "budget.pdf"))
                .thenReturn(new SejmApiClient.AttachmentFetchResult.PdfText(
                        "reply-key",
                        "budget.pdf",
                        "application/pdf",
                        "To jest bardzo ważny tekst załącznika, który powinien zostać skrócony.",
                        128));
        var retryPolicy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 900);
        var useCase = newUseCase(
                publisher,
                queuePort,
                statePort,
                retryPolicy,
                sejmApiClient,
                mock(ProjectOwnerNotifier.class));
        useCase.process(new ProcessInterpellationPublishCommand(messageWithAttachment));

        verify(publisher).publish(eq(
                "Interpelacja nr 42 (kadencja 10)\n"
                        + "Przykladowa interpelacja\n"
                        + "Adresaci: Minister Finansow\n"
                        + "Data zlozenia: 2026-07-01\n"
                        + "Skrót załącznika: To jest bardzo ważny tekst załącznika, który powinien zostać skrócony."));
    }

    @Test
    void givenUnsupportedAttachment_whenFormattingPost_thenNotifiesProjectOwnerAndSkipsAttachmentSummary() {
        var publisher = mock(FacebookPublisher.class);
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var projectOwnerNotifier = mock(ProjectOwnerNotifier.class);
        var messageWithAttachment = sampleMessage(1).withAttachments(List.of(
                new AttachmentMetadata("reply-key", "Budżet", "https://example.com/budget.doc", null, "budget.doc")));
        when(statePort.tryClaimForPublish(messageWithAttachment)).thenReturn(true);
        when(sejmApiClient.fetchAttachmentText(10, "reply-key", "budget.doc"))
                .thenReturn(new SejmApiClient.AttachmentFetchResult.Unsupported(
                        "reply-key",
                        "budget.doc",
                        "application/msword",
                        256,
                        "unsupported-attachment-type"));
        var retryPolicy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 900);
        var useCase = new DefaultProcessInterpellationPublishUseCase(
                publisher,
                queuePort,
                statePort,
                retryPolicy,
                sejmApiClient,
                projectOwnerNotifier);

        useCase.process(new ProcessInterpellationPublishCommand(messageWithAttachment));

        verify(projectOwnerNotifier).notifyOwner(any(String.class));
        verify(publisher).publish(eq(
                "Interpelacja nr 42 (kadencja 10)\n"
                        + "Przykladowa interpelacja\n"
                        + "Adresaci: Minister Finansow\n"
                        + "Data zlozenia: 2026-07-01"));
    }

    @Test
    void givenAttachmentTextAvailable_whenPublishing_thenAddsAttachmentSummaryAsCommentUnderPost() {
        var publisher = mock(FacebookPublisher.class);
        when(publisher.publish(any(String.class))).thenReturn("post-123");
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
        var sejmApiClient = mock(SejmApiClient.class);
        var messageWithAttachment = sampleMessage(1).withAttachments(List.of(
                new AttachmentMetadata("reply-key", "Budżet", "https://example.com/budget.pdf", null, "budget.pdf")));
        when(statePort.tryClaimForPublish(messageWithAttachment)).thenReturn(true);
        when(sejmApiClient.fetchAttachmentText(10, "reply-key", "budget.pdf"))
                .thenReturn(new SejmApiClient.AttachmentFetchResult.PdfText(
                        "reply-key",
                        "budget.pdf",
                        "application/pdf",
                        "To jest bardzo ważny tekst załącznika, który powinien zostać skrócony.",
                        128));
        var retryPolicy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 900);
        var useCase = newUseCase(
                publisher,
                queuePort,
                statePort,
                retryPolicy,
                sejmApiClient,
                mock(ProjectOwnerNotifier.class));

        useCase.process(new ProcessInterpellationPublishCommand(messageWithAttachment));

        verify(publisher).publishComment("post-123", "Załącznik: To jest bardzo ważny tekst załącznika, który powinien zostać skrócony.");
    }

    @Test
    void givenDuplicateDeliveries_whenOnlyFirstClaimSucceeds_thenPublishesExactlyOnce() {
        var publisher = mock(FacebookPublisher.class);
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
        var message = sampleMessage(1);
        when(statePort.tryClaimForPublish(message)).thenReturn(true, false);
        var retryPolicy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 900);
        var useCase = newUseCase(publisher, queuePort, statePort, retryPolicy);

        var first = useCase.process(new ProcessInterpellationPublishCommand(message));
        var second = useCase.process(new ProcessInterpellationPublishCommand(message));

        assertThat(first)
                .isEqualTo(new ProcessInterpellationPublishOutcome.Published("term-10-interpellation-42", 10, 42));
        assertThat(second)
                .isEqualTo(new ProcessInterpellationPublishOutcome.SkippedAlreadyPublished("term-10-interpellation-42", 10, 42));
        verify(publisher).publish(any(String.class));
    }

    @Test
    void givenPublishSucceededButMarkPublishedFails_whenProcessing_thenDoesNotScheduleRetry() {
        var publisher = mock(FacebookPublisher.class);
        var queuePort = mock(InterpellationPublishQueuePort.class);
        var statePort = mock(InterpellationPublishStatePort.class);
        var message = sampleMessage(1);
        when(statePort.tryClaimForPublish(message)).thenReturn(true);
        doThrow(new IllegalStateException("db unavailable")).when(statePort).markPublished(eq(message), any(String.class));
        var retryPolicy = new InterpellationPublishRetryPolicy(5, 60, 2.0, 900);
        var useCase = newUseCase(publisher, queuePort, statePort, retryPolicy);

        var outcome = useCase.process(new ProcessInterpellationPublishCommand(message));

        assertThat(outcome).isEqualTo(
                new ProcessInterpellationPublishOutcome.PublishConfirmationPending(
                        "term-10-interpellation-42",
                        10,
                        42,
                        "db unavailable"));
        verify(publisher).publish(any(String.class));
        verify(statePort).markPublishConfirmationPending(eq(message.withLastError("db unavailable")), eq("db unavailable"), any(String.class));
        verify(queuePort, never()).enqueue(any(), any(Duration.class));
        verify(queuePort, never()).enqueueDeadLetter(any());
    }

    private static InterpellationPublishQueueMessage sampleMessage(final int attempt) {
        return new InterpellationPublishQueueMessage(
                "term-10-interpellation-42",
                10,
                42,
                "Przykladowa interpelacja",
                List.of("Minister Finansow"),
                "2026-07-01",
                attempt,
                Instant.parse("2026-07-01T10:15:30Z"),
                null);
    }

        private static DefaultProcessInterpellationPublishUseCase newUseCase(
                        final FacebookPublisher publisher,
                        final InterpellationPublishQueuePort queuePort,
                        final InterpellationPublishStatePort statePort,
                        final InterpellationPublishRetryPolicy retryPolicy) {
                return newUseCase(
                                publisher,
                                queuePort,
                                statePort,
                                retryPolicy,
                                mock(SejmApiClient.class),
                                mock(ProjectOwnerNotifier.class));
        }

        private static DefaultProcessInterpellationPublishUseCase newUseCase(
                        final FacebookPublisher publisher,
                        final InterpellationPublishQueuePort queuePort,
                        final InterpellationPublishStatePort statePort,
                        final InterpellationPublishRetryPolicy retryPolicy,
                        final SejmApiClient sejmApiClient,
                        final ProjectOwnerNotifier projectOwnerNotifier) {
                return new DefaultProcessInterpellationPublishUseCase(
                                publisher,
                                queuePort,
                                statePort,
                                retryPolicy,
                                sejmApiClient,
                                projectOwnerNotifier);
        }
}
