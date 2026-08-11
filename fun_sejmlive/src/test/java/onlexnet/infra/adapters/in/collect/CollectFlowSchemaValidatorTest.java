package onlexnet.infra.adapters.in.collect;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import onlexnet.infra.adapters.in.azurefunc.JsonValidator;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResult;

class JsonValidatorTest {

	@Test
	void givenValidActivityPayloads_whenValidated_thenPasses() {
		var validator = newValidator();
		var result = new CollectActivityResult();
		result.setCount(3);

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

	private static JsonValidator newValidator() {
		var validator = new JsonValidator(new ObjectMapper().findAndRegisterModules());
		validator.init();
		return validator;
	}
}