package de.mhus.nimbus.world.player.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.PlayerId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimplePlayerTest {

    @Test
    public void testPlayerIdFormat() {
        // Test PlayerId Parsing für ecb_blade
        String playerIdString = "@ecb:blade";

        var playerId = PlayerId.of(playerIdString);

        assertThat(playerId).isPresent();
        assertThat(playerId.get().getUserId()).isEqualTo("ecb");
        assertThat(playerId.get().getCharacterId()).isEqualTo("blade");
        assertThat(playerId.get().getRawId()).isEqualTo("ecb:blade");

        System.out.println("PlayerId Test successful:");
        System.out.println("Full ID: " + playerId.get().getId());
        System.out.println("User ID: " + playerId.get().getUserId());
        System.out.println("Character ID: " + playerId.get().getCharacterId());
        System.out.println("Raw ID: " + playerId.get().getRawId());
    }

    @Test
    public void testJsonParsing() {
        // Simple JSON parsing test
        ObjectMapper mapper = new ObjectMapper();

        String jsonString = """
            {
              "user": {
                "userId": "ecb",
                "title": "Eric Cross Brooks"
              },
              "settings": {
                "name": "test_settings"
              }
            }
            """;

        try {
            var jsonNode = mapper.readTree(jsonString);
            assertThat(jsonNode.get("user").get("userId").asText()).isEqualTo("ecb");
            assertThat(jsonNode.get("user").get("title").asText()).isEqualTo("Eric Cross Brooks");

            System.out.println("JSON parsing test successful");
        } catch (Exception e) {
            throw new RuntimeException("JSON parsing failed", e);
        }
    }
}
