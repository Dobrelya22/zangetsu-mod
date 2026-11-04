package com.zangetsu.entity;

import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.Calling;
import com.zangetsu.logic.Fx;
import com.zangetsu.registry.ModEntities;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Тёмная фигура в небе первого сна: чернильная косматая масса со множеством глаз. Сама ничего не решает: что ей делать, говорит {@link Calling}, а сервер сообщает только фазу.
 * Глаза живут на клиенте: у каждого свой взгляд и свои моргания.
 */
public class ZangetsuSpiritEntity extends Entity {
    /** Что делают глаза и сама фигура. */
    public static final int ROAM = 0, WATCH = 1, STARE = 2, WRATH = 3, CLOSING = 4, DISSOLVING = 5;
    /** За сколько тиков масса выплёскивается из ничего. */
    public static final int EMERGE = 84;
    /** Когда открывается первый глаз и с каким шагом остальные. */
    public static final int EYES_AT = 58, EYE_OPEN_STEP = 5;
    /** Сколько у неё глаз и с каким шагом они закрываются на прощание. */
    public static final int EYE_COUNT = 11, EYE_CLOSE_STEP = 8;
    /** За сколько тиков фигура растворяется. */
    public static final int DISSOLVE = 50;
    /** Без сна (показательный бросок): когда бросить меч и сколько он летит, когда раствориться. */
    public static final int RELEASE_TICK = 64, FLIGHT_TICKS = 28, ALONE_FOR = 132;

    private static final TrackedData<Float> SCALE = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> PHASE = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Сколько тиков меч выходит из фигуры; 0 — меча в ней нет. */
    private static final TrackedData<Integer> HOLD = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Растёт при каждом броске: все щупальца разом вздрагивают. */
    private static final TrackedData<Integer> FLARES = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private UUID owner;
    private boolean bound;
    private Vec3d landing = Vec3d.ZERO;
    private int throwAt = -1;
    private int flight = FLIGHT_TICKS;
    /** Когда сменилась фаза и какая была раньше: глаза переходят плавно. */
    public int phaseAt;
    public int previousPhase = ROAM;
    public int holdAt;
    public int flareAt = -1000;

    public ZangetsuSpiritEntity(EntityType<? extends ZangetsuSpiritEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.ignoreCameraFrustum = true;
    }

    public static ZangetsuSpiritEntity summon(ServerWorld world, Vec3d at, float yaw, float scale, UUID owner) {
        ZangetsuSpiritEntity spirit = new ZangetsuSpiritEntity(ModEntities.SPIRIT, world);
        spirit.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
        spirit.dataTracker.set(SCALE, scale);
        spirit.setPosition(at);
        spirit.owner = owner;
        world.spawnEntity(spirit);
        return spirit;
    }

    /** Фигура живёт ровно столько, сколько идёт сон этого игрока. */
    public void bindToCalling() {
        this.bound = true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(SCALE, 1.0F);
        builder.add(PHASE, ROAM);
        builder.add(HOLD, 0);
        builder.add(FLARES, 0);
    }

    private int lastPhase = ROAM;

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (PHASE.equals(data)) {
            this.previousPhase = this.lastPhase;
            this.lastPhase = this.getPhase();
            this.phaseAt = this.age;
        } else if (HOLD.equals(data)) {
            this.holdAt = this.age;
        } else if (FLARES.equals(data)) {
            this.flareAt = this.age;
        }
    }

    public float getSpiritScale() {
        return this.dataTracker.get(SCALE);
    }

    public int getPhase() {
        return this.dataTracker.get(PHASE);
    }

    public void setPhase(int phase) {
        this.dataTracker.set(PHASE, phase);
    }

    /** Меч выходит из фигуры {@code hold} тиков, затем летит {@code flight} тиков и вонзается в {@code landing}. */
    public void holdSword(Vec3d landing, int hold, int flight) {
        this.landing = landing;
        this.flight = flight;
        this.throwAt = this.age + hold;
        this.dataTracker.set(HOLD, hold);
    }

    public int getHold() {
        return this.dataTracker.get(HOLD);
    }

    /** Насколько фигура есть: 0 — ещё нет или уже нет, 1 — целиком. */
    public float presence(float tickDelta) {
        float t = this.age + tickDelta;
        float grown = MathHelper.clamp(t / EMERGE, 0.0F, 1.0F);
        if (this.getPhase() == DISSOLVING) {
            return grown * MathHelper.clamp(1.0F - (t - this.phaseAt) / DISSOLVE, 0.0F, 1.0F);
        }
        return grown;
    }

    /** Откуда вылетает меч: перед телом фигуры, со стороны игрока. */
    private Vec3d mouth() {
        float s = this.getSpiritScale();
        Vec3d toLanding = this.landing.subtract(this.getPos());
        Vec3d flat = new Vec3d(toLanding.x, 0.0, toLanding.z);
        flat = flat.lengthSquared() < 1.0E-4 ? new Vec3d(0.0, 0.0, 1.0) : flat.normalize();
        // Масса наклонена вперёд, так что меч выходит вперёд и вниз.
        return this.getPos().add(flat.multiply(4.8 * s)).add(0.0, -7.6 * s, 0.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            this.turnToPlayer();
            return;
        }
        ServerWorld world = (ServerWorld) this.getWorld();
        if (this.age > 5 && this.bound && (this.owner == null || !Calling.isActive(this.owner))) {
            this.discard();
            return;
        }
        float s = this.getSpiritScale();
        if (this.age == 1) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.6F, 0.5F);
            world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 3.0F, 0.55F);
            Fx.farParticles(world, ParticleTypes.LARGE_SMOKE, this.getPos(), 80, 1.2 * s, 0.08);
        }
        if (this.throwAt >= 0 && this.age >= this.throwAt) {
            this.throwAt = -1;
            this.dataTracker.set(HOLD, 0);
            this.dataTracker.set(FLARES, this.dataTracker.get(FLARES) + 1);
            Vec3d from = this.mouth();
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 3.0F, 0.4F);
            world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 2.5F, 0.5F);
            ThrownZangetsuEntity sword = ThrownZangetsuEntity.launch(world, from, this.landing, this.owner, this.flight);
            Awakening.onSwordThrown(this.owner, sword);
        }
        if (!this.bound && this.age == ALONE_FOR) {
            this.setPhase(DISSOLVING);
        }
        if (this.getPhase() == DISSOLVING && this.age - this.phaseAt >= DISSOLVE) {
            this.discard();
        }
    }

    /** Всей массой медленно поворачивается вслед за игроком: глаза должны его видеть. */
    private void turnToPlayer() {
        PlayerEntity player = this.getWorld().getClosestPlayer(this, 512.0);
        if (player != null) {
            float target = (float) Math.toDegrees(Math.atan2(player.getZ() - this.getZ(), player.getX() - this.getX())) - 90.0F;
            this.setYaw(this.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(target - this.getYaw()) * 0.04F, -1.2F, 1.2F));
        }
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
