package onlexnet.infra.adapters.in.facebook;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import onlexnet.app.ports.in.publish.PublishDailyDigestCommand;
import onlexnet.app.ports.in.publish.PublishDailyDigestOutcome;
import onlexnet.app.ports.in.publish.PublishDailyDigestUseCase;

/**
 * Publishes daily Sejm digests to social media on a scheduled basis.
 */
@Component
public final class FacebookPublishingFunctions {

    static final String TIMER_FUNCTION_NAME = "Fun_FacebookPublish";
    static final String HTTP_FUNCTION_NAME = "Fun_FacebookPublishStart";
    static final String HTTP_FUNCTION_ROUTE = "Fun_FacebookPublishStart";

    private final PublishDailyDigestUseCase publishDailyDigestUseCase;

    public FacebookPublishingFunctions(final PublishDailyDigestUseCase publishDailyDigestUseCase) {
        this.publishDailyDigestUseCase = publishDailyDigestUseCase;
    }

    /**
     * Publishes today's Sejm digest to Facebook.
     */
    @FunctionName(TIMER_FUNCTION_NAME)
    public void publishDailyDigest(
            @TimerTrigger(name = "timer", schedule = "0 30 23 * * *")
            final String timerInfo,
            final ExecutionContext executionContext) {
        var outcome = this.publishDailyDigestUseCase
                .publish(new PublishDailyDigestCommand(LocalDate.now()));
        this.logOutcome(outcome, timerInfo, executionContext);
        if (outcome instanceof PublishDailyDigestOutcome.Failed failed) {
            throw failed.exception();
        }
    }

    /**
     * HTTP trigger for manually publishing today's Sejm digest to Facebook.
     */
    @FunctionName(HTTP_FUNCTION_NAME)
    public HttpResponseMessage publishDailyDigestHttp(
            @HttpTrigger(name = "request", methods = {HttpMethod.POST},
                    authLevel = AuthorizationLevel.FUNCTION,
                    route = HTTP_FUNCTION_ROUTE)
            final HttpRequestMessage<Optional<String>> request,
            final ExecutionContext executionContext) {
        try {
            var outcome = this.publishDailyDigestUseCase
                    .publish(new PublishDailyDigestCommand(LocalDate.now()));
            this.logOutcome(outcome, "http", executionContext);
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
            final ExecutionContext executionContext) {
        switch (outcome) {
            case PublishDailyDigestOutcome.Published published -> executionContext.getLogger().info(
                    "Publikowanie podsumowania Sejmu. Trigger: " + triggerInfo
                            + ", wiadomosc: " + published.message());
            case PublishDailyDigestOutcome.SkippedAlreadyPublished skipped -> executionContext.getLogger().info(
                    "Pomijanie publikacji - wpis dla dnia " + skipped.date() + " juz istnieje.");
            case PublishDailyDigestOutcome.SkippedNoDigest skipped -> executionContext.getLogger().info(
                    "Brak aktywnosci sejmowej dla dnia " + skipped.date() + ", pomijanie publikacji.");
            case PublishDailyDigestOutcome.Failed failed -> executionContext.getLogger().severe(
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