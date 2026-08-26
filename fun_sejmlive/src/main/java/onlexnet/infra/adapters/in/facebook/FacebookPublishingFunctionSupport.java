package onlexnet.infra.adapters.in.facebook;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import lombok.RequiredArgsConstructor;
import onlexnet.app.ports.in.publish.PublishDailyDigestCommand;
import onlexnet.app.ports.in.publish.PublishDailyDigestOutcome;
import onlexnet.app.ports.in.publish.PublishDailyDigestUseCase;
import onlexnet.infra.adapters.in.azurefunc.Log;

/**
 * Shared behavior for the Facebook publishing timer and HTTP trigger functions.
 */
@Component
@RequiredArgsConstructor
public final class FacebookPublishingFunctionSupport {

    private final PublishDailyDigestUseCase publishDailyDigestUseCase;

    /**
     * Publishes today's Sejm digest to Facebook, rethrowing on failure.
     */
    public void publishDailyDigest(final String triggerInfo, final ExecutionContext execCtx) {
        var outcome = this.publishDailyDigestUseCase
                .publish(new PublishDailyDigestCommand(LocalDate.now()));
        this.logOutcome(outcome, triggerInfo, execCtx);
        if (outcome instanceof PublishDailyDigestOutcome.Failed failed) {
            throw failed.exception();
        }
    }

    /**
     * HTTP trigger behavior for manually publishing today's Sejm digest to Facebook.
     */
    public HttpResponseMessage publishDailyDigestHttp(
            final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext execCtx) {
        try {
            var outcome = this.publishDailyDigestUseCase
                    .publish(new PublishDailyDigestCommand(LocalDate.now()));
            this.logOutcome(outcome, "http", execCtx);
            return switch (outcome) {
                case PublishDailyDigestOutcome.Published _ -> request.createResponseBuilder(HttpStatus.OK)
                        .body(Map.of(
                                "status", "PUBLISHED",
                                "message", "Published daily digest to Facebook."))
                        .build();
                case PublishDailyDigestOutcome.SkippedAlreadyPublished _ ->
                    request.createResponseBuilder(HttpStatus.OK)
                        .body(Map.of(
                                "status", "SKIPPED_ALREADY_PUBLISHED",
                                "message", "Digest for today was already published."))
                        .build();
                case PublishDailyDigestOutcome.SkippedNoDigest _ -> request.createResponseBuilder(HttpStatus.OK)
                        .body(Map.of(
                                "status", "SKIPPED_NO_DIGEST",
                                "message", "No digest data available for today."))
                        .build();
                case PublishDailyDigestOutcome.Failed failed -> request
                        .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to publish daily digest: " + this.safeErrorMessage(failed.exception()))
                        .build();
            };
        } catch (RuntimeException exception) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to publish daily digest: " + this.safeErrorMessage(exception))
                    .build();
        }
    }

    private void logOutcome(
            final PublishDailyDigestOutcome outcome,
            final String triggerInfo,
            final ExecutionContext execCtx) {
        switch (outcome) {
            case PublishDailyDigestOutcome.Published published -> Log.info(execCtx,
                    "Publikowanie podsumowania Sejmu. Trigger: " + triggerInfo
                            + ", wiadomosc: " + published.message());
            case PublishDailyDigestOutcome.SkippedAlreadyPublished skipped -> Log.info(execCtx,
                    "Pomijanie publikacji - wpis dla dnia " + skipped.date() + " juz istnieje.");
            case PublishDailyDigestOutcome.SkippedNoDigest skipped -> Log.info(execCtx,
                    "Brak aktywnosci sejmowej dla dnia " + skipped.date() + ", pomijanie publikacji.");
            case PublishDailyDigestOutcome.Failed failed -> execCtx.getLogger().severe(
                    "Nieudana publikacja podsumowania Sejmu. Trigger: " + triggerInfo
                            + ", blad: " + this.safeErrorMessage(failed.exception()));
        }
    }

    private String safeErrorMessage(final RuntimeException exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Unknown error";
        }
        return message;
    }
}
