package de.mhus.nimbus.shared.engine;

import de.mhus.nimbus.types.TsEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.TreeNode;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * This is a proxy for object mapper to support handling of TypeScript (engine) specific behavior.
 */
@Service
@Slf4j
public class EngineMapper {

    protected final JsonMapper objectMapper;

    public EngineMapper() {

        // Custom serializer for all enums (Jackson 3: ValueSerializer replaces JsonSerializer)
        SimpleModule enumModule = new SimpleModule();
        enumModule.addSerializer(new ValueSerializer<Enum<?>>() {
            @Override
            @SuppressWarnings("unchecked")
            public Class<Enum<?>> handledType() {
                return (Class<Enum<?>>) (Class<?>) Enum.class;
            }

            @Override
            public void serialize(Enum<?> value, JsonGenerator gen, SerializationContext ctxt) {
                if (value instanceof TsEnum) {
                    gen.writeString(((TsEnum) value).tsString());
                } else {
                    gen.writeString(value.name().toLowerCase());
                }
            }
        });

        // Custom enum deserializer via modifier (Jackson 3: ValueDeserializerModifier)
        SimpleModule deserializerModule = new SimpleModule();
        deserializerModule.setDeserializerModifier(new ValueDeserializerModifier() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public ValueDeserializer<?> modifyEnumDeserializer(
                    DeserializationConfig config,
                    JavaType type,
                    BeanDescription.Supplier beanDescRef,
                    ValueDeserializer<?> deserializer) {

                if (type.isEnumType()) {
                    return new ValueDeserializer<Enum<?>>() {
                        @Override
                        public Enum<?> deserialize(JsonParser p, DeserializationContext ctxt) {
                            String text = p.getString();
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
                            if (config.isEnabled(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)) {
                                log.warn("Unknown enum value: {}", text);
                                return null;
                            } else {
                                log.error("Unknown enum value: {}", text);
                                throw InvalidFormatException.from(p,
                                        "Cannot deserialize value '" + text + "' to enum " + enumClass.getSimpleName(),
                                        text, enumClass);
                            }
                        }
                    };
                }

                return deserializer;
            }
        });

        // Jackson 3: ObjectMapper is immutable, configure through the builder.
        objectMapper = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Jackson 3 defaults FAIL_ON_NULL_FOR_PRIMITIVES to true; keep Jackson 2 lenient behavior
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .addModule(enumModule)
                .addModule(deserializerModule)
                .build();
    }

    public ObjectNode createObjectNode() {
        return objectMapper.createObjectNode();
    }

    public JsonNode readTree(String content) {
        return objectMapper.readTree(content);
    }

    public String writeValueAsString(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    public <T> T treeToValue(TreeNode n, Class<T> valueType) {
        return objectMapper.treeToValue(n, valueType);
    }

    public <T> T readValue(String content, Class<T> valueType) {
        return objectMapper.readValue(content, valueType);
    }

    public ArrayNode createArrayNode() {
        return objectMapper.createArrayNode();
    }

    public <T extends JsonNode> T valueToTree(Object fromValue) {
        return objectMapper.valueToTree(fromValue);
    }

}
