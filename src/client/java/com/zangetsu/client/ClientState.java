package com.zangetsu.client;

import com.zangetsu.network.PlayerStatePayload;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
    /** id игрока → какой из трёх ударов мечом он сейчас наносит (идут по кругу). */
    public static final Int2IntOpenHashMap SWING_VARIANT = new Int2IntOpenHashMap();

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

    private ClientState() {
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

    public static int swingVariant(Entity entity) {
        return SWING_VARIANT.getOrDefault(entity.getId(), 0);
    }

    /** Начался новый удар — следующий вариант по кругу (диагональ, снизу вверх, горизонталь). */
    public static int nextSwing(Entity entity) {
        int variant = (swingVariant(entity) + 1) % 3;
        SWING_VARIANT.put(entity.getId(), variant);
        return variant;
    }

    public static boolean isHollow(Entity entity) {
        return HOLLOW.containsKey(entity.getId());
    }

    public static int maskCrack(Entity entity) {
        return HOLLOW.getOrDefault(entity.getId(), 0);
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
        SWING_VARIANT.remove(entityId);
    }

    public static void reset() {
        state = new PlayerStatePayload(false, 0, 1, false, 0, false);
        LIMBS.clear();
        ANIMATIONS.clear();
        HOLLOW.clear();
        SWING_VARIANT.clear();
        flashStart = -1;
    }
}
