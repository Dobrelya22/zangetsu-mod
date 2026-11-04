package com.zangetsu.client;

import com.zangetsu.client.render.FormPhysics;
import com.zangetsu.network.PlayerStatePayload;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.entity.Entity;

/** Что клиент знает о мече: своя связь, чужие отрубленные руки, анимации и маски Пустого. */
public final class ClientState {
    public static PlayerStatePayload state = new PlayerStatePayload(false, 0, 1, false, 0, false);
    /** id сущности → отрубленные конечности (флаги {@link com.zangetsu.logic.Limbs}). */
    public static final Int2IntOpenHashMap LIMBS = new Int2IntOpenHashMap();
    /** id игрока → анимация, которую он сейчас играет. */
    public static final Int2ObjectOpenHashMap<Animation> ANIMATIONS = new Int2ObjectOpenHashMap<>();
    /** id игроков в форме Пустого → насколько треснула их маска (0–3). */
    public static final Int2IntOpenHashMap HOLLOW = new Int2IntOpenHashMap();
    /** id игроков в финальной форме (маска с рогом). */
    public static final IntOpenHashSet FINAL = new IntOpenHashSet();
    /** id игрока → шаг его серии ударов. */
    public static final Int2IntOpenHashMap SWING_VARIANT = new Int2IntOpenHashMap();
    /** id игрока → удар, который он сейчас наносит. */
    public static final Int2ObjectOpenHashMap<Swing> SWINGS = new Int2ObjectOpenHashMap<>();

    /** Счётчик клиентских тиков — по нему идут вспышки на экране. */
    public static int ticks;
    public static int flashType;
    public static int flashStart = -1;
    public static int flashDuration;

    /** Анимация: какая, с какого «возраста» сущности началась, сколько длится и её параметр (куда бить). */
    public record Animation(int type, int startAge, int duration, int param) {
        public float progress(Entity entity, float tickDelta) {
            return (entity.age + tickDelta - this.startAge) / Math.max(1, this.duration);
        }
    }

    /**
     * Идущий удар: какой приём, когда начался (время — возраст сущности с долей тика), длительность, поворот к цели и цвет
     * следа. При попадании удар замирает на мгновение — это и даёт ощущение веса.
     */
    public static final class Swing {
        public final int move;
        public final float start, duration, aim;
        public final int color, startTick;
        /** Свой удар по нажатию: в момент удара он должен нанести урон. */
        public boolean attacking, struck, whooshed;
        /** Это казнь: её не перебить следующим ударом, а в миг удара всё замирает сильнее обычного. */
        public boolean execution;
        public Entity target;
        private float freezeAt = -1.0F, freezeFor;

        public Swing(int move, float start, float duration, float aim, int color, int startTick) {
            this.move = move;
            this.start = start;
            this.duration = duration;
            this.aim = aim;
            this.color = color;
            this.startTick = startTick;
        }

        public void freeze(float at, float length) {
            this.freezeAt = at;
            this.freezeFor = length;
        }

        public float progress(float now) {
            float time = now;
            if (this.freezeAt >= 0.0F && now >= this.freezeAt) {
                time = now < this.freezeAt + this.freezeFor ? this.freezeAt : now - this.freezeFor;
            }
            return (time - this.start) / this.duration;
        }
    }

    private ClientState() {
    }

    /** Текущий удар сущности (null — никакого или уже кончился). */
    public static Swing swing(Entity entity, float now) {
        Swing swing = SWINGS.get(entity.getId());
        if (swing != null && swing.progress(now) >= 1.0F) {
            SWINGS.remove(entity.getId());
            return null;
        }
        return swing;
    }

    public static int limbs(Entity entity) {
        return LIMBS.getOrDefault(entity.getId(), 0);
    }

    /** Текущая анимация сущности (null — никакой или уже кончилась). */
    public static Animation animation(Entity entity) {
        Animation animation = ANIMATIONS.get(entity.getId());
        if (animation != null && entity.age - animation.startAge() > animation.duration()) {
            ANIMATIONS.remove(entity.getId());
            return null;
        }
        return animation;
    }

    /** Новый удар — следующий шаг серии этого игрока. */
    public static int nextSwing(Entity entity) {
        int step = SWING_VARIANT.getOrDefault(entity.getId(), -1) + 1;
        SWING_VARIANT.put(entity.getId(), step);
        return step;
    }

    public static boolean isHollow(Entity entity) {
        return HOLLOW.containsKey(entity.getId());
    }

    public static int maskCrack(Entity entity) {
        return HOLLOW.getOrDefault(entity.getId(), 0);
    }

    public static boolean isFinal(Entity entity) {
        return FINAL.contains(entity.getId());
    }

    public static void flash(int type, int duration) {
        flashType = type;
        flashStart = ticks;
        flashDuration = duration;
    }

    public static void forget(int entityId) {
        LIMBS.remove(entityId);
        ANIMATIONS.remove(entityId);
        HOLLOW.remove(entityId);
        FINAL.remove(entityId);
        SWING_VARIANT.remove(entityId);
        SWINGS.remove(entityId);
        FormPhysics.forget(entityId);
    }

    public static void reset() {
        state = new PlayerStatePayload(false, 0, 1, false, 0, false);
        LIMBS.clear();
        ANIMATIONS.clear();
        HOLLOW.clear();
        FINAL.clear();
        SWING_VARIANT.clear();
        SWINGS.clear();
        FormPhysics.reset();
        flashStart = -1;
    }
}
