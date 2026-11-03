package com.zangetsu.entity;

import com.zangetsu.logic.Fx;
import com.zangetsu.logic.Limbs;
import com.zangetsu.registry.ModEntities;
import java.util.Locale;
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
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Отсечённый кусок моба: всё, что от настоящей модели остаётся по нужную сторону плоскостей разреза. Голова, половина туловища, рука,
 * рёбра скелета — одно и то же, только плоскости другие. Кусок либо летит кувырком, либо, если стоял на ногах, рушится вокруг ступней.
 */
public class SeveredPartEntity extends Entity {
    public static final int LIFETIME = 140;
    /** Кусок не рушится, а летит кувырком. */
    public static final int FREE = -1;
    /** Сколько тиков рушится кусок и насколько в итоге ложится (градусы). */
    private static final float TOPPLE_TICKS = 11.0F, TOPPLE_ANGLE = 86.0F;

    private static final TrackedData<String> SOURCE = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> SHAPE = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> LIMBS = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Vector3f> PIVOT = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    private static final TrackedData<Float> LIFT = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> MODEL_SCALE = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> FALL = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> SPIN_SPEED = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> TOPPLE = DataTracker.registerData(SeveredPartEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /**
     * Что отсечь. Плоскости — {nx, ny, nz, d} в пикселях модели: остаётся то, где n·p ≥ d. {@code px, py, pz} — вокруг какой точки
     * модели кусок вращается, {@code lift} — на сколько блоков она над опорой, когда кусок лежит. {@code fall} — куда падать (градусы
     * в плоскости тела: 90 — назад, 0 и 180 — вбок), {@code topple} — через сколько тиков рухнуть или {@link #FREE}.
     */
    public record Piece(float[][] planes, float px, float py, float pz, float lift, Vec3d velocity, float fall, int topple, float spin) {
    }

    public float spin;
    public float prevSpin;
    private float spinSpeed;
    private boolean spinStarted;
    private float[][] planes;

    public SeveredPartEntity(EntityType<? extends SeveredPartEntity> type, World world) {
        super(type, world);
    }

    /** Отделить кусок от моба {@code source}, у которого сейчас такие отсечения: модель рисуется в этой же позе. */
    public static void spawn(ServerWorld world, LivingEntity source, int limbs, Piece piece) {
        SeveredPartEntity entity = new SeveredPartEntity(ModEntities.SEVERED_PART, world);
        Vec3d at = Limbs.modelToWorld(source, piece.px(), piece.py(), piece.pz()).subtract(0.0, piece.lift(), 0.0);
        entity.refreshPositionAndAngles(at.x, at.y, at.z, source.getBodyYaw(), 0.0F);
        entity.setVelocity(piece.velocity());
        StringBuilder shape = new StringBuilder();
        for (float[] plane : piece.planes()) {
            shape.append(String.format(Locale.ROOT, "%.3f %.3f %.3f %.3f;", plane[0], plane[1], plane[2], plane[3]));
        }
        entity.dataTracker.set(SOURCE, Registries.ENTITY_TYPE.getId(source.getType()).getPath());
        entity.dataTracker.set(SHAPE, shape.toString());
        entity.dataTracker.set(LIMBS, limbs);
        entity.dataTracker.set(PIVOT, new Vector3f(piece.px(), piece.py(), piece.pz()));
        entity.dataTracker.set(LIFT, piece.lift());
        entity.dataTracker.set(MODEL_SCALE, Limbs.modelScale(source) * (source.isBaby() ? 0.6F : source.getScale()));
        entity.dataTracker.set(FALL, piece.fall());
        entity.dataTracker.set(SPIN_SPEED, piece.spin());
        entity.dataTracker.set(TOPPLE, piece.topple());
        world.spawnEntity(entity);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(SOURCE, "zombie");
        builder.add(SHAPE, "");
        builder.add(LIMBS, 0);
        builder.add(PIVOT, new Vector3f());
        builder.add(LIFT, 0.0F);
        builder.add(MODEL_SCALE, 1.0F);
        builder.add(FALL, 90.0F);
        builder.add(SPIN_SPEED, 0.0F);
        builder.add(TOPPLE, FREE);
    }

    /** Путь в реестре вида моба, от которого этот кусок: {@code zombie}, {@code wither_skeleton}. */
    public String getSource() {
        return this.dataTracker.get(SOURCE);
    }

    public int getLimbs() {
        return this.dataTracker.get(LIMBS);
    }

    public Vector3f getPivot() {
        return this.dataTracker.get(PIVOT);
    }

    public float getLift() {
        return this.dataTracker.get(LIFT);
    }

    public float getModelScale() {
        return this.dataTracker.get(MODEL_SCALE);
    }

    public float getFall() {
        return this.dataTracker.get(FALL);
    }

    /** Плоскости разреза, как в {@link Piece}. */
    public float[][] planes() {
        if (this.planes == null) {
            String[] parts = this.dataTracker.get(SHAPE).split(";");
            float[][] parsed = new float[parts.length][4];
            for (int i = 0; i < parts.length; i++) {
                String[] numbers = parts[i].trim().split(" ");
                for (int c = 0; c < 4 && c < numbers.length; c++) {
                    parsed[i][c] = numbers[c].isEmpty() ? 0.0F : Float.parseFloat(numbers[c]);
                }
            }
            this.planes = parsed;
        }
        return this.planes;
    }

    /** Насколько кусок сейчас повёрнут вокруг оси падения (градусы). */
    public float tilt(float tickDelta) {
        int topple = this.dataTracker.get(TOPPLE);
        if (topple == FREE) {
            return MathHelper.lerp(tickDelta, this.prevSpin, this.spin);
        }
        float t = (this.age + tickDelta - topple) / TOPPLE_TICKS;
        if (t <= 0.0F) {
            return 0.0F;
        }
        // Падает с ускорением, ударяется и чуть подпрыгивает.
        if (t < 1.0F) {
            return TOPPLE_ANGLE * t * t;
        }
        float after = (t - 1.0F) * 3.2F;
        return TOPPLE_ANGLE - 7.0F * Math.abs(MathHelper.sin(after)) * (float) Math.exp(-after * 1.1F);
    }

    @Override
    public void tick() {
        super.tick();
        this.prevSpin = this.spin;
        if (!this.spinStarted) {
            this.spinStarted = true;
            this.spinSpeed = this.dataTracker.get(SPIN_SPEED);
        }
        Vec3d v = this.getVelocity();
        if (!this.hasNoGravity()) {
            v = v.add(0.0, -0.04, 0.0);
        }
        // Скорость надо сохранить до хода: иначе тяжесть не копится и кусок летит вверх, пока не остановится.
        this.setVelocity(v);
        this.move(MovementType.SELF, v);
        v = this.getVelocity().multiply(0.98);
        if (this.isOnGround()) {
            v = v.multiply(0.6, -0.3, 0.6);
            this.spinSpeed *= 0.6F;
            // Докатившись, ложится плашмя.
            if (Math.abs(this.spinSpeed) < 2.5F) {
                this.spinSpeed = 0.0F;
                float rest = Math.round((this.spin - 90.0F) / 180.0F) * 180.0F + 90.0F;
                this.spin += (rest - this.spin) * 0.3F;
            }
        }
        this.setVelocity(v);
        this.spin += this.spinSpeed;
        Fx.Gore gore = Fx.Gore.of(this.getSource());
        if (this.getWorld().isClient) {
            if (this.age < 30 && this.random.nextInt(3) == 0) {
                this.getWorld().addParticle(gore.dust, this.getX(), this.getY() + this.getLift() + 0.1, this.getZ(), 0.0, 0.0, 0.0);
            }
            return;
        }
        int topple = this.dataTracker.get(TOPPLE);
        if (topple != FREE && this.age == topple + (int) TOPPLE_TICKS) {
            Fx.sound(this, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.5F, 0.55F);
        }
        if (this.age >= LIFETIME) {
            ((ServerWorld) this.getWorld()).spawnParticles(gore == Fx.Gore.ENDER ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.POOF,
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
