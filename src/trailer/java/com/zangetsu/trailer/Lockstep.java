package com.zangetsu.trailer;

import com.zangetsu.ZangetsuMod;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;

/**
 * Сервер и клиент идут шаг в шаг: пока идёт съёмка, встроенный сервер не тикает сам, а ждёт команды
 * клиента — «сделай тик» или «выполни действие». Так каждый кадр ролика — ровно 1/60 секунды игры
 * (или меньше в замедлении), как бы медленно ни рисовал компьютер.
 */
public final class Lockstep {
    private static final Runnable TICK = () -> {
    };
    private static final BlockingQueue<Runnable> COMMANDS = new LinkedBlockingQueue<>();
    private static final Semaphore DONE = new Semaphore(0);
    private static volatile boolean enabled;
    /** Только поток сервера: этот тик заказан клиентом. */
    private static boolean commandedTick;

    private Lockstep() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void enable(boolean value) {
        enabled = value;
    }

    /** Поток сервера, начало тика: ждём команду. */
    public static void beforeServerTick() {
        while (enabled) {
            Runnable command;
            try {
                command = COMMANDS.poll(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (command == null) {
                continue;
            }
            if (command == TICK) {
                commandedTick = true;
                return;
            }
            try {
                command.run();
            } catch (RuntimeException e) {
                ZangetsuMod.LOGGER.error("Трейлер: действие на сервере упало", e);
            } finally {
                DONE.release();
            }
        }
    }

    /** Поток сервера, конец тика. */
    public static void afterServerTick() {
        if (commandedTick) {
            commandedTick = false;
            DONE.release();
        }
    }

    /** Клиент: один тик сервера; вернётся, когда тик кончится. */
    public static void tickServer() {
        send(TICK);
    }

    /** Клиент: выполнить действие на сервере. В шаге — сразу и дождаться; иначе — просто поставить в очередь. */
    public static void onServer(Runnable action) {
        if (enabled) {
            send(action);
            return;
        }
        MinecraftServer server = MinecraftClient.getInstance().getServer();
        if (server != null) {
            server.execute(() -> {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    ZangetsuMod.LOGGER.error("Трейлер: действие на сервере упало", e);
                }
            });
        }
    }

    private static void send(Runnable command) {
        COMMANDS.add(command);
        try {
            if (!DONE.tryAcquire(60, TimeUnit.SECONDS)) {
                ZangetsuMod.LOGGER.error("Трейлер: сервер не ответил за минуту");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
