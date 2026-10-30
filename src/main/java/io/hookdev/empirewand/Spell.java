package io.hookdev.empirewand;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Every spell, and the order a wand goes through them with right-click (the order of the 2013 plugin). */
public enum Spell {
    EXPLODE, LIGHTNING_ARROW, ESCAPE, LAUNCH, FLAMETHROWER, LEAP, ENDER_EGG, CONFUSE, CAPTURE, LITTLE_SPARK, COMET, SPARK,
    JUMP, POISON_WAVE, EXPLOSIVE, BLOOD_BLOCK, LIGHTNING_STORM, BLOOD_WAVE, BLOOD_SPARK;

    /** The Empire Wand's seventeen. */
    public static final List<Spell> EMPIRE = List.of(EXPLODE, LIGHTNING_ARROW, ESCAPE, LAUNCH, FLAMETHROWER, LEAP, ENDER_EGG, CONFUSE, CAPTURE,
            LITTLE_SPARK, COMET, SPARK, JUMP, POISON_WAVE, EXPLOSIVE, BLOOD_BLOCK, LIGHTNING_STORM);
    /** Blood Magic's three. */
    public static final List<Spell> BLOOD = List.of(BLOOD_WAVE, BLOOD_SPARK, BLOOD_BLOCK);

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Its name, in dark aqua like the plugin's lore line. */
    public Component displayName() {
        return Component.translatable("spell.empirewand." + id()).withStyle(ChatFormatting.DARK_AQUA);
    }
}
