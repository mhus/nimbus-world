package de.mhus.nimbus.shared.engine;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.types.TsEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * This is a proxy for object mapper to support handling of TypeScript (engine) specific behavior.
 */
@Service
@Slf4j
public class EngineMapper {

    protected ObjectMapper objectMapper;

    public EngineMapper() {

        // Initialize utilities with modern configuration
        objectMapper = new ObjectMapper();

        // Configure ObjectMapper for case-insensitive enum mapping
        objectMapper.configure(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);

        // Add custom enum serializer and deserializer for TsEnum support
        SimpleModule enumModule = new SimpleModule();

        // Custom serializer for all enums
        enumModule.addSerializer(new JsonSerializer<Enum<?>>() {
            @Override
            @SuppressWarnings("unchecked")
            public Class<Enum<?>> handledType() {
                return (Class<Enum<?>>) (Class<?>) Enum.class;
            }

            @Override
            public void serialize(Enum<?> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                if (value instanceof TsEnum) {
                    gen.writeString(((TsEnum) value).tsString());
                } else {
                    gen.writeString(value.name().toLowerCase());
                }
            }
        });

        objectMapper.registerModule(enumModule);

        // Add a custom BeanDeserializerModifier for enum handling
        SimpleModule deserializerModule = new SimpleModule();
        deserializerModule.setDeserializerModifier(new com.fasterxml.jackson.databind.deser.BeanDeserializerModifier() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public com.fasterxml.jackson.databind.JsonDeserializer<?> modifyEnumDeserializer(
                    com.fasterxml.jackson.databind.DeserializationConfig config,
                    com.fasterxml.jackson.databind.JavaType type,
                    com.fasterxml.jackson.databind.BeanDescription beanDesc,
                    com.fasterxml.jackson.databind.JsonDeserializer<?> deserializer) {

                if (type.isEnumType()) {
                    return new com.fasterxml.jackson.databind.JsonDeserializer<Enum<?>>() {
                        @Override
                        public Enum<?> deserialize(com.fasterxml.jackson.core.JsonParser p,
                                                   com.fasterxml.jackson.databind.DeserializationContext ctxt) throws IOException {
                            String text = p.getText();
                            if (text == null) return null;

                            Class<? extends Enum> enumClass = (Class<? extends Enum>) type.getRawClass();
                            Object[] constants = enumClass.getEnumConstants();

                            // First try TsEnum matching
                            for (Object constant : constants) {
                                if (constant instanceof TsEnum) {
                                    if (((TsEnum) constant).tsString().equalsIgnoreCase(text)) {
                                        return (Enum<?>) constant;
                                    }
                                }
                            }

                            // Then try standard enum name matching
                            for (Object constant : constants) {
                                if (constant instanceof Enum) {
                                    if (((Enum<?>) constant).name().equalsIgnoreCase(text)) {
                                        return (Enum<?>) constant;
                                    }
                                }
                            }

                            // If no match found, handle according to configuration
                            if (config.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)) {
                                log.warn("Unknown enum value: {}", text);
                                return null;
                            } else {
                                log.error("Unknown enum value: {}", text);
                                throw new com.fasterxml.jackson.databind.exc.InvalidFormatException(p,
                                        "Cannot deserialize value '" + text + "' to enum " + enumClass.getSimpleName(),
                                        text, enumClass);
                            }
                        }
                    };
                }

                return deserializer;
            }
        });

        objectMapper.registerModule(deserializerModule);

        // Additional useful configurations for WebSocket message parsing
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

    }

    public ObjectNode createObjectNode() {
        return objectMapper.createObjectNode();
    }

    public JsonNode readTree(String content) throws JsonProcessingException, JsonMappingException {
        return objectMapper.readTree(content);
    }

    public String writeValueAsString(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    public <T> T treeToValue(TreeNode n, Class<T> valueType) throws IllegalArgumentException, JsonProcessingException {
        return objectMapper.treeToValue(n, valueType);
    }

    public <T> T readValue(String content, Class<T> valueType) throws JsonProcessingException, JsonMappingException {
        return objectMapper.readValue(content, valueType);
    }

    public ArrayNode createArrayNode() {
        return objectMapper.createArrayNode();
    }

    public <T extends JsonNode> T valueToTree(Object fromValue) throws IllegalArgumentException {
        return objectMapper.valueToTree(fromValue);
    }

}
