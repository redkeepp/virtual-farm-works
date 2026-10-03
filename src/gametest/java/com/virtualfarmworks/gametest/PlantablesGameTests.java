/*
 * PlantablesGameTests — game tests of the plantables expansion (owner decisions 2026-09-28): every vanilla plantable is
 * accepted, soils for the new plants (universal soils, natural soils, no soil at all), fixed yields (10 of itself, 10
 * flowers), trees grown in memory, and placed machines running a plant without soil and a tree.
 */
package com.virtualfarmworks.gametest;

import java.util.List;

import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.harvest.DropSource;
import com.virtualfarmworks.harvest.HarvestPlans;
import com.virtualfarmworks.harvest.Harvester;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineInventory;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.plant.PlantAnalysis;
import com.virtualfarmworks.plant.PlantAnalysis.Status;
import com.virtualfarmworks.plant.PlantRules;
import com.virtualfarmworks.plant.SoilRules;
import com.virtualfarmworks.registry.ModBlocks;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.sim.MachineStatus;
import com.virtualfarmworks.transfer.ItemResource;
import com.virtualfarmworks.transfer.ItemSlots;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class PlantablesGameTests {
    /** Where the machine stands: y 1, the test area's first layer (relative y 0 is the test's structure block). */
    private static final BlockPos MACHINE = new BlockPos(0, 1, 0);

    private PlantablesGameTests() {
    }

    /**
     * Seed/soil pairing of the new plants: universal soils (dirt, grass, mud, any farmland), natural soils kept, the
     * dropped conditions (water, nylium), crop-like plants keeping the hoe, and the plants that need no soil at all.
     */
    static void rules(GameTestHelper helper) {
        // Flowers, saplings, grass: universal soils, never a hoe, natural soils kept, sand is not a soil for them.
        expect(helper, Items.DANDELION, Items.DIRT, Status.VALID, false);
        expect(helper, Items.POPPY, Items.MUD, Status.VALID, false);
        expect(helper, Items.OAK_SAPLING, Items.FARMLAND, Status.VALID, false);
        expect(helper, Items.OAK_SAPLING, Items.SAND, Status.INVALID_SOIL, false);
        expect(helper, Items.SHORT_GRASS, Items.PODZOL, Status.VALID, false);
        expect(helper, Items.DEAD_BUSH, Items.SAND, Status.VALID, false);          // natural soil kept
        expect(helper, Items.DEAD_BUSH, Items.DIRT, Status.VALID, false);
        expect(helper, Items.AZALEA, Items.CLAY, Status.VALID, false);             // natural soil kept
        expect(helper, Items.WITHER_ROSE, Items.NETHERRACK, Status.VALID, false);  // natural soil kept
        expect(helper, Items.BIG_DRIPLEAF, Items.CLAY, Status.VALID, false);
        // Conditions dropped: no water for aquatic plants, no nylium for the fungi, a soil is enough.
        expect(helper, Items.KELP, Items.DIRT, Status.VALID, false);
        expect(helper, Items.SEAGRASS, Items.GRASS_BLOCK, Status.VALID, false);
        expect(helper, Items.SEA_PICKLE, Items.SAND, Status.VALID, false);
        expect(helper, Items.CRIMSON_FUNGUS, Items.DIRT, Status.VALID, false);
        expect(helper, Items.WARPED_FUNGUS, Items.WARPED_NYLIUM, Status.VALID, false);
        expect(helper, Items.GLOW_LICHEN, Items.DIRT, Status.VALID, false);
        // Crops in all but class keep needing tilled soil.
        expect(helper, Items.TORCHFLOWER_SEEDS, Items.FARMLAND, Status.VALID, false);
        expect(helper, Items.TORCHFLOWER_SEEDS, Items.DIRT, Status.VALID, true);
        expect(helper, Items.PITCHER_POD, Items.FARMLAND, Status.VALID, false);
        expect(helper, Items.PITCHER_POD, Items.DIRT, Status.VALID, true);
        // No soil needed: the soil slot is ignored, empty or not (owner).
        for (Item plant : List.of(Items.LILY_PAD, Items.SMALL_DRIPLEAF, Items.VINE, Items.WEEPING_VINES,
                Items.SPORE_BLOSSOM, Items.HANGING_ROOTS)) {
            expect(helper, plant, Items.AIR, Status.VALID, false);
            expect(helper, plant, Items.SOUL_SAND, Status.VALID, false);
            PlantAnalysis analysis = PlantAnalysis.analyze(new ItemStack(plant), ItemStack.EMPTY, MachineTier.STARTER);
            check(helper, !analysis.needsSoil() && analysis.soilSpeedMultiplier() == 1.0,
                    name(plant) + " must need no soil and get no soil bonus, got " + analysis);
        }
        check(helper, PlantAnalysis.analyze(new ItemStack(Items.POPPY), new ItemStack(Items.DIRT), MachineTier.STARTER)
                .needsSoil(), "a poppy must need a soil");
        helper.succeed();
    }

    /** What the seed and soil slots accept now: every plantable, the new natural soils, still no ice, glass or stone. */
    static void slots(GameTestHelper helper) {
        // 1.21.1 has none of the later plants (pale garden, eyeblossoms, leaf litter, dry grass, firefly bush...).
        for (Item plantable : List.of(Items.OAK_SAPLING, Items.MANGROVE_PROPAGULE, Items.AZALEA, Items.CRIMSON_FUNGUS,
                Items.POPPY, Items.SUNFLOWER, Items.PINK_PETALS, Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD,
                Items.KELP, Items.LILY_PAD, Items.NETHER_SPROUTS, Items.TWISTING_VINES, Items.VINE, Items.GLOW_LICHEN,
                Items.SPORE_BLOSSOM, Items.BIG_DRIPLEAF)) {
            check(helper, PlantRules.isPlantable(new ItemStack(plantable)), name(plantable) + " must be plantable");
        }
        for (Item notAPlant : List.of(Items.MOSS_CARPET, Items.TUBE_CORAL, Items.TUBE_CORAL_FAN, Items.MOSS_BLOCK,
                Items.MANGROVE_ROOTS)) {
            check(helper, !PlantRules.isPlantable(new ItemStack(notAPlant)), name(notAPlant) + " must not be plantable");
        }
        // Natural soils of the new plants become soils; "any solid surface" rules and soil-less plants add nothing.
        for (Item soil : List.of(Items.CLAY, Items.TERRACOTTA, Items.NETHERRACK, Items.SOUL_SOIL, Items.MUD,
                Items.CRIMSON_NYLIUM)) {
            check(helper, SoilRules.isAcceptableSoil(new ItemStack(soil)), name(soil) + " must be a soil");
        }
        for (Item notASoil : List.of(Items.ICE, Items.GLASS, Items.DIAMOND_BLOCK, Items.COBBLESTONE, Items.OAK_LOG,
                Items.POPPY, Items.BIG_DRIPLEAF)) {
            check(helper, !SoilRules.isAcceptableSoil(new ItemStack(notASoil)), name(notASoil) + " must not be a soil");
        }
        helper.succeed();
    }

    /**
     * Plants without a harvest of their own yield config drops.otherPlantYield (10) of themselves per plot; Torchflower
     * Seeds and Pitcher Pod yield 10 flowers (fixed_yield data map) and keep their seed; the config value is honored.
     */
    static void fixedYields(GameTestHelper helper) {
        expectHarvest(helper, Items.POPPY, Items.DIRT, Items.POPPY, 100);
        expectHarvest(helper, Items.KELP, Items.DIRT, Items.KELP, 100);
        expectHarvest(helper, Items.SHORT_GRASS, Items.DIRT, Items.SHORT_GRASS, 100);
        expectHarvest(helper, Items.LILY_PAD, Items.AIR, Items.LILY_PAD, 100);
        expectHarvest(helper, Items.WITHER_ROSE, Items.SOUL_SAND, Items.WITHER_ROSE, 100);
        expectHarvest(helper, Items.TORCHFLOWER_SEEDS, Items.FARMLAND, Items.TORCHFLOWER, 100);
        expectHarvest(helper, Items.TORCHFLOWER_SEEDS, Items.FARMLAND, Items.TORCHFLOWER_SEEDS, 0);
        expectHarvest(helper, Items.PITCHER_POD, Items.FARMLAND, Items.PITCHER_PLANT, 100);
        expectHarvest(helper, Items.PITCHER_POD, Items.FARMLAND, Items.PITCHER_POD, 0);

        // The config is read when the drop source is built (on revalidation). Restored in the same tick: tests of a
        // batch run together and must not see it (ConfigValue#set is in memory only).
        int configured = VfwServerConfig.OTHER_PLANT_YIELD.get();
        DropSource three;
        try {
            VfwServerConfig.OTHER_PLANT_YIELD.set(3);
            three = HarvestPlans.create(new ItemStack(Items.POPPY), new ItemStack(Items.DIRT));
        } finally {
            VfwServerConfig.OTHER_PLANT_YIELD.set(configured);
        }
        check(helper, three != null && amount(roll(helper, three, 10, 64), Items.POPPY) == 30,
                "otherPlantYield = 3 must yield 3 poppies per plot");
        helper.succeed();
    }

    /**
     * Every vanilla tree plant grows a tree in memory and yields its wood (one sapling is enough, dark oak included);
     * oak leaves broken by hand give saplings; fungi grow on dirt (nylium condition dropped).
     */
    static void treesGrow(GameTestHelper helper) {
        expectWood(helper, Items.OAK_SAPLING, Items.DIRT, Items.OAK_LOG);
        expectWood(helper, Items.SPRUCE_SAPLING, Items.DIRT, Items.SPRUCE_LOG);
        expectWood(helper, Items.BIRCH_SAPLING, Items.GRASS_BLOCK, Items.BIRCH_LOG);
        expectWood(helper, Items.JUNGLE_SAPLING, Items.DIRT, Items.JUNGLE_LOG);
        expectWood(helper, Items.ACACIA_SAPLING, Items.DIRT, Items.ACACIA_LOG);
        expectWood(helper, Items.DARK_OAK_SAPLING, Items.DIRT, Items.DARK_OAK_LOG);
        expectWood(helper, Items.CHERRY_SAPLING, Items.DIRT, Items.CHERRY_LOG);
        expectWood(helper, Items.MANGROVE_PROPAGULE, Items.MUD, Items.MANGROVE_LOG);
        expectWood(helper, Items.AZALEA, Items.DIRT, Items.OAK_LOG);
        expectWood(helper, Items.FLOWERING_AZALEA, Items.CLAY, Items.OAK_LOG);
        expectWood(helper, Items.CRIMSON_FUNGUS, Items.DIRT, Items.CRIMSON_STEM);
        expectWood(helper, Items.WARPED_FUNGUS, Items.DIRT, Items.WARPED_STEM);
        expectWood(helper, Items.CRIMSON_FUNGUS, Items.DIRT, Items.NETHER_WART_BLOCK);
        expectWood(helper, Items.WARPED_FUNGUS, Items.DIRT, Items.WARPED_WART_BLOCK);

        // 4 oak trees, every leaf rolled once (big budget): ~240 leaves at 5% make a sapling-free harvest practically
        // impossible, so this checks the leaves are broken by hand (no shears: saplings, not leaves).
        DropSource oak = HarvestPlans.create(new ItemStack(Items.OAK_SAPLING), new ItemStack(Items.DIRT));
        check(helper, oak != null, "no drop source for the oak sapling");
        List<DropTally.Entry<ItemResource>> drops = roll(helper, oak, 4, 4096);
        check(helper, amount(drops, Items.OAK_SAPLING) > 0, "oak leaves must drop saplings: " + drops);
        check(helper, amount(drops, Items.OAK_LEAVES) == 0, "leaves broken by hand must not drop leaves: " + drops);
        long logs = amount(drops, Items.OAK_LOG);
        check(helper, logs >= 12 && logs <= 250, "4 oak trees must give 12..250 logs, got " + logs);
        helper.succeed();
    }

    /** A placed machine with lily pads and no soil runs, has one plot per lily pad, and yields 10 per plot. */
    static void machineWithoutSoil(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, MachineSlots.SEED, Items.LILY_PAD, 5);
        machine.revalidate();
        check(helper, machine.status() == MachineStatus.RUNNING, "expected RUNNING without soil, got "
                + machine.status());
        check(helper, machine.totalPlots() == 5, "expected 5 plots (the seeds), got " + machine.totalPlots());

        // A soil in the slot changes nothing: plots stay the seeds, not min(seeds, soils).
        put(inputs, MachineSlots.SOIL, Items.SOUL_SAND, 2);
        machine.revalidate();
        check(helper, machine.status() == MachineStatus.RUNNING && machine.totalPlots() == 5,
                "a soil must be ignored, got " + machine.status() + " with " + machine.totalPlots() + " plots");

        disableAllOutputs(machine);
        machine.setProgressForTesting(1.0);
        helper.succeedWhen(() -> check(helper, count(machine, Items.LILY_PAD) == 50,
                "waiting for 50 lily pads, have " + count(machine, Items.LILY_PAD)));
    }

    /** A placed machine with two oak saplings on dirt harvests oak logs into its output. */
    static void machineGrowsTrees(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, MachineSlots.SEED, Items.OAK_SAPLING, 2);
        put(inputs, MachineSlots.SOIL, Items.DIRT, 2);
        machine.revalidate();
        check(helper, machine.status() == MachineStatus.RUNNING, "expected RUNNING, got " + machine.status());
        disableAllOutputs(machine);
        machine.setProgressForTesting(1.0);
        helper.succeedWhen(() -> check(helper, count(machine, Items.OAK_LOG) >= 6,
                "waiting for the oak logs of 2 trees, have " + count(machine, Items.OAK_LOG)));
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static void expect(GameTestHelper helper, Item seed, Item soil, Status status, boolean needsHoe) {
        PlantAnalysis analysis = PlantAnalysis.analyze(new ItemStack(seed), new ItemStack(soil), MachineTier.STARTER);
        String pair = name(seed) + " on " + name(soil);
        check(helper, analysis.status() == status, pair + ": expected " + status + ", got " + analysis.status());
        check(helper, analysis.needsHoe() == needsHoe, pair + ": expected needsHoe=" + needsHoe);
    }

    /** 10 plots, default config: exactly {@code expected} of {@code product}. */
    private static void expectHarvest(GameTestHelper helper, Item seed, Item soil, Item product, long expected) {
        DropSource source = HarvestPlans.create(new ItemStack(seed), new ItemStack(soil));
        check(helper, source != null, name(seed) + ": no drop source");
        long amount = amount(roll(helper, source, 10, 64), product);
        check(helper, amount == expected, name(seed) + " on " + name(soil) + ": expected " + expected + " "
                + name(product) + ", got " + amount);
    }

    /** 4 plots of a tree plant yield some of {@code wood}. */
    private static void expectWood(GameTestHelper helper, Item seed, Item soil, Item wood) {
        DropSource source = HarvestPlans.create(new ItemStack(seed), new ItemStack(soil));
        check(helper, source != null, name(seed) + ": no drop source");
        List<DropTally.Entry<ItemResource>> drops = roll(helper, source, 4, 64);
        check(helper, amount(drops, wood) > 0, name(seed) + " on " + name(soil) + ": expected some " + name(wood)
                + ", got " + drops);
    }

    private static List<DropTally.Entry<ItemResource>> roll(GameTestHelper helper, DropSource source, int plots,
                                                            int maxLootRolls) {
        var level = helper.getLevel();
        return Harvester.roll(source, plots, MachineTier.STARTER,
                new DropSource.Context(level, helper.absolutePos(BlockPos.ZERO), level.getRandom(), maxLootRolls, true));
    }

    private static long amount(List<DropTally.Entry<ItemResource>> drops, Item item) {
        return drops.stream().filter(drop -> drop.key().is(item)).mapToLong(DropTally.Entry::amount).sum();
    }

    private static FarmMatrixBlockEntity placeMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.STARTER_FARM_MATRIX.get().defaultBlockState());
        return helper.<FarmMatrixBlockEntity>getBlockEntity(MACHINE);
    }

    private static void put(MachineInventory inputs, int slot, Item item, int count) {
        inputs.set(slot, ItemResource.of(item), count);
    }

    private static void disableAllOutputs(FarmMatrixBlockEntity machine) {
        for (RelativeSide side : RelativeSide.all()) {
            if (machine.isOutputEnabled(side)) {
                machine.toggleOutput(side);
            }
        }
    }

    /** Amount of an item in the visible and hidden output slots. */
    private static long count(FarmMatrixBlockEntity machine, Item item) {
        return countIn(machine.output(), item) + countIn(machine.internalOutput(), item);
    }

    private static long countIn(ItemSlots handler, Item item) {
        long total = 0;
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getResource(i).is(item)) {
                total += handler.getAmountAsLong(i);
            }
        }
        return total;
    }

    private static String name(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
