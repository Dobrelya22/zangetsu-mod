package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import com.zangetsu.entity.ZangetsuSpiritEntity;
import com.zangetsu.logic.Eye;
import com.zangetsu.registry.ModComponents;
import com.zangetsu.registry.ModItems;
import java.io.InputStream;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
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
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Яхве в небе. Не шар, а широкий лист, раскинутый по небу, но весь из настоящего объёма: нос, усы, губы, воротник и складки плаща подняты сеткой по карте высот, сверху живут объёмные глаза и
 * отдельные пряди. Лист всегда повёрнут к тому, кто смотрит, с небольшой задержкой, поэтому объём виден. Внутри всё считается в «единицах головы», масштаб сущности — блоков на единицу.
 */
public class SpiritRenderer extends EntityRenderer<ZangetsuSpiritEntity> {
    private static final Identifier SHEET = ZangetsuMod.id("textures/entity/yhwach.png");
    private static final Identifier RELIEF = ZangetsuMod.id("textures/entity/yhwach_relief.png");
    private static final Identifier EYE = ZangetsuMod.id("textures/entity/yhwach_eye.png");
    private static final Identifier PUPIL = ZangetsuMod.id("textures/entity/yhwach_pupil.png");
    private static final Identifier WHITE = ZangetsuMod.id("textures/misc/white.png");
    private static final RenderLayer SHEET_LAYER = Flames.smoothCutout("zangetsu_yhwach", SHEET);
    private static final RenderLayer EYE_LAYER = Flames.smoothCutout("zangetsu_yhwach_eye", EYE);
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    /** Как лист лежит: пикселей на единицу головы, где середина лица и кончик носа (те же числа в tools/yhwach.cs). */
    private static final float PIXELS = 1536.0F, UNIT = 225.0F, FACE_X = 768.0F, NOSE_Y = 414.0F;
    /** Середина листа по высоте: туда попадает позиция сущности. */
    private static final float CENTER_Y = (NOSE_Y - PIXELS * 0.5F) / UNIT;
    private static final int CELLS = 120, VERTS = CELLS + 1;

    /** Глаза: x, y на листе, полуширина, полувысота, наклон (те же числа в tools/yhwach.cs). Первые восемь на тёмной массе, как на кадре, остальные на плаще. */
    private static final float[][] EYE_LAYOUT = {
            {0.70F, 0.95F, 0.33F, 0.35F, -28.0F},
            {-0.85F, 0.82F, 0.22F, 0.29F, 20.0F},
            {-0.32F, 1.22F, 0.19F, 0.24F, 10.0F},
            {-0.28F, 0.72F, 0.21F, 0.24F, -8.0F},
            {0.18F, 0.82F, 0.15F, 0.22F, 4.0F},
            {1.06F, 0.26F, 0.27F, 0.19F, -24.0F},
            {-1.08F, 0.13F, 0.22F, 0.17F, 24.0F},
            {-0.70F, 0.45F, 0.13F, 0.11F, 16.0F},
            {-2.05F, -2.05F, 0.34F, 0.25F, 18.0F},
            {2.20F, -1.95F, 0.38F, 0.27F, -20.0F},
            {-1.15F, -2.95F, 0.40F, 0.31F, 8.0F},
            {0.95F, -2.65F, 0.34F, 0.47F, -6.0F},
            {0.00F, -3.65F, 0.32F, 0.36F, 3.0F},
            {2.40F, -3.25F, 0.30F, 0.22F, -14.0F},
            {-2.55F, -3.45F, 0.32F, 0.23F, 16.0F},
            {-0.35F, -2.25F, 0.24F, 0.21F, -12.0F},
            {1.70F, -3.95F, 0.24F, 0.18F, 10.0F},
            {-1.70F, -4.05F, 0.25F, 0.19F, -9.0F},
    };
    private static final int EYE_RINGS = 8, EYE_SIDES = 22, LOCK_SIDES = 5;

    private static final class EyeShape {
        float x, y, z;
        float rx, ry, ux, uy;
        float width, height, depth;
        float wobbleA, wobbleB;
        float gazePeriod, gazeShift, blinkPeriod, blinkShift;
        int closeOrder;
    }

    private static final class Lock {
        float[] x, y, z;
        float thick, sway, frequency, phase, delay, tone;
        boolean pale;
    }

    // ---- сетка листа: заполняется один раз из карты высот
    private static boolean built;
    private static final float[] BASE_X = new float[VERTS * VERTS], BASE_Y = new float[VERTS * VERTS], BASE_Z = new float[VERTS * VERTS], BASE_U = new float[VERTS * VERTS], BASE_V = new float[VERTS * VERTS], SEED = new float[VERTS * VERTS];
    private static int[] quads = new int[0];
    private static EyeShape[] eyes = new EyeShape[0];
    private static Lock[] locks = new Lock[0];

    private final float[] px = new float[VERTS * VERTS], py = new float[VERTS * VERTS], pz = new float[VERTS * VERTS], alpha = new float[VERTS * VERTS], glow = new float[VERTS * VERTS];
    private final ItemRenderer itemRenderer;
    private final ItemStack sword = new ItemStack(ModItems.ZANGETSU);

    public SpiritRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.sword.set(ModComponents.EYE, Eye.OPEN);
    }

    // ---------------------------------------------------------------- заготовки

    private static void build() {
        built = true;
        float[] height = new float[VERTS * VERTS], cover = new float[VERTS * VERTS];
        try (InputStream stream = MinecraftClient.getInstance().getResourceManager().getResource(RELIEF).orElseThrow().getInputStream(); NativeImage image = NativeImage.read(stream)) {
            int size = image.getWidth();
            for (int j = 0; j < VERTS; j++) {
                for (int i = 0; i < VERTS; i++) {
                    int sx = Math.min(size - 1, i * size / CELLS), sy = Math.min(size - 1, j * size / CELLS);
                    int color = image.getColor(sx, sy);
                    // В NativeImage каналы лежат как ABGR: красный в младшем байте.
                    int r = color & 255, g = (color >> 8) & 255, b = (color >> 16) & 255;
                    height[j * VERTS + i] = (r * 256 + g) / 65535.0F * 3.0F - 1.0F;
                    cover[j * VERTS + i] = b / 255.0F;
                }
            }
        } catch (Exception e) {
            ZangetsuMod.LOGGER.error("Зангецу: нет рельефа Яхве", e);
        }
        Random random = new Random(61);
        for (int j = 0; j < VERTS; j++) {
            for (int i = 0; i < VERTS; i++) {
                int at = j * VERTS + i;
                float u = (float) i / CELLS, v = (float) j / CELLS;
                BASE_U[at] = u;
                BASE_V[at] = v;
                BASE_X[at] = (u * PIXELS - FACE_X) / UNIT;
                BASE_Y[at] = (NOSE_Y - v * PIXELS) / UNIT;
                BASE_Z[at] = height[at];
                SEED[at] = random.nextFloat();
            }
        }
        // Пустые углы листа не рисуем совсем.
        int[] list = new int[CELLS * CELLS];
        int count = 0;
        for (int j = 0; j < CELLS; j++) {
            for (int i = 0; i < CELLS; i++) {
                int a = j * VERTS + i, b = a + 1, c = a + VERTS, d = c + 1;
                if (cover[a] > 0.0F || cover[b] > 0.0F || cover[c] > 0.0F || cover[d] > 0.0F) {
                    list[count++] = a;
                }
            }
        }
        quads = java.util.Arrays.copyOf(list, count);
        buildEyes();
        buildLocks();
    }

    private static float heightAt(float x, float y) {
        float fx = MathHelper.clamp((x * UNIT + FACE_X) / PIXELS * CELLS, 0.0F, CELLS - 0.01F), fy = MathHelper.clamp((NOSE_Y - y * UNIT) / PIXELS * CELLS, 0.0F, CELLS - 0.01F);
        int i = (int) fx, j = (int) fy;
        float s = fx - i, t = fy - j;
        float top = MathHelper.lerp(s, BASE_Z[j * VERTS + i], BASE_Z[j * VERTS + i + 1]);
        float bottom = MathHelper.lerp(s, BASE_Z[(j + 1) * VERTS + i], BASE_Z[(j + 1) * VERTS + i + 1]);
        return MathHelper.lerp(t, top, bottom);
    }

    private static void buildEyes() {
        Random random = new Random(777L);
        eyes = new EyeShape[EYE_LAYOUT.length];
        for (int i = 0; i < eyes.length; i++) {
            float[] l = EYE_LAYOUT[i];
            EyeShape e = new EyeShape();
            e.x = l[0];
            e.y = l[1];
            e.z = heightAt(l[0], l[1]) + 0.03F;
            float tilt = l[4] * MathHelper.RADIANS_PER_DEGREE, c = MathHelper.cos(tilt), s = MathHelper.sin(tilt);
            e.rx = c;
            e.ry = s;
            e.ux = -s;
            e.uy = c;
            e.width = l[2];
            e.height = l[3];
            e.depth = Math.min(l[2], l[3]) * 0.6F;
            e.wobbleA = random.nextFloat() * 6.28F;
            e.wobbleB = random.nextFloat() * 6.28F;
            e.gazePeriod = 26.0F + random.nextFloat() * 46.0F;
            e.gazeShift = random.nextFloat() * 200.0F;
            e.blinkPeriod = 50.0F + random.nextFloat() * 74.0F;
            e.blinkShift = random.nextFloat() * 200.0F;
            // Самые большие глаза, те, что на голове, закрываются последними.
            e.closeOrder = eyes.length - 1 - i;
            eyes[i] = e;
        }
    }

    /** Живые пряди поверх запечённой гривы: выходят из неё, идут наружу и закручиваются на концах. */
    private static void buildLocks() {
        Random random = new Random(1987L);
        java.util.List<Lock> list = new java.util.ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 16; i++) {
                float angle = (58.0F - 170.0F * (i + random.nextFloat() * 0.8F) / 16.0F) * MathHelper.RADIANS_PER_DEGREE;
                list.add(lock(random, side, angle, 0.8F + random.nextFloat() * 0.55F, 0.075F + random.nextFloat() * 0.06F, false, side > 0 && i % 4 == 1));
            }
            for (int i = 0; i < 34; i++) {
                float angle = (70.0F - 195.0F * random.nextFloat()) * MathHelper.RADIANS_PER_DEGREE;
                list.add(lock(random, side, angle, 0.45F + random.nextFloat() * 0.8F, 0.012F + random.nextFloat() * 0.022F, true, side > 0 && random.nextFloat() < 0.3F));
            }
        }
        locks = list.toArray(new Lock[0]);
    }

    private static Lock lock(Random random, int side, float angle, float length, float thick, boolean ribbon, boolean pale) {
        Lock lock = new Lock();
        int n = 9;
        lock.x = new float[n];
        lock.y = new float[n];
        lock.z = new float[n];
        float x = side * (1.7F + MathHelper.cos(angle) * (0.72F + random.nextFloat() * 0.3F));
        float y = -0.3F + MathHelper.sin(angle) * (0.72F + random.nextFloat() * 0.3F);
        float z = heightAt(x, y) + 0.03F;
        float a = angle, curl = -(0.2F + random.nextFloat() * 0.3F);
        for (int i = 0; i < n; i++) {
            lock.x[i] = x;
            lock.y[i] = y;
            lock.z[i] = z + (float) i / n * 0.2F;
            float step = length / (n - 1);
            x += side * MathHelper.cos(a) * step;
            y += MathHelper.sin(a) * step;
            // Конец пряди заворачивается вниз, всё сильнее.
            a += curl * (0.3F + i * 0.24F);
        }
        lock.thick = thick;
        lock.sway = 0.03F + random.nextFloat() * 0.05F;
        lock.frequency = 0.03F + random.nextFloat() * 0.05F;
        lock.phase = random.nextFloat() * 6.28F;
        lock.delay = random.nextFloat() * 0.35F;
        lock.tone = 6.0F + random.nextFloat() * 10.0F;
        lock.pale = pale;
        return lock;
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

    /** Куда смотрит лист: на камеру, с мягким запаздыванием. Отдельно для каждого кадра, потому что камера в сценах летает. */
    private void face(ZangetsuSpiritEntity entity, float time, Vec3d rel) {
        double flat = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
        float wantedYaw = flat < 1.0 ? entity.faceYaw : (float) Math.toDegrees(Math.atan2(-rel.x, rel.z));
        float wantedPitch = (float) Math.toDegrees(Math.atan2(rel.y, Math.max(flat, 0.001)));
        long now = System.nanoTime();
        if (entity.faceNanos == 0L) {
            entity.faceYaw = wantedYaw;
            entity.facePitch = wantedPitch;
        } else {
            float dt = MathHelper.clamp((now - entity.faceNanos) / 1.0E9F, 0.0F, 0.1F);
            float k = 1.0F - (float) Math.exp(-dt * 2.6F);
            entity.faceYaw += MathHelper.wrapDegrees(wantedYaw - entity.faceYaw) * k;
            entity.facePitch += (wantedPitch - entity.facePitch) * k;
        }
        entity.faceNanos = now;
    }

    @Override
    public void render(ZangetsuSpiritEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (!built) {
            build();
        }
        float presence = entity.presence(tickDelta);
        float time = entity.age + tickDelta;
        if (presence > 0.002F) {
            this.figure(entity, presence, time, tickDelta, matrices, consumers);
        }
        if (entity.isBreakScene()) {
            YhwachHand.render(entity, tickDelta, matrices, consumers, this.dispatcher.camera.getPos(), this.itemRenderer);
        }
    }

    private void figure(ZangetsuSpiritEntity entity, float presence, float time, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers) {
        float scale = entity.getSpiritScale();
        Vec3d at = entity.getLerpedPos(tickDelta);
        Vec3d rel = this.dispatcher.camera.getPos().subtract(at);
        this.face(entity, time, rel);
        // Лист чуть покачивается: так глаз видит, что он объёмный.
        float bodyYaw = entity.faceYaw + 2.0F * MathHelper.sin(time * 0.023F);
        float tilt = -(entity.facePitch + 1.3F * MathHelper.sin(time * 0.019F + 1.0F));
        float bob = MathHelper.sin(time * 0.031F) * 0.06F;

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tilt));
        matrices.scale(scale, scale, scale);
        matrices.translate(0.0F, -CENTER_Y + bob, 0.0F);

        // Камера в осях листа: для взгляда глаз.
        float yawRad = bodyYaw * MathHelper.RADIANS_PER_DEGREE, cos = MathHelper.cos(yawRad), sin = MathHelper.sin(yawRad);
        float tiltRad = tilt * MathHelper.RADIANS_PER_DEGREE, tc = MathHelper.cos(tiltRad), ts = MathHelper.sin(tiltRad);
        float flatX = (float) (rel.x * cos + rel.z * sin), flatY = (float) rel.y, flatZ = (float) (-rel.x * sin + rel.z * cos);
        float camX = flatX / scale, camY = (flatY * tc + flatZ * ts) / scale + CENTER_Y - bob, camZ = (flatZ * tc - flatY * ts) / scale;

        float sinceFlare = time - entity.flareAt;
        float flare = sinceFlare < 0.0F || sinceFlare > 26.0F ? 0.0F : (sinceFlare < 3.0F ? sinceFlare / 3.0F : 1.0F - (sinceFlare - 3.0F) / 23.0F);

        this.sheet(consumers.getBuffer(SHEET_LAYER), matrices.peek(), time, presence, entity.talk(tickDelta), flare);
        VertexConsumer mass = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));
        this.locks(mass, matrices.peek(), time, presence, camX, camY, camZ);
        this.wisps(mass, matrices.peek(), time, presence);
        this.holdingSword(entity, tickDelta, matrices, consumers);

        float[] open = new float[eyes.length];
        for (int i = 0; i < eyes.length; i++) {
            open[i] = this.openness(entity, i, eyes[i], time);
        }
        VertexConsumer ball = consumers.getBuffer(EYE_LAYER);
        for (int i = 0; i < eyes.length; i++) {
            if (open[i] > 0.015F) {
                this.eyeball(ball, matrices.peek(), eyes[i], open[i]);
            }
        }
        VertexConsumer halo = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        for (int i = 0; i < eyes.length; i++) {
            if (open[i] > 0.015F) {
                this.halo(halo, matrices.peek(), eyes[i], open[i], time);
            }
        }
        VertexConsumer pupil = consumers.getBuffer(RenderLayer.getEntityTranslucent(PUPIL));
        for (int i = 0; i < eyes.length; i++) {
            if (open[i] > 0.1F) {
                this.pupil(pupil, matrices.peek(), entity, i, eyes[i], open[i], tickDelta, time, camX, camY, camZ);
            }
        }
        matrices.pop();
    }

    private static void put(VertexConsumer out, Matrix4f matrix, float x, float y, float z, int r, int g, int b, int a, float u, float v) {
        out.vertex(matrix, x, y, z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(FULL).normal(0.0F, 1.0F, 0.0F);
    }

    // ---------------------------------------------------------------- лист

    /**
     * Сам лист: голова неподвижна, плащ ниже плеч дышит волнами, по которым бежит свет. Появляется как чернильное пятно: граница расползается от лица наружу, и рельеф поднимается вслед за ней.
     * Прозрачности нет, граница получается рваной сама, потому что слой отсекает всё, что ниже порога.
     */
    private void sheet(VertexConsumer out, MatrixStack.Entry entry, float time, float presence, float talk, float flare) {
        float reach = presence * 9.2F - 1.0F;
        for (int at = 0; at < BASE_X.length; at++) {
            float x = BASE_X[at], y = BASE_Y[at], z = BASE_Z[at];
            float low = MathHelper.clamp((-0.9F - y) / 3.6F, 0.0F, 1.0F);
            float wing = low * low;
            float a1 = MathHelper.sin(y * 1.9F - time * 0.05F + x * 0.9F), a2 = MathHelper.sin(y * 3.7F + time * 0.07F + x * 2.3F), a3 = MathHelper.sin(x * 1.3F - time * 0.033F);
            float dx = wing * (a1 * 0.16F + a2 * 0.05F) + flare * x * 0.03F * low;
            float dy = wing * a3 * 0.06F;
            float dz = low * (a1 * 0.2F + a2 * 0.06F) + wing * a3 * 0.1F + flare * 0.2F * low;
            // Рот открывается, когда он говорит: нижняя челюсть уходит вниз.
            if (talk > 0.0F && y < -0.40F && y > -1.15F) {
                float ax = Math.abs(x);
                if (ax < 0.7F) {
                    dy -= talk * 0.075F * MathHelper.clamp((-0.40F - y) / 0.03F, 0.0F, 1.0F) * (1.0F - ax / 0.7F) * MathHelper.clamp((y + 1.15F) / 0.5F, 0.0F, 1.0F);
                }
            }
            // Чернильный край появления.
            float dist = MathHelper.sqrt(x * x + (y - 0.5F) * (y - 0.5F));
            float front = MathHelper.clamp((reach - dist - SEED[at] * 0.5F) / 0.32F, 0.0F, 1.0F);
            float rise = front * front * (3.0F - 2.0F * front);
            this.px[at] = x + dx;
            this.py[at] = y + dy;
            this.pz[at] = (z * rise) * 1.0F + dz;
            this.alpha[at] = front;
            this.glow[at] = 1.0F + low * (a1 * 0.28F + a2 * 0.1F);
        }
        Matrix4f matrix = entry.getPositionMatrix();
        for (int quad : quads) {
            this.sheetVertex(out, matrix, quad);
            this.sheetVertex(out, matrix, quad + 1);
            this.sheetVertex(out, matrix, quad + VERTS + 1);
            this.sheetVertex(out, matrix, quad + VERTS);
        }
    }

    private void sheetVertex(VertexConsumer out, Matrix4f matrix, int at) {
        int tone = Math.min(255, (int) (this.glow[at] * 238.0F));
        put(out, matrix, this.px[at], this.py[at], this.pz[at], tone, tone, tone, (int) (this.alpha[at] * 255.0F), BASE_U[at], BASE_V[at]);
    }

    // ---------------------------------------------------------------- пряди

    private void locks(VertexConsumer out, MatrixStack.Entry entry, float time, float presence, float camX, float camY, float camZ) {
        Matrix4f matrix = entry.getPositionMatrix();
        float[] ring = new float[LOCK_SIDES * 3 * 2];
        for (Lock lock : locks) {
            float grown = ease((presence - 0.45F - lock.delay * 0.5F) / 0.4F);
            if (grown <= 0.01F) {
                continue;
            }
            int n = lock.x.length;
            float lastX = 0.0F, lastY = 0.0F;
            for (int k = 0; k < n; k++) {
                float u = (float) k / (n - 1);
                float swing = u * u * lock.sway * 3.0F;
                float x = MathHelper.lerp(grown, lock.x[0], lock.x[k]) + MathHelper.sin(time * lock.frequency + lock.phase + u * 2.4F) * swing;
                float y = MathHelper.lerp(grown, lock.y[0], lock.y[k]) + MathHelper.cos(time * lock.frequency * 0.8F + lock.phase * 1.3F + u * 2.0F) * swing;
                float z = lock.z[k] + MathHelper.sin(time * lock.frequency * 0.6F + lock.phase * 0.7F + u) * swing * 0.6F;
                // Веретено: от нуля в глубине гривы, самая толстая в первой трети, к концу в волос.
                float r = lock.thick * MathHelper.sin(MathHelper.PI * (float) Math.pow(u, 0.62F));
                if (k == 0) {
                    float nx = lock.x[1] - lock.x[0], ny = lock.y[1] - lock.y[0];
                    lastX = x - nx;
                    lastY = y - ny;
                }
                float dx = x - lastX, dy = y - lastY;
                float l = Math.max(1.0E-4F, MathHelper.sqrt(dx * dx + dy * dy));
                float sx = -dy / l, sy = dx / l;
                int base = k == 0 ? 0 : LOCK_SIDES * 3;
                for (int j = 0; j < LOCK_SIDES; j++) {
                    float a = MathHelper.TAU * j / LOCK_SIDES, c = MathHelper.cos(a) * r, h = MathHelper.sin(a) * r * 0.7F;
                    ring[base + j * 3] = x + sx * c;
                    ring[base + j * 3 + 1] = y + sy * c;
                    ring[base + j * 3 + 2] = z + h;
                }
                if (k > 0) {
                    for (int j = 0; j < LOCK_SIDES; j++) {
                        int next = (j + 1) % LOCK_SIDES;
                        lockVertex(out, matrix, lock, ring, j, u - 1.0F / (n - 1));
                        lockVertex(out, matrix, lock, ring, next, u - 1.0F / (n - 1));
                        lockVertex(out, matrix, lock, ring, LOCK_SIDES + next, u);
                        lockVertex(out, matrix, lock, ring, LOCK_SIDES + j, u);
                    }
                    System.arraycopy(ring, LOCK_SIDES * 3, ring, 0, LOCK_SIDES * 3);
                }
                lastX = x;
                lastY = y;
            }
        }
    }

    /** Цвет пряди: сверху ловит свет, снизу в тени, у светлых прядей отлив седой. */
    private static void lockVertex(VertexConsumer out, Matrix4f matrix, Lock lock, float[] ring, int index, float u) {
        int side = index % LOCK_SIDES;
        float a = MathHelper.TAU * side / LOCK_SIDES;
        float sheen = Math.max(0.0F, MathHelper.sin(a) * 0.75F + MathHelper.cos(a) * 0.45F);
        float tone = lock.tone + 22.0F * sheen * sheen + 10.0F * u;
        int r, g, b;
        if (lock.pale) {
            r = (int) (46.0F + tone * 2.4F);
            g = (int) (42.0F + tone * 2.4F);
            b = (int) (60.0F + tone * 2.8F);
        } else {
            r = (int) tone;
            g = (int) tone;
            b = (int) (tone * 1.25F + 3.0F);
        }
        put(out, matrix, ring[index * 3], ring[index * 3 + 1], ring[index * 3 + 2], Math.min(255, r), Math.min(255, g), Math.min(255, b), 255, 0.5F, 0.5F);
    }

    /** С рваного подола срываются клочья и тают, уносясь вниз и в стороны. */
    private void wisps(VertexConsumer out, MatrixStack.Entry entry, float time, float presence) {
        if (presence < 0.6F) {
            return;
        }
        Matrix4f matrix = entry.getPositionMatrix();
        for (int i = 0; i < 40; i++) {
            float period = 70.0F + hash(i, 1) * 80.0F;
            float t = (time + hash(i, 2) * 500.0F) / period;
            int life = MathHelper.floor(t);
            t -= life;
            float x = (hash(i, life + 3) * 2.0F - 1.0F) * 3.2F + MathHelper.sin(t * 4.0F + i) * 0.1F;
            float y = -4.3F - hash(i, life + 4) * 0.5F - t * 1.0F;
            float z = 0.1F + hash(i, 5) * 0.2F;
            float size = (0.012F + hash(i, 6) * 0.03F) * MathHelper.sin(t * MathHelper.PI);
            if (size < 0.003F) {
                continue;
            }
            float tall = size * 4.5F;
            put(out, matrix, x - size, y, z, 9, 8, 11, 255, 0.5F, 0.5F);
            put(out, matrix, x + size, y, z, 9, 8, 11, 255, 0.5F, 0.5F);
            put(out, matrix, x + size * 0.2F, y + tall, z, 12, 11, 15, 255, 0.5F, 0.5F);
            put(out, matrix, x - size * 0.2F, y - tall, z, 7, 6, 9, 255, 0.5F, 0.5F);
        }
    }

    // ---------------------------------------------------------------- глаза

    /** Насколько открыт глаз: открывается по очереди, живёт своими морганиями, на прощание закрывается навсегда. */
    private float openness(ZangetsuSpiritEntity entity, int index, EyeShape eye, float time) {
        float open = ease((time - entity.eyesAt() - index * ZangetsuSpiritEntity.EYE_OPEN_STEP) / 8.0F);
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
        float squint = phase == ZangetsuSpiritEntity.WRATH ? 0.84F : 1.0F;
        return open * (1.0F - closed) * squint;
    }

    private static float blink(float t) {
        if (t < 0.0F || t > 7.0F) {
            return 0.0F;
        }
        return t < 2.5F ? ease(t / 2.5F) : 1.0F - ease((t - 2.5F) / 4.5F);
    }

    /** Край глаза неровный, как на кадре: не чистый овал. */
    private static float wobble(EyeShape e, float phi) {
        return 1.0F + 0.06F * MathHelper.sin(3.0F * phi + e.wobbleA) + 0.04F * MathHelper.sin(5.0F * phi + e.wobbleB);
    }

    /** Объёмное глазное яблоко: купол над массой. */
    private void eyeball(VertexConsumer out, MatrixStack.Entry entry, EyeShape e, float open) {
        Matrix4f matrix = entry.getPositionMatrix();
        for (int i = 0; i < EYE_RINGS; i++) {
            float t0 = MathHelper.HALF_PI * i / EYE_RINGS, t1 = MathHelper.HALF_PI * (i + 1) / EYE_RINGS;
            for (int j = 0; j < EYE_SIDES; j++) {
                float f0 = MathHelper.TAU * j / EYE_SIDES, f1 = MathHelper.TAU * (j + 1) / EYE_SIDES;
                eyeVertex(out, matrix, e, open, t0, f0);
                eyeVertex(out, matrix, e, open, t0, f1);
                eyeVertex(out, matrix, e, open, t1, f1);
                eyeVertex(out, matrix, e, open, t1, f0);
            }
        }
    }

    private static void eyeVertex(VertexConsumer out, Matrix4f matrix, EyeShape e, float open, float theta, float phi) {
        float st = MathHelper.sin(theta), ct = MathHelper.cos(theta);
        float lx = st * MathHelper.cos(phi), ly = st * MathHelper.sin(phi);
        float w = wobble(e, phi);
        float a = lx * e.width * w, b = ly * e.height * w * open, z = ct * e.depth;
        put(out, matrix, e.x + e.rx * a + e.ux * b, e.y + e.ry * a + e.uy * b, e.z + z, 255, 255, 255, 255, 0.5F + 0.5F * lx, 0.5F - 0.5F * ly);
    }

    /** Холодное свечение вокруг глаза на чёрной массе, как на кадре. */
    private void halo(VertexConsumer out, MatrixStack.Entry entry, EyeShape e, float open, float time) {
        Matrix4f matrix = entry.getPositionMatrix();
        int inner = (int) (150.0F * open * (0.85F + 0.15F * MathHelper.sin(time * 0.09F + e.wobbleA)));
        for (int j = 0; j < EYE_SIDES; j++) {
            float f0 = MathHelper.TAU * j / EYE_SIDES, f1 = MathHelper.TAU * (j + 1) / EYE_SIDES;
            haloVertex(out, matrix, e, open, f0, 0.97F, inner);
            haloVertex(out, matrix, e, open, f1, 0.97F, inner);
            haloVertex(out, matrix, e, open, f1, 1.42F, 0);
            haloVertex(out, matrix, e, open, f0, 1.42F, 0);
        }
    }

    private static void haloVertex(VertexConsumer out, Matrix4f matrix, EyeShape e, float open, float phi, float r, int a) {
        float w = wobble(e, phi) * r;
        float lx = MathHelper.cos(phi) * e.width * w, ly = MathHelper.sin(phi) * e.height * w * (r < 1.0F ? open : 0.3F + 0.7F * open);
        put(out, matrix, e.x + e.rx * lx + e.ux * ly, e.y + e.ry * lx + e.uy * ly, e.z + 0.13F, 120, 170, 255, a, 0.5F, 0.5F);
    }

    /** Зрачок лежит на самом куполе и ходит по нему: у каждого глаза свои резкие перебежки, а когда он обращается к игроку, смотрят все. */
    private void pupil(VertexConsumer out, MatrixStack.Entry entry, ZangetsuSpiritEntity entity, int index, EyeShape e, float open, float tickDelta, float time, float camX, float camY, float camZ) {
        float tx = camX - e.x, ty = camY - e.y, tz = camZ - e.z;
        float toX = tx * e.rx + ty * e.ry, toY = tx * e.ux + ty * e.uy, toZ = tz;
        float toLength = MathHelper.sqrt(toX * toX + toY * toY + toZ * toZ);
        toX /= toLength;
        toY /= toLength;
        toZ /= toLength;

        float t = time + e.gazeShift;
        int segment = MathHelper.floor(t / e.gazePeriod);
        float move = ease((t - segment * e.gazePeriod) / 3.0F);
        float gx = MathHelper.lerp(move, roamX(index, segment - 1, toX), roamX(index, segment, toX));
        float gy = MathHelper.lerp(move, roamY(index, segment - 1, toY), roamY(index, segment, toY));
        float gz = MathHelper.lerp(move, roamZ(index, segment - 1, toZ), roamZ(index, segment, toZ));

        float k = ease((entity.age + tickDelta - entity.phaseAt - index * 1.0F) / 3.0F);
        float stare = MathHelper.lerp(k, stareOf(entity.previousPhase, index), stareOf(entity.getPhase(), index));
        gx = MathHelper.lerp(stare, gx, toX);
        gy = MathHelper.lerp(stare, gy, toY);
        gz = MathHelper.lerp(stare, gz, toZ);

        float length = MathHelper.sqrt(gx * gx + gy * gy + gz * gz);
        gx /= length;
        gy /= length;
        // Зрачок всегда внутри глаза: у края овала он бы обрезался.
        float limit = gx * gx / (0.5F * 0.5F) + gy * gy / (0.5F * 0.5F);
        if (limit > 1.0F) {
            float shrink = 1.0F / MathHelper.sqrt(limit);
            gx *= shrink;
            gy *= shrink;
        }
        gz = MathHelper.sqrt(Math.max(0.0F, 1.0F - gx * gx - gy * gy));

        // Зрачок круглый, какой бы формы ни был глаз; когда он смотрит на игрока, зрачок немного сжимается.
        float radius = Math.min(e.width, e.height) * (0.3F - stare * 0.05F);
        Matrix4f matrix = entry.getPositionMatrix();
        int cells = 3;
        for (int i = 0; i < cells; i++) {
            float a0 = -1.0F + 2.0F * i / cells, a1 = -1.0F + 2.0F * (i + 1) / cells;
            for (int j = 0; j < cells; j++) {
                float b0 = -1.0F + 2.0F * j / cells, b1 = -1.0F + 2.0F * (j + 1) / cells;
                pupilVertex(out, matrix, e, open, gx, gy, a0, b0, radius);
                pupilVertex(out, matrix, e, open, gx, gy, a1, b0, radius);
                pupilVertex(out, matrix, e, open, gx, gy, a1, b1, radius);
                pupilVertex(out, matrix, e, open, gx, gy, a0, b1, radius);
            }
        }
    }

    private static void pupilVertex(VertexConsumer out, Matrix4f matrix, EyeShape e, float open, float gx, float gy, float a, float b, float radius) {
        float lx = gx + a * radius / e.width, ly = gy + b * radius / e.height;
        float q = lx * lx + ly * ly;
        float lz = MathHelper.sqrt(Math.max(0.02F, 1.0F - q));
        float x = lx * e.width, y = ly * e.height * open;
        put(out, matrix, e.x + e.rx * x + e.ux * y, e.y + e.ry * x + e.uy * y, e.z + lz * e.depth + 0.006F, 255, 255, 255, 255, 0.5F + 0.5F * a, 0.5F - 0.5F * b);
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

    /** На игрока или нет: пока он просто наблюдает, часть глаз всё равно бегает по сторонам. */
    private static float stareOf(int phase, int index) {
        return switch (phase) {
            case ZangetsuSpiritEntity.ROAM -> 0.0F;
            case ZangetsuSpiritEntity.WATCH -> index % 3 == 2 ? 0.0F : 1.0F;
            default -> 1.0F;
        };
    }

    /** Меч выходит прямо из груди, из-под воротника, и спускается по плащу остриём вниз. */
    private void holdingSword(ZangetsuSpiritEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers) {
        int hold = entity.getHold();
        if (hold <= 0) {
            return;
        }
        float out = ease((entity.age + tickDelta - entity.holdAt) / hold);
        matrices.push();
        matrices.translate(0.0F, -1.9F - out * 0.7F, 0.45F + out * 0.9F);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-16.0F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + tickDelta) * 3.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-135.0F));
        float size = 3.2F / entity.getSpiritScale();
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
        return SHEET;
    }
}
