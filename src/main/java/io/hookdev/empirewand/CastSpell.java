package io.hookdev.empirewand;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Left-click with a wand: the client sends this (a left-click in the air is never seen by the server on its own), and
 * the server casts the spell of the wand in the player's main hand.
 */
public record CastSpell() implements CustomPacketPayload {
    public static final CastSpell INSTANCE = new CastSpell();
    public static final Type<CastSpell> TYPE = new Type<>(EmpireWand.id("cast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CastSpell> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CastSpell message, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.getMainHandItem().getItem() instanceof WandItem wand && player.isAlive()) {
            Spells.cast(player, wand.spell(player.getMainHandItem()));
        }
    }
}
