package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import onlexnet.app.ports.out.SejmDailyDigestPersistence;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestratorEventV1DTO;

class TermSnapshotCollectedEventMaterializerTest {

    @Test
    void givenCollectEventAndPersistedRows_whenMaterializing_thenBuildsEnrichedSnapshotEvent() {
        var persistence = mock(SejmDailyDigestPersistence.class);
        var collectionDate = LocalDate.of(2026, 9, 7);
        var event = new CollectOrchestratorEventV1DTO()
                .orchestrationInstanceId("collect-instance-1")
                .source("timer")
                .termNum(10)
                .collectionDate(20260907)
                .countsByType(Map.of("INTERPELLATION", 2));

        List<Map<String, Object>> interpellationRows = List.of(
                new TreeMap<>(Map.of(
                        "item_key", "78",
                        "item_title", "B",
                        "item_json", "{\"num\":78,\"title\":\"B\",\"links\":{\"webDescription\":\"https://sejm.example/78\"}}")),
                new TreeMap<>(Map.of(
                        "item_key", "77",
                        "item_title", "A",
                        "item_json", "{\"num\":77,\"title\":\"A\",\"links\":{\"webDescription\":\"https://sejm.example/77\"}}")));
        List<Map<String, Object>> questionRows = List.of(
                new TreeMap<>(Map.of("item_key", "302")),
                new TreeMap<>(Map.of("item_key", "301")));
        List<Map<String, Object>> printRows = List.of(
                new TreeMap<>(Map.of("item_key", "402")),
                new TreeMap<>(Map.of("item_key", "401")));
        List<Map<String, Object>> billRows = List.of(
                new TreeMap<>(Map.of("item_key", "502")),
                new TreeMap<>(Map.of("item_key", "501")));

        when(persistence.findByDateAndType(eq(collectionDate), eq("INTERPELLATION"))).thenReturn(interpellationRows);
        when(persistence.findByDateAndType(eq(collectionDate), eq("WRITTEN_QUESTION"))).thenReturn(questionRows);
        when(persistence.findByDateAndType(eq(collectionDate), eq("PRINT"))).thenReturn(printRows);
        when(persistence.findByDateAndType(eq(collectionDate), eq("BILL"))).thenReturn(billRows);

        var materializer = new TermSnapshotCollectedEventMaterializer(persistence);

        var snapshotEvent = materializer.materialize(event);

        assertThat(snapshotEvent.collectionDate()).isEqualTo(20260907);
        assertThat(snapshotEvent.source()).isEqualTo("timer");
        assertThat(snapshotEvent.orchestrationInstanceId()).isEqualTo("collect-instance-1");
        assertThat(snapshotEvent.interpellationFingerprints())
                .containsEntry("77", sha256Hex("{\"num\":77,\"title\":\"A\",\"links\":{\"webDescription\":\"https://sejm.example/77\"}}"))
                .containsEntry("78", sha256Hex("{\"num\":78,\"title\":\"B\",\"links\":{\"webDescription\":\"https://sejm.example/78\"}}"));
        assertThat(snapshotEvent.interpellationPresentation())
                .containsEntry(
                        "77",
                        new TermSnapshotCollectedEvent.InterpellationPresentation("A", "https://sejm.example/77"))
                .containsEntry(
                        "78",
                        new TermSnapshotCollectedEvent.InterpellationPresentation("B", "https://sejm.example/78"));
        assertThat(snapshotEvent.writtenQuestionKeys()).containsExactly("301", "302");
        assertThat(snapshotEvent.printKeys()).containsExactly("401", "402");
        assertThat(snapshotEvent.billKeys()).containsExactly("501", "502");

        verify(persistence, times(1)).findByDateAndType(collectionDate, "INTERPELLATION");
        verify(persistence, times(1)).findByDateAndType(collectionDate, "WRITTEN_QUESTION");
        verify(persistence, times(1)).findByDateAndType(collectionDate, "PRINT");
        verify(persistence, times(1)).findByDateAndType(collectionDate, "BILL");
    }

    @Test
    void givenInterpellationRowWithoutItemJson_whenMaterializing_thenFailsFast() {
        var persistence = mock(SejmDailyDigestPersistence.class);
        var collectionDate = LocalDate.of(2026, 9, 7);
        var event = new CollectOrchestratorEventV1DTO()
                .orchestrationInstanceId("collect-instance-2")
                .source("http")
                .termNum(10)
                .collectionDate(20260907)
                .countsByType(Map.of());

        when(persistence.findByDateAndType(eq(collectionDate), eq("INTERPELLATION")))
                .thenReturn(List.of(new TreeMap<>(Map.of("item_key", "77"))));

        var materializer = new TermSnapshotCollectedEventMaterializer(persistence);

        assertThatThrownBy(() -> materializer.materialize(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required column 'item_json'");
    }

    private static String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            var hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
