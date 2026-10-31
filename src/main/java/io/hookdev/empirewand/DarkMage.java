package io.hookdev.empirewand;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion.Shape;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Dark Mage (owner, 31 Oct 2026): a robed wizard with an obsidian Empire Wand who keeps his distance and throws
 * your own spells at you (Spark, Comet, Confuse, Launch, Freeze), and blinks away when hit. He lives in Wizard Towers;
 * he often drops a scroll (ScrollsModifier), now and then his wand.
 */
public class DarkMage extends Monster {
    private int castIn = 40;

    public DarkMage(EntityType<? extends DarkMage> type, Level level) {
        super(type, level);
        this.xpReward = 15;
        ItemStack wand = new ItemStack(Content.EMPIRE_WAND.get());
        wand.set(Content.SKIN.get(), 2); // obsidian
        setItemSlot(EquipmentSlot.MAINHAND, wand);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.FOLLOW_RANGE, 32).add(Attributes.ARMOR, 2);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new KeepDistanceGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, DarkMage.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || distanceToSqr(target) > 24 * 24 || !hasLineOfSight(target)) return;
        if (--castIn > 0) return;
        castIn = 45 + getRandom().nextInt(30);
        cast(level, target);
    }

    /** One of his spells at the target: a beam from his wand, a cast sound, a swing. */
    void cast(ServerLevel level, LivingEntity target) {
        Vec3 from = getEyePosition().add(getLookAngle().scale(0.6)), at = target.position().add(0, target.getBbHeight() / 2, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, getSoundSource(), 1, 1);
        McVersion.swing(this);
        switch (getRandom().nextInt(5)) {
            case 0 -> { // Spark
                Fx.beam(level, from, at, Spell.SPARK.colour);
                Fx.firework(level, at, Shape.BURST, Fx.BLACK, Fx.RED, false, true);
                target.hurtServer(level, level.damageSources().indirectMagic(this, this), 4);
            }
            case 1 -> { // Comet
                Vec3 dir = at.subtract(from).normalize();
                SmallFireball fireball = new SmallFireball(level, this, dir);
                fireball.setPos(from);
                fireball.setDeltaMovement(dir.scale(1.6));
                level.addFreshEntity(fireball);
            }
            case 2 -> { // Confuse
                Fx.beam(level, from, at, Spell.CONFUSE.colour);
                Fx.firework(level, at, Shape.BURST, new int[] {0x2F002F, 0x480048}, new int[] {0}, false, false);
                target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 1));
            }
            case 3 -> { // Launch
                Fx.beam(level, from, at, Spell.LAUNCH.colour);
                target.setDeltaMovement(target.getDeltaMovement().add(0, 1.2, 0));
                target.needsSync = true;
                if (target instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
            default -> { // Freeze
                Fx.beam(level, from, at, Spell.FREEZE.colour);
                Fx.particles(level, ParticleTypes.SNOWFLAKE, at, 30, 0.5, 0.05);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3));
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 40));
            }
        }
    }

    /** Hit: now and then he blinks away, in a puff of the void. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && isAlive() && getRandom().nextFloat() < 0.3f) {
            Fx.particles(level, ParticleTypes.PORTAL, position().add(0, 1, 0), 40, 0.5, 0.5);
            for (int i = 0; i < 16; i++) {
                double x = getX() + (getRandom().nextDouble() - 0.5) * 16, z = getZ() + (getRandom().nextDouble() - 0.5) * 16;
                if (McVersion.randomTeleport(this, x, getY() + getRandom().nextInt(5) - 2, z)) {
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, getSoundSource(), 1, 1);
                    break;
                }
            }
        }
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.EVOKER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.EVOKER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.EVOKER_DEATH;
    }

    /** Not too close, not too far: he keeps between six and fourteen blocks from his target. */
    private class KeepDistanceGoal extends Goal {
        private int ticks;

        KeepDistanceGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return getTarget() != null && getTarget().isAlive();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30, 30);
            if (ticks++ % 10 != 0) return;
            double d = distanceTo(target);
            if (d > 14) {
                getNavigation().moveTo(target, 1.0);
            } else if (d < 6) {
                Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(DarkMage.this, 10, 4, target.position());
                if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.2);
            } else {
                getNavigation().stop();
            }
        }
    }
}
