package io.hookdev.empirewand;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** In the spell wheel: puts one of the wand's spells (its place in the list) on a number key, 1 to 5 (slot 0 to 4). */
public record SetFavourite(int slot, int index) implements CustomPacketPayload {
    public static final Type<SetFavourite> TYPE = new Type<>(EmpireWand.id("favourite"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetFavourite> CODEC = CustomPacketPayload.codec((m, buf) -> {
        buf.writeVarInt(m.slot);
        buf.writeVarInt(m.index);
    }, buf -> new SetFavourite(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetFavourite message, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.getMainHandItem().getItem() instanceof WandItem wand)) return;
        if (!wand.knows(player.getMainHandItem(), message.index())) return;
        wand.setFavourite(player.getMainHandItem(), message.slot(), message.index());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 2f);
    }
}
