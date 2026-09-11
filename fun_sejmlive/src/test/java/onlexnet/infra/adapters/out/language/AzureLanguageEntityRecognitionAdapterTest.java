package onlexnet.infra.adapters.out.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import onlexnet.app.ports.out.RecognizedEntities;

class AzureLanguageEntityRecognitionAdapterTest {

    private static final String ENDPOINT = "https://example-language.cognitiveservices.azure.com";

    @Test
    void givenBlankEndpointOrKey_whenRecognizing_thenReturnsEmptyWithoutCallingApi() {
        var adapter = new AzureLanguageEntityRecognitionAdapter("", "key");

        var result = adapter.recognize("Pan Jan Kowalski z Warszawy odwiedził PKN Orlen.");

        assertThat(result).isEqualTo(RecognizedEntities.empty());
    }

    @Test
    void givenBlankText_whenRecognizing_thenReturnsEmpty() {
        var restClient = RestClient.builder().baseUrl(ENDPOINT).build();
        var adapter = new AzureLanguageEntityRecognitionAdapter(restClient);

        assertThat(adapter.recognize(" ")).isEqualTo(RecognizedEntities.empty());
    }

    @Test
    void givenRecognizedAndLinkedEntities_whenRecognizing_thenGroupsByCategoryWithCanonicalNames() {
        var restClientBuilder = RestClient.builder().baseUrl(ENDPOINT);
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var restClient = restClientBuilder.build();
        var adapter = new AzureLanguageEntityRecognitionAdapter(restClient);

        server.expect(requestTo(ENDPOINT + "/language/:analyze-text?api-version=2023-04-01"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(jsonPath("$.kind").value("EntityRecognition"))
                .andRespond(withSuccess("""
                        {
                          "kind": "EntityRecognitionResults",
                          "results": {
                            "documents": [
                              {
                                "id": "1",
                                "entities": [
                                  { "text": "Jan Kowalski", "category": "Person" },
                                  { "text": "Warszawy", "category": "Location" },
                                  { "text": "wczoraj", "category": "DateTime" }
                                ]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        server.expect(requestTo(ENDPOINT + "/language/:analyze-text?api-version=2023-04-01"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.kind").value("EntityLinking"))
                .andRespond(withSuccess("""
                        {
                          "kind": "EntityLinkingResults",
                          "results": {
                            "documents": [
                              {
                                "id": "1",
                                "entities": [
                                  { "name": "Warszawa", "matches": [ { "text": "Warszawy" } ] }
                                ]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = adapter.recognize("Jan Kowalski przyjechał do Warszawy wczoraj.");

        assertThat(result.persons()).extracting("mentionText", "canonicalName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Jan Kowalski", null));
        assertThat(result.locations()).extracting("mentionText", "canonicalName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Warszawy", "Warszawa"));
        assertThat(result.organizations()).isEmpty();
        server.verify();
    }

    @Test
    void givenApiCallFails_whenRecognizing_thenReturnsEmptyInsteadOfThrowing() {
        var restClientBuilder = RestClient.builder().baseUrl(ENDPOINT);
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var restClient = restClientBuilder.build();
        var adapter = new AzureLanguageEntityRecognitionAdapter(restClient);

        server.expect(requestTo(ENDPOINT + "/language/:analyze-text?api-version=2023-04-01"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        var result = adapter.recognize("Tekst interpelacji.");

        assertThat(result).isEqualTo(RecognizedEntities.empty());
    }
}
