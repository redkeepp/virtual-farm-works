/*
 * MysticalDropSource — harvest drops of Mystical Agriculture (and Agradditions) crops, reproducing MA's own drop
 * formula with the farmland from the machine's soil slot instead of the block under a real crop. Only loaded when MA is
 * installed (created through MysticalCompat).
 */
package com.virtualfarmworks.compat.mysticalagriculture;

import org.jetbrains.annotations.Nullable;

import com.blakebr0.mysticalagriculture.api.crop.Crop;
import com.blakebr0.mysticalagriculture.api.farmland.IEssenceFarmland;
import com.blakebr0.mysticalagriculture.block.InferiumCropBlock;
import com.blakebr0.mysticalagriculture.config.ModConfigs;
import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.harvest.DropSource;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.sim.DropTally.Category;
import com.virtualfarmworks.sim.HarvestMath;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Why not simply call MA's {@code getDrops}: MA reads {@code level.getBlockState(origin.below())} to find the farmland,
 * and a virtual plot has no block below it. So the formula is reproduced here, fed with the SOIL SLOT's block. The game
 * test {@code mystical_drops_match_ma} compares this class against MA's real {@code getDrops} (with a real farmland in
 * the test world) so an MA update that changes the formula is caught.
 *
 * <h2>MA 9.0.9 formula, per mature crop (read from its bytecode)</h2>
 * With {@code c = crop.getSecondaryChance(soilBlock)} (0 on plain soil, 10% on any essence farmland, +10% on the
 * crop's own tier farmland):
 * <ul>
 *   <li>Resource crops ({@code MysticalCropBlock}): essence = 1, or 2 with chance {@code c}; seeds = 1, or 2 with
 *       chance {@code c} if MA config {@code secondarySeedDrops}; Fertilized Essence with chance MA config
 *       {@code fertilizedEssenceChance}. The three rolls are independent.</li>
 *   <li>Inferium crop ({@code InferiumCropBlock}): on an essence farmland of tier value {@code v},
 *       essence = {@code (int)(0.5 * v + 0.5)}, plus 1 with 50% chance when {@code v} is even and above 1 (so tiers
 *       1..5 give 1, 1.5, 2, 2.5, 3 on average); 1 on other soils. Seeds as above. Never Fertilized Essence.</li>
 * </ul>
 *
 * <h2>What VFW changes (owner rules)</h2>
 * <ul>
 *   <li>Each machine has a Fertilized Essence switch (GUI): OFF means no Fertilized Essence at all.</li>
 *   <li>The base seed is the replanting cost and is not output: only the EXTRA seed can come out.</li>
 *   <li>The extra-seed chance is {@code c x drops.mysticalagriculture.secondarySeedChanceMultiplier}, capped at 100%.
 *       The extra-essence chance stays MA's own {@code c} (it is production, not a seed).</li>
 *   <li>Essence is MAIN; the extra seed and Fertilized Essence are SECONDARY (see {@code DropTally}).</li>
 * </ul>
 * Per-plot chances are drawn with {@link HarvestMath#countSuccesses} (one cheap random number per plot, no loot table),
 * so MA harvests are exact even for thousands of plots.
 */
final class MysticalDropSource implements DropSource {
    private static final ResourceLocation FERTILIZED_ESSENCE =
            ResourceLocation.fromNamespaceAndPath("mysticalagriculture", "fertilized_essence");

    private final Crop crop;
    private final boolean inferium;
    private final Block soil;
    private final ItemResource essence;
    private final ItemResource seed;
    private final @Nullable ItemResource fertilizedEssence;

    MysticalDropSource(Crop crop, Block soil) {
        this.crop = crop;
        this.inferium = crop.getCropBlock() instanceof InferiumCropBlock;
        this.soil = soil;
        this.essence = ItemResource.of(crop.getEssenceItem());
        this.seed = ItemResource.of(crop.getSeedsItem());
        Item fertilized = BuiltInRegistries.ITEM.get(FERTILIZED_ESSENCE);
        this.fertilizedEssence = fertilized == Items.AIR ? null : ItemResource.of(fertilized);
    }

    @Override
    public void roll(int plots, Context context, DropTally<ItemResource> tally) {
        if (plots <= 0) {
            return;
        }
        var random = context.random();
        double chance = crop.getSecondaryChance(soil);

        // Each part is computed only if the machine's harvest filter lets it through (owner: filtered items are never
        // generated at all); the tally would ignore them anyway.
        if (tally.allows(essence)) {
            long essenceCount = inferium
                    ? inferiumEssence(plots, random::nextDouble)
                    : plots + HarvestMath.countSuccesses(plots, chance, random::nextDouble);
            tally.add(essence, essenceCount, Category.MAIN);
        }

        if (tally.allows(seed)) {
            double seedChance = secondarySeedsEnabled()
                    ? Math.min(1.0, chance * VfwServerConfig.MYSTICAL_SECONDARY_SEED_MULTIPLIER.get())
                    : 0.0;
            tally.add(seed, HarvestMath.countSuccesses(plots, seedChance, random::nextDouble), Category.SECONDARY);
        }

        // Fertilized Essence: never from Inferium crops (MA rule), and never when the machine's switch is OFF.
        if (!inferium && fertilizedEssence != null && context.fertilizedEssence() && tally.allows(fertilizedEssence)) {
            tally.add(fertilizedEssence,
                    HarvestMath.countSuccesses(plots, fertilizedEssenceChance(), random::nextDouble),
                    Category.SECONDARY);
        }
    }

    /** Inferium crop essence for {@code plots} plots on this soil (see class doc). */
    private long inferiumEssence(int plots, java.util.function.DoubleSupplier random) {
        if (!(soil instanceof IEssenceFarmland farmland)) {
            return plots;
        }
        int tierValue = farmland.getTier().getValue();
        long total = (long) plots * (int) (0.5 * tierValue + 0.5);
        if (tierValue > 1 && tierValue % 2 == 0) {
            total += HarvestMath.countSuccesses(plots, 0.5, random);
        }
        return total;
    }

    // --- MA config -------------------------------------------------------------------------------------------------
    // ModConfigs is MA's internal (non-API) class. If a future MA version moves it, fall back to MA's defaults instead
    // of crashing; the compile against the pinned MA version will also flag the change.

    private static volatile boolean warnedAboutConfig;

    private static boolean secondarySeedsEnabled() {
        try {
            return ModConfigs.SECONDARY_SEED_DROPS.get();
        } catch (LinkageError | RuntimeException e) {
            warnConfigOnce(e);
            return true;
        }
    }

    private static double fertilizedEssenceChance() {
        try {
            return ModConfigs.FERTILIZED_ESSENCE_DROP_CHANCE.get();
        } catch (LinkageError | RuntimeException e) {
            warnConfigOnce(e);
            return 0.1;
        }
    }

    private static void warnConfigOnce(Throwable e) {
        if (!warnedAboutConfig) {
            warnedAboutConfig = true;
            VirtualFarmWorks.LOGGER.warn("Could not read Mystical Agriculture's config; using MA's defaults "
                    + "(secondary seeds on, 10% Fertilized Essence). This is logged once.", e);
        }
    }
}
