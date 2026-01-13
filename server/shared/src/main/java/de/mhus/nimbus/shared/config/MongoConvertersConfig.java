package de.mhus.nimbus.shared.config;

import de.mhus.nimbus.shared.security.KeyKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class MongoConvertersConfig {

    @ReadingConverter
    static class KeyKindReadingConverter implements Converter<String, KeyKind> {
        private static final Logger log = LoggerFactory.getLogger(KeyKindReadingConverter.class);
        @Override
        public KeyKind convert(String source) {
            if (source == null) return null;
            String normalized = source.trim();
            if (normalized.isEmpty()) return null;
            String upper = normalized.toUpperCase();
            // Sonderfall: alte Bezeichnung 'symmetric' => SECRET
            if (upper.equals("SYMMETRIC")) upper = "SECRET";
            try {
                return KeyKind.valueOf(upper);
            } catch (IllegalArgumentException ex) {
                log.warn("Unbekannter KeyKind Wert '{}' in MongoDB, wird ignoriert", source);
                return null; // oder Exception weiterwerfen; hier tolerant bleiben
            }
        }
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new KeyKindReadingConverter());
        return new MongoCustomConversions(converters);
    }
}
