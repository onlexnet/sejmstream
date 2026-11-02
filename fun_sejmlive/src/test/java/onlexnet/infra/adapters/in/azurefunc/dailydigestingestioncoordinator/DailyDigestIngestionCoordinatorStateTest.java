package onlexnet.infra.adapters.in.azurefunc.dailydigestingestioncoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.durabletask.JacksonDataConverter;

import onlexnet.app.usecases.DailyDigestIngestionState;

class DailyDigestIngestionCoordinatorStateTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final JacksonDataConverter converter = new JacksonDataConverter();

    @ParameterizedTest
    @MethodSource("lifecycles")
    void shouldRoundTripEveryLifecycleThroughDurableConverter(DailyDigestIngestionState lifecycle) {
        var state = DailyDigestIngestionCoordinatorState.of(lifecycle);

        assertThat(converter.deserialize(converter.serialize(state), DailyDigestIngestionCoordinatorState.class))
                .isEqualTo(state);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRejectEitherLegacyRunningFlag(boolean running) {
        var json = "{\"running\":" + running + "}";
        assertThatThrownBy(() -> mapper.readValue(json, DailyDigestIngestionCoordinatorState.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class)
                .hasMessageContaining("legacy state is unsupported");
        assertThatThrownBy(() -> converter.deserialize(json, DailyDigestIngestionCoordinatorState.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("legacy state is unsupported");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"version\":2,\"lifecycle\":{\"type\":\"idle\"}}",
            "{\"version\":1,\"lifecycle\":{\"type\":\"owned\",\"instanceId\":\"\",\"attempt\":0}}",
            "{\"version\":1,\"lifecycle\":{\"type\":\"unknown\"}}"
    })
    void shouldRejectInvalidStateInsteadOfAssumingIdle(String json) {
        assertThatThrownBy(() -> mapper.readValue(json, DailyDigestIngestionCoordinatorState.class))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    private static Stream<DailyDigestIngestionState> lifecycles() {
        return Stream.of(
                new DailyDigestIngestionState.Idle(),
                new DailyDigestIngestionState.Owned("run-1", 3, "timer", Instant.parse("2026-10-27T22:00:00Z"), true),
                new DailyDigestIngestionState.Exhausted("run-5"),
                new DailyDigestIngestionState.RecoveryRequired(
                        DailyDigestIngestionState.Reason.ADMINISTRATIVE_STOP, "run-stopped"),
                new DailyDigestIngestionState.RecoveryRequired(DailyDigestIngestionState.Reason.LEGACY_OWNERSHIP, ""));
    }
}
