package com.zangetsu.client.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Настоящий разрез модели. Сквозь этот потребитель рисуется обычная модель моба, а он обрезает каждую грань плоскостями и отдаёт дальше
 * только то, что осталось с нужной стороны; швы разреза запоминает и потом закрывает ими срез. Кусок получается объёмным, с настоящей
 * текстурой моба на гранях и с мясом (костью, золой) на срезе, а плоскость может идти как угодно: поперёк, вдоль или наискось.
 */
final class SliceConsumer implements VertexConsumer {
    private static final float EPSILON = 1.0E-4F, JOIN = 1.0E-3F;
    /** Яркость середины среза: от краёв вглубь темнеет, поэтому срез читается как глубокий. */
    private static final int EDGE_COLOR = 0xFFFFFFFF, CENTER_COLOR = 0xFF6A6A6A;

    private record Point(float x, float y, float z, float u, float v, int seams) {
    }

    private final VertexConsumer out;
    private final Matrix4f toView;
    private final Matrix4f toModel;
    private final Matrix3f normals;
    /** Плоскости в блоках модели: остаётся то, где n·p ≥ d. */
    private final float[][] planes;
    private final List<List<Vector3f[]>> seams = new ArrayList<>();
    private final Point[] quad = new Point[4];
    private int count;

    /** {@code entry} — где модель стоит в мире; {@code planes} — в пикселях модели, как в куске. */
    SliceConsumer(VertexConsumer out, MatrixStack.Entry entry, float[][] planes) {
        this.out = out;
        this.toView = new Matrix4f(entry.getPositionMatrix());
        this.toModel = new Matrix4f(this.toView).invert();
        this.normals = new Matrix3f(entry.getNormalMatrix());
        this.planes = new float[planes.length][];
        for (int i = 0; i < planes.length; i++) {
            float[] p = planes[i];
            float length = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
            this.planes[i] = new float[]{p[0] / length, p[1] / length, p[2] / length, p[3] / length / 16.0F};
            this.seams.add(new ArrayList<>());
        }
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        Vector3f p = this.toModel.transformPosition(x, y, z, new Vector3f());
        this.quad[this.count++] = new Point(p.x, p.y, p.z, u, v, 0);
        if (this.count == 4) {
            this.count = 0;
            this.clip(color, overlay, light, nx, ny, nz);
        }
    }

    private void clip(int color, int overlay, int light, float nx, float ny, float nz) {
        List<Point> polygon = new ArrayList<>(List.of(this.quad));
        for (int i = 0; i < this.planes.length && !polygon.isEmpty(); i++) {
            polygon = clip(polygon, this.planes[i], 1 << i);
        }
        if (polygon.size() < 3) {
            return;
        }
        // Рёбра, лежащие в плоскости разреза, — швы для среза.
        for (int a = 0; a < polygon.size(); a++) {
            Point from = polygon.get(a), to = polygon.get((a + 1) % polygon.size());
            int shared = from.seams() & to.seams();
            for (int i = 0; i < this.planes.length; i++) {
                if ((shared & (1 << i)) != 0) {
                    this.seams.get(i).add(new Vector3f[]{new Vector3f(from.x(), from.y(), from.z()), new Vector3f(to.x(), to.y(), to.z())});
                }
            }
        }
        // Многоугольник — веером из четырёхугольников (последний может выродиться в треугольник).
        for (int i = 1; i < polygon.size() - 1; i += 2) {
            this.emit(polygon.get(0), color, overlay, light, nx, ny, nz);
            this.emit(polygon.get(i), color, overlay, light, nx, ny, nz);
            this.emit(polygon.get(i + 1), color, overlay, light, nx, ny, nz);
            this.emit(polygon.get(Math.min(i + 2, polygon.size() - 1)), color, overlay, light, nx, ny, nz);
        }
    }

    /** Отсечение многоугольника плоскостью (Сазерленд — Ходжман); новые вершины помечаются этой плоскостью. */
    private static List<Point> clip(List<Point> polygon, float[] plane, int mark) {
        List<Point> result = new ArrayList<>(polygon.size() + 1);
        for (int i = 0; i < polygon.size(); i++) {
            Point a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            float da = distance(a, plane), db = distance(b, plane);
            if (da >= -EPSILON) {
                result.add(a);
            }
            if ((da > EPSILON && db < -EPSILON) || (da < -EPSILON && db > EPSILON)) {
                float t = da / (da - db);
                result.add(new Point(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t,
                        a.u() + (b.u() - a.u()) * t, a.v() + (b.v() - a.v()) * t, (a.seams() & b.seams()) | mark));
            }
        }
        return result;
    }

    private static float distance(Point p, float[] plane) {
        return p.x() * plane[0] + p.y() * plane[1] + p.z() * plane[2] - plane[3];
    }

    private void emit(Point p, int color, int overlay, int light, float nx, float ny, float nz) {
        Vector3f view = this.toView.transformPosition(p.x(), p.y(), p.z(), new Vector3f());
        this.out.vertex(view.x, view.y, view.z, color, p.u(), p.v(), overlay, light, nx, ny, nz);
    }

    /**
     * Закрыть срезы. Вызывается, когда вся модель уже нарисована, и в другой слой: {@code cap} можно запрашивать только сейчас,
     * пока не нарисована модель, её буфер ещё занят.
     */
    void caps(VertexConsumer cap, int overlay, int light) {
        for (int i = 0; i < this.planes.length; i++) {
            float[] plane = this.planes[i];
            Vector3f outward = this.normals.transform(new Vector3f(-plane[0], -plane[1], -plane[2])).normalize();
            Vector3f normal = new Vector3f(plane[0], plane[1], plane[2]);
            Vector3f e1 = Math.abs(normal.y) > 0.9F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
            Vector3f e2 = new Vector3f(normal).cross(e1).normalize();
            e1 = new Vector3f(e2).cross(normal).normalize();
            List<Vector3f[]> segments = this.seams.get(i);
            while (!segments.isEmpty()) {
                List<Vector3f> loop = chain(segments);
                Vector3f center = new Vector3f();
                for (Vector3f p : loop) {
                    center.add(p);
                }
                center.div(loop.size());
                for (int a = 0; a < loop.size(); a++) {
                    Vector3f from = loop.get(a), to = loop.get((a + 1) % loop.size());
                    this.capVertex(cap, center, CENTER_COLOR, e1, e2, overlay, light, outward);
                    this.capVertex(cap, from, EDGE_COLOR, e1, e2, overlay, light, outward);
                    this.capVertex(cap, to, EDGE_COLOR, e1, e2, overlay, light, outward);
                    this.capVertex(cap, to, EDGE_COLOR, e1, e2, overlay, light, outward);
                }
            }
        }
    }

    /** Собрать из отрезков один замкнутый контур (сечение куба выпукло, контур всегда один на куб). */
    private static List<Vector3f> chain(List<Vector3f[]> segments) {
        List<Vector3f> loop = new ArrayList<>();
        Vector3f[] first = segments.remove(segments.size() - 1);
        loop.add(first[0]);
        Vector3f end = first[1];
        boolean found = true;
        while (found) {
            found = false;
            for (int i = segments.size() - 1; i >= 0; i--) {
                Vector3f[] s = segments.get(i);
                if (s[0].distanceSquared(end) < JOIN * JOIN) {
                    loop.add(s[0]);
                    end = s[1];
                } else if (s[1].distanceSquared(end) < JOIN * JOIN) {
                    loop.add(s[1]);
                    end = s[0];
                } else {
                    continue;
                }
                segments.remove(i);
                found = true;
                break;
            }
        }
        return loop;
    }

    private void capVertex(VertexConsumer cap, Vector3f p, int color, Vector3f e1, Vector3f e2, int overlay, int light, Vector3f normal) {
        Vector3f view = this.toView.transformPosition(p, new Vector3f());
        // Текстура ложится по самой плоскости: один повтор на блок модели.
        cap.vertex(view.x, view.y, view.z, color, p.dot(e1), p.dot(e2), overlay, light, normal.x, normal.y, normal.z);
    }

    // Модели рисуются только через полный vertex(...) выше.

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return this;
    }
}
