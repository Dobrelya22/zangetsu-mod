"""Где в дубле удар: самый резкий рост красного и изменения кадра. python hits.py дубль [дубль...]"""
import json
import os
import subprocess
import sys

_libs = os.environ.get("CLIP_PYLIBS")
if _libs:
    sys.path.insert(0, _libs)
import numpy as np  # noqa: E402

FFMPEG = os.environ.get("CLIP_FFMPEG", "ffmpeg")
W, H = 160, 90
out = {}
for take in sys.argv[1:]:
    path = "ref/clip/takes/%s.mkv" % take
    raw = subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-i", path, "-vf", "fps=120,scale=%d:%d" % (W, H), "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], capture_output=True).stdout
    a = np.frombuffer(raw, np.uint8).reshape(-1, H, W, 3).astype(np.float32)
    diff = np.abs(a[4:] - a[:-4]).mean(axis=(1, 2, 3))
    # Красные брызги: насколько красного прибавилось.
    red = np.clip(a[..., 0] - np.maximum(a[..., 1], a[..., 2]) - 40.0, 0.0, None).mean(axis=(1, 2))
    dred = np.clip(red[4:] - red[:-4], 0.0, None)
    score = diff / max(1e-3, diff.max()) + dred / max(1e-3, dred.max())
    start = int(0.5 * 120)
    peak = start + int(np.argmax(score[start:]))
    # Первый кадр, где рост уже заметен: это и есть касание.
    level = score[peak] * 0.5
    first = peak
    while first > start and score[first - 1] > level:
        first -= 1
    t = (first + 4) / 120.0
    out[take] = round(t, 3)
    print(take, "удар %.3f, пик %.3f" % (t, (peak + 4) / 120.0), flush=True)
os.makedirs("ref/clip", exist_ok=True)
old = {}
if os.path.exists("ref/clip/hits.json"):
    old = json.load(open("ref/clip/hits.json"))
old.update(out)
json.dump(old, open("ref/clip/hits.json", "w"), indent=0)
