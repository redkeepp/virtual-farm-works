package com.virtualfarmworks.config;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

    // --- output -----------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.IntValue AUTO_EXPORT_INTERVAL_TICKS;

    // --- drops ------------------------------------------------------------------------------------------------------
    public static final ModConfigSpec.DoubleValue GLOBAL_PRODUCTION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SECONDARY_DROP_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MYSTICAL_SECONDARY_SEED_MULTIPLIER;

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
        public final ModConfigSpec.ConfigValue<List<? extends String>> seedBlacklist;
        public final ModConfigSpec.ConfigValue<List<? extends String>> soilBlacklist;

        private MachineSettings(ModConfigSpec.Builder b, MachineTier tier, int defaultGrowthTicks) {
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
            seedBlacklist = b
                    .comment("Seeds/plantables that this tier refuses, on top of the global blacklist.",
                            "Entries: \"modid:item\", \"modid:*\" (whole mod) or \"#namespace:tag\" (item tag).",
                            "Example: blacklist Diamond Seeds here in Starter..Ionic to allow them only in higher tiers.")
                    .defineListAllowEmpty("seedBlacklist", List.of(), () -> "modid:item", ItemFilter::isValidEntry);
            soilBlacklist = b
                    .comment("Soils that this tier refuses, on top of the global blacklist. Same entry format.")
                    .defineListAllowEmpty("soilBlacklist", List.of(), () -> "modid:item", ItemFilter::isValidEntry);
            b.pop();
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
                .comment("If true, the hoe loses 1 durability per harvest cycle (only hoes that have durability;",
                        "unbreakable or energy hoes are never worn). Hoes never consume energy.")
                .define("consumeDurability", false);
        b.pop();

        b.comment("Output buffer").push("output");
        AUTO_EXPORT_INTERVAL_TICKS = b
                .comment("Ticks between auto-export attempts into adjacent inventories (5, 10, 20, 40...).",
                        "0 disables auto-export on every machine. Higher values reduce the cost of capability lookups",
                        "on servers with hundreds of machines.")
                .defineInRange("autoExportIntervalTicks", 20, 0, 1200);
        b.pop();

        b.comment("Drops").push("drops");
        GLOBAL_PRODUCTION_MULTIPLIER = b
                .comment("Global yield multiplier for the main product of every harvest (multiplied with the",
                        "per-tier one). 1.0 = the same as an equivalent physical farm.")
                .defineInRange("productionMultiplier", 1.0, 0.0, 1000.0);
        SECONDARY_DROP_MULTIPLIER = b
                .comment("Multiplier for secondary drops: extra seeds and by-products such as Mystical Agriculture's",
                        "Fertilized Essence. 1.0 = normal, 0.0 = disabled.")
                .defineInRange("secondaryDropMultiplier", 1.0, 0.0, 1000.0);
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
                        "The extra seed is also affected by drops.secondaryDropMultiplier.")
                .defineInRange("secondarySeedChanceMultiplier", 1.0, 0.0, 100.0);
        b.pop(2);

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
        // Starter: 30 s per cycle (owner decision). Add other tiers here when their specs arrive.
        machines.put(MachineTier.STARTER, new MachineSettings(b, MachineTier.STARTER, 600));
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
