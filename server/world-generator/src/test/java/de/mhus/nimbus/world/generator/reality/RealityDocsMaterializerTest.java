package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Offline test for the deterministic design + world-directives document rendering. */
class RealityDocsMaterializerTest {

    private final WDocumentService docs = mock(WDocumentService.class);
    private final RealityDocsMaterializer materializer = new RealityDocsMaterializer(docs);
    private final WorldId region = WorldId.of(WorldId.COLLECTION_REGION, "duskmoor").orElseThrow();

    private RealityPlan plan() {
        RealityPlan p = new RealityPlan();
        RealityPlan.Meta meta = new RealityPlan.Meta();
        meta.setTitle("Duskmoor");
        p.setMeta(meta);
        RealityPlan.Direction d = new RealityPlan.Direction();
        d.setPremise("The bog wakes.");
        p.setDirection(d);
        RealityPlan.BackgroundPower power = new RealityPlan.BackgroundPower();
        power.setName("The Peat King");
        power.setInfluence("pervasive");
        power.setStatus("rising");
        power.setManifestations(List.of("will-o-wisps"));
        p.setBackgroundPowers(List.of(power));
        RealityPlan.ItemSpec item = new RealityPlan.ItemSpec();
        item.setName("Bog Iron");
        item.setType("material");
        item.setSource("bog");
        p.setItems(List.of(item));
        return p;
    }

    @Test
    void writesDesignAndDirectivesDocuments() {
        MaterializeResult r = materializer.materialize(region, plan());

        assertThat(r.getCreated()).isEqualTo(2);
        ArgumentCaptor<String> collectionCap = ArgumentCaptor.forClass(String.class);
        verify(docs, times(2)).save(eq(region), collectionCap.capture(), anyString(), any());
        assertThat(collectionCap.getAllValues())
                .containsExactly("reality_design", "reality_world_directives");
    }

    @Test
    void designRenderIncludesItemCatalogAndDirection() {
        String md = materializer.renderDesign(plan());
        assertThat(md).contains("Reality Design — Duskmoor");
        assertThat(md).contains("The bog wakes.");
        assertThat(md).contains("Item catalog").contains("Bog Iron");
    }

    @Test
    void directivesRenderIncludesBackgroundPowersAndOpenness() {
        String md = materializer.renderDirectives(region, plan());
        assertThat(md).contains("World Directives — Duskmoor");
        assertThat(md).contains("The Peat King");
        assertThat(md).contains("pervasive");
        assertThat(md).containsIgnoringCase("open"); // open-system directive
    }
}
