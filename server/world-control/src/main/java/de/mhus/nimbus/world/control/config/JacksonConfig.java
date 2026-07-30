package de.mhus.nimbus.world.control.config;

import de.mhus.nimbus.types.TsEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson configuration for proper Java 8 date/time serialization and TsEnum support.
 * Enables support for Instant, LocalDateTime, etc. (java.time is built-in in Jackson 3).
 * Configures increased limits for large JSON payloads (model imports).
 * Registers custom serializer/deserializer for TsEnum enums (e.g., BlockEffect).
 */
@Configuration
@Slf4j
public class JacksonConfig {

    /**
     * Configures the primary ObjectMapper with Java 8 Time support,
     * TsEnum serialization/deserialization, and increased StreamReadConstraints
     * for large JSON payloads.
     * <p>
     * Default maxStringLength is 20MB, but large model imports can exceed this.
     * Increased to 200MB to support large model layer imports.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        // Configure StreamReadConstraints with increased limits
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxStringLength(200_000_000) // 200MB (up from 20MB default)
                .build();

        // Create JsonFactory with custom constraints (Jackson 3: factory is immutable, use builder)
        JsonFactory jsonFactory = JsonFactory.builder()
                .streamReadConstraints(constraints)
                .build();

        // Custom TsEnum serializer (Jackson 3: ValueSerializer replaces JsonSerializer)
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

        // Custom TsEnum deserializer (Jackson 3: ValueDeserializerModifier)
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

                            // First try TsEnum matching (by tsIndex string)
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

                            // If no match found, log warning and return null
                            log.warn("Unknown enum value '{}' for type {}", text, enumClass.getSimpleName());
                            return null;
                        }
                    };
                }

                return deserializer;
            }
        });

        // Jackson 3: ObjectMapper is immutable, configure through the builder.
        // java.time support (jsr310) is built-in and registered automatically.
        return JsonMapper.builder(jsonFactory)
                .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Jackson 3 defaults FAIL_ON_NULL_FOR_PRIMITIVES to true; keep Jackson 2 lenient behavior
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .addModule(enumModule)
                .addModule(deserializerModule)
                .build();
    }
}
