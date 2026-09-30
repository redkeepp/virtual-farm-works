/*
 * VfwGameTests — automated in-game tests (development only), run headless by `gradlew runGameTestServer`: plant/soil
 * pairing, slot rules, harvest yields, all-or-nothing storage and the Mystical Agriculture integration, against real
 * registries, loot tables, tags and config.
 */
package com.virtualfarmworks.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.compat.mysticalagriculture.MysticalCompat;
import com.virtualfarmworks.data.ModDataMaps;
import com.virtualfarmworks.harvest.DropSource;
import com.virtualfarmworks.harvest.HarvestPlans;
import com.virtualfarmworks.harvest.Harvester;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.plant.PlantAnalysis;
import com.virtualfarmworks.plant.PlantAnalysis.Status;
import com.virtualfarmworks.plant.PlantRules;
import com.virtualfarmworks.plant.SoilRules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * Automated in-game tests, run headless with {@code gradlew runGameTestServer} (boots a real server with every mod in
 * {@code run/mods}, runs the tests, exits non-zero on failure).
 *
 * <p>Registered only in development ({@link FMLEnvironment#isProduction()} false), so they never show up in players'
 * {@code /test} lists. Each test is a plain function: it runs checks against real registries/tags/config and calls
 * {@code helper.succeed()}; a failed {@link #check} fails the test with a readable message.
 *
 * <p>Add a test: write a {@code static void name(GameTestHelper)} and add one {@link #test} line to {@link #TESTS}
 * with the maximum number of ticks it may take (logic-only tests finish on their first tick; machine tests that wait
 * for real growth need more).
 */
public final class VfwGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, VirtualFarmWorks.MODID);

    /** A registered test function and how many ticks it may run before it counts as failed. */
    private record Spec(DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function, int maxTicks) {
    }

    private static final List<Spec> TESTS = List.of(
            // plant / soil rules (step 3)
            test("plant_rules_vanilla", VfwGameTests::plantRulesVanilla, 20),
            test("slot_rules", VfwGameTests::slotRules, 20),
            test("plant_rules_mystical", VfwGameTests::plantRulesMystical, 20),
            // harvest (step 5)
            test("harvest_vanilla_yields", VfwGameTests::harvestVanillaYields, 20),
            test("harvest_is_all_or_nothing", VfwGameTests::harvestIsAllOrNothing, 20),
            test("mystical_drops_match_ma", VfwGameTests::mysticalDropsMatchMa, 20),
            // placed machine (step 6)
            test("machine_runs_a_full_cycle", MachineGameTests::runsAFullCycle, 300), // 200-tick cycle at 3.0x
            test("machine_reports_states", MachineGameTests::reportsStates, 20),
            test("machine_output_full_holds_the_harvest", MachineGameTests::outputFullHoldsTheHarvest, 100),
            test("machine_saves_and_loads", MachineGameTests::savesAndLoads, 20),
            test("machine_auto_exports_to_enabled_faces", MachineGameTests::autoExportsToEnabledFaces, 120),
            test("machine_drops_its_contents", MachineGameTests::dropsItsContents, 20),
            test("machine_slot_rules", MachineGameTests::slotRules, 20),
            // GUI, server side (step 7)
            test("menu_actions", MachineGameTests::menuActions, 20),
            // finishing touches (step 8)
            test("machine_inserts_upgrades_from_hand", MachineGameTests::insertsUpgradesFromHand, 20),
            test("recipes_are_loaded", MachineGameTests::recipesAreLoaded, 20),
            test("mystical_effective_farmland", VfwGameTests::mysticalEffectiveFarmland, 20),
            // output: hidden slots and batched harvests (step 8, owner's deadlock report)
            test("machine_big_harvest_does_not_deadlock", MachineGameTests::bigHarvestDoesNotDeadlock, 20),
            test("machine_extreme_harvest_holds_the_rest", MachineGameTests::extremeHarvestHoldsTheRest, 20),
            test("machine_hidden_slots_refill_visible", MachineGameTests::hiddenSlotsRefillTheVisibleOnes, 20),
            // harvest filter (step 8)
            test("filter_roll_honors_the_filter", FilterGameTests::rollHonorsTheFilter, 20),
            test("filter_menu_edits_the_filter", FilterGameTests::menuEditsTheFilter, 20),
            test("filter_purges_the_output", FilterGameTests::filterPurgesTheOutput, 20),
            test("mystical_filter_keeps_only_essence", VfwGameTests::mysticalFilterKeepsOnlyEssence, 20),
            // every plantable (owner decisions 2026-09-28)
            test("plantables_rules", PlantablesGameTests::rules, 20),
            test("plantables_slots", PlantablesGameTests::slots, 20),
            test("plantables_fixed_yields", PlantablesGameTests::fixedYields, 20),
            test("plantables_trees_grow", PlantablesGameTests::treesGrow, 20),
            test("plantables_machine_without_soil", PlantablesGameTests::machineWithoutSoil, 40),
            test("plantables_machine_grows_trees", PlantablesGameTests::machineGrowsTrees, 40),
            // Entropic Farm Matrix (owner spec 2026-09-29)
            test("entropic_groups_must_all_be_valid", EntropicGameTests::groupsMustAllBeValid, 20),
            test("entropic_input_face_pulls_from_chests", EntropicGameTests::inputFacePullsFromChests, 20),
            test("entropic_energy_runs_the_machine", EntropicGameTests::energyRunsTheMachine, 20),
            test("entropic_faces_and_pipe_input", EntropicGameTests::facesAndPipeInput, 20),
            test("entropic_groups_harvest_together", EntropicGameTests::groupsHarvestTogether, 40),
            test("entropic_menu_works", EntropicGameTests::menuWorks, 20),
            test("entropic_replants_extra_seeds", EntropicGameTests::replantsExtraSeeds, 20),
            // Entropic autocrafter (owner spec 2026-09-29)
            test("crafter_plans", CrafterGameTests::crafterPlans, 20),
            test("crafter_crafts_the_harvest", CrafterGameTests::crafterCraftsTheHarvest, 20),
            test("crafter_menu_works", CrafterGameTests::crafterMenuWorks, 20),
            test("crafter_uses_the_catalyst", CrafterGameTests::crafterUsesTheCatalyst, 20));

    /**
     * Load benchmark: registered as a test ONLY in the {@code benchmark} run ({@code gradlew runBenchmark}, which sets
     * the {@link LoadBenchmark#PROPERTY} system property), and then alone. Synchronous, so it needs a single tick.
     */
    private static final Spec BENCHMARK = test("load_benchmark", LoadBenchmark::run, 20);

    private VfwGameTests() {
    }

    private static Spec test(String name, Consumer<GameTestHelper> function, int maxTicks) {
        return new Spec(FUNCTIONS.register(name, () -> function), maxTicks);
    }

    public static void register(IEventBus modEventBus) {
        if (FMLEnvironment.isProduction()) {
            return;
        }
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RegisterGameTestsEvent.class, event -> {
            Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
            List<Spec> specs = Boolean.getBoolean(LoadBenchmark.PROPERTY) ? List.of(BENCHMARK) : TESTS;
            for (Spec spec : specs) {
                // Vanilla's empty structure; "required" so a failure fails the whole run (non-zero exit code).
                event.registerTest(spec.function().getId(), new FunctionGameTestInstance(spec.function().getKey(),
                        new TestData<>(environment, Identifier.withDefaultNamespace("empty"), spec.maxTicks(), 0,
                                true)));
            }
        });
    }

    // --- tests ------------------------------------------------------------------------------------------------------

    /** Seed/soil pairing for every accepted vanilla plant type (owner's list), including hoe requirements. */
    private static void plantRulesVanilla(GameTestHelper helper) {
        // Crops need farmland; plain dirt works only with a hoe.
        expect(helper, Items.WHEAT_SEEDS, Items.FARMLAND, Status.VALID, false);
        expect(helper, Items.WHEAT_SEEDS, Items.DIRT, Status.VALID, true);
        expect(helper, Items.WHEAT_SEEDS, Items.GRASS_BLOCK, Status.VALID, true);
        expect(helper, Items.CARROT, Items.FARMLAND, Status.VALID, false);
        expect(helper, Items.POTATO, Items.DIRT, Status.VALID, true);
        expect(helper, Items.BEETROOT_SEEDS, Items.FARMLAND, Status.VALID, false);
        expect(helper, Items.WHEAT_SEEDS, Items.SOUL_SAND, Status.INVALID_SOIL, false);
        expect(helper, Items.WHEAT_SEEDS, Items.SAND, Status.INVALID_SOIL, false);
        // Stem crops.
        expect(helper, Items.MELON_SEEDS, Items.FARMLAND, Status.VALID, false);
        expect(helper, Items.PUMPKIN_SEEDS, Items.DIRT, Status.VALID, true);
        // Sugar cane: dirt or sand, never a hoe (the owner's example).
        expect(helper, Items.SUGAR_CANE, Items.DIRT, Status.VALID, false);
        expect(helper, Items.SUGAR_CANE, Items.SAND, Status.VALID, false);
        expect(helper, Items.SUGAR_CANE, Items.SOUL_SAND, Status.INVALID_SOIL, false);
        // Others.
        expect(helper, Items.CACTUS, Items.SAND, Status.VALID, false);
        expect(helper, Items.CACTUS, Items.DIRT, Status.INVALID_SOIL, false);
        expect(helper, Items.BAMBOO, Items.DIRT, Status.VALID, false);
        expect(helper, Items.COCOA_BEANS, Items.JUNGLE_LOG, Status.VALID, false);
        // Oak log supports no plant at all, so it is not even a soil (the soil slot would reject it).
        expect(helper, Items.COCOA_BEANS, Items.OAK_LOG, Status.MISSING_SOIL, false);
        expect(helper, Items.NETHER_WART, Items.SOUL_SAND, Status.VALID, false);
        expect(helper, Items.NETHER_WART, Items.DIRT, Status.INVALID_SOIL, false);
        expect(helper, Items.SWEET_BERRIES, Items.GRASS_BLOCK, Status.VALID, false);
        expect(helper, Items.GLOW_BERRIES, Items.STONE, Status.VALID, false);
        expect(helper, Items.BROWN_MUSHROOM, Items.MYCELIUM, Status.VALID, false);
        expect(helper, Items.RED_MUSHROOM, Items.PODZOL, Status.VALID, false);
        // Farmland is a soil, but not a mushroom soil. Cobblestone supports nothing, so it is not a soil at all.
        expect(helper, Items.BROWN_MUSHROOM, Items.FARMLAND, Status.INVALID_SOIL, false);
        expect(helper, Items.BROWN_MUSHROOM, Items.COBBLESTONE, Status.MISSING_SOIL, false);
        expect(helper, Items.CHORUS_FLOWER, Items.END_STONE, Status.VALID, false);
        // Missing / not plantable (saplings, flowers and torchflower seeds are plantable since 2026-09-28, see
        // PlantablesGameTests).
        expect(helper, Items.AIR, Items.FARMLAND, Status.MISSING_SEED, false);
        expect(helper, Items.WHEAT, Items.FARMLAND, Status.MISSING_SEED, false);
        expect(helper, Items.MOSS_CARPET, Items.DIRT, Status.MISSING_SEED, false);
        expect(helper, Items.WHEAT_SEEDS, Items.AIR, Status.MISSING_SOIL, false);
        expect(helper, Items.WHEAT_SEEDS, Items.GLASS, Status.MISSING_SOIL, false);
        helper.succeed();
    }

    /** What the seed, soil and hoe slots accept on their own (before pairing). */
    private static void slotRules(GameTestHelper helper) {
        check(helper, PlantRules.isPlantable(stack(Items.WHEAT_SEEDS)), "wheat seeds must be plantable");
        check(helper, PlantRules.isPlantable(stack(Items.COCOA_BEANS)), "cocoa beans must be plantable");
        // Owner, 2026-09-28: every plantable is accepted (more in PlantablesGameTests).
        check(helper, PlantRules.isPlantable(stack(Items.KELP)), "kelp must be plantable");
        check(helper, PlantRules.isPlantable(stack(Items.CRIMSON_FUNGUS)), "crimson fungus must be plantable");
        check(helper, PlantRules.isPlantable(stack(Items.VINE)), "vines must be plantable");
        check(helper, PlantRules.isPlantable(stack(Items.PITCHER_POD)), "pitcher pod must be plantable");
        check(helper, !PlantRules.isPlantable(stack(Items.WHEAT)), "an item that places no block must not be plantable");
        check(helper, !PlantRules.isPlantable(stack(Items.MOSS_CARPET)), "a moss carpet is not a plant");

        check(helper, SoilRules.isAcceptableSoil(stack(Items.DIRT)), "dirt must be a soil");
        check(helper, SoilRules.isAcceptableSoil(stack(Items.FARMLAND)), "farmland must be a soil");
        check(helper, SoilRules.isAcceptableSoil(stack(Items.SOUL_SAND)), "soul sand must be a soil");
        check(helper, SoilRules.isAcceptableSoil(stack(Items.JUNGLE_LOG)), "jungle log must be a soil (cocoa)");
        check(helper, SoilRules.isAcceptableSoil(stack(Items.END_STONE)), "end stone must be a soil (chorus)");
        check(helper, !SoilRules.isAcceptableSoil(stack(Items.GLASS)), "glass must not be a soil");
        check(helper, !SoilRules.isAcceptableSoil(stack(Items.DIAMOND_BLOCK)), "diamond block must not be a soil");
        check(helper, !SoilRules.isAcceptableSoil(stack(Items.SUGAR_CANE)), "a plant must not be a soil");
        check(helper, !SoilRules.isAcceptableSoil(stack(Items.WHEAT)), "a non-block item must not be a soil");

        check(helper, SoilRules.isHoe(stack(Items.WOODEN_HOE)), "wooden hoe must be a hoe");
        check(helper, SoilRules.isHoe(stack(Items.NETHERITE_HOE)), "netherite hoe must be a hoe");
        check(helper, !SoilRules.isHoe(stack(Items.DIAMOND_SHOVEL)), "a shovel must not be a hoe");
        helper.succeed();
    }

    /**
     * Mystical Agriculture integration (skipped with a pass when MA is not in run/mods): MA seeds on MA farmland, the
     * farmland growth bonus from the data map, and crux detection.
     */
    private static void plantRulesMystical(GameTestHelper helper) {
        if (!MysticalCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        ItemStack inferiumSeeds = byId("mysticalagriculture:inferium_seeds");
        ItemStack inferiumFarmland = byId("mysticalagriculture:inferium_farmland");
        ItemStack supremiumFarmland = byId("mysticalagriculture:supremium_farmland");

        PlantAnalysis onInferium = PlantAnalysis.analyze(inferiumSeeds, inferiumFarmland, MachineTier.STARTER);
        check(helper, onInferium.isValid() && !onInferium.needsHoe(),
                "inferium seeds on inferium farmland must be valid without hoe, got " + onInferium);
        check(helper, !onInferium.needsCrux(), "inferium seeds must not need a crux");
        check(helper, Math.abs(onInferium.soilSpeedMultiplier() - 1.15) < 1e-9,
                "inferium farmland must give +15%, got " + onInferium.soilSpeedMultiplier());
        check(helper, Math.abs(ModDataMaps.soilSpeedMultiplier(supremiumFarmland) - 1.35) < 1e-9,
                "supremium farmland must give +35%");
        check(helper, PlantAnalysis.analyze(inferiumSeeds, stack(Items.FARMLAND), MachineTier.STARTER).isValid(),
                "MA seeds must also grow on vanilla farmland (MA default config)");

        // Essences also expose their crop in MA's API; they must never be treated as seeds.
        check(helper, !MysticalCompat.requiresCrux(byId("mysticalagriculture:nether_star_essence")),
                "an essence must not be treated as a crux seed");

        // At least one MA seed declares a crux; the analysis must flag it.
        boolean foundCruxSeed = false;
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack seed = item.getDefaultInstance();
            if (PlantRules.isPlantable(seed) && MysticalCompat.requiresCrux(seed)) {
                foundCruxSeed = true;
                PlantAnalysis analysis = PlantAnalysis.analyze(seed, inferiumFarmland, MachineTier.STARTER);
                check(helper, analysis.isValid() && analysis.needsCrux(),
                        "crux seed " + BuiltInRegistries.ITEM.getKey(item) + " must be valid and need a crux, got "
                                + analysis);
                break;
            }
        }
        check(helper, foundCruxSeed, "expected at least one Mystical Agriculture seed with a crux");
        helper.succeed();
    }

    /**
     * One harvest of 10 plots for every accepted vanilla plant, checked against the vanilla loot tables with the
     * replanting cost paid (expected ranges derived from data/minecraft/loot_table/blocks/*.json, no Fortune).
     */
    private static void harvestVanillaYields(GameTestHelper helper) {
        // Crops: product + extra seeds; one seed (or the product itself) goes back into the ground per plot.
        expectHarvest(helper, Items.WHEAT_SEEDS, Items.FARMLAND, Items.WHEAT, 10, 10);
        expectHarvest(helper, Items.WHEAT_SEEDS, Items.FARMLAND, Items.WHEAT_SEEDS, 0, 30);   // (1..4) - 1
        expectHarvest(helper, Items.BEETROOT_SEEDS, Items.FARMLAND, Items.BEETROOT, 10, 10);
        expectHarvest(helper, Items.BEETROOT_SEEDS, Items.FARMLAND, Items.BEETROOT_SEEDS, 0, 30);
        expectHarvest(helper, Items.CARROT, Items.FARMLAND, Items.CARROT, 10, 40);             // (2..5) - 1
        expectHarvest(helper, Items.POTATO, Items.FARMLAND, Items.POTATO, 10, 40);
        expectHarvest(helper, Items.POTATO, Items.FARMLAND, Items.POISONOUS_POTATO, 0, 10);
        expectHarvest(helper, Items.NETHER_WART, Items.SOUL_SAND, Items.NETHER_WART, 10, 30);  // (2..4) - 1
        expectHarvest(helper, Items.COCOA_BEANS, Items.JUNGLE_LOG, Items.COCOA_BEANS, 20, 20); // 3 - 1
        // Stems: the fruit is harvested, the stem stays (no seeds out).
        expectHarvest(helper, Items.MELON_SEEDS, Items.FARMLAND, Items.MELON_SLICE, 30, 70);   // 3..7
        expectHarvest(helper, Items.MELON_SEEDS, Items.FARMLAND, Items.MELON_SEEDS, 0, 0);
        expectHarvest(helper, Items.PUMPKIN_SEEDS, Items.FARMLAND, Items.PUMPKIN, 10, 10);
        // Plants that stay in place: one segment / picking per cycle.
        expectHarvest(helper, Items.SUGAR_CANE, Items.SAND, Items.SUGAR_CANE, 10, 10);
        expectHarvest(helper, Items.CACTUS, Items.SAND, Items.CACTUS, 10, 10);
        expectHarvest(helper, Items.BAMBOO, Items.DIRT, Items.BAMBOO, 10, 10);
        expectHarvest(helper, Items.BROWN_MUSHROOM, Items.MYCELIUM, Items.BROWN_MUSHROOM, 10, 10);
        expectHarvest(helper, Items.SWEET_BERRIES, Items.GRASS_BLOCK, Items.SWEET_BERRIES, 20, 30); // 2..3
        expectHarvest(helper, Items.GLOW_BERRIES, Items.STONE, Items.GLOW_BERRIES, 10, 10);
        expectHarvest(helper, Items.CHORUS_FLOWER, Items.END_STONE, Items.CHORUS_FRUIT, 0, 10);    // 0..1
        helper.succeed();
    }

    /**
     * The output buffer receives a harvest completely or not at all (anti-dupe / anti-void). Uses a plain 9-slot
     * handler like the machine's buffer.
     */
    private static void harvestIsAllOrNothing(GameTestHelper helper) {
        ItemStacksResourceHandler buffer = new ItemStacksResourceHandler(9);
        ItemResource dirt = ItemResource.of(Items.DIRT);
        ItemResource wheat = ItemResource.of(Items.WHEAT);
        for (int slot = 0; slot < 8; slot++) {
            buffer.set(slot, dirt, 64);
        }
        buffer.set(8, dirt, 63); // room for exactly one more dirt, none for anything else

        // 1 dirt fits, 5 wheat do not: the dirt insertion must be rolled back too.
        List<DropTally.Entry<ItemResource>> harvest =
                List.of(new DropTally.Entry<>(dirt, 1), new DropTally.Entry<>(wheat, 5));
        check(helper, !Harvester.tryStore(harvest, buffer), "a harvest that does not fit must be refused");
        check(helper, buffer.getAmountAsInt(8) == 63, "partial insertion must be rolled back (slot 8 changed)");
        for (int slot = 0; slot < 9; slot++) {
            check(helper, buffer.getResource(slot).equals(dirt), "refused harvest must not add items (slot " + slot + ")");
        }

        // Free one slot: now everything fits, stacking into the existing dirt stack first.
        buffer.set(0, ItemResource.EMPTY, 0);
        check(helper, Harvester.tryStore(harvest, buffer), "a harvest that fits must be stored");
        check(helper, buffer.getAmountAsInt(8) == 64, "the extra dirt must complete the existing stack");
        check(helper, buffer.getResource(0).equals(wheat) && buffer.getAmountAsInt(0) == 5,
                "the wheat must go to the free slot");

        // An empty harvest (e.g. production multiplier 0) always succeeds, so the cycle can complete.
        check(helper, Harvester.tryStore(List.of(), buffer), "an empty harvest must succeed");
        helper.succeed();
    }

    /** VFW's Mystical Agriculture drops match MA's real getDrops (skipped with a pass when MA is absent). */
    private static void mysticalDropsMatchMa(GameTestHelper helper) {
        if (!MysticalCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        MysticalHarvestTests.run(helper); // loads MA classes only here
    }

    /** The owner's filter example on a real MA seed (skipped with a pass when MA is absent). */
    private static void mysticalFilterKeepsOnlyEssence(GameTestHelper helper) {
        if (!MysticalCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        MysticalHarvestTests.whitelistKeepsOnlyEssence(helper); // loads MA classes only here
    }

    /** VFW's switch for MA's effective-farmland rule, off and on (skipped with a pass when MA is absent). */
    private static void mysticalEffectiveFarmland(GameTestHelper helper) {
        if (!MysticalCompat.isLoaded()) {
            helper.succeed();
            return;
        }
        MysticalFarmlandTests.run(helper); // loads MA classes only here
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    /** Rolls a harvest of 10 plots (default config multipliers) and checks the amount of one item. */
    private static void expectHarvest(GameTestHelper helper, net.minecraft.world.item.Item seed,
                                      net.minecraft.world.item.Item soil, net.minecraft.world.item.Item product,
                                      long min, long max) {
        String pair = BuiltInRegistries.ITEM.getKey(seed) + " on " + BuiltInRegistries.ITEM.getKey(soil);
        DropSource source = HarvestPlans.create(stack(seed), stack(soil));
        check(helper, source != null, pair + ": no drop source");
        var level = helper.getLevel();
        List<DropTally.Entry<ItemResource>> drops = Harvester.roll(source, 10, MachineTier.STARTER,
                new DropSource.Context(level, helper.absolutePos(BlockPos.ZERO), level.getRandom(), 64, true));
        long amount = drops.stream()
                .filter(drop -> drop.key().is(product))
                .mapToLong(DropTally.Entry::amount)
                .sum();
        check(helper, amount >= min && amount <= max, pair + ": expected " + min + ".." + max + " "
                + BuiltInRegistries.ITEM.getKey(product) + ", got " + amount + " " + drops);
    }

    private static void expect(GameTestHelper helper, net.minecraft.world.item.Item seed, net.minecraft.world.item.Item soil,
                               Status status, boolean needsHoe) {
        PlantAnalysis analysis = PlantAnalysis.analyze(stack(seed), stack(soil), MachineTier.STARTER);
        String pair = BuiltInRegistries.ITEM.getKey(seed) + " on " + BuiltInRegistries.ITEM.getKey(soil);
        check(helper, analysis.status() == status, pair + ": expected " + status + ", got " + analysis.status());
        check(helper, analysis.needsHoe() == needsHoe, pair + ": expected needsHoe=" + needsHoe);
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }

    private static ItemStack stack(net.minecraft.world.item.Item item) {
        return new ItemStack(item);
    }

    private static ItemStack byId(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID, path);
    }
}
