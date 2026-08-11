package onlexnet.infra.adapters.in.azurefunc;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityRequest;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectActivityResult;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectCompletion;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectFailure;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectOrchestrationInput;
import onlexnet.infra.adapters.in.azurefunc.generated.model.CollectResult;

/**
 * Validates collect-flow transport DTOs against their source JSON schemas.
 */
@Component
@NullMarked
@RequiredArgsConstructor
public final class JsonValidator {

    public static final SchemaRef<CollectActivityRequest> COLLECT_ACTIVITY_REQUEST = new SchemaRef<>(
            CollectActivityRequest.class, "/schemajson/collect-flow/collect-activity-request.schema.json");
    public static final SchemaRef<CollectActivityResult> COLLECT_ACTIVITY_RESULT = new SchemaRef<>(
            CollectActivityResult.class, "/schemajson/collect-flow/collect-activity-result.schema.json");
    public static final SchemaRef<CollectOrchestrationInput> COLLECT_ORCHESTRATION_INPUT = new SchemaRef<>(
            CollectOrchestrationInput.class, "/schemajson/collect-flow/collect-orchestration-input.schema.json");
    public static final SchemaRef<CollectResult> COLLECT_RESULT = new SchemaRef<>(CollectResult.class,
            "/schemajson/collect-flow/collect-result.schema.json");
    public static final SchemaRef<CollectCompletion> COLLECT_COMPLETION = new SchemaRef<>(CollectCompletion.class,
            "/schemajson/collect-flow/collect-completion.schema.json");
    public static final SchemaRef<CollectFailure> COLLECT_FAILURE = new SchemaRef<>(CollectFailure.class,
            "/schemajson/collect-flow/collect-failure.schema.json");

    private static final List<SchemaRef<?>> SCHEMA_REFS = List.of(
            COLLECT_ACTIVITY_REQUEST,
            COLLECT_ACTIVITY_RESULT,
            COLLECT_ORCHESTRATION_INPUT,
            COLLECT_RESULT,
            COLLECT_COMPLETION,
            COLLECT_FAILURE);

    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private final ObjectMapper objectMapper;
    private Map<SchemaRef<?>, JsonSchema> schemas = Map.of();

    @PostConstruct
    public void init() {
        var loadedSchemas = new java.util.LinkedHashMap<SchemaRef<?>, JsonSchema>();
        for (var schemaRef : SCHEMA_REFS) {
            loadedSchemas.put(schemaRef, loadSchema(schemaRef));
        }
        this.schemas = Map.copyOf(loadedSchemas);
    }

    public <T> T validateReceived(final SchemaRef<T> schemaRef, final T payload) {
        return validate(schemaRef, payload, "Received");
    }

    public <T> @Nullable T validateReceivedIfPresent(final SchemaRef<T> schemaRef, final @Nullable T payload) {
        if (payload == null) {
            return null;
        }
        return validateReceived(schemaRef, payload);
    }

    public <T> T validateToSend(final SchemaRef<T> schemaRef, final T payload) {
        return validate(schemaRef, payload, "Outgoing");
    }

    private <T> T validate(final SchemaRef<T> schemaRef, final T payload, final String direction) {
        var typedPayload = schemaRef.modelType().cast(payload);
        var jsonNode = this.objectMapper.valueToTree(typedPayload);
        var schema = this.schemas.get(schemaRef);
        if (schema == null) {
            throw new IllegalStateException("Schema not loaded: " + schemaRef.resourcePath());
        }
        var errors = schema.validate(jsonNode);
        if (!errors.isEmpty()) {
            var details = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException(
                    direction + " payload does not match schema " + schemaRef.resourcePath() + ": " + details);
        }
        return typedPayload;
    }

    private static JsonSchema loadSchema(final SchemaRef<?> schemaRef) {
        try (var schemaStream = JsonValidator.class.getResourceAsStream(schemaRef.resourcePath())) {
            if (schemaStream == null) {
                throw new IllegalStateException("Schema resource not found: " + schemaRef.resourcePath());
            }
            return SCHEMA_FACTORY.getSchema(schemaStream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load schema " + schemaRef.resourcePath(), e);
        }
    }

    public record SchemaRef<T>(Class<T> modelType, String resourcePath) {
    }
}