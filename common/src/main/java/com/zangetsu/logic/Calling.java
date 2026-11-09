package com.zangetsu.logic;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.network.CallingPayload;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.registry.ModAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.zangetsu.network.Net;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Первый сон. Игрок просто живёт в мире, но когда он впервые ложится спать, ему снится малиновое небо и колосс в огромном плаще на горизонте: говорит, что он достоин меча, поднимает руки, и
 * Зангецу вылетает из его груди и вонзается в землю перед ним. Взял рукоять — просыпается с мечом. Вернулся в свою кровать за спиной — отказался: фигура прощается, и меча у него не будет уже никогда.
 */
public final class Calling {
    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, ZangetsuMod.id("calling"));

    /** Сколько тиков игрок должен пролежать, прежде чем сон начнётся: экран успевает потемнеть. */
    private static final int FALL_ASLEEP = 40;
    /** Где всё стоит относительно места появления (блоки вперёд и вверх). */
    public static final double FIGURE_AHEAD = 70.0, FIGURE_UP = 52.0, SWORD_AHEAD = 3.8;
    public static final float FIGURE_SCALE = 22.0F;
    private static final int BED_BEHIND = 5;
    /** Дальше этого сон не отпускает. */
    private static final double ROAM_LIMIT = 52.0;
    /** Ближе этого к кровати фигура предупреждает, что это навсегда. */
    private static final double WARN_AT = 3.6;

    /** Вступление: когда что происходит (тики от начала сна). */
    public static final int FIGURE_AT = 25, LINE_1 = 118, LINE_2 = 192, LINE_3 = 306, SWORD_AT = 366, HOLD = 40, FLIGHT = 44;
    /** Когда начинается пролёт камеры. */
    public static final int CAMERA_AT = 10;
    /** Сколько тиков после вонзания остаётся широкий кадр. */
    private static final int OFFER_BARS = 96;
    /** Отказ: тики от нажатия на кровать. */
    private static final int REFUSE_1 = 12, REFUSE_2 = 108, REFUSE_DUST = 130, REFUSE_3 = 184, REFUSE_4 = 290, REFUSE_CLOSE = 310;
    private static final int REFUSE_DISSOLVE = REFUSE_CLOSE + ZangetsuSpiritEntity.EYE_COUNT * ZangetsuSpiritEntity.EYE_CLOSE_STEP + 14;
    private static final int REFUSE_FADE = REFUSE_DISSOLVE + 16, REFUSE_WAKE = REFUSE_FADE + 44;
    /** Взял меч: тики от конца сцены подбора. */
    private static final int WAKE_FLASH = 44, WAKE_AT = 56;

    private static final int INTRO = 0, OFFER = 1, CLAIMING = 2, REFUSING = 3, WAKING = 4;

    private static final class Session {
        int tick;
        int state = INTRO;
        int stateAt;
        boolean warned;
        Vec3d home;
        Vec3d landing;
        BlockPos bedHead;
        BlockPos bedFoot;
        UUID figure;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Calling() {
    }

    public static boolean isActive(UUID player) {
        return SESSIONS.containsKey(player);
    }

    public static boolean inCalling(ServerPlayerEntity player) {
        return player.getWorld().getRegistryKey() == WORLD;
    }

    /** Отказавшийся не увидит этот сон уже никогда. */
    public static boolean shouldBegin(ServerPlayerEntity player, ZangetsuData data) {
        return !data.bonded && !data.refused && data.dream == null && !isActive(player.getUuid()) && !player.isSpectator()
                && player.isSleeping() && player.getSleepTimer() >= FALL_ASLEEP;
    }

    public static void begin(ServerPlayerEntity player) {
        ServerWorld world = player.getServer().getWorld(WORLD);
        if (world == null) {
            ZangetsuMod.LOGGER.error("Зангецу: нет измерения сна {}", WORLD.getValue());
            return;
        }
        ZangetsuData data = ModAttachments.data(player);
        BlockState bed = sleptBed(player);
        // Сначала будим: так игрок встаёт рядом с кроватью, туда он и вернётся.
        player.wakeUp(true, true);
        data.dream = new ZangetsuData.DreamReturn(player.getWorld().getRegistryKey().getValue(), player.getX(), player.getY(),
                player.getZ(), player.getYaw(), player.getPitch(), player.interactionManager.getGameMode().asString());

        Vec3d center = Dream.center(player.getUuid());
        Dream.loadField(world, center);
        int feet = clearing(world, center) + 1;
        Session session = new Session();
        session.home = new Vec3d(center.x, feet, center.z);
        session.landing = session.home.add(0.0, 0.0, SWORD_AHEAD);
        placeBed(world, session, bed, feet);
        SESSIONS.put(player.getUuid(), session);

        // Смотрит туда, где сейчас проступит фигура.
        player.teleport(world, session.home.x, session.home.y, session.home.z, 0.0F, -10.0F);
        if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            player.changeGameMode(GameMode.ADVENTURE);
        }
        player.clearStatusEffects();
        player.extinguish();
        send(player, CallingPayload.BEGIN, "", 80);
        // Пока он говорит, игрок стоит и смотрит, куда ведёт камера. Как только меч вонзился, он свободен.
        Cinematic.lock(player, SWORD_AT + HOLD + FLIGHT);
        Fx.sound(player, SoundEvents.BLOCK_BELL_RESONATE, 1.0F, 0.5F);
        Fx.sound(player, SoundEvents.AMBIENT_CAVE.value(), 0.8F, 0.7F);
        Sync.player(player);
    }

    /** Кровать во сне та же, в которой он лёг. */
    private static BlockState sleptBed(ServerPlayerEntity player) {
        BlockState state = player.getSleepingPosition().map(pos -> player.getWorld().getBlockState(pos)).orElse(Blocks.RED_BED.getDefaultState());
        return state.getBlock() instanceof BedBlock ? state.getBlock().getDefaultState() : Blocks.RED_BED.getDefaultState();
    }

    /** Небольшая поляна посреди холмов: ровная земля под мечом и кроватью, края плавно уходят в рельеф. Возвращает высоту земли в середине. */
    static int clearing(ServerWorld world, Vec3d center) {
        int cx = MathHelper.floor(center.x), cz = MathHelper.floor(center.z), radius = 24;
        int middle = surface(world, cx, cz);
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                float d = MathHelper.sqrt(dx * dx + dz * dz);
                if (d > radius) {
                    continue;
                }
                float edge = MathHelper.clamp((d - 10.0F) / (radius - 10.0F), 0.0F, 1.0F);
                float k = 1.0F - edge * edge * (3.0F - 2.0F * edge);
                int x = cx + dx, z = cz + dz;
                int top = surface(world, x, z);
                int target = Math.round(MathHelper.lerp(k, top, middle));
                // Трава, цветы и камни над новой землёй.
                for (int y = target + 1; y <= Math.max(top, target) + 3; y++) {
                    if (!world.getBlockState(pos.set(x, y, z)).isAir()) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), flags);
                    }
                }
                for (int y = top + 1; y < target; y++) {
                    world.setBlockState(pos.set(x, y, z), Blocks.DIRT.getDefaultState(), flags);
                }
                if (target != top || d <= 14.0F) {
                    world.setBlockState(pos.set(x, target, z), Blocks.GRASS_BLOCK.getDefaultState(), flags);
                }
            }
        }
        return middle;
    }

    /** Верхний сплошной блок столбца. */
    private static int surface(ServerWorld world, int x, int z) {
        return world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY() - 1;
    }

    private static void placeBed(ServerWorld world, Session session, BlockState bed, int feet) {
        int x = MathHelper.floor(session.home.x), z = MathHelper.floor(session.home.z);
        // Изножьем к игроку: подошёл — и ложись.
        session.bedFoot = new BlockPos(x, feet, z - BED_BEHIND);
        session.bedHead = session.bedFoot.north();
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        BlockState facing = bed.with(BedBlock.FACING, Direction.NORTH);
        world.setBlockState(session.bedHead, facing.with(BedBlock.PART, BedPart.HEAD), flags);
        world.setBlockState(session.bedFoot, facing.with(BedBlock.PART, BedPart.FOOT), flags);
    }

    private static void removeBed(ServerWorld world, Session session) {
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        world.setBlockState(session.bedHead, Blocks.AIR.getDefaultState(), flags);
        world.setBlockState(session.bedFoot, Blocks.AIR.getDefaultState(), flags);
    }

    private static void send(ServerPlayerEntity player, int event, String key, int ticks) {
        Net.send(player, new CallingPayload(event, key, ticks, 0.0, 0.0, 0.0));
    }

    private static void say(ServerPlayerEntity player, String line, int ticks) {
        send(player, CallingPayload.SPEECH, "zangetsu.calling." + line, ticks);
        Session session = SESSIONS.get(player.getUuid());
        ZangetsuSpiritEntity figure = session == null ? null : figure(player, session);
        if (figure != null) {
            figure.speak(ticks);
        }
    }

    private static void lookAt(ServerPlayerEntity player, Vec3d at, int ticks) {
        Net.send(player, new CallingPayload(CallingPayload.LOOK, "", ticks, at.x, at.y, at.z));
    }

    private static ZangetsuSpiritEntity figure(ServerPlayerEntity player, Session session) {
        if (session.figure != null && player.getWorld() instanceof ServerWorld world && world.getEntity(session.figure) instanceof ZangetsuSpiritEntity figure) {
            return figure;
        }
        return null;
    }

    private static void phase(ServerPlayerEntity player, Session session, int phase) {
        ZangetsuSpiritEntity figure = figure(player, session);
        if (figure != null) {
            figure.setPhase(phase);
        }
    }

    public static void tick(ServerPlayerEntity player, ZangetsuData data) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            // Вышел из игры посреди сна: просыпается там, где лёг. Если он не отказывался, сон придёт снова.
            if (inCalling(player)) {
                Dream.bringBack(player, data, false);
                send(player, CallingPayload.END, "", 0);
                Sync.player(player);
            }
            return;
        }
        if (!player.isAlive()) {
            return;
        }
        if (!inCalling(player)) {
            // Умер или ушёл командой: сон кончился.
            abort(player);
            return;
        }
        session.tick++;
        int since = session.tick - session.stateAt;
        switch (session.state) {
            case INTRO -> intro(player, session);
            case OFFER -> offer(player, session, since);
            case REFUSING -> refusing(player, session, since);
            case WAKING -> waking(player, session, since);
            default -> {
            }
        }
    }

    private static void intro(ServerPlayerEntity player, Session session) {
        ServerWorld world = player.getServerWorld();
        int t = session.tick;
        if (t == FIGURE_AT) {
            Vec3d at = session.home.add(0.0, FIGURE_UP, FIGURE_AHEAD);
            ZangetsuSpiritEntity figure = ZangetsuSpiritEntity.summon(world, at, 180.0F, FIGURE_SCALE, player.getUuid());
            UUID uuid = player.getUuid();
            figure.keepWhile(() -> SESSIONS.containsKey(uuid));
            session.figure = figure.getUuid();
        }
        ZangetsuSpiritEntity colossus = figure(player, session);
        if (t == CAMERA_AT) {
            // Камера одним дублем: от плеча игрока вверх к лицу, потом на всю фигуру и за мечом на землю.
            send(player, CallingPayload.FLY, "", SWORD_AT + HOLD + FLIGHT - CAMERA_AT + 8);
        }
        if (colossus != null && t == LINE_3 - 10) {
            colossus.setArms(1);
        }
        if (t >= LINE_1 && t < SWORD_AT + HOLD && (t - LINE_1) % 44 == 0) {
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.7F, 0.6F);
        }
        if (t == LINE_1 - 22) {
            phase(player, session, ZangetsuSpiritEntity.WATCH);
        }
        if (t == LINE_1) {
            say(player, "line1", 66);
        }
        if (t == LINE_2) {
            say(player, "line2", 106);
        }
        if (t == LINE_3) {
            phase(player, session, ZangetsuSpiritEntity.STARE);
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_ROAR, 0.5F, 0.5F);
            say(player, "line3", 80);
        }
        if (t == SWORD_AT) {
            // Меч выходит из самой фигуры, и только потом летит.
            Awakening.open(player);
            ZangetsuSpiritEntity figure = figure(player, session);
            if (figure != null) {
                figure.holdSword(session.landing, HOLD, FLIGHT);
            }
            Fx.sound(player, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.9F, 0.5F);
        }
        if (t == SWORD_AT + HOLD - 2) {
            lookAt(player, session.landing.add(0.0, 0.5, 0.0), FLIGHT + 4);
        }
    }

    /** Меч вонзился: теперь выбор за игроком. */
    public static void onSwordLanded(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.state != INTRO) {
            return;
        }
        session.state = OFFER;
        session.stateAt = session.tick;
        phase(player, session, ZangetsuSpiritEntity.WATCH);
        ZangetsuSpiritEntity figure = figure(player, session);
        if (figure != null) {
            figure.setArms(0);
        }
        say(player, "line4", 88);
    }

    private static void offer(ServerPlayerEntity player, Session session, int since) {
        if (since == OFFER_BARS) {
            send(player, CallingPayload.BARS_OFF, "", 1);
        }
        Vec3d bed = Vec3d.ofCenter(session.bedHead);
        if (!session.warned && player.getPos().distanceTo(bed) < WARN_AT) {
            session.warned = true;
            Fx.sound(player, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE, 0.9F, 0.6F);
            phase(player, session, ZangetsuSpiritEntity.STARE);
            say(player, "warn", 104);
        }
        // Сон не отпускает: кто ушёл слишком далеко, снова стоит перед мечом.
        if (player.getPos().subtract(session.home).horizontalLength() > ROAM_LIMIT) {
            Sync.fx(player, ClientFxPayload.WHITE_FLASH, 24);
            player.networkHandler.requestTeleport(session.home.x, session.home.y, session.home.z, 0.0F, -10.0F);
        }
    }

    /** Игрок взялся за рукоять. */
    public static void onPickUp(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            return;
        }
        session.state = CLAIMING;
        session.stateAt = session.tick;
        phase(player, session, ZangetsuSpiritEntity.STARE);
        send(player, CallingPayload.BARS_OFF, "", 0);
    }

    /** Сцена подбора кончилась: меч на плече, пора просыпаться. */
    public static void onClaimed(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            return;
        }
        session.state = WAKING;
        session.stateAt = session.tick;
        phase(player, session, ZangetsuSpiritEntity.WATCH);
        say(player, "take", 42);
    }

    private static void waking(ServerPlayerEntity player, Session session, int since) {
        if (since == WAKE_FLASH) {
            Sync.fx(player, ClientFxPayload.WHITE_FLASH, 60);
            Fx.sound(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.5F);
        }
        if (since == WAKE_AT) {
            wake(player, session);
            Fx.sound(player, SoundEvents.ITEM_TOTEM_USE, 0.7F, 1.1F);
        }
    }

    /** Игрок лёг обратно в свою кровать. Отказ записан сразу: выйти из игры и передумать уже нельзя. */
    public static void refuse(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.state != OFFER) {
            return;
        }
        ModAttachments.data(player).refused = true;
        session.state = REFUSING;
        session.stateAt = session.tick;
        Cinematic.lock(player, REFUSE_WAKE + 2);
        send(player, CallingPayload.BARS_ON, "", 0);
        ZangetsuSpiritEntity figure = figure(player, session);
        if (figure != null) {
            lookAt(player, figure.faceAt(), 26);
            figure.setPhase(ZangetsuSpiritEntity.WRATH);
            figure.setArms(1);
        }
        Fx.sound(player, SoundEvents.ENTITY_WARDEN_ANGRY, 1.0F, 0.5F);
    }

    private static void refusing(ServerPlayerEntity player, Session session, int since) {
        ServerWorld world = player.getServerWorld();
        switch (since) {
            case REFUSE_1 -> say(player, "refuse1", 90);
            case REFUSE_2 -> say(player, "refuse2", 72);
            case REFUSE_DUST -> swordToDust(player, world, session);
            case REFUSE_3 -> say(player, "refuse3", 100);
            case REFUSE_4 -> say(player, "refuse4", 70);
            case REFUSE_CLOSE -> {
                phase(player, session, ZangetsuSpiritEntity.CLOSING);
                ZangetsuSpiritEntity figure = figure(player, session);
                if (figure != null) {
                    figure.setArms(0);
                }
            }
            case REFUSE_DISSOLVE -> {
                phase(player, session, ZangetsuSpiritEntity.DISSOLVING);
                Fx.sound(player, SoundEvents.ENTITY_WARDEN_DEATH, 0.6F, 0.5F);
            }
            case REFUSE_FADE -> send(player, CallingPayload.FADE_OUT, "", 40);
            case REFUSE_WAKE -> {
                wake(player, session);
                player.sendMessage(Text.translatable("zangetsu.calling.woke").formatted(Formatting.GRAY, Formatting.ITALIC), false);
            }
            default -> {
            }
        }
    }

    /** Отвергнутый меч рассыпается прямо в земле. */
    private static void swordToDust(ServerPlayerEntity player, ServerWorld world, Session session) {
        Vec3d at = session.landing;
        for (int i = 0; i < 6; i++) {
            Fx.farParticles(world, ParticleTypes.LARGE_SMOKE, at.add(0.0, 0.3 * i, 0.0), 8, 0.14, 0.02);
            Fx.farParticles(world, ParticleTypes.ASH, at.add(0.0, 0.3 * i, 0.0), 30, 0.2, 0.04);
        }
        Fx.sound(world, at, SoundEvents.ENTITY_ITEM_BREAK, 1.0F, 0.5F);
        Fx.sound(world, at, SoundEvents.BLOCK_SAND_BREAK, 1.0F, 0.5F);
        Fx.sound(world, at, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, 0.5F, 0.6F);
        Awakening.cancel(player);
    }

    private static void wake(ServerPlayerEntity player, Session session) {
        ServerWorld world = player.getServerWorld();
        cleanUp(player, session, world);
        returnDropped(player, world);
        Dream.bringBack(player, ModAttachments.data(player), true);
        send(player, CallingPayload.END, "", 36);
        Sync.player(player);
    }

    private static void cleanUp(ServerPlayerEntity player, Session session, ServerWorld world) {
        SESSIONS.remove(player.getUuid());
        removeBed(world, session);
        if (session.figure != null) {
            Entity figure = world.getEntity(session.figure);
            if (figure != null) {
                figure.discard();
            }
        }
        Awakening.cancel(player);
    }

    /** Всё, что он выбросил во сне, остаётся при нём: это всё-таки сон. */
    private static void returnDropped(ServerPlayerEntity player, ServerWorld world) {
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, new Box(player.getBlockPos()).expand(96.0), Entity::isAlive)) {
            player.getInventory().offerOrDrop(item.getStack().copy());
            item.discard();
        }
    }

    /** Сон сорвался (смерть, команда): вернуть всё как было. Отказом это не считается. */
    public static void abort(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            return;
        }
        ServerWorld world = player.getServer().getWorld(WORLD);
        if (world != null) {
            cleanUp(player, session, world);
        }
        SESSIONS.remove(player.getUuid());
        ZangetsuData data = ModAttachments.data(player);
        if (inCalling(player)) {
            Dream.bringBack(player, data, false);
        } else if (data.dream != null) {
            player.changeGameMode(GameMode.byName(data.dream.gameMode(), GameMode.SURVIVAL));
            data.dream = null;
        }
        send(player, CallingPayload.END, "", 0);
        Sync.player(player);
    }

    /** Игрок вышел из игры: сон обрывается, при входе он окажется там, где лёг. */
    public static void disconnect(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            return;
        }
        ServerWorld world = player.getServer().getWorld(WORLD);
        if (world != null) {
            cleanUp(player, session, world);
        }
        SESSIONS.remove(player.getUuid());
    }

    public static void clear() {
        SESSIONS.clear();
    }

    /** Изголовье кровати во сне, для проверок. */
    public static BlockPos bed(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        return session == null ? null : session.bedHead;
    }

    /** Меч этого сна уже воткнут и ждёт выбора. */
    public static boolean isOffering(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        return session != null && session.state == OFFER;
    }

    /** Меч этого сна, если он уже есть (для проверок). */
    public static ThrownZangetsuEntity swordOf(ServerPlayerEntity player) {
        for (ThrownZangetsuEntity sword : player.getServerWorld().getEntitiesByClass(ThrownZangetsuEntity.class, new Box(player.getBlockPos()).expand(64.0),
                s -> player.getUuid().equals(s.getOwner()))) {
            return sword;
        }
        return null;
    }
}
