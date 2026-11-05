"""
Монтаж клипа кадр за кадром: дубли 240 к/с (см. src/trailer Clip.java) -> готовый ролик 1080p60.

Каждый выходной кадр собирается из нескольких кадров дубля (настоящее размытие движения), дальше зум и тряска на
ударах, расхождение цветов, свечение, цвет, зерно, плашки. Время дубля задаётся картой: как угодно быстро и медленно.
Планы считаются независимо, поэтому параллельно: отдельный процесс на план, потом склейка и финальное сжатие.
"""
import math
import os
import subprocess
import sys

_libs = os.environ.get("CLIP_PYLIBS")
if _libs:
    sys.path.insert(0, _libs)

import cv2  # noqa: E402
import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

W, H, FPS, SRC_FPS = 1920, 1080, 60, 240
BAR = 138
FFMPEG = os.environ.get("CLIP_FFMPEG", "ffmpeg")
FONTS = "C:/Windows/Fonts/"


# ---------------------------------------------------------------- время

def frame_of(t):
    return int(round(t * FPS))


class Src:
    """Откуда брать кадры: дубль и карта времени (секунда клипа -> секунда дубля)."""

    def __init__(self, take, points):
        self.take = take
        self.tx = np.array([p[0] for p in points], dtype=np.float64)
        self.ty = np.array([p[1] for p in points], dtype=np.float64)

    def at(self, t):
        if t <= self.tx[0]:
            return float(self.ty[0] + (t - self.tx[0]) * self._rate(0))
        if t >= self.tx[-1]:
            return float(self.ty[-1] + (t - self.tx[-1]) * self._rate(len(self.tx) - 2))
        return float(np.interp(t, self.tx, self.ty))

    def _rate(self, i):
        if len(self.tx) < 2:
            return 1.0
        return (self.ty[i + 1] - self.ty[i]) / max(1e-6, self.tx[i + 1] - self.tx[i])


def run(take, t0, s0, speed=1.0):
    """Ровная скорость: в секунду клипа t0 дубль на секунде s0."""
    return Src(take, [(t0, s0), (t0 + 1.0, s0 + speed)])


class Reader:
    """Последовательно читает кадры дубля; что уже не нужно, выбрасывает."""

    def __init__(self, folder, take, first):
        self.path = os.path.join(folder, take + ".mkv")
        first = max(0, first)
        self.pos = first
        self.first = first
        self.cache = {}
        self.last = None
        cmd = [FFMPEG, "-hide_banner", "-loglevel", "error", "-threads", "3", "-ss", "%.6f" % (first / SRC_FPS), "-i", self.path,
               "-an", "-sn", "-f", "rawvideo", "-pix_fmt", "bgr24", "-"]
        self.proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, bufsize=1 << 24)
        self.size = W * H * 3

    def get(self, index):
        index = max(index, self.first)
        if index in self.cache:
            return self.cache[index]
        while self.pos <= index:
            raw = self.proc.stdout.read(self.size)
            if len(raw) < self.size:
                self.pos = 1 << 60
                return self.last
            frame = np.frombuffer(raw, dtype=np.uint8).reshape(H, W, 3)
            self.cache[self.pos] = frame
            self.last = frame
            self.pos += 1
        return self.cache.get(index, self.last)

    def forget_before(self, index):
        for k in [k for k in self.cache if k < index]:
            del self.cache[k]

    def close(self):
        try:
            self.proc.stdout.close()
            self.proc.kill()
            self.proc.wait()
        except OSError:
            pass


TO_LINEAR = np.power(np.arange(256, dtype=np.float32) / 255.0, 2.2)


def exposure(reader, src, t, shutter):
    """Кадр как настоящая выдержка: среднее по времени в линейном свете, с дробными краями; вывод в [0,1] с гаммой."""
    a = src.at(t) * SRC_FPS
    b = src.at(t + shutter / FPS) * SRC_FPS
    if b < a:
        a, b = b, a
    if b - a < 1.0:
        # Замедление: кадров дубля не хватает, плавно переходим между двумя соседними.
        i = int(math.floor(a))
        k = a - i
        reader.forget_before(i)
        f0 = reader.get(i)
        if k < 0.04:
            return f0.astype(np.float32) / 255.0
        f1 = reader.get(i + 1)
        return cv2.pow(cv2.LUT(f0, TO_LINEAR) * (1.0 - k) + cv2.LUT(f1, TO_LINEAR) * k, 1.0 / 2.2)
    i0, i1 = int(math.floor(a)), int(math.ceil(b))
    step = max(1, (i1 - i0) // 14)
    reader.forget_before(i0)
    total = None
    weight = 0.0
    for i in range(i0, i1, step):
        w = float(min(i + step, b) - max(i, a))
        if w <= 0:
            continue
        lin = cv2.LUT(reader.get(i), TO_LINEAR)
        total = lin * w if total is None else total + lin * w
        weight += w
    return cv2.pow(total / max(1e-6, weight), 1.0 / 2.2)


# ---------------------------------------------------------------- цвет

class Look:
    def __init__(self, gain=1.0, gamma=1.0, contrast=0.3, sat=1.0, red_sat=None, shadow=(0, 0, 0), high=(0, 0, 0), ground=0.0, vignette=0.35, bloom=0.25, thresh=0.72, streak=0.0, embers=0.0, ember_rgb=(1.0, 0.42, 0.12)):
        self.streak = streak
        self.embers = embers
        self.ember_rgb = ember_rgb
        self.gain = gain
        self.gamma = gamma
        self.contrast = contrast
        self.sat = sat
        self.red_sat = sat if red_sat is None else red_sat
        # Оттенки заданы как (r, g, b); внутри кадр в порядке bgr.
        self.shadow = np.array(shadow[::-1], dtype=np.float32)
        self.high = np.array(high[::-1], dtype=np.float32)
        self.ground = ground
        self.vignette = vignette
        self.bloom = bloom
        self.thresh = thresh


def _vignette_map():
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    nx, ny = (x - W / 2) / (W / 2), (y - H / 2) / (H / 2)
    r = np.sqrt(nx * nx * 0.8 + ny * ny * 0.6)
    v = np.clip((r - 0.45) / 0.85, 0.0, 1.0)
    return v * v * (3 - 2 * v), np.clip((ny - 0.1) / 0.9, 0.0, 1.0)


VIGNETTE, GROUND = _vignette_map()


def grade(x, g, extra_gain=1.0):
    b, gr, r = x[..., 0], x[..., 1], x[..., 2]
    luma = 0.0722 * b + 0.7152 * gr + 0.2126 * r
    mask = np.clip((r - np.maximum(gr, b)) * 3.5, 0.0, 1.0)
    sat = g.sat + (g.red_sat - g.sat) * mask
    x = luma[..., None] + (x - luma[..., None]) * sat[..., None]
    x = np.clip(x * g.gain * extra_gain, 0.0, 4.0)
    if g.gamma != 1.0:
        x = cv2.pow(x, g.gamma)
    # Мягкое плечо: белое не обрубается, а уходит в свет.
    x = np.minimum(x, 0.8) + (1.0 - cv2.exp(-np.maximum(x - 0.8, 0.0) * 5.0)) * 0.2
    if g.contrast:
        s = x * x * (3.0 - 2.0 * x)
        x = x + (s - x) * g.contrast
    luma = (0.0722 * x[..., 0] + 0.7152 * x[..., 1] + 0.2126 * x[..., 2])[..., None]
    x = x + g.shadow * (1.0 - luma) ** 3 + g.high * luma ** 2
    return x


def bloom(x, amount, thresh, streak=0.0):
    if amount <= 0.001 and streak <= 0.001:
        return x
    small = cv2.resize(x, (W // 4, H // 4), interpolation=cv2.INTER_AREA)
    bright = np.clip(small - thresh, 0.0, None) / max(0.05, 1.0 - thresh)
    a = cv2.GaussianBlur(bright, (0, 0), 5.0)
    b = cv2.GaussianBlur(cv2.resize(bright, (W // 16, H // 16), interpolation=cv2.INTER_AREA), (0, 0), 6.0)
    glow = a * 0.6 + cv2.resize(b, (W // 4, H // 4), interpolation=cv2.INTER_LINEAR) * 0.8
    glow = glow * amount
    if streak > 0.001:
        # Анаморфный блик: яркое тянется в тонкую горизонтальную нить.
        line = cv2.GaussianBlur(bright, (0, 0), sigmaX=46.0, sigmaY=0.9)
        glow = glow + line * streak
    return x + cv2.resize(glow, (W, H), interpolation=cv2.INTER_LINEAR)


class Embers:
    """Искры и пепел в воздухе: мелкие, с полосой движения, поверх кадра. Положение зависит только от времени, поэтому планы считаются порознь."""

    def __init__(self, n=170, seed=3):
        rng = np.random.default_rng(seed)
        self.x0 = rng.uniform(0, W, n)
        self.y0 = rng.uniform(0, H, n)
        self.vx = rng.uniform(-46, 46, n)
        self.vy = rng.uniform(-120, -24, n)
        self.size = rng.uniform(0.35, 1.0, n)
        self.phase = rng.uniform(0, 6.28, n)
        self.freq = rng.uniform(0.6, 2.8, n)

    def draw(self, x, t, amount, rgb):
        if amount <= 0.001:
            return x
        layer = np.zeros((H // 2, W // 2), np.uint8)
        px = ((self.x0 + self.vx * t + np.sin(t * self.freq + self.phase) * 24.0) % W) / 2.0
        py = ((self.y0 + self.vy * t) % H) / 2.0
        tail = 0.022
        shift = 4
        scale = 1 << shift
        for i in range(len(px)):
            glow = 0.55 + 0.45 * math.sin(t * self.freq[i] * 5.0 + self.phase[i])
            value = int(255 * self.size[i] * glow)
            a = (int(px[i] * scale), int(py[i] * scale))
            b = (int((px[i] - self.vx[i] * tail / 2.0) * scale), int((py[i] - self.vy[i] * tail / 2.0) * scale))
            cv2.line(layer, a, b, value, 1 + int(self.size[i] > 0.82), cv2.LINE_AA, shift)
        soft = cv2.GaussianBlur(layer.astype(np.float32) / 255.0, (0, 0), 0.8)
        soft = cv2.resize(soft, (W, H), interpolation=cv2.INTER_LINEAR)
        return x + soft[..., None] * np.array(rgb[::-1], dtype=np.float32) * amount


EMBERS = Embers()


# ---------------------------------------------------------------- сдвиг кадра

def _matrix(scale, dx, dy, roll, cx=W / 2, cy=H / 2):
    a = math.radians(roll)
    c, s = math.cos(a) * scale, math.sin(a) * scale
    return np.array([[c, -s, cx - c * cx + s * cy + dx], [s, c, cy - s * cx - c * cy + dy]], dtype=np.float32)


def warp(x, scale, dx, dy, roll, split, blur, cx=W / 2, cy=H / 2):
    """Масштаб и сдвиг, расхождение цветов от центра и радиальное размытие удара."""
    flags = cv2.INTER_CUBIC
    if blur > 0.002:
        n = 7
        out = None
        for i in range(n):
            s = scale * (1.0 + blur * i / (n - 1))
            f = cv2.warpAffine(x, _matrix(s, dx, dy, roll, cx, cy), (W, H), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
            out = f if out is None else out + f
        x = out / n
    elif split <= 0.0003:
        return cv2.warpAffine(x, _matrix(scale, dx, dy, roll, cx, cy), (W, H), flags=flags, borderMode=cv2.BORDER_REPLICATE)
    if split <= 0.0003:
        return x
    base = scale if blur <= 0.002 else 1.0
    b0 = dx if blur <= 0.002 else 0.0
    b1 = dy if blur <= 0.002 else 0.0
    r0 = roll if blur <= 0.002 else 0.0
    ch = cv2.split(x)
    ch = [cv2.warpAffine(ch[0], _matrix(base * (1.0 - split), b0, b1, r0, cx, cy), (W, H), flags=flags, borderMode=cv2.BORDER_REPLICATE),
          cv2.warpAffine(ch[1], _matrix(base, b0, b1, r0, cx, cy), (W, H), flags=flags, borderMode=cv2.BORDER_REPLICATE),
          cv2.warpAffine(ch[2], _matrix(base * (1.0 + split), b0, b1, r0, cx, cy), (W, H), flags=flags, borderMode=cv2.BORDER_REPLICATE)]
    return cv2.merge(ch)


def noise1(t, seed):
    """Гладкий шум для тряски."""
    return (math.sin(t * 61.0 + seed) * 0.5 + math.sin(t * 37.0 + seed * 2.1) * 0.3 + math.sin(t * 89.0 + seed * 3.7) * 0.2)


# ---------------------------------------------------------------- удары и вспышки

class Timeline:
    """Глобальные события по времени трека: удары (зум, тряска, цвета), вспышки, пульс долей."""

    def __init__(self):
        self.hits = []
        self.flashes = []
        self.pulses = []
        self.cards = []
        self.bars = [(0.0, BAR)]
        self.fades = []

    def hit(self, t, power=1.0, zoom=True):
        self.hits.append((t, power, zoom))

    def flash(self, t, dur=0.12, rgb=(1, 1, 1), strength=1.0, attack=0.0):
        self.flashes.append((t, dur, np.array(rgb[::-1], dtype=np.float32), strength, attack))

    def pulse(self, t, power=0.35):
        self.pulses.append((t, power))

    def fade(self, t0, t1, a0, a1, rgb=(0, 0, 0)):
        """Шторка: с непрозрачности a0 к a1 за [t0, t1]."""
        self.fades.append((t0, t1, a0, a1, np.array(rgb[::-1], dtype=np.float32)))

    def state(self, t):
        scale, shake, split, blur, lift = 0.0, 0.0, 0.0, 0.0, 0.0
        for at, power, zoom in self.hits:
            d = t - at
            if d < -1e-4 or d > 1.2:
                continue
            e = math.exp(-d * 9.0)
            if zoom:
                scale += 0.075 * power * math.exp(-d * 13.0)
            shake += power * math.exp(-d * 8.0)
            split += 0.0065 * power * math.exp(-d * 15.0)
            blur += 0.05 * power * math.exp(-d * 34.0)
            lift += 0.42 * power * e * e
        for at, power in self.pulses:
            d = t - at
            if -1e-4 <= d < 0.5:
                scale += 0.018 * power * math.exp(-d * 11.0)
                split += 0.0016 * power * math.exp(-d * 18.0)
                lift += 0.1 * power * math.exp(-d * 15.0)
        return scale, shake, split, blur, lift

    def apply_flashes(self, x, t):
        for at, dur, rgb, strength, attack in self.flashes:
            d = t - at
            if d < -attack or d > dur:
                continue
            if d < 0:
                a = (d + attack) / max(1e-4, attack)
            else:
                k = d / max(1e-4, dur)
                a = (1.0 - k) ** 2
            a *= strength
            if a > 0.002:
                x = x * (1.0 - a * 0.85) + rgb * a
        return x

    def apply_fades(self, x, t):
        for t0, t1, a0, a1, rgb in self.fades:
            if t0 <= t <= t1:
                k = (t - t0) / max(1e-4, t1 - t0)
                a = a0 + (a1 - a0) * k * k * (3.0 - 2.0 * k)
                if a > 0.001:
                    x = x * (1.0 - a) + rgb * a
        return x

    def bar_at(self, t):
        bar = self.bars[0][1]
        points = sorted(self.bars)
        for (t0, v0), (t1, v1) in zip(points, points[1:]):
            if t0 <= t <= t1:
                k = (t - t0) / max(1e-4, t1 - t0)
                k = k * k * (3.0 - 2.0 * k)
                return v0 + (v1 - v0) * k
        return points[-1][1] if t > points[-1][0] else bar


# ---------------------------------------------------------------- надписи

def _font(name, size):
    return ImageFont.truetype(FONTS + name, size)


def text_layer(text, font, size, tracking=0, rgb=(255, 255, 255), pad=40):
    """Строка на прозрачном фоне: буква за буквой, с разрядкой. Вернёт bgra в [0,1]."""
    f = _font(font, size)
    probe = ImageDraw.Draw(Image.new("L", (8, 8)))
    widths = [probe.textlength(c, font=f) for c in text]
    total = sum(widths) + tracking * max(0, len(text) - 1)
    box = f.getbbox(text)
    height = box[3] + pad
    img = Image.new("L", (int(total) + pad * 2, height + pad), 0)
    draw = ImageDraw.Draw(img)
    x = pad
    for c, w in zip(text, widths):
        draw.text((x, pad // 2), c, font=f, fill=255)
        x += w + tracking
    alpha = np.asarray(img, dtype=np.float32) / 255.0
    ys, xs = np.nonzero(alpha > 0.01)
    alpha = alpha[max(0, ys.min() - 8):ys.max() + 8, max(0, xs.min() - 8):xs.max() + 8]
    out = np.zeros(alpha.shape + (4,), dtype=np.float32)
    out[..., 0], out[..., 1], out[..., 2] = rgb[2] / 255.0, rgb[1] / 255.0, rgb[0] / 255.0
    out[..., 3] = alpha
    return out


class Card:
    """Надпись на экране: появляется резко или плавно, чуть дрейфует, на ударе разъезжается по цветам."""

    def __init__(self, t0, t1, layer, y=0.5, x=0.5, scale0=1.0, scale1=1.0, fade_in=0.0, fade_out=0.15, glow=0.0, split=0.0, cut=None):
        self.t0, self.t1 = t0, t1
        self.layer = layer
        self.x, self.y = x, y
        self.scale0, self.scale1 = scale0, scale1
        self.fade_in, self.fade_out = fade_in, fade_out
        self.glow = glow
        self.split = split
        # Разрез: (секунда, смещение за секунду), верх и низ надписи разъезжаются по косой.
        self.cut = cut

    def alpha(self, t):
        a = 1.0
        if self.fade_in > 0:
            a = min(a, (t - self.t0) / self.fade_in)
        if self.fade_out > 0:
            a = min(a, (self.t1 - t) / self.fade_out)
        return float(np.clip(a, 0.0, 1.0))

    def draw(self, x, t):
        a = self.alpha(t)
        if a <= 0.002:
            return x
        k = (t - self.t0) / max(1e-4, self.t1 - self.t0)
        s = self.scale0 + (self.scale1 - self.scale0) * k
        l = self.layer
        h, w = l.shape[:2]
        cx, cy = self.x * W, self.y * H
        m = np.array([[s, 0, cx - s * w / 2], [0, s, cy - s * h / 2]], dtype=np.float32)
        layer = cv2.warpAffine(l, m, (W, H), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_CONSTANT, borderValue=(0, 0, 0, 0))
        layer = np.clip(layer, 0.0, 1.0)
        alpha = layer[..., 3:4]
        if self.cut is not None:
            at, speed = self.cut
            d = t - at
            if d > 0:
                # Косая линия через середину надписи: части едут в разные стороны.
                move = speed * (1.0 - math.exp(-d * 6.0))
                yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
                line = (yy - cy) + (xx - cx) * 0.18
                shift = np.where(line < 0, move, -move).astype(np.float32)
                alpha = cv2.remap(alpha, xx + shift, yy, cv2.INTER_LINEAR)[..., None]
                layer = cv2.remap(layer, xx + shift, yy, cv2.INTER_LINEAR)
        if self.glow > 0:
            g = cv2.GaussianBlur(alpha, (0, 0), 14.0)
            if g.ndim == 2:
                g = g[..., None]
            x = x + g * layer[..., :3] * self.glow * a
        x = x * (1.0 - alpha * a) + layer[..., :3] * alpha * a
        return x


# ---------------------------------------------------------------- план

class Shot:
    def __init__(self, t0, t1, src, look, shutter=0.6, zoom0=1.04, zoom1=None, pan=(0, 0, 0, 0), roll=0.0, center=None, over=None, over_alpha=None, handheld=1.0, name=""):
        self.t0, self.t1 = t0, t1
        self.src = src
        self.look = look
        self.shutter = shutter
        self.zoom0 = zoom0
        self.zoom1 = zoom0 if zoom1 is None else zoom1
        self.pan = pan
        self.roll = roll
        self.center = center
        self.over = over
        self.over_alpha = over_alpha
        self.handheld = handheld
        self.name = name


_grain = None


def _grain_tile():
    global _grain
    if _grain is None:
        rng = np.random.default_rng(7)
        tile = rng.normal(0.0, 1.0, (H + 64, W + 64)).astype(np.float32)
        _grain = cv2.GaussianBlur(tile, (0, 0), 0.8) * 1.6
    return _grain


def render_frame(frame, shot, tl, readers, folder):
    t = frame / FPS
    src_frame = exposure(readers[0], shot.src, t, shot.shutter)
    if shot.over is not None:
        a = shot.over_alpha(t)
        if a > 0.002:
            second = exposure(readers[1], shot.over, t, shot.shutter)
            # Двойная экспозиция: светлое складывается.
            src_frame = src_frame + second * a - src_frame * second * a
    k = (t - shot.t0) / max(1e-4, shot.t1 - shot.t0)
    ease = k * k * (3.0 - 2.0 * k)
    hit_zoom, shake, split, blur, lift = tl.state(t)
    zoom = shot.zoom0 + (shot.zoom1 - shot.zoom0) * ease + hit_zoom
    amp = 10.0 * shake
    dx = amp * noise1(t, 1.3) + (shot.pan[0] + (shot.pan[2] - shot.pan[0]) * ease)
    dy = amp * noise1(t, 4.1) + (shot.pan[1] + (shot.pan[3] - shot.pan[1]) * ease)
    # Ручная камера: едва заметно дышит.
    dx += shot.handheld * 1.6 * math.sin(t * 1.3 + shot.t0) + shot.handheld * 0.7 * math.sin(t * 3.1)
    dy += shot.handheld * 1.2 * math.sin(t * 1.7 + shot.t0 * 2.0)
    roll = shot.roll * ease + shake * 0.5 * noise1(t, 7.7) + shot.handheld * 0.05 * math.sin(t * 0.9)
    cx, cy = shot.center if shot.center else (W / 2, H / 2)
    x = warp(src_frame, zoom, dx, dy, roll, split + 0.0006, blur, cx, cy)
    g = shot.look
    x = grade(x, g, 1.0 + lift)
    x = bloom(x, g.bloom + lift * 0.5, g.thresh, g.streak)
    x = EMBERS.draw(x, t, g.embers, g.ember_rgb)
    x = tl.apply_flashes(x, t)
    if g.vignette > 0:
        x = x * (1.0 - VIGNETTE[..., None] * g.vignette)
    if g.ground > 0:
        x = x * (1.0 - GROUND[..., None] * g.ground)
    x = tl.apply_fades(x, t)
    for card in tl.cards:
        if card.t0 <= t <= card.t1:
            x = card.draw(x, t)
    # Зерно: едва заметное, и в чёрном почти нет. Заодно не даёт полосить в тёмных переходах при сжатии.
    tile = _grain_tile()
    ox, oy = (frame * 37) % 64, (frame * 23) % 64
    luma = np.clip(x[..., 1], 0.0, 1.0)
    grain = tile[oy:oy + H, ox:ox + W] * (0.006 + 0.016 * np.sqrt(luma))
    x = x + grain[..., None]
    bar = int(round(tl.bar_at(t)))
    out = (np.clip(x, 0.0, 1.0) * 255.0 + 0.5).astype(np.uint8)
    if bar > 0:
        out[:bar] = 0
        out[H - bar:] = 0
    return out


def open_readers(shot, folder, first_frame):
    t = first_frame / FPS
    readers = [Reader(folder, shot.src.take, int(math.floor(shot.src.at(t) * SRC_FPS)) - 1)]
    if shot.over is not None:
        readers.append(Reader(folder, shot.over.take, int(math.floor(shot.over.at(t) * SRC_FPS)) - 1))
    return readers


def render_shot(shot, tl, folder, out_path):
    """Один план в файл без потерь."""
    first, last = frame_of(shot.t0), frame_of(shot.t1)
    enc = subprocess.Popen([FFMPEG, "-hide_banner", "-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt", "bgr24", "-video_size", "%dx%d" % (W, H), "-framerate", str(FPS),
                            "-i", "-", "-c:v", "ffv1", "-level", "3", "-slices", "4", "-g", "1", out_path], stdin=subprocess.PIPE, bufsize=1 << 24)
    readers = open_readers(shot, folder, first)
    try:
        for f in range(first, last):
            enc.stdin.write(render_frame(f, shot, tl, readers, folder).tobytes())
    finally:
        for r in readers:
            r.close()
        enc.stdin.close()
        enc.wait()


def still(shot, tl, folder, t):
    f = frame_of(t)
    readers = open_readers(shot, folder, f)
    try:
        return render_frame(f, shot, tl, readers, folder)
    finally:
        for r in readers:
            r.close()
