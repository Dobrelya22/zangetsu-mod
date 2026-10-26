package com.zangetsu.entity;

import com.zangetsu.logic.Abilities;
import com.zangetsu.logic.Fx;
import com.zangetsu.registry.ModEntities;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Гецуга Теншо — чёрный серп с алой кромкой, который в форме Пустого срывается с клинка.
 * Летит прямо, проходит сквозь злых мобов и рубит каждого один раз.
 */
public class GetsugaEntity extends Entity {
    public static final int LIFETIME = 26;
    public static final float DAMAGE = 18.0F;
    private static final double SPEED = 1.4;

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
        Vec3d start = player.getEyePos().add(look.multiply(1.2)).add(0, -0.3, 0);
        getsuga.refreshPositionAndAngles(start.x, start.y, start.z, player.getYaw(), player.getPitch());
        getsuga.setVelocity(look.multiply(SPEED));
        getsuga.owner = player.getUuid();
        world.spawnEntity(getsuga);
        Fx.sound(player, SoundEvents.ENTITY_WITHER_SHOOT, 0.8F, 0.6F);
        Fx.sound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        this.setPosition(this.getPos().add(this.getVelocity()));
        if (this.getWorld().isClient) {
            for (int i = 0; i < 3 && this.age > 2; i++) {
                double side = (this.random.nextDouble() - 0.5) * 3.0;
                Vec3d v = this.getVelocity();
                Vec3d across = new Vec3d(-v.z, 0, v.x).normalize().multiply(side);
                this.getWorld().addParticle(i == 0 ? ParticleTypes.SMOKE : Fx.REIATSU_RED,
                        this.getX() + across.x, this.getY() + 0.3, this.getZ() + across.z, 0.0, 0.0, 0.0);
            }
            return;
        }
        ServerWorld world = (ServerWorld) this.getWorld();
        ServerPlayerEntity player = this.owner == null ? null : (ServerPlayerEntity) world.getPlayerByUuid(this.owner);
        if (player == null || this.age > LIFETIME) {
            Fx.particles(world, ParticleTypes.LARGE_SMOKE, this.getPos(), 12, 0.6, 0.02);
            this.discard();
            return;
        }
        Box area = this.getBoundingBox().expand(1.6, 0.8, 1.6);
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, area,
                m -> m.isAlive() && !this.hit.contains(m.getUuid()) && Abilities.isFoe(m, player))) {
            this.hit.add(mob.getUuid());
            mob.timeUntilRegen = 0;
            mob.damage(world.getDamageSources().indirectMagic(this, player), DAMAGE);
            Vec3d push = this.getVelocity().normalize();
            mob.takeKnockback(0.8, -push.x, -push.z);
            Fx.particles(world, Fx.REIATSU_RED, mob.getBoundingBox().getCenter(), 20, 0.3, 0.05);
            Fx.particles(world, ParticleTypes.SWEEP_ATTACK, mob.getBoundingBox().getCenter(), 1, 0.0, 0.0);
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
