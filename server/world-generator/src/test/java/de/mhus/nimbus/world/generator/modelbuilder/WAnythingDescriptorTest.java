package de.mhus.nimbus.world.generator.modelbuilder;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WAnythingDescriptorTest {

    @Test
    void parse_singleBlock() {
        var result = WAnythingDescriptor.parse("block:m:champignon");

        assertThat(result).isInstanceOf(WAnythingDescriptor.BlockStack.class);
        var blockStack = (WAnythingDescriptor.BlockStack) result;
        assertThat(blockStack.blockTypes()).containsExactly("m:champignon");
    }

    @Test
    void parse_multiBlock() {
        var result = WAnythingDescriptor.parse("block:m:sunflower_log,m:sunflower_top");

        assertThat(result).isInstanceOf(WAnythingDescriptor.BlockStack.class);
        var blockStack = (WAnythingDescriptor.BlockStack) result;
        assertThat(blockStack.blockTypes()).containsExactly("m:sunflower_log", "m:sunflower_top");
    }

    @Test
    void parse_modelWithParams() {
        var result = WAnythingDescriptor.parse("model:tree,log=m:birch_log,leaves=m:birch_leaves");

        assertThat(result).isInstanceOf(WAnythingDescriptor.ModelRef.class);
        var modelRef = (WAnythingDescriptor.ModelRef) result;
        assertThat(modelRef.name()).isEqualTo("tree");
        assertThat(modelRef.parameters()).containsExactlyInAnyOrderEntriesOf(
                Map.of("log", "m:birch_log", "leaves", "m:birch_leaves")
        );
    }

    @Test
    void parse_modelWithoutParams() {
        var result = WAnythingDescriptor.parse("model:cactus");

        assertThat(result).isInstanceOf(WAnythingDescriptor.ModelRef.class);
        var modelRef = (WAnythingDescriptor.ModelRef) result;
        assertThat(modelRef.name()).isEqualTo("cactus");
        assertThat(modelRef.parameters()).isEmpty();
    }

    @Test
    void parse_blank_throws() {
        assertThatThrownBy(() -> WAnythingDescriptor.parse(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");

        assertThatThrownBy(() -> WAnythingDescriptor.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void parse_unknownPrefix_throws() {
        assertThatThrownBy(() -> WAnythingDescriptor.parse("unknown:something"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown descriptor prefix");
    }
}
