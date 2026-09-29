/*
 * SetFilterGhostPayload — client-to-server packet for an item dragged from JEI onto a Farm Matrix harvest filter ghost
 * slot (owner request: filter items the player does not have). A vanilla slot click cannot carry an item the player
 * does not hold, hence this small packet.
 */
package com.virtualfarmworks.network;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.menu.AbstractFarmMatrixMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-authoritative like every other GUI action: the server applies it only to the sender's own open, still valid
 * Farm Matrix menu, and only as an item TYPE in a ghost slot of the page that player is viewing — nothing is created
 * or given, so trusting the item the client names is harmless.
 *
 * @param containerId the menu the drop was made on (ignored if the player has closed or switched it)
 * @param slot        ghost slot of the current page, 0..8
 * @param stack       the dropped item (only its type is kept)
 */
public record SetFilterGhostPayload(int containerId, int slot, ItemStack stack) implements CustomPacketPayload {
    public static final Type<SetFilterGhostPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID, "set_filter_ghost"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterGhostPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SetFilterGhostPayload::containerId,
                    ByteBufCodecs.VAR_INT, SetFilterGhostPayload::slot,
                    ItemStack.OPTIONAL_STREAM_CODEC, SetFilterGhostPayload::stack,
                    SetFilterGhostPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Registers the packet (mod event bus). Version "1": bump it if the format changes. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1")
                .playToServer(TYPE, STREAM_CODEC, SetFilterGhostPayload::handle));
    }

    /** Server, main thread (NeoForge's default for payload handlers). */
    private static void handle(SetFilterGhostPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.containerMenu instanceof AbstractFarmMatrixMenu menu && menu.containerId == payload.containerId()
                && menu.stillValid(player)) {
            menu.setFilterGhost(payload.slot(), payload.stack());
        }
    }
}
