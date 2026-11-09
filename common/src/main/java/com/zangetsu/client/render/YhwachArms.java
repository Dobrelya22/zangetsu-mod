package com.zangetsu.client.render;

import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Как двигаются руки Яхве. Запястье, кисть и пальцы идут по ключевым кадрам, а локоть считается сам: плечо стоит на месте, длины костей заданы, поэтому рука сгибается и разгибается как настоящая и никогда не тянется резиной.
 * Обе руки начинают во внутреннем кармане плаща, у груди: сначала выходит запястье, кисть тащится за ним, и только в конце раскрываются пальцы.
 *
 * <p>Ключи записаны для левой руки фигуры (x в плюсе), правая получается зеркально. Оси модели: x вбок, y вверх, z вперёд от груди.
 */
final class YhwachArms {
    /** Длины плеча и предплечья, как в модели: локоть в 0.5 от плеча, запястье в 1.1. */
    static final float UPPER = 0.5F, FORE = 0.6F;

    /** Кадр руки: запястье, куда смотрит локоть, куда направлены пальцы, куда смотрит тыл кисти; насколько согнуты пальцы и большой палец, насколько разведены. */
    record Pose(Vector3f wrist, Vector3f pole, Vector3f fingers, Vector3f back, float curl, float spread, float thumb) {
    }

    // Столбцы: момент, запястье x y z, пальцы x y z, тыл кисти x y z, локоть x y z, согнутость, разведённость, большой палец.
    /** Начало одно на все жесты: кисть лежит под плащом на груди, пальцы к середине тела, локоть отведён вниз и наружу. */
    private static final float[] POCKET = {0.0F, 0.30F, 1.20F, 0.12F, -0.80F, -0.55F, 0.10F, 0.12F, 0.02F, 0.99F, 0.55F, -1.0F, -0.25F, 0.50F, 0.45F, 0.60F};

    /** Обе руки перед собой и в стороны, ладонями к зрителю. */
    private static final float[][] RAISE = {
            POCKET,
            {0.16F, 0.31F, 1.24F, 0.24F, -0.86F, -0.30F, 0.40F, 0.40F, 0.10F, 0.91F, 0.55F, -1.0F, -0.25F, 0.55F, 0.45F, 0.60F},
            {0.34F, 0.36F, 1.30F, 0.52F, -0.50F, -0.38F, 0.78F, 0.62F, 0.62F, 0.48F, 0.60F, -0.95F, -0.25F, 0.60F, 0.45F, 0.60F},
            {0.56F, 0.50F, 1.36F, 0.84F, 0.10F, -0.12F, 0.99F, 0.25F, 0.96F, 0.10F, 0.60F, -0.90F, -0.22F, 0.38F, 0.55F, 0.45F},
            {0.80F, 0.82F, 1.32F, 0.90F, 0.36F, 0.48F, 0.80F, 0.20F, 0.80F, -0.56F, 0.48F, -0.95F, -0.15F, 0.12F, 0.85F, 0.15F},
            {1.00F, 0.905F, 1.288F, 0.848F, 0.46F, 0.86F, 0.22F, 0.02F, 0.24F, -0.97F, 0.35F, -1.0F, -0.10F, 0.0F, 1.0F, 0.0F}};

    /** Одна рука вперёд, ладонью вниз и навстречу мечу. Запястье чуть проскакивает вперёд и возвращается, как у живого движения. */
    private static final float[][] REACH = {
            POCKET,
            {0.15F, 0.31F, 1.24F, 0.24F, -0.86F, -0.30F, 0.40F, 0.40F, 0.10F, 0.91F, 0.55F, -1.0F, -0.25F, 0.55F, 0.45F, 0.60F},
            {0.34F, 0.34F, 1.30F, 0.50F, -0.50F, -0.38F, 0.78F, 0.62F, 0.62F, 0.48F, 0.60F, -0.95F, -0.25F, 0.60F, 0.45F, 0.60F},
            {0.60F, 0.30F, 1.30F, 0.74F, 0.06F, -0.20F, 0.98F, 0.05F, 0.98F, 0.20F, 0.60F, -0.90F, -0.20F, 0.40F, 0.55F, 0.45F},
            {0.84F, 0.22F, 1.16F, 1.00F, -0.02F, 0.20F, 0.98F, 0.0F, 0.98F, -0.20F, 0.45F, -1.0F, -0.10F, 0.10F, 0.90F, 0.12F},
            {1.00F, 0.215F, 1.188F, 0.984F, -0.05F, 0.45F, 0.89F, 0.0F, 0.89F, -0.45F, 0.40F, -1.0F, -0.10F, 0.02F, 1.0F, 0.0F}};

    private YhwachArms() {
    }

    static Pose raise(float u, float side) {
        return pose(sample(RAISE, u), side);
    }

    /** Правая рука, которая ловит меч. */
    static Pose reach(float u) {
        return pose(sample(REACH, u), -1.0F);
    }

    private static Pose pose(float[] k, float side) {
        return new Pose(new Vector3f(side * k[1], k[2], k[3]), new Vector3f(side * k[10], k[11], k[12]), new Vector3f(side * k[4], k[5], k[6]), new Vector3f(side * k[7], k[8], k[9]), k[13], k[14], k[15]);
    }

    /** Кривая через ключи, с общей скоростью в каждом ключе: движение идёт дугами и нигде не останавливается рывком. В начале и в конце скорость нулевая. */
    private static float[] sample(float[][] keys, float u) {
        int i = 0;
        while (i + 2 < keys.length && u >= keys[i + 1][0]) {
            i++;
        }
        float[] a = keys[i], b = keys[i + 1];
        float dt = b[0] - a[0];
        float s = MathHelper.clamp((u - a[0]) / dt, 0.0F, 1.0F), s2 = s * s, s3 = s2 * s;
        float[] out = new float[a.length];
        for (int f = 1; f < a.length; f++) {
            out[f] = (2.0F * s3 - 3.0F * s2 + 1.0F) * a[f] + (s3 - 2.0F * s2 + s) * dt * slope(keys, i, f) + (3.0F * s2 - 2.0F * s3) * b[f] + (s3 - s2) * dt * slope(keys, i + 1, f);
        }
        return out;
    }

    private static float slope(float[][] keys, int i, int f) {
        if (i == 0 || i == keys.length - 1) {
            return 0.0F;
        }
        return (keys[i + 1][f] - keys[i - 1][f]) / (keys[i + 1][0] - keys[i - 1][0]);
    }

    /** Локоть: плечо и запястье заданы, длины костей тоже, остаётся одна точка на окружности. Какая именно, говорит {@code pole}. */
    static Vector3f elbow(Vector3f shoulder, Vector3f wrist, Vector3f pole) {
        Vector3f d = new Vector3f(wrist).sub(shoulder);
        float dist = MathHelper.clamp(d.length(), Math.abs(UPPER - FORE) + 0.01F, UPPER + FORE - 0.004F);
        d.normalize();
        float a = (UPPER * UPPER - FORE * FORE + dist * dist) / (2.0F * dist);
        float h = MathHelper.sqrt(Math.max(0.0F, UPPER * UPPER - a * a));
        Vector3f side = new Vector3f(pole).sub(new Vector3f(d).mul(pole.dot(d))).normalize();
        return new Vector3f(shoulder).add(new Vector3f(d).mul(a)).add(side.mul(h));
    }

    /** Плечо в осях модели. В файле рука лежит вдоль -y, а рваный край рукава свисает вдоль -z: он и должен висеть вниз. */
    static Matrix4f upper(Vector3f shoulder, Vector3f elbow) {
        Vector3f d = new Vector3f(elbow).sub(shoulder).normalize();
        Vector3f under = new Vector3f(0.0F, -1.0F, -0.25F);
        under.sub(new Vector3f(d).mul(under.dot(d))).normalize();
        Vector3f y = new Vector3f(d).negate();
        Vector3f z = new Vector3f(under).negate();
        Vector3f x = new Vector3f(y).cross(z);
        return new Matrix4f(x.x, x.y, x.z, 0.0F, y.x, y.y, y.z, 0.0F, z.x, z.y, z.z, 0.0F, shoulder.x, shoulder.y, shoulder.z, 1.0F);
    }

    /** Предплечье: тот же поворот плеча, доведённый до нового направления, поэтому рукав не перекручивается. Локоть файла ложится точно в посчитанный локоть. */
    static Matrix4f fore(Vector3f shoulder, Vector3f elbow, Vector3f wrist) {
        Matrix4f upper = upper(shoulder, elbow);
        Vector3f du = new Vector3f(elbow).sub(shoulder).normalize();
        Vector3f df = new Vector3f(wrist).sub(elbow).normalize();
        Quaternionf turn = new Quaternionf().rotationTo(du, df);
        Vector3f x = upper.getColumn(0, new Vector3f()), y = upper.getColumn(1, new Vector3f()), z = upper.getColumn(2, new Vector3f());
        turn.transform(x);
        turn.transform(y);
        turn.transform(z);
        return new Matrix4f(x.x, x.y, x.z, 0.0F, y.x, y.y, y.z, 0.0F, z.x, z.y, z.z, 0.0F, elbow.x, elbow.y, elbow.z, 1.0F).translate(0.0F, UPPER, 0.0F);
    }

    /** Кисть: запястье в начале, пальцы вдоль -y, тыл вдоль +z. */
    static Matrix4f hand(Vector3f wrist, Vector3f fingers, Vector3f back) {
        Vector3f y = new Vector3f(fingers).negate().normalize();
        Vector3f z = new Vector3f(back);
        z.sub(new Vector3f(y).mul(z.dot(y))).normalize();
        Vector3f x = new Vector3f(y).cross(z);
        return new Matrix4f(x.x, x.y, x.z, 0.0F, y.x, y.y, y.z, 0.0F, z.x, z.y, z.z, 0.0F, wrist.x, wrist.y, wrist.z, 1.0F);
    }
}
