"""
Сборка клипа. Запуск: python render.py [--music файл.wav --out клип.mp4] [--sheet папка] [--only A-B]
Планы считаются параллельно и складываются в ref/clip/segs; пересчитывается только то, что изменилось.
"""
import argparse
import hashlib
import os
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

if os.environ.get("CLIP_PYLIBS"):
    sys.path.insert(0, os.environ["CLIP_PYLIBS"])
import cv2  # noqa: E402
import numpy as np  # noqa: E402

import edl  # noqa: E402
import engine  # noqa: E402

TAKES = "ref/clip/takes"
SEGS = "ref/clip/segs"


def signature(shot, tl):
    """Отпечаток плана: всё, что влияет на его кадры."""
    h = hashlib.sha1()
    src = shot.src
    h.update(repr((shot.t0, shot.t1, src.take, [float(v) for v in src.tx], [float(v) for v in src.ty], shot.shutter, shot.zoom0, shot.zoom1, shot.pan, shot.roll, shot.center, shot.handheld)).encode())
    if shot.over is not None:
        h.update(repr((shot.over.take, list(shot.over.tx), list(shot.over.ty))).encode())
    g = shot.look
    h.update(repr({k: (v.tolist() if hasattr(v, "tolist") else v) for k, v in vars(g).items()}).encode())
    for f in range(engine.frame_of(shot.t0), engine.frame_of(shot.t1)):
        t = f / engine.FPS
        h.update(repr((tuple(round(v, 5) for v in tl.state(t)), round(tl.bar_at(t), 2))).encode())
        if shot.over_alpha is not None:
            h.update(repr(round(shot.over_alpha(t), 4)).encode())
    for e in tl.flashes:
        if shot.t0 - 1.0 <= e[0] <= shot.t1:
            h.update(repr((e[0], e[1], e[2].tolist(), e[3], e[4])).encode())
    for e in tl.fades:
        if e[0] <= shot.t1 and e[1] >= shot.t0:
            h.update(repr((e[0], e[1], e[2], e[3], e[4].tolist())).encode())
    for c in tl.cards:
        if c.t0 <= shot.t1 and c.t1 >= shot.t0:
            h.update(repr((c.t0, c.t1, c.x, c.y, c.scale0, c.scale1, c.fade_in, c.fade_out, c.glow, c.cut, c.layer.shape, float(c.layer[..., 3].sum()))).encode())
    h.update(b"v2")
    return h.hexdigest()


def worker(i):
    shots, tl = edl.build()
    shot = shots[i]
    engine.render_shot(shot, tl, TAKES, os.path.join(SEGS, "seg_%04d.tmp.mkv" % i))
    os.replace(os.path.join(SEGS, "seg_%04d.tmp.mkv" % i), os.path.join(SEGS, "seg_%04d.mkv" % i))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--worker", type=int)
    ap.add_argument("--music")
    ap.add_argument("--out")
    ap.add_argument("--sheet")
    ap.add_argument("--jobs", type=int, default=12)
    ap.add_argument("--only", help="считать только этот кусок трека, например 40-60")
    ap.add_argument("--retake", default="", help="дубли переснялись: через запятую, планы из них считать заново")
    args = ap.parse_args()
    if args.worker is not None:
        cv2.setNumThreads(2)
        worker(args.worker)
        return
    shots, tl = edl.build()
    for a, b in zip(shots, shots[1:]):
        if engine.frame_of(a.t1) != engine.frame_of(b.t0):
            raise SystemExit("дырка между планами: %s %.3f и %s %.3f" % (a.name, a.t1, b.name, b.t0))
    print("планов", len(shots), "кадров", engine.frame_of(shots[-1].t1) - engine.frame_of(shots[0].t0), "длина %.2f" % shots[-1].t1)
    missing = sorted({s.src.take for s in shots if not os.path.exists(os.path.join(TAKES, s.src.take + ".mkv"))})
    if missing:
        raise SystemExit("нет дублей: %s" % missing)
    if args.sheet:
        return sheet(shots, tl, args.sheet, args.only)
    os.makedirs(SEGS, exist_ok=True)
    lo, hi = -1e9, 1e9
    if args.only:
        lo, hi = (float(v) for v in args.only.split("-"))
    todo = []
    retake = set(args.retake.split(","))
    for i, s in enumerate(shots):
        path = os.path.join(SEGS, "seg_%04d.mkv" % i)
        sig = signature(s, tl)
        old = open(path + ".sig").read() if os.path.exists(path) and os.path.exists(path + ".sig") else ""
        if (old != sig or s.src.take in retake) and s.t1 > lo and s.t0 < hi:
            todo.append((i, sig))
    print("считать планов:", len(todo))
    start = time.time()
    env = dict(os.environ)

    def job(item):
        i, sig = item
        path = os.path.join(SEGS, "seg_%04d.mkv" % i)
        result = subprocess.run([sys.executable, "-I", os.path.abspath(__file__), "--worker", str(i)], env=env)
        if result.returncode == 0:
            open(path + ".sig", "w").write(sig)
        else:
            print("план упал:", i, shots[i].name, flush=True)
        return result.returncode

    with ThreadPoolExecutor(args.jobs) as pool:
        codes = list(pool.map(job, todo))
    print("посчитано за %.0f с, ошибок %d" % (time.time() - start, sum(1 for c in codes if c)), flush=True)
    if args.out and not any(codes):
        final(shots, args.music, args.out)


def final(shots, music, out):
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    cmd = [engine.FFMPEG, "-hide_banner", "-loglevel", "warning", "-y", "-f", "rawvideo", "-pix_fmt", "bgr24", "-video_size", "%dx%d" % (engine.W, engine.H), "-framerate", str(engine.FPS), "-i", "-"]
    if music:
        cmd += ["-i", music, "-map", "0:v", "-map", "1:a", "-c:a", "aac", "-b:a", "320k", "-ar", "48000"]
    cmd += ["-vf", "scale=in_range=pc:out_range=tv:out_color_matrix=bt709:flags=accurate_rnd+full_chroma_int+lanczos,format=yuv420p",
            "-c:v", "libx264", "-preset", "slow", "-crf", "15", "-profile:v", "high", "-x264-params", "aq-mode=3:aq-strength=1.0:deblock=-1,-1:keyint=120",
            "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709", "-color_range", "tv", "-movflags", "+faststart", out]
    start = time.time()
    encoder = subprocess.Popen(cmd, stdin=subprocess.PIPE, bufsize=1 << 24)
    size = engine.W * engine.H * 3
    total = 0
    for i, shot in enumerate(shots):
        reader = subprocess.Popen([engine.FFMPEG, "-hide_banner", "-loglevel", "error", "-i", os.path.join(SEGS, "seg_%04d.mkv" % i), "-f", "rawvideo", "-pix_fmt", "bgr24", "-"], stdout=subprocess.PIPE, bufsize=1 << 24)
        count = 0
        while True:
            frame = reader.stdout.read(size)
            if len(frame) < size:
                break
            encoder.stdin.write(frame)
            count += 1
        reader.wait()
        expected = engine.frame_of(shot.t1) - engine.frame_of(shot.t0)
        if count != expected:
            raise SystemExit("план %d: %d кадров вместо %d" % (i, count, expected))
        total += count
    encoder.stdin.close()
    encoder.wait()
    print("кадров %d, сжато за %.0f с: %.0f МБ" % (total, time.time() - start, os.path.getsize(out) / 1e6))


def sheet(shots, tl, folder, only):
    """Лист проверки: начало, середина и конец каждого плана, параллельно."""
    os.makedirs(folder, exist_ok=True)
    lo, hi = -1e9, 1e9
    if only:
        lo, hi = (float(v) for v in only.split("-"))
    picked = [s for s in shots if s.t1 > lo and s.t0 < hi]
    times = []
    for s in picked:
        d = s.t1 - s.t0
        for k in (0.04, 0.5, 0.93):
            times.append((s, s.t0 + d * k))

    def one(item):
        s, t = item
        img = engine.still(s, tl, TAKES, t)
        small = cv2.resize(img, (480, 270), interpolation=cv2.INTER_AREA)
        cv2.putText(small, "%.2f %s" % (t, s.name), (6, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (120, 255, 255), 1, cv2.LINE_AA)
        return small

    with ThreadPoolExecutor(14) as pool:
        images = list(pool.map(one, times))
    rows = [np.hstack(images[i:i + 12]) if len(images[i:i + 12]) == 12 else np.hstack(images[i:i + 12] + [np.zeros((270, 480, 3), np.uint8)] * (12 - len(images[i:i + 12]))) for i in range(0, len(images), 12)]
    page = 0
    for i in range(0, len(rows), 6):
        name = os.path.join(folder, "sheet_%s_%02d.jpg" % ((only or "all").replace("-", "_"), page))
        cv2.imwrite(name, np.vstack(rows[i:i + 6]), [cv2.IMWRITE_JPEG_QUALITY, 88])
        print(name)
        page += 1


if __name__ == "__main__":
    main()
