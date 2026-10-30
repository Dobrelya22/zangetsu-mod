package com.zangetsu.client.render;

import com.zangetsu.client.ClientState;
import com.zangetsu.network.AnimationPayload;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Меч глазами игрока: вместо ванильного «тычка» — настоящие взмахи через весь экран. Три удара по очереди
 * (диагональ сверху справа, восходящий снизу слева, горизонтальный), рывок, когда меч рубит сам, и Гецуга:
 * клинок уходит высоко за правое плечо, замирает — и проносится по горизонтали.
 *
 * <p>Ключевые кадры — сдвиг и повороты руки с мечом в пространстве камеры (x — вправо, y — вверх, z — к нам),
 * вокруг кулака; между кадрами — плавный переход.
 */
public final class FirstPersonMotion {
    /** Ключевой кадр: доля анимации, сдвиг (блоки) и повороты (градусы) вокруг X, Y, Z. */
    private record Key(float t, float tx, float ty, float tz, float rx, float ry, float rz) {
    }

    private static final Key REST = new Key(0, 0, 0, 0, 0, 0, 0);
    /*
     * Кулак — у нижнего края экрана и там остаётся (рукоять за кадром), клинок проносится дугой через верх экрана
     * поворотом вокруг кулака (Z плюс — против часовой); в покое он смотрит вверх-влево (≈124° от «вправо»),
     * плоскостью к нам. Горизонтальный удар в середине проходит остриём «в экран».
     */
    private static final Key[][] CLEAVES = {
            // Диагональ: справа сверху — влево вниз.
            {REST, new Key(0.2F, 0.12F, 0.08F, 0.05F, -20, 0, -70), new Key(0.45F, -0.45F, 0.05F, -0.1F, 15, 0, 75),
                    new Key(0.62F, -0.48F, 0.0F, -0.08F, 15, 0, 78), new Key(1.0F, 0, 0, 0, 0, 0, 0)},
            // Восходящий: слева снизу — вправо вверх.
            {REST, new Key(0.2F, -0.4F, 0.0F, 0.0F, 10, 0, 80), new Key(0.45F, 0.12F, 0.1F, -0.1F, -15, 0, -72),
                    new Key(0.62F, 0.14F, 0.12F, -0.08F, -15, 0, -75), new Key(1.0F, 0, 0, 0, 0, 0, 0)},
            // Горизонтальный: справа налево, в середине — остриём в экран.
            {REST, new Key(0.2F, 0.15F, 0.1F, 0.05F, 0, 30, -110), new Key(0.32F, -0.15F, 0.12F, -0.12F, -60, 0, -34),
                    new Key(0.45F, -0.45F, 0.1F, -0.08F, 0, -30, 60), new Key(0.62F, -0.48F, 0.08F, -0.06F, 0, -30, 62),
                    new Key(1.0F, 0, 0, 0, 0, 0, 0)},
    };
    private static final Key[] AUTO = {REST, new Key(0.25F, 0.12F, 0.08F, 0.05F, -20, 0, -70), new Key(0.42F, -0.45F, 0.05F, -0.28F, 15, 0, 75),
            new Key(0.65F, -0.48F, 0.0F, -0.22F, 15, 0, 78), new Key(1.0F, 0, 0, 0, 0, 0, 0)};
    private static final Key[] GETSUGA = {REST, new Key(0.33F, 0.12F, 0.25F, 0.1F, -20, 0, -25), new Key(0.37F, 0.13F, 0.26F, 0.11F, -21, 0, -26),
            new Key(0.42F, -0.15F, 0.12F, -0.15F, -60, 0, -34), new Key(0.5F, -0.48F, 0.1F, -0.12F, 0, -30, 62),
            new Key(0.62F, -0.5F, 0.08F, -0.08F, 0, -30, 64), new Key(1.0F, 0, 0, 0, 0, 0, 0)};

    private FirstPersonMotion() {
    }

    /** Сдвинуть руку с мечом по текущей анимации; {@code side} — 1 правая рука, −1 левая (зеркально). */
    public static void apply(MatrixStack matrices, PlayerEntity player, int side, float swingProgress, float tickDelta) {
        Key key = REST;
        ClientState.Animation animation = ClientState.animation(player);
        if (animation != null && animation.type() == AnimationPayload.GETSUGA) {
            key = sample(GETSUGA, animation.progress(player, tickDelta));
        } else if (animation != null && animation.type() == AnimationPayload.AUTO_SLASH) {
            key = sample(AUTO, animation.progress(player, tickDelta));
        } else if (swingProgress > 0.0F) {
            key = sample(CLEAVES[ClientState.swingVariant(player)], swingProgress);
        }
        if (key == REST) {
            return;
        }
        matrices.translate(side * key.tx(), key.ty(), key.tz());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * key.ry()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(key.rx()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * key.rz()));
    }

    private static Key sample(Key[] keys, float t) {
        t = MathHelper.clamp(t, 0.0F, 1.0F);
        for (int i = 0; i + 1 < keys.length; i++) {
            Key a = keys[i], b = keys[i + 1];
            if (t <= b.t()) {
                float k = (t - a.t()) / Math.max(1.0E-4F, b.t() - a.t());
                k = k * k * (3.0F - 2.0F * k);
                return new Key(t, MathHelper.lerp(k, a.tx(), b.tx()), MathHelper.lerp(k, a.ty(), b.ty()), MathHelper.lerp(k, a.tz(), b.tz()),
                        MathHelper.lerp(k, a.rx(), b.rx()), MathHelper.lerp(k, a.ry(), b.ry()), MathHelper.lerp(k, a.rz(), b.rz()));
            }
        }
        return REST;
    }
}
