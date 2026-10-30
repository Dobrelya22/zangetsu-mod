package com.zangetsu.logic;

import com.zangetsu.entity.ArrowHalfEntity;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.registry.ModAttachments;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ProjectileDeflection;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Что меч делает сам, когда глаз открыт. */
public final class Abilities {
    private static final double PARRY_RANGE = 4.5;
    private static final double AUTO_ATTACK_RANGE = 4.0;
    private static final int AUTO_ATTACK_COOLDOWN = 16;
    private static final int AUTO_SLASH_TICKS = 8;

    private static final Map<UUID, Long> NEXT_AUTO_ATTACK = new HashMap<>();

    private Abilities() {
    }

    /** Уровень I+: стрелы, летящие в игрока, рассекаются пополам; прочие снаряды сбиваются. */
    public static void parryProjectiles(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Box area = player.getBoundingBox().expand(PARRY_RANGE + 2.0);
        Vec3d center = player.getBoundingBox().getCenter();
        List<ProjectileEntity> projectiles = world.getEntitiesByClass(ProjectileEntity.class, area,
                p -> p.isAlive() && p.getOwner() != player && p.getVelocity().lengthSquared() > 0.04);
        for (ProjectileEntity projectile : projectiles) {
            Vec3d pos = projectile.getPos();
            Vec3d velocity = projectile.getVelocity();
            Vec3d toPlayer = center.subtract(pos);
            double distance = toPlayer.length();
            if (distance > PARRY_RANGE || velocity.dotProduct(toPlayer) <= 0.0) {
                continue;
            }
            // Насколько близко пролетит снаряд — рубим только те, что попадут.
            double along = velocity.normalize().dotProduct(toPlayer);
            Vec3d closest = pos.add(velocity.normalize().multiply(along));
            if (closest.distanceTo(center) > 1.6) {
                continue;
            }
            slice(world, player, projectile);
        }
    }

    private static void slice(ServerWorld world, ServerPlayerEntity player, ProjectileEntity projectile) {
        Vec3d pos = projectile.getPos();
        player.swingHand(player.getMainHandStack().isEmpty() ? Hand.OFF_HAND : Hand.MAIN_HAND, true);
        Fx.particles(world, ParticleTypes.SWEEP_ATTACK, pos, 1, 0.0, 0.0);
        Fx.particles(world, ParticleTypes.CRIT, pos, 8, 0.1, 0.25);
        Fx.sound(world, pos, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, 1.4F);
        Fx.sound(world, pos, SoundEvents.ITEM_SHIELD_BLOCK, 0.6F, 1.8F);
        if (projectile instanceof ArrowEntity || projectile instanceof SpectralArrowEntity) {
            // Стрела — пополам: две половинки разлетаются и падают.
            ArrowHalfEntity.spawnPair(world, (PersistentProjectileEntity) projectile, projectile instanceof SpectralArrowEntity);
            projectile.discard();
        } else if (projectile instanceof TridentEntity || projectile instanceof FireballEntity) {
            // Трезубец и огненный шар гаста отбиваются туда, куда смотрит игрок.
            projectile.deflect(ProjectileDeflection.REDIRECTED, player, player, true);
        } else {
            Fx.particles(world, ParticleTypes.SMOKE, pos, 10, 0.1, 0.02);
            projectile.discard();
        }
    }

    /** Уровень III+: меч сам рубит ближайшего злого моба. */
    public static void autoAttack(ServerPlayerEntity player) {
        long now = player.getServer().getTicks();
        Long next = NEXT_AUTO_ATTACK.get(player.getUuid());
        if (next != null && now < next) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        List<MobEntity> mobs = world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(AUTO_ATTACK_RANGE),
                mob -> mob.isAlive() && isFoe(mob, player) && player.canSee(mob)
                        && (ModAttachments.limbs(mob) & Limbs.SPLIT) == 0);
        MobEntity target = null;
        double best = Double.MAX_VALUE;
        for (MobEntity mob : mobs) {
            double d = mob.squaredDistanceTo(player);
            if (d < best && d <= AUTO_ATTACK_RANGE * AUTO_ATTACK_RANGE + 1.0) {
                best = d;
                target = mob;
            }
        }
        if (target == null) {
            return;
        }
        NEXT_AUTO_ATTACK.put(player.getUuid(), now + AUTO_ATTACK_COOLDOWN);
        float damage = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * 0.75F;
        // Меч рвётся к цели сам: своя анимация с углом к мобу (рука — следом) и золотой шлейф у всех, кто видит.
        Vec3d to = target.getPos().subtract(player.getPos());
        int toward = Math.round(MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0F - player.getHeadYaw()));
        Sync.animation(player, AnimationPayload.AUTO_SLASH, AUTO_SLASH_TICKS, toward);
        Vec3d at = target.getBoundingBox().getCenter();
        Fx.particles(world, ParticleTypes.SWEEP_ATTACK, at, 1, 0.0, 0.0);
        Fx.particles(world, Fx.EYE_GOLD, at, 6, 0.3, 0.0);
        Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8F, 0.9F);
        target.damage(world.getDamageSources().playerAttack(player), damage);
        Vec3d push = target.getPos().subtract(player.getPos()).normalize();
        target.takeKnockback(0.35, -push.x, -push.z);
    }

    /** Злой моб, которого меч бьёт сам. Нейтральных не трогает, пока те сами не нападут. */
    public static boolean isFoe(MobEntity mob, ServerPlayerEntity player) {
        if (!(mob instanceof Monster)) {
            return false;
        }
        if (mob instanceof Angerable || mob instanceof AbstractPiglinEntity) {
            return mob.getTarget() == player;
        }
        return true;
    }

    /**
     * Уровень II+: удар моба в ближнем бою парируется. Если моб — зомби, пиглин, скелет или эндермен,
     * меч отсекает ему конечность; если рубить уже нечего — бьёт в ответ. Возвращает true, если удар отбит.
     */
    public static boolean tryParryMelee(ServerPlayerEntity player, DamageSource source, ZangetsuData data) {
        if (!(source.getAttacker() instanceof LivingEntity attacker) || source.getSource() != attacker
                || source.isIn(DamageTypeTags.IS_PROJECTILE) || source.isIn(DamageTypeTags.IS_EXPLOSION)
                || attacker == player || attacker.squaredDistanceTo(player) > 36.0) {
            return false;
        }
        float chance = data.hollowActive ? 1.0F : switch (data.level()) {
            case 2 -> 0.7F;
            case 3 -> 0.85F;
            default -> 1.0F;
        };
        if (player.getRandom().nextFloat() > chance) {
            return false;
        }
        ServerWorld world = player.getServerWorld();
        Vec3d between = player.getEyePos().add(attacker.getEyePos()).multiply(0.5);
        player.swingHand(Hand.MAIN_HAND, true);
        Fx.particles(world, ParticleTypes.CRIT, between, 14, 0.2, 0.4);
        Fx.particles(world, ParticleTypes.SWEEP_ATTACK, between, 1, 0.0, 0.0);
        Fx.sound(player, SoundEvents.ITEM_SHIELD_BLOCK, 1.0F, 1.5F);
        Fx.sound(player, SoundEvents.BLOCK_ANVIL_LAND, 0.35F, 1.9F);
        boolean cut = Limbs.cutNext(attacker, player);
        if (!cut) {
            float damage = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * 0.6F;
            attacker.damage(world.getDamageSources().playerAttack(player), damage);
        }
        Vec3d push = attacker.getPos().subtract(player.getPos()).normalize();
        attacker.takeKnockback(0.5, -push.x, -push.z);
        return true;
    }

    static void forget(Entity player) {
        NEXT_AUTO_ATTACK.remove(player.getUuid());
    }
}
