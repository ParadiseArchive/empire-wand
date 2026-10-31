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
    /** How many of its spells (from the start of its list) a new wand knows; the rest come from scrolls. */
    private final int starters;

    public WandItem(Properties properties, List<Spell> spells, int starters) {
        super(properties);
        this.spells = spells;
        this.starters = starters;
    }

    public int starters() {
        return starters;
    }

    /** The spells it knows: a bit for each place in its list. */
    public int known(ItemStack stack) {
        return stack.getOrDefault(Content.KNOWN.get(), (1 << starters) - 1);
    }

    public boolean knows(ItemStack stack, int index) {
        return index >= 0 && index < spells.size() && (known(stack) >> index & 1) != 0;
    }

    /** Learns a spell from a scroll; false if it is not one of its spells, or it knew it already. */
    public boolean learn(ItemStack stack, Spell spell) {
        int index = spells.indexOf(spell);
        if (index < 0 || knows(stack, index)) return false;
        stack.set(Content.KNOWN.get(), known(stack) | 1 << index);
        return true;
    }

    /** How many favourites a wand keeps (the keys 1 to 5). */
    public static final int FAVOURITES = 5;

    /** The spell on this number key (slot 0 is key 1), as its place in the list, or -1. */
    public int favourite(ItemStack stack, int slot) {
        var list = stack.getOrDefault(Content.FAVOURITES.get(), List.<Integer>of());
        return slot >= 0 && slot < list.size() ? list.get(slot) : -1;
    }

    /** Puts a spell on a number key (and takes it off any other key). */
    public void setFavourite(ItemStack stack, int slot, int index) {
        if (slot < 0 || slot >= FAVOURITES) return;
        var list = new java.util.ArrayList<>(stack.getOrDefault(Content.FAVOURITES.get(), List.<Integer>of()));
        while (list.size() < FAVOURITES) list.add(-1);
        for (int i = 0; i < list.size(); i++) if (list.get(i) == index) list.set(i, -1);
        list.set(slot, index);
        stack.set(Content.FAVOURITES.get(), List.copyOf(list));
    }

    /** The next spell it knows, round from this place in its list (step 1 forward, -1 back). */
    public int nextKnown(ItemStack stack, int from, int step) {
        for (int k = 1; k <= spells.size(); k++) {
            int i = Math.floorMod(from + k * step, spells.size());
            if (knows(stack, i)) return i;
        }
        return 0;
    }

    public List<Spell> spells() {
        return spells;
    }

    /** The spell it has selected (the first one on a new wand). */
    public Spell spell(ItemStack stack) {
        int index = Math.floorMod(stack.getOrDefault(Content.SPELL.get(), 0), spells.size());
        return spells.get(knows(stack, index) ? index : 0);
    }

    /** Selects the next spell it knows and tells the player, above the hotbar. */
    public Spell next(ItemStack stack, Player player) {
        return select(stack, nextKnown(stack, stack.getOrDefault(Content.SPELL.get(), 0), 1), player);
    }

    /** Selects this one of its spells: the crystal takes its colour (the item models tint it from custom model data). */
    public Spell select(ItemStack stack, int index, Player player) {
        stack.set(Content.SPELL.get(), index);
        Spell spell = spells.get(index);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(List.of(), List.of(), List.of(), List.of(spell.colour)));
        player.sendOverlayMessage(Component.translatable("message.empirewand.selected", spell.displayName()).withStyle(ChatFormatting.GOLD));
        return spell;
    }

    /** The skins: what to hold in the other hand (sneak and right-click) for each; a stick makes it classic again. */
    public static final java.util.List<net.minecraft.world.item.Item> SKINS = java.util.List.of(net.minecraft.world.item.Items.STICK,
            net.minecraft.world.item.Items.GOLD_BLOCK, net.minecraft.world.item.Items.OBSIDIAN, net.minecraft.world.item.Items.BONE_BLOCK);

    /** Sneak and right-click with a skin's block in the other hand: the wand takes that skin (the block is used up). */
    private boolean reskin(ItemStack wand, Player player, InteractionHand hand, ServerLevel server) {
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        int skin = -1;
        for (int i = 0; i < SKINS.size(); i++) if (other.is(SKINS.get(i))) skin = i;
        if (!player.isShiftKeyDown() || skin < 0 || wand.getOrDefault(Content.SKIN.get(), 0) == skin) return false;
        wand.set(Content.SKIN.get(), skin);
        if (!player.getAbilities().instabuild) other.shrink(1);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1, 1);
        server.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.5, 0.6, 0.5, 0.5);
        player.sendOverlayMessage(Component.translatable("message.empirewand.skin", Component.translatable("skin.empirewand." + skin)).withStyle(ChatFormatting.GOLD));
        return true;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel server) {
            if (reskin(player.getItemInHand(hand), player, hand, server)) return InteractionResult.SUCCESS;
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
