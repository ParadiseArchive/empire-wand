package io.hookdev.empirewand;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A wand: right-click selects its next spell (the name shows which), left-click casts it (see CastSpell). */
public class WandItem extends Item {
    private final List<Spell> spells;

    public WandItem(Properties properties, List<Spell> spells) {
        super(properties);
        this.spells = spells;
    }

    public List<Spell> spells() {
        return spells;
    }

    /** The spell it has selected (the first one on a new wand). */
    public Spell spell(ItemStack stack) {
        return spells.get(Math.floorMod(stack.getOrDefault(Content.SPELL.get(), 0), spells.size()));
    }

    /** Selects the next spell and tells the player, above the hotbar. */
    public Spell next(ItemStack stack, Player player) {
        return select(stack, Math.floorMod(stack.getOrDefault(Content.SPELL.get(), 0) + 1, spells.size()), player);
    }

    /** Selects this one of its spells: the crystal takes its colour (the item models tint it from custom model data). */
    public Spell select(ItemStack stack, int index, Player player) {
        stack.set(Content.SPELL.get(), index);
        Spell spell = spells.get(index);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(List.of(), List.of(), List.of(), List.of(spell.colour)));
        player.sendOverlayMessage(Component.translatable("message.empirewand.selected", spell.displayName()).withStyle(ChatFormatting.GOLD));
        return spell;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server) {
            next(player.getItemInHand(hand), player);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1, 1.4f);
            server.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
        }
        return InteractionResult.SUCCESS;
    }

    /** A wand always shimmers a little. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /** "Empire Wand (Explode)": red like the plugin's wand, its spell in dark aqua. */
    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(ChatFormatting.RED)
                .append(Component.literal(" (").withStyle(ChatFormatting.GRAY)).append(spell(stack).displayName()).append(Component.literal(")").withStyle(ChatFormatting.GRAY));
    }
}
