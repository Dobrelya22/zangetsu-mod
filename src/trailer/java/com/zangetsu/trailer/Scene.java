package com.zangetsu.trailer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;

/**
 * Сцена — непрерывный кусок жизни мира (один «дубль»), который снимается несколькими камерами: план за
 * планом, с событиями в нужные секунды и своей скоростью времени (замедления).
 */
public final class Scene {
    /** План: с какой секунды и какой камерой. */
    public record Cut(double start, String name, TrailerCamera.Shot shot) {
    }

    /** Событие в секунду ролика: действие на клиенте (серверные — через {@link Lockstep#onServer}). */
    public record Event(double time, Runnable action) {
    }

    public final String name;
    public final double start;
    public final double end;
    Consumer<MinecraftServer> setup = server -> {
    };
    Consumer<MinecraftClient> clientSetup = client -> {
    };
    /** Сколько тиков дать миру устояться после подготовки. */
    int settle = 40;
    /** Вокруг чего облететь камерой перед съёмкой, чтобы прогрузились все куски мира (null — площадка). */
    Supplier<Vec3d> sweepCenter;
    Insert.Spec insert;
    final List<Cut> cuts = new ArrayList<>();
    final List<Event> events = new ArrayList<>();
    final TreeMap<Double, Float> speeds = new TreeMap<>();

    Scene(String name, double start, double end) {
        this.name = name;
        this.start = start;
        this.end = end;
        this.speeds.put(start, 1.0F);
    }

    public int startFrame() {
        return (int) Math.round(this.start * Director.FPS);
    }

    public int endFrame() {
        return (int) Math.round(this.end * Director.FPS);
    }

    void seal() {
        this.cuts.sort(Comparator.comparingDouble(Cut::start));
        this.events.sort(Comparator.comparingDouble(Event::time));
    }

    Cut cutAt(double v) {
        Cut current = this.cuts.get(0);
        for (Cut cut : this.cuts) {
            if (cut.start() <= v + 1.0E-6) {
                current = cut;
            }
        }
        return current;
    }

    double cutEnd(Cut cut) {
        int i = this.cuts.indexOf(cut);
        return i + 1 < this.cuts.size() ? this.cuts.get(i + 1).start() : this.end;
    }

    float speedAt(double v) {
        Map.Entry<Double, Float> e = this.speeds.floorEntry(v + 1.0E-6);
        return e == null ? 1.0F : e.getValue();
    }
}
