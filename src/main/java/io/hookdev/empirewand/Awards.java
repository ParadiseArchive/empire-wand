package io.hookdev.empirewand;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The advancements (owner, 31 Oct 2026). The root one comes with the wand (an inventory trigger); the others have an
 * impossible criterion and are awarded here, from the spells. Counts live in the player's persistent data.
 */
public final class Awards {
    /** The spells that blow something up (for "100 explosions"). */
    private static final java.util.Set<Spell> BLASTS = java.util.EnumSet.of(Spell.EXPLODE, Spell.EXPLOSIVE, Spell.JUMP, Spell.BLOOD_BLOCK, Spell.METEOR_SHOWER, Spell.BLACK_HOLE, Spell.ESCAPE);

    private Awards() {}

    public static void award(ServerPlayer player, String id) {
        var advancement = ((ServerLevel) player.level()).getServer().getAdvancements().get(EmpireWand.id(id));
        if (advancement != null) player.getAdvancements().award(advancement, "done");
    }

    /** Adds one to a count of the player's and returns it. */
    static int add(ServerPlayer player, String key) {
        var data = player.getPersistentData();
        int n = data.getIntOr("empirewand." + key, 0) + 1;
        data.putInt("empirewand." + key, n);
        return n;
    }

    /** A spell was cast. */
    static void cast(ServerPlayer player, Spell spell) {
        if (BLASTS.contains(spell) && add(player, "blasts") >= 100) award(player, "pyromaniac");
    }

    /** A wand learned a spell from a scroll; it knows this many now. */
    static void learned(ServerPlayer player, int known, int all) {
        award(player, "bookworm");
        if (known >= 10) award(player, "apprentice");
        if (known >= all) award(player, "archmage");
    }
}
