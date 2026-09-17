package onlexnet.infra.adapters.in.azurefunc.termsnapshotreconciler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import onlexnet.app.ports.out.LocationCandidate;
import onlexnet.app.ports.out.RecognizedEntities;
import onlexnet.app.ports.out.RecognizedEntity;

class InterpellationEntitySummaryPresenterTest {

    private final InterpellationEntitySummaryPresenter presenter = new InterpellationEntitySummaryPresenter();

    @Test
    void givenNoEntities_whenPresenting_thenReturnsBlankSummary() {
        var result = this.presenter.present(RecognizedEntities.empty());

        assertThat(result).isBlank();
    }

    @Test
    void givenEntitiesInEachCategory_whenPresenting_thenRendersLabeledLines() {
        var entities = new RecognizedEntities(
                List.of(new RecognizedEntity("Jan Kowalski", null)),
                List.of(new RecognizedEntity("PKN Orlen", "Orlen S.A.")),
                List.of(new RecognizedEntity("Warszawy", "Warszawa")));

        var result = this.presenter.present(entities);

        assertThat(result).isEqualTo(
                "  Osoby: Jan Kowalski\n"
                        + "  Firmy: Orlen S.A.\n"
                        + "  Miejscowości: Warszawa");
    }

    @Test
    void givenDuplicateMentionsOfSameCanonicalName_whenPresenting_thenDeduplicates() {
        var entities = new RecognizedEntities(
                List.of(
                        new RecognizedEntity("Kowalski", "Jan Kowalski"),
                        new RecognizedEntity("Jan Kowalski", "Jan Kowalski")),
                List.of(),
                List.of());

        var result = this.presenter.present(entities);

        assertThat(result).isEqualTo("  Osoby: Jan Kowalski");
    }

    @Test
    void givenUnlinkedMentionsDifferingOnlyByCaseOrDiacritics_whenPresenting_thenNormalizesAndDeduplicates() {
        var entities = new RecognizedEntities(
                List.of(),
                List.of(),
                List.of(
                        new RecognizedEntity("Warszawa", null),
                        new RecognizedEntity("warszawa", null),
                        new RecognizedEntity("WARSZAWA", null)));

        var result = this.presenter.present(entities);

        assertThat(result).isEqualTo("  Miejscowości: Warszawa");
    }

    @Test
    void givenOnlyBlankMentions_whenPresenting_thenSkipsCategory() {
        var entities = new RecognizedEntities(
                List.of(new RecognizedEntity("", null)),
                List.of(),
                List.of());

        var result = this.presenter.present(entities);

        assertThat(result).isBlank();
    }

    @Test
    void givenLocationCandidates_whenPresenting_thenRendersCanonicalNameWithProvince() {
        var entities = new RecognizedEntities(List.of(), List.of(),
                List.of(new RecognizedEntity("Białegostoku", "Białystok")));
        var locations = List.of(
                new LocationCandidate("Białegostoku", "Białystok", "podlaskie", null, null, null, 0.95),
                new LocationCandidate("Gdańsku", "Gdańsk", null, null, null, null, 0.9));

        var result = this.presenter.present(entities, locations);

        assertThat(result).isEqualTo("  Miejscowości: Białystok (podlaskie), Gdańsk");
    }

    @Test
    void givenDuplicateLocationCandidatesForSameCanonicalName_whenPresenting_thenDeduplicates() {
        var entities = RecognizedEntities.empty();
        var locations = List.of(
                new LocationCandidate("Białegostoku", "Białystok", "podlaskie", null, null, null, 0.95),
                new LocationCandidate("Białystok", "Białystok", "podlaskie", null, null, null, 0.9));

        var result = this.presenter.present(entities, locations);

        assertThat(result).isEqualTo("  Miejscowości: Białystok (podlaskie)");
    }

    @Test
    void givenNoLocationCandidates_whenPresenting_thenFallsBackToRecognizedEntityLocations() {
        var entities = new RecognizedEntities(List.of(), List.of(),
                List.of(new RecognizedEntity("Warszawy", "Warszawa")));

        var result = this.presenter.present(entities, List.of());

        assertThat(result).isEqualTo("  Miejscowości: Warszawa");
    }
}
