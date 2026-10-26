package com.zangetsu.logic;

import net.minecraft.entity.Entity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Звуки и частицы на стороне сервера. */
public final class Fx {
    public static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.62F, 0.02F, 0.04F), 1.3F);
    public static final DustParticleEffect ENDER_BLOOD = new DustParticleEffect(new Vector3f(0.55F, 0.1F, 0.75F), 1.3F);
    public static final DustParticleEffect REIATSU_RED = new DustParticleEffect(new Vector3f(0.85F, 0.05F, 0.05F), 1.6F);
    public static final DustParticleEffect REIATSU_BLACK = new DustParticleEffect(new Vector3f(0.05F, 0.02F, 0.04F), 2.0F);
    public static final DustParticleEffect EYE_GOLD = new DustParticleEffect(new Vector3f(1.0F, 0.8F, 0.15F), 0.9F);

    private Fx() {
    }

    public static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
        at.getWorld().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    public static void sound(ServerWorld world, Vec3d at, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.PLAYERS, volume, pitch);
    }

    public static void particles(ServerWorld world, ParticleEffect effect, Vec3d at, int count, double spread, double speed) {
        world.spawnParticles(effect, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /** Частицы, видные издалека (до 512 блоков) — для фигуры в небе. */
    public static void farParticles(ServerWorld world, ParticleEffect effect, Vec3d at, int count, double spread, double speed) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            world.spawnParticles(player, effect, true, at.x, at.y, at.z, count, spread, spread, spread, speed);
        }
    }

    /** Кольцо частиц вокруг точки — ударная волна. */
    public static void ring(ServerWorld world, ParticleEffect effect, Vec3d center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double x = center.x + Math.cos(a) * radius, z = center.z + Math.sin(a) * radius;
            world.spawnParticles(effect, x, center.y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
