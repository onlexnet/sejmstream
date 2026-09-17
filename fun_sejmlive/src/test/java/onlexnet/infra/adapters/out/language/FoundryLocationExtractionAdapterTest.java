package onlexnet.infra.adapters.out.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import onlexnet.app.ports.out.LocationCandidate;

class FoundryLocationExtractionAdapterTest {

    private static final String ENDPOINT = "https://example-foundry.openai.azure.com";
    private static final String DEPLOYMENT = "location-extractor";
    private static final String CHAT_COMPLETIONS_PATH =
            ENDPOINT + "/openai/deployments/" + DEPLOYMENT + "/chat/completions?api-version=2024-02-15-preview";

    @Test
    void givenBlankEndpointKeyOrDeployment_whenExtracting_thenReturnsEmptyWithoutCallingApi() {
        var adapter = new FoundryLocationExtractionAdapter("", "key", "deployment");

        assertThat(adapter.extractLocations("Interpelacja dotyczy Białegostoku.")).isEmpty();
    }

    @Test
    void givenBlankText_whenExtracting_thenReturnsEmpty() {
        var restClient = RestClient.builder().baseUrl(ENDPOINT).build();
        var adapter = new FoundryLocationExtractionAdapter(restClient, DEPLOYMENT);

        assertThat(adapter.extractLocations(" ")).isEmpty();
    }

    @Test
    void givenModelReturnsLocationsJson_whenExtracting_thenParsesAndFiltersLowConfidenceCandidates() {
        var restClientBuilder = RestClient.builder().baseUrl(ENDPOINT);
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var restClient = restClientBuilder.build();
        var adapter = new FoundryLocationExtractionAdapter(restClient, DEPLOYMENT);

        server.expect(requestTo(CHAT_COMPLETIONS_PATH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "[{\\"rawMention\\":\\"Białymstoku\\",\\"canonicalName\\":\\"Białystok\\",\\"province\\":\\"podlaskie\\",\\"county\\":null,\\"latitude\\":null,\\"longitude\\":null,\\"confidence\\":0.98},{\\"rawMention\\":\\"jakims miejscem\\",\\"canonicalName\\":\\"Nieznane\\",\\"province\\":null,\\"county\\":null,\\"latitude\\":null,\\"longitude\\":null,\\"confidence\\":0.2}]"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.extractLocations("W ubiegłym roku interpelacja dotyczyła problemów w Białymstoku.");

        assertThat(result).containsExactly(
                new LocationCandidate("Białymstoku", "Białystok", "podlaskie", null, null, null, 0.98));
        server.verify();
    }

    @Test
    void givenModelReturnsEmptyArray_whenExtracting_thenReturnsEmptyList() {
        var restClientBuilder = RestClient.builder().baseUrl(ENDPOINT);
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var restClient = restClientBuilder.build();
        var adapter = new FoundryLocationExtractionAdapter(restClient, DEPLOYMENT);

        server.expect(requestTo(CHAT_COMPLETIONS_PATH))
                .andRespond(withSuccess("""
                        { "choices": [ { "message": { "content": "[]" } } ] }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.extractLocations("Tekst bez miejscowości.")).isEmpty();
    }

    @Test
    void givenApiCallFails_whenExtracting_thenReturnsEmptyInsteadOfThrowing() {
        var restClientBuilder = RestClient.builder().baseUrl(ENDPOINT);
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var restClient = restClientBuilder.build();
        var adapter = new FoundryLocationExtractionAdapter(restClient, DEPLOYMENT);

        server.expect(requestTo(CHAT_COMPLETIONS_PATH)).andRespond(withServerError());

        assertThat(adapter.extractLocations("Tekst interpelacji.")).isEmpty();
    }

    @Test
    void givenModelReturnsInvalidJson_whenExtracting_thenReturnsEmptyInsteadOfThrowing() {
        var restClientBuilder = RestClient.builder().baseUrl(ENDPOINT);
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var restClient = restClientBuilder.build();
        var adapter = new FoundryLocationExtractionAdapter(restClient, DEPLOYMENT);

        server.expect(requestTo(CHAT_COMPLETIONS_PATH))
                .andRespond(withSuccess("""
                        { "choices": [ { "message": { "content": "not json" } } ] }
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.extractLocations("Tekst interpelacji.")).isEmpty();
    }
}
