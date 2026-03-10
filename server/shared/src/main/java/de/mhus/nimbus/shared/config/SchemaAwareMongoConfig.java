package de.mhus.nimbus.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

/**
 * Configuration that replaces Spring Boot's auto-configured {@link MongoTemplate}
 * with {@link SchemaAwareMongoTemplate}.
 *
 * <p>Spring Boot's {@code MongoDataAutoConfiguration} uses
 * {@code @ConditionalOnMissingBean(MongoOperations.class)}, so defining our own
 * {@link MongoTemplate} bean here causes the auto-configured one to back off.</p>
 *
 * <p>This ensures that all {@code updateFirst}, {@code updateMulti}, and
 * {@code upsert} operations throughout the application automatically include
 * the {@code _schema} field from {@code @ActualSchemaVersion} annotations.</p>
 */
@Configuration
public class SchemaAwareMongoConfig {

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory dbFactory, MongoConverter converter) {
        return new SchemaAwareMongoTemplate(dbFactory, converter);
    }
}
