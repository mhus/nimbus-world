package de.mhus.nimbus.world.generator.reality;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Structured, typed representation of a Reality Instruction Document (see
 * {@code instructions/reality/instruction-document-schema.md}). Produced by {@link RealityPlanParser}
 * from the free-text instruction document and consumed by the later reality-generation phases
 * (items + images, lore, rules, ...).
 * <p>
 * All fields are optional on the wire (unknown/missing values stay null) so a partially specified
 * instruction still parses; the generation phases decide what is required.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealityPlan {

    private Meta meta;
    private String vision;
    /** Phase-1 seed: the through-line / core tension of the region (drives all lore). */
    private Direction direction;
    /** Phase-1 seed: hidden threats / overpowering forces that act in the background. */
    private List<BackgroundPower> backgroundPowers;
    /** Phase-1 seed: key acting figures / factions. */
    private List<CastMember> cast;
    /** Phase-1 seed: the chapter list to elaborate in phase 2 (the "table of contents"). */
    private List<Chapter> outline;
    /** Phase-2 elaborated deep lore (chapters) + any seed lore. */
    private List<LoreEntry> lore;
    private StyleGuide style;
    private List<BlockPaletteRef> blockPalette;
    /** Item classes / material tiers (e.g. leather, iron, steel...) — the progression ladder. */
    private List<ItemClass> itemClasses;
    private List<ItemSpec> items;
    private List<CreatureSpec> creatures;
    private List<NpcSpec> npcs;
    private List<RuleSpec> rules;
    private EconomySpec economy;
    private EnvSpec environment;
    private List<WorldTemplate> worldTemplates;
    private List<ItemSpec> specialItems;

    /** Section 1 — identification and generation controls. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Meta {
        private String regionId;
        private String title;
        private String language;
        private Integer version;
        private String author;
        private GenerationControls controls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GenerationControls {
        private Integer maxItems;
        private Integer maxCreatures;
        private String chatModel;
        private String imageModel;
        private Boolean overwrite;
        /** Shared collections whose blocks/textures are reused rather than generated. */
        private List<String> blocksFromShared;

        // --- catalog scale controls (see preset-catalog.md) ---
        /** Which preset catalog to inherit (baseline structure/counts). Null = default preset. */
        private String presetRef;
        /** Whether to fill the catalog from the preset beyond the explicitly named items. */
        private Boolean expandCatalog;
        /** Target total number of items (cap). */
        private Integer targetItemCount;
        /** Per-category target counts, overriding the preset defaults (e.g. {"weapon": 30}). */
        private Map<String, Integer> categoryCoverage;
        /** Active super-items, e.g. ["one_up", "one_up_forever"]. */
        private List<String> superItems;
    }

    /** Phase-1 seed — the core direction / through-line of the region. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Direction {
        /** One or two sentences: what is fundamentally at stake / where this is heading. */
        private String premise;
        private String tone;
    }

    /**
     * Phase-1 seed — a hidden threat / overpowering force acting in the background (e.g. a cult,
     * a guardian). Kept open: {@code status} can advance as worlds are generated (the lore engine).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BackgroundPower {
        private String name;
        private String nature;
        private String goal;
        /** How strongly it acts in the background: subtle | pervasive | overt (Star-Wars = pervasive). */
        private String influence;
        /** hidden | rumored | known */
        private String visibility;
        /** dormant | rising | active | waning — advanced incrementally by world generation. */
        private String status;
        /** How it surfaces in worlds (signs, cults, disasters). */
        private List<String> manifestations;
        /** Competing powers / factions it is in conflict with. */
        private List<String> opposedBy;
    }

    /** Phase-1 seed — a key acting figure or faction. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CastMember {
        private String name;
        private String role;
        private String description;
    }

    /** Phase-1 seed — one "chapter" of the outline to elaborate in phase 2. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Chapter {
        /** Canonical key, e.g. "deep_history" (used as document name). */
        private String key;
        private String title;
        /** history | geography | faction | power | legend | other */
        private String kind;
        /** What this chapter should establish (a short brief for the elaborator). */
        private String goal;
    }

    /** Section 3 — narrative entries (history/geography/faction/legend). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LoreEntry {
        private String title;
        /** history | geography | faction | legend | quest | other */
        private String kind;
        private String content;
    }

    /** Section 4 — binding visual style used for every generated image prompt. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StyleGuide {
        private String artStyle;
        private String palette;
        private Integer iconSize;
        private String promptPrefix;
        private String promptNegative;
        /** Whether generated icons must be freestanding with a real alpha channel. */
        private Boolean transparentBackground;
    }

    /**
     * An item class / material tier (e.g. leather, iron, steel, mythril) — the backbone of item
     * progression (Ultima/Minecraft style). Items reference a class via {@link ItemSpec#itemClass};
     * the class fixes the {@code tier} (progression rank) and shared properties, so all items of a
     * class stay consistent. Regions may rename classes thematically but keep the rank order.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ItemClass {
        /** Canonical class id, e.g. "iron" (used by {@link ItemSpec#itemClass}). */
        private String name;
        /** Display name, e.g. "Bog Iron" for a themed region. */
        private String title;
        /** Progression rank (ascending: lower = weaker/earlier). */
        private Integer rank;
        /** Maps to the {@code ItemTier} enum (e.g. "IRON"); drives item tier. */
        private String tier;
        private String description;
        /** Categories this class applies to, e.g. ["weapon","armor","tool"]. */
        private List<String> appliesTo;
        /** Base/source material slug of this class, e.g. "iron_ingot". */
        private String material;
    }

    /** Section 5 — block palette references (reused from shared collections, not generated). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BlockPaletteRef {
        private String sharedCollection;
        private String theme;
    }

    /** Section 6/14 — an item to create (WItemType/WItem) and to generate an icon for. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ItemSpec {
        private String name;
        /** material | tool | weapon | armor | food | potion | decoration | placeable | seed | super | ... */
        private String type;
        private String tier;
        private String rarity;
        private String description;
        private String useEffect;
        private String slot;
        private Integer priceHint;
        /** Name of the {@link ItemClass} this item belongs to (e.g. "iron"); drives the tier. */
        private String itemClass;
        /** Lore-bound / unique item that may be generated on demand only. */
        private Boolean loreBound;

        // --- super-item mechanics (see preset-catalog.md §4) ---
        /** Whether the item is consumed on use (false e.g. for "one-up forever"). */
        private Boolean consumable;
        /** Persistent power-up that is never consumed; maps to Item.exclusive=true. */
        private Boolean persistent;
        /** Effect key/description of a super-item (e.g. "extra_life"). */
        private String effect;

        // --- world-logic coherence relations (see preset-catalog.md §3) ---
        /** Where the raw material comes from (block type / creature / crop). */
        private String source;
        /** Ingredient item slugs this item is crafted from. */
        private List<String> recipe;
    }

    /** Section 7 — creature preset referencing a shared 3D model. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CreatureSpec {
        private String name;
        /** animal | avatar | ... */
        private String type;
        private String modelPath;
        private String behavior;
        private Map<String, String> modifiers;
    }

    /** Section 8 — NPC / faction role archetype. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NpcSpec {
        private String role;
        private String faction;
        private String tone;
    }

    /** Section 9 — building / logic rule. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RuleSpec {
        private String name;
        /** craft | build | logic | constraint */
        private String kind;
        /** natural-language or SpEL condition */
        private String when;
        private List<String> effects;
        private String description;
    }

    /** Section 10 — economy / balancing frame. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EconomySpec {
        private String currency;
        private List<String> tiers;
        private String rarityMix;
        private Map<String, String> priceBands;
    }

    /** Section 11 — environment / weather / time / seasons. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EnvSpec {
        private String weather;
        private String dayNight;
        private String seasons;
        private String backdrop;
    }

    /** Section 13 — a world template the World Generator (Genesis) can roll out. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class WorldTemplate {
        private String name;
        private String summary;
        private String biomeFocus;
        private String danger;
    }
}
