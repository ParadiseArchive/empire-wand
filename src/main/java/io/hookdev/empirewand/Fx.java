package io.hookdev.empirewand;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The effects: fireworks that burst on the spot (as the plugin's FireworkEffectPlayer did), particles, block dust. */
public final class Fx {
    /** Bukkit's colours, as the plugin used them. */
    public static final int WHITE = 0xFFFFFF, BLACK = 0x000000, RED = 0xFF0000, BLUE = 0x0000FF, PURPLE = 0x800080, ORANGE = 0xFFA500, AQUA = 0x00FFFF;

    private Fx() {}

    /**
     * A firework that bursts right here: a rocket with this explosion appears, bursts (entity event 17, the client draws
     * the explosion and plays its sound) and is gone before it can fly or hurt anyone.
     */
    public static void firework(ServerLevel level, Vec3 at, FireworkExplosion.Shape shape, int[] colours, int[] fades, boolean trail, boolean twinkle) {
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
        stack.set(DataComponents.FIREWORKS, new Fireworks(0, List.of(new FireworkExplosion(shape, IntList.of(colours), IntList.of(fades), trail, twinkle))));
        FireworkRocketEntity rocket = new FireworkRocketEntity(level, at.x, at.y, at.z, stack);
        level.addFreshEntity(rocket);
        level.broadcastEntityEvent(rocket, (byte) 17);
        rocket.discard();
    }

    public static void firework(ServerLevel level, Vec3 at, FireworkExplosion.Shape shape, int colour, int fade, boolean trail, boolean twinkle) {
        firework(level, at, shape, new int[] {colour}, new int[] {fade}, trail, twinkle);
    }

    /** The plugin's STEP_SOUND effect: a block's breaking dust and sound, without breaking it. */
    public static void blockDust(ServerLevel level, BlockPos pos, BlockState state) {
        level.levelEvent(2001, pos, Block.getId(state));
    }

    public static void particles(ServerLevel level, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(type, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }
}
