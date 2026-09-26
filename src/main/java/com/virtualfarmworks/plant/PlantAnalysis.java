/*
 * PlantAnalysis — pairs a machine's seed slot with its soil slot: MISSING_SEED / MISSING_SOIL / INVALID_SOIL / VALID,
 * plus whether a hoe or crux is required and the soil's speed bonus. Computed on revalidation, never per tick.
 */
package com.virtualfarmworks.plant;

import com.virtualfarmworks.compat.mysticalagriculture.MysticalCompat;
import com.virtualfarmworks.config.VfwConfig;
import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.data.ModDataMaps;
import com.virtualfarmworks.machine.MachineTier;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Result of pairing the seed slot with the soil slot of a machine: can this seed grow on this soil, and what else does
 * it need? Computed ONLY when the machine revalidates (seed/soil slot change, config generation change, tag reload),
 * then cached by the machine — never per tick.
 *
 * <p>This only covers the seed/soil pair. Whether a hoe or Crux Provider is actually installed, energy, output space
 * and the on/off switch are checked by the machine, which combines them into the final state shown in the GUI.
 *
 * @param status             see {@link Status}
 * @param needsHoe           the soil must be tilled for this plant and a hoe is required (false when the soil is
 *                           already suitable, or when the config disables the hoe requirement)
 * @param needsCrux          the seed is a Mystical Agriculture crop with a crux requirement
 * @param soilSpeedMultiplier growth speed multiplier granted by the soil ({@code 1.0} = no bonus), from the
 *                           {@code soil_properties} data map
 */
public record PlantAnalysis(Status status, boolean needsHoe, boolean needsCrux, double soilSpeedMultiplier) {

    /** Outcome of the seed/soil pairing, in the GUI's priority order. */
    public enum Status {
        /** Seed slot empty, not plantable, or blacklisted for this tier. */
        MISSING_SEED,
        /** Soil slot empty, not a soil, or blacklisted for this tier. */
        MISSING_SOIL,
        /**
         * Both present, but this plant cannot grow on this soil even with a hoe (e.g. wheat on soul sand), or the soil
         * breaks Mystical Agriculture's effective-farmland rule while VFW's switch for it is on.
         */
        INVALID_SOIL,
        /** The pair can grow (possibly needing a hoe and/or crux, see the flags). */
        VALID
    }

    private static final PlantAnalysis MISSING_SEED = new PlantAnalysis(Status.MISSING_SEED, false, false, 1.0);
    private static final PlantAnalysis MISSING_SOIL = new PlantAnalysis(Status.MISSING_SOIL, false, false, 1.0);
    private static final PlantAnalysis INVALID_SOIL = new PlantAnalysis(Status.INVALID_SOIL, false, false, 1.0);

    public boolean isValid() {
        return status == Status.VALID;
    }

    /**
     * Analyzes a seed/soil pair for a machine tier. Server-side (reads the server config); stack counts are ignored,
     * only the item types matter.
     */
    public static PlantAnalysis analyze(ItemStack seed, ItemStack soil, MachineTier tier) {
        // A blacklisted item already inside a machine (config changed after insertion) is treated as missing: it stays
        // in the slot so the player can take it back, but it never grows.
        if (!PlantRules.isPlantable(seed) || VfwConfig.isSeedBlacklisted(seed, tier)) {
            return MISSING_SEED;
        }
        if (!SoilRules.isAcceptableSoil(soil) || VfwConfig.isSoilBlacklisted(soil, tier)) {
            return MISSING_SOIL;
        }

        Block plantBlock = PlantRules.plantBlock(seed);
        BlockState soilState = SoilRules.soilState(soil);
        if (plantBlock == null || soilState == null) {
            return MISSING_SEED; // unreachable after the checks above; keeps the null analysis honest
        }
        BlockState plant = plantBlock.defaultBlockState();

        boolean tilled;
        if (PlantRules.canGrowOn(plant, soilState)) {
            tilled = false;                             // e.g. wheat on farmland, sugar cane on dirt
        } else if (SoilRules.isTillable(soil) && PlantRules.canGrowOn(plant, SoilRules.TILLED_SOIL)) {
            tilled = true;                              // e.g. wheat on dirt
        } else {
            return INVALID_SOIL;                        // e.g. wheat on soul sand, nether wart on dirt
        }

        // Mystical Agriculture's effective-farmland rule, only when the pack maker turns on VFW's switch for it (off by
        // default): e.g. Imperium seeds on Inferium Farmland become INVALID SOIL. Tested against the block the plant
        // actually stands on: the soil itself, or the farmland it becomes once tilled.
        if (VfwServerConfig.MYSTICAL_REQUIRES_EFFECTIVE_FARMLAND.get()) {
            Block ground = tilled ? SoilRules.TILLED_SOIL.getBlock() : soilState.getBlock();
            if (!MysticalCompat.isEffectiveFarmland(seed, ground)) {
                return INVALID_SOIL;
            }
        }

        boolean needsHoe = tilled && VfwServerConfig.REQUIRE_HOE.get();
        return new PlantAnalysis(Status.VALID, needsHoe, MysticalCompat.requiresCrux(seed),
                ModDataMaps.soilSpeedMultiplier(soil));
    }
}
