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

    /** When each player's last Black Hole and Tornado end (both at once: an advancement). */
    private static final Map<UUID, Long> HOLES = new HashMap<>(), TORNADOES = new HashMap<>();

    public static void cast(ServerPlayer player, Spell spell) {
        cast(player, spell, 1);
    }

    /**
     * A left-click with the wand: its spell, unless the wand is still cooling down; then the wand cools down for as long
     * as the spell says (half as long again after a charged cast). Not in creative.
     */
    public static boolean castFromWand(ServerPlayer player, WandItem wand, int charge) {
        var stack = player.getMainHandItem();
        if (player.getCooldowns().isOnCooldown(stack)) return false;
        Spell spell = wand.spell(stack);
        boolean charged = charge >= CastSpell.FULL;
        cast(player, spell, charged ? CHARGED : 1);
        if (!player.getAbilities().instabuild) player.getCooldowns().addCooldown(stack, Math.round(spell.cooldown * (charged ? 1.5f : 1)));
        return true;
    }

    /** How much stronger a fully charged cast is (hold left-click a second). */
    public static final float CHARGED = 1.6f;
    /** The power of the spell being cast now: 1, or CHARGED. */
    private static final ThreadLocal<Float> POWER = ThreadLocal.withInitial(() -> 1f);

    static float power() {
        return POWER.get();
    }

    /** How much further the wand reaches now (its Reach enchantment). */
    private static final ThreadLocal<Float> REACH = ThreadLocal.withInitial(() -> 1f);

    /** The level of one of the wand's enchantments (empirewand:power, reach, multicast) on the wand in the player's main hand. */
    static int enchantment(ServerPlayer player, String name) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, EmpireWand.id(name));
        return player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).get(key)
                .map(holder -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder, player.getMainHandItem())).orElse(0);
    }

    /**
     * Casts a spell at this power (1, or CHARGED when left-click was held): bigger blasts, more damage, further, longer.
     * The wand's enchantments (owner, 31 Oct 2026): Power adds 15% a level, Reach 25% a level to how far it reaches,
     * Multicast gives a 20% chance a level that the spell goes off twice.
     */
    public static void cast(ServerPlayer player, Spell spell, float power) {
        power *= 1 + 0.15f * enchantment(player, "power");
        float reach = 1 + 0.25f * enchantment(player, "reach");
        int multicast = enchantment(player, "multicast");
        if (multicast > 0 && player.getRandom().nextFloat() < 0.2f * multicast) {
            float again = power;
            Tasks.later(5, () -> {
                if (player.isAlive() && !player.hasDisconnected()) cast(player, spell, again, reach);
            });
        }
        cast(player, spell, power, reach);
    }

    private static void cast(ServerPlayer player, Spell spell, float power, float reach) {
        REACH.set(reach);
        POWER.set(power);
        try {
            if (power >= CHARGED) {
                Fx.particles((ServerLevel) player.level(), ParticleTypes.END_ROD, tip(player), 30, 0.3, 0.15);
                Awards.award(player, "full_power");
            }
            castNow(player, spell);
        } finally {
            POWER.set(1f);
            REACH.set(1f);
        }
    }

    private static void castNow(ServerPlayer player, Spell spell) {
        ServerLevel level = (ServerLevel) player.level();
        Awards.cast(player, spell);
        long now = level.getGameTime();
        if (spell == Spell.BLACK_HOLE) HOLES.put(player.getUUID(), now + 80);
        if (spell == Spell.TORNADO) TORNADOES.put(player.getUUID(), now + 120);
        if (HOLES.getOrDefault(player.getUUID(), 0L) > now && TORNADOES.getOrDefault(player.getUUID(), 0L) > now) Awards.award(player, "end_of_days");
        level.playSound(null, player.getX(), player.getY(), player.getZ(), castSound(spell), SoundSource.PLAYERS, 0.8f, 1);
        switch (spell) {
            case EXPLODE -> explode(player, level);
            case SPARK -> spark(player, level, spell, Fx.BLACK, Fx.RED, 3);
            case LITTLE_SPARK -> spark(player, level, spell, Fx.WHITE, Fx.RED, 5);
            case BLOOD_SPARK -> spark(player, level, spell, 0x3D0000, 0x3D0000, 3);
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
            case METEOR_SHOWER -> meteorShower(player, level);
            case CHAIN_LIGHTNING -> chainLightning(player, level);
            case EARTHQUAKE -> earthquake(player, level);
            case TORNADO -> tornado(player, level);
            case ICE_WAVE -> iceWave(player, level);
            case FREEZE -> freeze(player, level);
            case BLIZZARD -> blizzard(player, level);
            case BLINK -> blink(player, level);
            case BLACK_HOLE -> blackHole(player, level);
            case LEVITATE -> levitate(player, level);
            case ROOTS -> roots(player, level);
            case SPORES -> spores(player, level);
            case OVERGROWTH -> overgrowth(player, level);
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
            case METEOR_SHOWER -> SoundEvents.GHAST_SHOOT;
            case CHAIN_LIGHTNING -> SoundEvents.EVOKER_PREPARE_ATTACK;
            case EARTHQUAKE -> SoundEvents.RAVAGER_ROAR;
            case TORNADO -> SoundEvents.PHANTOM_FLAP;
            case ICE_WAVE, FREEZE, BLIZZARD -> SoundEvents.GLASS_BREAK;
            case BLINK, LEVITATE -> SoundEvents.ENDERMAN_TELEPORT;
            case BLACK_HOLE -> SoundEvents.END_PORTAL_FRAME_FILL;
            case ROOTS, SPORES, OVERGROWTH -> SoundEvents.GRASS_BREAK;
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
        HitResult hit = player.pick(range * REACH.get(), 1, false);
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
        shatter(player, level, at);
        level.explode(null, at.x, at.y, at.z, 5 * power(), false, Level.ExplosionInteraction.TNT);
    }

    // ---------- combos (owner, 31 Oct 2026) ----------

    /** A combo: said above the hotbar, a sound, and an advancement the first time. */
    static void combo(ServerPlayer player, String name) {
        player.sendOverlayMessage(Component.translatable("message.empirewand.combo", Component.translatable("combo.empirewand." + name)).withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
        Awards.award(player, "combo");
    }

    /** Shatter: a blast near something frozen (Freeze, Ice Wave, Blizzard) breaks it like glass: 8 damage more. */
    static void shatter(ServerPlayer player, ServerLevel level, Vec3 at) {
        boolean any = false;
        for (LivingEntity entity : near(level, at, 5, player)) {
            if (entity.getTicksFrozen() < entity.getTicksRequiredToFreeze()) continue;
            entity.setTicksFrozen(0);
            entity.hurtServer(level, level.damageSources().indirectMagic(player, player), 8 * power());
            Fx.blockDust(level, entity.blockPosition(), Blocks.ICE.defaultBlockState());
            Fx.blockDust(level, entity.blockPosition().above(), Blocks.PACKED_ICE.defaultBlockState());
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.8f);
            any = true;
        }
        if (any) combo(player, "shatter");
    }

    public static void shatterForTests(ServerPlayer player, ServerLevel level, Vec3 at) {
        shatter(player, level, at);
    }

    /** Each player's Tornado while it goes (its centre), and whether Spores made it toxic. */
    private static final Map<UUID, Vec3[]> TORNADO_AT = new HashMap<>();
    private static final java.util.Set<UUID> TOXIC = new java.util.HashSet<>();

    /** Spark and Little Spark (and Blood Spark): a firework where you look and a zap (Little Spark is the stronger one). */
    static void spark(ServerPlayer player, ServerLevel level, Spell spell, int colour, int fade, float damage) {
        Vec3 at = Vec3.atCenterOf(target(player, 50));
        beam(player, level, at, spell);
        Fx.firework(level, at, Shape.BURST, colour, fade, colour == 0x3D0000, true);
        zap(level, at, player, damage * power(), false);
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
            push(entity, new Vec3(0, 1.5 * power(), 0));
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
        float blast = 4 * power();
        SmallFireball fireball = new SmallFireball(level, player, look);
        fireball.setPos(player.getEyePosition().add(look.scale(2)));
        fireball.setDeltaMovement(speed);
        level.addFreshEntity(fireball);
        Vec3[] last = {fireball.position()};
        Tasks.every(1, 1, explosive ? 201 : 101, n -> {
            if (!fireball.isAlive()) {
                Vec3 at = last[0];
                if (explosive) {
                    level.explode(null, at.x, at.y, at.z, blast, true, Level.ExplosionInteraction.TNT);
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
        double reach = 10 * power();
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
            for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(reach))) lightning(level, entity.position(), player);
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
     * Blood Block: the first click turns the block you look at to redstone; the second throws it up
     * and at whatever you look at then, where it blows up.
     */
    static void bloodBlock(ServerPlayer player, ServerLevel level) {
        float power = 5 * power();
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
        float damage = 3;
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
        float blast = Math.min(10, 7 * power());
        Tasks.every(35, 2, 31, n -> {
            if (!player.isAlive()) return false;
            if (!player.onGround()) {
                Fx.firework(level, player.position(), Shape.LARGE_BALL, new int[] {Fx.ORANGE, Fx.BLACK}, new int[] {Fx.BLACK}, false, false);
                return true;
            }
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 10, 100));
            level.explode(null, player.getX(), player.getY(), player.getZ(), blast, true, Level.ExplosionInteraction.TNT);
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

    // ---------- new for the Empire Wand (owner, 31 Oct 2026) ----------

    /** The living thing the player looks at (within two blocks of their look), up to range blocks away, or none. */
    static @Nullable LivingEntity lookedAt(ServerPlayer player, ServerLevel level, double range) {
        Vec3 eye = player.getEyePosition(), end = Vec3.atCenterOf(target(player, range));
        LivingEntity best = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range), e -> e != player && e.isAlive())) {
            var hit = entity.getBoundingBox().inflate(1.5).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearest) {
                nearest = eye.distanceToSqr(hit.get());
                best = entity;
            }
        }
        return best;
    }

    /** Meteor Shower: twelve fireballs out of the sky round where you look, one after the other. */
    static void meteorShower(ServerPlayer player, ServerLevel level) {
        Vec3 centre = Vec3.atCenterOf(target(player, 60));
        beam(player, level, centre, Spell.METEOR_SHOWER);
        Tasks.every(1, 4, Math.round(12 * power()), n -> {
            double angle = level.getRandom().nextDouble() * Math.PI * 2, d = level.getRandom().nextDouble() * 6;
            Vec3 hit = centre.add(Math.cos(angle) * d, 0, Math.sin(angle) * d);
            Vec3 from = hit.add(-5 + level.getRandom().nextDouble() * 3, 28, -5 + level.getRandom().nextDouble() * 3);
            Vec3 dir = hit.subtract(from).normalize();
            var meteor = new net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball(level, player, dir, 2);
            meteor.setPos(from);
            meteor.setDeltaMovement(dir.scale(1.5));
            level.addFreshEntity(meteor);
            return true;
        });
    }

    /** Chain Lightning: a bolt into what you look at, which leaps on to the nearest creature (within eight blocks), five times. */
    static void chainLightning(ServerPlayer player, ServerLevel level) {
        LivingEntity first = lookedAt(player, level, 40);
        if (first == null) {
            Vec3 at = Vec3.atCenterOf(target(player, 40));
            beam(player, level, at, Spell.CHAIN_LIGHTNING);
            zap(level, at, player, 4, false);
            return;
        }
        List<LivingEntity> hit = new ArrayList<>();
        Vec3[] from = {tip(player)};
        LivingEntity[] next = {first};
        float damage = 6 * power();
        Vec3[] tornado = TORNADO_AT.get(player.getUUID());
        if (tornado != null) { // Storm: lightning into everything the tornado holds
            Vec3 c = tornado[0];
            for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(4, 8, 4), e -> e != player && e.isAlive())) lightning(level, caught.position(), player);
            combo(player, "storm");
        }
        Tasks.every(1, 3, Math.round((tornado != null ? 11 : 6) * power()), n -> {
            LivingEntity target = next[0];
            if (!target.isAlive()) return false;
            Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
            Fx.beam(level, from[0], to, Spell.CHAIN_LIGHTNING.colour);
            Fx.particles(level, ParticleTypes.ELECTRIC_SPARK, to, 20, 0.4, 0.3);
            level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6f, 1.6f);
            target.hurtServer(level, level.damageSources().indirectMagic(player, player), damage);
            hit.add(target);
            from[0] = to;
            next[0] = level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(8), e -> e != player && e.isAlive() && !hit.contains(e))
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(target))).orElse(null);
            return next[0] != null;
        });
    }

    /** Earthquake: rings of the ground jump up round you, wider and wider, and throw what stands on them in the air. */
    static void earthquake(ServerPlayer player, ServerLevel level) {
        BlockPos centre = player.blockPosition();
        Tasks.every(1, 2, Math.round(9 * power()), n -> {
            int r = n + 2;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < r - 0.5 || d >= r + 0.5) continue;
                BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.offset(x, 0, z)).below();
                if (Math.abs(top.getY() - centre.getY()) > 4) continue;
                BlockState state = level.getBlockState(top);
                if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, top) < 0 || !state.getFluidState().isEmpty()) continue;
                FallingBlockEntity block = FallingBlockEntity.fall(level, top, state);
                push(block, new Vec3(0, 0.35 + level.getRandom().nextDouble() * 0.15, 0));
            }
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre).inflate(r + 1, 3, r + 1), e -> e != player && e.isAlive())) {
                double d = Math.sqrt(entity.distanceToSqr(Vec3.atBottomCenterOf(centre)));
                if (d < r - 1 || d > r + 1) continue;
                push(entity, entity.getDeltaMovement().add(0, 0.8, 0));
                entity.hurtServer(level, level.damageSources().indirectMagic(player, player), 4);
            }
            level.playSound(null, centre.getX(), centre.getY(), centre.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4f, 0.5f);
            return true;
        });
    }

    /** Tornado: a whirlwind that crosses the land the way you look for six seconds, picks up whatever it meets and throws it off. */
    static void tornado(ServerPlayer player, ServerLevel level) {
        Vec3 look = player.getLookAngle(), flat = new Vec3(look.x, 0, look.z);
        Vec3 dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : flat.normalize();
        Vec3[] centre = {player.position().add(dir.scale(4))};
        int last = Math.round(120 * power());
        TORNADO_AT.put(player.getUUID(), centre);
        TOXIC.remove(player.getUUID());
        Tasks.every(1, 1, last + 1, n -> {
            if (n >= last) {
                TORNADO_AT.remove(player.getUUID(), centre);
                TOXIC.remove(player.getUUID());
            }
            Vec3 c = centre[0].add(dir.scale(0.2));
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(c.x), (int) Math.floor(c.z));
            c = new Vec3(c.x, ground, c.z);
            centre[0] = c;
            for (int k = 0; k < 24; k++) {
                double a = n * 0.6 + k * 0.8, radius = 0.4 + k * 0.12;
                level.sendParticles(TOXIC.contains(player.getUUID()) && k % 2 == 0 ? ParticleTypes.SPORE_BLOSSOM_AIR : ParticleTypes.CLOUD, c.x + Math.cos(a) * radius, c.y + k * 0.35, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
            }
            if (n % 10 == 0) level.playSound(null, c.x, c.y, c.z, SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1, 0.5f);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(4, 7, 4), e -> e != player && e.isAlive())) {
                Vec3 in = new Vec3(c.x - entity.getX(), 0, c.z - entity.getZ());
                Vec3 inward = in.lengthSqr() < 1.0e-4 ? Vec3.ZERO : in.normalize();
                if (TOXIC.contains(player.getUUID())) entity.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
                if (n >= last) { // the end: off they fly
                    push(entity, inward.scale(-1.5).add(0, 0.8, 0));
                    continue;
                }
                Vec3 round = new Vec3(-inward.z, 0, inward.x);
                push(entity, inward.scale(0.25).add(round.scale(0.6)).add(0, entity.getY() < c.y + 6 ? 0.35 : 0.05, 0));
            }
            return true;
        });
    }

    // ---------- the Frost Wand ----------

    /** Ice Wave: a wave of cold along your look: water freezes (for a while), what it passes is slowed and frozen. */
    static void iceWave(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        Tasks.every(1, 1, Math.round(20 * power()), n -> {
            Vec3 at = eye.add(look.scale(1 + n));
            Fx.particles(level, ParticleTypes.SNOWFLAKE, at, 15, 0.6, 0.02);
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(at).offset(-1, -3, -1), BlockPos.containing(at).offset(1, 0, 1))) {
                if (level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).isSource() && level.getBlockState(pos.above()).isAir()) {
                    level.setBlock(pos, Blocks.FROSTED_ICE.defaultBlockState(), 3);
                    level.scheduleTick(pos, Blocks.FROSTED_ICE, 60 + level.getRandom().nextInt(60)); // it melts again
                }
            }
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.5, 2.5, 1.5), e -> e != player && e.isAlive())) {
                entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3));
                entity.setTicksFrozen(Math.max(entity.getTicksFrozen(), entity.getTicksRequiredToFreeze() + 60));
                entity.hurtServer(level, level.damageSources().freeze(), 3);
            }
            return true;
        });
    }

    /** Freeze: what you look at freezes solid: it cannot move, and the cold bites for a while. */
    static void freeze(ServerPlayer player, ServerLevel level) {
        LivingEntity target = lookedAt(player, level, 40);
        Vec3 at = target != null ? target.position().add(0, target.getBbHeight() / 2, 0) : Vec3.atCenterOf(target(player, 40));
        beam(player, level, at, Spell.FREEZE);
        Fx.particles(level, ParticleTypes.SNOWFLAKE, at, 40, 0.6, 0.05);
        if (target == null) return;
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 9));
        target.setTicksFrozen(target.getTicksRequiredToFreeze() + 300);
        Fx.blockDust(level, target.blockPosition(), Blocks.ICE.defaultBlockState());
    }

    /** Blizzard: eight seconds of snowstorm round where you look: everything in it slows and freezes, snow settles. */
    static void blizzard(ServerPlayer player, ServerLevel level) {
        Vec3 centre = Vec3.atCenterOf(target(player, 50)).add(0, 0.5, 0);
        beam(player, level, centre, Spell.BLIZZARD);
        Tasks.every(1, 2, 80, n -> {
            level.sendParticles(ParticleTypes.SNOWFLAKE, centre.x, centre.y + 5, centre.z, 40, 6, 1.5, 6, 0.02);
            level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y + 6.5, centre.z, 6, 5, 0.3, 5, 0);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(6, 4, 6), e -> e != player && e.isAlive())) {
                entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
                entity.setTicksFrozen(Math.min(entity.getTicksFrozen() + 8, entity.getTicksRequiredToFreeze() + 100));
            }
            if (n % 5 == 0) for (int i = 0; i < 4; i++) { // snow settles
                BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(centre.add(level.getRandom().nextInt(13) - 6, 0, level.getRandom().nextInt(13) - 6)));
                BlockState snow = Blocks.SNOW.defaultBlockState();
                if (level.isEmptyBlock(top) && snow.canSurvive(level, top)) level.setBlock(top, snow, 3);
            }
            return true;
        });
    }

    // ---------- the Void Wand ----------

    /** Blink: you are where you look (up to forty blocks), in a flash. */
    static void blink(ServerPlayer player, ServerLevel level) {
        HitResult hit = player.pick(40, 1, false);
        Vec3 to = hit.getLocation().subtract(player.getLookAngle().scale(0.5));
        BlockPos spot = BlockPos.containing(to);
        for (int up = 0; up < 3 && !(level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir()); up++) spot = spot.above();
        teleportFx(level, player.position().add(0, 1, 0));
        player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        player.resetFallDistance();
        landSafely(player);
        teleportFx(level, player.position().add(0, 1, 0));
    }

    /** Black Hole: a dark swirl where you look pulls everything within ten blocks in for four seconds, then bursts. */
    static void blackHole(ServerPlayer player, ServerLevel level) {
        Vec3 centre = Vec3.atCenterOf(target(player, 40)).add(0, 1.5, 0);
        beam(player, level, centre, Spell.BLACK_HOLE);
        float blast = 4 * power();
        double pull = 10 * power();
        Tasks.every(1, 1, 81, n -> {
            if (n == 80) {
                level.explode(null, centre.x, centre.y, centre.z, blast, false, Level.ExplosionInteraction.TNT);
                Fx.firework(level, centre, Shape.LARGE_BALL, new int[] {0x4A1A70, Fx.BLACK}, new int[] {Fx.PURPLE}, true, true);
                return false;
            }
            level.sendParticles(ParticleTypes.SQUID_INK, centre.x, centre.y, centre.z, 6, 0.3, 0.3, 0.3, 0);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y, centre.z, 12, 2.5, 2.5, 2.5, 0.05);
            if (n % 20 == 0) level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 1, 0.5f);
            for (Entity entity : level.getEntities(player, new AABB(centre, centre).inflate(pull), e -> e.isAlive() && !(e instanceof FallingBlockEntity))) {
                Vec3 in = centre.subtract(entity.position());
                if (in.lengthSqr() > pull * pull || in.lengthSqr() < 0.25) continue;
                push(entity, entity.getDeltaMovement().scale(0.5).add(in.normalize().scale(0.35)));
            }
            return true;
        });
    }

    /** Levitate: what stands within four blocks of where you look floats up, and so does the ground there, for a few seconds. */
    static void levitate(ServerPlayer player, ServerLevel level) {
        BlockPos centre = target(player, 40);
        beam(player, level, Vec3.atCenterOf(centre), Spell.LEVITATE);
        for (LivingEntity entity : near(level, Vec3.atCenterOf(centre), 4, player)) entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 1));
        int lifted = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-2, -1, -2), centre.offset(2, 1, 2))) {
            if (lifted >= 12) break;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0 || !state.getFluidState().isEmpty() || !level.getBlockState(pos.above()).isAir()) continue;
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos.immutable(), state);
            block.setNoGravity(true);
            lifted++;
            Tasks.every(1, 1, 61, n -> {
                if (!block.isAlive()) return false;
                block.time = 1;
                if (n >= 60) { // down they come
                    block.setNoGravity(false);
                    return false;
                }
                push(block, new Vec3(0, 0.06, 0));
                return true;
            });
        }
        Fx.particles(level, ParticleTypes.REVERSE_PORTAL, Vec3.atCenterOf(centre), 40, 2, 0.05);
    }

    // ---------- the Nature Wand ----------

    /** Roots: roots grab what stands within three blocks of where you look: it cannot move or jump for five seconds. */
    static void roots(ServerPlayer player, ServerLevel level) {
        Vec3 at = targetEntity(player, 40);
        beam(player, level, at, Spell.ROOTS);
        for (LivingEntity entity : near(level, at, 3, player)) {
            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 9));
            entity.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 100, 128)); // a negative jump: none at all
            Fx.blockDust(level, entity.blockPosition(), Blocks.MANGROVE_ROOTS.defaultBlockState());
            Tasks.every(5, 10, 10, n -> {
                if (!entity.isAlive()) return false;
                Fx.blockDust(level, entity.blockPosition(), Blocks.MANGROVE_ROOTS.defaultBlockState());
                return true;
            });
        }
    }

    /** Spores: a poisonous cloud of spores where you look, for ten seconds. */
    static void spores(ServerPlayer player, ServerLevel level) {
        if (TORNADO_AT.containsKey(player.getUUID())) { // Toxic Tornado: the spores go into your whirlwind
            TOXIC.add(player.getUUID());
            combo(player, "toxic_tornado");
            return;
        }
        Vec3 at = targetEntity(player, 40);
        beam(player, level, at, Spell.SPORES);
        var cloud = new net.minecraft.world.entity.AreaEffectCloud(level, at.x, at.y, at.z);
        cloud.setOwner(player);
        cloud.setRadius(4);
        cloud.setDuration(200);
        cloud.setWaitTime(0);
        cloud.setCustomParticle(ParticleTypes.SPORE_BLOSSOM_AIR);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
        cloud.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
        level.addFreshEntity(cloud);
    }

    /** Overgrowth: everything within five blocks of where you look grows: grass, flowers, crops, saplings into trees. */
    static void overgrowth(ServerPlayer player, ServerLevel level) {
        BlockPos centre = target(player, 40);
        beam(player, level, Vec3.atCenterOf(centre), Spell.OVERGROWTH);
        ItemStack meal = new ItemStack(Items.BONE_MEAL, 64);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-5, -2, -5), centre.offset(5, 2, 5))) {
            if (pos.distSqr(centre) > 25) continue;
            for (int i = 0; i < 3; i++) {
                if (!net.minecraft.world.item.BoneMealItem.growCrop(meal, level, pos.immutable())) break;
                level.levelEvent(1505, pos, 15);
            }
            if (meal.isEmpty()) meal = new ItemStack(Items.BONE_MEAL, 64);
        }
        Fx.particles(level, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(centre), 40, 3, 0);
    }

    /** A player leaves: their blood block and what they carry are let go. */
    public static void forget(Player player) {
        TORNADO_AT.remove(player.getUUID());
        TOXIC.remove(player.getUUID());
        BLOOD_BLOCKS.remove(player.getUUID());
        LivingEntity carried = CARRIED.remove(player.getUUID());
        if (carried != null) carried.setNoGravity(false);
    }
}
