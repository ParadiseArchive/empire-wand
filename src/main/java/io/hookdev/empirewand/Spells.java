package io.hookdev.empirewand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion.Shape;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The spells, rebuilt from the plugin's PlayerListener and Bloedmagie (2013): the same reach, power, timings and
 * firework colours. What the plugin kept in one place for everyone (the spark flags, the blood block) is per player
 * now, and its Bukkit tricks (cancelled lightning that only did damage, NMS calls to speed up a fireball) are done the
 * plain way.
 */
public final class Spells {
    /** Blood Block: the block a player's first click turned to redstone; the second click throws it. */
    private static final Map<UUID, BlockPos> BLOOD_BLOCKS = new HashMap<>();
    /** Players who land without fall damage (Leap, Jump, Escape), until this game time. */
    private static final Map<UUID, Long> SAFE_LANDING = new HashMap<>();
    /** What each player carries above their head (Capture). */
    private static final Map<UUID, LivingEntity> CARRIED = new HashMap<>();

    private Spells() {}

    public static void cast(ServerPlayer player, Spell spell) {
        cast(player, spell, false);
    }

    /**
     * Casts a spell. From Blood Magic (owner, 31 Oct 2026) every spell costs half a heart, and hits much harder for it;
     * with half a heart left there is no blood to give.
     */
    public static void cast(ServerPlayer player, Spell spell, boolean blood) {
        ServerLevel level = (ServerLevel) player.level();
        if (blood && !player.getAbilities().instabuild) {
            if (player.getHealth() <= BLOOD_PRICE) {
                player.sendOverlayMessage(Component.translatable("message.empirewand.no_blood").withStyle(net.minecraft.ChatFormatting.DARK_RED));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6f, 1.5f);
                return;
            }
            // straight off its health: a hit would make it untouchable for half a second, and spamming would be free
            player.setHealth(player.getHealth() - BLOOD_PRICE);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6f, 0.8f);
        }
        if (blood) Fx.particles(level, new net.minecraft.core.particles.DustParticleOptions(0x8B0000, 1.2f), player.position().add(0, 1, 0), 16, 0.4, 0);
        BLOOD.set(blood);
        try {
            castNow(player, spell, level);
        } finally {
            BLOOD.set(false);
        }
    }

    /** Half a heart. */
    public static final float BLOOD_PRICE = 1;
    /** Cast from Blood Magic (stronger): set while the spell is cast. */
    private static final ThreadLocal<Boolean> BLOOD = ThreadLocal.withInitial(() -> false);

    private static void castNow(ServerPlayer player, Spell spell, ServerLevel level) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), castSound(spell), SoundSource.PLAYERS, 0.8f, 1);
        switch (spell) {
            case EXPLODE -> explode(player, level);
            case SPARK -> spark(player, level, spell, Fx.BLACK, Fx.RED, 3);
            case LITTLE_SPARK -> spark(player, level, spell, Fx.WHITE, Fx.RED, 5);
            case BLOOD_SPARK -> spark(player, level, spell, 0x3D0000, 0x3D0000, BLOOD.get() ? 7 : 3);
            case LEAP -> leap(player, level);
            case LAUNCH -> launch(player, level);
            case COMET -> fireball(player, level, false);
            case EXPLOSIVE -> fireball(player, level, true);
            case LIGHTNING_STORM -> lightningStorm(player, level);
            case LIGHTNING_ARROW -> lightningArrow(player, level);
            case ESCAPE -> escape(player, level);
            case BLOOD_BLOCK -> bloodBlock(player, level);
            case CONFUSE -> confuse(player, level);
            case POISON_WAVE -> poisonWave(player, level);
            case BLOOD_WAVE -> bloodWave(player, level);
            case JUMP -> jump(player, level);
            case CAPTURE -> capture(player, level);
        }
    }

    /** Each spell its own sound as it leaves the wand. */
    static net.minecraft.sounds.SoundEvent castSound(Spell spell) {
        return switch (spell) {
            case EXPLODE, EXPLOSIVE -> SoundEvents.EVOKER_CAST_SPELL;
            case LIGHTNING_ARROW -> SoundEvents.ARROW_SHOOT;
            case ESCAPE, JUMP -> SoundEvents.FIRECHARGE_USE;
            case LAUNCH, LEAP -> SoundEvents.ENDER_PEARL_THROW;
            case CONFUSE -> SoundEvents.EVOKER_PREPARE_WOLOLO;
            case CAPTURE -> SoundEvents.SHULKER_SHOOT;
            case LITTLE_SPARK, SPARK, LIGHTNING_STORM -> SoundEvents.EVOKER_PREPARE_ATTACK;
            case COMET -> SoundEvents.BLAZE_SHOOT;
            case POISON_WAVE -> SoundEvents.WITCH_THROW;
            case BLOOD_BLOCK, BLOOD_WAVE, BLOOD_SPARK -> SoundEvents.EVOKER_PREPARE_SUMMON;
        };
    }

    /** The tip of the wand in the player's right hand. */
    static Vec3 tip(Player player) {
        Vec3 look = player.getLookAngle(), right = look.cross(new Vec3(0, 1, 0)).normalize();
        return player.getEyePosition().add(look.scale(0.8)).add(right.scale(0.35)).add(0, -0.25, 0);
    }

    /** A beam from the wand to there, in the spell's colour. */
    static void beam(ServerPlayer player, ServerLevel level, Vec3 to, Spell spell) {
        Fx.beam(level, tip(player), to, spell.colour);
    }

    // ---------- where it lands ----------

    /** The block the player looks at, up to range blocks away (or the spot in the air at that distance). */
    static BlockPos target(Player player, double range) {
        HitResult hit = player.pick(range, 1, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : BlockPos.containing(hit.getLocation());
    }

    /** The plugin's testTarget: a living thing along the player's look (within two blocks of it), or else the block they look at. */
    static Vec3 targetEntity(Player player, double range) {
        Vec3 eye = player.getEyePosition(), end = Vec3.atCenterOf(target(player, range));
        LivingEntity best = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range), e -> e != player && e.isAlive())) {
            var hit = entity.getBoundingBox().inflate(1.5).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearest) {
                nearest = eye.distanceToSqr(hit.get());
                best = entity;
            }
        }
        return best != null ? best.position() : end;
    }

    static List<LivingEntity> near(ServerLevel level, Vec3 at, double radius, @Nullable Entity except) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> e != except && e.isAlive() && e.position().distanceTo(at) <= radius);
    }

    /** Sets a speed and makes sure the client hears about it (a player's own client moves them). */
    static void push(Entity entity, Vec3 speed) {
        entity.setDeltaMovement(speed);
        entity.needsSync = true;
        if (entity instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /** No fall damage on the next landing (within half a minute). */
    static void landSafely(Player player) {
        SAFE_LANDING.put(player.getUUID(), player.level().getGameTime() + 600);
    }

    public static boolean landsSafely(Player player) {
        Long until = SAFE_LANDING.remove(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    static void lightning(ServerLevel level, Vec3 at, ServerPlayer cause) {
        LightningBolt bolt = Content.lightningType().create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.snapTo(at.x, at.y, at.z);
        bolt.setCause(cause);
        level.addFreshEntity(bolt);
    }

    /**
     * The plugin's spark: a lightning strike it cancelled, and gave everything within two blocks damage (or poison)
     * instead. Here: a crackle of sparks and the damage, without the bolt.
     */
    static void zap(ServerLevel level, Vec3 at, ServerPlayer caster, float damage, boolean poison) {
        Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, at, 12, 0.6, 0.2);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2), e -> e != caster && e.isAlive())) {
            if (poison) entity.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 2));
            else entity.hurtServer(level, level.damageSources().indirectMagic(caster, caster), damage);
        }
    }

    // ---------- the Empire Wand ----------

    /** Explode: a blast of 5 where you look, with a white firework. */
    static void explode(ServerPlayer player, ServerLevel level) {
        Vec3 at = Vec3.atCenterOf(target(player, 50));
        beam(player, level, at, Spell.EXPLODE);
        Fx.firework(level, at, Shape.BURST, Fx.WHITE, Fx.BLACK, false, false);
        level.explode(null, at.x, at.y, at.z, 5, false, Level.ExplosionInteraction.TNT);
    }

    /** Spark and Little Spark (and Blood Spark): a firework where you look and a zap (Little Spark is the stronger one). */
    static void spark(ServerPlayer player, ServerLevel level, Spell spell, int colour, int fade, float damage) {
        Vec3 at = Vec3.atCenterOf(target(player, 50));
        beam(player, level, at, spell);
        Fx.firework(level, at, Shape.BURST, colour, fade, colour == 0x3D0000, true);
        zap(level, at, player, damage, false);
    }

    /** Leap: off you fly where you look, with an ender trail, and land without a scratch. */
    static void leap(ServerPlayer player, ServerLevel level) {
        push(player, player.getLookAngle().scale(6));
        landSafely(player);
        Fx.particles(level, ParticleTypes.PORTAL, player.position().add(0, 1, 0), 40, 0.5, 0.5);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2, 1);
        Tasks.every(8, 1, 151, n -> {
            if (player.onGround() || !player.isAlive()) return false;
            Fx.particles(level, ParticleTypes.REVERSE_PORTAL, player.position(), 6, 0.2, 0.02);
            return true;
        });
    }

    /** Launch: everything within three blocks of what you look at goes up in the air. */
    static void launch(ServerPlayer player, ServerLevel level) {
        Vec3 at = targetEntity(player, 50);
        beam(player, level, at, Spell.LAUNCH);
        Fx.firework(level, at, Shape.SMALL_BALL, Fx.BLUE, Fx.PURPLE, false, false);
        for (LivingEntity entity : near(level, at, 3, null)) {
            push(entity, new Vec3(0, 1.5, 0));
            Tasks.every(6, 3, 51, n -> {
                if (entity.onGround() || !entity.isAlive()) return false;
                Fx.particles(level, ParticleTypes.REVERSE_PORTAL, entity.position(), 6, 0.2, 0.02);
                return true;
            });
        }
    }

    /**
     * Comet (fast, a fiery trail, a big orange burst where it hits) and Explosive (a bit slower, a black trail, and a
     * blast of 4 with fire where it hits): a fireball the plugin pushed along faster every tick.
     */
    static void fireball(ServerPlayer player, ServerLevel level, boolean explosive) {
        Vec3 look = player.getLookAngle(), speed = look.scale(explosive ? 2 : 4);
        SmallFireball fireball = new SmallFireball(level, player, look);
        fireball.setPos(player.getEyePosition().add(look.scale(2)));
        fireball.setDeltaMovement(speed);
        level.addFreshEntity(fireball);
        Vec3[] last = {fireball.position()};
        Tasks.every(1, 1, explosive ? 201 : 101, n -> {
            if (!fireball.isAlive()) {
                Vec3 at = last[0];
                if (explosive) {
                    level.explode(null, at.x, at.y, at.z, 4, true, Level.ExplosionInteraction.TNT);
                    Fx.firework(level, at, Shape.LARGE_BALL, Fx.BLACK, Fx.ORANGE, true, true);
                } else {
                    Fx.firework(level, at, Shape.LARGE_BALL, Fx.ORANGE, Fx.RED, true, true);
                }
                return false;
            }
            last[0] = fireball.position();
            fireball.setDeltaMovement(speed); // straight on, and fast
            if (n >= (explosive ? 200 : 100)) fireball.discard();
            return true;
        });
        Tasks.every(explosive ? 2 : 4, explosive ? 3 : 2, explosive ? 81 : 51, n -> {
            if (!fireball.isAlive()) return false;
            if (explosive) Fx.firework(level, fireball.position(), Shape.BURST, Fx.BLACK, Fx.BLACK, false, false);
            else Fx.firework(level, fireball.position(), Shape.BURST, Fx.ORANGE, Fx.RED, false, false);
            return true;
        });
    }

    /** Lightning Storm: a ring of fireworks high round you, then lightning on everything within ten blocks. */
    static void lightningStorm(ServerPlayer player, ServerLevel level) {
        Vec3 centre = player.position();
        List<Vec3> ring = new ArrayList<>();
        int r = 13, cx = player.getBlockX(), cy = player.getBlockY(), cz = player.getBlockZ();
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
            int d = (cx - x) * (cx - x) + (cz - z) * (cz - z);
            if (d < r * r && d >= (r - 1) * (r - 1)) ring.add(new Vec3(x + 0.5, cy + 15, z + 0.5));
        }
        ring.sort(Comparator.comparingDouble(p -> Math.atan2(p.z - centre.z, p.x - centre.x))); // one after the other, round the circle
        for (int i = 0; i < ring.size(); i++) {
            Vec3 at = ring.get(i);
            Tasks.later(i, () -> Fx.firework(level, at, Shape.SMALL_BALL, Fx.AQUA, Fx.WHITE, false, false));
        }
        Tasks.later(ring.size(), () -> {
            if (!player.isAlive()) return;
            for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(10))) lightning(level, entity.position(), player);
            Fx.firework(level, centre.add(0, 5, 0), Shape.STAR, Fx.AQUA, Fx.AQUA, false, false);
        });
    }

    /** Lightning Arrow: an arrow with a sparkling trail; where it stops, lightning strikes. */
    static void lightningArrow(ServerPlayer player, ServerLevel level) {
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 3, 1);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(arrow);
        Vec3[] old = {null};
        Tasks.every(2, 4, 100, n -> {
            if (!arrow.isAlive()) return false;
            Vec3 now = arrow.position();
            if (old[0] != null && old[0].distanceToSqr(now) < 1.0e-4) { // it stopped
                arrow.discard();
                lightning(level, now, player);
                return false;
            }
            old[0] = now;
            Fx.firework(level, now, Shape.BURST, Fx.AQUA, Fx.WHITE, false, false);
            if (n >= 24) arrow.discard();
            return true;
        });
    }

    /** Escape: up and away forward, then down hard, while the spot you left blows up behind you. */
    static void escape(ServerPlayer player, ServerLevel level) {
        Vec3 from = player.position();
        float yaw = player.getYRot();
        push(player, direction(yaw, -40).scale(4));
        landSafely(player);
        Tasks.later(25, () -> {
            if (!player.isAlive()) return;
            push(player, direction(yaw, 80).scale(3));
            landSafely(player);
        });
        Tasks.every(8, 5, 11, n -> {
            if (player.onGround()) return false;
            Fx.particles(level, ParticleTypes.FLAME, player.position(), 20, 1, 0.02);
            return true;
        });
        Tasks.later(40, () -> level.explode(null, from.x, from.y + 1, from.z, 5, true, Level.ExplosionInteraction.NONE));
    }

    /** A direction from a yaw and a pitch (degrees), like the plugin worked it out. */
    static Vec3 direction(float yaw, double pitch) {
        double h = Math.cos(Math.toRadians(pitch));
        return new Vec3(-h * Math.sin(Math.toRadians(yaw)), -Math.sin(Math.toRadians(pitch)), h * Math.cos(Math.toRadians(yaw)));
    }

    /**
     * Blood Block (on both wands): the first click turns the block you look at to redstone; the second throws it up
     * and at whatever you look at then, where it blows up.
     */
    static void bloodBlock(ServerPlayer player, ServerLevel level) {
        float power = BLOOD.get() ? 7 : 5; // from Blood Magic it is bigger
        BlockPos armed = BLOOD_BLOCKS.remove(player.getUUID());
        if (armed == null || !level.getBlockState(armed).is(Blocks.REDSTONE_BLOCK)) { // the first click
            BlockPos at = target(player, 50);
            beam(player, level, Vec3.atCenterOf(at), Spell.BLOOD_BLOCK);
            BlockState redstone = Blocks.REDSTONE_BLOCK.defaultBlockState();
            level.setBlock(at, redstone, 3);
            BLOOD_BLOCKS.put(player.getUUID(), at);
            Tasks.every(1, 5, 61, n -> {
                if (!level.getBlockState(at).is(Blocks.REDSTONE_BLOCK)) {
                    BLOOD_BLOCKS.remove(player.getUUID(), at);
                    return false;
                }
                Fx.blockDust(level, at, redstone);
                return true;
            });
            return;
        }
        Vec3 aim = Vec3.atCenterOf(target(player, 50));
        beam(player, level, aim, Spell.BLOOD_BLOCK);
        FallingBlockEntity block = FallingBlockEntity.fall(level, armed, Blocks.REDSTONE_BLOCK.defaultBlockState());
        block.disableDrop();
        block.dropItem = false;
        push(block, new Vec3(0, 2, 0));
        Vec3[] last = {block.position()};
        Tasks.later(20, () -> {
            if (block.isAlive()) push(block, aim.subtract(block.position()).normalize());
        });
        Tasks.every(5, 2, 101, n -> {
            if (!block.isAlive()) return false;
            for (int i = 0; i < 4; i++) Fx.blockDust(level, block.blockPosition(), Blocks.REDSTONE_WIRE.defaultBlockState());
            return true;
        });
        Tasks.every(21, 1, 301, n -> {
            BlockPos below = BlockPos.containing(last[0]).below();
            boolean landed = !block.isAlive() || !(level.getBlockState(below).isAir() || level.getBlockState(below).is(Blocks.WATER));
            if (!landed) {
                last[0] = block.position();
                block.time = 1; // keep it in the air as long as it flies
                return true;
            }
            level.explode(null, last[0].x, last[0].y, last[0].z, power, false, Level.ExplosionInteraction.TNT);
            block.discard();
            return false;
        });
    }

    /** Confuse: everything within three blocks of what you look at gets dizzy for 25 seconds. */
    static void confuse(ServerPlayer player, ServerLevel level) {
        Vec3 at = targetEntity(player, 50);
        beam(player, level, at, Spell.CONFUSE);
        Fx.firework(level, at, Shape.BURST, new int[] {0x2F002F, 0x480048}, new int[] {0}, false, false);
        for (LivingEntity entity : near(level, at, 3, null)) entity.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 500, 3));
    }

    /** Poison Wave: a wave along your look, block by block, that poisons whatever it passes. */
    static void poisonWave(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        Tasks.every(1, 1, 51, n -> {
            Vec3 at = eye.add(look.scale(n));
            zap(level, at, player, 0, true);
            Fx.firework(level, at, Shape.BURST, new int[] {0x003C0A, Fx.BLACK}, new int[] {}, true, true);
            return true;
        });
    }

    /** Blood Wave: a wave of blood along your look (from three blocks out), a zap and a dark red burst every third block. */
    static void bloodWave(ServerPlayer player, ServerLevel level) {
        float damage = BLOOD.get() ? 6 : 3;
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        BlockState wire = Blocks.REDSTONE_WIRE.defaultBlockState();
        Tasks.every(1, 1, 31, n -> {
            BlockPos at = BlockPos.containing(eye.add(look.scale(3 + n)));
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) Fx.blockDust(level, at.offset(dx, dy, 0), wire);
            if ((n + 1) % 3 == 0) {
                zap(level, Vec3.atCenterOf(at), player, damage, false);
                Fx.firework(level, Vec3.atCenterOf(at), Shape.BURST, 0x400000, Fx.BLACK, false, true);
            }
            return true;
        });
    }

    /** Jump: straight up, a trail of fireworks on the way down, and a blast of 7 where you land (you feel nothing). */
    static void jump(ServerPlayer player, ServerLevel level) {
        push(player, new Vec3(0, 4, 0));
        landSafely(player);
        Tasks.every(35, 2, 31, n -> {
            if (!player.isAlive()) return false;
            if (!player.onGround()) {
                Fx.firework(level, player.position(), Shape.LARGE_BALL, new int[] {Fx.ORANGE, Fx.BLACK}, new int[] {Fx.BLACK}, false, false);
                return true;
            }
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 10, 100));
            level.explode(null, player.getX(), player.getY(), player.getZ(), 7, true, Level.ExplosionInteraction.TNT);
            return false;
        });
    }

    /**
     * Capture: picks up the creature you look at (within ten blocks) and carries it above your head; cast again to
     * throw it. (In 2013 it rode on the player; Minecraft no longer lets anything ride a player, so it floats along.)
     */
    static void capture(ServerPlayer player, ServerLevel level) {
        LivingEntity carried = CARRIED.remove(player.getUUID());
        if (carried != null && carried.isAlive()) {
            carried.setNoGravity(false);
            push(carried, player.getLookAngle().scale(3));
            teleportFx(level, carried.position());
            return;
        }
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(100));
        LivingEntity best = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(10), e -> e != player && e.isAlive())) {
            var hit = entity.getBoundingBox().inflate(1.5).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearest) {
                nearest = eye.distanceToSqr(hit.get());
                best = entity;
            }
        }
        if (best == null) return;
        LivingEntity caught = best;
        beam(player, level, caught.position().add(0, caught.getBbHeight() / 2, 0), Spell.CAPTURE);
        CARRIED.put(player.getUUID(), caught);
        caught.setNoGravity(true);
        carry(player, caught);
        teleportFx(level, caught.position());
        Tasks.every(1, 1, Integer.MAX_VALUE, n -> {
            if (CARRIED.get(player.getUUID()) != caught) return false;
            if (!caught.isAlive() || !player.isAlive() || player.hasDisconnected() || caught.level() != player.level()) {
                CARRIED.remove(player.getUUID(), caught);
                caught.setNoGravity(false);
                return false;
            }
            carry(player, caught);
            if (n % 3 == 0) Fx.particles(level, ParticleTypes.PORTAL, caught.position().add(0, 1.5, 0), 6, 0.2, 0.1);
            return true;
        });
    }

    /** Right above the player's head, still. */
    private static void carry(ServerPlayer player, LivingEntity caught) {
        caught.snapTo(player.getX(), player.getY() + player.getBbHeight() + 0.1, player.getZ(), player.getYRot(), 0);
        caught.setDeltaMovement(Vec3.ZERO);
        caught.resetFallDistance();
        caught.needsSync = true;
    }

    public static @Nullable LivingEntity carried(Player player) {
        return CARRIED.get(player.getUUID());
    }

    private static void teleportFx(ServerLevel level, Vec3 at) {
        Fx.particles(level, ParticleTypes.PORTAL, at, 40, 0.5, 0.5);
        Fx.particles(level, ParticleTypes.SMOKE, at, 8, 0.3, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2, 1);
    }

    /** A player leaves: their blood block and what they carry are let go. */
    public static void forget(Player player) {
        BLOOD_BLOCKS.remove(player.getUUID());
        LivingEntity carried = CARRIED.remove(player.getUUID());
        if (carried != null) carried.setNoGravity(false);
    }
}
