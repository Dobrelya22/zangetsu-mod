"""Капюшон и плащ Яхве как один кусок настоящей ткани: симуляция Blender.

Запуск: blender --background --factory-startup --python tools/blender/cloak_sim.py -- <папка> <строк> <столбцов> <кадров> [рендеры через запятую]
Круг ткани с центром на макушке накрывает голову и тело. Окно лица и вырез на груди прорезаны, их края закреплены.
Ткань шире того, что она покрывает, поэтому сама ложится складками, как скатерть. Ветер относит подол. Результат: <папка>/cloak.npz.

Координаты Blender: x вправо от зрителя, -y к зрителю, z вверх. Угол theta: 0 спереди, плюс вправо от зрителя.
"""
import math
import os
import sys
import time

import bpy
import numpy as np

ARGV = sys.argv[sys.argv.index("--") + 1:]
OUT = os.path.abspath(ARGV[0])
NS, NU, FRAMES = int(ARGV[1]), int(ARGV[2]), int(ARGV[3])
SHOW = [int(v) for v in ARGV[4].split(",")] if len(ARGV) > 4 else []
START = time.time()

# Настройки ткани и ветра: их подбираем по результату.
TENSION, COMPRESSION, SHEAR, BENDING = 20.0, 12.0, 8.0, 6.0
MASS, AIR, QUALITY = 0.25, 1.5, 7
WIND, WIND_NOISE, TURBULENCE, TURBULENCE_SIZE = 90.0, 1.0, 26.0, 1.3
COLLIDER_END = 0.66
GRAVITY, RADIAL, COLLISION_QUALITY, SOFT_PIN = 1.0, 0.0, 3, 0.0
FRICTION = 5.0
RIBS, RIB_SEED, RIB_DEPTH, RIB_COLLAR = 13, 5, 0.22, 0.1
EXCESS = (0.04, 0.12, 0.18, 0.2)
# Варианты, которые сравниваем.
PRESETS = {
    "cloak": dict(GRAVITY=0.3, WIND=200.0, TURBULENCE=50.0, RADIAL=30.0, COLLIDER_END=0.36, QUALITY=7, COLLISION_QUALITY=3, FRICTION=3.0, COMPRESSION=4.0, BENDING=3.0, AIR=3.0, RIB_DEPTH=0.16, RIB_COLLAR=0.1, EXCESS=(0.04, 0.14, 0.2, 0.22)),
}
if len(ARGV) > 5:
    globals().update(PRESETS[ARGV[5]])

# Ход ткани от макушки вниз: s, высота, полуось в стороны, вперёд, назад.
TABLE = np.array([
    (0.00, 2.15, 0.00, 0.00, 0.00),
    (0.02, 2.145, 0.07, 0.06, 0.07),
    (0.05, 2.11, 0.15, 0.125, 0.15),
    (0.08, 2.05, 0.21, 0.17, 0.205),
    (0.11, 1.98, 0.24, 0.19, 0.235),
    (0.14, 1.91, 0.245, 0.19, 0.24),
    (0.17, 1.84, 0.25, 0.18, 0.25),
    (0.20, 1.77, 0.29, 0.17, 0.28),
    (0.24, 1.69, 0.42, 0.18, 0.38),
    (0.30, 1.56, 0.66, 0.205, 0.6),
    (0.38, 1.38, 0.96, 0.23, 0.88),
    (0.48, 1.14, 1.3, 0.27, 1.25),
    (0.60, 0.84, 1.6, 0.36, 1.6),
    (0.74, 0.52, 1.95, 0.5, 1.95),
    (0.88, 0.22, 2.25, 0.72, 2.25),
    (1.00, -0.02, 2.5, 0.95, 2.5),
])
# Полуширина прорези: окно лица и вырез на груди. Высота, половина ширины.
HOLE = np.array([(1.12, 0.0), (1.2, 0.012), (1.3, 0.036), (1.4, 0.068), (1.5, 0.105), (1.6, 0.15), (1.7, 0.2), (1.74, 0.2), (1.77, 0.14), (1.8, 0.095), (1.84, 0.045), (1.865, 0.0)])


def log(*items):
    print("YH %6.1f" % (time.time() - START), *items, flush=True)


def smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def column(index, s):
    fine = np.linspace(0, 1, 800)
    # Сглаживаем таблицу, иначе на её узлах появляются изломы.
    values = np.interp(fine, TABLE[:, 0], TABLE[:, index])
    kernel = np.hanning(41)
    kernel /= kernel.sum()
    padded = np.concatenate([np.full(20, values[0]), values, np.full(20, values[-1])])
    return np.interp(s, fine, np.convolve(padded, kernel, mode="valid"))


def radius(s, theta):
    sin, cos = np.sin(theta), np.cos(theta)
    side, front, back = column(2, s), column(3, s), column(4, s)
    return 1 / np.sqrt((sin / np.maximum(side, 1e-4)) ** 2 + (cos / np.maximum(np.where(cos > 0, front, back), 1e-4)) ** 2)


def surface(s, theta, scale=1.0, drop=0.0):
    r = radius(s, theta) * scale
    return np.stack([r * np.sin(theta), -r * np.cos(theta), column(1, s) - drop], -1)


def hole_half(z):
    return np.interp(z, HOLE[:, 0], HOLE[:, 1], left=0.0, right=0.0)


def add_mesh(name, verts, faces):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts.tolist(), [], faces.tolist())
    mesh.update()
    mesh.shade_smooth()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    return obj


def grid_faces(ns, nu, wrap=True):
    index = np.arange(ns * nu).reshape(ns, nu)
    right = np.roll(index, -1, axis=1)
    faces = np.stack([index[:-1], right[:-1], right[1:], index[1:]], -1)
    return faces if wrap else faces[:, :-1]


def rib_field(theta):
    """1 на ребре, 0 в провале между рёбрами. Рёбра разной ширины и неравномерно расставлены, чтобы складки не выглядели рисунком."""
    rng = np.random.RandomState(RIB_SEED)
    angles = np.linspace(-math.pi, math.pi, RIBS, endpoint=False) + rng.uniform(-0.09, 0.09, RIBS)
    widths = rng.uniform(0.07, 0.15, RIBS)
    field = np.zeros_like(theta)
    for a, w in zip(angles, widths):
        delta = np.arctan2(np.sin(theta - a), np.cos(theta - a))
        field = np.maximum(field, np.exp(-(delta / w) ** 2))
    return field


def build_collider():
    """Тело под тканью: голова, плечи и склон. Ниже граница: дальше ткань висит и летит свободно."""
    rings, cols = 70, 300
    s = np.linspace(0.004, COLLIDER_END, rings)
    theta = np.linspace(-math.pi, math.pi, cols, endpoint=False)
    S, T = np.meshgrid(s, theta, indexing="ij")
    depth = RIB_DEPTH * smooth(0.2, 0.5, S) + RIB_COLLAR * np.exp(-((S - 0.24) / 0.05) ** 2)
    P = surface(S, T, scale=0.93 * (1 - depth * (1 - rib_field(T))))
    faces = grid_faces(rings, cols).reshape(-1, 4)[:, ::-1]
    bottom = rings * cols
    verts = np.concatenate([P.reshape(-1, 3), [[0, 0, column(1, COLLIDER_END) - 0.3]]])
    cap = np.stack([np.roll(np.arange(cols), -1) + (rings - 1) * cols, np.arange(cols) + (rings - 1) * cols, np.full(cols, bottom)], -1)
    mesh = bpy.data.meshes.new("collider")
    mesh.from_pydata(verts.tolist(), [], faces.tolist() + cap.tolist())
    mesh.update()
    mesh.shade_smooth()
    obj = bpy.data.objects.new("collider", mesh)
    bpy.context.scene.collection.objects.link(obj)
    modifier = obj.modifiers.new("Collision", "COLLISION")
    modifier.settings.thickness_outer = 0.018
    modifier.settings.cloth_friction = FRICTION
    modifier.settings.damping = 0.6
    obj.hide_render = True
    return obj


def build_cloth():
    s = np.linspace(0.008, 1.0, NS)
    theta = np.linspace(-math.pi, math.pi, NU, endpoint=False)
    S, T = np.meshgrid(s, theta, indexing="ij")
    # Лёгкая неровность: с неё начинаются складки, и они выходят разной ширины.
    rng = np.random.RandomState(11)
    wave = sum(a * np.sin(T * k + p) for a, k, p in zip(rng.uniform(0.004, 0.012, 9), rng.randint(5, 24, 9), rng.uniform(0, 6.3, 9)))
    start = surface(S, T, scale=1.0 + wave * smooth(0.16, 0.5, S))
    # В покое ткань шире того места, где лежит. Запас растёт от головы к подолу.
    excess = 1.0 + EXCESS[0] * smooth(0.04, 0.16, S) + EXCESS[1] * smooth(0.18, 0.3, S) + EXCESS[2] * smooth(0.3, 0.55, S) + EXCESS[3] * smooth(0.55, 1.0, S)
    rest = surface(S, T, scale=excess)
    z = column(1, S)
    half = hole_half(z)
    r = radius(S, T)
    cut = np.arctan2(half, np.maximum(column(3, S), 1e-3))
    inside = np.abs(T) < cut
    band = (np.abs(T) - cut) * r
    pinned = (S < 0.03) | inside | ((band < 0.028) & (z > 1.22) & (z < 1.9))
    faces = grid_faces(NS, NU)[:, :, ::-1]
    keep = ~(inside[:-1] | inside[1:] | np.roll(inside[:-1], -1, axis=1) | np.roll(inside[1:], -1, axis=1))
    obj = add_mesh("cloak", start.reshape(-1, 3), faces[keep])
    group = obj.vertex_groups.new(name="pin")
    group.add(np.nonzero(pinned.ravel())[0].tolist(), 1.0, "REPLACE")
    if SOFT_PIN > 0:
        # Мягкая привязка к форме горы: держит склон, но даёт ткани складываться. Подол свободен.
        weight = (SOFT_PIN * smooth(0.16, 0.3, S) * (1 - smooth(0.5, 0.82, S))).ravel()
        for i in np.nonzero((weight > 0.002) & ~pinned.ravel())[0].tolist():
            group.add([i], float(weight[i]), "REPLACE")
    obj.shape_key_add(name="Basis", from_mix=False)
    key = obj.shape_key_add(name="Rest", from_mix=False)
    key.data.foreach_set("co", rest.reshape(-1).astype(np.float32))
    modifier = obj.modifiers.new("Cloth", "CLOTH")
    st = modifier.settings
    st.quality = QUALITY
    st.mass = MASS
    st.air_damping = AIR
    st.tension_stiffness, st.compression_stiffness, st.shear_stiffness, st.bending_stiffness = TENSION, COMPRESSION, SHEAR, BENDING
    st.tension_damping, st.compression_damping, st.shear_damping, st.bending_damping = 5.0, 5.0, 5.0, 0.5
    st.vertex_group_mass = "pin"
    st.pin_stiffness = 1.0
    st.rest_shape_key = key
    st.effector_weights.gravity = GRAVITY
    co = modifier.collision_settings
    co.use_collision = True
    co.distance_min = 0.012
    co.collision_quality = COLLISION_QUALITY
    co.use_self_collision = False
    modifier.point_cache.frame_start = 1
    modifier.point_cache.frame_end = FRAMES
    log("ткань", NS * NU, "вершин", int(keep.sum()), "граней, закреплено", int(pinned.sum()))
    return obj, S, T


def add_wind():
    bpy.ops.object.effector_add(type="WIND", location=(-6, 0, 1.2), rotation=(0, math.radians(84), 0))
    wind = bpy.context.object
    wind.field.strength = WIND
    wind.field.noise = WIND_NOISE
    wind.field.flow = 0.0
    bpy.ops.object.effector_add(type="TURBULENCE", location=(0, 0, 1))
    gust = bpy.context.object
    gust.field.strength = TURBULENCE
    gust.field.size = TURBULENCE_SIZE
    gust.field.flow = 0.2
    if RADIAL > 0:
        bpy.ops.object.effector_add(type="FORCE", location=(0, 0, -0.4))
        push = bpy.context.object
        push.field.strength = RADIAL
        push.field.falloff_power = 0.0


def clay():
    m = bpy.data.materials.new("clay")
    m.use_nodes = True
    node = m.node_tree.nodes["Principled BSDF"]
    node.inputs["Base Color"].default_value = (0.2, 0.17, 0.18, 1)
    node.inputs["Roughness"].default_value = 0.75
    return m


def stage():
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE"
    scene.eevee.taa_render_samples = 16
    scene.render.resolution_x, scene.render.resolution_y = 960, 540
    scene.view_settings.view_transform = "Standard"
    world = bpy.data.worlds.new("w")
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (0.75, 0.8, 0.86, 1)
    world.node_tree.nodes["Background"].inputs[1].default_value = 0.6
    scene.world = world
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    sun.data.energy = 3.0
    sun.rotation_euler = (math.radians(55), 0, math.radians(-35))
    scene.collection.objects.link(sun)
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    scene.collection.objects.link(cam)
    scene.camera = cam
    return scene, cam


def shoot(scene, cam, tag):
    from mathutils import Vector
    for name, eye, target, lens in (("front", (0, -9.5, 1.1), (0.2, 0, 0.95), 50), ("quarter", (-6.5, -7.0, 1.7), (0.1, 0, 0.95), 50), ("side", (-9.5, 0, 1.4), (0, 0, 0.95), 50), ("hood", (0.25, -1.9, 1.75), (0, 0, 1.72), 50)):
        cam.location = eye
        cam.rotation_euler = (Vector(target) - Vector(eye)).to_track_quat("-Z", "Y").to_euler()
        cam.data.lens = lens
        scene.render.filepath = os.path.join(OUT, "%s_%s.png" % (tag, name))
        bpy.ops.render.render(write_still=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene, cam = stage()
    scene.frame_start, scene.frame_end = 1, FRAMES
    build_collider()
    cloth, S, T = build_cloth()
    cloth.data.materials.append(clay())
    add_wind()
    saved = {}
    count = NS * NU
    for frame in range(1, FRAMES + 1):
        scene.frame_set(frame)
        if frame in SHOW or frame == FRAMES or frame % 30 == 0:
            evaluated = cloth.evaluated_get(bpy.context.evaluated_depsgraph_get()).to_mesh()
            co = np.empty(count * 3)
            evaluated.vertices.foreach_get("co", co)
            cloth.evaluated_get(bpy.context.evaluated_depsgraph_get()).to_mesh_clear()
            saved["f%d" % frame] = co.reshape(NS, NU, 3).astype(np.float32)
            spread = np.linalg.norm(saved["f%d" % frame][:, :, :2], axis=2).max()
            log("кадр", frame, "наибольший радиус", round(float(spread), 2), "ниже всего", round(float(saved["f%d" % frame][:, :, 2].min()), 2))
        if frame in SHOW:
            shoot(scene, cam, "f%03d" % frame)
    np.savez_compressed(os.path.join(OUT, "cloak.npz"), S=S.astype(np.float32), T=T.astype(np.float32), table=TABLE, hole=HOLE, **saved)
    log("готово")


main()
