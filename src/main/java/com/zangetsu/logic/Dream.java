package com.zangetsu.logic;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.network.AnimationPayload;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.registry.ModAttachments;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/**
 * Сон с мечами. Если меч потерян, достаточно лечь спать: игроку снится поле, где в землю воткнуты
 * сотни одинаковых Зангецу. Он берёт любой — и просыпается с мечом и всей его прокачкой.
 */
public final class Dream {
    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, ZangetsuMod.id("dream"));
    /** Высота поверхности поля во сне (см. data/zangetsu/dimension/dream.json). */
    public static final double GROUND_Y = 5.0;
    public static final int PULL_DURATION = 60;
    private static final int SWORDS = 170;
    private static final double FIELD_RADIUS = 34.0;
    private static final Set<UUID> PULLING = new HashSet<>();

    private Dream() {
    }

    public static boolean inDream(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey() == WORLD;
    }

    /** У каждого игрока своё поле, чтобы сны не пересекались. */
    private static Vec3d center(UUID uuid) {
        return new Vec3d(Math.floorMod(uuid.hashCode(), 64) * 2000 + 0.5, GROUND_Y, 0.5);
    }

    public static boolean shouldBegin(ServerPlayerEntity player, ZangetsuData data) {
        return data.bonded && data.lost && data.dream == null && player.isSleeping() && player.getSleepTimer() >= 30
                && !inDream(player);
    }

    public static void begin(ServerPlayerEntity player) {
        ServerWorld dream = player.getServer().getWorld(WORLD);
        if (dream == null) {
            ZangetsuMod.LOGGER.error("Зангецу: нет измерения сна {}", WORLD.getValue());
            return;
        }
        ZangetsuData data = ModAttachments.data(player);
        // Сначала будим: так игрок встаёт рядом с кроватью, туда он и вернётся.
        player.wakeUp(true, true);
        data.dream = new ZangetsuData.DreamReturn(player.getWorld().getRegistryKey().getValue(), player.getX(), player.getY(),
                player.getZ(), player.getYaw(), player.getPitch(), player.interactionManager.getGameMode().asString());
        Vec3d c = center(player.getUuid());
        loadField(dream, c);
        player.teleport(dream, c.x, c.y, c.z, 0.0F, 10.0F);
        if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            player.changeGameMode(GameMode.ADVENTURE);
        }
        spawnSwords(dream, player.getUuid(), c);

        player.networkHandler.sendPacket(new TitleFadeS2CPacket(30, 80, 30));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("zangetsu.dream.subtitle").formatted(Formatting.GRAY)));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("zangetsu.dream.title").formatted(Formatting.WHITE)));
        Fx.sound(player, SoundEvents.BLOCK_BELL_RESONATE, 1.0F, 0.6F);
        Fx.sound(player, SoundEvents.AMBIENT_CAVE.value(), 0.8F, 1.2F);
        Sync.player(player);
    }

    private static void loadField(ServerWorld dream, Vec3d c) {
        int cx = MathHelper.floor(c.x) >> 4, cz = MathHelper.floor(c.z) >> 4;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                dream.getChunk(cx + dx, cz + dz);
            }
        }
    }

    /** Сотни одинаковых мечей, воткнутых в землю под разными углами. */
    private static void spawnSwords(ServerWorld dream, UUID owner, Vec3d c) {
        Random random = dream.getRandom();
        for (int i = 0; i < SWORDS; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            // Первые мечи — совсем рядом и прямо перед игроком, чтобы было за что взяться сразу.
            double distance = i < 6 ? 2.5 + i * 0.6 : 3.5 + Math.sqrt(random.nextDouble()) * (FIELD_RADIUS - 3.5);
            if (i < 6) {
                angle = Math.PI / 2.0 + (i - 2.5) * 0.35;
            }
            Vec3d at = new Vec3d(c.x + Math.cos(angle) * distance, GROUND_Y, c.z + Math.sin(angle) * distance);
            float yaw = random.nextFloat() * 360.0F;
            float tiltX = (random.nextFloat() - 0.5F) * 24.0F;
            float tiltZ = (random.nextFloat() - 0.5F) * 24.0F;
            ThrownZangetsuEntity.plant(dream, at, owner, yaw, tiltX, tiltZ);
        }
    }

    public static void pull(ServerPlayerEntity player, ThrownZangetsuEntity sword) {
        if (!inDream(player) || PULLING.contains(player.getUuid()) || ModAttachments.data(player).dream == null) {
            return;
        }
        PULLING.add(player.getUuid());
        Cinematic.face(player, sword.getPos().add(0.0, 1.3, 0.0));
        Cinematic.play(player, AnimationPayload.DREAM_PULL, PULL_DURATION);
        Fx.sound(player, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0F, 0.8F);
        Scheduler.later(16, () -> {
            sword.setState(ThrownZangetsuEntity.RISING);
            ServerWorld world = player.getServerWorld();
            Fx.particles(world, ParticleTypes.END_ROD, sword.getPos().add(0, 0.3, 0), 30, 0.3, 0.1);
            Fx.sound(player, SoundEvents.ITEM_TRIDENT_RETURN, 1.0F, 0.6F);
            Fx.sound(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.5F);
        });
        Scheduler.later(32, () -> Sync.fx(player, ClientFxPayload.WHITE_FLASH, 46));
        Scheduler.later(44, () -> wake(player));
    }

    private static void wake(ServerPlayerEntity player) {
        PULLING.remove(player.getUuid());
        if (player.isRemoved()) {
            return;
        }
        ZangetsuData data = ModAttachments.data(player);
        ZangetsuData.DreamReturn back = data.dream;
        data.dream = null;
        clearSwords(player);

        ServerWorld target = null;
        if (back != null) {
            target = player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, back.dimension()));
            player.changeGameMode(GameMode.byName(back.gameMode(), GameMode.SURVIVAL));
        }
        if (target == null) {
            sendHome(player);
            target = player.getServerWorld();
        } else {
            player.teleport(target, back.x(), back.y(), back.z(), back.yaw(), back.pitch());
        }
        // Проснулся — уже утро, как после обычного сна.
        if (target == player.getServer().getOverworld() && target.isNight()) {
            long time = target.getTimeOfDay();
            target.setTimeOfDay(time + (24000L - time % 24000L));
        }

        data.lost = false;
        ItemStack sword = SwordStacks.create(player);
        SwordStacks.giveToHand(player, sword);
        Fx.sound(player, SoundEvents.ITEM_TOTEM_USE, 0.8F, 1.1F);
        Fx.particles(player.getServerWorld(), ParticleTypes.END_ROD, player.getPos().add(0, 1, 0), 40, 0.5, 0.05);
        Sync.fx(player, ClientFxPayload.FLOATING_SWORD, 0);
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 60, 20));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("zangetsu.dream.back.subtitle").formatted(Formatting.GRAY)));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("zangetsu.dream.back").formatted(Formatting.GOLD)));
        Sync.player(player);
    }

    private static void clearSwords(ServerPlayerEntity player) {
        ServerWorld dream = player.getServer().getWorld(WORLD);
        if (dream == null) {
            return;
        }
        UUID owner = player.getUuid();
        Box field = Box.of(center(owner), FIELD_RADIUS * 2 + 16, 32, FIELD_RADIUS * 2 + 16);
        for (ThrownZangetsuEntity sword : dream.getEntitiesByClass(ThrownZangetsuEntity.class, field, s -> owner.equals(s.getOwner()))) {
            sword.discard();
        }
    }

    private static void sendHome(ServerPlayerEntity player) {
        ServerWorld overworld = player.getServer().getOverworld();
        BlockPos spawn = overworld.getSpawnPos();
        player.teleport(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
    }

    /** Каждый тик: игрок во сне после перезахода снова видит мечи; случайно попавший сюда — уходит домой. */
    static void tick(ServerPlayerEntity player, ZangetsuData data) {
        if (!inDream(player) || PULLING.contains(player.getUuid()) || player.age % 40 != 0) {
            return;
        }
        if (data.dream == null) {
            if (player.interactionManager.getGameMode() == GameMode.ADVENTURE) {
                player.changeGameMode(GameMode.SURVIVAL);
            }
            sendHome(player);
            return;
        }
        ServerWorld dream = player.getServerWorld();
        UUID owner = player.getUuid();
        boolean hasSwords = !dream.getEntitiesByClass(ThrownZangetsuEntity.class, player.getBoundingBox().expand(48.0),
                s -> owner.equals(s.getOwner())).isEmpty();
        if (!hasSwords) {
            Vec3d c = center(owner);
            loadField(dream, c);
            spawnSwords(dream, owner, c);
        }
    }

    /** Сразу отправить игрока в сон (команда для проверки). */
    public static void forceBegin(ServerPlayerEntity player) {
        ZangetsuData data = ModAttachments.data(player);
        if (!data.bonded || data.dream != null) {
            return;
        }
        for (ItemStack stack : SwordStacks.find(player)) {
            stack.setCount(0);
        }
        data.lost = true;
        begin(player);
    }
}
