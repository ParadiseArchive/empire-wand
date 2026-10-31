package io.hookdev.empirewand;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A spell scroll (owner, 31 Oct 2026): found in chests, vaults, suspicious blocks and on some mobs (ScrollsModifier),
 * or bought sealed from a librarian (the seal breaks when you open it: a spell, not a heavy one). Right-click it and the first of your wands that can learn its
 * spell (the one in your other hand first) learns it.
 */
public class ScrollItem extends Item {
    public ScrollItem(Properties properties) {
        super(properties);
    }

    /** A scroll of this spell; its ribbon in the spell's colour. */
    public static ItemStack of(Spell spell) {
        ItemStack stack = new ItemStack(Content.SPELL_SCROLL.get());
        stack.set(Content.SCROLL_SPELL.get(), spell.ordinal());
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(spell.colour)));
        return stack;
    }

    public static @Nullable Spell spell(ItemStack stack) {
        Integer id = stack.get(Content.SCROLL_SPELL.get());
        return id == null || id < 0 || id >= Spell.values().length ? null : Spell.values()[id];
    }

    /** The spells a wand does not know from the start: the ones scrolls are for. */
    public static List<Spell> findable() {
        List<Spell> spells = new ArrayList<>();
        for (WandItem wand : Content.wands()) {
            for (int i = wand.starters(); i < wand.spells().size(); i++) if (!spells.contains(wand.spells().get(i))) spells.add(wand.spells().get(i));
        }
        return spells;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack scroll = player.getItemInHand(hand);
        Spell spell = spell(scroll);
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (spell == null) { // a sealed scroll (from a librarian): the seal breaks, and it is a spell
            List<Spell> spells = new ArrayList<>(ScrollItem.findable());
            spells.removeAll(ScrollsModifier.HEAVY);
            Spell found = spells.get(server.getRandom().nextInt(spells.size()));
            ItemStack opened = of(found);
            opened.setCount(scroll.getCount());
            player.setItemInHand(hand, opened);
            player.sendOverlayMessage(Component.translatable("message.empirewand.seal", found.displayName()).withStyle(ChatFormatting.GOLD));
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1, 1);
            return InteractionResult.SUCCESS;
        }
        // the wand in the other hand first, then the rest of the inventory
        List<ItemStack> wands = new ArrayList<>();
        wands.add(player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND));
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) wands.add(player.getInventory().getItem(i));
        for (ItemStack stack : wands) {
            if (!(stack.getItem() instanceof WandItem wand) || !wand.learn(stack, spell)) continue;
            if (!player.getAbilities().instabuild) scroll.shrink(1);
            player.sendOverlayMessage(Component.translatable("message.empirewand.learned", wand.getName(new ItemStack(wand)).copy().withStyle(ChatFormatting.RED), spell.displayName()).withStyle(ChatFormatting.GOLD));
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1, 1);
            server.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 60, 0.6, 0.8, 0.6, 0.6);
            return InteractionResult.SUCCESS;
        }
        player.sendOverlayMessage(Component.translatable("message.empirewand.cannot_learn", spell.displayName()).withStyle(ChatFormatting.GRAY));
        return InteractionResult.FAIL;
    }

    /** "Spell Scroll: Comet". */
    @Override
    public Component getName(ItemStack stack) {
        Spell spell = spell(stack);
        return spell == null ? Component.translatable("item.empirewand.sealed_scroll").withStyle(ChatFormatting.GOLD) : Component.translatable("item.empirewand.spell_scroll.of", spell.displayName()).withStyle(ChatFormatting.GOLD);
    }
}
