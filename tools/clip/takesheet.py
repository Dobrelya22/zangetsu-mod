"""Лист кадров дубля с метками времени: python takesheet.py дубль шаг_в_секундах [столбцов] [от] [до] [ширина]"""
import os
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

_libs = os.environ.get("CLIP_PYLIBS")
if _libs:
    sys.path.insert(0, _libs)
import cv2  # noqa: E402
import numpy as np  # noqa: E402

FFMPEG = os.environ.get("CLIP_FFMPEG", "ffmpeg")
take = sys.argv[1]
step = float(sys.argv[2])
cols = int(sys.argv[3]) if len(sys.argv) > 3 else 8
start = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0
path = "ref/clip/takes/%s.mkv" % take
dur = float(subprocess.run([FFMPEG.replace("ffmpeg", "ffprobe"), "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path], capture_output=True, text=True).stdout.strip())
end = min(dur, float(sys.argv[5])) if len(sys.argv) > 5 else dur
w = int(sys.argv[6]) if len(sys.argv) > 6 else 400
h = w * 9 // 16


def grab(t):
    raw = subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-ss", "%.4f" % t, "-i", path, "-frames:v", "1", "-vf", "scale=%d:%d" % (w, h), "-f", "rawvideo", "-pix_fmt", "bgr24", "-"], capture_output=True).stdout
    if len(raw) < w * h * 3:
        return np.zeros((h, w, 3), np.uint8)
    img = np.frombuffer(raw, np.uint8).reshape(h, w, 3).copy()
    cv2.putText(img, "%.2f" % t, (5, 20), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 3, cv2.LINE_AA)
    cv2.putText(img, "%.2f" % t, (5, 20), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (120, 255, 255), 1, cv2.LINE_AA)
    return img


times = list(np.arange(start, end - 0.01, step))
with ThreadPoolExecutor(6) as pool:
    frames = list(pool.map(grab, times))
while len(frames) % cols:
    frames.append(np.zeros((h, w, 3), np.uint8))
sheet = np.vstack([np.hstack(frames[i:i + cols]) for i in range(0, len(frames), cols)])
os.makedirs("ref/clip/sheets", exist_ok=True)
out = "ref/clip/sheets/%s.jpg" % take
cv2.imwrite(out, sheet, [cv2.IMWRITE_JPEG_QUALITY, 85])
print(out, len(times), "кадров", sheet.shape[1], "x", sheet.shape[0])
