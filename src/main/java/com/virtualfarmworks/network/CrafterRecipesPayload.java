/*
 * CrafterRecipesPayload — server-to-client packet with the Entropic Farm Matrix autocrafter's recipe list (what each
 * recipe makes), sent to a player viewing the machine when the list changes. The list is dynamic (owner: "a slot is
 * only created when the player needs it"), so it does not travel as menu slots.
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
 * Display data only: the client shows it in the recipe list and never sends it back. An entry is empty for a recipe
 * that no longer exists in the loaded data.
 *
 * @param containerId the menu it belongs to (ignored if the player has closed or switched it meanwhile)
 * @param results     what each recipe makes, in list order, at most {@link MachineCrafter#MAX_RECIPES}
 */
public record CrafterRecipesPayload(int containerId, List<ItemStack> results) implements CustomPacketPayload {
    public static final Type<CrafterRecipesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualFarmWorks.MODID, "crafter_recipes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrafterRecipesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CrafterRecipesPayload::containerId,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MachineCrafter.MAX_RECIPES)),
                    CrafterRecipesPayload::results,
                    CrafterRecipesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Registers the packet (mod event bus). Version "1": bump it if the format changes. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1")
                .playToClient(TYPE, STREAM_CODEC, CrafterRecipesPayload::handle));
    }

    /** Client, main thread: hands the list to the open menu it was sent for. */
    private static void handle(CrafterRecipesPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.containerMenu instanceof EntropicFarmMatrixMenu menu && menu.containerId == payload.containerId()) {
            menu.setRecipeResults(payload.results());
        }
    }
}
