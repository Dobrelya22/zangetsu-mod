package com.zangetsu.client;

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
    /** id игроков в форме Пустого. */
    public static final IntOpenHashSet HOLLOW = new IntOpenHashSet();

    /** Счётчик клиентских тиков — по нему идут вспышки на экране. */
    public static int ticks;
    public static int flashType;
    public static int flashStart = -1;
    public static int flashDuration;

    /** Анимация: какая, с какого «возраста» сущности началась и сколько длится. */
    public record Animation(int type, int startAge, int duration) {
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

    public static boolean isHollow(Entity entity) {
        return HOLLOW.contains(entity.getId());
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
    }

    public static void reset() {
        state = new PlayerStatePayload(false, 0, 1, false, 0, false);
        LIMBS.clear();
        ANIMATIONS.clear();
        HOLLOW.clear();
        flashStart = -1;
    }
}
