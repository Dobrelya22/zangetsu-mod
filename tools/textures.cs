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
        Canvas[] atlas = SwordAtlasHD();
        Out(atlas[0], assets, "textures/entity/zangetsu_model.png", 2);
        Out(atlas[1], assets, "textures/entity/zangetsu_glow.png", 2);
        Out(SwordIcon(sp), assets, "textures/item/zangetsu_icon.png", 8);
        Out(EyePartsHD(), assets, "textures/entity/zangetsu_eye.png", 2);
        Out(White(), assets, "textures/misc/white.png", 16);
        Out(Icon(), assets, "icon.png", 4);
        Out(Spirit(), assets, "textures/entity/spirit.png", 4);
        Out(SpiritEyes(), assets, "textures/entity/spirit_eyes.png", 4);
        for (int crack = 0; crack <= 3; crack++)
        {
            Out(HollowMaskHD(crack), assets, "textures/entity/hollow_mask_" + crack + ".png", 3);
        }
        Out(MaskIcon(true), assets, "textures/gui/mask_full.png", 8);
        Out(MaskIcon(false), assets, "textures/gui/mask_empty.png", 8);
        Out(MaskIconCracks(), assets, "textures/gui/mask_cracks.png", 8);
        Out(HollowMaskEyesHD(), assets, "textures/entity/hollow_mask_eyes.png", 3);
        Out(SlashTrail(), assets, "textures/misc/slash.png", 4);
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

    /** Закрасить точки, заданные парами x, y. */
    static void Dots(Canvas c, Color col, params int[] xy)
    {
        for (int i = 0; i + 1 < xy.Length; i += 2) c.Set(xy[i], xy[i + 1], col);
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

    /** Размеры клинка (в пикселях модели, как в ZangetsuItemRenderer): u — вдоль меча, v — поперёк. */
    const double M_BLADE_U0 = 6.2, M_BLADE_U1 = 22.0, M_SPINE = -2.3, M_EDGE = 2.8, M_BEVEL = 1.1;
    const double M_EYE_U = 10.6, M_EYE_V = -0.6;

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

    // ------------------------------------------------------------------ шум для подробных текстур

    /** Плавный шум 0..1 (значения в узлах сетки, гладкая интерполяция между ними). */
    static double VNoise(double x, double y, int seed)
    {
        int xi = (int)Math.Floor(x), yi = (int)Math.Floor(y);
        double fx = x - xi, fy = y - yi;
        double a = Hash(xi, yi, seed), b = Hash(xi + 1, yi, seed), c = Hash(xi, yi + 1, seed), d = Hash(xi + 1, yi + 1, seed);
        double sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy;
    }

    /** Несколько октав плавного шума. */
    static double Fbm(double x, double y, int seed, int octaves)
    {
        double sum = 0, amp = 0.5, f = 1, norm = 0;
        for (int i = 0; i < octaves; i++)
        {
            sum += amp * VNoise(x * f, y * f, seed + i * 17);
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return sum / norm;
    }

    static double Smooth(double a, double b, double x)
    {
        double t = Sat((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    /** Расстояние от точки до ломаной (x0, y0, x1, y1, ...). */
    static double PolyDistance(double x, double y, double[] p)
    {
        double best = 1e9;
        for (int i = 0; i + 3 < p.Length; i += 2)
        {
            double ax = p[i], ay = p[i + 1], vx = p[i + 2] - ax, vy = p[i + 3] - ay;
            double t = Sat(((x - ax) * vx + (y - ay) * vy) / (vx * vx + vy * vy));
            double qx = ax + vx * t - x, qy = ay + vy * t - y;
            best = Math.Min(best, Math.Sqrt(qx * qx + qy * qy));
        }
        return best;
    }

    /** Маска иероглифа (0..1) размером size×size, нарисованная шрифтом Windows. */
    static double[,] Glyph(string text, int size)
    {
        var mask = new double[size, size];
        using (var bmp = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        using (var g = Graphics.FromImage(bmp))
        {
            g.Clear(Color.Black);
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.AntiAliasGridFit;
            string[] fonts = { "Yu Mincho", "MS Mincho", "SimSun", "Arial Unicode MS" };
            Font font = null;
            foreach (string name in fonts)
            {
                var f = new Font(name, size * 0.78f, FontStyle.Bold, GraphicsUnit.Pixel);
                if (f.Name == name) { font = f; break; }
                f.Dispose();
            }
            if (font == null) font = new Font(FontFamily.GenericSerif, size * 0.78f, FontStyle.Bold, GraphicsUnit.Pixel);
            var fmt = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
            g.DrawString(text, font, Brushes.White, new RectangleF(0, size * 0.04f, size, size), fmt);
            font.Dispose();
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                    mask[x, y] = bmp.GetPixel(x, y).R / 255.0;
        }
        return mask;
    }

    // ------------------------------------------------------------------ меч в высоком разрешении

    /*
     * Атлас объёмного Зангецу 512×512 (в ZangetsuItemRenderer — те же числа):
     *   клинок (0,0)-(448,160), горящий клинок (0,160)-(448,320): u 6.2..22 → x, v −2.3..2.8 → y;
     *   кожа ската под оплёткой (448,0)-(512,64), шёлковый шнур (448,64)-(512,96), тёмный металл (448,96)-(512,128),
     *   латунь (448,128)-(512,160), сталь цепи (448,160)-(512,192).
     * Свечение (zangetsu_glow.png, та же раскладка): раскалённые трещины горящего клинка — рисуется поверх светом.
     */
    const int BLADE_W = 448, BLADE_H = 160;

    static Canvas[] SwordAtlasHD()
    {
        var c = new Canvas(512, 512);
        var glow = new Canvas(512, 512);
        double[,] zan = Glyph("斬", 48), getsu = Glyph("月", 48);
        BladeHD(c, glow, 0, false, zan, getsu);
        BladeHD(c, glow, BLADE_H, true, zan, getsu);
        SameSkin(c, 448, 0);
        Cord(c, 448, 64);
        DarkMetal(c, 448, 96, 61);
        Brass(c, 448, 128);
        DarkMetal(c, 448, 160, 67);
        return new[] { c, glow };
    }

    static void BladeHD(Canvas c, Canvas glow, int y0, bool burning, double[,] zan, double[,] getsu)
    {
        // Царапины поперёк клинка — у настоящего меча, побывавшего в боях.
        double[][] scratches = {
            new[] { 9.0, -1.9, 9.9, -0.6 }, new[] { 14.9, -2.0, 16.1, 0.2 }, new[] { 18.2, -1.1, 18.7, 0.4 },
            new[] { 20.1, -1.8, 20.9, -0.9 }, new[] { 12.1, 1.4, 13.0, 2.4 }, new[] { 16.8, 1.6, 17.3, 2.5 },
        };
        for (int ty = 0; ty < BLADE_H; ty++)
        {
            for (int tx = 0; tx < BLADE_W; tx++)
            {
                double u = M_BLADE_U0 + (tx + 0.5) / BLADE_W * (M_BLADE_U1 - M_BLADE_U0);
                double v = M_SPINE + (ty + 0.5) / BLADE_H * (M_EDGE - M_SPINE);
                // Шлифовка вдоль клинка: длинные тонкие штрихи.
                double grain = (Fbm(tx / 70.0, ty / 1.3, 51, 3) - 0.5) * 0.24 + (Hash(tx, ty, 52) - 0.5) * 0.05;
                double hamon = M_BEVEL + 0.22 * Math.Sin(u * 1.7) + 0.1 * Math.Sin(u * 4.3 + 1.0) + (Fbm(u * 2.5, 3.0, 57, 3) - 0.5) * 0.16;
                Color col;
                if (v < M_SPINE + 0.13)
                {
                    // Фаска обуха: светлый блик ребра.
                    col = Mix(C(150, 156, 170), C(66, 70, 82), (v - M_SPINE) / 0.13);
                }
                else if (v < hamon - 0.1)
                {
                    double t = (v - M_SPINE) / (M_BEVEL - M_SPINE);
                    col = Mul(Mix(C(20, 22, 27), C(50, 54, 64), t), 1.0 + grain);
                    // Окалина у основания клинка — тёмная, пятнистая.
                    double scale = Sat((7.8 - u) / 1.6);
                    if (scale > 0) col = Mix(col, Mul(C(26, 22, 22), 0.7 + 0.6 * Fbm(tx / 5.0, ty / 5.0, 58, 3)), scale * 0.8);
                    // Дол: желобок вдоль клинка с бликом на нижней кромке.
                    double fullerLen = Smooth(13.6, 14.4, u) * (1 - Smooth(18.9, 19.6, u));
                    double dv = v + 1.62;
                    if (fullerLen > 0 && Math.Abs(dv) < 0.2)
                    {
                        double depth = 1 - Math.Abs(dv) / 0.2;
                        col = Mix(col, dv < 0.06 ? C(8, 9, 12) : C(110, 116, 130), fullerLen * (dv < 0.06 ? depth : 0.85));
                    }
                }
                else if (v < hamon + 0.13)
                {
                    // Туманная линия хамона с искрами ние.
                    double mist = Fbm(tx / 7.0, ty / 3.0, 53, 3);
                    col = Mix(C(118, 124, 136), C(220, 224, 234), Smooth(hamon - 0.1, hamon + 0.13, v) * 0.6 + mist * 0.4);
                    if (Hash(tx, ty, 54) > 0.982) col = C(255, 255, 255);
                }
                else
                {
                    double t = (v - hamon) / (M_EDGE - hamon);
                    double fine = (Fbm(tx / 40.0, ty / 0.8, 55, 2) - 0.5) * 0.1;
                    col = Mul(Mix(C(130, 138, 152), C(226, 231, 240), t), 1.0 + fine);
                    if (Hash(tx, ty, 59) > 0.994) col = C(255, 255, 255);
                    if (v > M_EDGE - 0.11) col = C(250, 252, 255);                 // заточка
                    // Зазубрины на кромке.
                    if (v > M_EDGE - 0.22 && Hash(tx / 9, 0, 61) > 0.9 && Hash(tx / 9, 1, 61) * 0.22 > M_EDGE - v - 0.05) col = C(60, 64, 74);
                }
                foreach (var s in scratches)
                {
                    double d = PolyDistance(u, v, s);
                    if (d < 0.03) col = Mix(col, C(200, 206, 218), (1 - d / 0.03) * 0.45);
                }
                // Гравировка «斬月» под долом: иероглифы стоят вдоль клинка, верхом к острию.
                double eng = Engraving(u, v, zan, getsu);
                if (eng > 0.05)
                {
                    double lip = Engraving(u - 0.04, v - 0.04, zan, getsu);
                    col = Mix(col, burning ? C(150, 30, 10) : C(8, 9, 12), eng * 0.9);
                    if (lip < 0.2 && eng > 0.4) col = Mix(col, C(150, 156, 170), 0.5);
                }
                // Гнездо глаза: тёмное углубление, багровый обод и жилки, расходящиеся по стали.
                double ex = (u - M_EYE_U) / 2.5, ey = (v - M_EYE_V) / 1.42;
                double e = ex * ex + ey * ey;
                if (e < 1.0)
                {
                    col = Mix(C(40, 8, 10), C(10, 2, 3), 1.0 - e);
                }
                else if (e < 1.4)
                {
                    col = Mix(col, C(96, 8, 14), (1.4 - e) / 0.4 * 0.85);
                }
                else if (e < 3.2)
                {
                    double ang = Math.Atan2(ey, ex);
                    double vein = Math.Abs(Math.Sin(ang * 9 + Fbm(e * 2, ang * 3, 62, 2) * 4));
                    double reach = 1 - (e - 1.4) / 1.8;
                    if (vein < 0.07 * reach && v < hamon - 0.1) col = Mix(col, C(110, 10, 16), reach * 0.8);
                }
                if (burning)
                {
                    if (v < hamon - 0.1 && e >= 1.0) col = Mix(col, C(70, 12, 8), 0.28);
                    double crack = BladeCrack(u, v) + (Fbm(tx / 6.0, ty / 6.0, 63, 2) - 0.5) * 0.04;
                    if (crack < 0.3)
                    {
                        // На самом клинке — обугленный край трещины; жар рисует слой свечения.
                        col = Mix(col, C(30, 6, 4), Sat(1 - crack / 0.3) * 0.8);
                        Color heat = crack < 0.045 ? C(255, 240, 190) : (crack < 0.1 ? C(255, 150, 40) : C(220, 50, 12));
                        int alpha = Cl(255 * Sat(1.25 - crack / 0.24));
                        glow.Set(tx, y0 + ty, Color.FromArgb(alpha, heat.R, heat.G, heat.B));
                    }
                }
                c.Set(tx, y0 + ty, col);
            }
        }
    }

    /** Насколько точка клинка попадает в гравировку (0..1). */
    static double Engraving(double u, double v, double[,] zan, double[,] getsu)
    {
        const double V0 = -1.25, SIZE = 1.5;
        double gyAlong, gx = (v - V0) / SIZE;
        double[,] glyph;
        if (u >= 14.3 && u < 14.3 + SIZE) { glyph = zan; gyAlong = (14.3 + SIZE - u) / SIZE; }
        else if (u >= 16.0 && u < 16.0 + SIZE) { glyph = getsu; gyAlong = (16.0 + SIZE - u) / SIZE; }
        else return 0;
        if (gx < 0 || gx >= 1) return 0;
        int n = glyph.GetLength(0);
        return glyph[Math.Min(n - 1, (int)(gx * n)), Math.Min(n - 1, (int)(gyAlong * n))];
    }

    /** Кожа ската под оплёткой: светлая, в мелких бугорках. */
    static void SameSkin(Canvas c, int x0, int y0)
    {
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++)
            {
                Color col = Mul(C(230, 224, 206), 0.94 + Fbm(x / 9.0, y / 9.0, 71, 2) * 0.12);
                // Бугорок в каждой клетке 6×6 со случайным сдвигом: светлый верх, тёмный низ.
                int cx = x / 6, cy = y / 6;
                double nx = cx * 6 + 1.5 + Hash(cx, cy, 72) * 3, ny = cy * 6 + 1.5 + Hash(cx, cy, 73) * 3;
                double r = 1.4 + Hash(cx, cy, 74) * 1.2;
                double dx = x + 0.5 - nx, dy = y + 0.5 - ny, d = Math.Sqrt(dx * dx + dy * dy);
                if (d < r) col = Mul(col, 1.08 - (dx + dy) / r * 0.09);
                else if (d < r + 0.8) col = Mul(col, 0.84);
                c.Set(x0 + x, y0 + y, col);
            }
    }

    /** Чёрный шёлковый шнур оплётки: x — вдоль шнура, y — поперёк; косое плетение, края в тени. */
    static void Cord(Canvas c, int x0, int y0)
    {
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 64; x++)
            {
                double across = Math.Abs(y + 0.5 - 16) / 16.0;
                double twill = ((x + y * 2) % 6) < 3 ? 1.12 : 0.88;
                Color col = Mix(C(70, 66, 80), C(16, 15, 19), across * across * 1.2 + 0.1);
                col = Mul(col, twill * (0.94 + Hash(x, y, 75) * 0.12));
                if (across > 0.88) col = C(10, 9, 12);
                c.Set(x0 + x, y0 + y, col);
            }
    }

    /** Воронёная сталь: тёмная, с проковкой и светлой фаской сверху. */
    static void DarkMetal(Canvas c, int x0, int y0, int seed)
    {
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 64; x++)
            {
                double dent = Fbm(x / 6.0, y / 6.0, seed, 3);
                Color col = Mul(Mix(C(64, 66, 76), C(24, 25, 30), y / 31.0), 0.82 + dent * 0.36);
                if (y < 2) col = C(150, 154, 168);
                if (y > 29) col = C(12, 12, 15);
                if (Hash(x, y, seed + 1) > 0.992) col = C(190, 194, 206);
                c.Set(x0 + x, y0 + y, col);
            }
    }

    /** Латунь с гравированным узором волн и пятнами патины. */
    static void Brass(Canvas c, int x0, int y0)
    {
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 64; x++)
            {
                Color col = Mix(C(240, 204, 116), C(128, 88, 34), y / 31.0);
                col = Mul(col, 0.9 + Fbm(x / 8.0, y / 8.0, 81, 2) * 0.2);
                double wave = Math.Abs(Math.Sin(x * 0.42 + 2.2 * Math.Sin(y * 0.33)));
                if (wave < 0.09 && y > 3 && y < 28) col = Mul(col, 0.45);
                if (y < 2) col = C(255, 238, 170);
                if (Fbm(x / 4.0, y / 4.0, 82, 2) > 0.74) col = Mix(col, C(70, 110, 80), 0.4);
                c.Set(x0 + x, y0 + y, col);
            }
    }

    // ------------------------------------------------------------------ глаз в высоком разрешении

    /**
     * Детали 3D-глаза 256×256 (раскладка в долях как у прежней 32×32): белок (0,0)-(16,8), радужки — обычная (0,8),
     * горящая (8,8), сердитая (16,8) по 8×8, роговица с бликом (24,8)-(32,16), веки (0,16)-(16,20),
     * обод (16,16)-(24,20), чёрный (24,16)-(28,20), блик (28,16)-(32,20).
     */
    static Canvas EyePartsHD()
    {
        const int K = 8;
        var c = new Canvas(32 * K, 32 * K);
        for (int y = 0; y < 8 * K; y++)
            for (int x = 0; x < 16 * K; x++)
            {
                double fy = Math.Abs(y + 0.5 - 4 * K) / (4.0 * K), fx = Math.Abs(x + 0.5 - 8 * K) / (8.0 * K);
                Color col = Mix(C(40, 10, 14), C(9, 2, 4), fy * 0.8 + fx * 0.3);
                // Ветвистые алые прожилки от уголков к радужке.
                double vein = Math.Abs(Math.Sin(x * 0.11 + Math.Sin(y * 0.19) * 2.0 + Fbm(x / 14.0, y / 14.0, 31, 3) * 6.0));
                if (vein < 0.05 + fx * 0.05) col = Mix(col, C(170, 18, 26), 0.75 * fx + 0.2);
                c.Set(x, y, Mul(col, 0.94 + Fbm(x / 3.0, y / 3.0, 32, 2) * 0.12));
            }
        IrisHD(c, 0, 8 * K, 8 * K, C(255, 248, 160), C(255, 204, 40), C(232, 128, 0), C(70, 30, 0), 1);
        IrisHD(c, 8 * K, 8 * K, 8 * K, C(255, 255, 240), C(255, 228, 90), C(255, 110, 16), C(180, 24, 4), 2);
        IrisHD(c, 16 * K, 8 * K, 8 * K, C(255, 206, 70), C(236, 104, 14), C(180, 44, 6), C(60, 10, 4), 3);
        // Роговица: влажный блеск — мягкое пятно и яркий серп блика сверху слева.
        for (int y = 0; y < 8 * K; y++)
            for (int x = 0; x < 8 * K; x++)
            {
                double dx = (x + 0.5) / (4.0 * K) - 1, dy = (y + 0.5) / (4.0 * K) - 1;
                double r = Math.Sqrt(dx * dx + dy * dy);
                if (r > 1) continue;
                double a = 22 * (1 - r * r);
                double hx = dx + 0.38, hy = dy + 0.42, hr = Math.Sqrt(hx * hx + hy * hy);
                if (hr < 0.22) a = Math.Max(a, 235 * (1 - hr / 0.22));
                double sx = dx - 0.35, sy = dy - 0.3, sr = Math.Sqrt(sx * sx + sy * sy);
                if (sr < 0.1) a = Math.Max(a, 160 * (1 - sr / 0.1));
                c.Set(24 * K + x, 8 * K + y, CA(Cl(a), 255, 255, 255));
            }
        // Веки: кожистые складки, темнее к ресницам.
        for (int y = 0; y < 4 * K; y++)
            for (int x = 0; x < 16 * K; x++)
            {
                Color col = Mix(C(112, 80, 84), C(42, 28, 32), y / (4.0 * K - 1));
                double fold = Math.Abs(Math.Sin(x * 0.23 + y * 0.09 + Fbm(x / 10.0, y / 4.0, 33, 2) * 3));
                if (fold < 0.1) col = Mul(col, 0.78);
                c.Set(x, 16 * K + y, Mul(col, 0.93 + Hash(x, y, 34) * 0.14));
            }
        for (int y = 0; y < 4 * K; y++)
            for (int x = 0; x < 8 * K; x++)
            {
                Color col = Mix(C(190, 34, 38), C(84, 8, 12), y / (4.0 * K - 1));
                c.Set(16 * K + x, 16 * K + y, Mul(col, 0.9 + Fbm(x / 4.0, y / 4.0, 35, 2) * 0.2));
            }
        Paint(c, 24 * K, 16 * K, 28 * K, 20 * K, C(6, 3, 3), 0, 0);
        Paint(c, 28 * K, 16 * K, 32 * K, 20 * K, C(255, 255, 255), 0, 0);
        return c;
    }

    /** Радужка: светлое кольцо у зрачка, воротничок, лучистые волокна с крипами, золотые крапинки, тёмный лимб. */
    static void IrisHD(Canvas c, int x0, int y0, int size, Color center, Color mid, Color outer, Color limbal, int seed)
    {
        double half = size / 2.0;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
            {
                double dx = x + 0.5 - half, dy = y + 0.5 - half;
                double r = Math.Sqrt(dx * dx + dy * dy) / half;
                if (r > 1.0) continue;
                double ang = Math.Atan2(dy, dx);
                Color col = r < 0.3 ? center : (r < 0.6 ? Mix(center, mid, (r - 0.3) / 0.3) : (r < 0.86 ? Mix(mid, outer, (r - 0.6) / 0.26) : Mix(outer, limbal, (r - 0.86) / 0.14)));
                double fibers = 0.84 + 0.16 * Math.Cos(ang * 41 + Fbm(r * 4, ang * 2, 36 + seed, 2) * 5);
                double crypt = Fbm(r * 6, ang * 4, 40 + seed, 3);
                if (crypt < 0.3 && r > 0.4 && r < 0.85) fibers *= 0.72;
                if (Math.Abs(r - 0.46) < 0.025) fibers *= 1.25;                      // воротничок
                if (Hash(x, y, 44 + seed) > 0.985 && r > 0.35) col = Mix(col, C(255, 255, 210), 0.7);
                c.Set(x0 + x, y0 + y, Mul(col, fibers));
            }
    }

    // ------------------------------------------------------------------ маска Пустого в высоком разрешении

    /*
     * Маска — вылепленная 3D-оболочка (HollowMaskRenderer), текстура 256×256:
     *   лицо (0,0)-(256,160): θ −100°..100° → x (θ &gt; 0 — левая сторона маски), φ 80°..−85° → y;
     *   рог (0,160)-(256,224): вдоль рога → x, вокруг → y; зубы (0,224)-(64,256); торец кости (64,224)-(128,256);
     *   глубина глазниц (128,224)-(160,256).
     * Глаза — отдельно, hollow_mask_eyes.png (те же координаты лица), рисуются светом.
     */
    const double MASK_T0 = -100, MASK_T1 = 100, MASK_P0 = 80, MASK_P1 = -85;
    const double MASK_EYE_T = 22, MASK_EYE_P = 2, MASK_EYE_DT = 12.5, MASK_EYE_DP = 7.5;

    static double MaskX(double theta) { return (theta - MASK_T0) / (MASK_T1 - MASK_T0) * 256; }
    static double MaskY(double phi) { return (MASK_P0 - phi) / (MASK_P0 - MASK_P1) * 160; }

    /** Внутри глазницы (эллипс, наружный уголок опущен): 0 — центр, 1 — край. */
    static double MaskEye(double theta, double phi)
    {
        double best = 99;
        foreach (int side in new[] { -1, 1 })
        {
            double dt = theta - side * MASK_EYE_T;
            double dp = phi - MASK_EYE_P + side * dt * 0.32;
            best = Math.Min(best, Math.Sqrt((dt / MASK_EYE_DT) * (dt / MASK_EYE_DT) + (dp / MASK_EYE_DP) * (dp / MASK_EYE_DP)));
        }
        return best;
    }

    /** Линия рта (φ в зависимости от θ): уголки чуть приподняты — оскал. */
    static double MaskMouth(double theta) { return -27 + 0.0035 * theta * theta; }

    static Canvas HollowMaskHD(int crack)
    {
        var c = new Canvas(256, 256);
        Color bone = C(240, 236, 226), red = C(176, 14, 22);
        // Трещины по стадиям: от левой глазницы по лбу, к виску, к челюсти (в градусах θ, φ).
        var cracks = new System.Collections.Generic.List<double[]>();
        if (crack >= 1)
        {
            cracks.Add(new[] { 31.0, 9.0, 34.0, 22.0, 29.0, 34.0, 33.0, 48.0, 27.0, 62.0 });
            cracks.Add(new[] { 34.0, 6.0, 46.0, 10.0, 55.0, 6.0, 66.0, 11.0 });
        }
        if (crack >= 2)
        {
            cracks.Add(new[] { 29.0, 34.0, 18.0, 41.0, 8.0, 38.0 });
            cracks.Add(new[] { 24.0, -6.0, 28.0, -18.0, 23.0, -30.0, 27.0, -36.0 });
            cracks.Add(new[] { -12.0, 30.0, -20.0, 18.0, -16.0, 8.0 });
        }
        if (crack >= 3)
        {
            cracks.Add(new[] { 46.0, 10.0, 52.0, -8.0, 47.0, -24.0, 55.0, -42.0, 50.0, -60.0 });
            cracks.Add(new[] { -24.0, -8.0, -30.0, -22.0, -26.0, -36.0 });
            cracks.Add(new[] { 8.0, 38.0, -4.0, 52.0, -14.0, 50.0, -24.0, 64.0 });
            cracks.Add(new[] { 0.0, -20.0, 6.0, -28.0, 2.0, -34.0 });
        }
        for (int y = 0; y < 160; y++)
            for (int x = 0; x < 256; x++)
            {
                double theta = MASK_T0 + (x + 0.5) / 256 * (MASK_T1 - MASK_T0);
                double phi = MASK_P0 - (y + 0.5) / 160 * (MASK_P0 - MASK_P1);
                // Кость: тёплые пятна, поры, тень к краям оболочки.
                Color col = Mul(bone, 0.92 + Fbm(x / 18.0, y / 18.0, 91, 3) * 0.12);
                col = Mix(col, C(222, 210, 186), Fbm(x / 40.0, y / 40.0, 92, 2) * 0.35);
                if (Hash(x, y, 93) > 0.93) col = Mul(col, 0.9);
                double edge = Math.Max(Smooth(70, 100, Math.Abs(theta)), Math.Max(Smooth(62, 80, phi), Smooth(-62, -85, phi)));
                col = Mul(col, 1 - edge * 0.22);
                // Тонкие старые трещинки кости.
                if (Math.Abs(Fbm(x / 9.0, y / 9.0, 94, 3) - 0.5) < 0.008) col = Mul(col, 0.86);
                // Алые полосы на левой стороне: от темени через глаз к челюсти, рваные края.
                double jag = (Fbm(phi / 6.0, 1, 95, 2) - 0.5) * 5;
                double s1 = Math.Abs(theta - (16 + 3 * Math.Sin(phi * 0.05) + jag)), s2 = Math.Abs(theta - (36 + 2 * Math.Sin(phi * 0.07 + 1) + jag * 0.8));
                bool stripe = (s1 < 4.2 && phi < 79 && phi > -72) || (s2 < 3.2 && phi < 70 && phi > -58);
                if (stripe)
                {
                    double brush = 0.85 + Fbm(x / 3.0, y / 9.0, 96, 2) * 0.3;
                    col = Mul(red, brush);
                }
                // Грязь в складках: вокруг глазниц, у рта, вдоль носа.
                double eye = MaskEye(theta, phi);
                if (eye < 1.0)
                {
                    col = C(8, 5, 6);
                }
                else if (eye < 1.4)
                {
                    col = Mix(col, C(60, 44, 40), (1.4 - eye) / 0.4 * 0.75);
                }
                double mouth = phi - MaskMouth(theta);
                if (Math.Abs(theta) < 38)
                {
                    if (Math.Abs(mouth) < 3.0) col = C(12, 6, 8);
                    else if (Math.Abs(mouth) < 5.0) col = Mix(col, C(70, 50, 46), (5 - Math.Abs(mouth)) / 2 * 0.6);
                }
                if (Math.Abs(theta) < 6 && phi < -4 && phi > -18) col = Mul(col, 1 - (1 - Math.Abs(theta) / 6) * 0.12);
                // Трещины: тёмная сердцевина, светлые сколы по краю.
                foreach (var p in cracks)
                {
                    double d = PolyDistance(theta, phi, p) + (Fbm(x / 3.0, y / 3.0, 97, 2) - 0.5) * 0.8;
                    if (d < 0.9) col = C(40, 28, 28);
                    else if (d < 1.6) col = Mix(col, C(255, 252, 244), 0.4);
                }
                // Отколотые куски: прозрачно, под ними видно лицо.
                bool gone = false;
                if (crack >= 2) gone |= Chunk(theta, phi, 38, 64, 9, 7, 98);
                if (crack >= 3)
                {
                    gone |= Chunk(theta, phi, 40, -58, 11, 9, 99);
                    gone |= Chunk(theta, phi, -32, -16, 6, 6, 100);
                    gone |= Chunk(theta, phi, -18, 66, 8, 6, 101);
                }
                c.Set(x, y, gone ? Color.FromArgb(0, 0, 0, 0) : col);
            }
        // Рог: костяные кольца, алая полоса сверху, темнеет к кончику.
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 256; x++)
            {
                double s = (x + 0.5) / 256, around = (y + 0.5) / 64;
                double ring = 0.9 + 0.1 * Math.Sin(x * 0.34 + Fbm(x / 20.0, y / 8.0, 102, 2) * 2);
                Color col = Mul(Mix(C(236, 230, 216), C(150, 136, 122), Smooth(0.45, 1.0, s)), ring * (0.94 + Fbm(x / 6.0, y / 6.0, 103, 2) * 0.12));
                if (Math.Abs(around - 0.5) < 0.08 + 0.04 * (1 - s) && s < 0.85) col = Mul(red, 0.9 + Hash(x, y, 104) * 0.15);
                if (s > 0.9) col = Mix(col, C(60, 44, 40), (s - 0.9) / 0.1);
                c.Set(x, 160 + y, col);
            }
        // Зубы: корень темнее, кончик почти белый.
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 64; x++)
            {
                Color col = Mix(C(196, 184, 160), C(252, 250, 242), y / 31.0);
                if (x % 16 == 0 || x % 16 == 15) col = Mul(col, 0.7);
                c.Set(x, 224 + y, Mul(col, 0.95 + Hash(x, y, 105) * 0.1));
            }
        Paint(c, 64, 224, 128, 256, C(206, 198, 182), 0.12, 106);
        Paint(c, 128, 224, 160, 256, C(6, 4, 5), 0.0, 0);
        return c;
    }

    /** Отколотый кусок: рваный эллипс вокруг (θ, φ). */
    static bool Chunk(double theta, double phi, double t0, double p0, double rt, double rp, int seed)
    {
        double dt = (theta - t0) / rt, dp = (phi - p0) / rp;
        double ang = Math.Atan2(dp, dt);
        double r = 0.75 + 0.35 * Fbm(ang * 2 + 10, 0, seed, 2) + 0.15 * Math.Sin(ang * 7);
        return dt * dt + dp * dp < r * r;
    }

    /** Глаза маски: жёлтые светящиеся радужки с узким зрачком в глубине глазниц (координаты лица). */
    static Canvas HollowMaskEyesHD()
    {
        var c = new Canvas(256, 256);
        for (int y = 0; y < 160; y++)
            for (int x = 0; x < 256; x++)
            {
                double theta = MASK_T0 + (x + 0.5) / 256 * (MASK_T1 - MASK_T0);
                double phi = MASK_P0 - (y + 0.5) / 160 * (MASK_P0 - MASK_P1);
                double e = MaskEye(theta, phi);
                if (e > 0.78) continue;
                double side = theta > 0 ? 1 : -1;
                double dt = (theta - side * (MASK_EYE_T - 1.5)) / 5.2, dp = (phi - MASK_EYE_P + 0.5) / 5.2;
                double r = Math.Sqrt(dt * dt + dp * dp);
                Color col;
                if (r < 1)
                {
                    col = Mix(C(255, 250, 170), C(255, 150, 20), r);
                    if (Math.Abs(dt) < 0.16 && Math.Abs(dp) < 0.8) col = C(30, 6, 0);
                }
                else
                {
                    double a = Sat(1.4 - r) * 0.55;
                    if (a <= 0) continue;
                    col = CA(Cl(255 * a), 255, 120, 10);
                }
                c.Set(x, y, col);
            }
        return c;
    }

    /** Шлейф удара: яркая передняя кромка, мягкий хвост, штрихи вдоль дуги (x — вдоль дуги, y — поперёк); цвет уже умножен на прозрачность. */
    static Canvas SlashTrail()
    {
        var c = new Canvas(128, 32);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 128; x++)
            {
                double across = (y + 0.5) / 32;
                double core = Math.Exp(-Math.Pow((across - 0.2) / 0.12, 2));
                double tail = Sat(1 - across) * 0.7;
                double streak = 0.75 + 0.25 * Fbm(x / 3.0, y / 12.0, 111, 2);
                double a = Sat(core + tail * streak) * Smooth(0, 0.06, across);
                // Свет шлейфа складывается с фоном, поэтому яркость — в самом цвете (а прозрачность не важна).
                c.Set(x, y, CA(Cl(255 * a), Cl(255 * a), Cl(255 * a), Cl(255 * a)));
            }
        return c;
    }
}
