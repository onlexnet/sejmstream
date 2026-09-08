package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.Gson;
import com.microsoft.durabletask.JacksonDataConverter;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;

import onlexnet.infra.adapters.in.azurefunc.collectorchestrator.CollectActivityResultWire;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequestDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectEventPublishRequestDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResultDTO;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestratorEventV1DTO;

class CollectFlowSchemaValidatorTest {

	@Test
	void givenValidActivityPayloads_whenValidated_thenPasses() {
		var validator = newValidator();
		var result = new CollectActivityResultDTO();
		result.setCount(3);
		result.setTermNum(10);
		result.setCollectionDate(20260827);

		assertThatCode(() -> validator.validateReceived(JsonValidator.COLLECT_ACTIVITY_REQUEST, new CollectActivityRequestDTO()))
				.doesNotThrowAnyException();
		assertThatCode(() -> validator.validateToSend(JsonValidator.COLLECT_ACTIVITY_RESULT, result))
				.doesNotThrowAnyException();

		var publishRequest = new CollectEventPublishRequestDTO();
		publishRequest.setOrchestrationInstanceId("collect-instance-1");
		publishRequest.setSource("timer");
		publishRequest.setTermNum(10);
		publishRequest.setCollectionDate(20260907);
		publishRequest.setCountsByType(java.util.Map.of("VOTING", 1));
		assertThatCode(() -> validator.validateToSend(JsonValidator.COLLECT_EVENT_PUBLISH_REQUEST, publishRequest))
				.doesNotThrowAnyException();

		var outboundEvent = new CollectOrchestratorEventV1DTO()
				.orchestrationInstanceId("collect-instance-1")
				.source("timer")
				.termNum(10)
				.collectionDate(20260907)
				.countsByType(java.util.Map.of("VOTING", 1));
		assertThatCode(() -> validator.validateToSend(JsonValidator.COLLECT_ORCHESTRATOR_EVENT_V1, outboundEvent))
				.doesNotThrowAnyException();
	}

	@Test
	void givenMissingRequiredField_whenValidated_thenFails() {
		var validator = newValidator();

		assertThatThrownBy(() -> validator.validateToSend(JsonValidator.COLLECT_ACTIVITY_RESULT, new CollectActivityResultDTO()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("collect-activity-result.schema.json");

		assertThatThrownBy(() -> validator.validateToSend(
				JsonValidator.COLLECT_EVENT_PUBLISH_REQUEST,
				new CollectEventPublishRequestDTO()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("collect-event-publish-request.schema.json");

		assertThatThrownBy(() -> validator.validateToSend(
				JsonValidator.COLLECT_ORCHESTRATOR_EVENT_V1,
				new CollectOrchestratorEventV1DTO()
						.source("timer")
						.termNum(10)
						.collectionDate(20260907)
						.countsByType(java.util.Map.of("VOTING", 1))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("collect-orchestrator-event-v1.schema.json");

		assertThatThrownBy(() -> validator.validateToSend(
				JsonValidator.COLLECT_ORCHESTRATOR_EVENT_V1,
				new CollectOrchestratorEventV1DTO()
						.orchestrationInstanceId("collect-instance-1")
						.source("timer")
						.termNum(10)
						.collectionDate(20260907)
						.countsByType(java.util.Map.of("VOTING", -1))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("collect-orchestrator-event-v1.schema.json");
	}

	@Test
	void givenLegacyIsoCollectionDateString_whenSchemaValidates_thenFails() throws Exception {
		var objectMapper = new ObjectMapper();
		var payload = objectMapper.readTree("""
				{
				  "orchestrationInstanceId": "collect-instance-1",
				  "source": "timer",
				  "termNum": 10,
				  "collectionDate": "2026-09-07",
				  "countsByType": {
				    "VOTING": 1
				  }
				}
				""");
		var schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
		try (var schemaStream = CollectFlowSchemaValidatorTest.class.getResourceAsStream(
				"/schemajson/collect-flow/collect-event-publish-request.schema.json")) {
			assertThat(schemaStream).isNotNull();
			var schema = schemaFactory.getSchema(schemaStream);
			var errors = schema.validate(payload);

			assertThat(errors).isNotEmpty();
			assertThat(errors)
					.anyMatch(message -> message.getMessage().contains("integer"));
		}
	}

	@Test
	void shouldRoundTripActivityWirePayloadBetweenGsonAndDurableJackson() {
		var wirePayload = new CollectActivityResultWire(
				3,
				10,
				20260827,
				java.util.List.of("item-1"),
				java.util.Map.of("item-1", "fingerprint"));

		var serializedByFunctionWorker = new Gson().toJson(wirePayload);
		var restoredByDurable = new JacksonDataConverter().deserialize(
				serializedByFunctionWorker,
				CollectActivityResultWire.class);

		assertThat(serializedByFunctionWorker).contains("\"collectionDate\":20260827");
		assertThat(restoredByDurable).isEqualTo(wirePayload);
	}

	private static JsonValidator newValidator() {
		var objectMapper = new ObjectMapper().findAndRegisterModules();
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		var validator = new JsonValidator(objectMapper);
		validator.init();
		return validator;
	}
}