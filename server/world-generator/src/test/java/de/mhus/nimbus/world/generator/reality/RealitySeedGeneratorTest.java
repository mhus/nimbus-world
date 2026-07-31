package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Offline test for the phase-1 seed generator (AI mocked). */
class RealitySeedGeneratorTest {

    private final AiModelService aiModelService = mock(AiModelService.class);
    private final RealitySeedGenerator generator =
            new RealitySeedGenerator(aiModelService, new RealityPlanParser(null, null));

    @Test
    void parsesSeedWithDirectionPowersAndOutlineAtHighTemperature() throws Exception {
        AiChat chat = mock(AiChat.class);
        when(aiModelService.createChat(anyString(), any())).thenReturn(Optional.of(chat));
        String seedJson = """
                {
                  "meta": { "regionId": "duskmoor", "title": "Duskmoor" },
                  "vision": "A mist-wreathed moor folk-fantasy.",
                  "direction": { "premise": "The bog remembers, and it is waking.", "tone": "melancholic" },
                  "backgroundPowers": [
                    { "name": "The Peat King", "nature": "ancient bog-god", "goal": "reclaim the drowned kingdom",
                      "influence": "pervasive", "visibility": "hidden", "status": "rising",
                      "manifestations": ["will-o-wisps", "sinking villages"], "opposedBy": ["Iron Wardens"] }
                  ],
                  "cast": [ { "name": "Warden Bael", "role": "guardian", "description": "keeps the pact" } ],
                  "style": { "iconSize": 64, "transparentBackground": true },
                  "outline": [
                    { "key": "deep_history", "title": "The Great Rain", "kind": "history", "goal": "how the kingdom drowned" },
                    { "key": "the_threat", "title": "The Peat King Stirs", "kind": "power", "goal": "origin of the threat" }
                  ]
                }
                """;
        when(chat.ask(anyString())).thenReturn(seedJson);

        RealityPlanResult result = generator.generate("A celtic moor with a hidden threat.");

        assertThat(result.isSuccessful()).isTrue();
        RealityPlan p = result.getPlan();
        assertThat(p.getDirection().getPremise()).contains("bog remembers");
        assertThat(p.getBackgroundPowers()).hasSize(1);
        assertThat(p.getBackgroundPowers().get(0).getInfluence()).isEqualTo("pervasive");
        assertThat(p.getBackgroundPowers().get(0).getStatus()).isEqualTo("rising");
        assertThat(p.getOutline()).hasSize(2);
        assertThat(p.getCast()).hasSize(1);

        // Seed must use a HIGH temperature for divergence.
        ArgumentCaptor<AiChatOptions> optsCap = ArgumentCaptor.forClass(AiChatOptions.class);
        verify(aiModelService).createChat(anyString(), optsCap.capture());
        assertThat(optsCap.getValue().getTemperature()).isEqualTo(1.0);
    }

    @Test
    void failsGracefullyWithoutChatModel() {
        when(aiModelService.createChat(anyString(), any())).thenReturn(Optional.empty());
        assertThat(generator.generate("x").hasFailed()).isTrue();
    }
}
