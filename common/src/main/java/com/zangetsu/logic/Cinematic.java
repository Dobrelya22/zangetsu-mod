package com.zangetsu.logic;

import com.zangetsu.ZangetsuMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Короткие «сцены»: игрок замирает, играет анимацию, камера у него уходит за спину. */
public final class Cinematic {
    private static final Identifier LOCK = ZangetsuMod.id("cinematic_lock");
    private static final Map<UUID, Long> LOCKED_UNTIL = new HashMap<>();

    private Cinematic() {
    }

    /** Анимация у всех, кто видит игрока; сам игрок на это время не может ходить и прыгать. */
    public static void play(ServerPlayerEntity player, int animation, int duration) {
        Sync.animation(player, animation, duration);
        lock(player, duration);
    }

    public static void lock(ServerPlayerEntity player, int ticks) {
        long until = player.getServer().getTicks() + ticks;
        LOCKED_UNTIL.merge(player.getUuid(), until, Math::max);
        apply(player, true);
        Scheduler.later(ticks, () -> {
            Long current = LOCKED_UNTIL.get(player.getUuid());
            if (current != null && current <= player.getServer().getTicks()) {
                LOCKED_UNTIL.remove(player.getUuid());
                apply(player, false);
            }
        });
    }

    public static boolean isLocked(ServerPlayerEntity player) {
        return LOCKED_UNTIL.containsKey(player.getUuid());
    }

    private static void apply(ServerPlayerEntity player, boolean locked) {
        modify(player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED), locked);
        modify(player.getAttributeInstance(EntityAttributes.GENERIC_JUMP_STRENGTH), locked);
    }

    private static void modify(EntityAttributeInstance attribute, boolean locked) {
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(LOCK);
        if (locked) {
            attribute.addTemporaryModifier(new EntityAttributeModifier(LOCK, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Повернуть игрока лицом к точке. */
    public static void face(ServerPlayerEntity player, Vec3d target) {
        Vec3d eye = player.getEyePos();
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) -(MathHelper.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
        player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
    }

    static void forget(ServerPlayerEntity player) {
        LOCKED_UNTIL.remove(player.getUuid());
        apply(player, false);
    }
}
