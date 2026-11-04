package com.zangetsu.entity;

import com.zangetsu.logic.Awakening;
import com.zangetsu.registry.ModEntities;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Яхве: широкий объёмный лист в небе, всегда лицом к смотрящему. Сам он ничего не решает: что ему делать, говорит сцена, а сервер сообщает только фазу.
 * Глаза живут на клиенте: у каждого свой взгляд и свои моргания. Масштаб сущности — блоков на единицу головы.
 */
public class ZangetsuSpiritEntity extends Entity {
    /** Что делают глаза и сама фигура. */
    public static final int ROAM = 0, WATCH = 1, STARE = 2, WRATH = 3, CLOSING = 4, DISSOLVING = 5;
    /** За сколько тиков он расползается по небу. */
    public static final int EMERGE = 96;
    /** Когда открывается первый глаз и с каким шагом остальные. */
    public static final int EYES_AT = 66, EYE_OPEN_STEP = 3;
    /** Сколько у него глаз и с каким шагом они закрываются на прощание. */
    public static final int EYE_COUNT = 18, EYE_CLOSE_STEP = 5;
    /** За сколько тиков он растворяется. */
    public static final int DISSOLVE = 50;
    /** Без сна (показательный бросок): когда бросить меч и сколько он летит, когда раствориться. */
    public static final int RELEASE_TICK = 64, FLIGHT_TICKS = 28, ALONE_FOR = 132;

    private static final TrackedData<Float> SCALE = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> PHASE = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Сколько тиков меч выходит из него; 0 — меча в нём нет. */
    private static final TrackedData<Integer> HOLD = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Растёт при каждом броске: плащ разом вздрагивает. */
    private static final TrackedData<Integer> FLARES = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Реплика: номер в старших разрядах, длина в младших, чтобы одинаковая длина тоже срабатывала. */
    private static final TrackedData<Integer> TALK = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Сцена, где он ломает меч: откуда вести руку, куда смотрит игрок, с какого тика проявляться и как быстро. */
    private static final TrackedData<Boolean> BREAK_SCENE = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Vector3f> ANCHOR = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.VECTOR3F);
    private static final TrackedData<Float> VICTIM_YAW = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> EMERGE_FROM = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> EMERGE_TICKS = DataTracker.registerData(ZangetsuSpiritEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private UUID owner;
    private BooleanSupplier keepWhile;
    private Vec3d landing = Vec3d.ZERO;
    private int throwAt = -1;
    private int flight = FLIGHT_TICKS;
    private int lastPhase = ROAM;
    private int talkSerial;
    /** Когда сменилась фаза и какая была раньше: глаза переходят плавно. */
    public int phaseAt;
    public int previousPhase = ROAM;
    public int holdAt;
    public int flareAt = -1000;
    public int talkAt = -1000;
    /** Клиент: куда сейчас смотрит лист. Догоняет камеру плавно, кадр за кадром. */
    public float faceYaw, facePitch;
    public long faceNanos;

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

    /** Фигура живёт ровно столько, сколько идёт её сцена. */
    public void keepWhile(BooleanSupplier condition) {
        this.keepWhile = condition;
    }

    /** Сцена, где он ломает меч: {@code anchor} — где висит меч относительно фигуры, {@code victimYaw} — куда смотрит игрок. */
    public void beginBreak(Vector3f anchor, float victimYaw, int from, int ticks) {
        this.dataTracker.set(ANCHOR, anchor);
        this.dataTracker.set(VICTIM_YAW, victimYaw);
        this.dataTracker.set(EMERGE_FROM, from);
        this.dataTracker.set(EMERGE_TICKS, ticks);
        this.dataTracker.set(BREAK_SCENE, true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(SCALE, 1.0F);
        builder.add(PHASE, ROAM);
        builder.add(HOLD, 0);
        builder.add(FLARES, 0);
        builder.add(TALK, 0);
        builder.add(BREAK_SCENE, false);
        builder.add(ANCHOR, new Vector3f());
        builder.add(VICTIM_YAW, 0.0F);
        builder.add(EMERGE_FROM, 0);
        builder.add(EMERGE_TICKS, EMERGE);
    }

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
        } else if (TALK.equals(data)) {
            this.talkAt = this.age;
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

    public boolean isBreakScene() {
        return this.dataTracker.get(BREAK_SCENE);
    }

    public Vector3f breakAnchor() {
        return this.dataTracker.get(ANCHOR);
    }

    public float victimYaw() {
        return this.dataTracker.get(VICTIM_YAW);
    }

    /** Он заговорил: губы и челюсть двигаются, пока идёт реплика. */
    public void speak(int ticks) {
        this.talkSerial++;
        this.dataTracker.set(TALK, (this.talkSerial << 12) | (ticks & 0xFFF));
    }

    /** Насколько сейчас открыт рот. */
    public float talk(float tickDelta) {
        int ticks = this.dataTracker.get(TALK) & 0xFFF;
        float t = this.age + tickDelta - this.talkAt;
        if (ticks == 0 || t < 0.0F || t > ticks * 0.66F) {
            return 0.0F;
        }
        float envelope = Math.min(1.0F, Math.min(t / 3.0F, (ticks * 0.66F - t) / 4.0F));
        return envelope * Math.abs(MathHelper.sin(t * 0.62F)) * (0.6F + 0.4F * MathHelper.sin(t * 0.23F));
    }

    /** Меч выходит из него {@code hold} тиков, затем летит {@code flight} тиков и вонзается в {@code landing}. */
    public void holdSword(Vec3d landing, int hold, int flight) {
        this.landing = landing;
        this.flight = flight;
        this.throwAt = this.age + hold;
        this.dataTracker.set(HOLD, hold);
    }

    public int getHold() {
        return this.dataTracker.get(HOLD);
    }

    /** Когда начинают открываться глаза. */
    public float eyesAt() {
        int from = this.dataTracker.get(EMERGE_FROM), ticks = this.dataTracker.get(EMERGE_TICKS);
        return from + ticks * 0.68F;
    }

    /** Насколько он есть: 0 — ещё нет или уже нет, 1 — целиком. */
    public float presence(float tickDelta) {
        float t = this.age + tickDelta - this.dataTracker.get(EMERGE_FROM);
        float grown = MathHelper.clamp(t / this.dataTracker.get(EMERGE_TICKS), 0.0F, 1.0F);
        if (this.getPhase() == DISSOLVING) {
            return grown * MathHelper.clamp(1.0F - (this.age + tickDelta - this.phaseAt) / DISSOLVE, 0.0F, 1.0F);
        }
        return grown;
    }

    /** Откуда вылетает меч: с груди, там, где он показался на плаще. Лист смотрит на того, кому брошен меч. */
    private Vec3d mouth() {
        float s = this.getSpiritScale();
        Vec3d toward = this.landing.add(0.0, 1.6, 0.0).subtract(this.getPos()).normalize();
        Vec3d up = new Vec3d(0.0, 1.0, 0.0).subtract(toward.multiply(toward.y)).normalize();
        return this.getPos().add(up.multiply(-1.25 * s)).add(toward.multiply(1.35 * s));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            return;
        }
        ServerWorld world = (ServerWorld) this.getWorld();
        if (this.age > 5 && this.keepWhile != null && !this.keepWhile.getAsBoolean()) {
            this.discard();
            return;
        }
        if (this.age == 1 && !this.isBreakScene()) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.6F, 0.5F);
            world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 3.0F, 0.55F);
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
        if (this.keepWhile == null && this.age == ALONE_FOR) {
            this.setPhase(DISSOLVING);
        }
        if (this.getPhase() == DISSOLVING && this.age - this.phaseAt >= DISSOLVE) {
            this.discard();
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
