"""Выгрузка Яхве в игру.

Запуск из корня репозитория: blender --background --factory-startup --python tools/blender/export_game.py -- ref/blender/cloak/cloak.npz 50
Берёт те же функции, что tools/blender/yhwach.py, но сетки делает лёгкими: складки и волокна плаща запекаются в текстуру.
Пишет src/main/resources/assets/zangetsu/models/yhwach.bin и текстуры в textures/entity.

Оси игры: x как в Blender, y вверх (z Blender), z к зрителю (-y Blender). Метры модели.

Формат: GZIP, числа старшим байтом вперёд. int частей; на часть: строка имени (UTF Java), байт текстуры (0 нет, 1 лицо, 2 шея, 3 рубашка, 4 плащ),
float minX minY minZ масштаб, int вершин; на вершину: 3 short позиция, 3 byte нормаль, int ARGB, при текстуре ещё 2 short u v; int четырёхугольников, индексы приращениями по int.
Затем int глаз; на глаз 14 float: центр, ось вправо, ось вверх, нормаль, полуширина, полувысота.
"""
import gzip
import os
import struct
import sys

argv = sys.argv[sys.argv.index("--") + 1:]
ROOT = os.path.abspath(".")
NPZ, FRAME = argv[0], argv[1]
TEXTURES = os.path.join(ROOT, "src/main/resources/assets/zangetsu/textures/entity")
MODEL = os.path.join(ROOT, "src/main/resources/assets/zangetsu/models/yhwach.bin")
sys.argv = ["blender", "--", TEXTURES, "none", "1", NPZ, FRAME]
source = open(os.path.join(ROOT, "tools/blender/yhwach.py"), encoding="utf-8").read()
exec(compile(source[:source.index("\nbuild()\nrender()\n")], "yhwach", "exec"))

TEXTURE_IDS = {"face": 1, "neck": 2, "shirt": 3, "cloak": 4}
PARTS = []


def to_game(P):
    return np.stack([P[:, 0], P[:, 2], -P[:, 1]], -1)


def part(name, P, Q, colors=None, alpha=None, normals=None, uv=None, texture=None, game_axes=False):
    """Записать часть. Оси переводятся, если сетка в координатах Blender."""
    N = normals_of(P, Q) if normals is None else normals
    if not game_axes:
        P, N = to_game(P), to_game(N)
    count = len(P)
    rgb = np.ones((count, 3)) if colors is None else np.asarray(colors)
    a = np.zeros(count) if alpha is None else np.asarray(alpha)
    PARTS.append({"name": name, "P": P.astype(np.float32), "N": N.astype(np.float32), "rgb": rgb, "a": a, "Q": np.asarray(Q), "uv": uv, "texture": TEXTURE_IDS.get(texture, 0)})
    log(name, count, "вершин", len(Q), "граней")


def write_all(eyes):
    with gzip.open(MODEL, "wb", compresslevel=9) as out:
        out.write(struct.pack(">i", len(PARTS)))
        for p in PARTS:
            name = p["name"].encode("utf-8")
            out.write(struct.pack(">H", len(name)) + name)
            out.write(struct.pack(">b", p["texture"]))
            low, high = p["P"].min(axis=0), p["P"].max(axis=0)
            scale = float(max((high - low).max(), 1e-6))
            out.write(struct.pack(">ffff", float(low[0]), float(low[1]), float(low[2]), scale))
            count = len(p["P"])
            out.write(struct.pack(">i", count))
            pos = np.clip((p["P"] - low) / scale * 65535.0, 0, 65535).astype(np.int32) - 32768
            nor = np.clip(p["N"] * 127.0, -127, 127).astype(np.int8)
            rgb = (np.clip(p["rgb"], 0, 1) * 255.0 + 0.5).astype(np.uint32)
            alpha = (np.clip(p["a"], 0, 1) * 255.0 + 0.5).astype(np.uint32)
            argb = (alpha << 24) | (rgb[:, 0] << 16) | (rgb[:, 1] << 8) | rgb[:, 2]
            fields = [("pos", ">i2", 3), ("nor", "i1", 3), ("col", ">u4")]
            if p["texture"]:
                fields.append(("uv", ">u2", 2))
            record = np.zeros(count, dtype=fields)
            record["pos"], record["nor"], record["col"] = pos, nor, argb
            if p["texture"]:
                record["uv"] = (np.clip(p["uv"], 0, 1) * 65535.0 + 0.5).astype(np.uint16)
            out.write(record.tobytes())
            flat = p["Q"].reshape(-1).astype(np.int64)
            out.write(struct.pack(">i", len(p["Q"])))
            out.write(np.diff(flat, prepend=0).astype(">i4").tobytes())
        out.write(struct.pack(">i", len(eyes)))
        for e in eyes:
            frame = [to_game(np.array([e[k]]))[0] for k in ("centre", "right", "up", "normal")]
            out.write(struct.pack(">14f", *[float(v) for f in frame for v in f], float(e["half_w"]), float(e["half_h"])))
    log("файл", os.path.getsize(MODEL) // 1024, "КБ")


def save_image(name, rgba):
    image = bpy.data.images.new(name, rgba.shape[1], rgba.shape[0], alpha=True)
    image.pixels.foreach_set(rgba.astype(np.float32).ravel())
    image.update()
    image.filepath_raw = os.path.join(TEXTURES, name + ".png")
    image.file_format = "PNG"
    image.save()
    log(name, os.path.getsize(image.filepath_raw) // 1024, "КБ")


def box_uv(u, v, box):
    """Текстура рисовалась снизу вверх, а игра читает её сверху вниз."""
    return np.stack([(u - box[0]) / (box[1] - box[0]), 1 - (v - box[2]) / (box[3] - box[2])], -1)


def merged(chunks):
    P, Q, C, A, base = [], [], [], [], 0
    for p, q, c, a in chunks:
        P.append(p)
        Q.append(q + base)
        C.append(c)
        A.append(a)
        base += len(p)
    return np.concatenate(P), np.concatenate(Q), np.concatenate(C), np.concatenate(A)


def covered_by_cloak(points, full, margin=0.08, cell=0.015):
    """Какие точки закрыты плащом спереди, даже если смотреть со сдвигом на margin: их никто никогда не видит, пока плащ цел."""
    P = full["P"].reshape(*full["V"].shape, 3)[~full["hole"]]
    size = (int(2.4 / cell), int(1.8 / cell))
    index = lambda x, z: (((x + 1.2) / cell).astype(int), ((z - 0.6) / cell).astype(int))
    ix, iz = index(P[:, 0], P[:, 2])
    fits = (ix >= 0) & (ix < size[0]) & (iz >= 0) & (iz < size[1])
    front = np.full(size, -np.inf)
    np.maximum.at(front, (ix[fits], iz[fits]), -P[fits, 1])
    closed = front.copy()
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            closed = np.maximum(closed, np.roll(front, (dx, dz), axis=(0, 1)))
    reach = int(margin / cell)
    solid = closed.copy()
    for dx in range(-reach, reach + 1):
        for dz in range(-reach, reach + 1):
            if dx * dx + dz * dz <= reach * reach:
                solid = np.minimum(solid, np.roll(closed, (dx, dz), axis=(0, 1)))
    px, pz = index(points[:, 0], points[:, 2])
    px, pz = np.clip(px, 0, size[0] - 1), np.clip(pz, 0, size[1] - 1)
    return -points[:, 1] < solid[px, pz] - 0.005


def build_game():
    global QUALITY, CLOAK, CLOAK_ROWS, CLOAK_COLS, CLOAK_HOLES
    os.makedirs(TEXTURES, exist_ok=True)
    wrap = lambda fn: (lambda x, y, z: fn(x, -y, z))

    # Плащ: плотная сетка даёт глаза и пряди, а её цвет становится текстурой.
    QUALITY, CLOAK_HOLES = 1.0, True
    CLOAK = build_cloak()
    full = CLOAK
    rgba = np.ones((*full["V"].shape, 4))
    rgba[..., :3] = full["colors"].reshape(*full["V"].shape, 3)
    rgba[..., 3] = (~full["hole"]).astype(float)
    save_image("yhwach_cloak", rgba)

    # Лёгкая сетка плаща для игры: дыры дальше делает прозрачность текстуры.
    CLOAK_ROWS, CLOAK_COLS, CLOAK_HOLES = 150, 440, False
    light = build_cloak()
    CLOAK_ROWS, CLOAK_COLS, CLOAK_HOLES = 620, 1500, True
    S = light["S"].reshape(-1)
    fade = (smooth(0.015, 0.05, light["S"]) * (0.4 + 0.6 * smooth(0.2, 0.42, light["S"])) * (1 - 0.8 * smooth(0.25, 0.8, light["ef"])) * smooth(0.06, 0.24, light["edge"])).reshape(-1)
    cloth_uv = np.stack([(light["U"].reshape(-1) + 1) / 2, 1 - light["V"].reshape(-1)], -1)
    part("cloak", light["P"].reshape(-1, 3), light["Q"], colors=np.stack([fade, fade, fade], -1), alpha=smooth(0.25, 1.0, S), normals=light["N"].reshape(-1, 3), uv=cloth_uv, texture="cloak")

    # Лицо, шея, рубашка.
    paint_face()
    paint_neck()
    paint_shirt()
    QUALITY = 2.2
    head_P, head_Q = nets(wrap(head_field), (-0.13, -0.2, 1.66), (0.13, 0.13, 2.0), 0.0016)
    head_P = relax(head_P, head_Q, 0.3)
    _, head_y, _ = to_head(head_P[:, 0], -head_P[:, 1], head_P[:, 2])
    part("head", head_P, head_Q, uv=box_uv(head_P[:, 0], head_y, FACE_BOX), texture="face")
    QUALITY = 3.5
    torso_P, torso_Q = nets(wrap(torso_field), (-0.46, -0.18, 1.06), (0.46, 0.17, 1.8), 0.0032)
    torso_P = relax(torso_P, torso_Q, 0.4)
    limit = np.maximum(shirt_inner(torso_P[:, 2], 1.0), shirt_inner(torso_P[:, 2], -1.0)) + 0.03
    limit = np.where(torso_P[:, 2] > 1.6, 0.15, limit)
    seen = np.abs(torso_P[:, 0]) < limit
    torso_Q = torso_Q[seen[torso_Q].any(axis=1)]
    torso_Q = torso_Q[~covered_by_cloak(torso_P, full)[torso_Q].all(axis=1)]
    part("neck", torso_P, torso_Q, uv=box_uv(torso_P[:, 0], torso_P[:, 2], NECK_BOX), texture="neck")
    QUALITY = 2.0
    panels = []
    for side in (-1, 1):
        panel_P, panel_Q = shirt_panel(side)
        panels.append((panel_P, panel_Q, np.ones((len(panel_P), 3)), np.zeros(len(panel_P))))
    shirt_P, shirt_Q, _, _ = merged(panels)
    before = len(shirt_Q)
    shirt_Q = shirt_Q[~covered_by_cloak(shirt_P, full)[shirt_Q].all(axis=1)]
    log("рубашка: за плащом убрано", before - len(shirt_Q), "из", before, "граней")
    part("shirt", shirt_P, shirt_Q, uv=box_uv(shirt_P[:, 0], shirt_P[:, 2], SHIRT_BOX), texture="shirt")
    back_P, back_Q = collar_back()
    before = len(back_Q)
    back_Q = back_Q[~covered_by_cloak(back_P, full)[back_Q].all(axis=1)]
    log("воротник: за плащом убрано", before - len(back_Q), "из", before, "граней")
    part("collar", back_P, back_Q, colors=np.tile(np.array((0.76, 0.78, 0.9)), (len(back_P), 1)))

    # Усы и клинья на скулах.
    black = np.array((0.03, 0.022, 0.026))
    QUALITY = 2.0
    mo_P, mo_Q = nets(wrap(moustache_field), (-0.17, -0.22, 1.7), (0.17, 0.02, 1.84), 0.0016)
    mo_P, mo_Q = keep_large(mo_P, mo_Q, 100)
    hair_a_P, hair_a_Q = moustache_hair(260, 3, 4).arrays()
    lock_P, lock_Q = cheek_locks().arrays()
    all_P, all_Q, all_C, all_A = merged([(p, q, np.tile(black, (len(p), 1)), np.zeros(len(p))) for p, q in ((mo_P, mo_Q), (hair_a_P, hair_a_Q), (lock_P, lock_Q))])
    part("moustache", all_P, all_Q, colors=all_C)

    # Волосы.
    QUALITY = 1.0
    hair = hair_strands()
    hair_P, hair_Q = hair.arrays()
    part("hair", hair_P, hair_Q, colors=hair.colors(), alpha=np.concatenate(hair.T) ** 1.3)

    # Глаза: каждый отдельно, потому что моргает и смотрит сам. Зрачок двигается по белку.
    eyes = []
    for x, z, hw, hh, tilt in HOOD_EYES:
        eyes.append(eye_geometry(*hood_cell(x, z), hw, hh, tilt, 1.0, len(eyes), 0.5, 40, iris=False))
    for deg, z, hw, hh, tilt in MANTLE_EYES:
        eyes.append(eye_geometry(*cloak_cell(deg, z), hw, hh, tilt, 0.8, len(eyes) + 20, 0.5, 40, iris=False))
    for i, e in enumerate(eyes):
        part("eye_%d" % i, e["P"], e["Q"], colors=e["colors"])
        rings, sides = 6, 18
        R, A = np.meshgrid(np.linspace(0, 1, rings), np.linspace(0, 2 * math.pi, sides, endpoint=False), indexing="ij")
        px = R * 0.12 * e["half_w"] * np.cos(A)
        py = R * 0.12 * e["half_h"] * np.sin(A)
        tx, ty = px * math.cos(e["tilt"]) - py * math.sin(e["tilt"]), px * math.sin(e["tilt"]) + py * math.cos(e["tilt"])
        P = e["centre"] + tx[..., None] * e["right"] + ty[..., None] * e["up"] + e["normal"] * (e["top"] + 0.0012) + e["normal"][None, None, :] * (0.001 * (1 - R ** 2))[..., None]
        index = np.arange(R.size).reshape(R.shape)
        nxt = np.roll(index, -1, axis=1)
        pupil_Q = np.stack([index[:-1], index[1:], nxt[1:], nxt[:-1]], -1).reshape(-1, 4)
        flat = P.reshape(-1, 3)
        if np.sum(np.cross(flat[pupil_Q[:, 2]] - flat[pupil_Q[:, 0]], flat[pupil_Q[:, 3]] - flat[pupil_Q[:, 1]]) @ e["normal"]) < 0:
            pupil_Q = pupil_Q[:, ::-1]
        part("pupil_%d" % i, flat, pupil_Q, colors=np.tile(np.array((0.34, 0.07, 0.1)), (len(flat), 1)))

    # Рука целиком: от плеча вдоль -y, с лёгким сгибом, манжетом и свисающей вдоль -z тканью. Поворачивается в плече.
    sleeve_dark = np.array((0.07, 0.03, 0.04))
    wrist = (0.0, -1.1, 0.0)
    elbow = (0.0, -0.5, 0.0)
    arm_axis = np.array(wrist) - np.array(elbow)
    arm_axis /= np.linalg.norm(arm_axis)
    QUALITY = 2.4
    tube_P, tube_Q = nets(sleeve_field((0.0, 0.0, 0.0), elbow, wrist), (-0.26, -1.3, -0.26), (0.26, 0.24, 0.3), 0.0085, sleeve_detail(wrist, arm_axis), 0.06)
    tube_P, tube_Q = keep_large(tube_P, tube_Q, 300)
    rows, cols = 16, 48
    V, U = np.meshgrid(np.linspace(0, 1, rows), np.linspace(0.05, 1, cols), indexing="ij")
    rip = 0.28 * spike(U * 6.0 + 0.7) + 0.14 * spike(U * 14.0 + 0.5) + 0.07 * spike(U * 31.0)
    drop = V * 0.6 * (1.0 - 0.62 * U) * (1 - rip)
    drape_P = np.stack([0.07 * drop * np.sin(U * 13.0 + V * 3.0) + 0.03 * drop * np.sin(U * 29.0), -1.1 * U + 0.09 * drop * np.sin(U * 7.0), -0.05 - drop], -1).reshape(-1, 3)
    index = np.arange(rows * cols).reshape(rows, cols)
    drape_Q = np.stack([index[:-1, :-1], index[:-1, 1:], index[1:, 1:], index[1:, :-1]], -1).reshape(-1, 4)
    arm_P, arm_Q, arm_C, arm_A = merged([(tube_P, tube_Q, np.tile(sleeve_dark, (len(tube_P), 1)), np.zeros(len(tube_P))), (drape_P, drape_Q, np.tile(sleeve_dark, (len(drape_P), 1)), V.reshape(-1) ** 1.2)])
    part("arm", arm_P, arm_Q, colors=arm_C, alpha=arm_A, game_axes=True)

    # Кисть: ладонь и фаланги отдельно, запястье в начале координат, пальцы вдоль -y, тыл к +z, большой палец к +x.
    QUALITY = 2.2
    palm_P, palm_Q = nets(hand_field([]), (-0.1, -0.16, -0.05), (0.1, 0.09, 0.05), 0.0019)
    palm_P = relax(palm_P, palm_Q, 0.4)
    glove, claw_tip = np.array((0.125, 0.05, 0.068)), np.array((0.34, 0.24, 0.27))
    palm_C = np.tile(glove, (len(palm_P), 1))
    part("palm_r", palm_P, palm_Q, colors=palm_C, game_axes=True)
    part("palm_l", palm_P * np.array((-1.0, 1.0, 1.0)), palm_Q[:, ::-1], colors=palm_C, game_axes=True)
    sizes = [(LENGTHS[i], (RADIUS[i], RADIUS[i] * 0.92, RADIUS[i] * 0.84, RADIUS[i] * 0.74)) for i in range(4)] + [(THUMB_LEN, (0.0165, 0.015, 0.0135, 0.0115))]
    for f, (lengths, radii) in enumerate(sizes):
        for j in range(3):
            length, r0, r1 = lengths[j], radii[j], radii[j + 1]
            pad = r0 * 1.3 + 0.004
            if j < 2:
                QUALITY = 1.8
                field = lambda x, y, z, length=length, r0=r0, r1=r1: smin(cone(x, y, z, (0.0, 0.0, 0.0), (0.0, -length, 0.0), r0, r1), ell(x, y, z, 0.0, 0.0, 0.0, r0 * 1.14, r0 * 1.14, r0 * 1.14), 0.004)
                P, Q = nets(field, (-pad, -length - pad, -pad), (pad, pad, pad), 0.0016)
                colors = np.tile(glove, (len(P), 1))
            else:
                # Кончик перчатки: длинный, сходится в острие и чуть загнут к ладони.
                QUALITY = 1.0
                claw = length * 2.1
                field = lambda x, y, z, claw=claw, r0=r0: smin(cone(x, y, z + 0.014 * (y / claw) ** 2, (0.0, 0.0, 0.0), (0.0, -claw, 0.0), r0, 0.0009), ell(x, y, z, 0.0, 0.0, 0.0, r0 * 1.14, r0 * 1.14, r0 * 1.14), 0.004)
                P, Q = nets(field, (-pad, -claw - pad, -pad - 0.016), (pad, pad, pad), 0.0016)
                k = (np.clip(-P[:, 1] / claw, 0.0, 1.0) ** 2)[:, None]
                colors = glove + (claw_tip - glove) * k
            part("f%d_%d" % (f, j), P, Q, colors=colors, game_axes=True)

    write_all(eyes)
    log("всего граней", sum(len(p["Q"]) for p in PARTS), "частей", len(PARTS))


build_game()
