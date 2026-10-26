package com.zangetsu.logic;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.registry.ModAttachments;
import com.zangetsu.registry.ModComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Пробуждение: у игрока осталось одно сердце — в небе появляется фигура, замахивается и бросает
 * ему Зангецу. Меч, вращаясь, вонзается лезвием в землю перед игроком; игрок касается рукояти —
 * сердца восстанавливаются, добавляется ещё 5 сердец (10 здоровья), меч навсегда остаётся с ним.
 */
public final class Awakening {
    public static final Identifier HEALTH_BONUS = ZangetsuMod.id("bond_health");
    /** Сколько ещё игрок неуязвим после того, как меч вонзился (чтобы успеть его взять). */
    private static final int PROTECTION_AFTER_LANDING = 600;
    public static final int PICKUP_DURATION = 46;
    private static final int TOUCH_TICK = 22;

    private static final class Session {
        int tick;
        int landedAt = -1;
        boolean picking;
        UUID sword;
        RegistryKey<World> world;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Awakening() {
    }

    public static boolean isActive(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** Пока идёт пробуждение, смерть не может забрать игрока. */
    public static boolean protects(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        return session != null && (session.landedAt < 0 || session.picking || session.tick - session.landedAt < PROTECTION_AFTER_LANDING);
    }

    public static boolean shouldTrigger(ServerPlayerEntity player, ZangetsuData data) {
        return !data.bonded && !SESSIONS.containsKey(player.getUuid()) && player.isAlive() && !player.isSpectator()
                && player.getHealth() <= 2.0F && data.dream == null;
    }

    public static void start(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Session session = new Session();
        session.world = world.getRegistryKey();
        SESSIONS.put(player.getUuid(), session);

        Vec3d look = horizontalLook(player);
        Vec3d landing = findLanding(player, look);

        // Фигура — впереди и выше игрока; в пещере ближе и меньше, чтобы её было видно.
        double[][] candidates = {{18, 13}, {14, 10}, {10, 7}, {7, 4.5}, {5, 3}};
        Vec3d spot = null;
        float scale = 1.2F;
        for (double[] c : candidates) {
            float s = (float) MathHelper.clamp(c[0] / 4.5, 1.2, 4.0);
            Vec3d pos = player.getPos().add(look.multiply(c[0])).add(0.0, c[1], 0.0);
            HitResult hit = world.raycast(new RaycastContext(player.getEyePos(), pos.add(0.0, 1.6 * s, 0.0),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
            spot = pos;
            scale = s;
            if (hit.getType() == HitResult.Type.MISS) {
                break;
            }
        }
        Vec3d toPlayer = player.getPos().subtract(spot);
        float yaw = (float) Math.toDegrees(Math.atan2(toPlayer.z, toPlayer.x)) - 90.0F;
        ZangetsuSpiritEntity.summon(world, spot, yaw, scale, player.getUuid(), landing);

        // Смотрим чуть ниже ног фигуры — тогда она целиком над заголовком.
        Cinematic.face(player, spot.add(0.0, -1.5, 0.0));
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 40, 15));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("zangetsu.awaken.subtitle").formatted(Formatting.GRAY)));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("zangetsu.awaken.title").formatted(Formatting.DARK_RED)));
        Fx.sound(player, SoundEvents.AMBIENT_CAVE.value(), 1.0F, 0.6F);

        // Время будто замирает: мобы вокруг вязнут.
        for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, player.getBoundingBox().expand(24.0), Entity::isAlive)) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 4));
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 4));
        }
    }

    private static Vec3d horizontalLook(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVector();
        Vec3d flat = new Vec3d(look.x, 0.0, look.z);
        return flat.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : flat.normalize();
    }

    /** Куда вонзится меч: на землю в паре шагов перед игроком. */
    private static Vec3d findLanding(ServerPlayerEntity player, Vec3d look) {
        World world = player.getWorld();
        for (double d : new double[]{2.3, 1.9, 1.5, 1.1}) {
            Vec3d p = player.getPos().add(look.multiply(d));
            BlockPos base = BlockPos.ofFloored(p.x, player.getY() + 0.5, p.z);
            for (int dy = 0; dy >= -4; dy--) {
                BlockPos pos = base.up(dy);
                if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty() && world.isTopSolid(pos.down(), player)) {
                    return new Vec3d(p.x, pos.getY(), p.z);
                }
            }
        }
        return player.getPos().add(look.multiply(1.5));
    }

    public static void onSwordThrown(UUID owner, ThrownZangetsuEntity sword) {
        Session session = SESSIONS.get(owner);
        if (session != null) {
            session.sword = sword.getUuid();
        }
    }

    public static void onSwordLanded(UUID owner, ThrownZangetsuEntity sword) {
        Session session = SESSIONS.get(owner);
        if (session == null) {
            return;
        }
        session.landedAt = session.tick;
        ServerPlayerEntity player = sword.getServer().getPlayerManager().getPlayer(owner);
        if (player != null) {
            Sync.fx(player, ClientFxPayload.SHAKE, 12);
            player.sendMessage(Text.translatable("zangetsu.awaken.touch").formatted(Formatting.GOLD), true);
        }
    }

    public static void pickUp(ServerPlayerEntity player, ThrownZangetsuEntity sword) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.landedAt < 0 || session.picking) {
            return;
        }
        session.picking = true;
        Cinematic.face(player, sword.getPos().add(0.0, 1.3, 0.0));
        Cinematic.play(player, AnimationPayload.TOUCH_HANDLE, PICKUP_DURATION);
        Fx.sound(player, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0F, 0.6F);
        Fx.particles(player.getServerWorld(), Fx.EYE_GOLD, sword.getPos().add(0, 1.0, 0), 30, 0.3, 0.0);
        Scheduler.later(TOUCH_TICK, () -> {
            if (!player.isAlive() || player.isRemoved()) {
                cancel(player);
                return;
            }
            bond(player, sword);
        });
        Scheduler.later(PICKUP_DURATION, () -> SESSIONS.remove(player.getUuid()));
    }

    private static void bond(ServerPlayerEntity player, ThrownZangetsuEntity sword) {
        ServerWorld world = player.getServerWorld();
        Vec3d at = sword.getPos().add(0.0, 1.0, 0.0);
        sword.discard();

        ZangetsuData data = ModAttachments.data(player);
        data.bonded = true;
        data.lost = false;
        applyHealthBonus(player);
        player.clearStatusEffects();
        player.setHealth(player.getMaxHealth());
        player.extinguish();

        ItemStack stack = SwordStacks.create(player);
        stack.set(ModComponents.EYE, Eye.OPEN);
        SwordStacks.giveToHand(player, stack);

        Fx.particles(world, ParticleTypes.TOTEM_OF_UNDYING, player.getPos().add(0, 1, 0), 120, 0.6, 0.6);
        Fx.particles(world, ParticleTypes.END_ROD, at, 40, 0.2, 0.25);
        Fx.particles(world, ParticleTypes.FLASH, at, 1, 0.0, 0.0);
        Fx.ring(world, ParticleTypes.SOUL_FIRE_FLAME, player.getPos().add(0, 0.1, 0), 2.0, 40);
        Fx.sound(player, SoundEvents.ITEM_TOTEM_USE, 1.0F, 0.8F);
        Fx.sound(player, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0F, 0.7F);
        Sync.fx(player, ClientFxPayload.FLOATING_SWORD, 0);

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 25));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("zangetsu.bonded.subtitle").formatted(Formatting.GRAY)));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("zangetsu.bonded.title").formatted(Formatting.DARK_RED, Formatting.BOLD)));
        player.sendMessage(Text.translatable("zangetsu.bonded.guide").formatted(Formatting.GOLD), false);
        Sync.player(player);
    }

    /** +10 к здоровью — навсегда, пока меч связан с игроком. */
    public static void applyHealthBonus(ServerPlayerEntity player) {
        EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null && !health.hasModifier(HEALTH_BONUS)) {
            health.addPersistentModifier(new EntityAttributeModifier(HEALTH_BONUS, 10.0, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }

    public static void removeHealthBonus(ServerPlayerEntity player) {
        EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.removeModifier(HEALTH_BONUS);
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    static void tick(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            return;
        }
        session.tick++;
        if (!player.isAlive() || player.getServerWorld().getRegistryKey() != session.world
                || (session.landedAt < 0 && session.tick > 400)) {
            cancel(player);
        }
    }

    /** Пробуждение сорвалось (игрок умер, ушёл в другой мир, вышел) — убрать меч и фигуру. */
    public static void cancel(ServerPlayerEntity player) {
        Session session = SESSIONS.remove(player.getUuid());
        if (session != null && session.sword != null) {
            Entity sword = player.getServerWorld().getEntity(session.sword);
            if (sword != null) {
                sword.discard();
            }
        }
    }
}
