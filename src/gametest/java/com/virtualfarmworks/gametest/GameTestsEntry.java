/*
 * GameTestsEntry — the game tests' own mod entry point, for development only. The gametest source set never goes into
 * the release jar, so the main mod class cannot call it; in dev runs the source set is part of the mod (build.gradle,
 * mods block) and FML finds this @Mod class next to VirtualFarmWorks.
 */
package com.virtualfarmworks.gametest;

import com.virtualfarmworks.VirtualFarmWorks;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** A further {@code @Mod} class for the same mod id, like the client's (VirtualFarmWorksClient). */
@Mod(VirtualFarmWorks.MODID)
public final class GameTestsEntry {
    public GameTestsEntry(IEventBus modEventBus) {
        VfwGameTests.register(modEventBus);
    }
}
