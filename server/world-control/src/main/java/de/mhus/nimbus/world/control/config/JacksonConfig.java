package de.mhus.nimbus.world.control.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.mhus.nimbus.types.TsEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;

/**
 * Jackson configuration for proper Java 8 date/time serialization and TsEnum support.
 * Enables support for Instant, LocalDateTime, etc.
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

        // Create JsonFactory with custom constraints
        JsonFactory jsonFactory = JsonFactory.builder()
                .streamReadConstraints(constraints)
                .build();

        // Create ObjectMapper with custom factory
        ObjectMapper mapper = new ObjectMapper(jsonFactory);

        // Register JavaTimeModule for Java 8 date/time types (Instant, LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());

        // Write dates as ISO-8601 strings instead of timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Configure case-insensitive enum mapping
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Add custom TsEnum serializer
        SimpleModule enumModule = new SimpleModule();
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
        mapper.registerModule(enumModule);

        // Add custom TsEnum deserializer
        SimpleModule deserializerModule = new SimpleModule();
        deserializerModule.setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public JsonDeserializer<?> modifyEnumDeserializer(
                    DeserializationConfig config,
                    JavaType type,
                    BeanDescription beanDesc,
                    JsonDeserializer<?> deserializer) {

                if (type.isEnumType()) {
                    return new JsonDeserializer<Enum<?>>() {
                        @Override
                        public Enum<?> deserialize(com.fasterxml.jackson.core.JsonParser p,
                                                   DeserializationContext ctxt) throws IOException {
                            String text = p.getText();
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
        mapper.registerModule(deserializerModule);

        return mapper;
    }
}
