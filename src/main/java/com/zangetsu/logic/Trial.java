package com.zangetsu.logic;

import com.zangetsu.entity.ThrownZangetsuEntity;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.network.CallingPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * Сон после того, как меч сломан. Поле мечей, а в небе Яхве: он говорит, что Зангецу в обиде, и его надо найти среди сотни фальшивых. У настоящего глаз на внутренней стороне клинка, у подделок снаружи.
 * Кто не нашёл за минуту, тому настоящий начинает подмигивать сам. Взял подделку, и она рассыпается; взял настоящего, и он прощает.
 */
public final class Trial {
    /** Сколько тиков идёт речь до начала поисков и через сколько настоящий начнёт подмигивать. */
    private static final int FIGURE_AT = 20, LINE_1 = 104, LINE_2 = 214, LINE_3 = 280, LINE_4 = 376, LINE_5 = 504, SEARCH_AT = 600;
    public static final int WINK_AFTER = 1200;
    private static final double FIGURE_AHEAD = 70.0, FIGURE_UP = 52.0;
    private static final float FIGURE_SCALE = 22.0F;
    /** Настоящий меч не близко и не далеко от места появления. */
    private static final double REAL_NEAR = 14.0, REAL_FAR = 34.0;
    /** Дальше этого сон не отпускает. */
    private static final double ROAM_LIMIT = 60.0;

    private static final int SPEAKING = 0, SEARCHING = 1, FOUND = 2;

    private static final class Session {
        int tick;
        int state = SPEAKING;
        int searchAt;
        int fakes;
        boolean winking;
        Vec3d home;
        UUID figure;
        UUID real;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Trial() {
    }

    public static boolean isActive(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** Поиски идут: можно брать мечи. */
    public static boolean isSearching(UUID player) {
        Session session = SESSIONS.get(player);
        return session != null && session.state == SEARCHING;
    }

    /** Настоящий уже подмигивает (для проверок). */
    public static boolean isWinking(UUID player) {
        Session session = SESSIONS.get(player);
        return session != null && session.winking;
    }

    static void begin(ServerPlayerEntity player, Vec3d home) {
        Session session = new Session();
        session.home = home;
        SESSIONS.put(player.getUuid(), session);
        ServerPlayNetworking.send(player, new CallingPayload(CallingPayload.BEGIN, "", 80, 0.0, 0.0, 0.0));
        Fx.sound(player, SoundEvents.BLOCK_BELL_RESONATE, 1.0F, 0.5F);
        Fx.sound(player, SoundEvents.AMBIENT_CAVE.value(), 0.8F, 0.7F);
    }

    /** Настоящий меч среди фальшивых: где-то неподалёку, в случайной стороне. */
    static void plantReal(ServerPlayerEntity player, ServerWorld world) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) {
            return;
        }
        Random random = world.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0, distance = REAL_NEAR + random.nextDouble() * (REAL_FAR - REAL_NEAR);
        double x = session.home.x + Math.cos(angle) * distance, z = session.home.z + Math.sin(angle) * distance;
        double y = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, 0, z)).getY();
        ThrownZangetsuEntity real = ThrownZangetsuEntity.plant(world, new Vec3d(x, y, z), player.getUuid(), random.nextFloat() * 360.0F, (random.nextFloat() - 0.5F) * 24.0F, (random.nextFloat() - 0.5F) * 24.0F);
        real.setKind(ThrownZangetsuEntity.REAL);
        session.real = real.getUuid();
    }

    public static boolean isReal(UUID player, ThrownZangetsuEntity sword) {
        Session session = SESSIONS.get(player);
        return session != null && sword.getUuid().equals(session.real);
    }

    private static ZangetsuSpiritEntity figure(ServerPlayerEntity player, Session session) {
        return session.figure != null && player.getServerWorld().getEntity(session.figure) instanceof ZangetsuSpiritEntity figure ? figure : null;
    }

    private static void say(ServerPlayerEntity player, Session session, String line, int ticks) {
        ServerPlayNetworking.send(player, new CallingPayload(CallingPayload.SPEECH, "zangetsu.trial." + line, ticks, 0.0, 0.0, 0.0));
        ZangetsuSpiritEntity figure = figure(player, session);
        if (figure != null) {
            figure.speak(ticks);
        }
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
            return;
        }
        if (!player.isAlive()) {
            return;
        }
        if (!Dream.inDream(player)) {
            abort(player, data);
            return;
        }
        session.tick++;
        ServerWorld world = player.getServerWorld();
        int t = session.tick;
        if (t == FIGURE_AT) {
            ZangetsuSpiritEntity figure = ZangetsuSpiritEntity.summon(world, session.home.add(0.0, FIGURE_UP, FIGURE_AHEAD), 180.0F, FIGURE_SCALE, player.getUuid());
            UUID uuid = player.getUuid();
            figure.keepWhile(() -> SESSIONS.containsKey(uuid));
            session.figure = figure.getUuid();
        }
        if (session.state == SPEAKING) {
            if (t == LINE_1 - 14) {
                phase(player, session, ZangetsuSpiritEntity.STARE);
            }
            if (t == LINE_1) {
                say(player, session, "line1", 104);
            }
            if (t == LINE_2) {
                say(player, session, "line2", 60);
            }
            if (t == LINE_3) {
                say(player, session, "line3", 90);
            }
            if (t == LINE_4) {
                phase(player, session, ZangetsuSpiritEntity.WATCH);
                say(player, session, "line4", 122);
            }
            if (t == LINE_5) {
                say(player, session, "line5", 90);
            }
            if (t == SEARCH_AT) {
                session.state = SEARCHING;
                session.searchAt = t;
                ServerPlayNetworking.send(player, new CallingPayload(CallingPayload.BARS_OFF, "zangetsu.trial.hint", 1, 0.0, 0.0, 0.0));
            }
            return;
        }
        if (session.state == SEARCHING) {
            // Минута прошла: настоящий открывает глаз и подмигивает, его слышно издалека.
            if (!session.winking && t - session.searchAt >= WINK_AFTER) {
                startWinking(player, session, world);
            }
            if (session.winking && (t - session.searchAt) % ThrownZangetsuEntity.WINK_CYCLE == 0 && world.getEntity(session.real) instanceof ThrownZangetsuEntity real) {
                Fx.sound(world, real.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 2.0F, 0.9F);
            }
            // Сон не отпускает: кто ушёл слишком далеко, снова стоит посреди поля.
            if (player.getPos().subtract(session.home).horizontalLength() > ROAM_LIMIT) {
                Sync.fx(player, com.zangetsu.network.ClientFxPayload.WHITE_FLASH, 24);
                player.networkHandler.requestTeleport(session.home.x, session.home.y, session.home.z, player.getYaw(), player.getPitch());
            }
        }
    }

    private static void startWinking(ServerPlayerEntity player, Session session, ServerWorld world) {
        session.winking = true;
        if (world.getEntity(session.real) instanceof ThrownZangetsuEntity real) {
            real.setWinking(true);
        }
        say(player, session, "wink", 90);
    }

    /** Сразу дойти до минуты (для проверок). */
    public static void hurry(ServerPlayerEntity player) {
        Session session = SESSIONS.get(player.getUuid());
        if (session != null) {
            if (session.state == SPEAKING) {
                session.tick = SEARCH_AT - 1;
                return;
            }
            session.searchAt = session.tick - WINK_AFTER;
        }
    }

    /** Игрок взялся за меч. */
    public static void pull(ServerPlayerEntity player, ThrownZangetsuEntity sword) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null || session.state != SEARCHING) {
            return;
        }
        if (isReal(player.getUuid(), sword)) {
            session.state = FOUND;
            phase(player, session, ZangetsuSpiritEntity.STARE);
            ServerPlayNetworking.send(player, new CallingPayload(CallingPayload.BARS_ON, "", 0, 0.0, 0.0, 0.0));
            say(player, session, "found", 80);
            Dream.pull(player, sword);
            return;
        }
        // Подделка рассыпается прямо в руке.
        session.fakes++;
        ServerWorld world = player.getServerWorld();
        Vec3d at = sword.getPos();
        for (int i = 0; i < 5; i++) {
            Fx.particles(world, ParticleTypes.ASH, at.add(0.0, 0.35 * i, 0.0), 26, 0.16, 0.03);
            Fx.particles(world, ParticleTypes.LARGE_SMOKE, at.add(0.0, 0.35 * i, 0.0), 3, 0.1, 0.01);
        }
        Fx.sound(world, at, SoundEvents.ENTITY_ITEM_BREAK, 1.0F, 0.6F);
        Fx.sound(world, at, SoundEvents.BLOCK_SAND_BREAK, 1.0F, 0.6F);
        sword.discard();
        Cinematic.lock(player, 14);
        say(player, session, session.fakes % 2 == 1 ? "fake1" : "fake2", 50);
    }

    /** Проснулся с мечом: Яхве больше не нужен. */
    static void finish(ServerPlayerEntity player) {
        Session session = SESSIONS.remove(player.getUuid());
        if (session != null && session.figure != null) {
            ServerWorld world = player.getServer().getWorld(Dream.WORLD);
            Entity figure = world == null ? null : world.getEntity(session.figure);
            if (figure != null) {
                figure.discard();
            }
        }
        ServerPlayNetworking.send(player, new CallingPayload(CallingPayload.END, "", 0, 0.0, 0.0, 0.0));
    }

    /** Сон сорвался: вернуть как было. Меч остаётся сломанным, сон придёт снова. */
    public static void abort(ServerPlayerEntity player, ZangetsuData data) {
        finish(player);
        Dream.afterAbort(player, data);
    }

    public static void disconnect(ServerPlayerEntity player) {
        SESSIONS.remove(player.getUuid());
    }

    public static void clear() {
        SESSIONS.clear();
    }

    /** Сколько фальшивых он уже взял (для проверок). */
    public static int fakesTaken(UUID player) {
        Session session = SESSIONS.get(player);
        return session == null ? 0 : session.fakes;
    }
}
