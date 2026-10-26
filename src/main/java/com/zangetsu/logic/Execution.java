package com.zangetsu.logic;

import com.zangetsu.entity.SeveredPartEntity;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.registry.ModAttachments;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Казнь. Безрукому зомби или пиглину меч отрубает голову; эндермена с подрубленными коленями
 * рассекает горизонтальным ударом пополам. Игрок замирает, камера уходит за спину.
 */
public final class Execution {
    public static final int DURATION = 32;
    private static final int HIT_TICK = 15;
    private static final Set<UUID> BUSY = new HashSet<>();

    private Execution() {
    }

    public static void request(ServerPlayerEntity player, int targetId) {
        ZangetsuData data = ModAttachments.data(player);
        if (!data.bonded || !SwordStacks.isHolding(player) || BUSY.contains(player.getUuid())) {
            return;
        }
        Entity entity = player.getServerWorld().getEntityById(targetId);
        if (!(entity instanceof LivingEntity target) || !Limbs.executable(target) || target.squaredDistanceTo(player) > 36.0
                || BUSY.contains(target.getUuid())) {
            return;
        }
        start(player, target);
    }

    private static void start(ServerPlayerEntity player, LivingEntity target) {
        ServerWorld world = player.getServerWorld();
        boolean enderman = Limbs.isEnderman(target);
        BUSY.add(player.getUuid());
        BUSY.add(target.getUuid());

        // Моб замирает лицом к игроку, игрок — лицом к мобу.
        if (target instanceof MobEntity mob) {
            mob.setAiDisabled(true);
        }
        target.setVelocity(Vec3d.ZERO);
        Vec3d toPlayer = player.getPos().subtract(target.getPos());
        float yaw = (float) Math.toDegrees(Math.atan2(toPlayer.z, toPlayer.x)) - 90.0F;
        target.setYaw(yaw);
        target.setBodyYaw(yaw);
        target.setHeadYaw(yaw);
        Cinematic.face(player, target.getEyePos());
        Cinematic.play(player, enderman ? AnimationPayload.SLASH : AnimationPayload.EXECUTE, DURATION);

        Fx.sound(player, SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.8F, 0.5F);
        Fx.sound(player, SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 0.6F, 0.6F);
        Fx.particles(world, Fx.REIATSU_BLACK, player.getPos().add(0, 1, 0), 30, 0.4, 0.02);

        Scheduler.later(HIT_TICK, () -> {
            if (!player.isAlive() || !target.isAlive() || target.getWorld() != player.getWorld()) {
                finish(player, target);
                return;
            }
            if (enderman) {
                splitEnderman(world, player, target);
            } else {
                behead(world, player, target);
            }
            Bond.change(player, Bond.EXECUTION_BONUS);
        });
        Scheduler.later(DURATION, () -> finish(player, target));
    }

    private static void behead(ServerWorld world, ServerPlayerEntity player, LivingEntity target) {
        Vec3d head = Limbs.modelToWorld(target, 0.0, -4.0, 0.0);
        Limbs.set(target, ModAttachments.limbs(target) | Limbs.HEAD);
        Vec3d away = target.getPos().subtract(player.getPos()).normalize();
        SeveredPartEntity.spawn(world, target, SeveredPartEntity.HEAD, head, away.multiply(0.18).add(0.0, 0.42, 0.0), target.getBodyYaw());
        Fx.particles(world, Fx.BLOOD, head, 60, 0.25, 0.12);
        Fx.particles(world, ParticleTypes.SWEEP_ATTACK, head, 2, 0.2, 0.0);
        Fx.particles(world, ParticleTypes.CRIT, head, 20, 0.3, 0.5);
        Fx.sound(target, SoundEvents.ITEM_TRIDENT_HIT, 1.2F, 0.6F);
        Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
        Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
        kill(world, player, target);
    }

    private static void splitEnderman(ServerWorld world, ServerPlayerEntity player, LivingEntity target) {
        // Колени подрублены — эндермен осел; пояс у него примерно на высоте 0,9 блока.
        Vec3d top = Limbs.modelToWorld(target, 0.0, 2.0, 0.0);
        Vec3d bottom = Limbs.modelToWorld(target, 0.0, 16.0, 0.0);
        Limbs.set(target, ModAttachments.limbs(target) | Limbs.SPLIT);
        Vec3d away = target.getPos().subtract(player.getPos()).normalize();
        Vec3d side = new Vec3d(-away.z, 0.0, away.x);
        SeveredPartEntity.spawn(world, target, SeveredPartEntity.ENDER_TOP, top, side.multiply(0.16).add(away.multiply(0.1)).add(0, 0.22, 0),
                target.getBodyYaw());
        SeveredPartEntity.spawn(world, target, SeveredPartEntity.ENDER_BOTTOM, bottom, away.multiply(0.04), target.getBodyYaw());
        Vec3d waist = Limbs.modelToWorld(target, 0.0, 8.0, 0.0);
        Fx.particles(world, Fx.ENDER_BLOOD, waist, 70, 0.4, 0.1);
        Fx.particles(world, ParticleTypes.PORTAL, waist, 80, 0.5, 0.6);
        Fx.particles(world, ParticleTypes.SWEEP_ATTACK, waist, 3, 0.6, 0.0);
        Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
        Fx.sound(target, SoundEvents.ENTITY_ENDERMAN_DEATH, 1.0F, 0.8F);
        Fx.sound(target, SoundEvents.ITEM_TRIDENT_HIT, 1.0F, 0.5F);
        kill(world, player, target);
    }

    private static void kill(ServerWorld world, ServerPlayerEntity player, LivingEntity target) {
        if (target instanceof MobEntity mob) {
            mob.setAiDisabled(false);
        }
        target.timeUntilRegen = 0;
        target.damage(world.getDamageSources().playerAttack(player), Float.MAX_VALUE);
    }

    private static void finish(ServerPlayerEntity player, LivingEntity target) {
        BUSY.remove(player.getUuid());
        BUSY.remove(target.getUuid());
        if (target.isAlive() && target instanceof MobEntity mob) {
            mob.setAiDisabled(false);
        }
    }
}
