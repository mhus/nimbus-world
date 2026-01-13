package de.mhus.nimbus.world.generator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot Application for World Generator Service.
 * Provides terrain and world generation executors for the Job System.
 */
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = {
        "de.mhus.nimbus.world.generator",
        "de.mhus.nimbus.world.shared",
        "de.mhus.nimbus.shared"
})
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@ComponentScan(basePackages = {
        "de.mhus.nimbus.world.generator",
        "de.mhus.nimbus.world.shared",
        "de.mhus.nimbus.shared"
})
public class GeneratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(GeneratorApplication.class, args);
    }
}
