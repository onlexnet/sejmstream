package onlexnet.sejmapi;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

@Component
public final class FacebookPublishingFunctions {

    private static final String FUNCTION_NAME = "SejmApiDemo_FacebookPublish";

    private final FacebookPublisher facebookPublisher;
    private final SejmApiClient sejmApiClient;

    public FacebookPublishingFunctions() {
        this(new DefaultFacebookPublisher(), new DefaultSejmApiClient());
    }

    FacebookPublishingFunctions(final FacebookPublisher facebookPublisher) {
        this(facebookPublisher, new DefaultSejmApiClient());
    }

    @Autowired
    FacebookPublishingFunctions(final FacebookPublisher facebookPublisher,
            final SejmApiClient sejmApiClient) {
        this.facebookPublisher = facebookPublisher;
        this.sejmApiClient = sejmApiClient;
    }

    @FunctionName(FUNCTION_NAME)
    public void publishHelloMessage(
            @TimerTrigger(name = "timer", schedule = "0 0 6 * * *")
            final String timerInfo,
            final ExecutionContext executionContext) {

        final List<SejmTerm> terms = this.sejmApiClient.fetchSimpleData("sejm/term");
        final String message = buildSummaryMessage(terms);

        executionContext.getLogger().info(
                "Publikowanie podsumowania Sejmu o 6:00. Trigger: " + timerInfo
                        + ", wiadomość: " + message);
        this.facebookPublisher.publish(message);
    }

    private String buildSummaryMessage(final List<SejmTerm> terms) {
        if (terms == null || terms.isEmpty()) {
            return "Brak danych z Sejmu do publikacji.";
        }

        final var currentTerm = terms.stream()
                .filter(SejmTerm::current)
                .findFirst()
                .orElse(terms.get(0));

        return String.format(
                "Sejm API: %d kadencji, aktualna kadencja to %d (od %s do %s).",
                terms.size(),
                currentTerm.num(),
                Objects.toString(currentTerm.from(), "brak daty początku"),
                Objects.toString(currentTerm.to(), "brak daty końca"));
    }
}
