"""Готовые кадры по времени трека из посчитанных планов: python frames.py выход.jpg столбцов ширина t1 t2 ..."""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
if os.environ.get("CLIP_PYLIBS"):
    sys.path.insert(0, os.environ["CLIP_PYLIBS"])
import cv2  # noqa: E402
import numpy as np  # noqa: E402

import edl  # noqa: E402
import engine  # noqa: E402

out, cols, width = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
times = [float(v) for v in sys.argv[4:]]
shots, _ = edl.build()
h = width * 9 // 16
images = []
for t in times:
    index = next(i for i, s in enumerate(shots) if s.t0 <= t < s.t1)
    seg = "ref/clip/segs/seg_%04d.mkv" % index
    raw = subprocess.run([engine.FFMPEG, "-hide_banner", "-loglevel", "error", "-ss", "%.4f" % (t - shots[index].t0), "-i", seg, "-frames:v", "1", "-vf", "scale=%d:%d:flags=area" % (width, h), "-f", "rawvideo", "-pix_fmt", "bgr24", "-"], capture_output=True).stdout
    img = np.frombuffer(raw, np.uint8).reshape(h, width, 3).copy() if len(raw) >= width * h * 3 else np.zeros((h, width, 3), np.uint8)
    cv2.putText(img, "%.2f %s" % (t, shots[index].name), (6, 18), cv2.FONT_HERSHEY_SIMPLEX, 0.42, (120, 255, 255), 1, cv2.LINE_AA)
    images.append(img)
while len(images) % cols:
    images.append(np.zeros((h, width, 3), np.uint8))
cv2.imwrite(out, np.vstack([np.hstack(images[i:i + cols]) for i in range(0, len(images), cols)]), [cv2.IMWRITE_JPEG_QUALITY, 90])
print(out)
