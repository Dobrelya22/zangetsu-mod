package com.zangetsu.client.render;

import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.HollowForm;
import net.minecraft.util.math.MathHelper;

/**
 * Приёмы Зангецу. Удар описан не углами рук, а самим мечом: где кулак, куда смотрит клинок и куда — лезвие; рука (и вторая,
 * если приём двуручный) приходят к мечу сами, а взгляд от первого лица и вид со стороны идут по одной и той же траектории.
 *
 * <p>Ритм у всех один, он и даёт вес: неторопливый замах, короткая пауза со сжатой силой, разгон через весь взмах с лезвием
 * по ходу движения, плавное затухание по инерции и спокойный возврат. Между кадрами — сплайн: движение через промежуточные кадры
 * не останавливается, а скорость нулевая только там, где это сказано (конец замаха, конец инерции).
 *
 * <p>Кадры записаны глазами игрока: x — вправо, y — вверх, z — к зрителю (в блоках, от глаз). Для вида со стороны они переводятся
 * в координаты модели (пиксели, x — влево, y — вниз).
 */
public final class SwordMoves {
    /** Поля кадра: время, остановка, кулак, клинок, лезвие, поворот и наклон корпуса, ноги, двуручность, поворот всего тела, присед. */
    public static final int T = 0, STOP = 1, FX = 2, BX = 5, EX = 8, YAW = 11, PITCH = 12, LEG_R = 13, LEG_L = 14, TWO = 15, SPIN = 16, DIP = 17, SIZE = 18;
    /** Масштаб перевода кадров в модель (пикселей на блок) и где глаза относительно шеи. */
    private static final float BODY_SCALE = 13.0F, EYE_Y = -3.5F, EYE_Z = 1.0F;
    /** После скольких тиков можно начинать следующий удар: столько же перезаряжается удар меча. */
    private static final float CHAIN_TICKS = 14.0F;

    /**
     * Приём: длительность в тиках, момент удара и откуда можно начать следующий (доли длительности), когда виден след,
     * кадры для взгляда и для модели.
     */
    public record Move(float duration, float hit, float chain, float trailFrom, float trailTo, float[][] view, float[][] body) {
    }

    private static float[] k(float t, boolean stop, float fx, float fy, float fz, float bx, float by, float bz, float ex, float ey, float ez,
            float yaw, float pitch, float legR, float legL, float two, float spin, float dip) {
        return new float[]{t, stop ? 1.0F : 0.0F, fx, fy, fz, bx, by, bz, ex, ey, ez, yaw, pitch, legR, legL, two, spin, dip};
    }

    /** Покой: значения подставляются из текущей позы (см. {@link #sample}). */
    private static float[] rest(float t, float spin) {
        float[] key = new float[SIZE];
        key[T] = t;
        key[STOP] = 1.0F;
        key[SPIN] = spin;
        return key;
    }

    /** Косой удар двумя руками: клинок поднят справа, рубит через весь экран вниз налево. */
    private static final Move KESA = move(18.0F, 0.42F,
            rest(0.0F, 0.0F),
            k(0.24F, true, 0.58F, -0.30F, -0.70F, 0.34F, 0.90F, -0.28F, -0.62F, -0.02F, -0.78F, 0.55F, -0.10F, 0.15F, -0.25F, 1.0F, 0.0F, 0.0F),
            k(0.31F, true, 0.60F, -0.26F, -0.68F, 0.38F, 0.90F, -0.22F, -0.60F, 0.0F, -0.80F, 0.60F, -0.12F, 0.18F, -0.28F, 1.0F, 0.0F, 0.0F),
            k(0.42F, false, 0.12F, -0.30F, -0.84F, -0.32F, 0.70F, -0.64F, -0.62F, -0.62F, -0.48F, 0.0F, 0.10F, -0.30F, 0.20F, 1.0F, 0.0F, 0.6F),
            k(0.50F, false, -0.30F, -0.46F, -0.80F, -0.80F, 0.10F, -0.59F, -0.40F, -0.85F, 0.30F, -0.60F, 0.28F, -0.55F, 0.35F, 1.0F, 0.0F, 1.2F),
            k(0.66F, true, -0.36F, -0.50F, -0.78F, -0.86F, -0.05F, -0.50F, -0.25F, -0.85F, 0.46F, -0.72F, 0.32F, -0.60F, 0.40F, 0.9F, 0.0F, 1.5F),
            rest(1.0F, 0.0F));
    /** Восходящий удар одной рукой: снизу слева вверх направо. */
    private static final Move RISING = move(16.0F, 0.40F,
            rest(0.0F, 0.0F),
            k(0.22F, true, -0.26F, -0.46F, -0.74F, -0.78F, -0.10F, -0.62F, 0.50F, 0.72F, -0.50F, -0.55F, 0.25F, -0.30F, 0.20F, 0.0F, 0.0F, 0.8F),
            k(0.29F, true, -0.30F, -0.48F, -0.72F, -0.82F, -0.12F, -0.56F, 0.45F, 0.72F, -0.52F, -0.60F, 0.28F, -0.32F, 0.22F, 0.0F, 0.0F, 1.0F),
            k(0.40F, false, 0.12F, -0.40F, -0.86F, 0.16F, 0.62F, -0.77F, 0.76F, 0.46F, 0.46F, 0.10F, 0.05F, -0.20F, 0.10F, 0.0F, 0.0F, 0.4F),
            k(0.48F, false, 0.50F, -0.22F, -0.76F, 0.60F, 0.72F, -0.35F, 0.60F, -0.05F, 0.80F, 0.55F, -0.10F, 0.10F, -0.10F, 0.0F, 0.0F, 0.0F),
            k(0.62F, true, 0.56F, -0.14F, -0.72F, 0.56F, 0.78F, -0.28F, 0.66F, -0.15F, 0.74F, 0.65F, -0.15F, 0.15F, -0.15F, 0.0F, 0.0F, 0.0F),
            rest(1.0F, 0.0F));
    /** Широкий горизонтальный взмах справа налево: клинок плашмя проходит перед глазами. */
    private static final Move SWEEP = move(18.0F, 0.42F,
            rest(0.0F, 0.0F),
            k(0.24F, true, 0.62F, -0.34F, -0.72F, 0.78F, 0.32F, -0.54F, -0.55F, 0.0F, -0.84F, 0.70F, -0.02F, 0.20F, -0.25F, 0.0F, 0.0F, 0.3F),
            k(0.31F, true, 0.64F, -0.32F, -0.70F, 0.82F, 0.33F, -0.46F, -0.50F, 0.0F, -0.87F, 0.75F, -0.04F, 0.22F, -0.28F, 0.0F, 0.0F, 0.4F),
            k(0.42F, false, 0.06F, -0.30F, -0.90F, -0.30F, 0.38F, -0.87F, -0.93F, -0.10F, 0.28F, 0.0F, 0.08F, -0.25F, 0.10F, 0.0F, 0.0F, 0.6F),
            k(0.50F, false, -0.44F, -0.34F, -0.76F, -0.90F, 0.20F, -0.38F, -0.38F, 0.0F, 0.92F, -0.75F, 0.12F, -0.45F, 0.30F, 0.0F, 0.0F, 0.8F),
            k(0.66F, true, -0.50F, -0.38F, -0.72F, -0.92F, 0.12F, -0.36F, -0.36F, -0.05F, 0.93F, -0.90F, 0.14F, -0.50F, 0.32F, 0.0F, 0.0F, 0.8F),
            rest(1.0F, 0.0F));
    /** Рубящий сверху двумя руками: самый тяжёлый, клинок вертикально над головой, потом присед в удар. */
    private static final Move OVERHEAD = move(20.0F, 0.47F,
            rest(0.0F, 0.0F),
            k(0.30F, true, 0.16F, -0.10F, -0.62F, 0.10F, 0.84F, -0.54F, 0.0F, -0.54F, -0.84F, 0.15F, -0.18F, 0.25F, -0.20F, 1.0F, 0.0F, -0.4F),
            k(0.38F, true, 0.16F, -0.06F, -0.60F, 0.10F, 0.90F, -0.42F, 0.0F, -0.42F, -0.90F, 0.18F, -0.22F, 0.28F, -0.22F, 1.0F, 0.0F, -0.6F),
            k(0.47F, false, 0.06F, -0.26F, -0.84F, 0.02F, 0.50F, -0.87F, 0.0F, -0.87F, -0.50F, 0.0F, 0.20F, -0.40F, 0.25F, 1.0F, 0.0F, 0.8F),
            k(0.54F, false, 0.04F, -0.44F, -0.82F, -0.18F, -0.10F, -0.98F, -0.55F, -0.82F, 0.15F, -0.05F, 0.42F, -0.60F, 0.40F, 1.0F, 0.0F, 2.0F),
            k(0.68F, true, 0.04F, -0.48F, -0.80F, -0.16F, -0.20F, -0.97F, -0.55F, -0.80F, 0.20F, -0.05F, 0.45F, -0.62F, 0.42F, 1.0F, 0.0F, 2.2F),
            rest(1.0F, 0.0F));
    /** Обратный взмах слева направо. */
    private static final Move BACKHAND = move(16.0F, 0.40F,
            rest(0.0F, 0.0F),
            k(0.20F, true, -0.30F, -0.38F, -0.72F, -0.82F, 0.30F, -0.48F, 0.50F, 0.10F, -0.86F, -0.70F, 0.05F, -0.20F, 0.15F, 0.0F, 0.0F, 0.3F),
            k(0.28F, true, -0.32F, -0.36F, -0.70F, -0.86F, 0.30F, -0.40F, 0.45F, 0.0F, -0.89F, -0.75F, 0.05F, -0.22F, 0.18F, 0.0F, 0.0F, 0.4F),
            k(0.40F, false, 0.12F, -0.32F, -0.90F, 0.24F, 0.38F, -0.89F, 0.96F, 0.0F, 0.26F, 0.05F, 0.05F, 0.10F, -0.10F, 0.0F, 0.0F, 0.5F),
            k(0.48F, false, 0.50F, -0.36F, -0.78F, 0.90F, 0.22F, -0.38F, 0.38F, 0.0F, 0.92F, 0.70F, 0.10F, 0.30F, -0.30F, 0.0F, 0.0F, 0.6F),
            k(0.62F, true, 0.56F, -0.40F, -0.74F, 0.92F, 0.14F, -0.36F, 0.36F, -0.05F, 0.93F, 0.85F, 0.10F, 0.32F, -0.32F, 0.0F, 0.0F, 0.6F),
            rest(1.0F, 0.0F));
    /** Выпад двумя руками: клинок плашмя в цель. */
    private static final Move THRUST = move(17.0F, 0.40F,
            rest(0.0F, 0.0F),
            k(0.26F, true, 0.26F, -0.42F, -0.50F, -0.16F, 0.30F, -0.94F, -0.90F, -0.38F, 0.03F, 0.45F, -0.05F, 0.25F, -0.20F, 1.0F, 0.0F, 0.4F),
            k(0.33F, true, 0.28F, -0.42F, -0.46F, -0.16F, 0.30F, -0.94F, -0.90F, -0.38F, 0.03F, 0.50F, -0.04F, 0.0F, 0.0F, 1.0F, 0.0F, 0.8F),
            k(0.40F, false, 0.10F, -0.36F, -0.92F, -0.06F, 0.18F, -0.98F, -0.93F, -0.33F, 0.0F, -0.15F, 0.16F, 0.0F, 0.0F, 1.0F, 0.0F, 2.4F),
            k(0.52F, false, 0.06F, -0.34F, -1.02F, -0.06F, 0.16F, -0.98F, -0.96F, -0.26F, 0.02F, -0.25F, 0.26F, -0.70F, 0.50F, 1.0F, 0.0F, 1.4F),
            k(0.64F, true, 0.08F, -0.36F, -0.98F, -0.08F, 0.14F, -0.98F, -0.94F, -0.30F, 0.03F, -0.22F, 0.24F, -0.68F, 0.48F, 1.0F, 0.0F, 1.3F),
            rest(1.0F, 0.0F));
    /** Двуручный удар снизу: от правого бедра вверх налево. */
    private static final Move UPSWING = move(19.0F, 0.44F,
            rest(0.0F, 0.0F),
            k(0.26F, true, 0.50F, -0.46F, -0.72F, 0.78F, -0.12F, -0.60F, -0.50F, 0.55F, -0.66F, 0.60F, 0.25F, 0.10F, -0.30F, 1.0F, 0.0F, 1.2F),
            k(0.34F, true, 0.54F, -0.46F, -0.70F, 0.82F, -0.14F, -0.54F, -0.46F, 0.56F, -0.68F, 0.65F, 0.28F, 0.12F, -0.32F, 1.0F, 0.0F, 1.5F),
            k(0.44F, false, 0.02F, -0.36F, -0.90F, -0.26F, 0.56F, -0.79F, -0.62F, 0.62F, 0.46F, -0.05F, 0.0F, -0.25F, 0.15F, 1.0F, 0.0F, 0.4F),
            k(0.52F, false, -0.40F, -0.16F, -0.78F, -0.62F, 0.72F, -0.30F, -0.62F, -0.20F, 0.75F, -0.60F, -0.12F, -0.40F, 0.25F, 1.0F, 0.0F, -0.2F),
            k(0.66F, true, -0.46F, -0.10F, -0.74F, -0.58F, 0.78F, -0.22F, -0.66F, -0.25F, 0.70F, -0.70F, -0.16F, -0.42F, 0.28F, 0.9F, 0.0F, -0.3F),
            rest(1.0F, 0.0F));
    /** Завершение серии: разворот всем телом с широким взмахом; от первого лица запястье в конце переворачивает клинок на другую сторону. */
    private static final Move WHIRL = move(24.0F, 0.42F,
            rest(0.0F, 0.0F),
            k(0.20F, true, 0.62F, -0.40F, -0.72F, 0.86F, 0.12F, -0.50F, -0.50F, 0.0F, -0.86F, 0.80F, 0.05F, 0.20F, -0.20F, 0.0F, -15.0F, 1.2F),
            k(0.28F, true, 0.66F, -0.38F, -0.70F, 0.88F, 0.12F, -0.46F, -0.46F, 0.0F, -0.89F, 0.85F, 0.05F, 0.22F, -0.22F, 0.0F, -20.0F, 1.5F),
            k(0.42F, false, 0.06F, -0.32F, -0.94F, -0.18F, 0.22F, -0.96F, -0.96F, -0.10F, 0.20F, 0.0F, 0.10F, -0.20F, 0.15F, 0.0F, 140.0F, 1.2F),
            k(0.52F, false, -0.52F, -0.36F, -0.74F, -0.92F, 0.14F, -0.36F, -0.36F, 0.0F, 0.93F, -0.60F, 0.10F, -0.30F, 0.20F, 0.0F, 280.0F, 0.8F),
            k(0.70F, false, -0.10F, -0.60F, -0.64F, -0.20F, 0.30F, -0.93F, 0.90F, 0.35F, -0.10F, -0.30F, 0.05F, -0.10F, 0.10F, 0.0F, 345.0F, 0.2F),
            rest(1.0F, 360.0F));

    /** Доля времени обряда финальной формы по его тику. */
    private static float rite(float tick) {
        return tick / HollowForm.RITE_TICKS;
    }

    /**
     * Обряд финальной формы, весь меч и тело: сжался от злости, молния вбивает его на колено с клинком в землю, он поднимается, большим кругом через сторону
     * забрасывает меч на плечо, держит, сжимается и бьёт выпадом двумя руками. Руки и голову поверх дополняет {@link PlayerPoses}.
     */
    public static final Move RITE = move(HollowForm.RITE_TICKS, rite(HollowForm.RITE_LUNGE),
            rest(0.0F, 0.0F),
            k(rite(18.0F), true, 0.50F, -0.92F, -0.30F, 0.12F, -0.35F, -0.93F, 0.0F, -0.93F, 0.35F, 0.05F, 0.22F, -0.10F, 0.10F, 0.0F, 0.0F, 1.0F),
            k(rite(38.0F), true, 0.56F, -0.86F, -0.22F, 0.20F, -0.62F, -0.76F, 0.0F, -0.77F, 0.63F, -0.06F, -0.10F, -0.12F, 0.12F, 0.0F, 0.0F, 0.6F),
            k(rite(HollowForm.RITE_STRIKE + 4.0F), true, 0.46F, -0.34F, -0.52F, 0.02F, -1.0F, -0.02F, 0.0F, 0.0F, -1.0F, 0.18F, 0.50F, 0.0F, 0.0F, 0.0F, 0.0F, 4.0F),
            k(rite(HollowForm.RITE_KNEEL_END), true, 0.46F, -0.36F, -0.52F, 0.02F, -1.0F, -0.02F, 0.0F, 0.0F, -1.0F, 0.16F, 0.48F, 0.0F, 0.0F, 0.0F, 0.0F, 3.9F),
            k(rite(104.0F), false, 0.52F, -0.62F, -0.48F, 0.20F, -0.96F, -0.20F, 0.0F, -0.2F, -0.98F, 0.10F, 0.25F, 0.0F, 0.0F, 0.0F, 0.0F, 2.0F),
            k(rite(HollowForm.RITE_STAND), true, 0.62F, -0.70F, -0.30F, 0.62F, -0.78F, -0.10F, 0.0F, -0.10F, -0.99F, -0.10F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
            k(rite(127.0F), false, 0.50F, -0.12F, -0.30F, 0.42F, 0.90F, 0.10F, -0.10F, -0.05F, -0.99F, -0.20F, -0.12F, 0.05F, -0.05F, 0.0F, 0.0F, -0.3F),
            k(rite(HollowForm.RITE_SHOULDER), true, 0.50F, -0.31F, -0.42F, 0.12F, 0.42F, 0.90F, -0.05F, 0.82F, -0.38F, -0.22F, -0.06F, 0.10F, -0.10F, 0.0F, 0.0F, 0.3F),
            k(rite(146.0F), true, 0.50F, -0.30F, -0.40F, 0.12F, 0.40F, 0.91F, -0.05F, 0.83F, -0.37F, -0.25F, -0.05F, 0.10F, -0.10F, 0.0F, 0.0F, 0.2F),
            k(rite(151.0F), true, 0.28F, -0.42F, -0.46F, -0.16F, 0.30F, -0.94F, -0.90F, -0.38F, 0.03F, 0.50F, -0.04F, 0.0F, 0.0F, 1.0F, 0.0F, 0.8F),
            k(rite(HollowForm.RITE_LUNGE), false, 0.10F, -0.36F, -0.92F, -0.06F, 0.18F, -0.98F, -0.93F, -0.33F, 0.0F, -0.15F, 0.16F, 0.0F, 0.0F, 1.0F, 0.0F, 2.4F),
            k(rite(160.0F), true, 0.06F, -0.34F, -1.02F, -0.06F, 0.16F, -0.98F, -0.96F, -0.26F, 0.02F, -0.25F, 0.20F, 0.0F, 0.0F, 1.0F, 0.0F, 3.0F),
            k(rite(167.0F), true, 0.08F, -0.36F, -0.98F, -0.08F, 0.14F, -0.98F, -0.94F, -0.30F, 0.03F, -0.22F, 0.18F, 0.0F, 0.0F, 1.0F, 0.0F, 2.9F),
            rest(1.0F, 0.0F));

    /** Доля времени подбора меча по тику сцены: меч в руке только с момента, когда он сжал рукоять. */
    private static float claim(float tick) {
        return (tick - Awakening.CLAIM_GRIP) / (Awakening.CLAIM_TICKS - Awakening.CLAIM_GRIP);
    }

    /**
     * Подбор меча: присел, вторая рука на рукояти, рывком вытащил клинок из земли, большим кругом через сторону забросил на плечо, держит.
     * Первый кадр — только заготовка: меч начинает оттуда, где стоял в земле (см. {@link PlayerPoses}).
     */
    public static final Move CLAIM = move(Awakening.CLAIM_TICKS - Awakening.CLAIM_GRIP, claim(50.0F),
            rest(0.0F, 0.0F),
            k(claim(Awakening.CLAIM_GRIP + 0.5F), true, 0.27F, -0.57F, -0.85F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.05F, 0.20F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3F),
            k(claim(37.0F), true, 0.24F, -0.66F, -0.80F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.02F, 0.32F, 0.05F, -0.05F, 1.0F, 0.0F, 2.0F),
            k(claim(Awakening.CLAIM_CLEAR), false, 0.22F, 0.0F, -0.50F, 0.02F, -1.0F, 0.02F, 0.0F, 0.0F, -1.0F, -0.02F, -0.10F, 0.0F, 0.0F, 1.0F, 0.0F, -0.6F),
            k(claim(54.0F), false, 0.62F, -0.30F, -0.22F, 0.96F, 0.22F, -0.10F, 0.0F, 0.0F, -1.0F, -0.10F, -0.05F, 0.0F, 0.0F, 0.0F, 0.0F, -0.2F),
            k(claim(60.0F), false, 0.50F, -0.06F, -0.30F, 0.42F, 0.90F, 0.10F, -0.10F, -0.05F, -0.99F, -0.20F, -0.12F, 0.05F, -0.05F, 0.0F, 0.0F, -0.3F),
            k(claim(Awakening.CLAIM_SHOULDER), true, 0.50F, -0.31F, -0.42F, 0.12F, 0.42F, 0.90F, -0.05F, 0.82F, -0.38F, -0.22F, -0.06F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3F),
            k(claim(Awakening.CLAIM_HOLD_END), true, 0.50F, -0.30F, -0.40F, 0.12F, 0.40F, 0.91F, -0.05F, 0.83F, -0.37F, -0.18F, -0.04F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1F),
            rest(1.0F, 0.0F));

    public static final Move[] ALL = {KESA, RISING, SWEEP, OVERHEAD, BACKHAND, THRUST, UPSWING, WHIRL, RITE, CLAIM};
    /** Порядок серии: двуручные чередуются с быстрыми, разворот завершает. */
    private static final int[] COMBO = {0, 2, 1, 3, 4, 6, 5, 7};
    /** Какими приёмами меч бьёт сам. */
    private static final int[] AUTO = {1, 2, 4, 0};
    /** Меч, рубящий сам, рвётся быстрее обычного удара. */
    public static final float AUTO_SPEED = 0.8F;

    private SwordMoves() {
    }

    private static Move move(float duration, float hit, float[]... view) {
        float from = 0.0F, to = 1.0F;
        for (float[] key : view) {
            if (key[STOP] > 0.0F && key[T] < hit) {
                from = key[T];
            }
        }
        for (int i = view.length - 1; i >= 0; i--) {
            if (view[i][STOP] > 0.0F && view[i][T] > hit) {
                to = view[i][T];
            }
        }
        return new Move(duration, hit, Math.min(0.92F, CHAIN_TICKS / duration), from, to, view, toBody(view));
    }

    /** Кадры глазами игрока → кадры модели. */
    private static float[][] toBody(float[][] view) {
        float[][] body = new float[view.length][];
        for (int i = 0; i < view.length; i++) {
            float[] key = view[i].clone();
            key[FX] = -view[i][FX] * BODY_SCALE;
            key[FX + 1] = EYE_Y - view[i][FX + 1] * BODY_SCALE;
            key[FX + 2] = view[i][FX + 2] * BODY_SCALE + EYE_Z;
            key[BX] = -view[i][BX];
            key[BX + 1] = -view[i][BX + 1];
            key[EX] = -view[i][EX];
            key[EX + 1] = -view[i][EX + 1];
            body[i] = key;
        }
        return body;
    }

    /** Следующий приём серии. */
    public static int combo(int step) {
        return COMBO[Math.floorMod(step, COMBO.length)];
    }

    public static int auto(int step) {
        return AUTO[Math.floorMod(step, AUTO.length)];
    }

    /** Сила следа клинка в момент {@code t}: разгорается на разгоне, гаснет к концу инерции. */
    public static float trail(Move move, float t) {
        return smooth(move.trailFrom(), move.trailFrom() + 0.05F, t) * (1.0F - smooth(move.trailTo() - 0.1F, move.trailTo(), t));
    }

    public static float smooth(float a, float b, float x) {
        float s = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return s * s * (3.0F - 2.0F * s);
    }

    /**
     * Значения приёма в момент {@code t} (сплайн Эрмита: в кадрах с остановкой скорость нулевая, в остальных движение
     * проходит насквозь). Первый и последний кадр — поза покоя {@code rest}: удар начинается и кончается там, где рука сейчас.
     * Поворот всего тела берётся из самих кадров.
     */
    public static float[] sample(float[][] keys, float t, float[] rest) {
        int last = keys.length - 1;
        int i = 0;
        while (i < last - 1 && t >= keys[i + 1][T]) {
            i++;
        }
        float t0 = keys[i][T], t1 = keys[i + 1][T], dt = t1 - t0;
        float s = MathHelper.clamp((t - t0) / dt, 0.0F, 1.0F);
        float s2 = s * s, s3 = s2 * s;
        float h00 = 2.0F * s3 - 3.0F * s2 + 1.0F, h10 = s3 - 2.0F * s2 + s, h01 = -2.0F * s3 + 3.0F * s2, h11 = s3 - s2;
        float[] out = new float[SIZE];
        out[T] = t;
        for (int c = FX; c < SIZE; c++) {
            float v0 = value(keys, i, c, rest), v1 = value(keys, i + 1, c, rest);
            out[c] = h00 * v0 + h10 * dt * tangent(keys, i, c, rest) + h01 * v1 + h11 * dt * tangent(keys, i + 1, c, rest);
        }
        return out;
    }

    private static float value(float[][] keys, int i, int c, float[] rest) {
        return (i == 0 || i == keys.length - 1) && c != SPIN ? rest[c] : keys[i][c];
    }

    private static float tangent(float[][] keys, int i, int c, float[] rest) {
        if (keys[i][STOP] > 0.0F) {
            return 0.0F;
        }
        return (value(keys, i + 1, c, rest) - value(keys, i - 1, c, rest)) / (keys[i + 1][T] - keys[i - 1][T]);
    }
}
