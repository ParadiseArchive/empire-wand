package io.hookdev.empirewand;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** What differs between Minecraft 26.1 and 26.3 outside the client (this is the 26.1 one). */
public final class McVersion {
    private McVersion() {}

    /** Swings its arm, for everyone to see. */
    public static void swing(LivingEntity entity) {
        entity.swing(InteractionHand.MAIN_HAND, true);
    }

    /** Teleports there if it can stand there (with particles). */
    public static boolean randomTeleport(LivingEntity entity, double x, double y, double z) {
        return entity.randomTeleport(x, y, z, true);
    }

    /** A structure template by its name. */
    public static Optional<StructureTemplate> template(ServerLevel level, Identifier id) {
        return level.getServer().getStructureManager().get(id);
    }
}
