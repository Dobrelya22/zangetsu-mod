package com.zangetsu.logic;

import java.util.ArrayList;
import java.util.List;

/** Отложенные действия на сервере: шаги анимаций, казней и сна идут по тикам. */
public final class Scheduler {
    private record Task(long due, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    private Scheduler() {
    }

    public static void later(int ticks, Runnable action) {
        TASKS.add(new Task(now + Math.max(1, ticks), action));
    }

    static void tick() {
        now++;
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(task -> {
            if (task.due() <= now) {
                due.add(task);
                return true;
            }
            return false;
        });
        for (Task task : due) {
            try {
                task.action().run();
            } catch (RuntimeException e) {
                com.zangetsu.ZangetsuMod.LOGGER.error("Зангецу: ошибка в отложенном действии", e);
            }
        }
    }

    static void clear() {
        TASKS.clear();
    }
}
