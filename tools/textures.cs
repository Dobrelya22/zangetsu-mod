// Текстуры мода «Зангецу», нарисованные кодом. Компилируется из tools/textures.ps1 (C# 5).
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;

public static class ZangetsuTextures
{
    // ------------------------------------------------------------------ общее

    static Color C(int r, int g, int b) { return Color.FromArgb(255, Cl(r), Cl(g), Cl(b)); }
    static Color CA(int a, int r, int g, int b) { return Color.FromArgb(Cl(a), Cl(r), Cl(g), Cl(b)); }
    static int Cl(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
    static int Cl(double v) { return Cl((int)Math.Round(v)); }
    static double Sat(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    static Color Mix(Color a, Color b, double t)
    {
        t = Sat(t);
        return Color.FromArgb(Cl(a.A + (b.A - a.A) * t), Cl(a.R + (b.R - a.R) * t), Cl(a.G + (b.G - a.G) * t), Cl(a.B + (b.B - a.B) * t));
    }

    static Color Mul(Color a, double k) { return Color.FromArgb(a.A, Cl(a.R * k), Cl(a.G * k), Cl(a.B * k)); }

    /** Детерминированный шум 0..1 для пикселя. */
    static double Hash(int x, int y, int seed)
    {
        unchecked
        {
            uint h = (uint)(x * 374761393 + y * 668265263 + seed * 982451653);
            h = (h ^ (h >> 13)) * 1274126177u;
            h ^= h >> 16;
            return (h & 0xFFFFFF) / 16777216.0;
        }
    }

    class Canvas
    {
        public readonly int W, H;
        public readonly Color[,] Px;
        public Canvas(int w, int h) { W = w; H = h; Px = new Color[w, h]; Clear(); }
        public void Clear() { for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) Px[x, y] = Color.FromArgb(0, 0, 0, 0); }
        public bool Filled(int x, int y) { return x >= 0 && y >= 0 && x < W && y < H && Px[x, y].A > 0; }
        public void Set(int x, int y, Color c) { if (x >= 0 && y >= 0 && x < W && y < H) Px[x, y] = c; }
        public void Fill(int x0, int y0, int w, int h, Color c) { for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) Set(x, y, c); }
        public void Save(string path)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(path));
            using (var bmp = new Bitmap(W, H, PixelFormat.Format32bppArgb))
            {
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) bmp.SetPixel(x, y, Px[x, y]);
                bmp.Save(path, ImageFormat.Png);
            }
        }
        public void SavePreview(string path, int scale, Color bg)
        {
            if (path == null) return;
            Directory.CreateDirectory(Path.GetDirectoryName(path));
            using (var bmp = new Bitmap(W * scale, H * scale, PixelFormat.Format32bppArgb))
            {
                for (int y = 0; y < H * scale; y++)
                    for (int x = 0; x < W * scale; x++)
                    {
                        Color c = Px[x / scale, y / scale];
                        Color b = ((x / (scale * 4) + y / (scale * 4)) % 2 == 0) ? bg : Mul(bg, 0.85);
                        bmp.SetPixel(x, y, Mix(Color.FromArgb(255, b.R, b.G, b.B), Color.FromArgb(255, c.R, c.G, c.B), c.A / 255.0));
                    }
                bmp.Save(path, ImageFormat.Png);
            }
        }
    }

    static string previewDir;

    static void Out(Canvas c, string assets, string rel, int previewScale)
    {
        c.Save(Path.Combine(assets, rel));
        if (!string.IsNullOrEmpty(previewDir))
        {
            c.SavePreview(Path.Combine(previewDir, rel.Replace('/', '_')), previewScale, C(120, 130, 150));
        }
    }

    public static void Run(string assets, string preview)
    {
        previewDir = preview;
        // Меч — объёмная модель со своим атласом; глаз — отдельные 3D-детали; плоская картинка — для частиц.
        SwordPaint sp = PaintSword();
        Out(SwordAtlas(), assets, "textures/entity/zangetsu_model.png", 6);
        Out(SwordIcon(sp), assets, "textures/item/zangetsu_icon.png", 8);
        Out(EyeParts(), assets, "textures/entity/zangetsu_eye.png", 4);
        Out(White(), assets, "textures/misc/white.png", 16);
        Out(Icon(), assets, "icon.png", 4);
        Out(Spirit(), assets, "textures/entity/spirit.png", 4);
        Out(SpiritEyes(), assets, "textures/entity/spirit_eyes.png", 4);
        for (int crack = 0; crack <= 3; crack++)
        {
            Out(HollowMask(crack), assets, "textures/entity/hollow_mask_" + crack + ".png", 8);
        }
        Out(MaskIcon(true), assets, "textures/gui/mask_full.png", 8);
        Out(MaskIcon(false), assets, "textures/gui/mask_empty.png", 8);
        Out(MaskIconCracks(), assets, "textures/gui/mask_cracks.png", 8);
        Out(HollowMaskEyes(), assets, "textures/entity/hollow_mask_eyes.png", 8);
        Out(CrescentMoon(), assets, "textures/environment/crescent_moon.png", 6);
        Out(EnderCut(), assets, "textures/entity/ender_cut.png", 16);
    }

    /** Закрасить прямоугольник [x0, x1) × [y0, y1) цветом с лёгким шумом. */
    static void Paint(Canvas c, int x0, int y0, int x1, int y1, Color col, double noise, int seed)
    {
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                c.Set(x, y, Mul(col, 1.0 + (Hash(x, y, seed) - 0.5) * noise));
    }

    /** Вертикальный градиент в прямоугольнике. */
    static void Gradient(Canvas c, int x0, int y0, int x1, int y1, Color top, Color bottom, double noise, int seed)
    {
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                c.Set(x, y, Mul(Mix(top, bottom, (y - y0 + 0.5) / Math.Max(1, y1 - y0)), 1.0 + (Hash(x, y, seed) - 0.5) * noise));
    }

    // ------------------------------------------------------------------ фигура в небе

    /**
     * Текстура таинственной фигуры (128×128). Раскладка совпадает с ZangetsuSpiritModel:
     * торс (0,0), голова (0,24), рога (40..64,0), руки (64,0), когти (82,0), ноги (88,0),
     * плащ (0,42), кости крыльев (0,64), перепонки (0,68), хвост (64,32).
     */
    static Canvas Spirit()
    {
        var c = new Canvas(128, 128);
        Color skin = C(24, 19, 30);
        Paint(c, 0, 0, 36, 24, skin, 0.35, 1);
        // Рёбра на груди (передняя грань торса: 6..18 × 6..24).
        for (int y = 9; y < 20; y += 3) for (int x = 8; x < 16; x++) if (x != 11 && x != 12) c.Set(x, y, C(48, 38, 58));
        Paint(c, 0, 24, 36, 42, skin, 0.3, 2);
        // Лицо (9..18 × 33..42): провалы глаз и пасть с зубами.
        Paint(c, 10, 35, 13, 37, C(6, 4, 8), 0, 0);
        Paint(c, 14, 35, 17, 37, C(6, 4, 8), 0, 0);
        for (int x = 10; x < 17; x++) { c.Set(x, 39, C(6, 4, 8)); c.Set(x, 40, x % 2 == 0 ? C(226, 220, 204) : C(6, 4, 8)); }
        // Рога: от кости у основания к чёрным кончикам.
        Gradient(c, 40, 0, 52, 9, C(196, 188, 168), C(226, 220, 202), 0.12, 3);
        Gradient(c, 52, 0, 60, 8, C(120, 112, 100), C(200, 192, 172), 0.12, 4);
        Paint(c, 60, 0, 64, 6, C(28, 24, 24), 0.2, 5);
        // Руки и ноги: тёмные, к кистям — багровые.
        Gradient(c, 64, 0, 82, 25, skin, C(46, 10, 16), 0.3, 6);
        Paint(c, 82, 0, 86, 5, C(210, 204, 186), 0.15, 7);
        Gradient(c, 88, 0, 108, 23, skin, C(12, 10, 14), 0.3, 8);
        // Плащ: снаружи чёрный, изнутри — алая подкладка, низ рваный.
        Paint(c, 0, 42, 42, 61, C(13, 13, 16), 0.3, 9);
        Paint(c, 1, 43, 14, 61, C(104, 12, 20), 0.3, 10);
        Paint(c, 36, 43, 42, 61, C(104, 12, 20), 0.3, 11);
        Tatter(c, 0, 42, 61, 4, 12);
        // Кости крыльев и перепонки с прожилками и дырами.
        Paint(c, 0, 64, 67, 67, C(52, 44, 46), 0.25, 13);
        Membrane(c, 0, 68, 32, 88, 14);
        Membrane(c, 32, 68, 60, 92, 15);
        // Хвост и его наконечник.
        Gradient(c, 64, 32, 82, 50, skin, C(40, 10, 14), 0.3, 16);
        for (int x = 70; x < 82; x += 3) c.Set(x, 32, C(200, 192, 172));
        Paint(c, 84, 32, 102, 37, C(70, 8, 14), 0.25, 17);
        return c;
    }

    static void Tatter(Canvas c, int x0, int x1, int bottom, int depth, int seed)
    {
        for (int x = x0; x < x1; x++)
        {
            int cut = (int)(Hash(x, 0, seed) * depth);
            for (int k = 0; k < cut; k++) c.Set(x, bottom - 1 - k, Color.FromArgb(0, 0, 0, 0));
        }
    }

    static void Membrane(Canvas c, int x0, int y0, int x1, int y1, int seed)
    {
        int w = x1 - x0, h = y1 - y0;
        for (int y = y0; y < y1; y++)
        {
            for (int x = x0; x < x1; x++)
            {
                double fx = (x - x0 + 0.5) / w, fy = (y - y0 + 0.5) / h;
                // Нижний край — фестонами между «пальцами» крыла.
                double scallop = 0.78 + 0.2 * Math.Abs(Math.Sin(fx * Math.PI * 3.0));
                if (fy > scallop || Hash(x, y, seed) > 0.985)
                {
                    c.Set(x, y, Color.FromArgb(0, 0, 0, 0));
                    continue;
                }
                Color col = Mix(C(54, 8, 16), C(22, 6, 10), fy);
                bool vein = Math.Abs(fx * 3.0 - Math.Round(fx * 3.0)) < 0.06 || Math.Abs(fy - fx * 0.4 - 0.1) < 0.025;
                if (vein) col = C(98, 18, 28);
                c.Set(x, y, Mul(col, 1.0 + (Hash(x, y, seed + 1) - 0.5) * 0.25));
            }
        }
    }

    /** Светящиеся глаза фигуры — отдельный слой, который рисуется без освещения. */
    static Canvas SpiritEyes()
    {
        var c = new Canvas(128, 128);
        Color iris = C(255, 214, 40), hot = C(255, 248, 170);
        c.Set(10, 35, iris); c.Set(11, 35, hot); c.Set(12, 35, iris);
        c.Set(10, 36, Mul(iris, 0.8)); c.Set(11, 36, C(40, 10, 0)); c.Set(12, 36, Mul(iris, 0.8));
        c.Set(14, 35, iris); c.Set(15, 35, hot); c.Set(16, 35, iris);
        c.Set(14, 36, Mul(iris, 0.8)); c.Set(15, 36, C(40, 10, 0)); c.Set(16, 36, Mul(iris, 0.8));
        return c;
    }

    // ------------------------------------------------------------------ маска Пустого

    /**
     * Маска Пустого (64×64) поверх головы игрока: шлем (0,10) с маской спереди и рога (32..58,10..26).
     * {@code crack}: 0 — цела, 1 — первая трещина, 2 — скол, 3 — почти рассыпалась (прозрачные
     * места — откололись, под ними видно лицо).
     */
    static Canvas HollowMask(int crack)
    {
        var c = new Canvas(64, 64);
        Color bone = C(236, 232, 222), shade = C(200, 194, 182), red = C(178, 16, 24);
        // Шлем: верх (8..16 × 10..18), низ (16..24 × 10..18), бока и затылок (0..32 × 18..26).
        Paint(c, 8, 10, 24, 18, bone, 0.08, 1);
        Paint(c, 0, 18, 32, 26, bone, 0.08, 2);
        // Красные полосы: по левой стороне лица и через макушку.
        for (int y = 18; y < 26; y++) { c.Set(24 + 1, y, red); c.Set(24 + 2, y, red); }  // затылок
        for (int y = 10; y < 18; y++) { c.Set(10, y, red); c.Set(11, y, red); }        // макушка
        for (int y = 18; y < 26; y++) { c.Set(1, y, red); c.Set(2, y, red); c.Set(5, y, red); }  // правый бок
        // Лицо маски (8..16 × 18..26): две алые полосы над левым глазом до самой челюсти,
        // косые прорези глаз, тень носа и оскал.
        Paint(c, 8, 18, 16, 26, bone, 0.06, 3);
        Color hole = C(10, 6, 8);
        for (int y = 18; y < 24; y++) { c.Set(9, y, red); c.Set(11, y, red); }
        c.Set(10, 20, hole); c.Set(9, 21, hole); c.Set(10, 21, hole); c.Set(11, 21, hole);
        c.Set(13, 20, hole); c.Set(12, 21, hole); c.Set(13, 21, hole); c.Set(14, 21, hole);
        c.Set(11, 22, shade); c.Set(12, 22, shade);
        for (int x = 8; x < 16; x++) { c.Set(x, 24, x % 2 == 0 ? bone : C(30, 22, 24)); c.Set(x, 25, x % 2 == 1 ? shade : C(30, 22, 24)); }
        c.Set(8, 24, shade); c.Set(15, 24, shade);
        // Рога: кость с тёмными кончиками.
        Gradient(c, 32, 10, 48, 18, bone, shade, 0.1, 4);
        Gradient(c, 32, 18, 48, 26, shade, C(150, 140, 128), 0.1, 5);
        Paint(c, 48, 10, 58, 15, C(40, 30, 28), 0.2, 6);

        Color line = C(52, 40, 40);
        Color gone = Color.FromArgb(0, 0, 0, 0);
        if (crack >= 1)
        {
            // Первая трещина: через лоб к правому глазу и по макушке.
            Dots(c, line, 14, 18, 14, 19, 13, 19);
            Dots(c, line, 13, 10, 13, 11, 12, 12, 12, 13, 13, 14);
        }
        if (crack >= 2)
        {
            // Скол: отломился угол челюсти справа и край правой щеки.
            Dots(c, gone, 15, 23, 15, 24, 15, 25, 14, 25, 16, 24, 16, 25, 17, 25);
            Dots(c, line, 14, 22, 14, 23, 13, 23, 8, 19, 9, 20, 8, 20);
            Dots(c, line, 18, 19, 19, 20, 19, 21, 20, 22, 3, 20, 3, 21, 4, 22);
        }
        if (crack >= 3)
        {
            // Почти рассыпалась: нет куска лба, половины оскала, части макушки.
            Dots(c, gone, 8, 18, 9, 18, 8, 19, 12, 24, 13, 24, 12, 25, 13, 25, 14, 24);
            Dots(c, gone, 13, 15, 14, 15, 13, 16, 14, 16, 15, 16, 13, 17, 14, 17, 15, 17);
            Dots(c, gone, 21, 18, 22, 18, 22, 19, 0, 24, 0, 25, 1, 25, 28, 18, 29, 18, 29, 19);
            Dots(c, line, 10, 22, 10, 23, 11, 23, 12, 18, 11, 19, 15, 21, 15, 22, 16, 20, 17, 21, 6, 18, 6, 19, 7, 20);
        }
        return c;
    }

    /** Закрасить точки, заданные парами x, y. */
    static void Dots(Canvas c, Color col, params int[] xy)
    {
        for (int i = 0; i + 1 < xy.Length; i += 2) c.Set(xy[i], xy[i + 1], col);
    }

    static Canvas HollowMaskEyes()
    {
        var c = new Canvas(64, 64);
        // Жёлтые глаза с чёрным зрачком в прорезях маски (лицо шлема — 8..16 × 18..26).
        Color hot = C(255, 226, 70), warm = C(255, 170, 30), pupil = C(40, 10, 0);
        c.Set(10, 20, hot); c.Set(9, 21, warm); c.Set(10, 21, pupil); c.Set(11, 21, warm);
        c.Set(13, 20, hot); c.Set(12, 21, warm); c.Set(13, 21, pupil); c.Set(14, 21, warm);
        return c;
    }

    // ------------------------------------------------------------------ маска в интерфейсе

    /** Силуэт маски для значка: внутри ли точка (координаты значка 32×32). */
    static bool MaskFace(double x, double y)
    {
        double dx = (x - 16.0) / 9.6, dy = (y - 17.0) / 11.0;
        if (dy > 0) dx /= 1.0 - dy * 0.28;  // подбородок уже лба
        return dx * dx + dy * dy < 1.0;
    }

    /** Рог значка: толстая дуга от виска вверх и в сторону; side = −1 — левый, 1 — правый. */
    static double HornDistance(double x, double y, int side, out double along)
    {
        double best = 99;
        along = 0;
        for (int i = 0; i <= 40; i++)
        {
            double t = i / 40.0;
            double hx = 16 + side * (7.0 + 5.5 * t + 1.5 * t * t), hy = 9.5 - 9.0 * t + 1.2 * t * t * t;
            double r = 1.9 * (1.0 - t) + 0.35;
            double d = Math.Sqrt((x - hx) * (x - hx) + (y - hy) * (y - hy)) - r;
            if (d < best) { best = d; along = t; }
        }
        return best;
    }

    static bool MaskEyeHole(double x, double y, out bool iris)
    {
        iris = false;
        foreach (int side in new[] { -1, 1 })
        {
            double cx = 16 + side * 4.6, cy = 14.6;
            double dx = x - cx, dy = (y - cy) + side * dx * 0.28;   // косые прорези
            double e = (dx / 3.2) * (dx / 3.2) + (dy / 1.75) * (dy / 1.75);
            if (e < 1.0)
            {
                iris = Math.Abs(dx + side * 0.3) < 1.2 && Math.Abs(dy) < 1.2;
                return true;
            }
        }
        return false;
    }

    /** Значок маски: {@code full} — живая (белая кость, алые полосы, горящие глаза), иначе тёмный силуэт. */
    static Canvas MaskIcon(bool full)
    {
        var c = new Canvas(32, 32);
        var inside = new bool[32, 32];
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++)
            {
                double px = x + 0.5, py = y + 0.5;
                double alongL, alongR;
                bool face = MaskFace(px, py);
                double hl = HornDistance(px, py, -1, out alongL);
                double hr = HornDistance(px, py, 1, out alongR);
                bool horn = !face && (hl < 0 || hr < 0);
                double a = hl < 0 ? alongL : alongR;
                if (!face && !horn) continue;
                inside[x, y] = true;
                Color col;
                if (!full)
                {
                    col = Color.FromArgb(215, 40, 34, 38);
                }
                else if (horn)
                {
                    col = a > 0.78 ? C(46, 34, 32) : Mix(C(232, 226, 212), C(176, 166, 150), a);
                }
                else
                {
                    col = Mix(C(244, 240, 230), C(196, 188, 176), Sat((py - 6) / 24.0) * 0.6 + Math.Abs(px - 16) / 20.0);
                    // Алые полосы на левой стороне лица (от лба до челюсти).
                    if ((Math.Abs(px - 10.5) < 0.9 || Math.Abs(px - 13.0) < 0.7) && py > 6.5 && py < 22.5) col = C(184, 18, 26);
                    // Оскал.
                    if (py > 22.5 && py < 26.0 && px > 10 && px < 22) col = ((int)px % 2 == 0) ? C(236, 230, 218) : C(34, 24, 26);
                    // Тень носа.
                    if (Math.Abs(px - 16) < 1.0 && py > 17.5 && py < 19.5) col = C(170, 160, 150);
                }
                bool iris;
                if (MaskEyeHole(px, py, out iris))
                {
                    col = full && iris ? C(255, 214, 50) : C(10, 6, 8);
                }
                c.Set(x, y, col);
            }
        // Тёмный контур по силуэту.
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++)
            {
                if (inside[x, y]) continue;
                bool edge = (x > 0 && inside[x - 1, y]) || (x < 31 && inside[x + 1, y]) || (y > 0 && inside[x, y - 1]) || (y < 31 && inside[x, y + 1]);
                if (edge) c.Set(x, y, Color.FromArgb(full ? 230 : 170, 12, 8, 10));
            }
        return c;
    }

    /** Скол на значке: круг выбитой кости (только в пределах лица). */
    static void Chip(Canvas c, int o, double cx, double cy, double r, Color col)
    {
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++)
            {
                double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                double wobble = r * (0.8 + 0.35 * Hash(x, y, 61));
                if (dx * dx + dy * dy < wobble * wobble && MaskFace(x + 0.5, y + 0.5)) c.Set(o + x, y, col);
            }
    }

    /** Трещины значка: три кадра 32×32 подряд — трещина, скол, почти рассыпалась. */
    static Canvas MaskIconCracks()
    {
        var c = new Canvas(96, 32);
        Color line = C(48, 30, 30), chip = C(20, 14, 16);
        for (int stage = 1; stage <= 3; stage++)
        {
            int o = (stage - 1) * 32;
            Dots(c, line, o + 17, 6, o + 17, 7, o + 18, 8, o + 18, 9, o + 19, 10, o + 19, 11, o + 20, 12);
            if (stage >= 2)
            {
                Chip(c, o, 22.5, 24.0, 2.6, chip);
                Dots(c, line, o + 11, 18, o + 10, 19, o + 10, 20, o + 9, 21, o + 21, 17, o + 22, 18, o + 22, 19, o + 23, 20);
                Dots(c, line, o + 14, 8, o + 13, 9, o + 13, 10);
            }
            if (stage >= 3)
            {
                Chip(c, o, 9.5, 9.0, 2.8, chip);
                Chip(c, o, 14.5, 25.5, 2.2, chip);
                Dots(c, line, o + 15, 12, o + 15, 13, o + 16, 14, o + 16, 15, o + 16, 16, o + 7, 16, o + 8, 17, o + 8, 18, o + 24, 13, o + 25, 14, o + 24, 15);
                Dots(c, line, o + 12, 20, o + 13, 21, o + 13, 22, o + 19, 19, o + 18, 20, o + 19, 21);
            }
        }
        return c;
    }

    // ------------------------------------------------------------------ гецуга и луна

    /** Серп Гецуги Теншо: чёрная сердцевина с алой кромкой, выпуклостью вверх (по ходу полёта). */
    static Canvas Getsuga()
    {
        var c = new Canvas(64, 32);
        for (int y = 0; y < 32; y++)
        {
            for (int x = 0; x < 64; x++)
            {
                double px = x + 0.5, py = y + 0.5;
                double outer = Math.Sqrt((px - 32) * (px - 32) + (py - 46) * (py - 46)) - 42;   // < 0 внутри
                double inner = Math.Sqrt((px - 32) * (px - 32) + (py - 56) * (py - 56)) - 44;   // > 0 вне
                double band = Math.Min(-outer, inner);   // глубина внутри серпа
                if (band <= -1.5) continue;
                double a = Sat((band + 1.5) / 2.5);
                Color col;
                if (band > 2.2) col = Mix(C(30, 0, 4), C(4, 0, 2), Sat((band - 2.2) / 3));
                else if (band > 0.6) col = C(220, 24, 30);
                else col = C(255, 120, 90);
                c.Set(x, y, Color.FromArgb(Cl(a * 255), col.R, col.G, col.B));
            }
        }
        return c;
    }

    /** Серповидная луна для неба: светлый серп с мягким алым ореолом (рисуется со сложением цветов). */
    static Canvas CrescentMoon()
    {
        var c = new Canvas(64, 64);
        for (int y = 0; y < 64; y++)
        {
            for (int x = 0; x < 64; x++)
            {
                double px = x + 0.5, py = y + 0.5;
                double outer = Math.Sqrt((px - 32) * (px - 32) + (py - 32) * (py - 32)) - 22;
                double inner = Math.Sqrt((px - 40) * (px - 40) + (py - 26) * (py - 26)) - 20;
                double band = Math.Min(-outer, inner);
                Color col;
                double a;
                if (band > 0)
                {
                    col = Mix(C(220, 16, 20), C(255, 120, 90), Sat(band / 5));
                    a = 1;
                }
                else
                {
                    // Ореол гаснет к краям картинки, чтобы не было видно квадрата.
                    double r = Math.Sqrt((px - 32) * (px - 32) + (py - 32) * (py - 32));
                    double edge = Sat((31.0 - r) / 6.0);
                    double glow = Math.Exp(band / 3.2);
                    col = C(190, 0, 8);
                    a = glow * 0.8 * edge;
                }
                c.Set(x, y, Color.FromArgb(Cl(a * 255), col.R, col.G, col.B));
            }
        }
        return c;
    }

    static Canvas White()
    {
        var c = new Canvas(4, 4);
        c.Fill(0, 0, 4, 4, C(255, 255, 255));
        return c;
    }

    /** Срез эндермена: тёмно-фиолетовое нутро с пурпурными искрами. */
    static Canvas EnderCut()
    {
        var c = new Canvas(16, 16);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 16; x++)
            {
                double h = Hash(x, y, 21);
                Color col = h > 0.9 ? C(224, 120, 255) : (h > 0.7 ? C(120, 30, 150) : Mix(C(40, 8, 52), C(70, 14, 90), Hash(x, y, 22)));
                c.Set(x, y, col);
            }
        return c;
    }

    // ------------------------------------------------------------------ меч

    const int EYE_CLOSED = 0, EYE_OPEN = 1, EYE_BURNING = 2, EYE_ANGRY = 3;

    /**
     * Зангецу (шикай): широкий клинок-тесак без гарды, тёмная сердцевина с серебряной кромкой и волнистым
     * хамоном, рукоять в чёрной оплётке поверх белой ткани. Меч лежит по диагонали, как ванильные мечи:
     * навершие внизу слева, остриё вверху справа. Рисуем в координатах клинка: u — вдоль, v — поперёк;
     * лезвие (v &gt; 0) — сверху слева, обух (v &lt; 0) — снизу справа: так в руке от третьего лица лезвие
     * смотрит вниз, как у настоящего тесака.
     *
     * <p>Меч объёмный: каждая часть — свой слой, и в игре слои выдавливаются на разную толщину
     * (рукоять и обух толще, кромка тоньше). Глаз — отдельная 3D-деталь, на клинке под ним только гнездо.
     */
    const int P_POMMEL = 1, P_WRAP = 2, P_COLLAR = 3, P_SPINE = 5, P_CORE = 6, P_EDGE = 7;
    const double EYE_U = 37.0, EYE_V = -0.8;
    const double SW_S = 0.70710678, SW_X0 = 5.0, SW_Y0 = 59.0;

    /** Центр глаза на картинке меча (в пикселях 64×64). */
    static double EyeX() { return SW_X0 + (EYE_U - EYE_V) * SW_S; }
    static double EyeY() { return SW_Y0 - (EYE_U + EYE_V) * SW_S; }

    class SwordPaint
    {
        public Color[,] Col = new Color[64, 64];
        public int[,] Part = new int[64, 64];
        public int[,] Region = new int[64, 64];
    }

    static SwordPaint PaintSword()
    {
        const int N = 64;
        var sp = new SwordPaint();
        for (int y = 0; y < N; y++)
        {
            for (int x = 0; x < N; x++)
            {
                double X = x + 0.5 - SW_X0, Y = y + 0.5 - SW_Y0;
                double u = (X - Y) * SW_S;
                double v = -(X + Y) * SW_S;
                double n = Hash(x, y, 7) - 0.5;
                Color col = Color.Empty;
                int part = 0;
                if (u >= 0 && u < 2.4 && Math.Abs(v) <= 3.3)
                {
                    col = v > 1.6 ? C(118, 122, 134) : (v < -1.6 ? C(26, 27, 32) : C(52, 55, 63));
                    part = P_POMMEL;
                }
                else if (u >= 2.4 && u < 19.6 && Math.Abs(v) <= 2.7)
                {
                    double p = 4.3;
                    double k = Math.Round((u - 2.4 - p / 2) / p);
                    double uc = 2.4 + p / 2 + k * p;
                    bool cloth = Math.Abs(u - uc) + Math.Abs(v) * 0.9 < p / 2 - 0.25;
                    col = cloth ? Mix(C(236, 232, 220), C(168, 162, 150), (2.7 - v) / 5.4) : (v > 1.4 ? C(66, 60, 70) : C(22, 20, 25));
                    part = P_WRAP;
                }
                else if (u >= 19.6 && u < 22.6 && Math.Abs(v) <= 3.8)
                {
                    col = v > 2.0 ? C(246, 214, 122) : (v < -1.8 ? C(112, 80, 30) : C(190, 148, 60));
                    part = P_COLLAR;
                }
                else if (u >= 22.6 && u <= 82.5)
                {
                    double edge = u < 68 ? 8.0 : 8.0 - (u - 68.0);
                    if (v >= -6.0 && v <= edge)
                    {
                        double hamon = 4.1 + 0.9 * Math.Sin(u * 0.55) + 0.35 * Math.Sin(u * 1.37 + 1.0);
                        hamon = Math.Min(hamon, edge - 1.6);
                        if (v < -4.4)
                        {
                            col = v < -5.1 ? C(12, 13, 16) : C(46, 49, 58);    // обух: тень снаружи, блик внутри
                            part = P_SPINE;
                        }
                        else if (v < hamon - 0.45)
                        {
                            col = Mix(C(28, 30, 36), C(52, 55, 64), (v + 4.4) / 9.0);
                            col = Mul(col, 1.0 + n * 0.10);
                            // Дол (желобок) вдоль клинка — от глаза до острия.
                            if (u > 47 && u < 64 && v > -2.6 && v < -1.5) col = C(17, 18, 23);
                            else if (u > 47 && u < 64 && v >= -1.5 && v < -0.8) col = C(70, 75, 86);
                            part = P_CORE;
                        }
                        else
                        {
                            if (v < hamon + 0.45) col = C(176, 182, 194);        // туманная линия хамона
                            else
                            {
                                double t = (v - hamon) / Math.Max(0.5, edge - hamon);
                                col = Mix(C(140, 147, 160), C(226, 231, 239), t);
                                if (v > edge - 0.95) col = C(252, 253, 255);
                                col = Mul(col, 1.0 + n * 0.05);
                            }
                            part = P_EDGE;
                        }
                    }
                }
                if (part != 0)
                {
                    sp.Col[x, y] = col;
                    sp.Part[x, y] = part;
                    sp.Region[x, y] = part >= P_SPINE ? 4 : part;
                }
            }
        }
        // Тёмный контур по общему силуэту.
        var copy = (Color[,])sp.Col.Clone();
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++)
            {
                if (sp.Part[x, y] == 0) continue;
                bool border = Empty(sp, x - 1, y) || Empty(sp, x + 1, y) || Empty(sp, x, y - 1) || Empty(sp, x, y + 1);
                if (border) sp.Col[x, y] = Mix(copy[x, y], C(8, 8, 12), sp.Region[x, y] == 4 ? 0.55 : 0.7);
            }
        return sp;
    }

    static bool Empty(SwordPaint sp, int x, int y)
    {
        return x < 0 || y < 0 || x >= 64 || y >= 64 || sp.Part[x, y] == 0;
    }

    /** Один слой меча: только пиксели нужных частей (гнездо глаза — тёмное, горящий клинок — с трещинами). */
    static Canvas SwordLayer(SwordPaint sp, int[] parts, bool socket, bool cracks)
    {
        var c = new Canvas(64, 64);
        double ex = EyeX(), ey = EyeY();
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++)
            {
                if (Array.IndexOf(parts, sp.Part[x, y]) < 0) continue;
                Color col = sp.Col[x, y];
                if (socket && sp.Region[x, y] == 4)
                {
                    double dx = (x + 0.5 - ex) / 8.4, dy = (y + 0.5 - ey) / 5.4;
                    double d = dx * dx + dy * dy;
                    if (d < 1.0) col = Mix(C(30, 8, 10), C(14, 4, 6), 1.0 - d);
                }
                if (cracks && sp.Part[x, y] == P_CORE && CrackAt(x, y)) col = C(255, 110, 30);
                c.Set(x, y, col);
            }
        return c;
    }

    /** Раскалённые трещины горящего клинка — от глаза к острию и к рукояти. */
    static bool CrackAt(int x, int y)
    {
        double X = x + 0.5 - SW_X0, Y = y + 0.5 - SW_Y0;
        double u = (X - Y) * SW_S, v = -(X + Y) * SW_S;
        double[][] paths = {
            new[] { 43.0, -1.0, 47.0, -2.4, 52.0, -1.8, 57.0, -3.0, 63.0, -2.2, 68.0, -3.4 },
            new[] { 44.0, 0.6, 49.0, 1.6, 53.0, 0.8, 58.0, 2.0 },
            new[] { 31.0, -1.2, 28.0, -2.6, 25.0, -1.8 },
            new[] { 31.0, 0.8, 27.5, 2.0, 24.5, 1.2 },
        };
        foreach (var p in paths)
            for (int i = 0; i + 3 < p.Length; i += 2)
            {
                double ax = p[i], ay = p[i + 1], bx = p[i + 2], by = p[i + 3];
                double vx = bx - ax, vy = by - ay;
                double t = Sat(((u - ax) * vx + (v - ay) * vy) / (vx * vx + vy * vy));
                double qx = ax + vx * t - u, qy = ay + vy * t - v;
                if (qx * qx + qy * qy < 0.42) return true;
            }
        return false;
    }

    /** Плоская картинка меча целиком (с нарисованным глазом) — для частиц и значка. */
    static Canvas SwordIcon(SwordPaint sp)
    {
        var c = SwordLayer(sp, new[] { P_POMMEL, P_WRAP, P_COLLAR, P_SPINE, P_CORE, P_EDGE }, false, false);
        DrawEye(c, Math.Round(EyeX() * 2) / 2, Math.Round(EyeY() * 2) / 2, 1.0, EYE_OPEN, sp.Region);
        return c;
    }

    // ------------------------------------------------------------------ 3D-модель меча

    /*
     * Атлас объёмного Зангецу (128×128). Размеры клинка совпадают с ZangetsuModel (в пикселях модели):
     * u — вдоль меча от навершия, v — поперёк (обух −2.3, начало скоса 1.1, лезвие 2.8).
     *   клинок (0,0)-(112,40), горящий клинок (0,40)-(112,80): u 6.2..22 → x, v −2.3..2.8 → y;
     *   оплётка (0,80)-(64,96), тёмный металл (64,80)-(96,96), латунь (96,80)-(128,96).
     */
    const double M_BLADE_U0 = 6.2, M_BLADE_U1 = 22.0, M_SPINE = -2.3, M_EDGE = 2.8, M_BEVEL = 1.1;
    const double M_EYE_U = 10.6, M_EYE_V = -0.6;

    static Canvas SwordAtlas()
    {
        var c = new Canvas(128, 128);
        BladeTexture(c, 0, false);
        BladeTexture(c, 40, true);
        // Оплётка: белые ромбы ткани между перекрещенными чёрными шнурами.
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 64; x++)
            {
                double s = x + 0.5, a = y + 0.5;
                double k = Math.Round((s - 8.0) / 16.0);
                double sc = 8.0 + k * 16.0;
                double d = Math.Abs(s - sc) / 8.0 + Math.Abs(a - 8.0) / 8.0;
                Color col;
                if (d < 0.74)
                {
                    col = Mix(C(240, 236, 224), C(178, 172, 160), Math.Abs(a - 8.0) / 8.0 * 0.8 + d * 0.3);
                }
                else
                {
                    bool lit = ((int)(s + a) % 4) < 2;
                    col = lit ? C(58, 52, 62) : C(22, 20, 25);
                }
                c.Set(x, 80 + y, Mul(col, 1.0 + (Hash(x, y, 41) - 0.5) * 0.08));
            }
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 32; x++)
            {
                c.Set(64 + x, 80 + y, Mul(Mix(C(86, 90, 102), C(30, 32, 38), y / 15.0), 1.0 + (Hash(x, y, 42) - 0.5) * 0.16));
                c.Set(96 + x, 80 + y, Mul(Mix(C(250, 220, 130), C(130, 92, 34), y / 15.0), 1.0 + (Hash(x, y, 43) - 0.5) * 0.12));
            }
        return c;
    }

    static void BladeTexture(Canvas c, int y0, bool burning)
    {
        for (int ty = 0; ty < 40; ty++)
        {
            for (int tx = 0; tx < 112; tx++)
            {
                double u = M_BLADE_U0 + (tx + 0.5) / 112.0 * (M_BLADE_U1 - M_BLADE_U0);
                double v = M_SPINE + (ty + 0.5) / 40.0 * (M_EDGE - M_SPINE);
                double brushed = (Hash(tx / 6, ty, 51) - 0.5) * 0.12 + (Hash(tx, ty, 52) - 0.5) * 0.06;
                double hamon = M_BEVEL + 0.22 * Math.Sin(u * 1.7) + 0.1 * Math.Sin(u * 4.3 + 1.0);
                Color col;
                if (v < M_SPINE + 0.16)
                {
                    col = C(92, 97, 110);                                      // блик по ребру обуха
                }
                else if (v < hamon - 0.12)
                {
                    double t = (v - M_SPINE) / (M_BEVEL - M_SPINE);
                    col = Mul(Mix(C(24, 26, 31), C(54, 58, 67), t), 1.0 + brushed);
                    // Дол — желобок вдоль клинка от глаза к острию.
                    if (u > 13.8 && u < 19.4 && v > -1.85 && v < -1.55) col = C(14, 15, 19);
                    else if (u > 13.8 && u < 19.4 && v >= -1.55 && v < -1.38) col = C(84, 90, 102);
                }
                else if (v < hamon + 0.12)
                {
                    col = Mix(C(150, 156, 168), C(205, 210, 220), Hash(tx, ty, 53));  // туманная линия хамона
                }
                else
                {
                    double t = (v - hamon) / (M_EDGE - hamon);
                    col = Mul(Mix(C(136, 144, 158), C(230, 235, 243), t), 1.0 + brushed * 0.5);
                    if (v > M_EDGE - 0.18) col = C(252, 253, 255);
                }
                // Гнездо глаза: тёмное углубление с багровым краем.
                double ex = (u - M_EYE_U) / 2.5, ey = (v - M_EYE_V) / 1.42;
                double e = ex * ex + ey * ey;
                if (e < 1.0) col = Mix(C(34, 8, 10), C(12, 3, 4), 1.0 - e);
                else if (e < 1.35) col = Mix(col, C(70, 6, 10), (1.35 - e) / 0.35 * 0.7);
                if (burning)
                {
                    if (v < hamon - 0.12 && e >= 1.0) col = Mix(col, C(90, 10, 6), 0.25);
                    double crack = BladeCrack(u, v);
                    if (crack < 0.07) col = C(255, 226, 150);
                    else if (crack < 0.16) col = C(255, 120, 30);
                    else if (crack < 0.3) col = Mix(col, C(200, 40, 10), 0.6);
                }
                c.Set(tx, y0 + ty, col);
            }
        }
    }

    /** Расстояние (в пикселях модели) до ближайшей раскалённой трещины горящего клинка. */
    static double BladeCrack(double u, double v)
    {
        double[][] paths = {
            new[] { 13.0, -0.7, 14.6, -1.2, 16.2, -0.6, 17.8, -1.4, 19.6, -0.9, 21.0, -1.6 },
            new[] { 12.9, 0.1, 14.4, 0.7, 15.8, 0.3, 17.2, 0.9 },
            new[] { 8.2, -0.9, 7.4, -1.5, 6.6, -1.1 },
            new[] { 8.3, 0.0, 7.5, 0.6, 6.7, 0.3 },
            new[] { 10.6, -1.9, 11.4, -2.2 },
        };
        double best = 99;
        foreach (var p in paths)
            for (int i = 0; i + 3 < p.Length; i += 2)
            {
                double ax = p[i], ay = p[i + 1], bx = p[i + 2], by = p[i + 3];
                double vx = bx - ax, vy = by - ay;
                double t = Sat(((u - ax) * vx + (v - ay) * vy) / (vx * vx + vy * vy));
                double qx = ax + vx * t - u, qy = ay + vy * t - v;
                best = Math.Min(best, Math.Sqrt(qx * qx + qy * qy));
            }
        return best;
    }

    /**
     * Детали 3D-глаза (128×128, раскладка в долях как у 32×32): белок (0,0)-(16,8), радужки — обычная (0,8), горящая (8,8),
     * сердитая (16,8) по 8×8 (круг, вне круга прозрачно), веки (0,16)-(16,20), обод (16,16)-(24,20),
     * чёрный (24,16)-(28,20), блик (28,16)-(32,20).
     */
    static Canvas EyeParts()
    {
        // Раскладка та же, что у прежней текстуры 32×32, только в 4 раза подробнее (128×128).
        const int K = 4;
        var c = new Canvas(32 * K, 32 * K);
        // Белок (0,0)-(64,32): чёрно-багровый, с ветвистыми алыми прожилками от уголков.
        for (int y = 0; y < 8 * K; y++)
            for (int x = 0; x < 16 * K; x++)
            {
                double fy = Math.Abs(y + 0.5 - 4 * K) / (4.0 * K), fx = Math.Abs(x + 0.5 - 8 * K) / (8.0 * K);
                Color col = Mix(C(34, 9, 12), C(10, 3, 5), fy * 0.8 + fx * 0.3);
                double vein = Math.Abs(Math.Sin(x * 0.21 + Math.Sin(y * 0.37) * 2.0 + Hash(x / 9, y / 9, 31) * 3.0));
                if (vein < 0.06 + fx * 0.05) col = Mix(col, C(150, 16, 22), 0.75);
                c.Set(x, y, Mul(col, 1.0 + (Hash(x, y, 32) - 0.5) * 0.1));
            }
        Iris(c, 0, 8 * K, 8 * K, C(255, 246, 150), C(255, 202, 40), C(232, 132, 0), C(84, 40, 0));
        Iris(c, 8 * K, 8 * K, 8 * K, C(255, 255, 238), C(255, 230, 96), C(255, 122, 20), C(190, 30, 6));
        Iris(c, 16 * K, 8 * K, 8 * K, C(255, 204, 64), C(240, 112, 16), C(190, 50, 8), C(70, 12, 6));
        // Веки (0,64)-(64,80): кожистые, светлее к краю, с тёмной каймой ресниц снизу.
        for (int y = 0; y < 4 * K; y++)
            for (int x = 0; x < 16 * K; x++)
            {
                Color col = Mix(C(104, 76, 80), C(48, 32, 36), y / (4.0 * K - 1));
                if (Math.Abs(Math.Sin(x * 0.5 + y * 0.2)) < 0.12) col = Mul(col, 0.8);   // складки
                c.Set(x, 16 * K + y, Mul(col, 1.0 + (Hash(x, y, 33) - 0.5) * 0.12));
            }
        // Обод (64,64)-(96,80): багровый, с бликом сверху.
        for (int y = 0; y < 4 * K; y++)
            for (int x = 0; x < 8 * K; x++)
                c.Set(16 * K + x, 16 * K + y, Mul(Mix(C(176, 30, 34), C(92, 10, 14), y / (4.0 * K - 1)), 1.0 + (Hash(x, y, 34) - 0.5) * 0.16));
        Paint(c, 24 * K, 16 * K, 28 * K, 20 * K, C(6, 3, 3), 0, 0);
        Paint(c, 28 * K, 16 * K, 32 * K, 20 * K, C(255, 255, 255), 0, 0);
        return c;
    }

    /** Радужка: светлое кольцо у зрачка, лучистые волокна, тёмный лимб по краю. */
    static void Iris(Canvas c, int x0, int y0, int size, Color center, Color mid, Color outer, Color limbal)
    {
        double half = size / 2.0;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
            {
                double dx = x + 0.5 - half, dy = y + 0.5 - half;
                double r = Math.Sqrt(dx * dx + dy * dy) / half;
                if (r > 1.0) continue;
                double ang = Math.Atan2(dy, dx);
                double fibers = 0.86 + 0.14 * Math.Cos(ang * 23 + Math.Sin(ang * 5) * 2) + (Hash(x, y, 35) - 0.5) * 0.1;
                Color col = r < 0.32 ? center : (r < 0.62 ? Mix(center, mid, (r - 0.32) / 0.3) : (r < 0.86 ? Mix(mid, outer, (r - 0.62) / 0.24) : Mix(outer, limbal, (r - 0.86) / 0.14)));
                c.Set(x0 + x, y0 + y, Mul(col, fibers));
            }
    }

    static bool Filled(Color[,] px, int x, int y)
    {
        return x >= 0 && y >= 0 && x < px.GetLength(0) && y < px.GetLength(1) && px[x, y].A > 0;
    }

    /** Миндалевидный глаз: жёлтая радужка, вертикальный «тигриный» зрачок, тёмный белок как у Пустого. */
    static void DrawEye(Canvas c, double ex, double ey, double scale, int state, int[,] region)
    {
        double W = 7.4, H = 4.3;
        int x0 = (int)Math.Floor(ex - (W + 4) * scale), x1 = (int)Math.Ceiling(ex + (W + 4) * scale);
        int y0 = (int)Math.Floor(ey - (H + 4) * scale), y1 = (int)Math.Ceiling(ey + (H + 4) * scale);
        var baseCol = (Color[,])c.Px.Clone();
        for (int y = y0; y <= y1; y++)
        {
            for (int x = x0; x <= x1; x++)
            {
                if (!c.Filled(x, y)) continue;
                double dx = (x + 0.5 - ex) / scale, dy = (y + 0.5 - ey) / scale;
                double t = dx / W;
                double top = -999, bot = -999;
                if (Math.Abs(t) < 1)
                {
                    top = -H * Math.Pow(1 - t * t, 0.75);
                    bot = H * 0.8 * Math.Pow(1 - t * t, 0.9);
                }
                bool inside = Math.Abs(t) < 1 && dy > top && dy < bot;
                double tr = dx / (W + 1.25);
                bool inRim = !inside && Math.Abs(tr) < 1
                        && dy > -(H + 1.15) * Math.Pow(1 - tr * tr, 0.75) && dy < (H * 0.8 + 1.0) * Math.Pow(1 - tr * tr, 0.9);
                double tg = dx / (W + 3.6);
                bool inGlow = !inside && !inRim && Math.Abs(tg) < 1
                        && dy > -(H + 3.2) * Math.Pow(1 - tg * tg, 0.7) && dy < (H * 0.8 + 3.0) * Math.Pow(1 - tg * tg, 0.8);
                Color col = c.Px[x, y];

                if (state == EYE_CLOSED)
                {
                    if (inside)
                    {
                        // Сомкнутые веки: верхнее светлее, по шву — ресницы.
                        double seam = 0.9 * (1 - t * t);
                        if (Math.Abs(dy - seam) < 0.55 / scale + 0.25) col = C(8, 3, 5);
                        else if (dy < seam) col = Mix(C(98, 74, 78), C(58, 42, 46), (dy - top) / Math.Max(0.1, seam - top));
                        else col = C(44, 30, 34);
                    }
                    else if (inRim)
                    {
                        col = dy < 0 ? C(92, 18, 22) : C(60, 14, 18);
                    }
                }
                else
                {
                    bool burning = state == EYE_BURNING;
                    bool angry = state == EYE_ANGRY;
                    if (angry)
                    {
                        // Сердитый прищур: верхнее веко опущено.
                        double lid = top + (bot - top) * 0.42;
                        if (inside && dy < lid)
                        {
                            col = Mix(C(98, 74, 78), C(58, 42, 46), (dy - top) / Math.Max(0.1, lid - top));
                            if (dy > lid - 0.8 / scale) col = C(8, 3, 5);
                            c.Set(x, y, col);
                            continue;
                        }
                    }
                    if (inside)
                    {
                        double ix = dx, iy = dy - 0.4;
                        double R = 4.45;
                        double r = Math.Sqrt(ix * ix + iy * iy) / R;
                        double pupilW = burning ? 0.62 : (angry ? 0.55 : 0.9);
                        double pupil = (ix / pupilW) * (ix / pupilW) + (iy / 3.9) * (iy / 3.9);
                        if (r < 1)
                        {
                            double ang = Math.Atan2(iy, ix);
                            double streak = 0.9 + 0.1 * Math.Cos(ang * 7 + 0.5) + (Hash(x, y, 3) - 0.5) * 0.08;
                            if (pupil < 1) col = burning ? C(30, 0, 0) : C(6, 3, 3);
                            else if (pupil < 1.7) col = burning ? C(190, 30, 0) : C(96, 40, 0);
                            else if (burning)
                            {
                                if (r < 0.32) col = C(255, 255, 238);
                                else if (r < 0.6) col = Mul(C(255, 232, 96), streak);
                                else if (r < 0.86) col = Mul(C(255, 142, 24), streak);
                                else col = C(196, 36, 8);
                            }
                            else if (angry)
                            {
                                if (r < 0.6) col = Mul(C(255, 196, 40), streak);
                                else if (r < 0.88) col = Mul(C(226, 92, 10), streak);
                                else col = C(80, 14, 6);
                            }
                            else
                            {
                                if (r < 0.36) col = Mul(C(255, 242, 128), streak);
                                else if (r < 0.7) col = Mul(C(255, 200, 32), streak);
                                else if (r < 0.9) col = Mul(C(232, 138, 0), streak);
                                else col = C(88, 42, 0);
                            }
                        }
                        else
                        {
                            // Белок: чёрный с алыми прожилками (у горящего — раскалённый).
                            double vein = Hash((int)Math.Floor(dx * 1.3), (int)Math.Floor(dy * 2.0), 11);
                            if (burning) col = Mix(C(130, 8, 12), C(232, 52, 24), 1 - Math.Min(1, (r - 1) * 1.2));
                            else col = vein > 0.83 ? C(96, 10, 14) : C(18, 7, 9);
                        }
                        // Тень от верхнего века.
                        if (dy < top + 1.05) col = Mul(col, burning ? 0.8 : 0.55);
                        // Блики.
                        if (!burning && Math.Abs(dx + 1.9) < 0.55 && Math.Abs(dy + 1.2) < 0.55) col = C(255, 255, 255);
                        if (!burning && Math.Abs(dx - 1.7) < 0.45 && Math.Abs(dy - 2.0) < 0.45) col = C(255, 248, 200);
                    }
                    else if (inRim)
                    {
                        if (burning) col = dy < 0 ? C(255, 112, 40) : C(226, 70, 24);
                        else col = dy < 0 ? C(132, 18, 22) : C(84, 12, 16);
                    }
                    else if (inGlow && burning && region != null && region[Math.Max(0, Math.Min(region.GetLength(0) - 1, x)), Math.Max(0, Math.Min(region.GetLength(1) - 1, y))] == 4)
                    {
                        col = Mix(baseCol[x, y], C(255, 70, 20), 0.45);
                    }
                }
                c.Set(x, y, col);
            }
        }
    }


    // ------------------------------------------------------------------ иконка

    static Canvas Icon()
    {
        var c = new Canvas(128, 128);
        for (int y = 0; y < 128; y++)
        {
            for (int x = 0; x < 128; x++)
            {
                double d = Math.Sqrt((x - 63.5) * (x - 63.5) + (y - 63.5) * (y - 63.5)) / 90.0;
                Color col = Mix(C(46, 49, 58), C(14, 15, 19), d);
                col = Mul(col, 1.0 + (Hash(x / 2, y / 2, 5) - 0.5) * 0.12);
                c.Set(x, y, col);
            }
        }
        DrawEye(c, 64, 62, 7.0, EYE_OPEN, null);
        return c;
    }
}
