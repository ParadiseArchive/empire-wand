package io.hookdev.empirewand;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The spellbook (owner, 31 Oct 2026): every spell, what it does, whether your wands know it, and where its scroll lies. */
public class SpellbookItem extends Item {
    /** Opens the book's screen; the client sets it (the server has no screens). */
    public static Runnable open = () -> {};

    public SpellbookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) open.run();
        return InteractionResult.SUCCESS;
    }
}
