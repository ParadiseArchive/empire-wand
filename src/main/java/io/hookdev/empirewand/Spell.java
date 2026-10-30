package io.hookdev.empirewand;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Every spell, with its colour (the wand's crystal, its beam, its icon), and the order a wand goes through them with
 * right-click (the order of the 2013 plugin).
 */
public enum Spell {
    EXPLODE(0xF4F4F4), LIGHTNING_ARROW(0x00FFFF), ESCAPE(0xFF5A1F), LAUNCH(0x5A4BFF), LEAP(0xB45CFF), CONFUSE(0x9A2A9A), CAPTURE(0x3FE0A8),
    LITTLE_SPARK(0xFFF27A), COMET(0xFFA500), SPARK(0xFF3B3B), JUMP(0xFF8C1A), POISON_WAVE(0x35D04A), EXPLOSIVE(0x3A3A3A), BLOOD_BLOCK(0xC0102A),
    LIGHTNING_STORM(0x4FC8FF), BLOOD_WAVE(0x8B0000), BLOOD_SPARK(0xD0182E),
    METEOR_SHOWER(0xFF6A00), CHAIN_LIGHTNING(0x9FD8FF), EARTHQUAKE(0x8B5A2B), TORNADO(0xC8D0D8);

    /** The Empire Wand's: the fifteen from 2013 (owner, 31 Oct 2026: Flamethrower and Ender Egg are gone), and four new ones. */
    public static final List<Spell> EMPIRE = List.of(EXPLODE, LIGHTNING_ARROW, ESCAPE, LAUNCH, LEAP, CONFUSE, CAPTURE, LITTLE_SPARK, COMET, SPARK, JUMP, POISON_WAVE, EXPLOSIVE, BLOOD_BLOCK, LIGHTNING_STORM,
            METEOR_SHOWER, CHAIN_LIGHTNING, EARTHQUAKE, TORNADO);
    /** Blood Magic's three. */
    public static final List<Spell> BLOOD = List.of(BLOOD_WAVE, BLOOD_SPARK, BLOOD_BLOCK);

    /** Its colour, 0xRRGGBB. */
    public final int colour;

    Spell(int colour) {
        this.colour = colour;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Its name, in dark aqua like the plugin's lore line. */
    public Component displayName() {
        return Component.translatable("spell.empirewand." + id()).withStyle(ChatFormatting.DARK_AQUA);
    }
}
