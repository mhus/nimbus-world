package de.mhus.nimbus.world.ai.model.cortecs;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Manual, network-dependent round-trip test for the cortecs (DeepSeek) provider via the OpenAI
 * protocol. Excluded from normal builds ({@code @Tag("manual")}); run with the key in the env:
 * <pre>{@code
 *   CORTECS_API_KEY=<jwt> mvn -pl world-shared-ai test \
 *       -Dtest=CortecsLangchainModelManualTest -DexcludedGroups=
 * }</pre>
 * Key/base URL are also stored locally in s_settings (langchain4j.cortecs.apiKey / .baseUrl) and in
 * {@code ../vance-wb/confidential/init-settings-cortecs-deepseek.yaml}.
 */
@Tag("manual")
class CortecsLangchainModelManualTest {

    @Test
    void roundTripAgainstDeepSeek() throws Exception {
        String apiKey = System.getenv("CORTECS_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "CORTECS_API_KEY not set – skipping manual cortecs test");

        CortecsSettings settings = mock(CortecsSettings.class);
        when(settings.isAvailable()).thenReturn(true);
        when(settings.getBaseUrl()).thenReturn("https://api.cortecs.ai/v1");
        when(settings.getApiKey()).thenReturn(apiKey);

        CortecsLangchainModel provider = new CortecsLangchainModel(settings);
        assertThat(provider.getName()).isEqualTo("cortecs");

        Optional<AiChat> chatOpt = provider.createAiChat("deepseek-v4-pro",
                AiChatOptions.builder().temperature(0.2).maxTokens(50).build());
        assertThat(chatOpt).isPresent();

        String answer = chatOpt.get().ask("Reply with exactly the single word: pong");
        System.out.println("DeepSeek answered: " + answer);
        assertThat(answer).isNotBlank();
    }
}
