"""Яхве в Blender: этап силуэта и пропорций.

Запуск: blender --background --factory-startup --python tools/blender/yhwach.py -- <папка рендеров> [виды через запятую|all] [качество]
Качество 1 — полное, 2 — черновик. Готовых моделей нет, всё строится здесь.

Пропорции сняты с кадров: голова с массой над ней 0.37 м, усы шире челюсти в 1.65 раза, ниже подбородка гора расходится под 30–40 градусов,
у земли шириной больше шести метров. Координаты: x вправо от зрителя, f вперёд к зрителю (в Blender это -y), z вверх.
"""
import math
import os
import sys
import time

import bpy
import numpy as np
from mathutils import Matrix, Vector

ARGV = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = os.path.abspath(ARGV[0] if ARGV else ".")
WANTED = None if len(ARGV) < 2 or ARGV[1] == "all" else ARGV[1].split(",")
QUALITY = float(ARGV[2]) if len(ARGV) > 2 else 1.0
CLOAK_FILE = os.path.abspath(ARGV[3]) if len(ARGV) > 3 else os.path.abspath("ref/blender/cloak/cloak.npz")
CLOAK_FRAME = int(ARGV[4]) if len(ARGV) > 4 else 50
START = time.time()


def log(*items):
    print("YH %6.1f" % (time.time() - START), *items, flush=True)


# ------------------------------------------------------------------ шум и примитивы

_rng = np.random.RandomState(7)
_perm = np.tile(_rng.permutation(256), 2)
_grad = _rng.normal(size=(256, 3))
_grad /= np.linalg.norm(_grad, axis=1, keepdims=True)


def pn(x, y, z):
    x, y, z = np.asarray(x, np.float64), np.asarray(y, np.float64), np.asarray(z, np.float64)
    xi, yi, zi = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64), np.floor(z).astype(np.int64)
    fx, fy, fz = x - xi, y - yi, z - zi
    u, v, w = fx * fx * fx * (fx * (fx * 6 - 15) + 10), fy * fy * fy * (fy * (fy * 6 - 15) + 10), fz * fz * fz * (fz * (fz * 6 - 15) + 10)

    def g(ax, ay, az, dx, dy, dz):
        i = _perm[_perm[_perm[(xi + ax) & 255] + ((yi + ay) & 255)] + ((zi + az) & 255)]
        t = _grad[i]
        return t[..., 0] * dx + t[..., 1] * dy + t[..., 2] * dz

    a0 = g(0, 0, 0, fx, fy, fz) * (1 - u) + g(1, 0, 0, fx - 1, fy, fz) * u
    a1 = g(0, 1, 0, fx, fy - 1, fz) * (1 - u) + g(1, 1, 0, fx - 1, fy - 1, fz) * u
    b0 = g(0, 0, 1, fx, fy, fz - 1) * (1 - u) + g(1, 0, 1, fx - 1, fy, fz - 1) * u
    b1 = g(0, 1, 1, fx, fy - 1, fz - 1) * (1 - u) + g(1, 1, 1, fx - 1, fy - 1, fz - 1) * u
    return ((a0 * (1 - v) + a1 * v) * (1 - w) + (b0 * (1 - v) + b1 * v) * w) * 1.6


def fbm(x, y, z, octaves=3):
    total, amp, freq, norm = 0.0, 1.0, 1.0, 0.0
    for i in range(octaves):
        total = total + pn(x * freq + i * 11.3, y * freq + i * 7.1, z * freq + i * 3.7) * amp
        norm += amp
        amp *= 0.5
        freq *= 2.03
    return total / norm


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def smin(a, b, k):
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0, 1)
    return b + (a - b) * h - k * h * (1 - h)


def smax(a, b, k):
    return -smin(-a, -b, k)


def ell(x, y, z, cx, cy, cz, rx, ry, rz):
    px, py, pz = x - cx, y - cy, z - cz
    k0 = np.sqrt((px / rx) ** 2 + (py / ry) ** 2 + (pz / rz) ** 2)
    k1 = np.sqrt((px / (rx * rx)) ** 2 + (py / (ry * ry)) ** 2 + (pz / (rz * rz)) ** 2)
    return k0 * (k0 - 1) / np.maximum(k1, 1e-9)


def cone(x, y, z, a, b, r1, r2):
    """Скруглённый конус между точками a и b."""
    bax, bay, baz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    l2 = bax * bax + bay * bay + baz * baz
    rr = r1 - r2
    a2 = l2 - rr * rr
    il2 = 1.0 / l2
    pax, pay, paz = x - a[0], y - a[1], z - a[2]
    yy = pax * bax + pay * bay + paz * baz
    zz = yy - l2
    cx, cy, cz = pax * l2 - bax * yy, pay * l2 - bay * yy, paz * l2 - baz * yy
    x2 = cx * cx + cy * cy + cz * cz
    y2 = yy * yy * l2
    z2 = zz * zz * l2
    k = np.sign(rr) * rr * rr * x2
    return np.where(np.sign(zz) * a2 * z2 > k, np.sqrt(x2 + z2) * il2 - r2,
                    np.where(np.sign(yy) * a2 * y2 < k, np.sqrt(x2 + y2) * il2 - r1,
                             (np.sqrt(x2 * a2 * il2) + yy * rr) * il2 - r1))


def spike(x):
    f = x - np.floor(x)
    t = 1 - np.abs(f - 0.5) * 2
    return t * t * t


def gauss(x, y, cx, cy, wx, wy):
    return np.exp(-(((x - cx) / wx) ** 2 + ((y - cy) / wy) ** 2))


def seg(x, y, x0, y0, x1, y1):
    dx, dy = x1 - x0, y1 - y0
    t = np.clip(((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy), 0, 1)
    return np.hypot(x - x0 - dx * t, y - y0 - dy * t)


def curve_table(ys, vs, n=800):
    """Плавная кривая по точкам: y по убыванию. Возвращает функцию от массива."""
    ys, vs = np.array(ys, float), np.array(vs, float)
    grid = np.linspace(ys[-1], ys[0], n)
    out = np.empty(n)
    count = len(ys)
    for g, y in enumerate(grid):
        i = 0
        while i < count - 2 and y < ys[i + 1]:
            i += 1
        h = ys[i] - ys[i + 1]
        t = (ys[i] - y) / h
        m0 = (vs[i + 1] - vs[i - 1]) / (ys[i - 1] - ys[i + 1]) * h if i > 0 else vs[i + 1] - vs[i]
        m1 = (vs[i + 2] - vs[i]) / (ys[i] - ys[i + 2]) * h if i + 2 < count else vs[i + 1] - vs[i]
        t2, t3 = t * t, t * t * t
        out[g] = (2 * t3 - 3 * t2 + 1) * vs[i] + (t3 - 2 * t2 + t) * m0 + (3 * t2 - 2 * t3) * vs[i + 1] + (t3 - t2) * m1
    return lambda y: np.interp(y, grid, out)


# ------------------------------------------------------------------ сетка из поля расстояний

CORNERS = [(dx, dy, dz) for dz in (0, 1) for dy in (0, 1) for dx in (0, 1)]
EDGES = [(0, 1), (2, 3), (4, 5), (6, 7), (0, 2), (1, 3), (4, 6), (5, 7), (0, 4), (1, 5), (2, 6), (3, 7)]


def nets(base, lo, hi, h, detail=None, band=0.0):
    """Сетка поверхности нулевого уровня. base считается всюду, detail — только у поверхности, это в разы быстрее."""
    h = h * QUALITY
    lo, hi = np.array(lo, float), np.array(hi, float)
    n = np.ceil((hi - lo) / h).astype(int) + 1
    xs, ys, zs = (lo[i] + h * np.arange(n[i]) for i in range(3))
    grid_y, grid_z = np.meshgrid(ys, zs, indexing="ij")
    V = np.empty(n, dtype=np.float32)
    for i in range(n[0]):
        V[i] = base(np.full_like(grid_y, xs[i]), grid_y, grid_z)
    if detail is not None:
        at = np.nonzero(np.abs(V) < band)
        V[at] = detail(xs[at[0]], ys[at[1]], zs[at[2]], V[at].astype(np.float64))
    inside = V < 0
    count = np.zeros(n - 1, dtype=np.uint8)
    for dx, dy, dz in CORNERS:
        count += inside[dx:n[0] - 1 + dx, dy:n[1] - 1 + dy, dz:n[2] - 1 + dz]
    active = (count > 0) & (count < 8)
    cells = np.argwhere(active)
    m = len(cells)
    vid = np.full(active.shape, -1, dtype=np.int64)
    vid[active] = np.arange(m)
    val = np.stack([V[cells[:, 0] + dx, cells[:, 1] + dy, cells[:, 2] + dz] for dx, dy, dz in CORNERS], 1).astype(np.float64)
    pos, num = np.zeros((m, 3)), np.zeros(m)
    for a, b in EDGES:
        va, vb = val[:, a], val[:, b]
        cross = (va < 0) != (vb < 0)
        t = va[cross] / (va[cross] - vb[cross])
        ca, cb = np.array(CORNERS[a], float), np.array(CORNERS[b], float)
        pos[cross] += ca + (cb - ca) * t[:, None]
        num[cross] += 1
    P = lo + (cells + pos / num[:, None]) * h
    quads = []
    i, j, k = np.nonzero(inside[:-1, 1:-1, 1:-1] != inside[1:, 1:-1, 1:-1])
    j, k = j + 1, k + 1
    q = np.stack([vid[i, j - 1, k - 1], vid[i, j, k - 1], vid[i, j, k], vid[i, j - 1, k]], 1)
    q[~inside[i, j, k]] = q[~inside[i, j, k]][:, ::-1]
    quads.append(q)
    i, j, k = np.nonzero(inside[1:-1, :-1, 1:-1] != inside[1:-1, 1:, 1:-1])
    i, k = i + 1, k + 1
    q = np.stack([vid[i - 1, j, k - 1], vid[i - 1, j, k], vid[i, j, k], vid[i, j, k - 1]], 1)
    q[~inside[i, j, k]] = q[~inside[i, j, k]][:, ::-1]
    quads.append(q)
    i, j, k = np.nonzero(inside[1:-1, 1:-1, :-1] != inside[1:-1, 1:-1, 1:])
    i, j = i + 1, j + 1
    q = np.stack([vid[i - 1, j - 1, k], vid[i, j - 1, k], vid[i, j, k], vid[i - 1, j, k]], 1)
    q[~inside[i, j, k]] = q[~inside[i, j, k]][:, ::-1]
    quads.append(q)
    Q = np.concatenate(quads)
    return P, Q[(Q >= 0).all(1)]


def relax(P, Q, strength=0.5, times=1):
    """Лёгкое сглаживание: убирает ступеньки сетки."""
    for _ in range(times):
        total, num = np.zeros_like(P), np.zeros(len(P))
        for a, b in ((0, 1), (1, 2), (2, 3), (3, 0)):
            np.add.at(total, Q[:, a], P[Q[:, b]])
            np.add.at(num, Q[:, a], 1)
        moved = num > 0
        P = P.copy()
        P[moved] += (total[moved] / num[moved][:, None] - P[moved]) * strength
    return P


def keep_large(P, Q, min_quads):
    """Выбросить мелкие крошки: куски меньше min_quads."""
    label = np.arange(len(P))
    for _ in range(400):
        low = np.min(label[Q], axis=1)
        before = label.copy()
        for c in range(4):
            np.minimum.at(label, Q[:, c], low)
        label = label[label]
        if np.array_equal(before, label):
            break
    sizes = np.bincount(label[Q[:, 0]], minlength=len(P))
    Q = Q[sizes[label[Q[:, 0]]] >= min_quads]
    used = np.unique(Q)
    remap = np.full(len(P), -1, dtype=np.int64)
    remap[used] = np.arange(len(used))
    return P[used], remap[Q]


def normals_of(P, Q):
    u, v = P[Q[:, 2]] - P[Q[:, 0]], P[Q[:, 3]] - P[Q[:, 1]]
    fn = np.cross(u, v)
    N = np.zeros_like(P)
    for c in range(4):
        np.add.at(N, Q[:, c], fn)
    return N / np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-12)


# ------------------------------------------------------------------ объекты и материалы

def srgb(c):
    c = np.asarray(c, float)
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def add_object(name, P, Q, material, colors=None, matrix=None, normals=None, uv=None):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(P.tolist(), [], Q.tolist())
    mesh.update()
    mesh.shade_smooth()
    if colors is not None:
        attr = mesh.color_attributes.new("Col", "FLOAT_COLOR", "POINT")
        rgba = np.ones((len(P), 4))
        rgba[:, :3] = srgb(colors)
        attr.data.foreach_set("color", rgba.ravel())
    if uv is not None:
        mesh.attributes.new("uvmap", "FLOAT2", "POINT").data.foreach_set("vector", uv.astype(np.float32).ravel())
    if normals is not None:
        mesh.normals_split_custom_set_from_vertices(normals.tolist())
    mesh.materials.append(material)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    if matrix is not None:
        obj.matrix_world = matrix
    log(name, len(P), "вершин", len(Q), "граней")
    return obj


def add_hull(name, P, Q, thickness, matrix=None, band=0.0, fade=None):
    """Чернильный контур как в игре: копия, раздутая по нормалям и вывернутая. С band под контуром лежит винная кайма."""
    N = normals_of(P, Q)
    if fade is not None:
        N = N * fade[:, None]
    if band > 0:
        add_object(name + "_band", P + N * band, Q[:, ::-1], MAT["band"], matrix=matrix)
    return add_object(name + "_ink", P + N * (band + thickness), Q[:, ::-1], MAT["ink"], matrix=matrix)


def socket(sockets, name, kind):
    return next(s for s in sockets if s.name == name and s.type == kind)


def mixer(nt, mode, factor, a, b):
    node = nt.nodes.new("ShaderNodeMix")
    node.data_type = "RGBA"
    node.blend_type = mode
    if isinstance(factor, (int, float)):
        node.inputs["Factor"].default_value = factor
    else:
        nt.links.new(factor, node.inputs["Factor"])
    for sock, value in ((socket(node.inputs, "A", "RGBA"), a), (socket(node.inputs, "B", "RGBA"), b)):
        if isinstance(value, tuple):
            sock.default_value = (*srgb(value), 1)
        else:
            nt.links.new(value, sock)
    return socket(node.outputs, "Result", "RGBA")


def ramp(nt, source, a, b, c0=(0, 0, 0), c1=(1, 1, 1)):
    node = nt.nodes.new("ShaderNodeValToRGB")
    nt.links.new(source, node.inputs["Fac"])
    els = node.color_ramp.elements
    els[0].position, els[1].position = a, b
    els[0].color, els[1].color = (*c0, 1), (*c1, 1)
    return node.outputs["Color"]


def toon(name, base=None, attribute=False, shadow=(0.6, 0.5, 0.55), rim=None, rim_from=0.74, rim_to=0.93, rim_power=1.0, grain=None, threshold=0.2, soft=0.015, texture=None):
    """Рисованный свет: то же, что шейдер в игре. Тень — резкая ступень, по краю тонкая цветная линия."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    output, emit = nt.nodes.new("ShaderNodeOutputMaterial"), nt.nodes.new("ShaderNodeEmission")
    nt.links.new(emit.outputs[0], output.inputs[0])
    diffuse, to_rgb = nt.nodes.new("ShaderNodeBsdfDiffuse"), nt.nodes.new("ShaderNodeShaderToRGB")
    diffuse.inputs["Color"].default_value = (1, 1, 1, 1)
    nt.links.new(diffuse.outputs[0], to_rgb.inputs[0])
    lit = ramp(nt, to_rgb.outputs["Color"], max(threshold - soft, 0.0), threshold + soft)
    if texture is not None:
        coords, node = nt.nodes.new("ShaderNodeAttribute"), nt.nodes.new("ShaderNodeTexImage")
        coords.attribute_name = "uvmap"
        node.image = texture
        node.extension = "EXTEND"
        nt.links.new(coords.outputs["Vector"], node.inputs["Vector"])
        color = node.outputs["Color"]
    elif attribute:
        node = nt.nodes.new("ShaderNodeVertexColor")
        node.layer_name = "Col"
        color = node.outputs["Color"]
    elif grain is not None:
        coords, noise = nt.nodes.new("ShaderNodeTexCoord"), nt.nodes.new("ShaderNodeTexNoise")
        nt.links.new(coords.outputs["Object"], noise.inputs["Vector"])
        noise.inputs["Scale"].default_value = grain[2]
        noise.inputs["Detail"].default_value = 5
        flecks = ramp(nt, noise.outputs["Fac"], 0.52, 0.66)
        color = mixer(nt, "MIX", flecks, grain[0], grain[1])
    else:
        node = nt.nodes.new("ShaderNodeRGB")
        node.outputs[0].default_value = (*srgb(base), 1)
        color = node.outputs[0]
    dark = mixer(nt, "MULTIPLY", 1.0, color, shadow)
    result = mixer(nt, "MIX", lit, dark, color)
    if rim is not None:
        weight = nt.nodes.new("ShaderNodeLayerWeight")
        weight.inputs["Blend"].default_value = 0.5
        edge = ramp(nt, weight.outputs["Facing"], rim_from, rim_to, (0, 0, 0), (rim_power, rim_power, rim_power))
        result = mixer(nt, "MIX", edge, result, rim)
    nt.links.new(result, emit.inputs["Color"])
    return m


def ink(name="ink", color=(0.002, 0.001, 0.002)):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    output, emit = nt.nodes.new("ShaderNodeOutputMaterial"), nt.nodes.new("ShaderNodeEmission")
    emit.inputs["Color"].default_value = (*color, 1)
    nt.links.new(emit.outputs[0], output.inputs[0])
    m.use_backface_culling = True
    return m


MAT = {}


def materials():
    MAT["ink"] = ink()
    MAT["band"] = ink("band", tuple(srgb((0.25, 0.03, 0.085))))
    MAT["mass"] = toon("mass", grain=((0.032, 0.018, 0.024), (0.17, 0.035, 0.058), 90.0), shadow=(0.36, 0.3, 0.36), threshold=0.3, soft=0.2)
    MAT["hair"] = toon("hair", attribute=True, shadow=(0.66, 0.52, 0.6))
    MAT["cloth"] = toon("cloth", attribute=True, shadow=(0.5, 0.42, 0.48), threshold=0.3, soft=0.25)
    MAT["hooks"] = toon("hooks", base=(0.07, 0.03, 0.04), shadow=(0.5, 0.42, 0.48), threshold=0.3, soft=0.25)
    MAT["hood"] = toon("hood", grain=((0.032, 0.018, 0.024), (0.17, 0.035, 0.058), 150.0), shadow=(0.42, 0.34, 0.42), threshold=0.3, soft=0.2)
    MAT["skin"] = toon("skin", attribute=True, shadow=(0.86, 0.72, 0.76), threshold=0.05)
    MAT["moustache"] = toon("moustache", base=(0.03, 0.022, 0.026), shadow=(0.7, 0.6, 0.7))
    MAT["eye"] = toon("eye", attribute=True, shadow=(0.88, 0.8, 0.82), threshold=0.04)


# ------------------------------------------------------------------ лицо

# Мерки с кадров (x и y в метрах от центра черепа): переносица +0.035, кончик носа -0.04, рот от -0.05 до -0.086,
# низ губы -0.097, низ подбородка -0.133. Челюсть 0.16 в ширину, усы 0.254.
HEAD_Z, HEAD_F, HEAD_TILT = 1.83, 0.035, math.radians(6)
PROFILE = curve_table([0.13, 0.10, 0.07, 0.04, 0.01, -0.02, -0.045, -0.07, -0.09, -0.105, -0.118, -0.128, -0.136, -0.142],
                      [0.05, 0.08, 0.094, 0.1, 0.1, 0.102, 0.106, 0.104, 0.104, 0.102, 0.112, 0.108, 0.085, 0.04])
WIDTH = curve_table([0.13, 0.10, 0.06, 0.02, -0.02, -0.05, -0.08, -0.10, -0.115, -0.128, -0.136, -0.142],
                    [0.055, 0.072, 0.082, 0.086, 0.086, 0.084, 0.08, 0.072, 0.06, 0.044, 0.03, 0.012])
NOSE_Y, NOSE_H, NOSE_W = [-0.047, -0.04, -0.032, -0.02, 0.0, 0.03, 0.05], [0.0, 0.04, 0.052, 0.046, 0.034, 0.018, 0.006], [0.006, 0.015, 0.018, 0.017, 0.016, 0.015, 0.012]
BROW_X, BROW_Y, BROW_T = [0.002, 0.02, 0.036, 0.06, 0.09, 0.127], [-0.05, -0.045, -0.042, -0.043, -0.053, -0.076], [0.006, 0.008, 0.014, 0.019, 0.015, 0.003]


def to_head(x, f, z):
    """Из координат тела в координаты головы: x, y вверх, z вперёд, начало в центре черепа."""
    yy, zz = z - HEAD_Z, f - HEAD_F
    c, sn = math.cos(HEAD_TILT), math.sin(HEAD_TILT)
    return x, yy * c - zz * sn, yy * sn + zz * c


def head_world(x, y, z):
    """Из координат головы в координаты Blender."""
    c, sn = math.cos(HEAD_TILT), math.sin(HEAD_TILT)
    return np.array((x, -(HEAD_F - y * sn + z * c), HEAD_Z + y * c + z * sn))


def mouth_line(x):
    """Линия сомкнутых губ: уголки чуть подняты."""
    return -0.072 + 4.0 * x * x


def nose_height(x, y):
    """Длинный угловатый нос: плоская спинка с резкими гранями, крылья отдельно."""
    ax = np.abs(x)
    h, w = np.interp(y, NOSE_Y, NOSE_H), np.interp(y, NOSE_Y, NOSE_W)
    top = np.clip((w - ax) / (w * 0.5), 0, 1)
    return np.maximum(h * top * top * (3 - 2 * top), 0.013 * gauss(ax, y, 0.025, -0.032, 0.0095, 0.0075))


def face_height(x, y, nose=True):
    ax = np.abs(x)
    wide = WIDTH(y)
    zc = -0.02
    q = np.clip(1 - (ax / wide) ** 2.6, 0, 1)
    f = zc + (PROFILE(y) - zc) * np.sqrt(q)
    f = f + 0.01 * gauss(ax, y, 0.066, 0.0, 0.02, 0.016)
    f = f - 0.012 * gauss(ax, y, 0.07, -0.06, 0.016, 0.03)
    f = f + 0.008 * gauss(ax, y, 0.03, -0.058, 0.03, 0.02)
    if nose:
        f = f + nose_height(x, y)
    fold = np.minimum(seg(ax, y, 0.033, -0.03, 0.054, -0.056), seg(ax, y, 0.054, -0.056, 0.064, -0.1))
    f = f - 0.004 * np.exp(-(fold / 0.003) ** 2)
    low = y - mouth_line(x)
    edge = 1 - smooth(0.04, 0.054, ax)
    f = f - 0.0028 * np.exp(-(low / 0.0016) ** 2) * edge
    f = f + 0.0042 * np.exp(-((low + 0.0085) / 0.005) ** 2) * edge
    f = f + 0.0022 * np.exp(-((low - 0.006) / 0.005) ** 2) * edge
    f = f - 0.003 * gauss(x, y, 0, -0.0925, 0.024, 0.005)
    f = f + 0.004 * gauss(x, y, 0, -0.118, 0.032, 0.012)
    return np.where(ax >= wide, zc, f)


def head_field(x, f, z):
    x, y, zz = to_head(x, f, z)
    ax = np.abs(x)
    h = ell(x, y, zz, 0, 0.035, -0.012, 0.08, 0.095, 0.102)
    h = smin(h, ell(x, y, zz, 0, -0.05, 0.0, 0.074, 0.08, 0.085), 0.02)
    surface = face_height(x, y)
    front = np.maximum((zz - surface) * 0.62, np.maximum(np.maximum(y - 0.125, -0.143 - y), 0.004 - zz))
    return smin(h, front, 0.003)


def moustache_surface(ax, y):
    wide = WIDTH(y)
    near = np.minimum(ax, wide * 0.97)
    return face_height(near, y, nose=False) - np.maximum(ax - wide * 0.97, 0) * 0.55


def moustache_field(x, f, z):
    """Основа усов: толстая подушка. Рваный низ дают отдельные пряди."""
    x, y, zz = to_head(x, f, z)
    ax = np.abs(x)
    centre, half = np.interp(ax, BROW_X, BROW_Y), np.interp(ax, BROW_X, BROW_T)
    m = np.minimum(half - np.abs(y - centre), 0.127 - ax)
    surface = moustache_surface(ax, y)
    thick = 0.006 + 0.008 * np.clip(m / 0.012, 0, 1)
    return np.maximum(np.maximum((zz - surface - thick) * 0.62, surface - 0.012 - zz), -m * 0.7)


def moustache_hair(count=460, sides=4, steps=5):
    """Пряди усов: от середины вниз и в стороны, кончики выходят за край подушки."""
    rng = np.random.RandomState(31)
    hair = Strands()
    for side in (-1, 1):
        for i in range(count):
            a = rng.rand() ** 0.9
            x0 = 0.003 + a * 0.118
            y0 = np.interp(x0, BROW_X, BROW_Y) + rng.uniform(-1.05, 0.7) * np.interp(x0, BROW_X, BROW_T)
            angle = math.radians(58 * (1 - a) + 22 * a + rng.normal() * 8)
            length = (0.008 + 0.012 * rng.rand()) * (0.7 + 0.8 * a)
            points = []
            for k in range(4):
                u = k / 3
                x, y = x0 + math.cos(angle) * length * u, y0 - math.sin(angle) * length * u - 0.004 * u * u
                points.append(head_world(side * x, y, float(moustache_surface(x, y)) + 0.0075 * (1 - u * 0.6)))
            hair.add(points, (0.0016 + 0.0016 * rng.rand()) * (460 / count) ** 0.5, flat=0.55, sides=sides, steps=steps, belly=0.1, taper=0.9)
    return hair


def cheek_locks():
    """Чёрные клинья из-под капюшона: лежат на скулах, нос свободен."""
    rng = np.random.RandomState(17)
    locks = Strands()
    for side in (-1, 1):
        for x0 in (0.025, 0.038, 0.052, 0.066, 0.08, 0.09, 0.098):
            bottom = (0.008 - rng.rand() * 0.032) if x0 < 0.08 else (-0.02 - rng.rand() * 0.02)
            points = []
            for k in range(5):
                u = k / 4
                x, y = x0 + 0.014 * u * u + 0.004 * rng.normal() * u, 0.05 + (bottom - 0.05) * u
                points.append(head_world(side * x, y, float(face_height(x, y)) + 0.0035))
            locks.add(points, 0.0065 + 0.0035 * rng.rand(), flat=0.3, sides=5, steps=12, belly=0.15, taper=1.0)
    return locks


class Painter:
    """Рисунок на лице: тени и тушь. Кисть с плавной шириной, заливка с ровным краем."""

    def __init__(self, x0, x1, y0, y1, width, base):
        self.x0, self.y0, self.scale = x0, y0, width / (x1 - x0)
        self.w, self.h = width, int(round(width * (y1 - y0) / (x1 - x0)))
        self.image = np.ones((self.h, self.w, 3), np.float32) * np.array(base, np.float32)

    def blend(self, x0, y0, cover, color, alpha):
        region = self.image[y0:y0 + cover.shape[0], x0:x0 + cover.shape[1]]
        region += (np.array(color, np.float32) - region) * (cover * alpha)[..., None]

    def box(self, pts, margin):
        lo = np.floor((pts.min(axis=0) - margin - (self.x0, self.y0)) * self.scale).astype(int)
        hi = np.ceil((pts.max(axis=0) + margin - (self.x0, self.y0)) * self.scale).astype(int)
        lo, hi = np.maximum(lo, 0), np.minimum(hi, (self.w, self.h))
        return lo, hi

    def stroke(self, points, w0, w1=None, color=(0.1, 0.06, 0.08), alpha=1.0, mirror=False):
        w1 = w0 if w1 is None else w1
        for sign in ((1, -1) if mirror else (1,)):
            source = np.array(points, float) * (sign, 1)
            pts = spline(source, np.linspace(0, 1, max(12, 10 * len(points))))
            widths = np.linspace(w0, w1, len(pts))
            lo, hi = self.box(pts, max(w0, w1))
            if (hi <= lo).any():
                continue
            xs = self.x0 + (np.arange(lo[0], hi[0]) + 0.5) / self.scale
            ys = self.y0 + (np.arange(lo[1], hi[1]) + 0.5) / self.scale
            cover = np.zeros((hi[1] - lo[1], hi[0] - lo[0]), np.float32)
            for i in range(len(pts) - 1):
                r = max(widths[i], widths[i + 1]) / 2 + 2 / self.scale
                ax0, ax1 = np.searchsorted(xs, min(pts[i][0], pts[i + 1][0]) - r), np.searchsorted(xs, max(pts[i][0], pts[i + 1][0]) + r)
                ay0, ay1 = np.searchsorted(ys, min(pts[i][1], pts[i + 1][1]) - r), np.searchsorted(ys, max(pts[i][1], pts[i + 1][1]) + r)
                if ax1 <= ax0 or ay1 <= ay0:
                    continue
                gx, gy = np.meshgrid(xs[ax0:ax1], ys[ay0:ay1])
                dist = seg(gx, gy, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1])
                value = np.clip((widths[i] / 2 - dist) * self.scale + 0.5, 0, 1)
                cover[ay0:ay1, ax0:ax1] = np.maximum(cover[ay0:ay1, ax0:ax1], value)
            self.blend(lo[0], lo[1], cover, color, alpha)

    def fill(self, points, color, alpha=1.0, mirror=False, soft=1):
        for sign in ((1, -1) if mirror else (1,)):
            source = np.array(points, float) * (sign, 1)
            closed = np.concatenate([source, source[:3]])
            pts = spline(closed, np.linspace(0, (len(source)) / (len(closed) - 1), 14 * len(source)))
            lo, hi = self.box(pts, 2 / self.scale)
            if (hi <= lo).any():
                continue
            gx, gy = np.meshgrid(self.x0 + (np.arange(lo[0], hi[0]) + 0.5) / self.scale, self.y0 + (np.arange(lo[1], hi[1]) + 0.5) / self.scale)
            inside = np.zeros(gx.shape, bool)
            for a, b in zip(pts, np.roll(pts, -1, axis=0)):
                crossing = (a[1] > gy) != (b[1] > gy)
                inside ^= crossing & (gx < (b[0] - a[0]) * (gy - a[1]) / (b[1] - a[1] + 1e-12) + a[0])
            cover = inside.astype(np.float32)
            for _ in range(soft):
                cover = (cover * 2 + np.roll(cover, 1, 0) + np.roll(cover, -1, 0) + np.roll(cover, 1, 1) + np.roll(cover, -1, 1)) / 6
            self.blend(lo[0], lo[1], cover, color, alpha)

    def to_blender(self, name):
        rgba = np.ones((self.h, self.w, 4), np.float32)
        rgba[..., :3] = np.clip(self.image, 0, 1)
        image = bpy.data.images.new(name, self.w, self.h, alpha=False)
        image.pixels.foreach_set(rgba.ravel())
        image.update()
        image.filepath_raw = os.path.join(OUT, name + ".png")
        image.file_format = "PNG"
        os.makedirs(OUT, exist_ok=True)
        image.save()
        image.pack()
        return image


SKIN_TONE, SHADE, DEEP, DARK_INK, WHITE_TONE = (0.875, 0.72, 0.6), (0.62, 0.46, 0.46), (0.42, 0.29, 0.31), (0.1, 0.06, 0.08), (0.98, 0.95, 0.9)
FACE_BOX = (-0.17, 0.17, -0.2, 0.14)
INK = np.array(DARK_INK)
NECK_BOX = (-0.3, 0.3, 1.1, 1.82)


def paint_face():
    p = Painter(*FACE_BOX, 2048, SKIN_TONE)
    # Боковые плоскости и впадины щёк.
    p.fill([(0.074, 0.08), (0.12, 0.08), (0.12, -0.15), (0.05, -0.15), (0.066, -0.1), (0.072, -0.06)], SHADE, mirror=True)
    p.fill([(0.05, -0.012), (0.082, -0.02), (0.08, -0.06), (0.068, -0.06)], SHADE, alpha=0.8, mirror=True)
    # Тень капюшона на верхе щёк и у корня носа.
    p.fill([(0.02, 0.08), (0.11, 0.08), (0.1, 0.008), (0.06, 0.012), (0.034, 0.002)], DEEP, alpha=0.85, mirror=True)
    p.fill([(-0.022, 0.08), (0.022, 0.08), (0.012, 0.03), (-0.012, 0.03)], DEEP, alpha=0.55)
    # Нос.
    p.fill([(-0.006, 0.04), (-0.016, 0.04), (-0.022, 0.0), (-0.03, -0.022), (-0.03, -0.034), (-0.018, -0.038), (-0.008, -0.03), (-0.006, 0.0)], SHADE)
    p.fill([(0.012, 0.04), (0.017, 0.0), (0.024, -0.02), (0.016, -0.02), (0.012, 0.0)], SHADE, alpha=0.45)
    p.fill([(-0.024, -0.036), (0.0, -0.047), (0.024, -0.036), (0.012, -0.04), (0.0, -0.043), (-0.012, -0.04)], DEEP)
    p.stroke([(0.007, 0.03), (0.0085, 0.004), (0.0095, -0.014)], 0.003, 0.001, WHITE_TONE, alpha=0.8)
    p.stroke([(0.017, -0.012), (0.03, -0.022), (0.033, -0.032), (0.026, -0.039), (0.015, -0.038)], 0.0016, 0.0009, mirror=True)
    p.fill([(0.008, -0.0385), (0.018, -0.036), (0.02, -0.039), (0.01, -0.042)], DEEP, mirror=True)
    p.stroke([(0.014, 0.03), (0.016, 0.0), (0.02, -0.012)], 0.001, 0.0006, alpha=0.6, mirror=True)
    p.stroke([(-0.015, -0.04), (0.0, -0.047), (0.015, -0.04)], 0.001)
    # Складки щёк.
    p.stroke([(0.036, -0.006), (0.06, -0.022), (0.072, -0.04)], 0.0012, 0.0006, alpha=0.7, mirror=True)
    # От уголков рта вниз: глубокая складка улыбки, одна и сильная.
    p.fill([(0.06, -0.06), (0.066, -0.082), (0.06, -0.105), (0.046, -0.127), (0.066, -0.11), (0.078, -0.06)], SHADE, alpha=0.45, mirror=True)
    p.stroke([(0.058, -0.06), (0.064, -0.082), (0.058, -0.105), (0.044, -0.126)], 0.002, 0.0008, mirror=True)
    # Глубокие линии от уголков рта к подбородку и тень по бокам подбородка.
    p.stroke([(0.05, -0.07), (0.052, -0.09), (0.042, -0.114)], 0.0015, 0.0006, alpha=0.8, mirror=True)
    p.fill([(0.03, -0.098), (0.05, -0.098), (0.04, -0.128), (0.02, -0.136), (0.024, -0.118)], SHADE, alpha=0.22, mirror=True, soft=6)
    # Штрихи на скулах.
    for points in ([(0.042, 0.014), (0.056, -0.008)], [(0.056, 0.016), (0.07, -0.01)], [(0.03, 0.006), (0.04, -0.01)]):
        p.stroke(points, 0.0012, 0.0005, alpha=0.65, mirror=True)
    # Сомкнутые губы.
    xs = np.linspace(-0.046, 0.046, 11)
    upper = [(x, -0.056 - 2.0 * x * x) for x in xs] + [(x, float(mouth_line(x)) + 0.0005) for x in xs[::-1]]
    p.fill(upper, (0.66, 0.42, 0.4), alpha=0.55)
    lower = [(x, float(mouth_line(x)) - 0.0005) for x in xs] + [(x, float(mouth_line(x)) - 0.015 * (1 - (x / 0.05) ** 2)) for x in xs[::-1]]
    p.fill(lower, (0.72, 0.47, 0.43), alpha=0.4)
    p.stroke([(x, float(mouth_line(x))) for x in np.linspace(-0.052, 0.052, 11)], 0.0019, 0.0019)
    p.stroke([(0.012, -0.08), (0.026, -0.077)], 0.0018, 0.0008, WHITE_TONE, alpha=0.5)
    p.fill([(-0.026, -0.0885), (0.0, -0.0945), (0.026, -0.0885), (0.012, -0.0865), (0.0, -0.0895), (-0.012, -0.0865)], SHADE, alpha=0.6)
    # Под подбородком тень, сам подбородок без контура: его отделяет тень на шее.
    p.fill([(-0.09, -0.13), (-0.03, -0.138), (0.03, -0.138), (0.09, -0.13), (0.09, -0.19), (-0.09, -0.19)], DEEP)
    p.fill([(0.03, -0.062), (0.09, -0.075), (0.085, -0.085), (0.05, -0.074)], SHADE, alpha=0.5, mirror=True)
    # Края лица темнее: прямая граница головы растворяется в темноте капюшона.
    p.fill([(0.078, 0.08), (0.12, 0.08), (0.12, -0.075), (0.082, -0.075), (0.082, 0.0)], DEEP, alpha=0.9, mirror=True)
    return p.to_blender("yhwach_face")


def paint_neck():
    p = Painter(*NECK_BOX, 1600, SKIN_TONE)
    p.fill([(0.06, 1.72), (0.11, 1.72), (0.11, 1.5), (0.02, 1.5), (0.03, 1.56)], SHADE, alpha=0.55, mirror=True)
    # Тень от подбородка на шее.
    p.fill([(-0.085, 1.76), (0.085, 1.76), (0.075, 1.69), (0.045, 1.655), (0.0, 1.64), (-0.045, 1.655), (-0.075, 1.69)], SHADE)
    p.stroke([(0.07, 1.68), (0.045, 1.6), (0.016, 1.515)], 0.0028, 0.0012, mirror=True)
    p.stroke([(0.03, 1.64), (0.018, 1.58)], 0.0018, 0.0008, alpha=0.7, mirror=True)
    p.stroke([(-0.012, 1.615), (0.0, 1.605), (0.012, 1.615)], 0.0016, 0.0016, alpha=0.7)
    p.stroke([(0.016, 1.51), (0.06, 1.52), (0.12, 1.54)], 0.0024, 0.001, mirror=True)
    p.stroke([(-0.016, 1.51), (0.0, 1.495), (0.016, 1.51)], 0.002, 0.002)
    p.stroke([(0.0, 1.49), (0.0, 1.33)], 0.002, 0.001)
    p.fill([(0.0, 1.45), (0.06, 1.46), (0.1, 1.42), (0.06, 1.39), (0.012, 1.4)], SHADE, alpha=0.3, mirror=True)
    return p.to_blender("yhwach_neck")


# ------------------------------------------------------------------ шея, грудь, рубашка

def torso_field(x, f, z):
    ax = np.abs(x)
    h = cone(x, f, z, (0, 0.012, 1.74), (0, -0.012, 1.5), 0.072, 0.09)
    h = smin(h, ell(x, f, z, 0, 0.008, 1.34, 0.22, 0.135, 0.24), 0.06)
    h = smin(h, cone(ax, f, z, (0.05, 0.0, 1.61), (0.34, -0.02, 1.5), 0.085, 0.105), 0.05)
    h = smin(h, cone(ax, f, z, (0.052, 0.04, 1.72), (0.012, 0.09, 1.51), 0.011, 0.012), 0.012)
    h = smin(h, cone(ax, f, z, (0.014, 0.102, 1.5), (0.135, 0.07, 1.53), 0.0095, 0.0105), 0.012)
    h = smin(h, ell(ax, f, z, 0.086, 0.092, 1.38, 0.09, 0.05, 0.062), 0.03)
    return scut(h, ell(x, f, z, 0, 0.114, 1.495, 0.016, 0.018, 0.018), 0.012)


def scut(a, b, k):
    return smax(a, -b, k)


SHIRT_BOX = (-0.45, 0.45, 1.0, 1.82)


def shirt_inner(z, side):
    """Рваный край рубашки у кожи: от него и до плаща лежит белое."""
    w = 0.098 * np.clip((z - 1.38) / 0.26, 0, 1) ** 0.85
    jag = 0.013 * saw(z * 5.3 + 0.3 + 0.37 * side) + 0.005 * saw(z * 13.0 + 0.11 * side)
    return np.where(z < 1.38, 0.0, w + jag * smooth(1.38, 1.48, z))


def shirt_top(x, side):
    """Верх воротника: у шеи высоко, к плечу падает, край рваный."""
    ax = np.abs(x)
    return 1.785 - 0.62 * np.clip(ax - 0.08, 0, 1) - 0.03 * saw(ax * 14.0 + side * 0.3) - 0.012 * saw(ax * 37.0)


def torso_front(x, z):
    """Перед торса: луч с фронта до поверхности."""
    f = np.full_like(x, 0.4)
    for _ in range(26):
        f = f - np.maximum(torso_field(x, f, z) * 0.9, 0.0006)
    return np.maximum(f, -0.13)


def shirt_panel(side):
    rows, cols = int(300 / QUALITY), int(120 / QUALITY)
    V, U = np.meshgrid(np.linspace(0, 1, rows), np.linspace(0, 1, cols), indexing="ij")
    guess = U * 0.42
    Z = 1.02 + (shirt_top(guess, side) - 1.02) * V
    inner = shirt_inner(Z, side)
    X = inner + U * (0.42 - inner)
    Z = 1.02 + (shirt_top(X, side) - 1.02) * V
    inner = shirt_inner(Z, side)
    X = inner + U * (0.42 - inner)
    stand = 0.035 * smooth(1.6, 1.76, Z) * smooth(0.06, 0.16, X)
    folds = 0.007 * pn(X * 13 + side, Z * 4.5, 1.7) + 0.003 * pn(X * 34, Z * 9, side * 2.0)
    F = torso_front(X * side, Z) + 0.018 + stand + folds
    P = np.stack([X * side, -F, Z], -1)
    index = np.arange(rows * cols).reshape(rows, cols)
    Q = np.stack([index[:-1, :-1], index[:-1, 1:], index[1:, 1:], index[1:, :-1]], -1).reshape(-1, 4)
    flat = P.reshape(-1, 3)
    if np.sum(normals_of(flat, Q)[:, 1]) > 0:
        Q = Q[:, ::-1]
    return flat, Q


def collar_back():
    """Воротник за шеей: белое видно за челюстью, а не чёрный провал."""
    rows, cols = int(60 / QUALITY), int(160 / QUALITY)
    V, U = np.meshgrid(np.linspace(0, 1, rows), np.linspace(-1, 1, cols), indexing="ij")
    X = U * 0.25
    top = 1.8 - 0.36 * np.clip(np.abs(X) - 0.1, 0, 1) - 0.02 * saw(np.abs(X) * 18.0)
    Z = 1.5 + (top - 1.5) * V
    F = -0.1 - 0.5 * X * X + 0.04 * smooth(1.6, 1.78, Z)
    P = np.stack([X, -F, Z], -1)
    index = np.arange(rows * cols).reshape(rows, cols)
    return P.reshape(-1, 3), np.stack([index[:-1, :-1], index[:-1, 1:], index[1:, 1:], index[1:, :-1]], -1).reshape(-1, 4)


def paint_shirt():
    lavender = (0.74, 0.76, 0.89)
    p = Painter(*SHIRT_BOX, 1400, (0.97, 0.97, 0.985))
    # Синеватая тень у шеи, как на кадрах.
    p.fill([(0.075, 1.82), (0.16, 1.82), (0.17, 1.7), (0.125, 1.62), (0.095, 1.6), (0.085, 1.66)], lavender, mirror=True)
    # Плащ бросает тень на рубашку вдоль всего выреза.
    zs = np.linspace(1.14, 1.72, 14)
    half = np.interp(zs, WINDOW[:, 0], WINDOW[:, 1])
    p.fill([(max(h - 0.012, 0.003), z) for h, z in zip(half, zs)] + [(h + 0.06, z) for h, z in zip(half[::-1], zs[::-1])], lavender, alpha=0.7, mirror=True)
    # Складки ткани.
    for points, alpha in (([(0.11, 1.66), (0.07, 1.52), (0.04, 1.4)], 0.4), ([(0.14, 1.62), (0.1, 1.48), (0.075, 1.38)], 0.3), ([(0.1, 1.74), (0.13, 1.68), (0.18, 1.64)], 0.45), ([(0.06, 1.34), (0.03, 1.24)], 0.25)):
        p.stroke(points, 0.0022, 0.0006, alpha=alpha, mirror=True)
    p.fill([(0.08, 1.58), (0.1, 1.5), (0.07, 1.4), (0.058, 1.48)], lavender, alpha=0.55, mirror=True)
    p.stroke([(0.0, 1.38), (0.0, 1.13)], 0.0016, 0.0009, alpha=0.5)
    for side in (-1, 1):
        p.stroke([(side * float(shirt_inner(z, side)), z) for z in np.linspace(1.4, 1.78, 50)], 0.002, 0.0016, alpha=0.85)
        p.stroke([(side * x, float(shirt_top(x, side)) - 0.001) for x in np.linspace(0.1, 0.4, 40)], 0.0016, 0.0012, alpha=0.6)
    return p.to_blender("yhwach_shirt")


# ------------------------------------------------------------------ капюшон и плащ

def saw(x):
    """Длинный склон и резкий обрыв: так рвётся ткань."""
    f = x - np.floor(x)
    return f * f


def table_column(table, index, s):
    """То же сглаживание таблицы, что в симуляции."""
    fine = np.linspace(0, 1, 800)
    values = np.interp(fine, table[:, 0], table[:, index])
    kernel = np.hanning(41)
    kernel /= kernel.sum()
    padded = np.concatenate([np.full(20, values[0]), values, np.full(20, values[-1])])
    return np.interp(s, fine, np.convolve(padded, kernel, mode="valid"))


def catmull(f):
    return ((-0.5 * f + f * f - 0.5 * f * f * f), (1 - 2.5 * f * f + 1.5 * f * f * f), (0.5 * f + 2 * f * f - 1.5 * f * f * f), (-0.5 * f * f + 0.5 * f * f * f))


def sample_grid(grid, rows, cols):
    """Плавная выборка из сетки ткани. По столбцам сетка замкнута."""
    ns, nu = grid.shape[:2]
    rows = np.clip(rows, 0, ns - 1)
    i0 = np.clip(np.floor(rows).astype(np.int64), 1, ns - 3)
    j0 = np.floor(cols).astype(np.int64)
    wi, wj = catmull(rows - i0), catmull(cols - j0)
    out = 0.0
    for a in range(4):
        for b in range(4):
            out = out + (wi[a] * wj[b])[..., None] * grid[i0 + a - 1, (j0 + b - 1) % nu]
    return out


CLOAK_ROWS, CLOAK_COLS, CLOAK_HOLES = 620, 1500, True
WINDOW = np.array([(1.12, 0.0), (1.2, 0.012), (1.3, 0.035), (1.4, 0.07), (1.5, 0.12), (1.6, 0.19), (1.66, 0.23), (1.72, 0.25), (1.76, 0.235), (1.785, 0.18), (1.81, 0.12), (1.84, 0.06), (1.865, 0.0)])


def cloak_hem(th):
    """Подол: неровный, с узкими рваными разрывами."""
    ex, ef = np.sin(th), np.cos(th)
    amp = 0.5 + 0.9 * np.clip(pn(ex * 2.7 + 3, ef * 2.7, 1.7) + 0.5, 0, 1)
    rips = 0.15 * amp * spike(th / math.pi * 9.0 + 0.37) + 0.07 * spike(th / math.pi * 23.0 + 0.71) + 0.035 * spike(th / math.pi * 53.0 + 0.2)
    return np.clip(0.995 - 0.09 * (pn(ex * 2.3 + 4, ef * 2.3, 1.3) + 0.5) - rips, 0.3, 1.0)


def opening(z, side, hole):
    """Полуширина окна лица и выреза с рваным краем."""
    half = np.interp(z, WINDOW[:, 0], WINDOW[:, 1], left=0.0, right=0.0)
    chest = 0.018 * saw(z * 7.3 + 0.3 + side * 0.37) + 0.008 * saw(z * 17.0 + side * 0.61) + 0.003 * spike(z * 53.0 + side * 0.2)
    brow = 0.014 * spike(z * 58.0 + 0.4 + side * 0.2) + 0.009 * spike(z * 121.0 + 0.2)
    return half + chest * smooth(1.2, 1.42, z) * smooth(1.72, 1.68, z) + brow * smooth(1.76, 1.8, z) * smooth(1.862, 1.84, z)


def build_cloak():
    data = np.load(CLOAK_FILE)
    grid = data["f%d" % CLOAK_FRAME].astype(np.float64)
    S0, table, hole = data["S"][:, 0].astype(np.float64), data["table"], data["hole"]
    ns, nu = grid.shape[:2]
    # Нормали ткани по её сетке: знак общий, внутри складок ткань и правда смотрит вбок.
    normal = np.cross(np.gradient(grid, axis=0), np.roll(grid, -1, axis=1) - np.roll(grid, 1, axis=1))
    for _ in range(2):
        normal = normal * 0.4 + (np.roll(normal, 1, axis=1) + np.roll(normal, -1, axis=1)) * 0.2 + (np.roll(normal, 1, axis=0) + np.roll(normal, -1, axis=0)) * 0.1
    unit = normal / np.maximum(np.linalg.norm(normal, axis=-1, keepdims=True), 1e-12)
    blurred = grid.copy()
    for _ in range(14):
        blurred = blurred * 0.4 + (np.roll(blurred, 1, axis=1) + np.roll(blurred, -1, axis=1)) * 0.2 + (np.concatenate([blurred[:1], blurred[:-1]]) + np.concatenate([blurred[1:], blurred[-1:]])) * 0.1
    theta0 = np.linspace(-math.pi, math.pi, nu, endpoint=False)
    outward = np.stack([np.sin(theta0), -np.cos(theta0), np.zeros(nu)], -1)
    if np.sum(normal[ns // 3] * outward) < 0:
        normal, unit = -normal, -unit
    # Плюс — гребень складки, минус — её глубина.
    cavity = np.sum((grid - blurred) * unit, axis=-1)
    cavity = np.clip(cavity / max(float(np.std(cavity)) * 1.6, 1e-6), -1, 1)[..., None]
    v, u = np.linspace(0, 1, int(CLOAK_ROWS / QUALITY)), np.linspace(-1, 1, int(CLOAK_COLS / QUALITY) + 1)
    V, U = np.meshgrid(v, u, indexing="ij")
    side = np.where(U >= 0, 1.0, -1.0)
    th0 = side * math.pi * (1 - np.abs(U))
    S = cloak_hem(th0) * V
    Z = table_column(table, 1, S)
    RX, RF = np.maximum(table_column(table, 2, S), 1e-3), np.maximum(table_column(table, 3, S), 1e-3)  # окно считается по форме из таблицы
    half = opening(Z, side, hole)
    phi = np.arctan2(half, RF * np.sqrt(np.maximum(1 - (half / RX) ** 2, 0.02)))
    theta = side * (phi + (math.pi - phi) * (1 - np.abs(U)))
    ex, ef = np.sin(theta), np.cos(theta)
    def at(ss, th):
        """Точка и нормаль ткани: ход сверху вниз ss и угол th."""
        sx, sf = np.sin(th), np.cos(th)
        grid_rows = (ss - S0[0]) / (S0[1] - S0[0])
        grid_cols = (th + math.pi) / (2 * math.pi) * nu
        point = sample_grid(grid, grid_rows, grid_cols)
        unit_n = sample_grid(normal, grid_rows, grid_cols)
        unit_n = unit_n / np.maximum(np.linalg.norm(unit_n, axis=-1, keepdims=True), 1e-12)
        # Ветер по ткани.
        flow = smooth(0.3, 0.95, ss)
        strength = 0.6 + 0.8 * np.clip(pn(sx * 1.7 + 2, sf * 1.7, 0.6) + 0.5, 0, 1)
        phase = ss * 15.0 * (1 + 0.25 * pn(sx * 1.3, sf * 1.3, 3.1)) + th * 3.0 + 5.0 * pn(sx * 2.4, sf * 2.4, 1.9)
        wave = 0.17 * flow ** 1.3 * strength * np.sin(phase) + 0.05 * flow * np.sin(phase * 2.3 + 1.1)
        point = point + unit_n * (wave * (1 - 0.7 * smooth(0.35, 0.85, sf)))[..., None]
        right, left = smooth(0.2, 0.95, sx), smooth(0.2, 0.95, -sx)
        point[..., 0] += point[..., 0] * (0.34 * right - 0.04 * left) * flow
        point[..., 2] += (0.62 * right - 0.2 * left) * flow ** 1.6
        # Макушка: гладкий купол по таблице, без заломов там, где ткань закреплена.
        height = table_column(table, 1, ss)
        side_r, front_r, back_r = np.maximum(table_column(table, 2, ss), 1e-3), np.maximum(table_column(table, 3, ss), 1e-3), np.maximum(table_column(table, 4, ss), 1e-3)
        radius = 1 / np.sqrt((sx / side_r) ** 2 + (sf / np.where(sf > 0, front_r, back_r)) ** 2)
        dome = np.stack([radius * sx, -radius * sf, height], -1)
        return dome + (point - dome) * smooth(0.05, 0.14, ss)[..., None], unit_n

    P, N = at(S, theta)
    rows = (S - S0[0]) / (S0[1] - S0[0])
    cols = (theta + math.pi) / (2 * math.pi) * nu
    P[0] = P[0].mean(axis=0)
    smooth_P = P.copy()
    shade = np.clip(sample_grid(cavity, rows, cols)[..., 0], -1, 1) * 0.5 + 0.5
    fibres = np.clip(0.5 + pn(ex * 110, ef * 110, S * 4) * 0.8 + pn(ex * 260, ef * 260, S * 7) * 0.5, 0, 1)
    # Капюшон: мягкие складки от макушки к плечам, ткань не гладкий шар.
    drape = smooth(0.0, 0.2, np.abs(pn(ex * 6.5 + 1, ef * 6.5, S * 1.4)))
    shade = shade * (1 - (1 - drape) * 0.55 * smooth(0.03, 0.1, S) * (1 - smooth(0.2, 0.3, S)))
    valley, ridge = np.array((0.026, 0.017, 0.021)), np.array((0.125, 0.045, 0.062))
    colors = valley + (ridge - valley) * (shade ** 1.3)[..., None]
    colors = colors * (0.78 + 0.44 * fibres)[..., None]
    # Мелкая фактура: только живой край, а не комья.
    fine = (0.005 * pn(ex * 60, ef * 60, S * 10) + 0.003 * pn(ex * 150, ef * 150, S * 20)) * smooth(0.02, 0.1, S) * (1 + 2 * smooth(0.88, 1.0, V))
    P = P + N * fine[..., None]
    index = np.arange(P.shape[0] * P.shape[1]).reshape(P.shape[:2])
    Q = np.stack([index[:-1, :-1], index[:-1, 1:], index[1:, 1:], index[1:, :-1]], -1).reshape(-1, 4)
    flat_S, flat_N = smooth_P.reshape(-1, 3), N.reshape(-1, 3)
    fn = np.cross(flat_S[Q[:, 2]] - flat_S[Q[:, 0]], flat_S[Q[:, 3]] - flat_S[Q[:, 1]])
    if np.sum(fn * flat_N[Q[:, 0]]) < 0:
        Q = Q[:, ::-1]
    # Редкие рваные дыры у подола.
    holes = fbm(ex * 5.0 + 9, ef * 5.0, S * 7.0, 3)
    mask = (holes > 0.42 - 0.12 * smooth(0.7, 1.0, S)) & (S > 0.68) & (ef < 0.7)
    if CLOAK_HOLES:
        Q = Q[~mask[:-1, :-1].reshape(-1)]
    log("ткань кадр", CLOAK_FRAME, "наибольший радиус", round(float(np.hypot(P[..., 0], P[..., 1]).max()), 2), "ниже всего", round(float(P[..., 2].min()), 2))
    edge_distance = (np.abs(theta) - phi) * np.hypot(P[..., 0], P[..., 1])
    return {"V": V, "U": U, "hole": mask, "at": at, "theta": theta, "edge": edge_distance, "name": "cloak", "P": P, "N": N, "Q": Q, "phi": phi, "S": S, "ex": ex, "ef": ef, "smooth": smooth_P, "colors": colors.reshape(-1, 3)}


def layer_point(layer, degrees, z):
    P, N, phi = layer["P"], layer["N"], layer["phi"]
    th = math.radians(abs(degrees))
    ns, nu1 = P.shape[:2]
    edge = phi[:, nu1 - 1 if degrees >= 0 else 0]
    a = np.clip(1 - (th - edge) / (math.pi - edge), 0, 1)
    cols = np.rint((nu1 - 1) * (a + 1) / 2).astype(int) if degrees >= 0 else np.rint((nu1 - 1) * (1 - a) / 2).astype(int)
    rows = np.arange(ns)
    miss = np.abs(P[rows, cols, 2] - z)
    r = int(np.argmin(miss))
    if miss[r] > 0.03:
        return None
    return P[r, cols[r]], N[r, cols[r]]


def top_point(degrees, z):
    """Точка плаща: 0 — спереди, плюс — вправо от зрителя. Если здесь ткани нет (рваный подол), берём чуть выше."""
    for height in (z, z + 0.1, z + 0.2, z + 0.32, z + 0.45):
        found = layer_point(CLOAK, degrees, height)
        if found is not None:
            return found
    return CLOAK["P"][0, 0], CLOAK["N"][0, 0]


def hood_point(x, z):
    """Точка капюшона, видимая спереди, по положению на лице: x вправо, z вверх."""
    P, N, S = CLOAK["P"], CLOAK["N"], CLOAK["S"]
    miss = np.hypot(P[..., 0] - x, P[..., 2] - z) + 10.0 * ((S > 0.2) | (P[..., 1] > 0))
    at = np.unravel_index(int(np.argmin(miss)), miss.shape)
    return P[at], N[at]


# ------------------------------------------------------------------ глаза

def eye_geometry(row, col, half_w, half_h, tilt, dim=1.0, seed=0, ring_scale=1.0, sides=96, iris=True):
    """Глаз, лежащий на складках плаща: выпуклый белок, плотная неровная кайма, маленький зрачок."""
    at = CLOAK["at"]
    s0, t0 = np.array([CLOAK["S"][row, col]]), np.array([CLOAK["theta"][row, col]])
    centre, centre_n = at(s0, t0)
    centre, n = centre[0], centre_n[0]
    # Шаг берём порядка размера глаза: так мелкая складка под центром не искажает его.
    wide = max(float(np.hypot(centre[0], centre[1])), 0.05)
    ds, dt = half_h / 2.5 * 0.6, half_w / wide * 0.6
    along_r = (at(s0 + ds, t0)[0][0] - at(s0 - ds, t0)[0][0]) / (2 * ds)
    along_c = (at(s0, t0 + dt)[0][0] - at(s0, t0 - dt)[0][0]) / (2 * dt)
    up = np.array((0, 0, 1.0)) - n * n[2]
    up /= max(np.linalg.norm(up), 1e-6)
    right = np.cross(up, n)
    rings = np.concatenate([np.linspace(0, 0.3, int(16 * ring_scale), endpoint=False), np.linspace(0.3, 0.86, int(10 * ring_scale), endpoint=False), np.linspace(0.86, 1, int(14 * ring_scale))])
    R, A = np.meshgrid(rings, np.linspace(0, 2 * math.pi, sides, endpoint=False), indexing="ij")
    phase = seed * 1.7
    wobble = 1 + 0.04 * np.sin(A * 3 + phase) + 0.016 * np.sin(A * 5 + phase * 1.3)
    rim = 0.09 + 0.035 * np.sin(A * 7 + phase) + 0.018 * np.sin(A * 17 + phase * 2) + 0.014 * np.sin(A * 31 + phase * 3)
    total = 1 + rim
    inside = R * total
    u, v = inside * wobble * np.cos(A), inside * wobble * np.sin(A)
    t = math.radians(tilt)
    x, y = u * half_w * math.cos(t) - v * half_h * math.sin(t), u * half_w * math.sin(t) + v * half_h * math.cos(t)
    target = x[..., None] * right + y[..., None] * up
    # Контур остаётся ровным овалом, а по глубине следует за тканью.
    a11, a12, a22 = along_r @ along_r, along_r @ along_c, along_c @ along_c
    det = a11 * a22 - a12 * a12
    b1, b2 = target @ along_r, target @ along_c
    base, _ = at(np.clip(s0 + (a22 * b1 - a12 * b2) / det, 0.01, 1.0), t0 + (a11 * b2 - a12 * b1) / det)
    bulge = min(min(half_w, half_h) * 0.32, 0.05)
    dome = bulge * np.sqrt(np.clip(1 - inside ** 2, 0, 1))
    lift = 0.012 * (1 - smooth(1.0, total, np.maximum(inside, 1.0)))
    height = np.sum((base - centre) * n, axis=-1)
    P = centre + x[..., None] * right + y[..., None] * up + n * (height * 0.7 + dome + lift)[..., None]
    white = np.array((232, 226, 220.)) / 255 + (np.array((196, 182, 180.)) - np.array((232, 226, 220.))) / 255 * smooth(0.72, 1.0, inside)[..., None]
    c = white * dim
    if iris:
        pupil = 0.12 * (1 + 0.07 * np.sin(A * 3 + phase * 2) + 0.03 * np.sin(A * 7 + phase))
        c = c + (np.array((88, 18, 26.)) / 255 - c) * (1 - smooth(pupil - 0.012, pupil + 0.012, inside))[..., None]
    c = c + (np.array((14, 10, 14.)) / 255 - c) * smooth(0.985, 1.005, inside)[..., None]
    index = np.arange(R.size).reshape(R.shape)
    nxt = np.roll(index, -1, axis=1)
    Q = np.stack([index[:-1], index[1:], nxt[1:], nxt[:-1]], -1).reshape(-1, 4)
    flat = P.reshape(-1, 3)
    if np.sum(np.cross(flat[Q[:, 2]] - flat[Q[:, 0]], flat[Q[:, 3]] - flat[Q[:, 1]]) @ n) < 0:
        Q = Q[:, ::-1]
    return {"P": flat, "Q": Q, "colors": c.reshape(-1, 3), "centre": centre, "right": right, "up": up, "normal": n, "half_w": half_w, "half_h": half_h, "tilt": t, "top": float(bulge + 0.012), "seed": seed}


def eye_patch(name, row, col, half_w, half_h, tilt, dim=1.0, seed=0):
    eye = eye_geometry(row, col, half_w, half_h, tilt, dim, seed)
    add_object(name, eye["P"], eye["Q"], MAT["eye"], colors=eye["colors"])


def cloak_cell(degrees, z):
    """Ячейка ткани: угол вокруг фигуры и высота."""
    P, phi = CLOAK["P"], CLOAK["phi"]
    th = math.radians(abs(degrees))
    ns, nu1 = P.shape[:2]
    edge = phi[:, nu1 - 1 if degrees >= 0 else 0]
    a = np.clip(1 - (th - edge) / (math.pi - edge), 0, 1)
    cols = np.rint((nu1 - 1) * (a + 1) / 2).astype(int) if degrees >= 0 else np.rint((nu1 - 1) * (1 - a) / 2).astype(int)
    rows = np.arange(ns)
    r = int(np.argmin(np.abs(P[rows, cols, 2] - z)))
    return r, int(cols[r])


def hood_cell(x, z):
    P, S = CLOAK["P"], CLOAK["S"]
    miss = np.hypot(P[..., 0] - x, P[..., 2] - z) + 10.0 * ((S > 0.2) | (P[..., 1] > 0))
    at = np.unravel_index(int(np.argmin(miss)), miss.shape)
    return int(at[0]), int(at[1])


HOOD_EYES = [
    (0.115, 1.945, 0.068, 0.074, -20),
    (-0.15, 1.935, 0.042, 0.056, 18),
    (-0.06, 2.02, 0.036, 0.04, 8),
    (-0.05, 1.925, 0.038, 0.044, 12),
    (0.012, 1.96, 0.028, 0.036, -8),
    (-0.115, 1.875, 0.026, 0.021, 22),
    (-0.19, 1.85, 0.038, 0.03, 24),
    (0.19, 1.85, 0.046, 0.036, -24),
]

MANTLE_EYES = [
    (-40, 1.02, 0.11, 0.075, 24), (42, 1.04, 0.13, 0.085, -22), (-52, 0.66, 0.2, 0.11, 26), (54, 0.68, 0.22, 0.12, -22), (-66, 1.16, 0.1, 0.065, 30), (68, 1.18, 0.11, 0.07, -30),
    (-30, 0.36, 0.13, 0.09, 8), (34, 0.34, 0.15, 0.1, -10), (-76, 0.74, 0.16, 0.09, 18), (82, 0.76, 0.2, 0.1, -14),
    (104, 0.85, 0.22, 0.11, 10), (126, 0.5, 0.26, 0.14, -10), (-112, 0.78, 0.2, 0.11, -10), (-134, 0.45, 0.24, 0.12, 12),
    (160, 0.85, 0.22, 0.12, 6), (-165, 0.5, 0.26, 0.14, -6),
]


# ------------------------------------------------------------------ пряди

def spline(pts, t):
    pts = np.asarray(pts, float)
    n = len(pts)
    f = t * (n - 1)
    i = np.minimum(f.astype(int), n - 2)
    s = (f - i)[:, None]
    p0, p1, p2, p3 = pts[np.maximum(i - 1, 0)], pts[i], pts[i + 1], pts[np.minimum(i + 2, n - 1)]
    return 0.5 * (2 * p1 + (p2 - p0) * s + (2 * p0 - 5 * p1 + 4 * p2 - p3) * s * s + (3 * p1 - p0 - 3 * p2 + p3) * s * s * s)


class Strands:
    def __init__(self):
        self.P, self.Q, self.T, self.count = [], [], [], 0

    def add(self, pts, r0, flat=0.55, sides=6, steps=26, belly=0.35, grow=0.0, taper=0.85):
        t = np.linspace(0, 1, steps + 1)
        C = spline(pts, t)
        D = np.gradient(C, axis=0)
        D /= np.maximum(np.linalg.norm(D, axis=1, keepdims=True), 1e-9)
        side = np.cross(D[0], np.array([0.13, 0.27, 0.95]))
        side /= np.linalg.norm(side)
        rings = []
        for i in range(steps + 1):
            side = side - D[i] * np.dot(side, D[i])
            side /= max(np.linalg.norm(side), 1e-9)
            other = np.cross(D[i], side)
            r = r0 * (grow + (1 - grow) * smooth(0.0, 0.12, t[i]) if grow > 0 else 1) * (1 - t[i]) ** taper * (1 + belly * np.sin(math.pi * t[i]))
            a = np.linspace(0, 2 * math.pi, sides, endpoint=False)
            rings.append(C[i] + np.outer(np.cos(a), side) * r + np.outer(np.sin(a), other) * r * flat)
        rings = np.array(rings).reshape(-1, 3)
        base = self.count
        idx = np.arange((steps + 1) * sides).reshape(steps + 1, sides) + base
        nxt = np.roll(idx, -1, axis=1)
        self.Q.append(np.stack([idx[:-1], nxt[:-1], nxt[1:], idx[1:]], -1).reshape(-1, 4))
        self.P.append(rings)
        self.T.append(np.repeat(t, sides))
        self.count += len(rings)

    def arrays(self):
        return np.concatenate(self.P), np.concatenate(self.Q)

    def colors(self):
        t = smooth(0.3, 0.66, np.concatenate(self.T))[:, None]
        root, tip = np.array((0.03, 0.017, 0.022)), np.array((0.27, 0.034, 0.09))
        return root + (tip - root) * t


def wavy(start, direction, length, drop, wave, freq, phase, curl, n=12):
    """Прядь: идёт от start, падает, волнуется, кончик закручен вверх."""
    d = np.array(direction, float)
    d /= np.linalg.norm(d)
    lateral = np.cross(d, np.array([0, 0, 1.0]))
    lateral /= max(np.linalg.norm(lateral), 1e-6)
    pts = []
    for k in range(n):
        t = k / (n - 1)
        p = np.array(start, float) + d * length * t
        p[2] -= drop * t * t
        p[2] += wave * np.sin(t * freq + phase) * t
        p += lateral * wave * 0.6 * np.cos(t * freq * 0.7 + phase) * t
        pts.append(p)
    a = pts[-1]
    for k in range(1, 4):
        angle = k * 0.85
        pts.append(a + d * curl * math.sin(angle) + np.array([0, 0, 1.0]) * curl * (1 - math.cos(angle)))
    return pts


def flowing(start, direction, length, drop, wave, freq, phase, curl, n=14):
    """Лента волос: две волны разной длины, кончик закручен. Правее от зрителя ветер несёт дальше."""
    d = np.array(direction, float)
    d /= np.linalg.norm(d)
    lateral = np.cross(d, np.array((0, 0, 1.0)))
    lateral /= max(np.linalg.norm(lateral), 1e-6)
    points = []
    for k in range(n):
        t = k / (n - 1)
        p = np.array(start, float) + d * length * t
        p[2] += -drop * t * t + wave * t * (np.sin(t * freq + phase) + 0.45 * np.sin(t * freq * 2.3 + phase * 1.7))
        p += lateral * wave * 0.7 * t * np.cos(t * freq * 0.7 + phase)
        points.append(p)
    end = points[-1]
    for k in range(1, 4):
        angle = k * 0.8
        points.append(end + d * curl * math.sin(angle) + np.array((0, 0, 1.0)) * curl * (1 - math.cos(angle)))
    return points


def hair_strands():
    rng = np.random.RandomState(4711)
    hair = Strands()
    # Короткие пряди по куполу.
    for degrees, z, dz in ((-74, 2.09, 0.55), (-92, 2.0, 0.15), (-106, 1.92, -0.2), (70, 2.1, 0.5), (90, 2.0, 0.05), (106, 1.91, -0.3), (150, 2.09, 0.5), (-150, 2.09, 0.5)):
        p, n = top_point(degrees, z)
        th = math.radians(degrees)
        hair.add(flowing(p - n * 0.008, (math.sin(th), -math.cos(th) * 0.3, dz), 0.07 + rng.rand() * 0.13, 0.015, 0.012, 7, rng.rand() * 6.3, 0.012 + rng.rand() * 0.012, n=7), 0.006 + rng.rand() * 0.005, flat=0.45, sides=6, steps=20, belly=0.15, taper=1.4)
    # Длинные ленты из-под капюшона: слева тяжело падают, справа ветер относит их дальше.
    for side, count in ((-1, 6), (1, 7)):
        for i in range(count):
            rank = i / (count - 1)
            start = (side * (0.15 + 0.05 * rng.rand()), 0.03 - rng.rand() * 0.18, 1.78 + rank * 0.22)
            length = (0.7 + 0.9 * (1 - rank) + rng.rand() * 0.5) * (1.15 if side > 0 else 0.95)
            direction = (side * 0.9, 0.05 + rng.rand() * 0.25, -0.6 + rng.rand() * 0.6 + (0.1 if side > 0 else 0.0))
            drop = (0.12 + rng.rand() * 0.3) * (0.7 if side > 0 else 1.2)
            hair.add(flowing(start, direction, length, drop, 0.08 + rng.rand() * 0.09, 4 + rng.rand() * 3.5, rng.rand() * 6.3, 0.04 + rng.rand() * 0.06, n=18), 0.012 + rng.rand() * 0.016, flat=0.38, sides=6, steps=56, belly=0.6, taper=1.1, grow=0.25)
    # Рваные хвосты на краях ткани: кончики, которые треплет ветер.
    for degrees, z, length, drop, thick in ((96, 0.95, 1.0, 0.1, 0.05), (100, 0.6, 1.3, 0.2, 0.06), (108, 0.35, 1.0, 0.1, 0.05), (-96, 1.05, 0.8, 0.28, 0.045), (-104, 0.65, 1.1, 0.4, 0.055), (150, 0.8, 0.9, 0.05, 0.05), (-152, 0.75, 1.0, 0.1, 0.05)):
        p, n = top_point(degrees, z)
        th = math.radians(degrees)
        d = (math.sin(th), -math.cos(th), 0.02 + rng.rand() * 0.1)
        hair.add(flowing(p - n * 0.03, d, length, drop, 0.05 + rng.rand() * 0.05, 6 + rng.rand() * 3, rng.rand() * 6.3, 0.05 + rng.rand() * 0.06), thick, steps=44, belly=0.25, taper=1.35)
    return hair


# ------------------------------------------------------------------ кисти и рукава

KNUCKLES = [(0.034, -0.118, 0.002), (0.012, -0.126, 0.004), (-0.011, -0.121, 0.003), (-0.032, -0.107, 0.0)]
LENGTHS = [(0.06, 0.038, 0.028), (0.066, 0.042, 0.03), (0.06, 0.038, 0.028), (0.046, 0.028, 0.023)]
RADIUS = [0.0125, 0.013, 0.012, 0.0105]
THUMB_BASE, THUMB_LEN = (0.034, -0.026, -0.006), (0.052, 0.042, 0.033)


def rotated(direction, axis, degrees):
    a = math.radians(degrees)
    return direction * math.cos(a) + np.cross(axis, direction) * math.sin(a) + axis * np.dot(axis, direction) * (1 - math.cos(a))


def finger_chain(start, spread, curls, lengths):
    d = rotated(np.array([0, -1.0, 0]), np.array([0, 0, 1.0]), spread)
    axis = rotated(np.array([1.0, 0, 0]), np.array([0, 0, 1.0]), spread)
    pts = [np.array(start, float)]
    for curl, length in zip(curls, lengths):
        d = rotated(d, axis, -curl)
        pts.append(pts[-1] + d * length)
    return pts


def hand_chains(spreads, curls, thumb_spread, thumb_curls):
    chains = [finger_chain(KNUCKLES[i], spreads[i], curls[i], LENGTHS[i]) for i in range(4)]
    d = rotated(np.array([0, -1.0, 0]), np.array([0, 0, 1.0]), thumb_spread)
    d = rotated(d, np.array([0, 1.0, 0]), 26)
    pts = [np.array(THUMB_BASE, float)]
    axis = np.cross(d, np.array([0, 0, 1.0]))
    axis /= np.linalg.norm(axis)
    for curl, length in zip(thumb_curls, THUMB_LEN):
        d = rotated(d, axis, -curl)
        pts.append(pts[-1] + d * length)
    chains.append(pts)
    return chains


def hand_field(chains):
    radii = [(RADIUS[i], RADIUS[i] * 0.92, RADIUS[i] * 0.84, RADIUS[i] * 0.74) for i in range(4)] + [(0.0165, 0.015, 0.0135, 0.0115)]

    def field(x, y, z):
        h = cone(x, y, z, (0, 0.022, 0), (0, -0.01, 0), 0.033, 0.033)
        h = smin(h, ell(x, y, z, 0.002, -0.065, 0, 0.052, 0.063, 0.021), 0.018)
        # Сухожилия на тыле, бугры на ладони.
        for k in KNUCKLES:
            h = smin(h, cone(x, y, z, (k[0] * 0.5, -0.02, 0.0075), (k[0], k[1], k[2] + 0.005), 0.0095, 0.0125), 0.0065)
        h = smin(h, ell(x, y, z, 0.034, -0.038, -0.011, 0.021, 0.034, 0.018), 0.012)
        h = smin(h, ell(x, y, z, -0.035, -0.058, -0.009, 0.0135, 0.034, 0.013), 0.01)
        for chain, r in zip(chains, radii):
            for i in range(3):
                h = smin(h, cone(x, y, z, chain[i], chain[i + 1], r[i], r[i + 1]), 0.004)
                h = smin(h, ell(x, y, z, *chain[i + 1], r[i + 1] * 1.14, r[i + 1] * 1.14, r[i + 1] * 1.14), 0.004) if i < 2 else h
        return h

    return field


def hand_colors(P, chains):
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    c = np.tile(np.array([230, 194, 172.]) / 255, (len(P), 1))
    # Ногти.
    for chain in chains:
        tip, before = chain[3], chain[2]
        axis = (tip - before) / np.linalg.norm(tip - before)
        rel = P - before
        along = rel @ axis
        length = np.linalg.norm(tip - before)
        back = z - (before[2] + axis[2] * along)
        wide = np.abs(x - (before[0] + axis[0] * along))
        nail = (along > length * 0.45) & (back > 0.004) & (wide < 0.0105)
        c[nail] = np.array([246, 226, 216.]) / 255
    # Линии на ладони.
    line = seg(x, y, 0.04, -0.075, -0.045, -0.094)
    line = np.minimum(line, seg(x, y, 0.045, -0.052, -0.02, -0.078))
    t = ((1 - smooth(0.0006, 0.002, line)) * (z < -0.006) * 0.4)[:, None]
    return c + (INK - c) * t


def basis(wrist, fingers, thumb_side, scale, mirror=False):
    """Матрица кисти: fingers — куда смотрят пальцы, thumb_side — куда большой палец."""
    y = -Vector(fingers).normalized()
    x = Vector(thumb_side)
    x = (x - y * x.dot(y)).normalized()
    z = x.cross(y).normalized()
    m = Matrix(((x.x * scale, y.x * scale, z.x * scale, wrist[0]), (x.y * scale, y.y * scale, z.y * scale, wrist[1]), (x.z * scale, y.z * scale, z.z * scale, wrist[2]), (0, 0, 0, 1)))
    return Matrix.Scale(-1, 4, (1, 0, 0)) @ m if mirror else m


def sleeve_field(shoulder, elbow, wrist):
    axis = np.array(wrist) - np.array(elbow)
    axis /= np.linalg.norm(axis)

    def field(x, f, z):
        h = cone(x, f, z, shoulder, elbow, 0.17, 0.115)
        h = smin(h, cone(x, f, z, elbow, wrist, 0.115, 0.07), 0.05)
        rx, rf, rz = x - wrist[0], f - wrist[1], z - wrist[2]
        along = rx * axis[0] + rf * axis[1] + rz * axis[2]
        radial = np.sqrt(np.maximum((rx - axis[0] * along) ** 2 + (rf - axis[1] * along) ** 2 + (rz - axis[2] * along) ** 2, 1e-12))
        return smin(h, np.sqrt((radial - 0.098) ** 2 + (along * 0.8) ** 2) - 0.056, 0.04)

    return field


def arm_drape(shoulder, elbow, wrist, seed):
    """Полотно под рукой: от плеча до запястья, снизу рваное, колышется."""
    rows, cols = int(60 / QUALITY), int(160 / QUALITY)
    V, U = np.meshgrid(np.linspace(0, 1, rows), np.linspace(0.05, 1, cols), indexing="ij")
    a, b, c = np.array(shoulder, float), np.array(elbow, float), np.array(wrist, float)
    first = np.clip(U / 0.5, 0, 1)[..., None]
    second = np.clip((U - 0.5) / 0.5, 0, 1)[..., None]
    root = a + (b - a) * first + (c - b) * second
    rip = 0.28 * spike(U * 6.0 + seed) + 0.14 * spike(U * 14.0 + seed * 0.7) + 0.07 * spike(U * 31.0)
    length = 1.05 * (1.02 - 0.72 * U) * (1 - rip)
    drop = V * length
    wave = 0.07 * drop * np.sin(U * 13.0 + V * 3.0 + seed) + 0.03 * drop * np.sin(U * 29.0 + seed * 2)
    x, f, z = root[..., 0] + wave, root[..., 1] + 0.09 * drop * np.sin(U * 7.0 + seed), root[..., 2] - 0.06 - drop
    P = np.stack([x, -f, z], -1)
    index = np.arange(rows * cols).reshape(rows, cols)
    return P.reshape(-1, 3), np.stack([index[:-1, :-1], index[:-1, 1:], index[1:, 1:], index[1:, :-1]], -1).reshape(-1, 4)


def sleeve_detail(wrist, axis):
    def detail(x, f, z, base):
        # Рваный край: кисть выходит из клочьев.
        d = base + 0.012 * pn(x * 15, f * 15, z * 15) + 0.008 * pn(x * 48, f * 48, z * 48)
        along = (x - wrist[0]) * axis[0] + (f - wrist[1]) * axis[1] + (z - wrist[2]) * axis[2]
        a = np.arctan2(x - wrist[0], z - wrist[2])
        return np.maximum(d, along - 0.016 - 0.04 * spike(a * 1.9 + 0.2) - 0.022 * spike(a * 4.7))

    return detail


# ------------------------------------------------------------------ сцена

def world(kind):
    w = bpy.data.worlds.get("sky") or bpy.data.worlds.new("sky")
    w.use_nodes = True
    nt = w.node_tree
    nt.nodes.clear()
    output, bg = nt.nodes.new("ShaderNodeOutputWorld"), nt.nodes.new("ShaderNodeBackground")
    nt.links.new(bg.outputs[0], output.inputs[0])
    coords, sep, path = nt.nodes.new("ShaderNodeTexCoord"), nt.nodes.new("ShaderNodeSeparateXYZ"), nt.nodes.new("ShaderNodeLightPath")
    nt.links.new(coords.outputs["Generated"], sep.inputs[0])
    noise = nt.nodes.new("ShaderNodeTexNoise")
    nt.links.new(coords.outputs["Generated"], noise.inputs["Vector"])
    noise.inputs["Scale"].default_value = 3.4
    noise.inputs["Detail"].default_value = 7
    noise.inputs["Roughness"].default_value = 0.55
    height = nt.nodes.new("ShaderNodeMapRange")
    nt.links.new(sep.outputs["Z"], height.inputs["Value"])
    height.inputs["From Min"].default_value, height.inputs["From Max"].default_value = -0.15, 0.55
    if kind == "day":
        sky = mixer(nt, "MIX", height.outputs[0], (0.72, 0.8, 0.9), (0.12, 0.32, 0.62))
        cloud = ramp(nt, noise.outputs["Fac"], 0.5, 0.6)
        color = mixer(nt, "MIX", cloud, sky, (0.95, 0.96, 0.98))
    else:
        sky = mixer(nt, "MIX", height.outputs[0], (0.32, 0.03, 0.08), (0.05, 0.005, 0.02))
        cloud = ramp(nt, noise.outputs["Fac"], 0.5, 0.68)
        color = mixer(nt, "MIX", cloud, sky, (0.5, 0.06, 0.14))
    camera_only = mixer(nt, "MIX", path.outputs["Is Camera Ray"], (0, 0, 0), color)
    nt.links.new(camera_only, bg.inputs["Color"])
    bpy.context.scene.world = w


def camera(eye, target, lens):
    cam = bpy.context.scene.camera
    cam.location = (eye[0], -eye[1], eye[2])
    direction = Vector((target[0], -target[1], target[2])) - cam.location
    cam.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
    cam.data.lens = lens


VIEWS = {
    # Имя: глаз (x, f, z), куда смотрит, объектив, небо, руки.
    "face_038": ((0.02, 1.25, 1.68), (0.01, 0, 1.86), 50, "day", False),
    "mouth_076": ((0.0, 0.62, 1.76), (0.0, 0, 1.79), 50, "day", False),
    "nose_086": ((0.0, 0.42, 1.8), (0.0, 0, 1.815), 50, "day", False),
    "face_side": ((-1.15, 0.14, 1.8), (0, 0.1, 1.82), 50, "day", False),
    "face_quarter": ((-0.75, 0.95, 1.72), (0, 0.03, 1.84), 50, "day", False),
    "front": ((0, 15.0, 1.1), (0.2, 0, 0.9), 50, "day", False),
    "as_068": ((-1.4, 6.8, 0.9), (0.72, 0, 1.32), 50, "day", False),
    "as_037": ((0.34, 2.6, 1.52), (0.06, 0, 1.68), 50, "day", False),
    "as_010": ((0, 3.0, 0.1), (0, 0, 1.56), 30, "day", True),
    "three_quarter": ((-9.5, 11.0, 2.4), (0.2, 0, 0.9), 50, "day", False),
    "side": ((-15.0, 0, 1.6), (0, -0.3, 0.9), 50, "day", False),
    "back": ((1.6, -15.0, 2.6), (0, 0, 0.9), 50, "day", False),
    "crimson": ((1.0, 5.6, 0.45), (0.1, 0, 1.46), 38, "crimson", True),
}


SHELLS = []


def update_shells(eye):
    """Кайма для этой камеры: глаз в координатах Blender."""
    position = np.array(eye, float)
    for obj, base, normals, fade, offset in SHELLS:
        view = position - base
        view /= np.maximum(np.linalg.norm(view, axis=1, keepdims=True), 1e-9)
        back = smooth(0.12, -0.02, np.sum(normals * view, axis=1))[:, None]
        shell = base + normals * (offset * fade * back - 0.05 * (1 - back))
        obj.data.vertices.foreach_set("co", shell.astype(np.float32).ravel())
        obj.data.update()


def build():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE"
    scene.eevee.taa_render_samples = 48
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.render.resolution_x, scene.render.resolution_y = 1280, 720
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.data.clip_start, cam.data.clip_end = 0.05, 200
    sun_data = bpy.data.lights.new("sun", "SUN")
    sun_data.energy = 3.14
    sun_data.angle = math.radians(1.5)
    sun_data.use_shadow = False
    sun = bpy.data.objects.new("sun", sun_data)
    scene.collection.objects.link(sun)
    sun.rotation_euler = (math.radians(62), 0, math.radians(-28))
    materials()

    global CLOAK
    CLOAK = build_cloak()
    flat_P, flat_N = CLOAK["P"].reshape(-1, 3), CLOAK["N"].reshape(-1, 3)
    add_object("cloak", flat_P, CLOAK["Q"], MAT["cloth"], colors=CLOAK["colors"], normals=flat_N)
    # Кайма ýже на капюшоне: он маленький по сравнению с плащом.
    fade = (smooth(0.015, 0.05, CLOAK["S"]) * (0.4 + 0.6 * smooth(0.2, 0.42, CLOAK["S"])) * (1 - 0.8 * smooth(0.25, 0.8, CLOAK["ef"])) * smooth(0.06, 0.24, CLOAK["edge"])).reshape(-1, 1)
    base = CLOAK["smooth"].reshape(-1, 3)
    for suffix, offset, material in (("_band", 0.03, "band"), ("_ink", 0.037, "ink")):
        obj = add_object("cloak" + suffix, base, CLOAK["Q"][:, ::-1], MAT[material])
        SHELLS.append((obj, base, flat_N, fade, offset))
    core_P, core_Q = nets(lambda x, y, z: ell(x, -y, z, 0, -0.22, 1.25, 0.6, 0.14, 0.3), (-0.7, -0.1, 0.9), (0.7, 0.45, 1.6), 0.03)
    add_object("core", core_P, core_Q, MAT["hooks"])
    log("плащ готов")

    wrap = lambda fn: (lambda x, y, z: fn(x, -y, z))
    face_image, neck_image = paint_face(), paint_neck()
    log("рисунок лица готов")
    MAT["face"] = toon("face", texture=face_image, shadow=(0.9, 0.82, 0.86), threshold=0.03)
    MAT["neck"] = toon("neck", texture=neck_image, shadow=(0.9, 0.82, 0.86), threshold=0.03)
    head_P, head_Q = nets(wrap(head_field), (-0.13, -0.2, 1.66), (0.13, 0.13, 2.0), 0.0016)
    head_P = relax(head_P, head_Q, 0.3)
    _, head_y, _ = to_head(head_P[:, 0], -head_P[:, 1], head_P[:, 2])
    box = FACE_BOX
    head_uv = np.stack([(head_P[:, 0] - box[0]) / (box[1] - box[0]), (head_y - box[2]) / (box[3] - box[2])], -1)
    add_object("head", head_P, head_Q, MAT["face"], uv=head_uv)
    add_hull("head", head_P, head_Q, 0.0008)

    mo_P, mo_Q = nets(wrap(moustache_field), (-0.17, -0.22, 1.7), (0.17, 0.02, 1.84), 0.0016)
    mo_P, mo_Q = keep_large(mo_P, mo_Q, 200)
    add_object("moustache", mo_P, mo_Q, MAT["moustache"])
    mo_hair_P, mo_hair_Q = moustache_hair().arrays()
    add_object("moustache_hair", mo_hair_P, mo_hair_Q, MAT["moustache"])
    lock_P, lock_Q = cheek_locks().arrays()
    add_object("cheek_locks", lock_P, lock_Q, MAT["moustache"])

    torso_P, torso_Q = nets(wrap(torso_field), (-0.46, -0.18, 1.06), (0.46, 0.17, 1.8), 0.0032)
    torso_P = relax(torso_P, torso_Q, 0.4)
    box = NECK_BOX
    torso_uv = np.stack([(torso_P[:, 0] - box[0]) / (box[1] - box[0]), (torso_P[:, 2] - box[2]) / (box[3] - box[2])], -1)
    add_object("torso", torso_P, torso_Q, MAT["neck"], uv=torso_uv)

    MAT["shirt"] = toon("shirt", texture=paint_shirt(), shadow=(0.82, 0.84, 0.94), threshold=0.05)
    box = SHIRT_BOX
    for index, side in enumerate((-1, 1)):
        panel_P, panel_Q = shirt_panel(side)
        panel_uv = np.stack([(panel_P[:, 0] - box[0]) / (box[1] - box[0]), (panel_P[:, 2] - box[2]) / (box[3] - box[2])], -1)
        add_object("shirt_%d" % index, panel_P, panel_Q, MAT["shirt"], uv=panel_uv)
    back_P, back_Q = collar_back()
    add_object("collar_back", back_P, back_Q, toon("collar_back", base=(0.76, 0.78, 0.9), shadow=(0.9, 0.9, 0.95), threshold=0.02))
    log("голова и грудь готовы")

    # Глаза.
    for i, (x, z, hw, hh, tilt) in enumerate(HOOD_EYES):
        row, col = hood_cell(x, z)
        eye_patch("hood_eye_%d" % i, row, col, hw, hh, tilt, seed=i)
    for i, (deg, z, hw, hh, tilt) in enumerate(MANTLE_EYES):
        row, col = cloak_cell(deg, z)
        eye_patch("mantle_eye_%d" % i, row, col, hw, hh, tilt, 0.8, seed=i + 20)

    # Волосы и щупальца.
    hair = hair_strands()
    hair_P, hair_Q = hair.arrays()
    add_object("hair", hair_P, hair_Q, MAT["hair"], colors=hair.colors())
    add_hull("hair", hair_P, hair_Q, 0.004)

    # Руки: разведены, как на кадре снизу. Правая рука зрителя — зеркало левой с другим разворотом.
    open_hand = hand_chains((15, 4, -7, -22), [(10, 16, 12), (8, 14, 10), (10, 16, 12), (12, 18, 14)], 54, (6, 14, 10))
    field = hand_field(open_hand)
    hand_P, hand_Q = nets(field, (-0.1, -0.3, -0.08), (0.17, 0.09, 0.08), 0.0019)
    hand_P = relax(hand_P, hand_Q, 0.4)
    hand_c = hand_colors(hand_P, open_hand)
    SIZE = 1.55
    arms = [
        ((-0.34, 0.0, 1.5), (-0.74, 0.34, 1.16), (-0.98, 0.98, 1.26), (-0.42, 0.24, 0.87), (0.9, 0.05, 0.42), False),
        ((0.34, 0.0, 1.5), (0.74, 0.34, 1.14), (1.02, 0.94, 1.2), (0.46, 0.22, 0.86), (-0.88, 0.1, 0.46), True),
    ]
    for index, (shoulder, elbow, wrist, fingers, thumb, _) in enumerate(arms):
        name = "hand_%d" % index
        fingers_b, thumb_b = Vector((fingers[0], -fingers[1], fingers[2])), Vector((thumb[0], -thumb[1], thumb[2]))
        matrix = basis((wrist[0], -wrist[1], wrist[2]), fingers_b, thumb_b, SIZE)
        if index == 1:
            # Вторая кисть зеркальна: большой палец смотрит к телу.
            matrix = matrix @ Matrix.Scale(-1, 4, (1, 0, 0))
        add_object(name, hand_P, hand_Q, MAT["skin"], colors=hand_c, matrix=matrix)
        add_hull(name, hand_P, hand_Q, 0.0018, matrix=matrix)
        arm = np.array(wrist) - np.array(elbow)
        arm /= np.linalg.norm(arm)
        sleeve = sleeve_field(shoulder, elbow, wrist)
        detail = sleeve_detail(wrist, arm)
        lo = np.min([shoulder, elbow, wrist], axis=0) - 0.24
        hi = np.max([shoulder, elbow, wrist], axis=0) + 0.24
        sl_P, sl_Q = nets(lambda x, y, z: sleeve(x, -y, z), (lo[0], -hi[1], lo[2]), (hi[0], -lo[1], hi[2]), 0.0085, lambda x, y, z, b: detail(x, -y, z, b), 0.06)
        sl_P, sl_Q = keep_large(sl_P, sl_Q, 300)
        add_object("sleeve_%d" % index, sl_P, sl_Q, MAT["hooks"])
        drape_P, drape_Q = arm_drape(shoulder, elbow, wrist, index * 2.3 + 0.7)
        add_object("sleeve_drape_%d" % index, drape_P, drape_Q, MAT["hooks"])
    log("сцена собрана")


def render():
    os.makedirs(OUT, exist_ok=True)
    scene = bpy.context.scene
    for name, (eye, target, lens, sky, arms) in VIEWS.items():
        if WANTED is not None and name not in WANTED:
            continue
        world(sky)
        for obj in bpy.data.objects:
            if obj.name.startswith(("hand_", "sleeve_")):
                obj.hide_render = not arms
        camera(eye, target, lens)
        update_shells((eye[0], -eye[1], eye[2]))
        scene.render.filepath = os.path.join(OUT, name + ".png")
        started = time.time()
        bpy.ops.render.render(write_still=True)
        log("вид", name, round(time.time() - started, 1), "с")


build()
render()
if QUALITY <= 1.0:
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "yhwach.blend"))
log("готово")
