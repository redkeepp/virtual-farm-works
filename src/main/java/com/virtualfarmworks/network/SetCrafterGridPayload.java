/*
 * SetCrafterGridPayload — client-to-server packet that fills the Entropic Farm Matrix autocrafter's recipe grid: JEI's
 * "+" on a crafting recipe (owner spec) and items dragged from JEI onto a grid cell. A vanilla slot click cannot carry
 * items the player does not hold, hence this small packet.
 */
package com.virtualfarmworks.network;

import java.util.List;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.machine.MachineCrafter;
import com.virtualfarmworks.menu.EntropicFarmMatrixMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-authoritative like every other GUI action: applied only to the sender's own open, still valid Entropic menu,
 * and only as item TYPES in the recipe grid of that menu — nothing is created or given, so trusting the items the
 * client names is harmless.
 *
 * @param containerId the menu the recipe was sent to (ignored if the player has closed or switched it)
 * @param grid        the 9 cells, row by row (only the item types are kept)
 */
public record SetCrafterGridPayload(int containerId, List<ItemStack> grid) implements CustomPacketPayload {
    public static final Type<SetCrafterGridPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualFarmWorks.MODID, "set_crafter_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetCrafterGridPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetCrafterGridPayload::containerId,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MachineCrafter.GRID_SIZE)),
                    SetCrafterGridPayload::grid,
                    SetCrafterGridPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Registers the packet (mod event bus). Version "1": bump it if the format changes. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1")
                .playToServer(TYPE, STREAM_CODEC, SetCrafterGridPayload::handle));
    }

    /** Server, main thread (NeoForge's default for payload handlers). */
    private static void handle(SetCrafterGridPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.containerMenu instanceof EntropicFarmMatrixMenu menu && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.setCraftGrid(payload.grid());
        }
    }
}
