package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Offline test for the phase-2 lore elaborator (AI mocked). */
class RealityLoreElaboratorTest {

    private final AiModelService aiModelService = mock(AiModelService.class);
    private final RealityLoreElaborator elaborator = new RealityLoreElaborator(aiModelService);

    private RealityPlan seededPlan() {
        RealityPlan p = new RealityPlan();
        p.setVision("A mist moor.");
        RealityPlan.Direction d = new RealityPlan.Direction();
        d.setPremise("The bog wakes.");
        p.setDirection(d);
        RealityPlan.Chapter c1 = new RealityPlan.Chapter();
        c1.setKey("deep_history");
        c1.setTitle("The Great Rain");
        c1.setKind("history");
        RealityPlan.Chapter c2 = new RealityPlan.Chapter();
        c2.setKey("the_threat");
        c2.setTitle("The Peat King");
        c2.setKind("power");
        p.setOutline(List.of(c1, c2));
        return p;
    }

    @Test
    void elaboratesEachChapterWithRollingContextAtLowTemperature() throws Exception {
        AiChat chat = mock(AiChat.class);
        when(aiModelService.createChat(anyString(), any())).thenReturn(Optional.of(chat));
        when(chat.ask(anyString()))
                .thenReturn("## The Great Rain\nThe kingdom drowned in the endless rain.")
                .thenReturn("## The Peat King\nBeneath the black lakes, something ancient stirs.");

        RealityPlanResult result = elaborator.elaborate(seededPlan());

        assertThat(result.isSuccessful()).isTrue();
        RealityPlan p = result.getPlan();
        assertThat(p.getLore()).hasSize(2);
        assertThat(p.getLore().get(0).getTitle()).isEqualTo("The Great Rain");
        assertThat(p.getLore().get(1).getTitle()).isEqualTo("The Peat King");
        assertThat(p.getLore().get(0).getContent()).contains("kingdom drowned");

        // Two chapters -> two chat calls; the 2nd prompt carries a summary of the 1st (rolling context).
        ArgumentCaptor<String> promptCap = ArgumentCaptor.forClass(String.class);
        verify(chat, times(2)).ask(promptCap.capture());
        String secondPrompt = promptCap.getAllValues().get(1);
        assertThat(secondPrompt).contains("The Great Rain");     // previous chapter in the summary
        assertThat(secondPrompt).contains("The bog wakes.");     // seed context (direction)

        // Elaboration uses a LOW temperature.
        ArgumentCaptor<AiChatOptions> optsCap = ArgumentCaptor.forClass(AiChatOptions.class);
        verify(aiModelService).createChat(anyString(), optsCap.capture());
        assertThat(optsCap.getValue().getTemperature()).isEqualTo(0.3);
    }

    @Test
    void noOutlineKeepsPlanUnchanged() {
        RealityPlan p = new RealityPlan();
        RealityPlanResult r = elaborator.elaborate(p);
        assertThat(r.isSuccessful()).isTrue();
        assertThat(r.getPlan()).isSameAs(p);
    }
}
