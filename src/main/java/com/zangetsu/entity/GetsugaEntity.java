package com.zangetsu.entity;

import com.zangetsu.logic.Abilities;
import com.zangetsu.logic.Fx;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.registry.ModEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Гецуга Теншо — огромный серп энергии: чёрное ядро, алое свечение и раскалённая кромка. Срывается
 * с клинка в форме Пустого, растёт, летит прямо, проходит сквозь злых мобов и рубит каждого один раз.
 * Форма серпа задана здесь ({@link #arc}), чтобы отрисовка и частицы совпадали.
 */
public class GetsugaEntity extends Entity {
    public static final int LIFETIME = 26;
    public static final float DAMAGE = 18.0F;
    /** Наклон серпа — как у косого удара клинком. */
    public static final float ROLL = -28.0F;
    private static final double SPEED = 1.35;
    private static final double SPAN = Math.toRadians(74.0);

    private UUID owner;
    private final Set<UUID> hit = new HashSet<>();

    public GetsugaEntity(EntityType<? extends GetsugaEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public static void fire(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        GetsugaEntity getsuga = new GetsugaEntity(ModEntities.GETSUGA, world);
        Vec3d look = player.getRotationVector();
        Vec3d start = player.getEyePos().add(look.multiply(1.4)).add(0, -0.35, 0);
        getsuga.refreshPositionAndAngles(start.x, start.y, start.z, player.getYaw(), player.getPitch());
        getsuga.setVelocity(look.multiply(SPEED));
        getsuga.owner = player.getUuid();
        world.spawnEntity(getsuga);
        Fx.sound(player, SoundEvents.ENTITY_WITHER_SHOOT, 0.9F, 0.55F);
        Fx.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2F, 0.45F);
        Fx.sound(player, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.5F, 1.6F);
        Fx.particles(world, ParticleTypes.SWEEP_ATTACK, start, 3, 0.6, 0.0);
        com.zangetsu.logic.Sync.fx(player, ClientFxPayload.SHAKE, 6);
    }

    // ---------------------------------------------------------------- форма серпа

    /** Размер серпа: быстро вырастает из клинка в первые тики. */
    public static float size(float age) {
        float t = MathHelper.clamp(age / 6.0F, 0.0F, 1.0F);
        return 0.35F + 0.65F * (1.0F - (1.0F - t) * (1.0F - t));
    }

    /** Насколько серп виден: в последние тики гаснет. */
    public static float alpha(float age) {
        return MathHelper.clamp((LIFETIME - age) / 6.0F, 0.0F, 1.0F);
    }

    /**
     * Точка середины серпа в его собственных координатах (x — вбок, y — вверх, z — вперёд по полёту);
     * t от −1 до 1 — от кончика до кончика. Серп выгнут дугой и середина его чуть впереди кончиков.
     */
    public static Vector3f arc(float t, float size) {
        double phi = t * SPAN;
        double r = 2.3 * size;
        return new Vector3f((float) (r * Math.sin(phi)), (float) (r * (Math.cos(phi) - 0.6)),
                (float) (0.9 * size * (Math.cos(phi) - Math.cos(SPAN))));
    }

    /** Направление поперёк серпа (от вогнутой стороны к выпуклой) в той же точке. */
    public static Vector3f across(float t) {
        double phi = t * SPAN;
        return new Vector3f((float) Math.sin(phi), (float) Math.cos(phi), 0.0F);
    }

    /** Половина ширины серпа: толще в середине, острые кончики. */
    public static float halfWidth(float t, float size) {
        return (float) (0.42 * size * Math.pow(1.0 - Math.abs(t), 0.7));
    }

    /** Поворот из координат серпа в мировые — тот же, что у отрисовщика. */
    public static Quaternionf orientation(float yaw, float pitch) {
        return new Quaternionf(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw))
                .mul(RotationAxis.POSITIVE_X.rotationDegrees(pitch))
                .mul(RotationAxis.POSITIVE_Z.rotationDegrees(ROLL));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        this.setPosition(this.getPos().add(this.getVelocity()));
        float size = size(this.age);
        if (this.getWorld().isClient) {
            if (this.age > 1) {
                // Искры и дым срываются с кромки и кончиков серпа.
                Quaternionf q = orientation(this.getYaw(), this.getPitch());
                for (int i = 0; i < 4; i++) {
                    float t = this.random.nextFloat() * 2.0F - 1.0F;
                    Vector3f p = arc(t, size).add(across(t).mul(halfWidth(t, size) * (this.random.nextFloat() * 2.0F - 1.0F)));
                    q.transform(p);
                    this.getWorld().addParticle(i == 0 ? Fx.REIATSU_BLACK : Fx.REIATSU_RED, this.getX() + p.x, this.getY() + p.y,
                            this.getZ() + p.z, 0.0, 0.0, 0.0);
                }
                for (float tip : new float[]{-0.97F, 0.97F}) {
                    Vector3f p = q.transform(arc(tip, size));
                    this.getWorld().addParticle(ParticleTypes.SMOKE, this.getX() + p.x, this.getY() + p.y, this.getZ() + p.z, 0.0, 0.01, 0.0);
                }
            }
            return;
        }
        ServerWorld world = (ServerWorld) this.getWorld();
        ServerPlayerEntity player = this.owner == null ? null : (ServerPlayerEntity) world.getPlayerByUuid(this.owner);
        if (player == null || this.age > LIFETIME) {
            Fx.particles(world, ParticleTypes.LARGE_SMOKE, this.getPos(), 16, 0.8, 0.02);
            Fx.particles(world, Fx.REIATSU_RED, this.getPos(), 20, 1.0, 0.02);
            this.discard();
            return;
        }
        Box area = this.getBoundingBox().expand(2.4 * size, 1.4 * size, 2.4 * size);
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, area,
                m -> m.isAlive() && !this.hit.contains(m.getUuid()) && Abilities.isFoe(m, player))) {
            this.hit.add(mob.getUuid());
            mob.timeUntilRegen = 0;
            mob.damage(world.getDamageSources().indirectMagic(this, player), DAMAGE);
            Vec3d push = this.getVelocity().normalize();
            mob.takeKnockback(0.9, -push.x, -push.z);
            Vec3d at = mob.getBoundingBox().getCenter();
            Fx.particles(world, Fx.REIATSU_RED, at, 30, 0.4, 0.08);
            Fx.particles(world, Fx.REIATSU_BLACK, at, 16, 0.4, 0.05);
            Fx.particles(world, ParticleTypes.SWEEP_ATTACK, at, 2, 0.3, 0.0);
            Fx.particles(world, ParticleTypes.CRIT, at, 14, 0.3, 0.5);
            Fx.sound(mob, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
        }
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        this.setVelocity(packet.getVelocityX(), packet.getVelocityY(), packet.getVelocityZ());
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 128.0 * 128.0;
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
