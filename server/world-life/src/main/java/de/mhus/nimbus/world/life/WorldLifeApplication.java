package de.mhus.nimbus.world.life;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ReflectiveScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * World Life Application - Entity Simulation Service
 *
 * Simulates entity behavior in the Nimbus world:
 * - Tracks active chunks via Redis (from world-player pods)
 * - Simulates entities only in active chunks (performance optimization)
 * - Generates terrain-aware movement pathways
 * - Distributes pathways to world-player pods for client broadcasting
 * - Supports multi-pod deployment with entity ownership coordination
 */
@EnableMongoAuditing
@ReflectiveScan(basePackages = {
        "de.mhus.nimbus.world.life",
        "de.mhus.nimbus.world.shared",
        "de.mhus.nimbus.shared"
})
@EnableMongoRepositories(basePackages = {
        "de.mhus.nimbus.world.life",
        "de.mhus.nimbus.world.shared",
        "de.mhus.nimbus.shared"
})
@OpenAPIDefinition(
        info = @Info(
                title = "World Life API",
                version = "v1",
                description = "API for entity simulation and world life systems",
                contact = @Contact(name = "Nimbus"),
                license = @License(name = "Apache-2.0")
        )
)
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@ComponentScan(basePackages = {
        "de.mhus.nimbus.world.life",
        "de.mhus.nimbus.world.shared",
        "de.mhus.nimbus.shared"
})
public class WorldLifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorldLifeApplication.class, args);
    }
}
