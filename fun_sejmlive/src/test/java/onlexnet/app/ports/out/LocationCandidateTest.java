package onlexnet.app.ports.out;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class LocationCandidateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void givenLocationCandidateWithAllFields_whenRoundTrippedThroughJson_thenPreservesAllFields() throws Exception {
        var candidate = new LocationCandidate("Białymstoku", "Białystok", "podlaskie", "białostocki", 53.13, 23.15, 0.98);

        var json = this.objectMapper.writeValueAsString(candidate);
        var deserialized = this.objectMapper.readValue(json, LocationCandidate.class);

        assertThat(deserialized).isEqualTo(candidate);
    }

    @Test
    void givenLocationCandidateWithNullableFieldsAbsent_whenRoundTrippedThroughJson_thenPreservesNulls() throws Exception {
        var candidate = new LocationCandidate("Gdańsku", "Gdańsk", null, null, null, null, 0.9);

        var json = this.objectMapper.writeValueAsString(candidate);
        var deserialized = this.objectMapper.readValue(json, LocationCandidate.class);

        assertThat(deserialized).isEqualTo(candidate);
    }
}
