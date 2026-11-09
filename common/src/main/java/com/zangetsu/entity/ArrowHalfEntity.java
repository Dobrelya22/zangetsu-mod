package com.zangetsu.entity;

import com.zangetsu.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Половинка разрубленной стрелы: наконечник или хвост с оперением. Падает и исчезает. */
public class ArrowHalfEntity extends Entity {
    public static final int TIP = 0;
    public static final int TAIL = 1;
    public static final int LIFETIME = 60;

    private static final TrackedData<Integer> HALF = DataTracker.registerData(ArrowHalfEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> SPECTRAL = DataTracker.registerData(ArrowHalfEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public float roll;
    public float prevRoll;
    private float rollSpeed;

    public ArrowHalfEntity(EntityType<? extends ArrowHalfEntity> type, World world) {
        super(type, world);
        this.rollSpeed = (this.random.nextBoolean() ? 1 : -1) * (25.0F + this.random.nextFloat() * 25.0F);
    }

    /** Две половинки на месте стрелы: разлетаются в стороны поперёк её полёта. */
    public static void spawnPair(ServerWorld world, PersistentProjectileEntity arrow, boolean spectral) {
        Vec3d v = arrow.getVelocity();
        Vec3d dir = v.lengthSquared() > 1.0E-4 ? v.normalize() : new Vec3d(0, 0, 1);
        Vec3d side = new Vec3d(-dir.z, 0.0, dir.x);
        if (side.lengthSquared() < 1.0E-4) {
            side = new Vec3d(1, 0, 0);
        }
        side = side.normalize();
        for (int half = TIP; half <= TAIL; half++) {
            ArrowHalfEntity entity = new ArrowHalfEntity(ModEntities.ARROW_HALF, world);
            Vec3d offset = dir.multiply(half == TIP ? 0.12 : -0.12);
            entity.refreshPositionAndAngles(arrow.getX() + offset.x, arrow.getY() + offset.y, arrow.getZ() + offset.z,
                    arrow.getYaw(), arrow.getPitch());
            double s = half == TIP ? 1.0 : -1.0;
            entity.setVelocity(side.multiply(0.14 * s).add(dir.multiply(-0.08)).add(0.0, 0.18, 0.0));
            entity.dataTracker.set(HALF, half);
            entity.dataTracker.set(SPECTRAL, spectral);
            world.spawnEntity(entity);
        }
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(HALF, TIP);
        builder.add(SPECTRAL, false);
    }

    public int getHalf() {
        return this.dataTracker.get(HALF);
    }

    public boolean isSpectral() {
        return this.dataTracker.get(SPECTRAL);
    }

    @Override
    public void tick() {
        super.tick();
        this.prevRoll = this.roll;
        Vec3d v = this.getVelocity().add(0.0, -0.05, 0.0);
        this.move(MovementType.SELF, v);
        v = this.getVelocity().multiply(0.98);
        if (this.isOnGround()) {
            v = v.multiply(0.5, 0.0, 0.5);
            this.rollSpeed *= 0.5F;
        } else {
            this.setPitch(this.getPitch() + this.rollSpeed * 0.3F);
        }
        this.setVelocity(v);
        this.roll += this.rollSpeed;
        if (!this.getWorld().isClient && this.age >= LIFETIME) {
            this.discard();
        }
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        this.setVelocity(packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ());
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
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
