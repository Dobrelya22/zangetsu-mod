"""
Монтажный лист клипа. Секунды слева — время трека (сетка тактов в ref/clip/beats.json), справа — секунды дубля.
Трек: 165,4 удара в минуту, такт 1,45 с. Вступление до 23,64; дропы 23,64 / 47,10 / 82,07 / 105,23 / 128,30.
"""
import json
import os

import engine
from engine import BAR, Card, Look, Shot, Src, Timeline, text_layer

D = json.load(open("ref/clip/beats.json"))
BARS = D["bars"]
BEATS = D["beats"]
DURATION = D["duration"]
BEAT = (BEATS[-1] - BEATS[0]) / (len(BEATS) - 1)
# Касание клинка в дублях казней (секунда дубля), снято с кадров: у обоих проходов одно и то же.
HITS = {"kesa": 1.58, "behead": 1.60, "waist": 1.65, "shatter": 1.78, "launch": 1.58, "gyaku": 1.40, "chest": 1.32, "cleave": 1.78, "split": 1.58, "slices": 1.48}
# Секунда, когда срывается Гецуга.
WAVE = 1.02

# ---------------------------------------------------------------- цвет по частям

DREAM = Look(gain=1.32, gamma=0.96, contrast=0.5, sat=0.82, red_sat=1.3, shadow=(0.012, 0.0, 0.004), vignette=0.55, bloom=0.22, thresh=0.62, embers=0.3, ember_rgb=(1.0, 0.25, 0.2))
TRIAL = Look(gain=0.92, gamma=1.08, contrast=0.55, sat=0.6, red_sat=1.3, shadow=(0.0, 0.002, 0.012), ground=0.3, vignette=0.6, bloom=0.2, thresh=0.7, embers=0.2, ember_rgb=(0.9, 0.9, 1.0))
EYE = Look(gain=1.0, gamma=1.05, contrast=0.55, sat=1.0, red_sat=1.15, vignette=0.75, bloom=0.5, thresh=0.5, streak=0.25)
STEEL = Look(gain=1.0, gamma=1.0, contrast=0.5, sat=0.62, red_sat=1.35, shadow=(0.0, 0.004, 0.012), ground=0.42, vignette=0.55, bloom=0.14, thresh=0.8)
HOLLOW = Look(gain=0.94, gamma=1.04, contrast=0.62, sat=0.62, red_sat=1.45, shadow=(0.012, 0.0, 0.0), ground=0.6, vignette=0.6, bloom=0.32, thresh=0.62, streak=0.12, embers=0.28, ember_rgb=(1.0, 0.2, 0.12))
VOID = Look(gain=1.3, gamma=0.98, contrast=0.55, sat=0.7, red_sat=1.25, vignette=0.55, bloom=0.3, thresh=0.5, streak=0.1, embers=0.22, ember_rgb=(1.0, 0.3, 0.3))
FINAL = Look(gain=0.88, gamma=1.06, contrast=0.6, sat=0.82, red_sat=1.35, ground=0.6, vignette=0.6, bloom=0.42, thresh=0.58, streak=0.2, embers=0.5)
RAMPAGE = Look(gain=0.74, gamma=1.12, contrast=0.62, sat=0.72, red_sat=1.5, shadow=(0.014, 0.0, 0.0), ground=0.68, vignette=0.65, bloom=0.42, thresh=0.6, streak=0.2, embers=0.5)
RAMPAGE_WIDE = Look(gain=0.56, gamma=1.2, contrast=0.6, sat=0.72, red_sat=1.5, shadow=(0.014, 0.0, 0.0), ground=0.5, vignette=0.7, bloom=0.3, thresh=0.72, streak=0.12, embers=0.45)
HOLLOW_WIDE = Look(gain=0.62, gamma=1.18, contrast=0.6, sat=0.62, red_sat=1.45, shadow=(0.012, 0.0, 0.0), ground=0.4, vignette=0.7, bloom=0.22, thresh=0.75, embers=0.28, ember_rgb=(1.0, 0.2, 0.12))
CLOSE = Look(gain=0.92, gamma=1.08, contrast=0.6, sat=0.85, red_sat=1.3, ground=0.45, vignette=0.6, bloom=0.42, thresh=0.6, streak=0.22, embers=0.5)


def snap(t):
    """Ближайшая настоящая доля трека."""
    return min(BEATS, key=lambda b: abs(b - t))


def at(take, t, s, rate=1.0):
    """В секунду трека t дубль на секунде s и идёт со скоростью rate."""
    return Src(take, [(t, s), (t + 1.0, s + rate)])


def impact(take, t, s, pre=1.0, post=0.5, stop=0.05):
    """До удара скорость pre, в момент удара почти стоп, потом замедление: удар чувствуется."""
    return Src(take, [(t - 3.0, s - 3.0 * pre), (t, s), (t + stop, s + stop * 0.08), (t + stop + 3.0, s + stop * 0.08 + 3.0 * post)])


class Edit:
    def __init__(self):
        self.cuts = []
        self.tl = Timeline()

    def cut(self, t, src, look, **kw):
        self.cuts.append((t, src, look, kw))

    def finish(self):
        self.cuts.sort(key=lambda c: c[0])
        shots = []
        for i, (t, src, look, kw) in enumerate(self.cuts):
            end = self.cuts[i + 1][0] if i + 1 < len(self.cuts) else DURATION
            if engine.frame_of(end) <= engine.frame_of(t):
                raise SystemExit("пустой план: %s на %.2f" % (src.take, t))
            shots.append(Shot(t, end, src, look, name=src.take, **kw))
        return shots


def hit(e, t, power=1.0, flash=0.0, rgb=(1, 1, 1), dur=0.12):
    e.tl.hit(t, power)
    if flash > 0:
        e.tl.flash(t, dur, rgb, flash)


def execution(e, name, t, look=STEEL, power=0.8, rate=1.7, post=1.25, flash=0.28, close=True):
    """Казнь на сильную долю: замах общим планом за две доли до удара, в сам удар склейка на крупный."""
    h = HITS[name]
    e.cut(snap(t - BEAT * 2.0), at("x_%s_a" % name, t, h, rate), look, zoom0=1.05, zoom1=1.09)
    e.cut(t, impact("x_%s_%s" % (name, "b" if close else "a"), t, h, pre=rate, post=post, stop=0.045), look, zoom0=1.08 if close else 1.16, zoom1=1.04 if close else 1.1)
    hit(e, t, power, flash)


def subtitle(e, t0, t1, text):
    e.tl.cards.append(Card(t0, t1, text_layer(text, "segoeuil.ttf", 38, tracking=3, rgb=(235, 228, 224)), y=0.936, fade_in=0.18, fade_out=0.25))


def line(e, t0, t1, text, size=62, y=0.5, rgb=(255, 255, 255), glow=0.8, font="georgiab.ttf", tracking=6, scale0=0.98, scale1=1.03):
    """Большая реплика поверх кадра."""
    e.tl.cards.append(Card(t0, t1, text_layer(text, font, size, tracking=tracking, rgb=rgb), y=y, scale0=scale0, scale1=scale1, fade_in=0.05, fade_out=0.2, glow=glow))


def title(e, t0, t1, slash=None, fade_out=0.0):
    e.tl.cards.append(Card(t0, t1, text_layer("斬月", "YuGothB.ttc", 300, tracking=26), y=0.43, scale0=0.97, scale1=1.05, fade_out=fade_out, glow=0.9, cut=slash and (slash, 70.0)))
    e.tl.cards.append(Card(t0, t1, text_layer("ZANGETSU", "bahnschrift.ttf", 64, tracking=34, rgb=(230, 50, 40)), y=0.69, scale0=1.0, scale1=1.03, fade_out=fade_out, glow=0.7, cut=slash and (slash, 70.0)))


def build():
    e = Edit()
    tl = e.tl
    intro(e)
    drop1(e)
    mask(e)
    hollow(e)
    sword_break(e)
    drop2(e)
    stop(e)
    drop3(e)
    breakdown(e)
    drop4(e)
    outro(e)

    # Пульс: каждая доля в дропах чуть толкает кадр, сильная доля сильнее.
    for a, b in ((25.11, 44.16), (47.10, 67.53), (82.07, 102.33), (105.23, 118.21), (128.30, 148.41)):
        for t in BEATS:
            if a <= t < b:
                strong = min(abs(t - x) for x in BARS) < 0.05
                tl.pulse(t, 0.8 if strong else 0.34)
    return e.finish(), tl


# ---------------------------------------------------------------- 0:00 вступление

def intro(e):
    tl = e.tl
    # Кадр открывается щелью, как глаз.
    tl.bars = [(0.0, 540), (0.6, 500), (5.94, BAR)]
    tl.fade(0.0, 2.2, 1.0, 0.0)
    # Глаз меча во тьме открывается.
    e.cut(0.0, at("sword_wake", 0.0, 0.0, 1.0), EYE, zoom0=1.05, zoom1=1.16, handheld=0.6)
    # Один на поле; за спиной проступает фигура.
    e.cut(5.94, Src("calling_cine", [(5.94, 1.9), (11.88, 8.7)]), DREAM, zoom0=1.04, zoom1=1.1)
    subtitle(e, 7.42, 11.0, "Вот мы и встретились.")
    # Удар: он весь, во весь кадр.
    e.cut(11.88, at("calling_game", 11.88, 6.8, 0.95), DREAM, zoom0=1.06, zoom1=1.12)
    hit(e, 11.88, 0.8, 0.5, (1.0, 0.2, 0.15), 0.3)
    # Лицо длинным объективом.
    e.cut(14.80, at("calling_cine", 14.80, 9.2, 0.75), DREAM, zoom0=1.04, zoom1=1.08)
    e.cut(16.28, at("calling_cine", 16.28, 13.2, 0.8), DREAM, zoom0=1.04, zoom1=1.08)
    # Удар: руки из плаща.
    e.cut(17.76, at("calling_game", 17.76, 15.7, 0.95), DREAM, zoom0=1.06, zoom1=1.16)
    hit(e, 17.76, 0.8, 0.5, (1.0, 0.2, 0.15), 0.3)
    subtitle(e, 18.3, 20.5, "Ты достоин носить этот меч.")
    # Меч летит и вонзается: 22,91.
    e.cut(20.69, Src("calling_cine", [(20.69, 20.35), (22.91, 22.42)]), DREAM, zoom0=1.04, zoom1=1.12)
    hit(e, 22.54, 0.9, 0.6, (1, 1, 1), 0.1)
    hit(e, 22.91, 1.2, 0.9, (1, 1, 1), 0.16)
    e.cut(22.91, impact("calling_game", 22.91, 22.62, post=0.5), DREAM, zoom0=1.12, zoom1=1.06)


# ---------------------------------------------------------------- 0:23 дроп 1: без формы

def drop1(e):
    tl = e.tl
    # Название на первый удар дропа; на следующий такт его разрубает.
    e.cut(23.64, at("b_walk", 23.64, 0.6, 0.8), STEEL, zoom0=1.1, zoom1=1.16)
    tl.fade(23.64, 25.11, 0.62, 0.62)
    title(e, 23.64, 25.11, slash=24.74)
    hit(e, 23.64, 1.5, 1.0, (1, 1, 1), 0.28)
    hit(e, 24.74, 0.7, 0.35, (1.0, 0.2, 0.15), 0.1)
    # Герой идёт: коротко, чтобы выдохнуть.
    e.cut(25.11, at("b_walk", 25.11, 3.2, 0.9), STEEL, zoom0=1.05, zoom1=1.1)
    hit(e, 25.11, 1.0, 0.5, (1.0, 0.25, 0.18), 0.16)
    execution(e, "kesa", 26.57)
    execution(e, "behead", 28.04)
    execution(e, "waist", 29.52)
    execution(e, "shatter", 30.98)
    execution(e, "launch", 32.45, post=0.8)
    # Провал в треке: круг проверки реакции.
    e.cut(33.91, at("qte_good", 33.91, 0.75, 0.42), STEEL, zoom0=1.04, zoom1=1.08, handheld=1.2)
    hit(e, 33.91, 0.6)
    execution(e, "gyaku", 35.39)
    execution(e, "chest", 36.84)
    e.cut(38.30, at("b_face", 38.30, 0.5, 0.8), STEEL, zoom0=1.06, zoom1=1.11)
    hit(e, 38.30, 0.8)
    e.cut(snap(38.30 + BEAT), at("sword_open", 38.30 + BEAT, 2.6, 0.5), EYE, zoom0=1.04, zoom1=1.1)
    execution(e, "cleave", 39.78, power=1.1, flash=0.4, close=False, post=1.0)
    execution(e, "split", 41.24, power=0.9)
    execution(e, "slices", 42.70, power=1.0)


# ---------------------------------------------------------------- 0:44 маска

def mask(e):
    tl = e.tl
    # Тишина: ладонь по лицу, ровно на удар маска.
    e.cut(44.16, Src("rite_mask", [(44.16, 0.9), (46.01, 4.45)]), HOLLOW, zoom0=1.05, zoom1=1.14, handheld=0.5)
    hit(e, 46.01, 1.5, 0.8, (1.0, 0.15, 0.1), 0.34)
    e.cut(46.01, impact("rite_mask", 46.01, 4.45, post=0.8), HOLLOW, zoom0=1.14, zoom1=1.1)


# ---------------------------------------------------------------- 0:47 Пустой

def wave(e, name, t, look=HOLLOW, zoom0=1.05, zoom1=1.1, rate=1.1, lead=None):
    """Гецуга срывается ровно на такт."""
    e.cut(snap(t - (lead if lead is not None else BEAT)), at(name, t, WAVE, rate), look, zoom0=zoom0, zoom1=zoom1)
    hit(e, t, 1.0, 0.35, (1.0, 0.25, 0.2), 0.14)


def hollow(e):
    # Маска и горящий клинок — на вход дропа.
    e.cut(47.10, at("h_fight", 47.10, 0.05, 0.6), HOLLOW, zoom0=1.06, zoom1=1.12)
    hit(e, 47.10, 1.5, 0.9, (1, 1, 1), 0.26)
    wave(e, "h_wave_a", 48.54)
    wave(e, "h_wave_b", 50.01)
    e.cut(51.47, at("mask_hollow", 51.47, 0.4, 0.6), CLOSE, zoom0=1.04, zoom1=1.1)
    hit(e, 51.47, 0.8)
    e.cut(52.94, at("h_fight", 52.94, 4.2, 1.0), HOLLOW, zoom0=1.05, zoom1=1.08)
    hit(e, 52.94, 0.7)
    e.cut(snap(54.40 - BEAT * 2), at("h_wave_c", 54.40, 1.62, 1.0), HOLLOW, zoom0=1.05, zoom1=1.1)
    hit(e, 54.40, 1.2, 0.35, (1.0, 0.25, 0.2), 0.16)
    # Волна сквозь камеру: красный кадр на сильную долю.
    e.cut(snap(55.85 - BEAT * 2), Src("h_fight", [(55.85 - BEAT * 2, 12.5), (55.85, 13.0)]), HOLLOW, zoom0=1.04, zoom1=1.06)
    hit(e, 55.85, 1.3, 0.4, (1.0, 0.2, 0.15), 0.2)
    e.cut(55.85, at("h_fight", 55.85, 13.1, 0.7), HOLLOW, zoom0=1.08, zoom1=1.05)
    e.cut(57.31, at("h_fight", 57.31, 7.0, 0.9), HOLLOW_WIDE, zoom0=1.04, zoom1=1.08)
    hit(e, 57.31, 0.8)
    e.cut(58.77, at("h_fight", 58.77, 10.15, 0.9), CLOSE, zoom0=1.05, zoom1=1.1)
    hit(e, 58.77, 1.0, 0.25)
    # Быстрее: по две доли.
    t = 60.23
    picks = [("h_fight", 6.05), ("h_fight", 1.3), ("h_fight", 8.5), ("h_fight", 3.3), ("h_fight", 4.8), ("h_fight", 9.0), ("h_wave_b", WAVE + 0.05), ("h_fight", 10.5)]
    for i, (take, s) in enumerate(picks):
        ct = snap(t + i * BEAT * 2)
        e.cut(ct, at(take, ct, s, 1.0), HOLLOW, zoom0=1.05, zoom1=1.08)
        hit(e, ct, 0.55)
    e.cut(66.07, at("mask_hollow", 66.07, 3.2, 0.5), CLOSE, zoom0=1.05, zoom1=1.14)
    hit(e, 66.07, 0.9, 0.3, (1.0, 0.2, 0.15), 0.2)


# ---------------------------------------------------------------- 1:07 Яхве ломает меч

def sword_break(e):
    tl = e.tl
    # Появился. Рука из плаща.
    e.cut(67.53, Src("break_cine", [(67.53, 1.2), (68.99, 3.0)]), VOID, zoom0=1.04, zoom1=1.1)
    tl.flash(67.53, 0.5, (0.0, 0.0, 0.0), 1.0)
    e.cut(68.99, Src("break_game", [(68.99, 3.2), (69.72, 5.6)]), VOID, zoom0=1.1, zoom1=1.16)
    # Меч раскручивается и летит прямо в ладонь: удар 70,44.
    e.cut(69.72, Src("break_cine", [(69.72, 5.75), (70.2, 7.2)]), VOID, zoom0=1.06, zoom1=1.1)
    e.cut(70.2, Src("break_game", [(70.2, 7.45), (70.44, 7.9), (73.34, 9.7)]), VOID, zoom0=1.05, zoom1=1.12)
    hit(e, 70.44, 1.6, 0.6, (1, 1, 1), 0.14)
    # Рука с мечом крупно.
    e.cut(73.34, Src("break_game", [(73.34, 10.0), (76.26, 12.6)]), VOID, zoom0=1.42, zoom1=1.6, center=(480, 780), pan=(200, -85, 250, -115))
    subtitle(e, 73.5, 76.1, "Я разочарован.")
    # Трещины: всё ближе, до самого хруста.
    e.cut(76.26, Src("break_game", [(76.26, 12.72), (78.0, 13.2), (81.71, 14.05)]), VOID, zoom0=1.2, zoom1=1.85, center=(800, 640), pan=(40, -20, 120, -60))
    hit(e, 76.26, 1.1, 0.35, (1.0, 0.2, 0.15), 0.2)
    subtitle(e, 76.5, 79.6, "Я думал, ты сильнее.")
    # Хруст.
    e.cut(81.71, impact("break_game", 81.71, 14.05, post=0.5), VOID, zoom0=1.3, zoom1=1.12)
    hit(e, 81.71, 1.9, 1.0, (1, 1, 1), 0.2)


# ---------------------------------------------------------------- 1:22 дроп 2: поле мечей и финальная форма

def drop2(e):
    # Поле мечей.
    e.cut(82.07, at("trial", 82.07, 0.3, 1.0), TRIAL, zoom0=1.04, zoom1=1.08)
    hit(e, 82.07, 1.2, 0.5, (1, 1, 1), 0.2)
    e.cut(83.52, at("trial", 83.52, 2.2, 1.0), TRIAL, zoom0=1.05, zoom1=1.08)
    hit(e, 83.52, 0.7)
    e.cut(84.97, at("trial", 84.97, 4.2, 1.0), TRIAL, zoom0=1.05, zoom1=1.1)
    hit(e, 84.97, 0.7)
    e.cut(86.42, at("trial", 86.42, 6.2, 1.0), TRIAL, zoom0=1.05, zoom1=1.1)
    hit(e, 86.42, 0.7)
    # Обряд: ярость, молния, огонь.
    e.cut(87.86, Src("rite_final", [(87.86, 0.1), (89.31, 2.45)]), FINAL, zoom0=1.05, zoom1=1.12)
    hit(e, 87.86, 0.9)
    e.cut(89.31, impact("rite_final", 89.31, 2.62, post=1.0), FINAL, zoom0=1.1, zoom1=1.05)
    hit(e, 89.31, 1.8, 1.0, (1, 1, 1), 0.3)
    e.cut(90.76, at("rite_final", 90.76, 4.6, 1.0), FINAL, zoom0=1.05, zoom1=1.1)
    hit(e, 90.76, 0.9, 0.3, (1.0, 0.45, 0.1), 0.18)
    e.cut(92.20, at("rite_final", 92.20, 6.1, 0.9), FINAL, zoom0=1.06, zoom1=1.12)
    hit(e, 92.20, 0.9)
    e.cut(93.65, at("mask_final", 93.65, 0.3, 0.55), CLOSE, zoom0=1.04, zoom1=1.1)
    hit(e, 93.65, 1.2, 0.4, (1.0, 0.45, 0.1), 0.2)
    # Отличная реакция: ОТЛИЧНО, ТЫ СЛИШКОМ МЕДЛИТЕЛЕН, удар в спину.
    e.cut(95.10, Src("qte_quarter", [(95.10, 0.45), (95.82, 1.3), (96.54, 2.75)]), FINAL, zoom0=1.05, zoom1=1.08, handheld=1.0, pan=(0, -34, 0, -38))
    hit(e, 95.10, 0.9)
    hit(e, 95.82, 1.3, 0.3)
    e.cut(96.54, at("ff_wave_a", 96.54, WAVE, 1.0), FINAL, zoom0=1.05, zoom1=1.1)
    hit(e, 96.54, 1.2, 0.3, (1.0, 0.45, 0.1), 0.18)
    e.cut(98.00, at("ff_orbit", 98.00, 0.4, 0.9), FINAL, zoom0=1.04, zoom1=1.08)
    hit(e, 98.00, 0.9)
    e.cut(99.44, at("ff_swing", 99.44, 3.2, 1.0), FINAL, zoom0=1.1, zoom1=1.14)
    hit(e, 99.44, 0.7)
    e.cut(snap(99.44 + BEAT * 2), at("ff_swing", 99.44 + BEAT * 2, 0.7, 1.0), FINAL, zoom0=1.1, zoom1=1.14)
    e.cut(100.89, at("ff_wave_b", 100.89, WAVE, 0.9), FINAL, zoom0=1.05, zoom1=1.08)
    hit(e, 100.89, 1.1, 0.3, (1.0, 0.45, 0.1), 0.18)
    e.cut(snap(100.89 + BEAT * 2), at("mask_final", 100.89 + BEAT * 2, 4.0, 0.6), CLOSE, zoom0=1.05, zoom1=1.12)


# ---------------------------------------------------------------- 1:42 стоп: смерть и «это ещё не конец»

def stop(e):
    tl = e.tl
    tl.bars += [(102.2, BAR), (102.33, 215), (105.1, 215), (105.23, BAR)]
    e.cut(102.33, Src("rampage_follow_b", [(102.33, 0.8), (102.88, 1.6)]), RAMPAGE, zoom0=1.05, zoom1=1.1, handheld=0.5)
    tl.flash(102.33, 0.3, (0, 0, 0), 1.0)
    hit(e, 102.88, 1.0, 0.7, (1.0, 0.1, 0.08), 0.14)
    e.cut(102.88, at("rampage_follow_a", 102.88, 2.2, 0.6), RAMPAGE, zoom0=1.25, zoom1=1.3)
    line(e, 102.95, 104.45, "Это ещё не конец.", size=58, y=0.3)
    hit(e, 103.42, 1.0, 0.7, (1.0, 0.1, 0.08), 0.14)
    e.cut(103.42, at("rampage_follow_b", 103.42, 3.4, 0.6), RAMPAGE, zoom0=1.1, zoom1=1.15)
    hit(e, 104.50, 1.2, 0.9, (1, 1, 1), 0.14)
    # Сквозная дыра открывается.
    e.cut(104.50, at("rampage_hole", 104.50, 6.3, 0.8), RAMPAGE, zoom0=1.05, zoom1=1.16, center=(960, 560))
    hit(e, 104.86, 1.2, 0.5, (1.0, 0.1, 0.08), 0.14)


# ---------------------------------------------------------------- 1:45 дроп 3: Пустой забрал тело

def drop3(e):
    # Бой длится ровно столько же, сколько дроп: идём один в один, меняются только камеры.
    fight0 = 8.75
    plan = [(105.23, "rampage_follow_b"), (106.66, "rampage_follow_a"), (108.11, "rampage_follow_b"), (109.55, "rampage_follow_a"), (110.26, "rampage_game"), (110.99, "rampage_follow_b"),
            (112.43, "rampage_follow_a"), (113.15, "rampage_follow_b"), (113.88, "rampage_follow_a"), (114.60, "rampage_follow_b"), (115.32, "rampage_game"), (116.04, "rampage_follow_a"), (116.76, "rampage_follow_b"), (117.48, "rampage_follow_a")]
    for t, take in plan:
        wide = take == "rampage_game"
        zoom = 1.28 if wide else 1.06
        e.cut(t, at(take, t, fight0 + (t - 105.23), 1.0), RAMPAGE_WIDE if wide else RAMPAGE, zoom0=zoom, zoom1=zoom + 0.05)
    hit(e, 105.23, 1.7, 1.0, (1, 1, 1), 0.28)
    for t in (106.66, 108.11, 109.55, 110.99, 112.43, 113.88, 115.32, 116.76):
        hit(e, t, 0.75, 0.15)


# ---------------------------------------------------------------- 1:58 тишина: окружили, луна, глаз

def breakdown(e):
    tl = e.tl
    tl.bars += [(118.1, BAR), (128.1, 205), (128.28, 205), (128.30, 112)]
    # Толпа смыкается.
    e.cut(118.21, Src("rampage_follow_b", [(118.21, 21.9), (119.65, 23.1)]), RAMPAGE, zoom0=1.05, zoom1=1.12)
    e.cut(119.65, Src("rampage_wide", [(119.65, 23.1), (121.09, 24.6)]), RAMPAGE_WIDE, zoom0=1.08, zoom1=1.2)
    e.cut(121.09, Src("rampage_follow_a", [(121.09, 24.7), (122.54, 26.4)]), RAMPAGE, zoom0=1.08, zoom1=1.16)
    # Удар: луна.
    e.cut(122.54, Src("rampage_game", [(122.54, 26.6), (snap(126.14), 28.15)]), RAMPAGE, zoom0=1.05, zoom1=1.14)
    hit(e, 122.54, 1.6, 0.8, (1.0, 0.15, 0.1), 0.34)
    # Лицо. Глаз вспыхивает.
    e.cut(snap(126.14), Src("rampage_game", [(snap(126.14), 28.3), (127.94, 30.05), (128.30, 30.3)]), CLOSE, zoom0=1.05, zoom1=1.24, center=(960, 480))
    hit(e, 127.20, 0.6, 0.25, (1.0, 0.4, 0.1), 0.2)


# ---------------------------------------------------------------- 2:08 дроп 4: всех подряд

def drop4(e):
    tl = e.tl
    # Толчок: кольца, все разлетаются.
    e.cut(128.30, impact("rampage_follow_b", 128.30, 30.35, post=0.45), RAMPAGE, zoom0=1.14, zoom1=1.08)
    hit(e, 128.30, 2.0, 1.0, (1, 1, 1), 0.34)
    # Прыжки: 2,4 секунды игры растянуты на замедлении с разных камер.
    e.cut(129.73, Src("rampage_game", [(129.73, 30.8), (131.17, 31.5)]), RAMPAGE_WIDE, zoom0=1.32, zoom1=1.38)
    hit(e, 129.73, 1.1, 0.25)
    e.cut(131.17, Src("rampage_follow_a", [(131.17, 31.0), (132.61, 31.8)]), RAMPAGE, zoom0=1.06, zoom1=1.1)
    hit(e, 131.17, 1.0, 0.25)
    e.cut(132.61, Src("rampage_follow_b", [(132.61, 31.9), (134.05, 32.6)]), RAMPAGE, zoom0=1.06, zoom1=1.1)
    hit(e, 132.61, 1.0, 0.25)
    e.cut(134.05, Src("rampage_game", [(134.05, 31.9), (135.49, 32.7)]), RAMPAGE_WIDE, zoom0=1.3, zoom1=1.36)
    hit(e, 134.05, 1.0, 0.25)
    e.cut(135.49, Src("rampage_wide", [(135.49, 32.4), (136.92, 33.1)]), RAMPAGE_WIDE, zoom0=1.06, zoom1=1.1)
    hit(e, 135.49, 1.0, 0.25)
    # Всё, что было, вспышками: по две доли.
    t = 136.92
    picks = [("mask_final", 1.0, CLOSE), ("x_split_b", HITS["split"] + 0.1, STEEL), ("sword_burning", 2.4, EYE), ("h_fight", 13.0, HOLLOW), ("rite_final", 2.85, FINAL), ("h_wave_a", WAVE + 0.1, HOLLOW),
             ("ff_wave_a", WAVE + 0.1, FINAL), ("mask_hollow", 1.2, CLOSE), ("x_waist_b", HITS["waist"] - 0.05, STEEL), ("ff_orbit", 1.8, FINAL), ("break_game", 7.9, VOID), ("rampage_game", 29.8, CLOSE),
             ("x_behead_b", HITS["behead"] + 0.4, STEEL), ("ff_wave_b", WAVE + 0.1, FINAL), ("rite_final", 4.9, FINAL), ("rampage_game", 27.3, RAMPAGE)][:12]
    for i, (take, s, look) in enumerate(picks):
        if i < 8:
            ct = snap(t + i * BEAT * 2)
            e.cut(ct, at(take, ct, s, 0.8), look, zoom0=1.08, zoom1=1.04)
            hit(e, ct, 0.7, 0.12)
    for i, (take, s, look) in enumerate(picks[8:]):
        ct = snap(t + BEAT * 16 + i * BEAT)
        e.cut(ct, at(take, ct, s, 0.8), look, zoom0=1.1, zoom1=1.06)
        hit(e, ct, 0.5, 0.1)
    # Он уже позади всех. Жёлтые разрезы светятся. Меч в руке.
    e.cut(144.11, at("rampage_follow_a", 144.11, 33.75, 0.45), RAMPAGE, zoom0=1.1, zoom1=1.18)
    hit(e, 144.11, 1.3, 0.5, (1.0, 0.85, 0.3), 0.22)
    e.cut(145.54, at("rampage_follow_b", 145.54, 34.3, 0.5), RAMPAGE, zoom0=1.05, zoom1=1.1)
    e.tl.cards.append(Card(145.72, 146.9, text_layer("Жалкие.", "georgiab.ttf", 86, tracking=6, rgb=(255, 244, 235)), x=0.29, y=0.26, scale0=0.98, scale1=1.03, fade_in=0.05, fade_out=0.2, glow=0.8))
    # Взмах.
    e.cut(146.97, Src("rampage_follow_b", [(146.97, 35.0), (148.10, 35.5), (148.41, 35.85)]), RAMPAGE, zoom0=1.12, zoom1=1.3)


# ---------------------------------------------------------------- 2:28 развязка

def outro(e):
    tl = e.tl
    # Взмах: все разом.
    e.cut(148.41, impact("rampage_wide", 148.41, 35.85, post=0.4), RAMPAGE_WIDE, zoom0=1.1, zoom1=1.05)
    hit(e, 148.41, 2.2, 1.0, (1, 1, 1), 0.3)
    # Вокруг только куски. Маска разбивается, под ней снова он.
    e.cut(149.87, Src("rampage_follow_a", [(149.87, 37.5), (150.6, 38.5), (151.30, 39.4)]), RAMPAGE, zoom0=1.1, zoom1=1.26)
    hit(e, 149.87, 0.9)
    hit(e, 150.6, 1.1, 0.5, (1, 1, 1), 0.16)
    tl.fade(150.5, 151.30, 0.0, 1.0)
    # Название.
    e.cut(151.30, at("rampage_follow_a", 151.30, 39.5, 0.5), RAMPAGE, zoom0=1.04, zoom1=1.05)
    tl.fade(151.30, DURATION + 1.0, 1.0, 1.0)
    title(e, 151.30, 153.16, fade_out=0.9)
    hit(e, 151.30, 1.0, 0.9, (1, 1, 1), 0.3)
