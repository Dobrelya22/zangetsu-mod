"""
Картинки для страницы мода: большой баннер и заголовки разделов из настоящих кадров игры, обложка для видео.
Запуск из корня репозитория: python banners.py (нужны дубли в ref/clip/takes). Результат в docs/page.
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
if os.environ.get("CLIP_PYLIBS"):
    sys.path.insert(0, os.environ["CLIP_PYLIBS"])
import cv2  # noqa: E402
import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

import edl  # noqa: E402
import engine  # noqa: E402
from edl import CLOSE, DREAM, EYE, FINAL, HOLLOW, STEEL, VOID  # noqa: E402

OUT = "docs/page"
FONTS = "C:/Windows/Fonts/"


def frame(take, s):
    raw = subprocess.run([engine.FFMPEG, "-hide_banner", "-loglevel", "error", "-ss", "%.4f" % s, "-i", "ref/clip/takes/%s.mkv" % take, "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "bgr24", "-"], capture_output=True).stdout
    return np.frombuffer(raw, np.uint8).reshape(engine.H, engine.W, 3).astype(np.float32) / 255.0


def graded(take, s, look):
    x = engine.grade(frame(take, s), look)
    x = engine.bloom(x, look.bloom, look.thresh, look.streak)
    x = engine.EMBERS.draw(x, 3.0 + s, look.embers, look.ember_rgb)
    if look.vignette > 0:
        x = x * (1.0 - engine.VIGNETTE[..., None] * look.vignette * 0.8)
    if look.ground > 0:
        x = x * (1.0 - engine.GROUND[..., None] * look.ground)
    return np.clip(x, 0.0, 1.0)


def crop(x, width, height, cx=0.5, cy=0.5, zoom=1.0):
    """Вырезать кусок с нужными пропорциями; cx, cy — центр выреза в долях кадра."""
    h, w = x.shape[:2]
    scale = max(width / w, height / h) * zoom
    resized = cv2.resize(x, (int(round(w * scale)), int(round(h * scale))), interpolation=cv2.INTER_AREA if scale < 1 else cv2.INTER_CUBIC)
    rh, rw = resized.shape[:2]
    x0 = int(np.clip(cx * rw - width / 2, 0, rw - width))
    y0 = int(np.clip(cy * rh - height / 2, 0, rh - height))
    return resized[y0:y0 + height, x0:x0 + width]


def tracked(draw, xy, text, font, fill, tracking, anchor_right=False):
    x, y = xy
    widths = [draw.textlength(c, font=font) for c in text]
    total = sum(widths) + tracking * (len(text) - 1)
    if anchor_right:
        x -= total
    for c, w in zip(text, widths):
        draw.text((x, y), c, font=font, fill=fill)
        x += w + tracking
    return total


def to_pil(x):
    return Image.fromarray((x[..., ::-1] * 255.0 + 0.5).astype(np.uint8), "RGB")


def with_glow(base, draw_fn, blur=16, strength=0.9):
    """Текст со свечением: рисуем отдельно, размываем, складываем."""
    layer = Image.new("RGB", base.size, (0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    a = np.asarray(layer, dtype=np.float32) / 255.0
    glow = cv2.GaussianBlur(a, (0, 0), blur)
    b = np.asarray(base, dtype=np.float32) / 255.0
    out = np.clip(b + glow * strength, 0.0, 1.0)
    out = np.maximum(out, a)
    return Image.fromarray((out * 255.0 + 0.5).astype(np.uint8), "RGB")


def compose(x, W, H, side, sx, sy, zoom):
    """Герой в одной половине кадра, вторая под текст: фон там из того же кадра, размытый и притушенный, переход плавный."""
    h, w = x.shape[:2]
    scale = zoom * max(H / h, 0.62 * W / w)
    fg = cv2.resize(x, (int(round(w * scale)), int(round(h * scale))), interpolation=cv2.INTER_AREA if scale < 1 else cv2.INTER_CUBIC)
    tx = W * (0.70 if side == "left" else 0.30)
    m = np.array([[1.0, 0.0, tx - sx * fg.shape[1]], [0.0, 1.0, H * 0.5 - sy * fg.shape[0]]], dtype=np.float32)
    placed = cv2.warpAffine(fg, m, (W, H), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
    back = cv2.GaussianBlur(crop(x, W, H, 0.5, 0.5), (0, 0), 46) * 0.3
    ramp = np.linspace(0.0, 1.0, W, dtype=np.float32)
    k = (ramp - 0.34) / 0.2 if side == "left" else (0.66 - ramp) / 0.2
    k = np.clip(k, 0.0, 1.0)
    k = (k * k * (3.0 - 2.0 * k))[None, :, None]
    return back * (1.0 - k) + placed * k


def section(name, take, s, look, title, lines, side="left", sx=0.5, sy=0.5, zoom=1.0):
    """{side}: с какой стороны текст. title и lines — списки строк: длинное переносим сами, чтобы текст не заходил на героя."""
    W, H = 1600, 460
    x = compose(graded(take, s, look), W, H, side, sx, sy, zoom)
    img = to_pil(x)
    head = ImageFont.truetype(FONTS + "bahnschrift.ttf", 70)
    small = ImageFont.truetype(FONTS + "segoeuil.ttf", 33)
    right = side == "right"
    px = W - 76 if right else 76
    block = len(title) * 84 + 30 + len(lines) * 44
    y = (H - block) / 2

    def text(d):
        for k, t in enumerate(title):
            tracked(d, (px, y + k * 84), t, head, (255, 255, 255), 5, right)

    img = with_glow(img, text, blur=14, strength=0.45)
    d = ImageDraw.Draw(img)
    bar_y = y + len(title) * 84 + 8
    d.rectangle((px - 150 if right else px, bar_y, px if right else px + 150, bar_y + 5), fill=(225, 42, 34))
    for k, t in enumerate(lines):
        tracked(d, (px, bar_y + 24 + k * 44), t, small, (230, 224, 220), 1, right)
    img.save(os.path.join(OUT, name + ".jpg"), quality=90, subsampling=0, optimize=True)


def logo(img, cx, top, kanji_size=250, name_size=66, tracking=32):
    kanji = ImageFont.truetype(FONTS + "YuGothB.ttc", kanji_size)
    name = ImageFont.truetype(FONTS + "bahnschrift.ttf", name_size)
    probe = ImageDraw.Draw(img)
    kw = probe.textlength("斬", font=kanji) + probe.textlength("月", font=kanji) + 26
    nw = sum(probe.textlength(c, font=name) for c in "ZANGETSU") + tracking * 7

    def text(d):
        tracked(d, (cx - kw / 2, top), "斬月", kanji, (255, 255, 255), 26)

    img = with_glow(img, text, blur=20, strength=0.8)

    def text2(d):
        tracked(d, (cx - nw / 2, top + kanji_size * 1.22), "ZANGETSU", name, (232, 46, 38), tracking)

    return with_glow(img, text2, blur=12, strength=0.8)


def main():
    os.makedirs(OUT, exist_ok=True)
    # Большой баннер: маска с огнём справа, название слева.
    W, H = 1600, 700
    img = to_pil(compose(graded("mask_final", 1.0, CLOSE), W, H, "left", 0.49, 0.47, 1.06))
    img = logo(img, 360, 118)
    d = ImageDraw.Draw(img)
    small = ImageFont.truetype(FONTS + "segoeuil.ttf", 34)
    line = "A living sword from Bleach"
    d.text((360 - d.textlength(line, font=small) / 2, 530), line, font=small, fill=(230, 224, 220))
    img.save(os.path.join(OUT, "banner.jpg"), quality=91, subsampling=0, optimize=True)

    # Разделы.
    section("s_dream", "calling_game", 8.2, DREAM, ["THE SWORD", "CHOOSES YOU"], ["No crafting. Go to sleep,", "and it comes to you in a dream."], "left", 0.5, 0.5, 1.2)
    section("s_eye", "sword_burning", 2.5, EYE, ["A LIVING EYE"], ["It watches your enemies, blinks", "and has to be befriended."], "right", 0.51, 0.42, 0.9)
    section("s_combat", "ff_swing", 1.05, FINAL, ["REAL SWORD", "COMBAT"], ["Eight weighty moves.", "Arrows split in mid-air. Limbs fly."], "left", 0.45, 0.42, 1.5)
    section("s_exec", "x_behead_b", 2.6, STEEL, ["CUT THEM", "IN TWO"], ["Beat the reaction check, and the", "blade slices the mob's real model."], "right", 0.62, 0.48, 1.05)
    section("s_hollow", "h_fight", 0.55, HOLLOW, ["HOLLOW FORM"], ["Put on the mask. A blood moon rises.", "Unleash Getsuga Tensho."], "left", 0.55, 0.45, 1.1)
    section("s_final", "ff_orbit", 1.5, FINAL, ["THE FINAL", "FORM"], ["A horned mask, living fire,", "a cloth cloak and ringing chains."], "right", 0.5, 0.48, 1.45)
    section("s_break", "break_game", 13.5, VOID, ["THE SWORD DIES", "TO SAVE YOU"], ["One fatal blow, and", "Yhwach crushes it in his fist."], "left", 0.5, 0.5, 1.55)
    section("s_rampage", "rampage_game", 29.9, CLOSE, ["THE HOLLOW", "TAKES OVER"], ["Die in the Final Form and watch", "it slaughter a whole crowd."], "right", 0.5, 0.47, 1.25)
    section("s_start", "calling_cine", 25.2, DREAM, ["HOW TO START"], ["Fabric, Minecraft 1.21.1.", "Then just go to bed."], "left", 0.55, 0.52, 1.15)

    # То же для русской страницы.
    section("ru_dream", "calling_game", 8.2, DREAM, ["МЕЧ ВЫБИРАЕТ", "ТЕБЯ"], ["Крафтить не надо. Ляг спать,", "и он придёт к тебе во сне."], "left", 0.5, 0.5, 1.2)
    section("ru_eye", "sword_burning", 2.5, EYE, ["ЖИВОЙ ГЛАЗ"], ["Следит за врагами, моргает,", "и с ним надо подружиться."], "right", 0.51, 0.42, 0.9)
    section("ru_combat", "ff_swing", 1.05, FINAL, ["НАСТОЯЩИЙ", "БОЙ НА МЕЧАХ"], ["Восемь весомых приёмов.", "Стрелы пополам, конечности летят."], "left", 0.45, 0.42, 1.5)
    section("ru_exec", "x_behead_b", 2.6, STEEL, ["РУБИ", "ПОПОЛАМ"], ["Успей по реакции, и клинок", "разрубит саму модель моба."], "right", 0.62, 0.48, 1.05)
    section("ru_hollow", "h_fight", 0.55, HOLLOW, ["ФОРМА ПУСТОГО"], ["Надень маску. Взойдёт кровавая луна.", "Выпусти Гецугу Теншо."], "left", 0.55, 0.45, 1.1)
    section("ru_final", "ff_orbit", 1.5, FINAL, ["ФИНАЛЬНАЯ", "ФОРМА"], ["Маска с рогом, живой огонь,", "тканевый плащ и звенящие цепи."], "right", 0.5, 0.48, 1.45)
    section("ru_break", "break_game", 13.5, VOID, ["МЕЧ ГИБНЕТ,", "СПАСАЯ ТЕБЯ"], ["Один смертельный удар,", "и Яхве раздавит его в кулаке."], "left", 0.5, 0.5, 1.55)
    section("ru_rampage", "rampage_game", 29.9, CLOSE, ["ПУСТОЙ ЗАБИРАЕТ", "ТЕЛО"], ["Умри в финальной форме и смотри,", "как он рубит целую толпу."], "right", 0.5, 0.47, 1.25)
    section("ru_start", "calling_cine", 25.2, DREAM, ["КАК НАЧАТЬ"], ["Fabric, Minecraft 1.21.1.", "А дальше просто ляг спать."], "left", 0.55, 0.52, 1.15)

    # Обложка видео.
    W, H = 1280, 720
    img = to_pil(compose(graded("rampage_game", 29.9, CLOSE), W, H, "left", 0.5, 0.47, 1.12))
    img = logo(img, 300, 130, kanji_size=180, name_size=50, tracking=22)
    d = ImageDraw.Draw(img)
    small = ImageFont.truetype(FONTS + "bahnschrift.ttf", 38)
    d.text((300 - d.textlength("MUSIC VIDEO", font=small) / 2, 450), "MUSIC VIDEO", font=small, fill=(240, 236, 232))
    img.save(os.path.join(OUT, "video.jpg"), quality=92, subsampling=0, optimize=True)
    print("готово")


if __name__ == "__main__":
    main()
