"""Проверка готового ролика: склейки против долей трека, резкие провалы и пересветы, яркость по частям, листы кадров. python verify.py ролик.mp4"""
import json
import os
import subprocess
import sys

if os.environ.get("CLIP_PYLIBS"):
    sys.path.insert(0, os.environ["CLIP_PYLIBS"])
import cv2  # noqa: E402
import numpy as np  # noqa: E402

FFMPEG = os.environ.get("CLIP_FFMPEG", "ffmpeg")
path = sys.argv[1]
D = json.load(open("ref/clip/beats.json"))
beats = np.array(D["beats"])
W, H = 96, 54
raw = subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-i", path, "-vf", "scale=%d:%d:flags=area" % (W, H), "-f", "rawvideo", "-pix_fmt", "gray", "-"], capture_output=True).stdout
frames = np.frombuffer(raw, np.uint8).reshape(-1, H, W).astype(np.float32)
print("кадров", len(frames), "секунд %.3f" % (len(frames) / 60.0))
# Полосы не считаем: только картинка.
pic = frames[:, 8:46]
diff = np.abs(pic[1:] - pic[:-1]).mean(axis=(1, 2))
base = np.convolve(diff, np.ones(31) / 31, mode="same")
cuts = [i + 1 for i in range(2, len(diff) - 2) if diff[i] > max(10.0, base[i] * 3.0) and diff[i] == diff[i - 2:i + 3].max()]
times = np.array(cuts) / 60.0
errors = np.array([np.min(np.abs(beats - t)) for t in times])
print("резких склеек %d; от ближайшей доли: среднее %.1f мс, 90%% в пределах %.1f мс, хуже 40 мс: %d" % (len(times), errors.mean() * 1000, np.percentile(errors, 90) * 1000, int((errors > 0.04).sum())))
print("далеко от доли:", ["%.2f" % t for t, e in zip(times, errors) if e > 0.04][:30])
brightness = pic.mean(axis=(1, 2))
print("средняя яркость кадра по частям (0-255):")
for name, a, b in (("вступление", 0, 23.64), ("дроп1", 23.64, 44.16), ("маска", 44.16, 47.1), ("пустой", 47.1, 67.53), ("яхве", 67.53, 82.07), ("дроп2", 82.07, 102.33), ("стоп", 102.33, 105.23), ("дроп3", 105.23, 118.21), ("луна", 118.21, 128.3), ("дроп4", 128.3, 148.41), ("конец", 148.41, 153.16)):
    seg = brightness[int(a * 60):int(b * 60)]
    print("  %-11s средняя %5.1f  мин %5.1f  макс %5.1f" % (name, seg.mean(), seg.min(), seg.max()))
white = brightness > 170
runs, start = [], None
for i, v in enumerate(white):
    if v and start is None:
        start = i
    if not v and start is not None:
        if i - start >= 12:
            runs.append((round(start / 60.0, 2), round((i - start) / 60.0, 2)))
        start = None
print("белые вспышки дольше 0,2 с:", runs)
black = brightness < 2.0
runs, start = [], None
for i, v in enumerate(black):
    if v and start is None:
        start = i
    if not v and start is not None:
        if i - start >= 18:
            runs.append((round(start / 60.0, 2), round((i - start) / 60.0, 2)))
        start = None
print("чёрное дольше 0,3 с:", runs)
