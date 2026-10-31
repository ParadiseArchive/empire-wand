package io.hookdev.empirewand;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Every spell, with its colour (the wand's crystal, its beam, its icon) and its cooldown, and the order a wand goes through them with
 * right-click (the order of the 2013 plugin).
 */
public enum Spell {
    EXPLODE(0xF4F4F4, 30), LIGHTNING_ARROW(0x00FFFF, 20), ESCAPE(0xFF5A1F, 60), LAUNCH(0x5A4BFF, 30), LEAP(0xB45CFF, 40), CONFUSE(0x9A2A9A, 40), CAPTURE(0x3FE0A8, 20),
    LITTLE_SPARK(0xFFF27A, 25), COMET(0xFFA500, 20), SPARK(0xFF3B3B, 15), JUMP(0xFF8C1A, 80), POISON_WAVE(0x35D04A, 60), EXPLOSIVE(0x3A3A3A, 40), BLOOD_BLOCK(0xC0102A, 20),
    LIGHTNING_STORM(0x4FC8FF, 200), BLOOD_WAVE(0x8B0000, 60), BLOOD_SPARK(0xD0182E, 20),
    METEOR_SHOWER(0xFF6A00, 300), CHAIN_LIGHTNING(0x9FD8FF, 80), EARTHQUAKE(0x8B5A2B, 200), TORNADO(0xC8D0D8, 300),
    ICE_WAVE(0x9FE8FF, 60), FREEZE(0xBFF4FF, 60), BLIZZARD(0xE8F8FF, 240), BLINK(0x7A3CFF, 40), BLACK_HOLE(0x4A1A70, 300), LEVITATE(0xD8B0FF, 80),
    ROOTS(0x6B8E23, 60), SPORES(0x9ACD32, 120), OVERGROWTH(0x2E8B57, 100);

    /**
     * The Empire Wand's, the only wand (owner, 31 Oct 2026): the fifteen from 2013 (Flamethrower and Ender Egg are gone),
     * then the new ones, among them those of the wands that were (Blood Magic, Frost, Void, Nature).
     */
    public static final List<Spell> EMPIRE = List.of(EXPLODE, LIGHTNING_ARROW, ESCAPE, LAUNCH, LEAP, CONFUSE, CAPTURE, LITTLE_SPARK, COMET, SPARK, JUMP, POISON_WAVE, EXPLOSIVE, BLOOD_BLOCK, LIGHTNING_STORM,
            METEOR_SHOWER, CHAIN_LIGHTNING, EARTHQUAKE, TORNADO, BLOOD_WAVE, BLOOD_SPARK, ICE_WAVE, FREEZE, BLIZZARD, BLINK, BLACK_HOLE, LEVITATE, ROOTS, SPORES, OVERGROWTH);

    /** Its colour, 0xRRGGBB. */
    public final int colour;
    /** How long the wand cools down after it (owner, 31 Oct 2026: without one it was far too strong), in ticks. */
    public final int cooldown;

    Spell(int colour, int cooldown) {
        this.colour = colour;
        this.cooldown = cooldown;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Its name, in dark aqua like the plugin's lore line. */
    public Component displayName() {
        return Component.translatable("spell.empirewand." + id()).withStyle(ChatFormatting.DARK_AQUA);
    }
}
