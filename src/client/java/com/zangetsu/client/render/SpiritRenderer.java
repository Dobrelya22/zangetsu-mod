package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.logic.Eye;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Фигура в небе. Косматая чернильная масса, вся из объёма: несколько шевелящихся комьев, сотни острых клочьев и волос, длинные щупальца снизу. На ней овальные глаза: у каждого свой
 * размер, наклон, взгляд и свои моргания. Масса выплёскивается из ничего и так же втягивается обратно, поэтому прозрачность ей не нужна.
 */
public class SpiritRenderer extends EntityRenderer<ZangetsuSpiritEntity> {
    private static final Identifier WHITE = ZangetsuMod.id("textures/misc/white.png");
    private static final Identifier SCLERA = ZangetsuMod.id("textures/entity/spirit_eye.png");
    private static final Identifier PUPIL = ZangetsuMod.id("textures/entity/spirit_pupil.png");
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    /** Комья массы: центр, радиусы, сдвиг волны. Края нарочно торчат неровно: это не шар. */
    private static final float[][] LOBES = {
            {0.0F, 0.0F, 0.0F, 6.3F, 6.7F, 4.6F, 0.0F},
            {2.8F, 2.9F, -0.4F, 4.5F, 4.1F, 3.7F, 1.3F},
            {-3.5F, 0.7F, -0.5F, 4.3F, 4.8F, 3.7F, 2.4F},
            {0.7F, -3.8F, -0.4F, 4.8F, 4.1F, 3.5F, 3.7F},
            {4.1F, -1.9F, -0.8F, 3.3F, 3.5F, 2.9F, 4.6F},
            {-2.0F, 4.0F, -1.0F, 3.5F, 3.3F, 2.9F, 5.5F},
            {-6.6F, -2.4F, -1.4F, 3.1F, 2.6F, 2.4F, 0.7F},
            {6.1F, 3.2F, -1.6F, 3.3F, 2.5F, 2.4F, 2.0F},
            {1.2F, 6.6F, -1.4F, 3.4F, 2.4F, 2.4F, 3.1F},
            {-2.8F, -6.3F, -1.2F, 3.1F, 3.0F, 2.4F, 4.2F},
    };
    private static final int LAT = 14, LON = 24;

    /** На сколько градусов вся масса наклонена вперёд: она висит высоко и смотрит на игрока сверху вниз. */
    private static final float TILT = 34.0F;
    private static final float TILT_SIN = MathHelper.sin(TILT * MathHelper.RADIANS_PER_DEGREE), TILT_COS = MathHelper.cos(TILT * MathHelper.RADIANS_PER_DEGREE);

    /** Глаза: x и y на теле (доли), размер, вытянутость по высоте, наклон в градусах. Самый большой закрывается последним. */
    private static final float[][] EYE_LAYOUT = {
            {0.52F, 0.56F, 1.08F, 0.60F, -13.0F},
            {-0.10F, -0.06F, 0.84F, 1.30F, 3.0F},
            {-0.66F, 0.46F, 0.64F, 0.68F, 19.0F},
            {0.60F, -0.50F, 0.74F, 0.66F, 11.0F},
            {-0.72F, -0.30F, 0.48F, 0.74F, -16.0F},
            {0.97F, 0.74F, 0.42F, 0.70F, 21.0F},
            {0.62F, 1.04F, 0.34F, 0.64F, -27.0F},
            {0.02F, -0.84F, 0.40F, 0.72F, -8.0F},
            {-1.04F, 0.12F, 0.30F, 0.82F, 31.0F},
            {-0.26F, 0.96F, 0.32F, 0.70F, -11.0F},
            {-0.44F, -0.72F, 0.27F, 0.76F, 14.0F},
    };
    private static final float EYE_WIDTH = 2.3F;
    private static final int EYE_RINGS = 8, EYE_SIDES = 18;
    /** Клочья, которые отрываются от массы и уносятся вверх. */
    private static final int WISPS = 44;

    private static final class Spike {
        float rx, ry, rz, dx, dy, dz, ax, ay, az, bx, by, bz;
        float length, thick, sag, sway, frequency, phase, delay, tone, curl, kink;
        int segments;
        /** Шерсть и лоскуты: плоская лента, всегда повёрнутая к камере. Толстые клочья объёмные. */
        boolean ribbon;
        /** Откуда растёт: ком и точка на нём. Корень ходит вместе с поверхностью кома. */
        int lobe;
        float nx, ny, nz, theta, phi, rootAt;
    }

    private static final class EyeShape {
        float x, y, z;
        float rx, ry, rz, ux, uy, uz, nx, ny, nz;
        float width, height, depth;
        float gazePeriod, gazeShift, blinkPeriod, blinkShift;
        int closeOrder;
    }

    private static final Spike[] SPIKES = buildSpikes();
    private static final EyeShape[] EYES = buildEyes();

    private static final float[] SIN3 = {0.0F, 0.8660254F, -0.8660254F};
    private static final float[] COS3 = {1.0F, -0.5F, -0.5F};

    private final float[] grid = new float[(LAT + 1) * (LON + 1) * 4];
    private final float[] section = new float[9 * 8];
    private float spineX, spineY, spineZ;
    private final ItemRenderer itemRenderer;
    private final ItemStack sword = new ItemStack(ModItems.ZANGETSU);

    public SpiritRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.sword.set(ModComponents.EYE, Eye.OPEN);
    }

    // ---------------------------------------------------------------- заготовки

    private static Spike[] buildSpikes() {
        Random random = new Random(1987L);
        List<Spike> spikes = new ArrayList<>();
        // Вниз свисают длинные рваные пряди: они колышутся медленнее всего.
        for (int i = 0; i < 46; i++) {
            float a = random.nextFloat() * TWO_PI, spread = 0.2F + random.nextFloat() * 0.6F;
            Spike spike = null;
            while (spike == null) {
                spike = spike(random, MathHelper.cos(a) * spread, -TILT_COS, MathHelper.sin(a) * spread + TILT_SIN, 5.0F + random.nextFloat() * 6.5F, 0.32F + random.nextFloat() * 0.45F, 6, 0.0F, false, false);
            }
            spike.sway = 0.1F + random.nextFloat() * 0.08F;
            spike.frequency = 0.03F + random.nextFloat() * 0.025F;
            spike.sag = 0.04F;
            spikes.add(spike);
        }
        // Большие рваные клочья во все стороны.
        for (int i = 0; i < 74; i++) {
            spikes.add(radial(random, 4.0F + random.nextFloat() * 6.0F, 0.45F + random.nextFloat() * 0.5F, 5, 0.1F, false));
        }
        // Лоскуты: широкие у основания и сметённые вниз, как косматая шкура.
        for (int i = 0; i < 620; i++) {
            spikes.add(radial(random, 2.0F + random.nextFloat() * 3.4F, 0.18F + random.nextFloat() * 0.34F, 4, 0.34F, true));
        }
        // Шерсть: именно она делает край мохнатым.
        for (int i = 0; i < 1250; i++) {
            spikes.add(radial(random, 1.0F + random.nextFloat() * 2.5F, 0.035F + random.nextFloat() * 0.085F, 3, 0.3F, true));
        }
        // Шерсть на самом теле между глазами: лежит по поверхности и стекает вниз, поэтому спереди масса не гладкая.
        for (int i = 0; i < 620; i++) {
            Spike fur = null;
            while (fur == null) {
                float x = random.nextFloat() * 2.0F - 1.0F, y = random.nextFloat() * 2.0F - 1.0F, z = 0.25F + random.nextFloat() * 0.75F;
                if (x * x + y * y + z * z <= 1.0F) {
                    fur = spike(random, x, y, z, 1.2F + random.nextFloat() * 2.2F, 0.05F + random.nextFloat() * 0.12F, 3, 0.0F, true, true);
                }
            }
            spikes.add(fur);
        }
        return spikes.toArray(new Spike[0]);
    }

    private static Spike radial(Random random, float length, float thick, int segments, float sweep, boolean ribbon) {
        while (true) {
            float x = random.nextFloat() * 2.0F - 1.0F, y = random.nextFloat() * 2.0F - 1.0F, z = random.nextFloat() * 2.0F - 1.0F;
            float l = x * x + y * y + z * z;
            if (l < 0.04F || l > 1.0F) {
                continue;
            }
            Spike spike = spike(random, x, y, z, length, thick, segments, sweep, ribbon, false);
            if (spike != null) {
                return spike;
            }
        }
    }

    /** Точка глубоко внутри другого кома: оттуда клочок бы не вышел наружу. */
    private static boolean buried(float x, float y, float z, float[] own, float limit) {
        for (float[] lobe : LOBES) {
            if (lobe != own) {
                float dx = (x - lobe[0]) / lobe[3], dy = (y - lobe[1]) / lobe[4], dz = (z - lobe[2]) / lobe[5];
                if (dx * dx + dy * dy + dz * dz < limit) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Один клочок из точки на поверхности случайного кома в сторону (x, y, z). Спереди не растёт там, где глаз. {@code lying} — шерсть, лежащая по телу. */
    private static Spike spike(Random random, float x, float y, float z, float length, float thick, int segments, float sweep, boolean ribbon, boolean lying) {
        int index = random.nextInt(LOBES.length);
        float[] lobe = LOBES[index];
        float l = MathHelper.sqrt(x * x + y * y + z * z);
        x /= l;
        y /= l;
        z /= l;
        Spike s = new Spike();
        s.lobe = index;
        s.nx = x;
        s.ny = y;
        s.nz = z;
        s.theta = (float) Math.acos(MathHelper.clamp(y, -1.0F, 1.0F));
        s.phi = (float) Math.atan2(z, x);
        s.rootAt = lying ? 1.0F : 0.9F;
        float rootX = lobe[0] + x * lobe[3] * s.rootAt, rootY = lobe[1] + y * lobe[4] * s.rootAt, rootZ = lobe[2] + z * lobe[5] * s.rootAt;
        if (buried(rootX, rootY, rootZ, lobe, lying ? 1.1F : 0.62F)) {
            return null;
        }
        if (rootZ > 0.5F) {
            for (float[] eye : EYE_LAYOUT) {
                float ex = rootX - eye[0] * 6.0F, ey = rootY - eye[1] * 6.3F;
                float size = eye[2] * EYE_WIDTH * Math.max(1.0F, eye[3]);
                float reach = lying ? size * 1.15F + 0.35F + length * 0.3F : size * 1.3F + 0.8F;
                if (ex * ex + ey * ey < reach * reach) {
                    return null;
                }
            }
        }
        // Чуть врозь с нормалью, вниз и немного по кругу: не ровный ёж, а шкура.
        float jitter = 0.4F;
        float dx, dy, dz;
        if (lying) {
            dx = x * 0.9F + (random.nextFloat() - 0.5F) * jitter;
            dy = y * 0.9F - 0.4F + (random.nextFloat() - 0.5F) * jitter;
            dz = z * 0.2F;
        } else {
            dx = x + z * sweep + (random.nextFloat() - 0.5F) * jitter;
            dy = y - TILT_COS * sweep * 0.8F + (random.nextFloat() - 0.5F) * jitter;
            dz = z - x * sweep + TILT_SIN * sweep * 0.8F + (random.nextFloat() - 0.5F) * jitter;
        }
        float d = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        s.dx = dx / d;
        s.dy = dy / d;
        s.dz = dz / d;
        // Две оси сечения.
        float ax = s.dy, ay = -s.dx, az = 0.0F;
        if (ax * ax + ay * ay < 0.01F) {
            ax = 1.0F;
            ay = 0.0F;
        }
        float al = MathHelper.sqrt(ax * ax + ay * ay + az * az);
        s.ax = ax / al;
        s.ay = ay / al;
        s.az = az / al;
        s.bx = s.dy * s.az - s.dz * s.ay;
        s.by = s.dz * s.ax - s.dx * s.az;
        s.bz = s.dx * s.ay - s.dy * s.ax;
        s.length = length;
        s.thick = thick;
        s.segments = segments;
        s.ribbon = ribbon;
        s.sag = lying ? 0.02F : 0.1F + random.nextFloat() * 0.3F;
        s.sway = lying ? 0.03F + random.nextFloat() * 0.04F : 0.04F + random.nextFloat() * 0.08F;
        s.frequency = 0.04F + random.nextFloat() * 0.07F;
        s.phase = random.nextFloat() * TWO_PI;
        s.delay = random.nextFloat() * 0.5F;
        s.tone = (lying ? 12.0F : 4.0F) + random.nextFloat() * 10.0F;
        s.curl = (random.nextFloat() - 0.5F) * 0.5F;
        // Излом посередине: край получается рваным, а не гладким.
        s.kink = lying ? 0.0F : (random.nextFloat() - 0.5F) * 0.5F;
        return s;
    }

    /** Как дышит поверхность кома. */
    private static float wave(float phase, float theta, float phi, float time) {
        return 1.0F + 0.17F * MathHelper.sin(3.0F * phi + 2.0F * theta + time * 0.04F + phase)
                + 0.1F * MathHelper.sin(5.0F * theta - 2.0F * phi - time * 0.055F + phase * 2.0F)
                + 0.045F * MathHelper.sin(9.0F * phi + 7.0F * theta + time * 0.09F + phase * 3.0F);
    }

    private static EyeShape[] buildEyes() {
        EyeShape[] eyes = new EyeShape[EYE_LAYOUT.length];
        Random random = new Random(777L);
        for (int i = 0; i < eyes.length; i++) {
            float[] layout = EYE_LAYOUT[i];
            EyeShape e = new EyeShape();
            float ex = layout[0], ey = layout[1];
            e.x = ex * 6.0F;
            e.y = ey * 6.3F;
            e.z = 4.5F * MathHelper.sqrt(Math.max(0.09F, 1.0F - (ex * ex + ey * ey) * 0.8F)) + 0.75F;
            float nx = ex * 0.85F, ny = ey * 0.85F, nz = 1.0F;
            float nl = MathHelper.sqrt(nx * nx + ny * ny + nz * nz);
            e.nx = nx / nl;
            e.ny = ny / nl;
            e.nz = nz / nl;
            // Правая ось (вид снаружи): вверх × нормаль.
            float rx = e.nz, rz = -e.nx;
            float rl = MathHelper.sqrt(rx * rx + rz * rz);
            rx /= rl;
            rz /= rl;
            float ux = e.ny * rz, uy = e.nz * rx - e.nx * rz, uz = -e.ny * rx;
            float tilt = layout[4] * MathHelper.RADIANS_PER_DEGREE;
            float c = MathHelper.cos(tilt), s = MathHelper.sin(tilt);
            e.rx = rx * c + ux * s;
            e.ry = uy * s;
            e.rz = rz * c + uz * s;
            e.ux = -rx * s + ux * c;
            e.uy = uy * c;
            e.uz = -rz * s + uz * c;
            e.width = layout[2] * EYE_WIDTH;
            e.height = e.width * layout[3];
            e.depth = e.width * 0.55F;
            e.gazePeriod = 26.0F + random.nextFloat() * 46.0F;
            e.gazeShift = random.nextFloat() * 200.0F;
            e.blinkPeriod = 50.0F + random.nextFloat() * 74.0F;
            e.blinkShift = random.nextFloat() * 200.0F;
            // Большие глаза закрываются последними.
            e.closeOrder = eyes.length - 1 - i;
            eyes[i] = e;
        }
        return eyes;
    }

    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216.0F;
    }

    private static float ease(float t) {
        t = MathHelper.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    // ---------------------------------------------------------------- кадр

    @Override
    public void render(ZangetsuSpiritEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float presence = entity.presence(tickDelta);
        if (presence <= 0.002F) {
            return;
        }
        float time = entity.age + tickDelta;
        float scale = entity.getSpiritScale();
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw());
        float bob = MathHelper.sin(time * 0.031F) * 0.45F;
        // Волна после броска: все клочья вскидываются разом.
        float sinceFlare = time - entity.flareAt;
        float flare = sinceFlare < 0.0F || sinceFlare > 26.0F ? 0.0F : (sinceFlare < 3.0F ? sinceFlare / 3.0F : 1.0F - (sinceFlare - 3.0F) / 23.0F);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        matrices.scale(scale, scale, scale);
        matrices.translate(0.0F, bob, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(TILT));

        // Камера в осях фигуры: отсюда и контур, и взгляд на игрока.
        Vec3d rel = this.dispatcher.camera.getPos().subtract(entity.getLerpedPos(tickDelta));
        float rad = bodyYaw * MathHelper.RADIANS_PER_DEGREE, cos = MathHelper.cos(rad), sin = MathHelper.sin(rad);
        float flatY = (float) rel.y / scale - bob, flatZ = (float) (-rel.x * sin + rel.z * cos) / scale;
        float camX = (float) (rel.x * cos + rel.z * sin) / scale, camY = flatY * TILT_COS + flatZ * TILT_SIN, camZ = flatZ * TILT_COS - flatY * TILT_SIN;
        float camLength = MathHelper.sqrt(camX * camX + camY * camY + camZ * camZ);
        float vx = camX / camLength, vy = camY / camLength, vz = camZ / camLength;

        VertexConsumer mass = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));
        MatrixStack.Entry entry = matrices.peek();
        float pop = ease(presence * 1.5F);
        for (float[] lobe : LOBES) {
            this.lobe(mass, entry, lobe, time, pop * (1.0F + flare * 0.06F), vx, vy, vz);
        }
        for (Spike spike : SPIKES) {
            this.spike(mass, entry, spike, time, presence, flare, pop, vx, vy, vz);
        }
        this.wisps(mass, entry, time, presence, vx, vy, vz);
        float[] openness = new float[EYES.length];
        for (int i = 0; i < EYES.length; i++) {
            openness[i] = this.openness(entity, i, EYES[i], time);
            this.socket(mass, entry, EYES[i], pop, vx, vy, vz);
        }

        this.holdingSword(entity, tickDelta, matrices, consumers);

        VertexConsumer sclera = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(SCLERA));
        entry = matrices.peek();
        float wrath = entity.getPhase() == ZangetsuSpiritEntity.WRATH || entity.getPhase() == ZangetsuSpiritEntity.CLOSING ? 1.0F : 0.0F;
        for (int i = 0; i < EYES.length; i++) {
            if (openness[i] > 0.015F) {
                this.eyeball(sclera, entry, EYES[i], openness[i], wrath);
            }
        }
        VertexConsumer pupil = consumers.getBuffer(RenderLayer.getEntityTranslucent(PUPIL));
        for (int i = 0; i < EYES.length; i++) {
            if (openness[i] > 0.1F) {
                this.pupil(pupil, entry, entity, i, EYES[i], openness[i], tickDelta, time, camX, camY, camZ);
            }
        }
        matrices.pop();
    }

    private static void put(VertexConsumer out, MatrixStack.Entry entry, Matrix4f matrix, float x, float y, float z, int r, int g, int b, int a, float u, float v) {
        out.vertex(matrix, x, y, z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(entry, 0.0F, 1.0F, 0.0F);
    }

    /** Цвет чёрной массы: почти чёрный, а на контуре и сверху чуть светлее, поэтому виден объём. */
    private static void massVertex(VertexConsumer out, MatrixStack.Entry entry, Matrix4f matrix, float x, float y, float z, float tone) {
        int r = Math.min(255, (int) (tone * 0.92F)), g = Math.min(255, (int) (tone * 0.95F)), b = Math.min(255, (int) (tone * 1.16F));
        put(out, entry, matrix, x, y, z, r, g, b, 255, 0.5F, 0.5F);
    }

    private void lobe(VertexConsumer out, MatrixStack.Entry entry, float[] lobe, float time, float pop, float vx, float vy, float vz) {
        float[] grid = this.grid;
        float phase = lobe[6];
        int index = 0;
        for (int i = 0; i <= LAT; i++) {
            float theta = (float) Math.PI * i / LAT;
            float st = MathHelper.sin(theta), ct = MathHelper.cos(theta);
            for (int j = 0; j <= LON; j++) {
                float phi = TWO_PI * j / LON;
                float nx = st * MathHelper.cos(phi), ny = ct, nz = st * MathHelper.sin(phi);
                float wave = wave(phase, theta, phi, time) * pop;
                grid[index] = lobe[0] + nx * lobe[3] * wave;
                grid[index + 1] = lobe[1] + ny * lobe[4] * wave;
                grid[index + 2] = lobe[2] + nz * lobe[5] * wave;
                float rim = 1.0F - Math.abs(nx * vx + ny * vy + nz * vz);
                grid[index + 3] = 4.0F + 20.0F * rim * rim + 5.0F * Math.max(0.0F, ny);
                index += 4;
            }
        }
        Matrix4f matrix = entry.getPositionMatrix();
        int row = (LON + 1) * 4;
        for (int i = 0; i < LAT; i++) {
            for (int j = 0; j < LON; j++) {
                int a = (i * (LON + 1) + j) * 4, b = a + 4, c = a + row, d = c + 4;
                massVertex(out, entry, matrix, grid[a], grid[a + 1], grid[a + 2], grid[a + 3]);
                massVertex(out, entry, matrix, grid[b], grid[b + 1], grid[b + 2], grid[b + 3]);
                massVertex(out, entry, matrix, grid[d], grid[d + 1], grid[d + 2], grid[d + 3]);
                massVertex(out, entry, matrix, grid[c], grid[c + 1], grid[c + 2], grid[c + 3]);
            }
        }
    }

    /** Точка на оси клочка: волна сильнее к концу, излом посередине, сверху всё немного провисает. */
    private void spine(Spike s, float u, float length, float time) {
        float l = length * u, along = length * u * u;
        float bent = u > 0.5F ? (u - 0.5F) * 2.0F * s.kink * length : 0.0F;
        float w1 = MathHelper.sin(time * s.frequency + s.phase + u * 2.6F) * s.sway * along + s.curl * along * 0.5F + bent;
        float w2 = MathHelper.cos(time * s.frequency * 0.71F + s.phase * 1.7F + u * 3.3F) * s.sway * along;
        this.spineX = s.rx + s.dx * l + s.ax * w1 + s.bx * w2;
        this.spineY = s.ry + s.dy * l + s.ay * w1 + s.by * w2 - s.sag * along * TILT_COS;
        this.spineZ = s.rz + s.dz * l + s.az * w1 + s.bz * w2 + s.sag * along * TILT_SIN;
    }

    private void spike(VertexConsumer out, MatrixStack.Entry entry, Spike s, float time, float presence, float flare, float pop, float vx, float vy, float vz) {
        float grown = ease((presence - s.delay) / (1.0F - s.delay));
        if (grown <= 0.01F) {
            return;
        }
        float[] lobe = LOBES[s.lobe];
        float surface = wave(lobe[6], s.theta, s.phi, time) * pop * s.rootAt;
        s.rx = lobe[0] + s.nx * lobe[3] * surface;
        s.ry = lobe[1] + s.ny * lobe[4] * surface;
        s.rz = lobe[2] + s.nz * lobe[5] * surface;
        float breathe = 1.0F + 0.06F * MathHelper.sin(time * 0.07F + s.phase);
        float length = s.length * grown * breathe * (1.0F + flare * 0.4F);
        float thick = s.thick * (0.5F + 0.5F * grown);
        if (s.ribbon) {
            this.ribbon(out, entry, s, time, length, thick, vx, vy, vz);
            return;
        }
        float[] p = this.section;
        int segments = s.segments;
        for (int k = 0; k <= segments; k++) {
            float u = (float) k / segments;
            this.spine(s, u, length, time);
            float left = 1.0F - u;
            float radius = thick * left * (0.35F + 0.65F * left);
            for (int j = 0; j < 3; j++) {
                float c = COS3[j] * radius, q = SIN3[j] * radius;
                int at = (k * 3 + j) * 3;
                p[at] = this.spineX + s.ax * c + s.bx * q;
                p[at + 1] = this.spineY + s.ay * c + s.by * q;
                p[at + 2] = this.spineZ + s.az * c + s.bz * q;
            }
        }
        Matrix4f matrix = entry.getPositionMatrix();
        for (int j = 0; j < 3; j++) {
            int n = (j + 1) % 3;
            // Освещение грани: контур и верх светлее, к концу клочка чуть светлее.
            float nx = s.ax * COS3[j] + s.bx * SIN3[j], ny = s.ay * COS3[j] + s.by * SIN3[j], nz = s.az * COS3[j] + s.bz * SIN3[j];
            float rim = 1.0F - Math.abs(nx * vx + ny * vy + nz * vz);
            float tone = s.tone + 24.0F * rim * rim + 7.0F * Math.max(0.0F, ny);
            for (int k = 0; k < segments; k++) {
                float bottom = tone + 12.0F * k / segments, top = tone + 12.0F * (k + 1) / segments;
                int a = (k * 3 + j) * 3, b = (k * 3 + n) * 3, c = ((k + 1) * 3 + n) * 3, d = ((k + 1) * 3 + j) * 3;
                massVertex(out, entry, matrix, p[a], p[a + 1], p[a + 2], bottom);
                massVertex(out, entry, matrix, p[b], p[b + 1], p[b + 2], bottom);
                massVertex(out, entry, matrix, p[c], p[c + 1], p[c + 2], top);
                massVertex(out, entry, matrix, p[d], p[d + 1], p[d + 2], top);
            }
        }
    }

    /** Шерстинка или лоскут: всегда ребром не к камере, поэтому край виден косматым с любой стороны. */
    private void ribbon(VertexConsumer out, MatrixStack.Entry entry, Spike s, float time, float length, float thick, float vx, float vy, float vz) {
        float sx = s.dy * vz - s.dz * vy, sy = s.dz * vx - s.dx * vz, sz = s.dx * vy - s.dy * vx;
        float side = MathHelper.sqrt(sx * sx + sy * sy + sz * sz);
        if (side < 0.05F) {
            sx = s.ax;
            sy = s.ay;
            sz = s.az;
        } else {
            sx /= side;
            sy /= side;
            sz /= side;
        }
        float tone = s.tone + 18.0F * (1.0F - Math.min(1.0F, side)) * 0.2F + 16.0F * side * side;
        Matrix4f matrix = entry.getPositionMatrix();
        int segments = s.segments;
        this.spine(s, 0.0F, length, time);
        float px = this.spineX, py = this.spineY, pz = this.spineZ, pr = thick;
        for (int k = 1; k <= segments; k++) {
            float u = (float) k / segments;
            this.spine(s, u, length, time);
            float left = 1.0F - u;
            float r = thick * left * (0.35F + 0.65F * left);
            float bottom = tone + 11.0F * (k - 1) / segments, top = tone + 11.0F * k / segments;
            massVertex(out, entry, matrix, px - sx * pr, py - sy * pr, pz - sz * pr, bottom);
            massVertex(out, entry, matrix, px + sx * pr, py + sy * pr, pz + sz * pr, bottom);
            massVertex(out, entry, matrix, this.spineX + sx * r, this.spineY + sy * r, this.spineZ + sz * r, top);
            massVertex(out, entry, matrix, this.spineX - sx * r, this.spineY - sy * r, this.spineZ - sz * r, top);
            px = this.spineX;
            py = this.spineY;
            pz = this.spineZ;
            pr = r;
        }
    }

    /** С массы всё время срываются чернильные клочья и уносятся вверх, тая на лету. */
    private void wisps(VertexConsumer out, MatrixStack.Entry entry, float time, float presence, float vx, float vy, float vz) {
        if (presence < 0.3F) {
            return;
        }
        Matrix4f matrix = entry.getPositionMatrix();
        for (int i = 0; i < WISPS; i++) {
            float period = 70.0F + hash(i, 1) * 80.0F;
            float t = (time + hash(i, 2) * 500.0F) / period;
            int life = MathHelper.floor(t);
            t -= life;
            // Вверх мира в осях наклонённой массы.
            float a = hash(i, life + 3) * TWO_PI, back = (hash(i, life + 4) - 0.8F) * 0.8F;
            float nl = MathHelper.sqrt(1.0F + back * back);
            float nx = MathHelper.cos(a) / nl, ny = MathHelper.sin(a) / nl, nz = back / nl;
            float distance = (7.0F + hash(i, 5) * 3.0F + t * 4.5F) * presence;
            float rise = t * t * 7.0F;
            float x = nx * distance + MathHelper.sin(t * 5.0F + i) * 0.7F;
            float y = ny * distance + TILT_COS * rise;
            float z = nz * distance - TILT_SIN * rise;
            float size = (0.08F + hash(i, 6) * 0.17F) * MathHelper.sin(t * MathHelper.PI);
            if (size < 0.02F) {
                continue;
            }
            // Рваный лоскуток лицом к камере.
            float sx = vz, sy = 0.0F, sz = -vx;
            float side = MathHelper.sqrt(sx * sx + sz * sz);
            sx /= side;
            sz /= side;
            float ux = vy * sz, uy = vz * sx - vx * sz, uz = -vy * sx;
            float tall = size * 4.2F, spin = MathHelper.sin(time * 0.1F + i) * 0.6F;
            float tx = (ux + sx * spin) * tall, ty = (uy + sy * spin) * tall, tz = (uz + sz * spin) * tall;
            massVertex(out, entry, matrix, x - sx * size, y - sy * size, z - sz * size, 9.0F);
            massVertex(out, entry, matrix, x + sx * size, y + sy * size, z + sz * size, 9.0F);
            massVertex(out, entry, matrix, x + tx, y + ty, z + tz, 14.0F);
            massVertex(out, entry, matrix, x - tx * 0.7F, y - ty * 0.7F, z - tz * 0.7F, 7.0F);
        }
    }

    /** Насколько открыт глаз: открывается по очереди, живёт своими морганиями, на прощание закрывается навсегда. */
    private float openness(ZangetsuSpiritEntity entity, int index, EyeShape eye, float time) {
        float open = ease((time - ZangetsuSpiritEntity.EYES_AT - index * ZangetsuSpiritEntity.EYE_OPEN_STEP) / 7.0F);
        int phase = entity.getPhase();
        if (phase == ZangetsuSpiritEntity.DISSOLVING) {
            return 0.0F;
        }
        if (phase == ZangetsuSpiritEntity.CLOSING) {
            open *= 1.0F - ease((time - entity.phaseAt - eye.closeOrder * ZangetsuSpiritEntity.EYE_CLOSE_STEP) / 7.0F);
        }
        float t = time + eye.blinkShift;
        int cycle = MathHelper.floor(t / eye.blinkPeriod);
        float local = t - cycle * eye.blinkPeriod;
        float closed = blink(local - eye.blinkPeriod * 0.6F);
        // Иногда моргает дважды подряд.
        if (hash(index, cycle) < 0.3F) {
            closed = Math.max(closed, blink(local - eye.blinkPeriod * 0.6F - 9.0F));
        }
        float squint = phase == ZangetsuSpiritEntity.WRATH ? 0.82F : 1.0F;
        return open * (1.0F - closed) * squint;
    }

    /** Веко опускается за пару тиков и поднимается чуть медленнее. */
    private static float blink(float t) {
        if (t < 0.0F || t > 7.0F) {
            return 0.0F;
        }
        return t < 2.5F ? ease(t / 2.5F) : 1.0F - ease((t - 2.5F) / 4.5F);
    }

    /** Чёрная припухлость вокруг глаза: на ней он лежит ровно, какой бы ком ни шевелился под ним. */
    private void socket(VertexConsumer out, MatrixStack.Entry entry, EyeShape e, float pop, float vx, float vy, float vz) {
        float w = e.width * 1.2F, h = e.height * 1.2F + e.width * 0.08F, d = e.depth * 0.82F;
        Matrix4f matrix = entry.getPositionMatrix();
        int rings = 5, sides = 16;
        float centerX = e.x * pop, centerY = e.y * pop, centerZ = e.z * pop;
        for (int i = 0; i < rings; i++) {
            float t0 = (float) Math.PI * 0.5F * i / rings, t1 = (float) Math.PI * 0.5F * (i + 1) / rings;
            for (int j = 0; j < sides; j++) {
                float f0 = TWO_PI * j / sides, f1 = TWO_PI * (j + 1) / sides;
                socketVertex(out, entry, matrix, e, centerX, centerY, centerZ, w * pop, h * pop, d * pop, t0, f0, vx, vy, vz);
                socketVertex(out, entry, matrix, e, centerX, centerY, centerZ, w * pop, h * pop, d * pop, t0, f1, vx, vy, vz);
                socketVertex(out, entry, matrix, e, centerX, centerY, centerZ, w * pop, h * pop, d * pop, t1, f1, vx, vy, vz);
                socketVertex(out, entry, matrix, e, centerX, centerY, centerZ, w * pop, h * pop, d * pop, t1, f0, vx, vy, vz);
            }
        }
    }

    private static void socketVertex(VertexConsumer out, MatrixStack.Entry entry, Matrix4f matrix, EyeShape e, float cx, float cy, float cz, float w, float h, float d, float theta, float phi, float vx, float vy, float vz) {
        float st = MathHelper.sin(theta), ct = MathHelper.cos(theta);
        float lx = st * MathHelper.cos(phi), ly = st * MathHelper.sin(phi);
        float nx = e.rx * lx + e.ux * ly + e.nx * ct, ny = e.ry * lx + e.uy * ly + e.ny * ct, nz = e.rz * lx + e.uz * ly + e.nz * ct;
        float rim = 1.0F - Math.abs(nx * vx + ny * vy + nz * vz);
        float a = lx * w, b = ly * h, c = ct * d;
        massVertex(out, entry, matrix, cx + e.rx * a + e.ux * b + e.nx * c, cy + e.ry * a + e.uy * b + e.ny * c, cz + e.rz * a + e.uz * b + e.nz * c, 3.0F + 5.0F * rim * rim);
    }

    private void eyeball(VertexConsumer out, MatrixStack.Entry entry, EyeShape e, float open, float wrath) {
        Matrix4f matrix = entry.getPositionMatrix();
        int g = (int) (255.0F - wrath * 8.0F), b = (int) (255.0F - wrath * 12.0F);
        for (int i = 0; i < EYE_RINGS; i++) {
            float t0 = (float) Math.PI * 0.5F * i / EYE_RINGS, t1 = (float) Math.PI * 0.5F * (i + 1) / EYE_RINGS;
            for (int j = 0; j < EYE_SIDES; j++) {
                float f0 = TWO_PI * j / EYE_SIDES, f1 = TWO_PI * (j + 1) / EYE_SIDES;
                eyeVertex(out, entry, matrix, e, open, t0, f0, g, b);
                eyeVertex(out, entry, matrix, e, open, t0, f1, g, b);
                eyeVertex(out, entry, matrix, e, open, t1, f1, g, b);
                eyeVertex(out, entry, matrix, e, open, t1, f0, g, b);
            }
        }
    }

    private static void eyeVertex(VertexConsumer out, MatrixStack.Entry entry, Matrix4f matrix, EyeShape e, float open, float theta, float phi, int g, int b) {
        float st = MathHelper.sin(theta), ct = MathHelper.cos(theta);
        float lx = st * MathHelper.cos(phi), ly = st * MathHelper.sin(phi);
        float a = lx * e.width, c = ly * e.height * open, d = ct * e.depth;
        put(out, entry, matrix, e.x + e.rx * a + e.ux * c + e.nx * d, e.y + e.ry * a + e.uy * c + e.ny * d, e.z + e.rz * a + e.uz * c + e.nz * d,
                255, g, b, 255, 0.5F + 0.5F * lx, 0.5F - 0.5F * ly);
    }

    /** Куда смотреть: у каждого глаза свои резкие перебежки, а смотреть на игрока он может и сам по себе; когда фигура обращается к нему, смотрят все. */
    private void pupil(VertexConsumer out, MatrixStack.Entry entry, ZangetsuSpiritEntity entity, int index, EyeShape e, float open, float tickDelta, float time, float camX, float camY, float camZ) {
        // На игрока, в осях глаза.
        float tx = camX - e.x, ty = camY - e.y, tz = camZ - e.z;
        float toX = tx * e.rx + ty * e.ry + tz * e.rz, toY = tx * e.ux + ty * e.uy + tz * e.uz, toZ = tx * e.nx + ty * e.ny + tz * e.nz;
        float toLength = MathHelper.sqrt(toX * toX + toY * toY + toZ * toZ);
        toX /= toLength;
        toY /= toLength;
        toZ /= toLength;

        // Сам по себе.
        float t = time + e.gazeShift;
        int segment = MathHelper.floor(t / e.gazePeriod);
        float move = ease((t - segment * e.gazePeriod) / 3.0F);
        float gx = MathHelper.lerp(move, roamX(index, segment - 1, toX), roamX(index, segment, toX));
        float gy = MathHelper.lerp(move, roamY(index, segment - 1, toY), roamY(index, segment, toY));
        float gz = MathHelper.lerp(move, roamZ(index, segment - 1, toZ), roamZ(index, segment, toZ));

        // Фаза сцены: на игрока или кто куда.
        int phase = entity.getPhase();
        float k = ease((entity.age + tickDelta - entity.phaseAt - index * 1.5F) / 3.0F);
        float stare = MathHelper.lerp(k, stareOf(entity.previousPhase, index), stareOf(phase, index));
        gx = MathHelper.lerp(stare, gx, toX);
        gy = MathHelper.lerp(stare, gy, toY);
        gz = MathHelper.lerp(stare, gz, toZ);

        // Зрачок всегда на видимой стороне глаза.
        float length = MathHelper.sqrt(gx * gx + gy * gy + gz * gz);
        gx /= length;
        gy /= length;
        gz /= length;
        // Зрачок всегда целиком внутри глаза: у края овала он бы обрезался.
        float limit = gx * gx / (0.46F * 0.46F) + gy * gy / (0.27F * 0.27F);
        if (limit > 1.0F) {
            float shrink = 1.0F / MathHelper.sqrt(limit);
            gx *= shrink;
            gy *= shrink;
        }
        gz = MathHelper.sqrt(Math.max(0.0F, 1.0F - gx * gx - gy * gy));

        // Когда смотрит на игрока, зрачок сужается в щель.
        boolean wrathful = phase == ZangetsuSpiritEntity.WRATH || phase == ZangetsuSpiritEntity.CLOSING;
        float width = 0.36F * (1.0F - stare * 0.24F) * (wrathful ? 1.25F : 1.0F), height = 0.5F;
        // Оси вокруг взгляда.
        float e1x = 1.0F - gx * gx, e1y = -gx * gy, e1z = -gx * gz;
        float e1 = MathHelper.sqrt(e1x * e1x + e1y * e1y + e1z * e1z);
        e1x /= e1;
        e1y /= e1;
        e1z /= e1;
        float e2x = gy * e1z - gz * e1y, e2y = gz * e1x - gx * e1z, e2z = gx * e1y - gy * e1x;

        Matrix4f matrix = entry.getPositionMatrix();
        int columns = 3, rows = 4;
        float h = e.height * open;
        for (int i = 0; i < columns; i++) {
            float a0 = -1.0F + 2.0F * i / columns, a1 = -1.0F + 2.0F * (i + 1) / columns;
            for (int j = 0; j < rows; j++) {
                float b0 = -1.0F + 2.0F * j / rows, b1 = -1.0F + 2.0F * (j + 1) / rows;
                pupilVertex(out, entry, matrix, e, h, gx, gy, gz, e1x, e1y, e1z, e2x, e2y, e2z, a0, b0, width, height);
                pupilVertex(out, entry, matrix, e, h, gx, gy, gz, e1x, e1y, e1z, e2x, e2y, e2z, a1, b0, width, height);
                pupilVertex(out, entry, matrix, e, h, gx, gy, gz, e1x, e1y, e1z, e2x, e2y, e2z, a1, b1, width, height);
                pupilVertex(out, entry, matrix, e, h, gx, gy, gz, e1x, e1y, e1z, e2x, e2y, e2z, a0, b1, width, height);
            }
        }
    }

    private static void pupilVertex(VertexConsumer out, MatrixStack.Entry entry, Matrix4f matrix, EyeShape e, float h, float gx, float gy, float gz, float e1x, float e1y, float e1z, float e2x, float e2y, float e2z, float a, float b, float width, float height) {
        float dx = gx + e1x * a * width + e2x * b * height, dy = gy + e1y * a * width + e2y * b * height, dz = gz + e1z * a * width + e2z * b * height;
        float l = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= l;
        dy /= l;
        dz /= l;
        float x = dx * e.width * 1.015F, y = dy * h * 1.015F, z = dz * e.depth * 1.015F + 0.012F;
        put(out, entry, matrix, e.x + e.rx * x + e.ux * y + e.nx * z, e.y + e.ry * x + e.uy * y + e.ny * z, e.z + e.rz * x + e.uz * y + e.nz * z,
                255, 255, 255, 255, 0.5F + 0.5F * a, 0.5F - 0.5F * b);
    }

    private static boolean roamsToPlayer(int index, int segment) {
        return hash(index * 7 + 3, segment) < 0.22F;
    }

    private static float roamX(int index, int segment, float toX) {
        return roamsToPlayer(index, segment) ? toX : (hash(index + 11, segment) - 0.5F) * 1.5F;
    }

    private static float roamY(int index, int segment, float toY) {
        return roamsToPlayer(index, segment) ? toY : (hash(index + 37, segment) - 0.5F) * 1.3F;
    }

    private static float roamZ(int index, int segment, float toZ) {
        return roamsToPlayer(index, segment) ? toZ : 1.0F;
    }

    /** На игрока или нет: пока фигура просто наблюдает, часть глаз всё равно бегает по сторонам. */
    private static float stareOf(int phase, int index) {
        return switch (phase) {
            case ZangetsuSpiritEntity.ROAM -> 0.0F;
            case ZangetsuSpiritEntity.WATCH -> index % 3 == 2 ? 0.0F : 1.0F;
            default -> 1.0F;
        };
    }

    /** Меч выходит прямо из зрачка центрального глаза: остриё вниз, чёрный клинок на белом глазу. */
    private void holdingSword(ZangetsuSpiritEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers) {
        int hold = entity.getHold();
        if (hold <= 0) {
            return;
        }
        float out = ease((entity.age + tickDelta - entity.holdAt) / hold);
        matrices.push();
        matrices.translate(-0.6F, 0.8F - out * 4.4F, 5.7F + out * 2.5F);
        // Остриё смотрит вниз и чуть вперёд, а сам меч медленно поворачивается вокруг своей оси.
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-22.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + tickDelta) * 3.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-135.0F));
        float size = 2.8F / entity.getSpiritScale();
        matrices.scale(size, size, size);
        net.minecraft.entity.Entity previous = EyeTracker.push(entity);
        this.itemRenderer.renderItem(this.sword, ModelTransformationMode.NONE, FULL, OverlayTexture.DEFAULT_UV, matrices, consumers, entity.getWorld(), entity.getId());
        EyeTracker.pop(previous);
        matrices.pop();
    }

    @Override
    public boolean shouldRender(ZangetsuSpiritEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public Identifier getTexture(ZangetsuSpiritEntity entity) {
        return WHITE;
    }
}
