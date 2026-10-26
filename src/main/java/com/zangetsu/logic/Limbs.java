package com.zangetsu.logic;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.SeveredPartEntity;
import com.zangetsu.registry.ModAttachments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Отсечение конечностей. Зомби, пиглинам и скелетам меч сначала отрубает левую руку, потом правую;
 * эндермену — подрубает левое колено, потом правое. Когда рубить больше нечего, открывается казнь.
 */
public final class Limbs {
    public static final int LEFT_ARM = 1;
    public static final int RIGHT_ARM = 2;
    public static final int HEAD = 4;
    public static final int LEFT_KNEE = 8;
    public static final int RIGHT_KNEE = 16;
    /** Эндермен рассечён пополам: сам он не рисуется, вместо него летят две половины. */
    public static final int SPLIT = 32;

    private static final Identifier SLOW = ZangetsuMod.id("limb_slow");

    private Limbs() {
    }

    public static boolean isHumanoid(LivingEntity entity) {
        return entity instanceof ZombieEntity || entity instanceof AbstractPiglinEntity || entity instanceof AbstractSkeletonEntity;
    }

    public static boolean isEnderman(LivingEntity entity) {
        return entity instanceof EndermanEntity;
    }

    public static boolean has(int limbs, int flags) {
        return (limbs & flags) == flags;
    }

    public static boolean disarmed(int limbs) {
        return has(limbs, LEFT_ARM | RIGHT_ARM);
    }

    public static boolean hobbled(int limbs) {
        return has(limbs, LEFT_KNEE | RIGHT_KNEE);
    }

    /** Можно казнить: все конечности уже отсечены, а моб ещё жив. */
    public static boolean executable(LivingEntity entity) {
        int limbs = ModAttachments.limbs(entity);
        if (!entity.isAlive() || (limbs & (HEAD | SPLIT)) != 0) {
            return false;
        }
        return (isHumanoid(entity) && disarmed(limbs)) || (isEnderman(entity) && hobbled(limbs));
    }

    public static void set(LivingEntity entity, int limbs) {
        entity.setAttached(ModAttachments.LIMBS, limbs);
        Sync.limbs(entity);
    }

    /** Отсечь следующую конечность; false — рубить уже нечего (или это не тот моб). */
    public static boolean cutNext(LivingEntity target, ServerPlayerEntity player) {
        int limbs = ModAttachments.limbs(target);
        int part = 0;
        if (isHumanoid(target)) {
            part = (limbs & LEFT_ARM) == 0 ? LEFT_ARM : ((limbs & RIGHT_ARM) == 0 ? RIGHT_ARM : 0);
        } else if (isEnderman(target)) {
            part = (limbs & LEFT_KNEE) == 0 ? LEFT_KNEE : ((limbs & RIGHT_KNEE) == 0 ? RIGHT_KNEE : 0);
        }
        if (part == 0) {
            return false;
        }
        cut(target, player, part);
        return true;
    }

    public static void cut(LivingEntity target, ServerPlayerEntity player, int part) {
        ServerWorld world = (ServerWorld) target.getWorld();
        int limbs = ModAttachments.limbs(target) | part;
        set(target, limbs);

        // Отрубленная часть отлетает в сторону.
        int severed;
        Vec3d at;
        Vec3d side;
        float yawRad = (float) Math.toRadians(180.0F - target.getBodyYaw());
        Vec3d left = new Vec3d(-1, 0, 0).rotateY(yawRad);
        switch (part) {
            case LEFT_ARM -> {
                severed = SeveredPartEntity.LEFT_ARM;
                at = modelToWorld(target, 5.0, 6.0, 0.0);
                side = left;
            }
            case RIGHT_ARM -> {
                severed = SeveredPartEntity.RIGHT_ARM;
                at = modelToWorld(target, -5.0, 6.0, 0.0);
                side = left.negate();
            }
            case LEFT_KNEE -> {
                severed = SeveredPartEntity.LEFT_SHIN;
                at = modelToWorld(target, 2.0, 17.0, 0.0);
                side = left;
            }
            default -> {
                severed = SeveredPartEntity.RIGHT_SHIN;
                at = modelToWorld(target, -2.0, 17.0, 0.0);
                side = left.negate();
            }
        }
        Vec3d velocity = side.multiply(0.22).add(0.0, 0.28, 0.0).add(target.getRotationVector().multiply(-0.05));
        SeveredPartEntity.spawn(world, target, severed, at, velocity, target.getBodyYaw());

        if (target instanceof MobEntity mob) {
            // Отрубленная рука роняет то, что держала.
            if (part == LEFT_ARM) {
                drop(mob, EquipmentSlot.OFFHAND);
            } else if (part == RIGHT_ARM) {
                drop(mob, EquipmentSlot.MAINHAND);
            }
            if (disarmed(limbs)) {
                mob.setCanPickUpLoot(false);
            }
        }
        updateSlowness(target, limbs);

        Fx.particles(world, isEnderman(target) ? Fx.ENDER_BLOOD : Fx.BLOOD, at, 26, 0.18, 0.05);
        Fx.particles(world, ParticleTypes.SWEEP_ATTACK, at, 1, 0.0, 0.0);
        Fx.particles(world, ParticleTypes.CRIT, at, 10, 0.25, 0.3);
        Fx.sound(target, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.8F);
        Fx.sound(target, SoundEvents.ITEM_TRIDENT_HIT, 1.0F, 0.7F);
        target.damage(world.getDamageSources().playerAttack(player), 1.0F);

        Text message = Text.translatable("zangetsu.cut." + switch (part) {
            case LEFT_ARM -> "left_arm";
            case RIGHT_ARM -> "right_arm";
            case LEFT_KNEE -> "left_knee";
            default -> "right_knee";
        }).formatted(Formatting.RED);
        if (target.isAlive() && executable(target)) {
            message = Text.translatable("zangetsu.cut.execute_hint", Text.keybind("key.zangetsu.execute")).formatted(Formatting.DARK_RED, Formatting.BOLD);
        }
        player.sendMessage(message, true);
    }

    private static void drop(MobEntity mob, EquipmentSlot slot) {
        ItemStack stack = mob.getEquippedStack(slot);
        if (!stack.isEmpty()) {
            mob.dropStack(stack.copy(), 1.0F);
            mob.equipStack(slot, ItemStack.EMPTY);
        }
    }

    private static void updateSlowness(LivingEntity target, int limbs) {
        EntityAttributeInstance speed = target.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double slow = 0.0;
        if (isEnderman(target)) {
            slow = hobbled(limbs) ? -1.0 : ((limbs & (LEFT_KNEE | RIGHT_KNEE)) != 0 ? -0.6 : 0.0);
        } else if (disarmed(limbs)) {
            slow = -0.25;
        }
        speed.removeModifier(SLOW);
        if (slow != 0.0) {
            speed.addPersistentModifier(new EntityAttributeModifier(SLOW, slow, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /**
     * Точка модели (в пикселях модели, как в BipedEntityModel: y вниз, 24 — земля) в мире — так же,
     * как её ставит LivingEntityRenderer: поворот по телу, разворот осей и сдвиг на 1,501 блока.
     */
    public static Vec3d modelToWorld(LivingEntity entity, double mx, double my, double mz) {
        Vec3d local = new Vec3d(-mx / 16.0, 1.501 - my / 16.0, mz / 16.0).multiply(entity.getScale());
        return entity.getPos().add(local.rotateY((float) Math.toRadians(180.0F - entity.getBodyYaw())));
    }
}
