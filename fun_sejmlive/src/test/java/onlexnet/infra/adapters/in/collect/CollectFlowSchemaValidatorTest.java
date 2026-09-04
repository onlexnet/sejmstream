package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gson.Gson;
import com.microsoft.durabletask.JacksonDataConverter;

import onlexnet.infra.adapters.in.azurefunc.collectorchestrator.CollectActivityResultWire;
import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResult;

class JsonValidatorTest {

	@Test
	void givenValidActivityPayloads_whenValidated_thenPasses() {
		var validator = newValidator();
		var result = new CollectActivityResult();
		result.setCount(3);
		result.setTermNum(10);
		result.setCollectionDate(java.time.LocalDate.of(2026, 8, 27));

		assertThatCode(() -> validator.validateReceived(JsonValidator.COLLECT_ACTIVITY_REQUEST, new CollectActivityRequest()))
				.doesNotThrowAnyException();
		assertThatCode(() -> validator.validateToSend(JsonValidator.COLLECT_ACTIVITY_RESULT, result))
				.doesNotThrowAnyException();
	}

	@Test
	void givenMissingRequiredField_whenValidated_thenFails() {
		var validator = newValidator();

		assertThatThrownBy(() -> validator.validateToSend(JsonValidator.COLLECT_ACTIVITY_RESULT, new CollectActivityResult()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("collect-activity-result.schema.json");
	}

	@Test
	void shouldRoundTripActivityWirePayloadBetweenGsonAndDurableJackson() {
		var wirePayload = new CollectActivityResultWire(
				3,
				10,
				"2026-08-27",
				java.util.List.of("item-1"),
				java.util.Map.of("item-1", "fingerprint"));

		var serializedByFunctionWorker = new Gson().toJson(wirePayload);
		var restoredByDurable = new JacksonDataConverter().deserialize(
				serializedByFunctionWorker,
				CollectActivityResultWire.class);

		assertThat(serializedByFunctionWorker).contains("\"collectionDate\":\"2026-08-27\"");
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