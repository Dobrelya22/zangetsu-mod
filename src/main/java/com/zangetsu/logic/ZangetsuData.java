package com.zangetsu.logic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.Identifier;

/**
 * Связь игрока с Зангецу. Живёт в данных игрока (а не в предмете), поэтому переживает смерть,
 * потерю меча и сон: меч, вернувшийся во сне, приходит со всей прокачкой.
 */
public final class ZangetsuData {
    public static final Codec<ZangetsuData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("bonded", false).forGetter(d -> d.bonded),
            Codec.INT.optionalFieldOf("bond", 0).forGetter(d -> d.bond),
            Codec.LONG.optionalFieldOf("hollow_day", -1L).forGetter(d -> d.hollowDay),
            Codec.INT.optionalFieldOf("hollow_used", 0).forGetter(d -> d.hollowUsed),
            Codec.BOOL.optionalFieldOf("hollow_active", false).forGetter(d -> d.hollowActive),
            Codec.BOOL.optionalFieldOf("lost", false).forGetter(d -> d.lost),
            DreamReturn.CODEC.optionalFieldOf("dream").forGetter(d -> Optional.ofNullable(d.dream)),
            Codec.BOOL.optionalFieldOf("refused", false).forGetter(d -> d.refused),
            Codec.BOOL.optionalFieldOf("broken", false).forGetter(d -> d.broken)
    ).apply(instance, ZangetsuData::new));

    /** Меч уже пришёл к игроку. */
    public boolean bonded;
    /** Дружба с мечом: растёт за злых мобов, падает за добрых. */
    public int bond;
    /** День, за который считается время формы Пустого, и сколько тиков уже потрачено. */
    public long hollowDay;
    public int hollowUsed;
    public boolean hollowActive;
    /** Меча нет в инвентаре — он вернётся во сне. */
    public boolean lost;
    /** Куда вернуть игрока после сна с мечами (null — игрок не спит). */
    public DreamReturn dream;
    /** Отказался от меча в первом сне: больше он не придёт. */
    public boolean refused;
    /** Яхве сломал меч, спасая от смерти: вернуть его можно, только найдя настоящего среди фальшивых. */
    public boolean broken;

    public ZangetsuData() {
        this(false, 0, -1L, 0, false, false, Optional.empty(), false, false);
    }

    private ZangetsuData(boolean bonded, int bond, long hollowDay, int hollowUsed, boolean hollowActive, boolean lost,
            Optional<DreamReturn> dream, boolean refused, boolean broken) {
        this.bonded = bonded;
        this.bond = bond;
        this.hollowDay = hollowDay;
        this.hollowUsed = hollowUsed;
        this.hollowActive = hollowActive;
        this.lost = lost;
        this.dream = dream.orElse(null);
        this.refused = refused;
        this.broken = broken;
    }

    public int level() {
        return Bond.levelFor(this.bond);
    }

    public record DreamReturn(Identifier dimension, double x, double y, double z, float yaw, float pitch, String gameMode) {
        public static final Codec<DreamReturn> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("dimension").forGetter(DreamReturn::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(DreamReturn::x),
                Codec.DOUBLE.fieldOf("y").forGetter(DreamReturn::y),
                Codec.DOUBLE.fieldOf("z").forGetter(DreamReturn::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(DreamReturn::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(DreamReturn::pitch),
                Codec.STRING.fieldOf("game_mode").forGetter(DreamReturn::gameMode)
        ).apply(instance, DreamReturn::new));
    }
}
