/*
 * VfwGameTests — automated in-game tests (development only), run headless by `gradlew runGameTestServer`: plant/soil
 * pairing, slot rules and the Mystical Agriculture integration, against real registries, tags and config.
 */
package com.virtualfarmworks.gametest;

import java.util.function.Consumer;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.compat.mysticalagriculture.MysticalCompat;
import com.virtualfarmworks.data.ModDataMaps;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.plant.PlantAnalysis;
import com.virtualfarmworks.plant.PlantAnalysis.Status;
import com.virtualfarmworks.plant.PlantRules;
import com.virtualfarmworks.plant.SoilRules;

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

/**
 * Automated in-game tests, run headless with {@code gradlew runGameTestServer} (boots a real server with every mod in
 * {@code run/mods}, runs the tests, exits non-zero on failure).
 *
 * <p>Registered only in development ({@link FMLEnvironment#isProduction()} false), so they never show up in players'
 * {@code /test} lists. Each test is a plain function: it runs checks against real registries/tags/config and calls
 * {@code helper.succeed()}; a failed {@link #check} fails the test with a readable message.
 *
 * <p>Add a test: write a {@code static void name(GameTestHelper)} and register it in {@link #FUNCTIONS} with
 * {@link #test}.
 */
public final class VfwGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, VirtualFarmWorks.MODID);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PLANT_RULES_VANILLA =
            FUNCTIONS.register("plant_rules_vanilla", () -> VfwGameTests::plantRulesVanilla);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> SLOT_RULES =
            FUNCTIONS.register("slot_rules", () -> VfwGameTests::slotRules);
    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PLANT_RULES_MYSTICAL =
            FUNCTIONS.register("plant_rules_mystical", () -> VfwGameTests::plantRulesMystical);

    private VfwGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        if (FMLEnvironment.isProduction()) {
            return;
        }
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(RegisterGameTestsEvent.class, event -> {
            Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(id("default"));
            for (DeferredHolder<Consumer<GameTestHelper>, ? extends Consumer<GameTestHelper>> function
                    : FUNCTIONS.getEntries()) {
                // Logic-only tests: vanilla's empty structure, 1 tick is enough, required so a failure fails the run.
                event.registerTest(function.getId(), new FunctionGameTestInstance(function.getKey(),
                        new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 20, 0, true)));
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
        // Missing / not plantable.
        expect(helper, Items.AIR, Items.FARMLAND, Status.MISSING_SEED, false);
        expect(helper, Items.OAK_SAPLING, Items.DIRT, Status.MISSING_SEED, false);
        expect(helper, Items.DANDELION, Items.DIRT, Status.MISSING_SEED, false);
        expect(helper, Items.TORCHFLOWER_SEEDS, Items.FARMLAND, Status.MISSING_SEED, false);
        expect(helper, Items.WHEAT_SEEDS, Items.AIR, Status.MISSING_SOIL, false);
        expect(helper, Items.WHEAT_SEEDS, Items.GLASS, Status.MISSING_SOIL, false);
        helper.succeed();
    }

    /** What the seed, soil and hoe slots accept on their own (before pairing). */
    private static void slotRules(GameTestHelper helper) {
        check(helper, PlantRules.isPlantable(stack(Items.WHEAT_SEEDS)), "wheat seeds must be plantable");
        check(helper, PlantRules.isPlantable(stack(Items.COCOA_BEANS)), "cocoa beans must be plantable");
        check(helper, !PlantRules.isPlantable(stack(Items.KELP)), "kelp must not be plantable (owner: later)");
        check(helper, !PlantRules.isPlantable(stack(Items.CRIMSON_FUNGUS)), "crimson fungus must not be plantable");
        check(helper, !PlantRules.isPlantable(stack(Items.VINE)), "vines must not be plantable");
        check(helper, !PlantRules.isPlantable(stack(Items.PITCHER_POD)), "pitcher pod must not be plantable");

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

    // --- helpers ----------------------------------------------------------------------------------------------------

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
