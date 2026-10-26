package com.zangetsu.entity;

import com.zangetsu.logic.Fx;
import com.zangetsu.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Отрубленная часть моба: рука, голова, голень эндермена или его половина. Рисуется кусочком
 * настоящей модели этого моба, кувыркается, падает и через несколько секунд рассыпается.
 */
public class SeveredPartEntity extends Entity {
    public static final int LEFT_ARM = 0;
    public static final int RIGHT_ARM = 1;
    public static final int HEAD = 2;
    public static final int LEFT_SHIN = 3;
    public static final int RIGHT_SHIN = 4;
    public static final int ENDER_TOP = 5;
    public static final int ENDER_BOTTOM = 6;

    public static final int LIFETIME = 120;

    private static final TrackedData<String> SOURCE = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> PART = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> BABY = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** Кувырок: ось и скорость задаются случайно на клиенте. */
    public float spin;
    public float prevSpin;
    public float spinSpeed;

    public SeveredPartEntity(EntityType<? extends SeveredPartEntity> type, World world) {
        super(type, world);
        this.spinSpeed = 18.0F + this.random.nextFloat() * 22.0F;
    }

    public static void spawn(ServerWorld world, LivingEntity source, int part, Vec3d at, Vec3d velocity, float yaw) {
        SeveredPartEntity entity = new SeveredPartEntity(ModEntities.SEVERED_PART, world);
        entity.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
        entity.setVelocity(velocity);
        entity.dataTracker.set(SOURCE, Registries.ENTITY_TYPE.getId(source.getType()).toString());
        entity.dataTracker.set(PART, part);
        entity.dataTracker.set(BABY, source.isBaby());
        world.spawnEntity(entity);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(SOURCE, "minecraft:zombie");
        builder.add(PART, LEFT_ARM);
        builder.add(BABY, false);
    }

    public String getSource() {
        return this.dataTracker.get(SOURCE);
    }

    public int getPart() {
        return this.dataTracker.get(PART);
    }

    public boolean isBabySource() {
        return this.dataTracker.get(BABY);
    }

    @Override
    public void tick() {
        super.tick();
        this.prevSpin = this.spin;
        Vec3d v = this.getVelocity();
        if (!this.hasNoGravity()) {
            v = v.add(0.0, -0.04, 0.0);
        }
        this.move(MovementType.SELF, v);
        v = this.getVelocity().multiply(0.98);
        if (this.isOnGround()) {
            v = v.multiply(0.6, -0.3, 0.6);
            this.spinSpeed *= 0.6F;
        }
        this.setVelocity(v);
        this.spin += this.spinSpeed;
        int part = this.getPart();
        boolean ender = part >= LEFT_SHIN;
        if (this.getWorld().isClient) {
            if (this.age < 30 && this.random.nextInt(3) == 0) {
                this.getWorld().addParticle(ender ? ParticleTypes.PORTAL : Fx.BLOOD,
                        this.getX(), this.getY() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
            }
            return;
        }
        if (this.age >= LIFETIME) {
            ((ServerWorld) this.getWorld()).spawnParticles(ender ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.POOF,
                    this.getX(), this.getY() + 0.2, this.getZ(), 8, 0.15, 0.15, 0.15, 0.02);
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
