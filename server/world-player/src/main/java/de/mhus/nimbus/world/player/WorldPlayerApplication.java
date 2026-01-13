package de.mhus.nimbus.world.player;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ReflectiveScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableMongoAuditing
@ReflectiveScan(basePackages = {"de.mhus.nimbus.world.player","de.mhus.nimbus.world.shared","de.mhus.nimbus.shared"})
@EnableMongoRepositories(basePackages = {"de.mhus.nimbus.world.player","de.mhus.nimbus.world.shared","de.mhus.nimbus.shared"})
@OpenAPIDefinition(info = @Info(title = "World Player API", version = "v1", description = "API for world player", contact = @Contact(name="Nimbus"), license = @License(name="Apache-2.0")))
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@ComponentScan(basePackages = {"de.mhus.nimbus.world.player","de.mhus.nimbus.world.shared","de.mhus.nimbus.shared"})
public class WorldPlayerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorldPlayerApplication.class, args);
    }
}
