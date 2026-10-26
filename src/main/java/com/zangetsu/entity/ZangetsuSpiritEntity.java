package com.zangetsu.entity;

import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.Fx;
import com.zangetsu.registry.ModEntities;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Таинственная фигура в небе: рогатая, крылатая, с хвостом. Появляется, замахивается и бросает
 * Зангецу игроку, у которого осталось одно сердце, а потом растворяется в дыму.
 */
public class ZangetsuSpiritEntity extends Entity {
    /** На каком тике меч срывается с руки. */
    public static final int RELEASE_TICK = 64;
    public static final int FADE_IN = 30;
    public static final int FADE_OUT_START = 120;
    public static final int LIFETIME = 150;
    /** Сколько тиков меч летит до земли. */
    public static final int FLIGHT_TICKS = 28;

    private static final TrackedData<Float> SCALE = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private UUID owner;
    private Vec3d landing = Vec3d.ZERO;

    public ZangetsuSpiritEntity(EntityType<? extends ZangetsuSpiritEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
    }

    public static ZangetsuSpiritEntity summon(ServerWorld world, Vec3d at, float yaw, float scale, UUID owner, Vec3d landing) {
        ZangetsuSpiritEntity spirit = new ZangetsuSpiritEntity(ModEntities.SPIRIT, world);
        spirit.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
        spirit.dataTracker.set(SCALE, scale);
        spirit.owner = owner;
        spirit.landing = landing;
        world.spawnEntity(spirit);
        return spirit;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(SCALE, 4.0F);
    }

    public float getSpiritScale() {
        return this.dataTracker.get(SCALE);
    }

    /** Насколько фигура проявилась: 0 — невидима, 1 — целиком. */
    public float getOpacity(float tickDelta) {
        float t = this.age + tickDelta;
        if (t < FADE_IN) {
            return t / FADE_IN;
        }
        if (t > FADE_OUT_START) {
            return MathHelper.clamp(1.0F - (t - FADE_OUT_START) / (LIFETIME - FADE_OUT_START), 0.0F, 1.0F);
        }
        return 1.0F;
    }

    /** Мировая точка правой руки в момент броска — оттуда вылетает меч. */
    public Vec3d handPosition() {
        float s = this.getSpiritScale();
        float yawRad = (float) Math.toRadians(-this.getYaw());
        Vec3d right = new Vec3d(-1, 0, 0).rotateY(yawRad);
        Vec3d forward = new Vec3d(0, 0, 1).rotateY(yawRad);
        // Рука поднята над головой: примерно 2,4 высоты фигуры вверх, чуть вправо и назад.
        return this.getPos().add(0, 2.35 * s, 0).add(right.multiply(0.5 * s)).add(forward.multiply(-0.2 * s));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            if (this.age < FADE_OUT_START && this.random.nextInt(2) == 0) {
                float s = this.getSpiritScale();
                this.getWorld().addParticle(ParticleTypes.LARGE_SMOKE,
                        this.getX() + (this.random.nextDouble() - 0.5) * s,
                        this.getY() + this.random.nextDouble() * 2.0 * s,
                        this.getZ() + (this.random.nextDouble() - 0.5) * s, 0.0, 0.02, 0.0);
            }
            return;
        }
        ServerWorld world = (ServerWorld) this.getWorld();
        // До броска фигура живёт, пока идёт пробуждение; после броска спокойно растворяется сама.
        if (this.age > 5 && this.age < RELEASE_TICK && (this.owner == null || !Awakening.isActive(this.owner))) {
            this.discard();
            return;
        }
        float s = this.getSpiritScale();
        Vec3d chest = this.getPos().add(0, 1.4 * s, 0);
        if (this.age == 1) {
            world.playSound(null, chest.x, chest.y, chest.z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.4F, 0.55F);
            world.playSound(null, chest.x, chest.y, chest.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 3.0F, 0.6F);
            Fx.farParticles(world, ParticleTypes.LARGE_SMOKE, chest, 120, 0.6 * s, 0.05);
        }
        if (this.age % 3 == 0 && this.age < FADE_OUT_START) {
            Fx.farParticles(world, Fx.REIATSU_BLACK, chest, 10, 0.5 * s, 0.0);
        }
        if (this.age == RELEASE_TICK - 22) {
            world.playSound(null, chest.x, chest.y, chest.z, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.2F, 0.7F);
        }
        if (this.age == RELEASE_TICK) {
            Vec3d hand = this.handPosition();
            world.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 3.0F, 0.4F);
            ThrownZangetsuEntity sword = ThrownZangetsuEntity.launch(world, hand, this.landing, this.owner, FLIGHT_TICKS);
            Awakening.onSwordThrown(this.owner, sword);
        }
        if (this.age == FADE_OUT_START) {
            Fx.farParticles(world, ParticleTypes.LARGE_SMOKE, chest, 150, 0.7 * s, 0.08);
        }
        if (this.age >= LIFETIME) {
            this.discard();
        }
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        this.setBodyYaw(packet.getYaw());
    }

    @Override
    public boolean shouldRender(double distance) {
        return true;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
