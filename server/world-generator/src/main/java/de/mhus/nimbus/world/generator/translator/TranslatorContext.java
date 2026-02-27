package de.mhus.nimbus.world.generator.translator;

import de.mhus.nimbus.world.generator.fauna.FaunaIndex;
import de.mhus.nimbus.world.generator.flora.FloraIndex;
import lombok.Builder;
import lombok.Data;

/**
 * Context container for the translator providing dynamic information
 * about available flora and fauna options in the current region.
 */
@Data
@Builder
public class TranslatorContext {

    private FloraIndex floraIndex;
    private FaunaIndex faunaIndex;

    /**
     * Build the flora/fauna options section for the AI prompt.
     */
    public String toPromptSection() {
        var sb = new StringBuilder();

        boolean hasFlora = floraIndex != null && !floraIndex.getAllFloraNames().isEmpty();
        boolean hasFauna = faunaIndex != null && !faunaIndex.getAllFaunaNames().isEmpty();

        if (!hasFlora && !hasFauna) {
            return "";
        }

        sb.append("## Available Flora & Fauna Options\n\n");
        sb.append("**IMPORTANT: You MUST set `gf_flora` and `gf_fauna` in the `parameters` of EVERY biome feature.**\n");
        sb.append("Choose the most appropriate option from the tables below. ");
        sb.append("The biome prefix must match the biome type (e.g., use `forest_mixed` or `forest_dense` for a FOREST biome, ");
        sb.append("`plains_flower` or `plains_grass` for a PLAINS biome).\n");
        sb.append("Also set `gf_density` (0.0-1.0) to control vegetation density.\n");
        sb.append("For SWAMP/MARSH biomes, additionally set `gf_water_flora` and `gf_water_density`.\n");
        sb.append("For COAST/OCEAN biomes, additionally set `gf_sea_flora` and `gf_sea_density`.\n\n");

        if (hasFlora) {
            sb.append(floraIndex.toPromptDescription());
            sb.append("\n");
        }

        if (hasFauna) {
            sb.append(faunaIndex.toPromptDescription());
            sb.append("\n");
        }

        return sb.toString();
    }
}
