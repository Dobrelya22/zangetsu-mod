package com.zangetsu.logic;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
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
    public static final DustParticleEffect REIATSU_RED = new DustParticleEffect(new Vector3f(0.85F, 0.05F, 0.05F), 0.9F);
    public static final DustParticleEffect REIATSU_BLACK = new DustParticleEffect(new Vector3f(0.05F, 0.02F, 0.04F), 1.0F);
    public static final DustParticleEffect EYE_GOLD = new DustParticleEffect(new Vector3f(1.0F, 0.8F, 0.15F), 0.9F);

    /** Что брызжет из разрезанного моба и чем заполнен срез: у зомби гниль, у скелета кость, у крипера порох. */
    public enum Gore {
        BLOOD(Fx.BLOOD, null, "flesh"),
        ROT(new DustParticleEffect(new Vector3f(0.33F, 0.16F, 0.06F), 1.3F), null, "rot"),
        BONE(new DustParticleEffect(new Vector3f(0.86F, 0.84F, 0.76F), 1.1F), new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.BONE)), "bone"),
        SOOT(new DustParticleEffect(new Vector3f(0.07F, 0.07F, 0.08F), 1.4F), new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COAL)), "soot"),
        POWDER(new DustParticleEffect(new Vector3f(0.24F, 0.42F, 0.2F), 1.2F), new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.GUNPOWDER)), "powder"),
        ENDER(Fx.ENDER_BLOOD, null, "ender");

        public final ParticleEffect dust;
        /** Кусочки того, из чего сделан моб; null — только брызги. */
        public final ParticleEffect chunk;
        /** Текстура среза. */
        public final String cut;

        Gore(ParticleEffect dust, ParticleEffect chunk, String cut) {
            this.dust = dust;
            this.chunk = chunk;
            this.cut = "textures/entity/cut_" + cut + ".png";
        }

        public static Gore of(LivingEntity entity) {
            return of(Registries.ENTITY_TYPE.getId(entity.getType()).getPath());
        }

        /** По имени вида моба: так же решает и клиент, у которого есть только оно. */
        public static Gore of(String path) {
            if (path.equals("wither_skeleton")) {
                return SOOT;
            }
            if (path.contains("skeleton") || path.equals("stray") || path.equals("bogged")) {
                return BONE;
            }
            if (path.equals("creeper")) {
                return POWDER;
            }
            if (path.equals("enderman")) {
                return ENDER;
            }
            if (path.contains("zombie") || path.contains("zombified") || path.equals("husk") || path.equals("drowned")) {
                return ROT;
            }
            return BLOOD;
        }
    }

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
