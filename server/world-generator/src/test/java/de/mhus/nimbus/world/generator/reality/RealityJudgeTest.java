package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiModelService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Offline tests for the AI balance judge (C2). The AI chat is mocked. */
class RealityJudgeTest {

    private final AiModelService aiModelService = mock(AiModelService.class);
    private final RealityJudge judge = new RealityJudge(aiModelService);

    private RealityPlan planWithRegion() {
        RealityPlan plan = new RealityPlan();
        RealityPlan.Meta meta = new RealityPlan.Meta();
        meta.setRegionId("duskmoor");
        plan.setMeta(meta);
        RealityPlan.ItemSpec i = new RealityPlan.ItemSpec();
        i.setName("Peat Brick");
        i.setType("material");
        plan.setItems(List.of(i));
        return plan;
    }

    @Test
    void parsesVerdictAndFiltersMajorFindings() throws Exception {
        AiChat chat = mock(AiChat.class);
        when(aiModelService.createChat(anyString(), any())).thenReturn(Optional.of(chat));
        String verdictJson = """
                {
                  "acceptable": false,
                  "score": 68,
                  "summary": "Mostly fine, one major issue.",
                  "findings": [
                    { "ref": "one_up_forever", "severity": "major",
                      "issue": "too cheap for a permanent power-up", "suggestion": "raise price and quest-gate it" },
                    { "ref": "peat_brick", "severity": "minor",
                      "issue": "slightly overpriced", "suggestion": "lower priceHint to 2" }
                  ]
                }
                """;
        when(chat.ask(anyString())).thenReturn("```json\n" + verdictJson + "\n```");

        JudgeVerdict verdict = judge.judge(planWithRegion());

        assertThat(verdict.isConclusive()).isTrue();
        assertThat(verdict.isAcceptable()).isFalse();
        assertThat(verdict.getScore()).isEqualTo(68);
        assertThat(verdict.getFindings()).hasSize(2);
        assertThat(verdict.majorFindings()).hasSize(1);
        assertThat(verdict.majorFindings().get(0).getRef()).isEqualTo("one_up_forever");

        // The prompt carries the serialized plan.
        ArgumentCaptor<String> promptCap = ArgumentCaptor.forClass(String.class);
        verify(chat).ask(promptCap.capture());
        assertThat(promptCap.getValue()).contains("duskmoor").contains("Peat Brick");
    }

    @Test
    void returnsInconclusiveWhenNoChatModel() {
        when(aiModelService.createChat(anyString(), any())).thenReturn(Optional.empty());

        JudgeVerdict verdict = judge.judge(planWithRegion());

        assertThat(verdict.isConclusive()).isFalse();
        assertThat(verdict.hasErrors()).isTrue();
        assertThat(verdict.isAcceptable()).isFalse();
    }

    @Test
    void nullPlanIsInconclusive() {
        assertThat(judge.judge(null).isConclusive()).isFalse();
    }

    @Test
    void majorFindingsFilterIsCaseInsensitiveAndNullSafe() {
        JudgeVerdict v = new JudgeVerdict();
        assertThat(v.majorFindings()).isEmpty(); // null findings
        v.setFindings(List.of(
                new JudgeFinding("a", "MAJOR", "x", "y"),
                new JudgeFinding("b", "minor", "x", "y"),
                new JudgeFinding("c", null, "x", "y")));
        assertThat(v.majorFindings()).hasSize(1);
        assertThat(v.majorFindings().get(0).getRef()).isEqualTo("a");
    }
}
