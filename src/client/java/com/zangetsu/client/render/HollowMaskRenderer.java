package com.zangetsu.client.render;

import com.zangetsu.ZangetsuMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

/**
 * Маска Пустого — вылепленная из кости, а не разрисованная: надбровья, глубокие глазницы, переносица и нос, скулы
 * с впадинами под ними, выступающая челюсть с губами вокруг тёмного провала рта. В провале — настоящие объёмные
 * зубы: округлые клыки растут из-под губ, сужаются к острию и входят между зубами другого ряда. Тени в складках
 * запечены в цвет вершин, поэтому лепка читается и при ровном свете. Текстура даёт только кость, полосы и глубину
 * прорезей глаз; нарисованных контуров нет. Финальная маска (уровень V) — те же черты, но полосы чёрные и рваные,
 * глаза в чёрной обводке и один длинный рог. Трещины и сколы рисует текстура; рог на последних стадиях обломан.
 *
 * <p>Всё строится в координатах головы (пиксели модели: лицо смотрит в −z, вниз — +y, левая сторона маски — +x).
 * Каждый горизонтальный срез оболочки — скруглённый квадрат вокруг головы; α — угол вокруг головы (0 — середина
 * лица), y — высота. Текстура разложена по (α, y), черты лица заданы во фронтальных координатах (x, y) — так же,
 * как в tools/textures.cs (HollowMaskHD).
 */
public final class HollowMaskRenderer {
    private static final Identifier[] MASK = new Identifier[4];
    private static final Identifier[] FINAL = new Identifier[4];
    private static final Identifier EYES = ZangetsuMod.id("textures/entity/hollow_mask_eyes.png");
    /** Охват: ±ALPHA градусов вокруг головы; верх лба, низ подбородка и край челюсти по бокам. */
    static final float ALPHA = 78.0F, TOP = -8.75F, CHIN = 2.8F, JAW = -1.1F;
    /** Полуширина и глубина среза (голова — ±4) и степень скругления: при 8 углы головы не протыкают маску. */
    private static final float A0 = 4.55F, B0 = 4.62F, SQUARENESS = 8.0F;
    /** Прорези глаз: середина, полудлина, полувысота и наклон (наружный уголок выше — злой прищур). */
    static final float EYE_X = 2.05F, EYE_Y = -3.85F, EYE_HALF = 1.4F, EYE_H = 0.4F, EYE_TILT = 0.18F;
    /** Рот: верхняя губа посередине, высота и полуширина. */
    static final float MOUTH_TOP = -2.15F, MOUTH_H = 2.85F, MOUTH_HALF = 3.35F;
    /** Дно провала рта относительно челюсти — до него уходят бока зубов. */
    private static final float MOUTH_FLOOR = -0.7F;
    /** Кость не темнеет вместе с миром: маска должна оставаться белой и ночью. */
    private static final int MIN_BLOCK_LIGHT = 11;
    private static final int NU = 72, NV = 72;
    private static final float THICKNESS = 0.4F;
    /** Готовая поверхность (пиксели), нормали, тени в складках (RGB) и фронтальный x; форма одна на всех — считаем один раз. */
    private static final Vector3f[][] SURFACE = new Vector3f[NU + 1][NV + 1];
    private static final Vector3f[][] NORMALS = new Vector3f[NU + 1][NV + 1];
    private static final int[][] SHADE = new int[NU + 1][NV + 1];
    private static final float[][] FRONT_X = new float[NU + 1][NV + 1];
    /** Вершины зубов: зубы на месте, поэтому их тоже строим один раз. */
    private static final List<Vertex> TEETH = new ArrayList<>();

    static {
        for (int i = 0; i < MASK.length; i++) {
            MASK[i] = ZangetsuMod.id("textures/entity/hollow_mask_" + i + ".png");
            FINAL[i] = ZangetsuMod.id("textures/entity/hollow_final_" + i + ".png");
        }
        build();
        teeth();
    }

    private HollowMaskRenderer() {
    }

    /**
     * Нарисовать маску в координатах головы (матрица уже повёрнута как голова модели). {@code grow} — 0..1, маска
     * нарастает при превращении; {@code glow} — яркость глаз 0..255; {@code finalForm} — финальная маска с рогом.
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, int crack, float grow, int glow,
            boolean finalForm) {
        crack = MathHelper.clamp(crack, 0, 3);
        matrices.push();
        matrices.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        // Нарастает от середины лица.
        float scale = MathHelper.lerp(grow, 0.82F, 1.0F);
        matrices.translate(0.0F, -3.0F, -4.0F);
        matrices.scale(scale, scale, scale);
        matrices.translate(0.0F, 3.0F, 4.0F);
        int alpha = (int) (MathHelper.clamp(grow, 0.0F, 1.0F) * 255.0F);
        Identifier texture = (finalForm ? FINAL : MASK)[crack];
        int boneLight = LightmapTextureManager.pack(Math.max(LightmapTextureManager.getBlockLightCoordinates(light), MIN_BLOCK_LIGHT),
                LightmapTextureManager.getSkyLightCoordinates(light));
        Emitter bone = new Emitter(matrices.peek(), consumers.getBuffer(RenderLayer.getEntityTranslucent(texture)), boneLight, alpha << 24);
        face(bone);
        rims(bone);
        for (Vertex v : TEETH) {
            bone.vertex(v.p(), v.u(), v.v(), v.n(), v.rgb());
        }
        if (finalForm) {
            horn(bone, 1, crack >= 3 ? 0.45F : (crack >= 2 ? 0.8F : 1.0F));
        }
        if (glow > 0) {
            Emitter eyes = new Emitter(matrices.peek(), consumers.getBuffer(RenderLayer.getEyes(EYES)), LightmapTextureManager.MAX_LIGHT_COORDINATE, 255 << 24);
            eyeCells(eyes, (glow << 16) | (glow << 8) | glow);
        }
        matrices.pop();
    }

    // ---------------------------------------------------------------- форма

    static float smooth(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float gauss(float x) {
        return (float) Math.exp(-x * x);
    }

    private static float band(float x, float from, float to, float soft) {
        return MathHelper.clamp((x - from) / soft, 0.0F, 1.0F) * MathHelper.clamp((to - x) / soft, 0.0F, 1.0F);
    }

    /** Верхний край маски: плавный купол, выше всего посередине лба. */
    static float top(float alpha) {
        float s = Math.abs(alpha) / ALPHA;
        return TOP + 2.5F * s * s;
    }

    /** Нижний край: посередине — длинный подбородок, по бокам маска кончается у челюсти. */
    static float bottom(float alpha) {
        return MathHelper.lerp(smooth(0.12F, 0.7F, Math.abs(alpha) / ALPHA), CHIN, JAW);
    }

    /** Насколько лицо у́же на высоте y: ниже глаз сужается к длинному подбородку (голова за ним остаётся видна). */
    static float jaw(float y) {
        float t = MathHelper.clamp((y + 3.4F) / 6.2F, 0.0F, 1.0F);
        return 2.85F * (0.75F * t + 0.25F * t * t);
    }

    /** Точка оболочки без рельефа: луч из оси головы под углом α до скруглённого квадрата на высоте y. */
    static Vector3f base(float alphaDeg, float y) {
        float lean = smooth(-7.7F, TOP, y), chin = smooth(-1.6F, CHIN, y);
        float a = A0 * (1.0F - 0.16F * lean) - jaw(y), b = B0 * (1.0F - 0.2F * lean) * (1.0F - 0.05F * chin);
        double rad = Math.toRadians(alphaDeg), dx = Math.sin(rad), dz = -Math.cos(rad);
        double r = Math.pow(Math.pow(Math.abs(dx / a), SQUARENESS) + Math.pow(Math.abs(dz / b), SQUARENESS), -1.0 / SQUARENESS);
        return new Vector3f((float) (r * dx), y, (float) (r * dz));
    }

    /** Нормаль оболочки без рельефа (наружу от оси головы). */
    private static Vector3f baseNormal(float alpha, float y) {
        Vector3f p = base(alpha, y);
        Vector3f da = base(alpha + 0.5F, y).sub(base(alpha - 0.5F, y));
        Vector3f dy = base(alpha, y + 0.05F).sub(base(alpha, y - 0.05F));
        Vector3f n = da.cross(dy);
        if (n.x * p.x + n.z * p.z < 0.0F) {
            n.negate();
        }
        return n.normalize();
    }

    /** Угол α, под которым оболочка на высоте y проходит через фронтальный x (только перед ушами). */
    private static float alphaFor(float x, float y) {
        float lo = -89.0F, hi = 89.0F;
        for (int k = 0; k < 28; k++) {
            float mid = (lo + hi) * 0.5F;
            if (base(mid, y).x < x) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) * 0.5F;
    }

    /** Прорезь глаза-«линза» с острыми уголками: меньше 1 — внутри; {@code grow} расширяет её. */
    static float eye(float x, float y, float grow) {
        float ax = Math.abs(x);
        float dx = (ax - EYE_X) / (EYE_HALF + 0.2F * grow);
        if (Math.abs(dx) >= 1.0F) {
            return 9.0F;
        }
        float h = (EYE_H + 0.17F * grow) * (1.0F - dx * dx);
        float center = EYE_Y - EYE_TILT * (ax - EYE_X);
        return Math.abs(y - center) / Math.max(h, 1.0E-3F);
    }

    static float mouthTop(float x) {
        return MOUTH_TOP + 0.04F * x * x;
    }

    /** Нижняя губа: глубокая дуга, у уголков сходится с верхней. */
    static float mouthBottom(float x) {
        float s = Math.abs(x) / MOUTH_HALF;
        return mouthTop(x) + MOUTH_H * MathHelper.sqrt(Math.max(0.0F, 1.0F - s * s * s));
    }

    /** Челюсть выдаётся вперёд: глубокому рту есть куда уходить, не задевая голову. */
    private static float muzzle(float x, float y) {
        return 0.38F * gauss((y + 0.7F) / 2.0F) * gauss(x / 3.6F);
    }

    /**
     * Лепка лица (пиксели вдоль нормали). Всё плавное: сетка крупнее прорези глаза, ступеньки превратились бы в
     * рваные шипы; чёткие края рисует текстура.
     */
    private static float relief(float x, float y) {
        float ax = Math.abs(x);
        float d = muzzle(x, y);
        // Глазница — широкая впадина вокруг прорези.
        float eyeLine = EYE_Y - EYE_TILT * (ax - EYE_X);
        float ex = (ax - EYE_X) / (EYE_HALF + 0.55F), ey = (y - eyeLine) / (EYE_H + 0.55F);
        d -= 0.42F * smooth(1.0F, 0.35F, MathHelper.sqrt(ex * ex + ey * ey));
        // Злые надбровья: дуга над глазницей, к переносице опускается.
        d += 0.35F * gauss((y - (eyeLine - 0.78F - 0.12F * (ax - 0.6F))) / 0.42F) * band(ax, 0.2F, 4.2F, 0.6F);
        // Лоб и шов посередине него.
        d += 0.15F * gauss((y + 6.8F) / 1.2F) * gauss(x / 2.2F);
        d -= 0.1F * gauss(x / 0.22F) * band(y, -8.7F, -5.2F, 0.6F);
        // Переносица и нос с ноздрями.
        d += 0.28F * gauss(x / (0.42F + 0.05F * (y + 5.2F))) * band(y, -5.3F, -2.6F, 0.6F);
        d += 0.38F * gauss(x / 0.7F) * gauss((y + 2.8F) / 0.42F);
        d -= 0.12F * gauss((ax - 0.46F) / 0.2F) * gauss((y + 2.52F) / 0.18F);
        // Скулы и впадины под ними.
        d += 0.3F * gauss((ax - 3.1F) / 0.75F) * gauss((y + 2.75F) / 0.55F);
        d -= 0.18F * gauss((ax - 3.7F) / 0.5F) * gauss((y + 1.25F) / 0.6F);
        // Губы и провал рта: m — насколько точка внутри рта (минус — снаружи).
        float m = Math.min(Math.min(y - mouthTop(x), mouthBottom(x) - y), MOUTH_HALF - ax);
        d += 0.22F * gauss(m / 0.32F) + MOUTH_FLOOR * smooth(-0.05F, 0.38F, m);
        // Подбородок.
        d += 0.28F * gauss((y - 1.9F) / 0.7F) * gauss(x / 1.2F);
        return d;
    }

    private static float alpha(int i) {
        return MathHelper.lerp((float) i / NU, -ALPHA, ALPHA);
    }

    private static float height(int i, int j) {
        float a = alpha(i);
        return MathHelper.lerp((float) j / NV, top(a), bottom(a));
    }

    private static void build() {
        float[][] heights = new float[NU + 1][NV + 1];
        for (int i = 0; i <= NU; i++) {
            for (int j = 0; j <= NV; j++) {
                float a = alpha(i), y = height(i, j);
                Vector3f p = base(a, y);
                FRONT_X[i][j] = p.x;
                heights[i][j] = relief(p.x, y);
                SURFACE[i][j] = p.add(baseNormal(a, y).mul(heights[i][j]));
            }
        }
        for (int i = 0; i <= NU; i++) {
            for (int j = 0; j <= NV; j++) {
                NORMALS[i][j] = normal(i, j);
                SHADE[i][j] = cavityShade(heights, i, j);
            }
        }
    }

    /** Тень в складке: чем точка глубже соседей и глубже самой кости, тем темнее; тень чуть сиреневая, как у кости в аниме. */
    private static int cavityShade(float[][] heights, int i, int j) {
        float sum = 0.0F;
        for (int di = -2; di <= 2; di += 2) {
            for (int dj = -2; dj <= 2; dj += 2) {
                sum += heights[MathHelper.clamp(i + di, 0, NU)][MathHelper.clamp(j + dj, 0, NV)];
            }
        }
        float h = heights[i][j];
        float cavity = MathHelper.clamp((sum / 9.0F - h) * 3.2F, 0.0F, 0.45F) + MathHelper.clamp(-h * 0.55F, 0.0F, 0.35F);
        return rgb(1.0F - cavity * 0.95F, 1.0F - cavity * 1.05F, 1.0F - cavity * 0.8F);
    }

    private static int rgb(float r, float g, float b) {
        return ((int) (MathHelper.clamp(r, 0.0F, 1.0F) * 255.0F) << 16) | ((int) (MathHelper.clamp(g, 0.0F, 1.0F) * 255.0F) << 8)
                | (int) (MathHelper.clamp(b, 0.0F, 1.0F) * 255.0F);
    }

    /** Нормаль сетки наружу (от оси головы). */
    private static Vector3f normal(int i, int j) {
        Vector3f du = new Vector3f(SURFACE[Math.min(NU, i + 1)][j]).sub(SURFACE[Math.max(0, i - 1)][j]);
        Vector3f dv = new Vector3f(SURFACE[i][Math.min(NV, j + 1)]).sub(SURFACE[i][Math.max(0, j - 1)]);
        Vector3f n = du.cross(dv);
        Vector3f p = SURFACE[i][j];
        if (n.x * p.x + n.z * p.z < 0.0F) {
            n.negate();
        }
        return n.lengthSquared() < 1.0E-10F ? new Vector3f(p.x, 0.0F, p.z).normalize() : n.normalize();
    }

    // ---------------------------------------------------------------- части

    private static float faceU(int i) {
        return (float) i / NU;
    }

    private static float faceV(int j) {
        return (float) j / NV * (160.0F / 256.0F);
    }

    private static void face(Emitter e) {
        for (int i = 0; i < NU; i++) {
            for (int j = 0; j < NV; j++) {
                e.vertex(SURFACE[i][j], faceU(i), faceV(j), NORMALS[i][j], SHADE[i][j]);
                e.vertex(SURFACE[i][j + 1], faceU(i), faceV(j + 1), NORMALS[i][j + 1], SHADE[i][j + 1]);
                e.vertex(SURFACE[i + 1][j + 1], faceU(i + 1), faceV(j + 1), NORMALS[i + 1][j + 1], SHADE[i + 1][j + 1]);
                e.vertex(SURFACE[i + 1][j], faceU(i + 1), faceV(j), NORMALS[i + 1][j], SHADE[i + 1][j]);
            }
        }
    }

    /** Свечение глаз — только клетки вокруг прорезей (остальная текстура глаз чёрная и ничего не добавляет). */
    private static void eyeCells(Emitter e, int rgb) {
        for (int i = 0; i < NU; i++) {
            for (int j = 0; j < NV; j++) {
                float x = (FRONT_X[i][j] + FRONT_X[i + 1][j + 1]) * 0.5F, y = (height(i, j) + height(i + 1, j + 1)) * 0.5F;
                if (eye(x, y, 2.5F) < 1.0F) {
                    e.vertex(SURFACE[i][j], faceU(i), faceV(j), NORMALS[i][j], rgb);
                    e.vertex(SURFACE[i][j + 1], faceU(i), faceV(j + 1), NORMALS[i][j + 1], rgb);
                    e.vertex(SURFACE[i + 1][j + 1], faceU(i + 1), faceV(j + 1), NORMALS[i + 1][j + 1], rgb);
                    e.vertex(SURFACE[i + 1][j], faceU(i + 1), faceV(j), NORMALS[i + 1][j], rgb);
                }
            }
        }
    }

    /** Толщина кости по краю оболочки: полоса от лицевой поверхности внутрь. */
    private static void rims(Emitter e) {
        float u0 = 64.0F / 256.0F, u1 = 128.0F / 256.0F, v0 = 224.0F / 256.0F, v1 = 1.0F;
        for (int i = 0; i < NU; i++) {
            rim(e, SURFACE[i][0], SURFACE[i + 1][0], NORMALS[i][0], NORMALS[i + 1][0], u0, v0, u1, v1);
            rim(e, SURFACE[i + 1][NV], SURFACE[i][NV], NORMALS[i + 1][NV], NORMALS[i][NV], u0, v0, u1, v1);
        }
        for (int j = 0; j < NV; j++) {
            rim(e, SURFACE[0][j + 1], SURFACE[0][j], NORMALS[0][j + 1], NORMALS[0][j], u0, v0, u1, v1);
            rim(e, SURFACE[NU][j], SURFACE[NU][j + 1], NORMALS[NU][j], NORMALS[NU][j + 1], u0, v0, u1, v1);
        }
    }

    private static void rim(Emitter e, Vector3f a, Vector3f b, Vector3f na, Vector3f nb, float u0, float v0, float u1, float v1) {
        Vector3f ia = new Vector3f(a).sub(new Vector3f(na).mul(THICKNESS)), ib = new Vector3f(b).sub(new Vector3f(nb).mul(THICKNESS));
        Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(na)).normalize();
        e.vertex(a, u0, v0, n, 0xFFFFFF);
        e.vertex(b, u1, v0, n, 0xFFFFFF);
        e.vertex(ib, u1, v1, n, 0xC8C4D0);
        e.vertex(ia, u0, v1, n, 0xC8C4D0);
    }

    /**
     * Оскал: восемь зубов верхнего ряда растут из-под верхней губы, семь нижних — из-под нижней, острия входят
     * между зубами другого ряда. Нижний ряд у́же верхнего, поэтому острия чуть сведены к середине. Клыки длиннее.
     */
    private static void teeth() {
        float inward = 0.92F;
        for (int k = 0; k < 8; k++) {
            float x = (k - 3.5F) * 0.84F, half = 0.44F, tipX = x * inward;
            float reach = (mouthBottom(tipX) - mouthTop(x)) * (k == 1 || k == 6 ? 0.88F : 0.78F);
            tooth(x - half, mouthTop(x - half) - 0.05F, x + half, mouthTop(x + half) - 0.05F, tipX, mouthTop(x) + reach);
        }
        for (int k = 0; k < 7; k++) {
            float x = (k - 3.0F) * 0.84F * inward, half = 0.42F, tipX = x / inward;
            float reach = (mouthBottom(x) - mouthTop(tipX)) * (k == 1 || k == 5 ? 0.86F : 0.76F);
            tooth(x - half, mouthBottom(x - half) + 0.05F, x + half, mouthBottom(x + half) + 0.05F, tipX, mouthBottom(x) - reach);
        }
    }

    /**
     * Настоящий зуб: сечение — полуокружность от дна рта до выпуклой передней стороны, вдоль зуба оно сужается к
     * острию, а передняя сторона выгибается наружу и у острия снова уходит внутрь. Нормали гладкие, у корня и
     * по бокам — тень.
     */
    private static void tooth(float x0, float y0, float x1, float y1, float tipX, float tipY) {
        int ns = 9, na = 6;
        float rootX = (x0 + x1) * 0.5F, rootY = (y0 + y1) * 0.5F, halfX = (x1 - x0) * 0.5F, halfY = (y1 - y0) * 0.5F;
        Vector3f[][] points = new Vector3f[ns + 1][na + 1];
        int[][] shade = new int[ns + 1][na + 1];
        for (int k = 0; k <= ns; k++) {
            float s = (float) k / ns;
            float width = (float) Math.pow(1.0F - s, 0.7F);
            float x = MathHelper.lerp(s, rootX, tipX), y = MathHelper.lerp(s, rootY, tipY);
            float front = 0.02F + 0.26F * MathHelper.sin(MathHelper.PI * s) - 0.2F * s * s;
            for (int j = 0; j <= na; j++) {
                float angle = MathHelper.PI * j / na, across = -MathHelper.cos(angle), arc = MathHelper.sin(angle);
                float fx = x + halfX * across * width, fy = y + halfY * across * width;
                float alpha = alphaFor(fx, fy);
                Vector3f n = baseNormal(alpha, fy);
                float lift = muzzle(fx, fy) + MOUTH_FLOOR + (front - MOUTH_FLOOR) * arc;
                points[k][j] = base(alpha, fy).add(new Vector3f(n).mul(lift));
                float ao = (0.74F + 0.26F * arc) * (0.8F + 0.2F * smooth(0.0F, 0.32F, s));
                shade[k][j] = rgb(ao, ao * 0.99F, ao * 1.02F);
            }
        }
        Vector3f[][] normals = new Vector3f[ns + 1][na + 1];
        for (int k = 0; k <= ns; k++) {
            // У самого острия сетка сходится в точку — там берём нормаль предыдущего ряда.
            int row = Math.min(k, ns - 1);
            for (int j = 0; j <= na; j++) {
                Vector3f along = new Vector3f(points[row + 1][j]).sub(points[Math.max(0, row - 1)][j]);
                Vector3f side = new Vector3f(points[row][Math.min(na, j + 1)]).sub(points[row][Math.max(0, j - 1)]);
                Vector3f n = along.cross(side);
                if (n.z > 0.0F) {
                    n.negate();
                }
                normals[k][j] = n.lengthSquared() < 1.0E-10F ? new Vector3f(0.0F, 0.0F, -1.0F) : n.normalize();
            }
        }
        float uSpan = 64.0F / 256.0F, vRoot = 224.0F / 256.0F, vSpan = 32.0F / 256.0F;
        for (int k = 0; k < ns; k++) {
            for (int j = 0; j < na; j++) {
                float u0 = uSpan * j / na, u1 = uSpan * (j + 1) / na, v0 = vRoot + vSpan * k / ns, v1 = vRoot + vSpan * (k + 1) / ns;
                TEETH.add(new Vertex(points[k][j], u0, v0, normals[k][j], shade[k][j]));
                TEETH.add(new Vertex(points[k + 1][j], u0, v1, normals[k + 1][j], shade[k + 1][j]));
                TEETH.add(new Vertex(points[k + 1][j + 1], u1, v1, normals[k + 1][j + 1], shade[k + 1][j + 1]));
                TEETH.add(new Vertex(points[k][j + 1], u1, v0, normals[k][j + 1], shade[k][j + 1]));
            }
        }
    }

    /**
     * Рог финальной маски: растёт сверху сбоку головы ({@code side} 1 — левая сторона маски), уходит вверх и
     * загибается далеко вперёд, сужаясь к острию. {@code keep} &lt; 1 — обломан: короче и с рваным торцом.
     */
    private static void horn(Emitter e, int side, float keep) {
        int segments = 22, around = 12;
        float rootAlpha = side * 48.0F, rootY = -6.9F;
        Vector3f base = base(rootAlpha, rootY).sub(baseNormal(rootAlpha, rootY).mul(1.6F));
        Vector3f[] rings = new Vector3f[(segments + 1) * around];
        Vector3f[] centers = new Vector3f[segments + 1];
        float[] radius = new float[segments + 1];
        for (int k = 0; k <= segments; k++) {
            float s = keep * k / segments;
            centers[k] = new Vector3f(base).add(side * (2.2F * s - 0.2F * s * s), -(4.8F * s + 0.5F * s * s), -(0.6F * s + 5.2F * s * s));
            radius[k] = 1.45F * (float) Math.pow(1.0F - s, 0.75F) + 0.05F;
        }
        // Рамка вдоль оси рога (перенос без закручивания).
        Vector3f prevN = null;
        for (int k = 0; k <= segments; k++) {
            Vector3f t = new Vector3f(centers[Math.min(segments, k + 1)]).sub(centers[Math.max(0, k - 1)]).normalize();
            Vector3f n = prevN == null ? new Vector3f(1.0F, 0.0F, 0.0F).cross(t).normalize()
                    : new Vector3f(prevN).sub(new Vector3f(t).mul(prevN.dot(t))).normalize();
            if (n.lengthSquared() < 1.0E-6F) {
                n.set(0.0F, 0.0F, 1.0F);
            }
            prevN = n;
            Vector3f b = new Vector3f(t).cross(n).normalize();
            boolean broken = keep < 1.0F && k == segments;
            for (int a = 0; a < around; a++) {
                float ang = MathHelper.TAU * a / around;
                float r = radius[k] * (broken ? 0.55F + 0.45F * Math.abs(MathHelper.sin(a * 2.3F + side)) : 1.0F);
                Vector3f p = new Vector3f(centers[k]).add(new Vector3f(n).mul(MathHelper.cos(ang) * r)).add(new Vector3f(b).mul(MathHelper.sin(ang) * r));
                if (broken) {
                    p.add(new Vector3f(t).mul(0.35F * MathHelper.sin(a * 1.7F + 0.5F)));
                }
                rings[k * around + a] = p;
            }
        }
        float vOff = 160.0F / 256.0F, vSpan = 64.0F / 256.0F;
        for (int k = 0; k < segments; k++) {
            for (int a = 0; a < around; a++) {
                int a1 = (a + 1) % around;
                Vector3f p00 = rings[k * around + a], p01 = rings[k * around + a1], p10 = rings[(k + 1) * around + a], p11 = rings[(k + 1) * around + a1];
                Vector3f n = new Vector3f(p00).add(p01).add(p10).add(p11).mul(0.25F).sub(new Vector3f(centers[k]).lerp(centers[k + 1], 0.5F)).normalize();
                float u0 = keep * k / segments, u1 = keep * (k + 1) / segments;
                float v0 = vOff + vSpan * a / around, v1 = vOff + vSpan * (a + 1) / around;
                e.vertex(p00, u0, v0, n, 0xFFFFFF);
                e.vertex(p10, u1, v0, n, 0xFFFFFF);
                e.vertex(p11, u1, v1, n, 0xFFFFFF);
                e.vertex(p01, u0, v1, n, 0xFFFFFF);
            }
        }
        // Торец: у целого рога — острие, у обломанного — рваный скол (тёмная сердцевина кости).
        Vector3f tipCenter = centers[segments];
        Vector3f axis = new Vector3f(centers[segments]).sub(centers[segments - 1]).normalize();
        float tu = keep < 1.0F ? 136.0F / 256.0F : 0.99F, tv = keep < 1.0F ? 232.0F / 256.0F : vOff + 0.5F * vSpan;
        for (int a = 0; a < around; a++) {
            Vector3f p0 = rings[segments * around + a], p1 = rings[segments * around + (a + 1) % around];
            e.vertex(p0, tu, tv, axis, 0xFFFFFF);
            e.vertex(p1, tu, tv, axis, 0xFFFFFF);
            e.vertex(tipCenter, tu, tv, axis, 0xFFFFFF);
            e.vertex(tipCenter, tu, tv, axis, 0xFFFFFF);
        }
    }

    // ---------------------------------------------------------------- вывод вершин

    private record Vertex(Vector3f p, float u, float v, Vector3f n, int rgb) {
    }

    private record Emitter(MatrixStack.Entry entry, VertexConsumer vertices, int light, int alpha) {
        void vertex(Vector3f p, float u, float v, Vector3f n, int rgb) {
            this.vertices.vertex(this.entry.getPositionMatrix(), p.x, p.y, p.z).color(this.alpha | rgb).texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV).light(this.light).normal(this.entry, n.x, n.y, n.z);
        }
    }
}
