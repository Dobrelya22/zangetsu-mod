package com.zangetsu.client.render;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Физика и жар финальной формы одного игрока. Ткань и цепи живут в мире: каждая точка помнит свою скорость, тянется за тем, к чему пришита,
 * падает, отстаёт от рывка, наматывается при развороте и обтекает руки, ноги и туловище. Считается каждый кадр по настоящему времени, поэтому
 * движение плавное при любой частоте кадров. Жар — отдельное число: растёт от бега и ударов, от него огонь выше, быстрее и бешенее.
 *
 * <p>Координаты физики — блоки от опорной точки рядом с игроком (в float точности на большом удалении от нуля мира не хватило бы).
 */
public final class FormPhysics {
    private static final Int2ObjectOpenHashMap<FormPhysics> ALL = new Int2ObjectOpenHashMap<>();
    /** Самый крупный шаг счёта (тики) и через сколько тиков без отрисовки всё начинается заново. */
    private static final float STEP = 0.34F, STALE = 20.0F;
    /** Прыжок опоры больше этого (блоки за кадр) — телепорт или шаг: ткань переносится вместе с игроком. */
    private static final float JUMP = 2.5F;
    private static final float MAX_HEAT = 2.2F, HEAT_DECAY = 0.05F;
    /** На сколько сносит огонь на единицу скорости его основания и самый сильный снос. */
    private static final float WIND_PER_SPEED = 1.7F, MAX_WIND = 1.8F;

    private double originX, originY, originZ;
    private boolean hasOrigin;
    private final Matrix4f simFromSpace = new Matrix4f();
    private final Matrix4f spaceFromSim = new Matrix4f();
    private final Matrix4f bodyFromSim = new Matrix4f();
    private final List<Simulated> simulated = new ArrayList<>();
    private final Map<String, Cloth> cloths = new HashMap<>();
    private final Map<String, Rope> ropes = new HashMap<>();
    private final Map<String, FlameWind> winds = new HashMap<>();
    private float heat, heatAt = -1.0F, flameTime, flameAt = -1.0F, nextJingle;

    private FormPhysics() {
    }

    public static FormPhysics of(Entity entity) {
        return ALL.computeIfAbsent(entity.getId(), id -> new FormPhysics());
    }

    public static void forget(int entityId) {
        ALL.remove(entityId);
    }

    public static void reset() {
        ALL.clear();
    }

    // ---------------------------------------------------------------- жар

    /** Что-то вспыхнуло: удар, Гецуга, казнь, молния. */
    public void kick(float amount) {
        this.heat = Math.min(MAX_HEAT, this.heat + amount);
    }

    /** Текущий жар: остывает со временем, а бег не даёт ему упасть ниже своего уровня. */
    public float heat(float now, float speed) {
        if (this.heatAt >= 0.0F && now > this.heatAt) {
            this.heat = Math.max(0.0F, this.heat - (now - this.heatAt) * HEAT_DECAY);
        }
        this.heatAt = now;
        this.heat = Math.max(this.heat, Math.min(0.75F, speed * 2.4F));
        return this.heat;
    }

    /** Время для огня: при жаре бежит быстрее, но без рывков, потому что копится, а не умножается. */
    public float flameTime(float now) {
        if (this.flameAt >= 0.0F && now > this.flameAt) {
            this.flameTime += (now - this.flameAt) * (1.0F + 1.3F * this.heat);
        }
        this.flameAt = now;
        return this.flameTime;
    }

    // ---------------------------------------------------------------- пространство

    /**
     * Войти в кадр: {@code root} — матрица корня модели игрока. Вернёт false, если модель рисуется не в мире (игрок в инвентаре):
     * физику тогда считать нельзя, а рисовать надо то, что уже посчитано.
     */
    public boolean enterWorld(Matrix4f root, Entity player, float tickDelta) {
        Vec3d camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        origin(player);
        this.simFromSpace.translation((float) (camera.x - this.originX), (float) (camera.y - this.originY), (float) (camera.z - this.originZ));
        this.spaceFromSim.set(this.simFromSpace).invert();
        Vector3f feet = root.transformPosition(0.0F, 1.5F, 0.0F, new Vector3f());
        this.simFromSpace.transformPosition(feet);
        double dx = feet.x + this.originX - MathHelper.lerp(tickDelta, player.prevX, player.getX());
        double dy = feet.y + this.originY - MathHelper.lerp(tickDelta, player.prevY, player.getY());
        double dz = feet.z + this.originZ - MathHelper.lerp(tickDelta, player.prevZ, player.getZ());
        return dx * dx + dy * dy + dz * dz < 9.0;
    }

    /** То же для руки от первого лица: там матрицы стоят в координатах камеры. */
    public void enterFirstPerson(Entity player) {
        var camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        origin(player);
        Vec3d at = camera.getPos();
        this.simFromSpace.translation((float) (at.x - this.originX), (float) (at.y - this.originY), (float) (at.z - this.originZ))
                .rotate(new Quaternionf(camera.getRotation()));
        this.spaceFromSim.set(this.simFromSpace).invert();
    }

    /** Опора рядом с игроком; когда он далеко ушёл, всё переносится на новую. */
    private void origin(Entity player) {
        if (!this.hasOrigin) {
            this.hasOrigin = true;
            this.originX = player.getX();
            this.originY = player.getY();
            this.originZ = player.getZ();
            return;
        }
        double dx = player.getX() - this.originX, dy = player.getY() - this.originY, dz = player.getZ() - this.originZ;
        if (dx * dx + dy * dy + dz * dz > 48.0 * 48.0) {
            this.originX += dx;
            this.originY += dy;
            this.originZ += dz;
            for (Simulated s : this.simulated) {
                s.shift((float) -dx, (float) -dy, (float) -dz);
            }
        }
    }

    /** Как перевести физику в координаты туловища. В инвентаре туловище нарисовано не в мире, поэтому берётся последнее настоящее положение. */
    public Matrix4f bodyFromSim(boolean stepped, Matrix4f simFromBody) {
        if (stepped) {
            this.bodyFromSim.set(simFromBody).invert();
        }
        return this.bodyFromSim;
    }

    public Matrix4f simFromLocal(Matrix4f local) {
        return new Matrix4f(this.simFromSpace).mul(local);
    }

    public Vector3f spaceDirection(Vector3f simDirection) {
        return this.spaceFromSim.transformDirection(new Vector3f(simDirection));
    }

    /** Высота земли под ногами в координатах физики; когда игрок в воздухе — бесконечно низко. */
    public float ground(Entity player) {
        return player.isOnGround() ? (float) (player.getY() - this.originY) + 0.03F : Float.NEGATIVE_INFINITY;
    }

    // ---------------------------------------------------------------- хозяйство

    public Cloth cloth(String key, float[][][] rest, int pinnedRows, float[] softness, float tear) {
        return this.cloths.computeIfAbsent(key, k -> {
            Cloth cloth = new Cloth(rest, pinnedRows, softness, tear);
            this.simulated.add(cloth);
            return cloth;
        });
    }

    public Rope rope(String key, int links) {
        return this.ropes.computeIfAbsent(key, k -> {
            Rope rope = new Rope(links);
            this.simulated.add(rope);
            return rope;
        });
    }

    /** Ветер для огня у подвижной точки: она сама движется вместе с рукой, поэтому пламя отстаёт от неё, как от настоящего рывка. */
    public Vector3f flameWind(String key, Vector3f at, float now) {
        return this.winds.computeIfAbsent(key, k -> new FlameWind()).update(at, now);
    }

    /** Толчок всей ткани и всем цепям: {@code (x, y, z)} — сколько скорости добавить (блоки за тик). */
    public void impulse(float x, float y, float z) {
        for (Simulated s : this.simulated) {
            s.impulse(x, y, z);
        }
    }

    /** Удар от точки: всё разлетается от неё. Точка — координаты мира. */
    public void blast(double x, double y, double z, float strength) {
        for (Simulated s : this.simulated) {
            s.blast((float) (x - this.originX), (float) (y - this.originY), (float) (z - this.originZ), strength);
        }
    }

    /** Цепи звенят, когда их дёрнуло достаточно сильно. Сила — разность скоростей конца цепи и её крепления. */
    public void jingle(PlayerEntity player, float now, float strength) {
        if (strength < 0.13F || now < this.nextJingle) {
            return;
        }
        this.nextJingle = now + 5.0F + player.getRandom().nextFloat() * 4.0F;
        float volume = Math.min(0.55F, 0.12F + strength * 0.5F), pitch = 0.9F + player.getRandom().nextFloat() * 0.5F;
        player.getWorld().playSound(player.getX(), player.getY() + 0.9, player.getZ(),
                strength > 0.3F ? SoundEvents.BLOCK_CHAIN_PLACE : SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.PLAYERS, volume, pitch, false);
    }

    /** Капсула столкновений: отрезок {a, b} и радиус (блоки). */
    public static float[] capsule(Matrix4f simFromLocal, float ax, float ay, float az, float bx, float by, float bz, float radius) {
        Vector3f a = simFromLocal.transformPosition(ax, ay, az, new Vector3f()), b = simFromLocal.transformPosition(bx, by, bz, new Vector3f());
        return new float[]{a.x, a.y, a.z, b.x, b.y, b.z, radius};
    }

    /** Вытолкнуть точку (px[i], py[i], pz[i]) из капсул и не пустить её под землю. */
    private static void collide(float[] px, float[] py, float[] pz, int i, float[][] capsules, float ground) {
        for (float[] c : capsules) {
            float ax = c[0], ay = c[1], az = c[2];
            float dx = c[3] - ax, dy = c[4] - ay, dz = c[5] - az;
            float length = dx * dx + dy * dy + dz * dz;
            float t = length < 1.0E-6F ? 0.0F : MathHelper.clamp(((px[i] - ax) * dx + (py[i] - ay) * dy + (pz[i] - az) * dz) / length, 0.0F, 1.0F);
            float ox = px[i] - (ax + dx * t), oy = py[i] - (ay + dy * t), oz = pz[i] - (az + dz * t);
            float distance = ox * ox + oy * oy + oz * oz, r = c[6];
            if (distance < r * r && distance > 1.0E-8F) {
                float d = MathHelper.sqrt(distance), push = (r - d) / d;
                px[i] += ox * push;
                py[i] += oy * push;
                pz[i] += oz * push;
            }
        }
        if (py[i] < ground) {
            py[i] = ground;
        }
    }

    private abstract static class Simulated {
        float[] x, y, z, vx, vy, vz, px, py, pz;
        float last = -1.0F;
        boolean ready;

        void allocate(int size) {
            this.x = new float[size];
            this.y = new float[size];
            this.z = new float[size];
            this.vx = new float[size];
            this.vy = new float[size];
            this.vz = new float[size];
            this.px = new float[size];
            this.py = new float[size];
            this.pz = new float[size];
        }

        void shift(float dx, float dy, float dz) {
            for (int i = 0; i < this.x.length; i++) {
                this.x[i] += dx;
                this.y[i] += dy;
                this.z[i] += dz;
            }
        }

        void impulse(float ix, float iy, float iz) {
            for (int i = 0; i < this.vx.length; i++) {
                this.vx[i] += ix;
                this.vy[i] += iy;
                this.vz[i] += iz;
            }
        }

        void blast(float cx, float cy, float cz, float strength) {
            for (int i = 0; i < this.vx.length; i++) {
                float dx = this.x[i] - cx, dy = this.y[i] - cy, dz = this.z[i] - cz;
                float d = Math.max(0.2F, MathHelper.sqrt(dx * dx + dy * dy + dz * dz));
                this.vx[i] += dx / d * strength;
                this.vy[i] += dy / d * strength;
                this.vz[i] += dz / d * strength;
            }
        }
    }

    /**
     * Ткань: сетка точек, связанных по длине. Верхние строки пришиты к телу, остальные свободны; слабая память о своей форме не даёт ей
     * свалиться в ком, но с рывком и ветром не спорит. Там, где полотно рвётся на полосы, поперечные связи слабые: полосы бьются сами по себе.
     */
    public static final class Cloth extends Simulated {
        private static final float GRAVITY = 0.042F, DRAG = 0.045F;
        private static final int ITERATIONS = 4;
        private final int cols, rows, pinnedRows;
        private final float[] restX, restY, restZ, softness;
        private final int[] linkA, linkB;
        private final float[] linkLength, linkStiffness;
        private final boolean[] linkAlong;

        /** {@code rest} — покой в координатах тела (блоки), {@code softness} — память о форме по строкам, {@code tear} — с какой строки полосы. */
        private Cloth(float[][][] rest, int pinnedRows, float[] softness, float tear) {
            this.cols = rest.length;
            this.rows = rest[0].length;
            this.pinnedRows = pinnedRows;
            this.softness = softness;
            int size = this.cols * this.rows;
            this.allocate(size);
            this.restX = new float[size];
            this.restY = new float[size];
            this.restZ = new float[size];
            for (int c = 0; c < this.cols; c++) {
                for (int j = 0; j < this.rows; j++) {
                    int i = this.index(c, j);
                    this.restX[i] = rest[c][j][0];
                    this.restY[i] = rest[c][j][1];
                    this.restZ[i] = rest[c][j][2];
                }
            }
            List<float[]> links = new ArrayList<>();
            for (int c = 0; c < this.cols; c++) {
                for (int j = 0; j < this.rows; j++) {
                    if (j + 1 < this.rows) {
                        links.add(new float[]{this.index(c, j), this.index(c, j + 1), 0.95F, 1.0F});
                    }
                    if (j + 2 < this.rows) {
                        links.add(new float[]{this.index(c, j), this.index(c, j + 2), 0.22F, 1.0F});
                    }
                    if (c + 1 < this.cols) {
                        links.add(new float[]{this.index(c, j), this.index(c + 1, j), j >= tear ? 0.16F : 0.75F, 0.0F});
                    }
                }
            }
            int count = links.size();
            this.linkA = new int[count];
            this.linkB = new int[count];
            this.linkLength = new float[count];
            this.linkStiffness = new float[count];
            this.linkAlong = new boolean[count];
            for (int k = 0; k < count; k++) {
                float[] link = links.get(k);
                int a = (int) link[0], b = (int) link[1];
                this.linkA[k] = a;
                this.linkB[k] = b;
                float dx = this.restX[a] - this.restX[b], dy = this.restY[a] - this.restY[b], dz = this.restZ[a] - this.restZ[b];
                this.linkLength[k] = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
                this.linkStiffness[k] = link[2];
                this.linkAlong[k] = link[3] > 0.5F;
            }
        }

        private int index(int c, int j) {
            return c * this.rows + j;
        }

        /** Рассчитать ткань к моменту {@code now}. {@code grow} — насколько полотно уже развернулось (при превращении). */
        public void step(Matrix4f simFromBody, float now, float[][] capsules, float ground, float grow, float speed, float heat) {
            int size = this.x.length;
            float[] tx = new float[size], ty = new float[size], tz = new float[size];
            Vector3f t = new Vector3f();
            for (int c = 0; c < this.cols; c++) {
                int topIndex = this.index(c, this.pinnedRows - 1);
                for (int j = 0; j < this.rows; j++) {
                    int i = this.index(c, j);
                    // Пока плащ не развернулся, всё лежит у плеч и вытягивается из них.
                    float k = j < this.pinnedRows ? 1.0F : grow;
                    simFromBody.transformPosition(this.restX[topIndex] + (this.restX[i] - this.restX[topIndex]) * k,
                            this.restY[topIndex] + (this.restY[i] - this.restY[topIndex]) * k,
                            this.restZ[topIndex] + (this.restZ[i] - this.restZ[topIndex]) * k, t);
                    tx[i] = t.x;
                    ty[i] = t.y;
                    tz[i] = t.z;
                }
            }
            float dt = now - this.last;
            if (!this.ready || dt > STALE || dt < 0.0F) {
                System.arraycopy(tx, 0, this.x, 0, size);
                System.arraycopy(ty, 0, this.y, 0, size);
                System.arraycopy(tz, 0, this.z, 0, size);
                java.util.Arrays.fill(this.vx, 0.0F);
                java.util.Arrays.fill(this.vy, 0.0F);
                java.util.Arrays.fill(this.vz, 0.0F);
                this.ready = true;
                this.last = now;
                return;
            }
            if (dt < 1.0E-4F) {
                return;
            }
            this.last = now;
            int anchor = this.index(this.cols / 2, 0);
            float jx = tx[anchor] - this.x[anchor], jy = ty[anchor] - this.y[anchor], jz = tz[anchor] - this.z[anchor];
            if (jx * jx + jy * jy + jz * jz > JUMP * JUMP) {
                // Шаг или телепорт: ткань переносится следом и немного взлетает от рывка.
                this.shift(jx, jy, jz);
                float fling = -0.05F / Math.max(0.1F, MathHelper.sqrt(jx * jx + jy * jy + jz * jz));
                this.impulse(jx * fling, 0.08F, jz * fling);
            }
            int steps = Math.min(8, Math.max(1, (int) Math.ceil(dt / STEP)));
            float h = dt / steps;
            float flutter = 0.0015F + 0.013F * speed + 0.01F * heat;
            for (int s = 1; s <= steps; s++) {
                this.substep(tx, ty, tz, (float) s / steps, h, now, flutter, capsules, ground, grow);
            }
        }

        private void substep(float[] tx, float[] ty, float[] tz, float k, float h, float now, float flutter, float[][] capsules, float ground, float grow) {
            float keep = Math.max(0.0F, 1.0F - DRAG * h);
            for (int c = 0; c < this.cols; c++) {
                for (int j = 0; j < this.rows; j++) {
                    int i = this.index(c, j);
                    if (j < this.pinnedRows) {
                        this.px[i] = MathHelper.lerp(k, this.x[i], tx[i]);
                        this.py[i] = MathHelper.lerp(k, this.y[i], ty[i]);
                        this.pz[i] = MathHelper.lerp(k, this.z[i], tz[i]);
                        continue;
                    }
                    float r = (float) j / this.rows;
                    float fx = flutter * r * MathHelper.sin(now * 0.43F + c * 0.8F + j * 0.55F);
                    float fz = flutter * r * MathHelper.cos(now * 0.37F - c * 0.6F + j * 0.7F);
                    this.vx[i] = (this.vx[i] + fx * h) * keep;
                    this.vy[i] = (this.vy[i] - GRAVITY * h) * keep;
                    this.vz[i] = (this.vz[i] + fz * h) * keep;
                    float pull = Math.min(1.0F, this.softness[j] * h);
                    this.px[i] = this.x[i] + this.vx[i] * h;
                    this.py[i] = this.y[i] + this.vy[i] * h;
                    this.pz[i] = this.z[i] + this.vz[i] * h;
                    this.px[i] += (tx[i] - this.px[i]) * pull;
                    this.py[i] += (ty[i] - this.py[i]) * pull;
                    this.pz[i] += (tz[i] - this.pz[i]) * pull;
                }
            }
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                for (int l = 0; l < this.linkA.length; l++) {
                    int a = this.linkA[l], b = this.linkB[l];
                    float length = this.linkLength[l] * (this.linkAlong[l] ? Math.max(0.02F, grow) : 1.0F);
                    float dx = this.px[b] - this.px[a], dy = this.py[b] - this.py[a], dz = this.pz[b] - this.pz[a];
                    float d = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d < 1.0E-5F) {
                        continue;
                    }
                    float error = (d - length) / d * this.linkStiffness[l];
                    boolean pinA = a % this.rows < this.pinnedRows, pinB = b % this.rows < this.pinnedRows;
                    if (pinA && pinB) {
                        continue;
                    }
                    float wa = pinA ? 0.0F : (pinB ? 1.0F : 0.5F), wb = pinB ? 0.0F : (pinA ? 1.0F : 0.5F);
                    this.px[a] += dx * error * wa;
                    this.py[a] += dy * error * wa;
                    this.pz[a] += dz * error * wa;
                    this.px[b] -= dx * error * wb;
                    this.py[b] -= dy * error * wb;
                    this.pz[b] -= dz * error * wb;
                }
            }
            for (int i = 0; i < this.x.length; i++) {
                if (i % this.rows >= this.pinnedRows) {
                    collide(this.px, this.py, this.pz, i, capsules, ground);
                }
                this.vx[i] = (this.px[i] - this.x[i]) / h;
                this.vy[i] = (this.py[i] - this.y[i]) / h;
                this.vz[i] = (this.pz[i] - this.z[i]) / h;
                this.x[i] = this.px[i];
                this.y[i] = this.py[i];
                this.z[i] = this.pz[i];
            }
        }

        /** Сетка в координатах тела, в пикселях модели, как её ждёт отрисовка. Пока физика ни разу не считалась — покой. */
        public Vector3f[][] local(Matrix4f bodyFromSim) {
            Vector3f[][] grid = new Vector3f[this.cols][this.rows];
            for (int c = 0; c < this.cols; c++) {
                for (int j = 0; j < this.rows; j++) {
                    int i = this.index(c, j);
                    grid[c][j] = this.ready ? bodyFromSim.transformPosition(this.x[i], this.y[i], this.z[i], new Vector3f()).mul(16.0F)
                            : new Vector3f(this.restX[i], this.restY[i], this.restZ[i]).mul(16.0F);
                }
            }
            return grid;
        }
    }

    /** Цепь: звенья одинаковой длины, первое держится за кольцо на теле. Тяжелее ткани и почти не чувствует воздуха. */
    public static final class Rope extends Simulated {
        private static final float GRAVITY = 0.075F, DRAG = 0.018F;
        private static final int ITERATIONS = 6;
        private final int nodes;
        /** Как сильно цепь дёрнуло в этот кадр: от этого она звенит. */
        public float shock;

        private Rope(int links) {
            this.nodes = links + 1;
            this.allocate(this.nodes);
        }

        public void step(Vector3f anchor, Vector3f restDirection, float linkLength, float now, float[][] capsules, float ground) {
            float dt = now - this.last;
            if (!this.ready || dt > STALE || dt < 0.0F) {
                for (int i = 0; i < this.nodes; i++) {
                    this.x[i] = anchor.x + restDirection.x * linkLength * i;
                    this.y[i] = anchor.y + restDirection.y * linkLength * i;
                    this.z[i] = anchor.z + restDirection.z * linkLength * i;
                    this.vx[i] = this.vy[i] = this.vz[i] = 0.0F;
                }
                this.ready = true;
                this.last = now;
                this.shock = 0.0F;
                return;
            }
            if (dt < 1.0E-4F) {
                return;
            }
            this.last = now;
            float jx = anchor.x - this.x[0], jy = anchor.y - this.y[0], jz = anchor.z - this.z[0];
            if (jx * jx + jy * jy + jz * jz > JUMP * JUMP) {
                this.shift(jx, jy, jz);
            }
            int steps = Math.min(8, Math.max(1, (int) Math.ceil(dt / STEP)));
            float h = dt / steps, keep = Math.max(0.0F, 1.0F - DRAG * h);
            float fromX = this.x[0], fromY = this.y[0], fromZ = this.z[0];
            int end = this.nodes - 1;
            float before = MathHelper.sqrt(this.vx[end] * this.vx[end] + this.vy[end] * this.vy[end] + this.vz[end] * this.vz[end]);
            for (int s = 1; s <= steps; s++) {
                float k = (float) s / steps;
                this.px[0] = MathHelper.lerp(k, fromX, anchor.x);
                this.py[0] = MathHelper.lerp(k, fromY, anchor.y);
                this.pz[0] = MathHelper.lerp(k, fromZ, anchor.z);
                for (int i = 1; i < this.nodes; i++) {
                    this.vy[i] -= GRAVITY * h;
                    this.vx[i] *= keep;
                    this.vy[i] *= keep;
                    this.vz[i] *= keep;
                    this.px[i] = this.x[i] + this.vx[i] * h;
                    this.py[i] = this.y[i] + this.vy[i] * h;
                    this.pz[i] = this.z[i] + this.vz[i] * h;
                }
                for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                    for (int i = 0; i < end; i++) {
                        float dx = this.px[i + 1] - this.px[i], dy = this.py[i + 1] - this.py[i], dz = this.pz[i + 1] - this.pz[i];
                        float d = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);
                        if (d < 1.0E-5F) {
                            continue;
                        }
                        float error = (d - linkLength) / d;
                        float wa = i == 0 ? 0.0F : 0.5F, wb = i == 0 ? 1.0F : 0.5F;
                        this.px[i] += dx * error * wa;
                        this.py[i] += dy * error * wa;
                        this.pz[i] += dz * error * wa;
                        this.px[i + 1] -= dx * error * wb;
                        this.py[i + 1] -= dy * error * wb;
                        this.pz[i + 1] -= dz * error * wb;
                    }
                    for (int i = 1; i < this.nodes; i++) {
                        collide(this.px, this.py, this.pz, i, capsules, ground);
                    }
                }
                for (int i = 0; i < this.nodes; i++) {
                    this.vx[i] = (this.px[i] - this.x[i]) / h;
                    this.vy[i] = (this.py[i] - this.y[i]) / h;
                    this.vz[i] = (this.pz[i] - this.z[i]) / h;
                    this.x[i] = this.px[i];
                    this.y[i] = this.py[i];
                    this.z[i] = this.pz[i];
                }
            }
            float after = MathHelper.sqrt(this.vx[end] * this.vx[end] + this.vy[end] * this.vy[end] + this.vz[end] * this.vz[end]);
            this.shock = Math.abs(after - before) / Math.max(0.2F, dt) * 0.6F + after * 0.25F;
        }

        /** Узлы цепи в координатах {@code localFromSim}, в пикселях модели. */
        public Vector3f[] local(Matrix4f localFromSim) {
            Vector3f[] out = new Vector3f[this.nodes];
            for (int i = 0; i < this.nodes; i++) {
                out[i] = localFromSim.transformPosition(this.x[i], this.y[i], this.z[i], new Vector3f()).mul(16.0F);
            }
            return out;
        }
    }

    /** Снос огня у одной точки: сглаженная скорость этой точки с обратным знаком. */
    private static final class FlameWind {
        private final Vector3f previous = new Vector3f(), wind = new Vector3f();
        private float last = -1.0F;

        Vector3f update(Vector3f at, float now) {
            float dt = now - this.last;
            if (this.last < 0.0F || dt > 10.0F || dt < 0.0F) {
                this.previous.set(at);
                this.wind.set(0.0F);
                this.last = now;
                return this.wind;
            }
            if (dt > 1.0E-4F) {
                Vector3f speed = new Vector3f(this.previous).sub(at).div(dt);
                // Телепорт — не ветер.
                if (speed.lengthSquared() < 9.0F) {
                    speed.mul(WIND_PER_SPEED);
                    this.wind.lerp(speed, Math.min(1.0F, dt * 0.45F));
                }
                if (this.wind.lengthSquared() > MAX_WIND * MAX_WIND) {
                    this.wind.normalize(MAX_WIND);
                }
                this.previous.set(at);
                this.last = now;
            }
            return this.wind;
        }
    }
}
