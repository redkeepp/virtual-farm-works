/*
 * VfwServerConfig — definition of config/virtualfarmworks-server.toml: every balance value pack makers can change
 * (growth, hoe, output, drops, Mystical Agriculture rules, blacklists, per-tier machine settings) together with the
 * comments they read in the file.
 */
package com.virtualfarmworks.config;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.machine.MachineLayout;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The SERVER config. On NeoForge 26.1.2 the file is {@code config/virtualfarmworks-server.toml} (verified in a dev run;
 * older NeoForge versions used {@code <world>/serverconfig/}). Server type because every value is gameplay/balance:
 * NeoForge syncs it to clients on login, so GUIs and tooltips show the server's numbers.
 *
 * <p>Requirement (owner): pack makers must be able to rebalance everything WITHOUT touching Java. Nothing that is a
 * balance number may be hard-coded elsewhere; read it from here. Full list and rationale:
 * {@code docs/specs/configurability.md}.
 *
 * <p>Do not read these values in hot paths (per tick). Machines copy what they need into cached fields when they
 * revalidate, and revalidate when {@link VfwConfig#generation()} changes.
 *
 * <p>Only machine tiers that exist get a {@code machines.<tier>} section (Starter for now). Adding a tier later just
 * appends a new section to existing config files; nothing is lost.
 */
public final class VfwServerConfig {
    public static final ModConfigSpec SPEC;

    // --- growth -----------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue GROWTH_BONUS_PER_UPGRADE;
    public static final ModConfigSpec.IntValue GROWTH_UPGRADES_PER_SLOT;

    // --- hoe --------------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.BooleanValue REQUIRE_HOE;
    public static final ModConfigSpec.BooleanValue HOE_CONSUMES_DURABILITY;
    public static final ModConfigSpec.IntValue HOE_WEAR_INTERVAL_TICKS;

    // --- output -----------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue AUTO_EXPORT_INTERVAL_TICKS;

    // --- drops ------------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue GLOBAL_PRODUCTION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SECONDARY_DROP_MULTIPLIER;
    public static final ModConfigSpec.IntValue OTHER_PLANT_YIELD;
    public static final ModConfigSpec.DoubleValue MYSTICAL_SECONDARY_SEED_MULTIPLIER;

    // --- Mystical Agriculture ---------------------------------------------------------------------------------------
    public static final ModConfigSpec.BooleanValue MYSTICAL_REQUIRES_EFFECTIVE_FARMLAND;

    // --- performance ------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue MAX_LOOT_ROLLS_PER_HARVEST;
    public static final ModConfigSpec.IntValue MAX_TREES_GROWN_PER_HARVEST;

    // --- filters ----------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GLOBAL_SEED_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GLOBAL_SOIL_BLACKLIST;

    /** Per-tier sections, only for tiers whose machine is registered. */
    public static final Map<MachineTier, MachineSettings> MACHINES;

    /** Values of one {@code machines.<tier>} section. */
    public static final class MachineSettings {
        public final ModConfigSpec.IntValue growthTicks;
        public final ModConfigSpec.DoubleValue noWaterSpeedMultiplier;
        public final ModConfigSpec.DoubleValue productionMultiplier;
        public final ModConfigSpec.IntValue internalBufferSlots;
        public final ModConfigSpec.ConfigValue<List<? extends String>> seedBlacklist;
        public final ModConfigSpec.ConfigValue<List<? extends String>> soilBlacklist;
        // Grid tiers only (null on the Starter, whose slots are fixed by the owner's spec).
        public final ModConfigSpec.@Nullable IntValue seedsPerSlot;
        public final ModConfigSpec.@Nullable IntValue soilsPerSlot;
        // Tiers that use energy only.
        public final ModConfigSpec.@Nullable IntValue energyPerPlot;
        // Tiers with replanting only.
        public final ModConfigSpec.@Nullable BooleanValue replant;
        // Tiers with the autocrafter only.
        public final ModConfigSpec.@Nullable IntValue crafterRecipes;
        public final ModConfigSpec.@Nullable IntValue crafterBufferLimit;

        private MachineSettings(ModConfigSpec.Builder b, MachineTier tier, int defaultGrowthTicks,
                                int defaultInternalBufferSlots) {
            MachineLayout layout = MachineLayout.of(tier);
            b.push(tier.getSerializedName());
            growthTicks = b
                    .comment("Ticks for one full growth cycle at 1.0x speed (Water Provider installed, no Growth Speed",
                            "Upgrades, no accelerating soil). 20 ticks = 1 second. Every crop in the machine shares this",
                            "single cycle, regardless of its natural growth time.")
                    .defineInRange("growthTicks", defaultGrowthTicks, 1, 1_728_000);
            // Minimum 0.01, not 0: at 0 the machine would show RUNNING while never advancing, and the owner's state list
            // has no "missing water" state. A mandatory-water mode would need that state first.
            noWaterSpeedMultiplier = b
                    .comment("Speed multiplier while no Water Provider Upgrade is installed. 0.25 = four times slower.",
                            "1.0 makes the Water Provider optional speed-wise.")
                    .defineInRange("noWaterSpeedMultiplier", 0.25, 0.01, 1.0);
            productionMultiplier = b
                    .comment("Yield multiplier for this tier (multiplied with the global one). Independent from speed:",
                            "changes how much each harvest produces, not how often it happens.")
                    .defineInRange("productionMultiplier", 1.0, 0.0, 1000.0);
            // Owner design (step 8): a hidden buffer behind the 9 visible output slots, deleted when the machine breaks.
            internalBufferSlots = b
                    .comment("Hidden output slots behind the 9 visible ones. Harvests fill the visible slots first,",
                            "then these; they refill the visible slots as those empty. Players and pipes cannot see or",
                            "reach them, and their contents are DELETED when the machine is broken (only the 9 visible",
                            "slots drop). When both are full, ripe plots simply wait to be harvested: nothing is lost.",
                            "0 = no hidden slots.")
                    .defineInRange("internalBufferSlots", defaultInternalBufferSlots, 0, 256);
            seedBlacklist = b
                    .comment("Seeds/plantables that this tier refuses, on top of the global blacklist.",
                            "Entries: \"modid:item\", \"modid:*\" (whole mod) or \"#namespace:tag\" (item tag).",
                            "Example: blacklist Diamond Seeds here in Starter..Ionic to allow them only in higher tiers.")
                    .defineListAllowEmpty("seedBlacklist", List.of(), () -> "modid:item", ItemFilter::isValidEntry);
            soilBlacklist = b
                    .comment("Soils that this tier refuses, on top of the global blacklist. Same entry format.")
                    .defineListAllowEmpty("soilBlacklist", List.of(), () -> "modid:item", ItemFilter::isValidEntry);
            if (layout.groups() > 1) {
                // Owner's Entropic spec: 64 plantables / 64 soils per slot, both editable; the plot capacity follows
                // (groups x the smaller of the two), and so does the energy buffer.
                seedsPerSlot = b
                        .comment("Plantables each slot of the seed grid holds. The machine's plot capacity is",
                                "" + layout.groups() + " plot groups x the smaller of seedsPerSlot and soilsPerSlot"
                                        + " (default " + layout.groups() + " x 64 = " + layout.groups() * 64 + ").",
                                "Change these two values to change the capacity. May exceed a stack (e.g. 128).")
                        .defineInRange("seedsPerSlot", 64, 1, 10_000);
                soilsPerSlot = b
                        .comment("Soils each slot of the soil grid holds. See seedsPerSlot.")
                        .defineInRange("soilsPerSlot", 64, 1, 10_000);
            } else {
                seedsPerSlot = null;
                soilsPerSlot = null;
            }
            if (layout.usesEnergy()) {
                energyPerPlot = b
                        .comment("FE per tick each planted plot consumes while the machine grows (owner spec: 90).",
                                "The energy buffer is sized automatically: plot capacity x energyPerPlot x 3, so it",
                                "always holds three ticks of the highest possible consumption.",
                                "Without enough energy for a tick the machine shows MISSING FE and does not grow.")
                        .defineInRange("energyPerPlot", 90, 0, 1_000_000);
            } else {
                energyPerPlot = null;
            }
            if (layout.acceptsInput()) {
                // Owner's Entropic spec: generated plantables look for free soil inside the machine.
                replant = b
                        .comment("If true, each machine's replant button works (on by default): plantables produced by",
                                "the harvest (extra seeds, saplings...) are not exported but planted in the machine, in",
                                "plot groups that already hold the same plantable and still have free soil, then in",
                                "empty seed slots above a soil they grow on. What finds no such soil goes to the output.",
                                "If false, replanting is off everywhere and the button shows DISABLED.")
                        .define("replant", true);
            } else {
                replant = null;
            }
            if (layout.hasCrafter()) {
                crafterRecipes = b
                        .comment("Recipes the autocrafter can hold (owner: up to 100; the list only grows as recipes",
                                "are added).")
                        .defineInRange("crafterRecipes", 100, 1, 100);
                crafterBufferLimit = b
                        .comment("Items of ONE kind the autocrafter keeps while it waits for the rest of a recipe",
                                "(e.g. 5 essences of a recipe that needs 8). Beyond this they go to the output, so an",
                                "ingredient whose partner never comes cannot pile up forever.")
                        .defineInRange("crafterBufferLimit", 1024, 0, 1_000_000);
            } else {
                crafterRecipes = null;
                crafterBufferLimit = null;
            }
            b.pop();
        }

        /** Plot capacity of a grid tier: groups x min(seedsPerSlot, soilsPerSlot); Starter: 64. */
        public int plotCapacity(MachineLayout layout) {
            if (seedsPerSlot == null || soilsPerSlot == null) {
                return MachineSlots.SEED_SOIL_LIMIT;
            }
            return layout.groups() * Math.min(seedsPerSlot.get(), soilsPerSlot.get());
        }
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Growth Speed Upgrades").push("growth");
        GROWTH_BONUS_PER_UPGRADE = b
                .comment("Extra growth speed added by EACH Growth Speed Upgrade (0.5 = +50%). Upgrades add up:",
                        "4 upgrades at 0.5 = 1.0 + 2.0 = 3.0x. Every upgrade tier gives the same bonus; tiers only",
                        "decide which machines accept the upgrade.")
                .defineInRange("bonusPerUpgrade", 0.5, 0.0, 100.0);
        GROWTH_UPGRADES_PER_SLOT = b
                .comment("How many Growth Speed Upgrades fit in EACH of the 4 upgrade slots.",
                        "1 = max 4 upgrades per machine; 3 = max 12.")
                .defineInRange("upgradesPerSlot", 1, 1, 64);
        b.pop();

        b.comment("Hoe slot").push("hoe");
        REQUIRE_HOE = b
                .comment("If true, crops that need tilled soil require a hoe when the soil is not already tilled",
                        "(e.g. wheat on dirt). If false, the hoe slot is never required.")
                .define("requireHoe", true);
        HOE_CONSUMES_DURABILITY = b
                .comment("If true, the hoe wears over time: it loses 1 durability every wearIntervalTicks while the",
                        "machine is RUNNING and actually needs the hoe (the soil must be tilled). Only hoes that have",
                        "durability wear; unbreakable or energy hoes never do. Hoes never consume energy.")
                .define("consumeDurability", false);
        HOE_WEAR_INTERVAL_TICKS = b
                .comment("Ticks of RUNNING between two points of hoe wear (20 ticks = 1 second). Default 1200 = one",
                        "minute: a wooden hoe (59) lasts about 1 hour, iron (250) about 4 hours, diamond (1561) about",
                        "26 hours of machine time. Only used when consumeDurability is true.")
                .defineInRange("wearIntervalTicks", 1200, 1, 1_728_000);
        b.pop();

        b.comment("Output buffer").push("output");
        AUTO_EXPORT_INTERVAL_TICKS = b
                .comment("Ticks between auto-export attempts into adjacent inventories (5, 10, 20, 40...); faces set",
                        "to INPUT pull from adjacent inventories on the same schedule. 0 disables both on every",
                        "machine. Higher values reduce the cost of capability lookups on servers with hundreds of",
                        "machines.")
                .defineInRange("autoExportIntervalTicks", 20, 0, 1200);
        b.pop();

        b.comment("Drops").push("drops");
        GLOBAL_PRODUCTION_MULTIPLIER = b
                .comment("Global yield multiplier for the main product of every harvest (multiplied with the",
                        "per-tier one). 1.0 = each plot gives what one mature plant drops when harvested by hand.",
                        "Only the amount changes; how often the machine harvests is set by growthTicks and upgrades.")
                .defineInRange("productionMultiplier", 1.0, 0.0, 1000.0);
        SECONDARY_DROP_MULTIPLIER = b
                .comment("Multiplier for secondary drops: extra seeds and by-products such as Mystical Agriculture's",
                        "Fertilized Essence. 1.0 = normal, 0.0 = disabled.")
                .defineInRange("secondaryDropMultiplier", 1.0, 0.0, 1000.0);
        // Owner decision (2026-09-28): "each one yields 10 of itself per harvest".
        OTHER_PLANT_YIELD = b
                .comment("Items per plot and harvest for plants without a harvest of their own: flowers, grass,",
                        "ferns, bushes, roots, vines, lily pads, kelp and other aquatic plants... Each plot yields this",
                        "many of the plant itself and the plant stays planted. Also the default amount of the",
                        "fixed_yield data map (Torchflower Seeds and Pitcher Pod yield this many flowers).",
                        "Not used by crops (their real drops), trees (the drops of the whole tree) or Mystical",
                        "Agriculture. The production multipliers apply on top.")
                .defineInRange("otherPlantYield", 10, 0, 1_000_000);
        b.push("mysticalagriculture");
        // Base chances below were read from Mystical Agriculture 9.0.9 (Crop#getSecondaryChance): 10% on any
        // essence farmland, +10% more when the farmland matches the crop tier, 0% on non-essence soil. They can differ
        // if MA changes them or a crop overrides its base chance. Keep the examples in sync if that happens.
        MYSTICAL_SECONDARY_SEED_MULTIPLIER = b
                .comment("MULTIPLIES Mystical Agriculture's own chance of dropping an extra seed. It is NOT a chance",
                        "by itself: final chance = MA chance x this value (capped at 100%).",
                        "MA's chances (MA 9.0.x defaults):",
                        "  - soil is not an essence farmland ............. 0%  (no multiplier can raise it)",
                        "  - any essence farmland ........................ 10%",
                        "  - farmland of the SAME tier as the crop ....... 20% (e.g. Inferium crop on Inferium Farmland)",
                        "Examples:",
                        "  1.0 -> MA's normal chances: 10% / 20%",
                        "  0.0 -> never drops an extra seed",
                        "  0.5 -> half: 5% / 10%",
                        "  1.5 -> 15% / 30%   (use this if you want 30% on the matching farmland)",
                        "  3.0 -> 30% / 60%   (use this if you want 30% on any essence farmland)",
                        "  5.0 -> 50% / 100%",
                        "To get X% on the matching farmland use X / 20 (e.g. 30% -> 1.5); on any essence farmland",
                        "use X / 10 (e.g. 30% -> 3.0).",
                        "Only the extra SEED is affected. MA's separate chance of an extra ESSENCE (same percentages)",
                        "is production and stays as in MA (scale it with the production multipliers instead).",
                        "The extra seed is also affected by drops.secondaryDropMultiplier.",
                        "Mystical Agriculture's own config option secondarySeedDrops = false disables extra seeds.")
                .defineInRange("secondarySeedChanceMultiplier", 1.0, 0.0, 100.0);
        b.pop(2);

        b.comment("Mystical Agriculture crops (ignored when Mystical Agriculture is not installed)")
                .push("mysticalagriculture");
        // Mirrors MA 9.0.9's own rule (MysticalCropBlock#canGrow, applied when MA's option of the same name is on).
        // VFW has its OWN switch on purpose (owner decision): machines ignore MA's setting unless the pack maker turns
        // this one on too. Same key name as MA's so a search for it in the config folder finds both.
        MYSTICAL_REQUIRES_EFFECTIVE_FARMLAND = b
                .comment("If true, Mystical Agriculture seeds only grow in a machine whose soil is the farmland of",
                        "their own tier (e.g. Imperium seeds need Imperium Farmland; Supremium Farmland does not count",
                        "for them). Any other soil shows INVALID SOIL. Same rule as Mystical Agriculture's own option",
                        "requiresEffectiveFarmland for crops planted in the world, with the same exceptions:",
                        "  - Inferium Seeds are exempt and grow on any farmland.",
                        "  - Blocks in the block tag #mysticalagriculture:always_effective_farmland count as the right",
                        "    farmland for every tier (Awakened Supremium Farmland by default).",
                        "This is VFW's own switch: machines do NOT follow Mystical Agriculture's option automatically.",
                        "Set both to true to apply the rule in the world and in the machines.")
                .define("requiresEffectiveFarmland", false);
        b.pop();

        b.comment("Server performance").push("performance");
        MAX_LOOT_ROLLS_PER_HARVEST = b
                .comment("Maximum loot-table evaluations per harvest. A machine with more ACTIVE plots than this",
                        "evaluates this many and scales the result (e.g. 6000 plots, 64 rolls: each roll counts for",
                        "93.75 plots). The expected yield stays exact; only the randomness of a single harvest is",
                        "coarser. Lower = cheaper harvests on huge machines. Mystical Agriculture crops do not use",
                        "loot tables and are always computed exactly.")
                .defineInRange("maxLootRollsPerHarvest", 64, 1, 4096);
        MAX_TREES_GROWN_PER_HARVEST = b
                .comment("Trees grown (in memory, never in the world) per harvest of a sapling or fungus. Each grown",
                        "tree stands for plots / trees plots (e.g. 64 saplings, 4 trees: each tree counts 16 times).",
                        "The expected yield stays exact; only the randomness of a single harvest is coarser (tree",
                        "heights and shapes vary). Growing a tree costs about as much as tens of loot rolls, so keep",
                        "this low on busy servers. The blocks of the grown trees then share maxLootRollsPerHarvest.")
                .defineInRange("maxTreesGrownPerHarvest", 4, 1, 256);
        b.pop();

        b.comment("Blacklists. By default everything is allowed; list what is NOT allowed.").push("filters");
        GLOBAL_SEED_BLACKLIST = b
                .comment("Seeds/plantables refused by every machine.",
                        "Entries: \"modid:item\", \"modid:*\" (whole mod) or \"#namespace:tag\" (item tag).")
                .defineListAllowEmpty("seedBlacklist", List.of(), () -> "modid:item", ItemFilter::isValidEntry);
        GLOBAL_SOIL_BLACKLIST = b
                .comment("Soils refused by every machine. Same entry format.")
                .defineListAllowEmpty("soilBlacklist", List.of(), () -> "modid:item", ItemFilter::isValidEntry);
        b.pop();

        b.comment("Per-tier machine settings").push("machines");
        Map<MachineTier, MachineSettings> machines = new EnumMap<>(MachineTier.class);
        // Starter: 30 s per cycle and 27 hidden output slots (owner decisions). Entropic: same cycle ("the rest like the
        // Starter", owner), 72 hidden slots = 3 per visible slot like the Starter's 27 for 9. Add the other tiers here
        // when their specs arrive.
        machines.put(MachineTier.STARTER, new MachineSettings(b, MachineTier.STARTER, 600, 27));
        machines.put(MachineTier.ENTROPIC, new MachineSettings(b, MachineTier.ENTROPIC, 600, 72));
        MACHINES = Map.copyOf(machines);
        b.pop();

        SPEC = b.build();
    }

    private VfwServerConfig() {
    }

    /**
     * Settings of a machine tier. Throws if the tier has no machine yet, which is a programming error (a machine was
     * registered without adding its config section above).
     */
    public static MachineSettings machine(MachineTier tier) {
        MachineSettings settings = MACHINES.get(tier);
        if (settings == null) {
            throw new IllegalStateException("No config section for machine tier " + tier.getSerializedName());
        }
        return settings;
    }
}
