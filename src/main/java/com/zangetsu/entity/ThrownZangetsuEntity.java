package com.zangetsu.entity;

import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.Dream;
import com.zangetsu.logic.Fx;
import com.zangetsu.registry.ModEntities;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Зангецу вне рук: летит, вращаясь, и вонзается лезвием в землю, а потом ждёт, когда игрок
 * коснётся рукояти. Во сне таких мечей сотни — подойдёт любой.
 */
public class ThrownZangetsuEntity extends Entity {
    public static final int FLYING = 0;
    public static final int STUCK = 1;
    /** Меч вытаскивают из земли (во сне). */
    public static final int RISING = 2;
    public static final double GRAVITY = 0.06;

    private static final TrackedData<Integer> STATE = DataTracker.registerData(ThrownZangetsuEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> FLIGHT_TICKS = DataTracker.registerData(ThrownZangetsuEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> DREAM = DataTracker.registerData(ThrownZangetsuEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> TILT_X = DataTracker.registerData(ThrownZangetsuEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TILT_Z = DataTracker.registerData(ThrownZangetsuEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private UUID owner;
    private Vec3d target = Vec3d.ZERO;
    private int flown;
    /** С какого тика (по age) меч вонзён или поднимается — для анимаций на клиенте. */
    public int stateAge;
    private int lastState = -1;

    public ThrownZangetsuEntity(EntityType<? extends ThrownZangetsuEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    /** Бросить меч из точки {@code from} так, чтобы ровно через {@code ticks} тиков он вонзился в {@code to}. */
    public static ThrownZangetsuEntity launch(ServerWorld world, Vec3d from, Vec3d to, UUID owner, int ticks) {
        ThrownZangetsuEntity sword = new ThrownZangetsuEntity(ModEntities.THROWN_ZANGETSU, world);
        sword.setPosition(from);
        sword.owner = owner;
        sword.target = to;
        sword.dataTracker.set(FLIGHT_TICKS, ticks);
        Vec3d d = to.subtract(from);
        // pos(n) = from + n·v − g·n(n−1)/2 — подбираем v так, чтобы pos(ticks) = to.
        double vy = (d.y + GRAVITY * ticks * (ticks - 1) / 2.0) / ticks;
        sword.setVelocity(d.x / ticks, vy, d.z / ticks);
        sword.setYaw((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        world.spawnEntity(sword);
        return sword;
    }

    /** Меч, уже воткнутый в землю (сон с мечами). */
    public static ThrownZangetsuEntity plant(ServerWorld world, Vec3d at, UUID owner, float yaw, float tiltX, float tiltZ) {
        ThrownZangetsuEntity sword = new ThrownZangetsuEntity(ModEntities.THROWN_ZANGETSU, world);
        sword.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0.0F);
        sword.owner = owner;
        sword.target = at;
        sword.dataTracker.set(STATE, STUCK);
        sword.dataTracker.set(DREAM, true);
        sword.dataTracker.set(TILT_X, tiltX);
        sword.dataTracker.set(TILT_Z, tiltZ);
        world.spawnEntity(sword);
        return sword;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(STATE, FLYING);
        builder.add(FLIGHT_TICKS, 28);
        builder.add(DREAM, false);
        builder.add(TILT_X, 0.0F);
        builder.add(TILT_Z, 0.0F);
    }

    public int getState() {
        return this.dataTracker.get(STATE);
    }

    public void setState(int state) {
        this.dataTracker.set(STATE, state);
    }

    public int getFlightTicks() {
        return this.dataTracker.get(FLIGHT_TICKS);
    }

    public boolean isDream() {
        return this.dataTracker.get(DREAM);
    }

    public float getTiltX() {
        return this.dataTracker.get(TILT_X);
    }

    public float getTiltZ() {
        return this.dataTracker.get(TILT_Z);
    }

    public UUID getOwner() {
        return this.owner;
    }

    /** Доля пройденного полёта (0..1) — по ней меч на клиенте докручивается точно лезвием вниз. */
    public float flightProgress(float tickDelta) {
        return Math.min(1.0F, (this.age + tickDelta) / Math.max(1, this.getFlightTicks()));
    }

    @Override
    public void tick() {
        super.tick();
        int state = this.getState();
        if (state != this.lastState) {
            this.lastState = state;
            this.stateAge = this.age;
        }
        if (state == FLYING) {
            Vec3d v = this.getVelocity();
            this.setPosition(this.getPos().add(v));
            this.setVelocity(v.x, v.y - GRAVITY, v.z);
            this.flown++;
            if (this.getWorld().isClient) {
                this.getWorld().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
                this.getWorld().addParticle(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            } else if (this.flown >= this.getFlightTicks()) {
                this.land();
            }
        }
        if (this.getWorld().isClient) {
            return;
        }
        ServerWorld world = (ServerWorld) this.getWorld();
        if (this.age > 5 && !this.isDream() && (this.owner == null || !Awakening.isActive(this.owner))) {
            this.discard();
            return;
        }
        if (state == STUCK && this.age % 10 == 0) {
            Fx.particles(world, Fx.EYE_GOLD, this.getPos().add(0, 1.0, 0), 2, 0.15, 0.0);
        }
    }

    private void land() {
        ServerWorld world = (ServerWorld) this.getWorld();
        this.setPosition(this.target);
        this.setVelocity(Vec3d.ZERO);
        this.setState(STUCK);
        Vec3d at = this.target;
        BlockState ground = world.getBlockState(BlockPos.ofFloored(at.x, at.y - 0.5, at.z));
        Fx.sound(world, at, SoundEvents.BLOCK_ANVIL_LAND, 1.0F, 0.5F);
        Fx.sound(world, at, SoundEvents.ITEM_TRIDENT_THUNDER.value(), 1.0F, 1.2F);
        Fx.sound(world, at, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0F, 0.8F);
        if (!ground.isAir()) {
            Fx.particles(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), at, 60, 0.4, 0.15);
        }
        Fx.particles(world, ParticleTypes.EXPLOSION, at.add(0, 0.3, 0), 1, 0.0, 0.0);
        Fx.ring(world, ParticleTypes.CLOUD, at.add(0, 0.1, 0), 1.5, 20);
        Fx.ring(world, ParticleTypes.CLOUD, at.add(0, 0.1, 0), 2.6, 30);
        Fx.ring(world, ParticleTypes.SOUL_FIRE_FLAME, at.add(0, 0.2, 0), 3.4, 36);
        // Ударная волна отбрасывает мобов вокруг (игрока не трогает).
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(6.0),
                e -> !(e instanceof PlayerEntity) && e.isAlive())) {
            Vec3d push = entity.getPos().subtract(at).normalize();
            entity.takeKnockback(1.2, -push.x, -push.z);
        }
        Awakening.onSwordLanded(this.owner, this);
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (this.getState() != STUCK || hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (this.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (this.owner != null && !this.owner.equals(player.getUuid())) {
            return ActionResult.PASS;
        }
        if (this.isDream()) {
            Dream.pull((net.minecraft.server.network.ServerPlayerEntity) player, this);
        } else {
            Awakening.pickUp((net.minecraft.server.network.ServerPlayerEntity) player, this);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        this.setVelocity(packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ());
    }

    @Override
    public boolean canHit() {
        return this.getState() == STUCK;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 160.0 * 160.0;
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
