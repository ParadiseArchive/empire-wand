package io.hookdev.empirewand;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The spell wheel (or sneak and scroll): selects one of the spells of the wand in the player's main hand, by its place in the wand's list. */
public record SelectSpell(int index) implements CustomPacketPayload {
    public static final Type<SelectSpell> TYPE = new Type<>(EmpireWand.id("select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectSpell> CODEC = CustomPacketPayload.codec(SelectSpell::write, SelectSpell::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(index);
    }

    private static SelectSpell read(RegistryFriendlyByteBuf buf) {
        return new SelectSpell(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectSpell message, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.getMainHandItem().getItem() instanceof WandItem wand)) return;
        int index = Math.floorMod(message.index(), wand.spells().size());
        if (!wand.knows(player.getMainHandItem(), index)) return; // not learnt yet
        wand.select(player.getMainHandItem(), index, player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.6f);
    }
}
