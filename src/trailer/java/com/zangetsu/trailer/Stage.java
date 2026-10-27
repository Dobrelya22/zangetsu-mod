package com.zangetsu.trailer;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.logic.Bond;
import com.zangetsu.logic.Execution;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.logic.Limbs;
import com.zangetsu.logic.SwordStacks;
import com.zangetsu.logic.ZangetsuData;
import com.zangetsu.registry.ModAttachments;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.block.Block;
import net.minecraft.block.SnowyBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

/**
 * Постановка трейлера на сервере: площадка в заснеженной равнине, мобы, молнии, удары мечом.
 * Всё, что здесь, выполняется в потоке сервера.
 */
public final class Stage {
    /** Центр площадки (там стоит герой) и центр поля во сне. Читаются и камерой на клиенте. */
    public static volatile Vec3d center = Vec3d.ZERO;
    public static volatile Vec3d dreamCenter = Vec3d.ZERO;
    /** Номера сущностей по имени (одинаковы на сервере и клиенте). */
    public static final Map<String, Integer> IDS = new ConcurrentHashMap<>();
    private static boolean arenaReady;

    private Stage() {
    }

    public static ServerPlayerEntity player(MinecraftServer server) {
        return server.getPlayerManager().getPlayerList().get(0);
    }

    public static Vec3d at(double x, double y, double z) {
        return center.add(x, y, z);
    }

    public static int id(String name) {
        return IDS.getOrDefault(name, -1);
    }

    public static Entity entity(MinecraftServer server, String name) {
        return player(server).getServerWorld().getEntityById(id(name));
    }

    public static void command(MinecraftServer server, String command) {
        server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), command);
    }

    // ---------------------------------------------------------------- площадка

    /** Ровное место в заснеженной равнине: ищем один раз, дальше все сцены снимаются там. */
    public static void arena(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        if (!arenaReady) {
            Pair<BlockPos, RegistryEntry<Biome>> found = world.locateBiome(b -> b.matchesKey(BiomeKeys.SNOWY_PLAINS),
                    BlockPos.ORIGIN, 6400, 32, 64);
            BlockPos base = found != null ? found.getFirst() : BlockPos.ORIGIN;
            BlockPos best = base;
            int bestScore = Integer.MAX_VALUE;
            for (int dx = -96; dx <= 96; dx += 24) {
                for (int dz = -96; dz <= 96; dz += 24) {
                    BlockPos c = base.add(dx, 0, dz);
                    if (!world.getBiome(c.withY(height(world, c.getX(), c.getZ()))).matchesKey(BiomeKeys.SNOWY_PLAINS)) {
                        continue;
                    }
                    int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
                    for (int sx = -16; sx <= 16; sx += 8) {
                        for (int sz = -16; sz <= 16; sz += 8) {
                            int y = height(world, c.getX() + sx, c.getZ() + sz);
                            min = Math.min(min, y);
                            max = Math.max(max, y);
                        }
                    }
                    int score = (max - min) * 10 + Math.abs(dx) / 24 + Math.abs(dz) / 24;
                    if (score < bestScore) {
                        bestScore = score;
                        best = c;
                    }
                }
            }
            int y = height(world, best.getX(), best.getZ());
            center = new Vec3d(best.getX() + 0.5, y, best.getZ() + 0.5);
            flatten(world, best, y);
            lights(world);
            arenaReady = true;
            ZangetsuMod.LOGGER.info("Трейлер: площадка {} (неровность {})", center, bestScore);
            for (String rule : new String[]{"doDaylightCycle false", "doWeatherCycle false", "doMobSpawning false", "doFireTick false",
                    "mobGriefing false", "doMobLoot false", "showDeathMessages false", "sendCommandFeedback false",
                    "announceAdvancements false", "naturalRegeneration true", "doInsomnia false", "doPatrolSpawning false",
                    "doTraderSpawning false"}) {
                command(server, "gamerule " + rule);
            }
        }
        command(server, "time set 13400");
        command(server, "weather rain 1000000");
    }

    /** Убрать всех, кроме героя, вокруг точки. */
    public static void clear(ServerWorld world, Vec3d around) {
        for (Entity e : world.getEntitiesByClass(Entity.class, Box.of(around, 120, 80, 120), e -> !(e instanceof PlayerEntity))) {
            e.discard();
        }
        IDS.keySet().removeIf(k -> !k.startsWith("keep"));
    }

    /** Невидимые источники света вокруг героя — тёплый «рисующий» свет; дальше по полю — сумерки. */
    private static void lights(ServerWorld world) {
        for (int i = 0; i < 8; i++) {
            light(world, TrailerCamera.around(center, 3.5, i * 45.0 + 22.5, 0.0), 2, 12);
        }
        for (int i = 0; i < 10; i++) {
            light(world, TrailerCamera.around(center, 9.0, i * 36.0, 0.0), 2, 8);
        }
        light(world, center, 4, 10);
    }

    private static void light(ServerWorld world, Vec3d p, int height, int level) {
        BlockPos pos = BlockPos.ofFloored(p.x, ground(world, p.x, p.z) + height, p.z);
        if (world.getBlockState(pos).isAir()) {
            world.setBlockState(pos, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, level));
        }
    }

    /**
     * Сцена: ровное снежное поле радиусом 22 блока, к краю (34) плавно переходящее в настоящий рельеф; деревья,
     * лёд и трава на нём убраны.
     */
    private static void flatten(ServerWorld world, BlockPos c, int y) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -34; dx <= 34; dx++) {
            for (int dz = -34; dz <= 34; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > 34.0) {
                    continue;
                }
                int x = c.getX() + dx, z = c.getZ() + dz;
                int original = height(world, x, z);
                double k = MathHelper.clamp((r - 22.0) / 12.0, 0.0, 1.0);
                k = k * k * (3.0 - 2.0 * k);
                int h = (int) Math.round(MathHelper.lerp(k, y, original));
                for (int yy = Math.min(original, h) - 4; yy < h - 1; yy++) {
                    world.setBlockState(pos.set(x, yy, z), Blocks.DIRT.getDefaultState(), Block.NOTIFY_LISTENERS);
                }
                world.setBlockState(pos.set(x, h - 1, z), Blocks.GRASS_BLOCK.getDefaultState().with(SnowyBlock.SNOWY, true),
                        Block.NOTIFY_LISTENERS);
                world.setBlockState(pos.set(x, h, z), Blocks.SNOW.getDefaultState(), Block.NOTIFY_LISTENERS);
                for (int yy = h + 1; yy <= Math.max(original, h) + 14; yy++) {
                    if (!world.getBlockState(pos.set(x, yy, z)).isAir()) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            }
        }
    }

    /**
     * Кольцо мобов вокруг героя, лицом к нему: {@code count} штук на радиусе от {@code r0} до {@code r1},
     * по углам от {@code a0} до {@code a1}; типы — по кругу. {@code health} — сколько здоровья (0 — полное).
     */
    public static void ring(ServerPlayerEntity player, String prefix, int count, double r0, double r1, double a0, double a1,
            List<EntityType<? extends MobEntity>> types, boolean ai, float health) {
        for (int i = 0; i < count; i++) {
            double k = count == 1 ? 0.5 : i / (double) (count - 1);
            double jitter = ((i * 0.618034) % 1.0) - 0.5;
            double angle = a0 + (a1 - a0) * k + jitter * (a1 - a0) / Math.max(1, count) * 0.6;
            double radius = r0 + (r1 - r0) * ((i * 0.381966 + 0.13) % 1.0);
            Vec3d pos = TrailerCamera.around(center, radius, angle, 0.0);
            spawn(player, prefix + i, types.get(i % types.size()), pos, yawToCenter(pos), ai, health);
        }
    }

    /** Лицом к герою с точки {@code pos}. */
    public static float yawToCenter(Vec3d pos) {
        Vec3d d = center.subtract(pos);
        return (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0F;
    }

    /** Герой на площадке: смотрит на север, неуязвим (удары парируются, но не ранят). */
    public static void hero(ServerPlayerEntity player, boolean freshBond) {
        if (freshBond) {
            player.setAttached(ModAttachments.PLAYER, new ZangetsuData());
            player.getInventory().clear();
        }
        if (player.getServerWorld() != player.getServer().getOverworld()) {
            player.teleport(player.getServer().getOverworld(), center.x, center.y, center.z, 180.0F, 5.0F);
        }
        teleport(player, center, 180.0F, 5.0F);
        player.setHealth(player.getMaxHealth());
        // Никаких красных вспышек от ударов: стрелы меч рассекает и так, а казни и отсечения — по сценарию.
        player.setInvulnerable(true);
        player.clearStatusEffects();
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 1_000_000, 255, false, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 1_000_000, 0, false, false, false));
    }

    /** Связь с мечом нужного уровня и меч в руке. */
    public static void bond(ServerPlayerEntity player, int level) {
        ZangetsuData data = ModAttachments.data(player);
        data.bonded = true;
        data.lost = false;
        if (SwordStacks.find(player).isEmpty()) {
            SwordStacks.giveToHand(player, SwordStacks.create(player));
        }
        Bond.change(player, Bond.THRESHOLDS[level - 1] - data.bond);
    }

    public static void teleport(ServerPlayerEntity player, Vec3d pos, float yaw, float pitch) {
        player.networkHandler.requestTeleport(pos.x, pos.y, pos.z, yaw, pitch);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
    }

    public static void face(ServerPlayerEntity player, float yaw) {
        teleport(player, player.getPos(), yaw, 0.0F);
    }

    // ---------------------------------------------------------------- мобы

    public static double ground(ServerWorld world, double x, double z) {
        return height(world, (int) Math.floor(x), (int) Math.floor(z));
    }

    /** Высота земли; кусок мира при этом загружается (у незагруженного высота — самое дно мира). */
    private static int height(ServerWorld world, int x, int z) {
        return world.getChunk(x >> 4, z >> 4).sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }

    /** Моб на площадке; {@code ai} — сразу идёт на героя. */
    public static int spawn(ServerPlayerEntity player, String name, EntityType<? extends MobEntity> type, Vec3d pos, float yaw, boolean ai,
            float health) {
        ServerWorld world = player.getServerWorld();
        MobEntity mob = type.create(world);
        if (mob == null) {
            return -1;
        }
        double y = ground(world, pos.x, pos.z);
        mob.refreshPositionAndAngles(pos.x, y, pos.z, yaw, 0.0F);
        mob.initialize(world, world.getLocalDifficulty(BlockPos.ofFloored(pos)), SpawnReason.COMMAND, null);
        // Малыш-зомби мог сесть на курицу — ни малышей, ни кур в кадре.
        Entity mount = mob.getVehicle();
        if (mount != null) {
            mob.stopRiding();
            mount.discard();
        }
        if (mob instanceof ZombieEntity zombie) {
            zombie.setBaby(false);
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            mob.equipStack(slot, ItemStack.EMPTY);
        }
        if (type == EntityType.ZOMBIFIED_PIGLIN) {
            mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
        } else if (type == EntityType.STRAY || type == EntityType.SKELETON) {
            mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        } else {
            mob.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        mob.setHeadYaw(yaw);
        mob.setBodyYaw(yaw);
        mob.setPersistent();
        mob.setAiDisabled(!ai);
        if (health > 0) {
            mob.setHealth(Math.min(health, mob.getMaxHealth()));
        }
        if (ai) {
            mob.setTarget(player);
        }
        world.spawnEntity(mob);
        if (name != null) {
            IDS.put(name, mob.getId());
        }
        return mob.getId();
    }

    /** Ряд мобов поперёк направления (для Гецуги): {@code yaw} — откуда смотрит герой. */
    public static void line(ServerPlayerEntity player, String prefix, float heroYaw, double from, double to, int count, float health) {
        double a = Math.toRadians(heroYaw);
        Vec3d forward = new Vec3d(-Math.sin(a), 0, Math.cos(a));
        Vec3d side = new Vec3d(forward.z, 0, -forward.x);
        EntityType<?>[] types = {EntityType.ZOMBIE, EntityType.HUSK, EntityType.ZOMBIE, EntityType.ZOMBIFIED_PIGLIN, EntityType.STRAY, EntityType.ZOMBIE};
        for (int i = 0; i < count; i++) {
            double across = (i - (count - 1) / 2.0) * 1.3;
            double along = from + (to - from) * ((i * 7) % count) / Math.max(1.0, count - 1);
            Vec3d pos = center.add(forward.multiply(along)).add(side.multiply(across));
            @SuppressWarnings("unchecked")
            EntityType<? extends MobEntity> type = (EntityType<? extends MobEntity>) types[i % types.length];
            spawn(player, prefix + i, type, pos, heroYaw + 180.0F, false, health);
        }
    }

    public static void wake(MinecraftServer server, String prefix) {
        ServerPlayerEntity player = player(server);
        for (Map.Entry<String, Integer> e : IDS.entrySet()) {
            if (e.getKey().startsWith(prefix) && player.getServerWorld().getEntityById(e.getValue()) instanceof MobEntity mob) {
                mob.setAiDisabled(false);
                mob.clearStatusEffects();
                mob.setTarget(player);
            }
        }
    }

    public static void killAll(MinecraftServer server) {
        ServerPlayerEntity player = player(server);
        for (LivingEntity e : player.getServerWorld().getEntitiesByClass(LivingEntity.class, Box.of(center, 60, 30, 60),
                e -> !(e instanceof PlayerEntity) && e.isAlive())) {
            e.setInvulnerable(false);
            e.damage(player.getServerWorld().getDamageSources().magic(), 1000.0F);
        }
    }

    // ---------------------------------------------------------------- действия

    public static void lightning(MinecraftServer server, Vec3d pos) {
        ServerWorld world = player(server).getServerWorld();
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.setCosmetic(true);
            bolt.refreshPositionAfterTeleport(pos.x, ground(world, pos.x, pos.z), pos.z);
            world.spawnEntity(bolt);
        }
    }

    public static void cut(MinecraftServer server, String name, int part) {
        ServerPlayerEntity player = player(server);
        if (entity(server, name) instanceof LivingEntity target && target.isAlive()) {
            Vec3d d = target.getPos().subtract(player.getPos());
            face(player, (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0));
            player.swingHand(Hand.MAIN_HAND, true);
            Limbs.cut(target, player, part);
        }
    }

    public static void execute(MinecraftServer server, String name) {
        ServerPlayerEntity player = player(server);
        Entity e = entity(server, name);
        if (e != null) {
            e.setInvulnerable(false);
            Execution.request(player, e.getId());
        }
    }

    public static void swing(MinecraftServer server, String name) {
        if (entity(server, name) instanceof LivingEntity mob) {
            mob.swingHand(Hand.MAIN_HAND, true);
        }
    }

    public static void getsuga(MinecraftServer server, float yaw) {
        ServerPlayerEntity player = player(server);
        teleport(player, player.getPos(), yaw, 0.0F);
        player.swingHand(Hand.MAIN_HAND, true);
        HollowForm.tryGetsuga(player);
    }

    /** Стрелы в героя — меч рассечёт их на лету. {@code angle} — откуда летят (как в {@link TrailerCamera#around}). */
    public static void arrows(MinecraftServer server, double distance, double angle) {
        ServerPlayerEntity player = player(server);
        ServerWorld world = player.getServerWorld();
        Vec3d target = player.getEyePos().add(0, -0.4, 0);
        for (int i = 0; i < 3; i++) {
            Vec3d from = TrailerCamera.around(center, distance, angle + (i - 1) * 9.0, 1.8);
            ArrowEntity arrow = new ArrowEntity(world, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            Vec3d d = target.subtract(from);
            arrow.setVelocity(d.x, d.y + 0.12, d.z, 1.6F, 0.0F);
            world.spawnEntity(arrow);
        }
    }

    public static ZangetsuSpiritEntity spirit(MinecraftServer server) {
        List<ZangetsuSpiritEntity> list = player(server).getServerWorld().getEntitiesByClass(ZangetsuSpiritEntity.class,
                Box.of(center, 120, 120, 120), e -> true);
        return list.isEmpty() ? null : list.get(0);
    }

    public static ThrownZangetsuEntity nearestSword(ServerWorld world, Vec3d around, double radius, double minDistance) {
        List<ThrownZangetsuEntity> list = new ArrayList<>(world.getEntitiesByClass(ThrownZangetsuEntity.class,
                Box.of(around, radius * 2, 40, radius * 2), e -> e.getPos().distanceTo(around) >= minDistance));
        list.sort(Comparator.comparingDouble(e -> e.getPos().squaredDistanceTo(around)));
        return list.isEmpty() ? null : list.get(0);
    }
}
