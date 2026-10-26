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
        string[] names = { "zangetsu_closed", "zangetsu_open", "zangetsu_burning", "zangetsu_angry" };
        for (int state = 0; state < names.Length; state++)
        {
            Out(Sword(state), assets, "textures/item/" + names[state] + ".png", 8);
        }
        Out(Icon(), assets, "icon.png", 4);
        Out(Spirit(), assets, "textures/entity/spirit.png", 4);
        Out(SpiritEyes(), assets, "textures/entity/spirit_eyes.png", 4);
        Out(HollowMask(), assets, "textures/entity/hollow_mask.png", 8);
        Out(HollowMaskEyes(), assets, "textures/entity/hollow_mask_eyes.png", 8);
        Out(Getsuga(), assets, "textures/entity/getsuga.png", 8);
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
     * Маска Пустого (64×64) поверх головы игрока: шлем (0,10) с маской спереди, рога (32..58,10..26),
     * длинные рыжие волосы (0,26) и дыра в груди (20,26).
     */
    static Canvas HollowMask()
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
        // Лицо маски (8..16 × 18..26).
        Paint(c, 8, 18, 16, 26, bone, 0.06, 3);
        for (int y = 18; y < 24; y++) c.Set(10, y, red);
        for (int y = 18; y < 22; y++) c.Set(11, y, red);
        c.Set(9, 20, C(10, 6, 8)); c.Set(10, 20, C(10, 6, 8)); c.Set(9, 21, C(10, 6, 8)); c.Set(10, 21, C(10, 6, 8));
        c.Set(13, 20, C(10, 6, 8)); c.Set(14, 20, C(10, 6, 8)); c.Set(13, 21, C(10, 6, 8)); c.Set(14, 21, C(10, 6, 8));
        c.Set(11, 22, shade); c.Set(12, 22, shade);
        for (int x = 8; x < 16; x++) { c.Set(x, 24, x % 2 == 0 ? bone : C(30, 22, 24)); c.Set(x, 25, x % 2 == 1 ? shade : C(30, 22, 24)); }
        // Рога: кость с тёмными кончиками.
        Gradient(c, 32, 10, 48, 18, bone, shade, 0.1, 4);
        Gradient(c, 32, 18, 48, 26, shade, C(150, 140, 128), 0.1, 5);
        Paint(c, 48, 10, 58, 15, C(40, 30, 28), 0.2, 6);
        // Длинные рыжие волосы.
        for (int y = 26; y < 49; y++)
            for (int x = 0; x < 20; x++)
            {
                double strand = Hash(x, 0, 7);
                Color col = Mix(C(244, 136, 44), C(186, 82, 20), strand);
                if (y > 44 && Hash(x, y, 8) > 0.5) col = Color.FromArgb(0, 0, 0, 0);
                c.Set(x, y, col.A == 0 ? col : Mul(col, 1.0 + (Hash(x, y, 9) - 0.5) * 0.15));
            }
        // Дыра Пустого в груди: чёрный круг с тёмно-красным ободком.
        for (int y = 26; y < 30; y++)
            for (int x = 20; x < 28; x++)
            {
                double fx = ((x - 20) % 4) - 1.5, fy = (y - 26) - 1.5;
                double d = Math.Sqrt(fx * fx + fy * fy);
                c.Set(x, y, d < 1.3 ? C(4, 2, 4) : (d < 2.1 ? C(60, 8, 12) : Color.FromArgb(0, 0, 0, 0)));
            }
        return c;
    }

    static Canvas HollowMaskEyes()
    {
        var c = new Canvas(64, 64);
        // Жёлтые глаза в прорезях маски (лицо шлема — 8..16 × 18..26).
        c.Set(9, 20, C(255, 222, 60)); c.Set(10, 20, C(60, 20, 0));
        c.Set(9, 21, C(255, 180, 30)); c.Set(10, 21, C(255, 200, 40));
        c.Set(14, 20, C(255, 222, 60)); c.Set(13, 20, C(60, 20, 0));
        c.Set(14, 21, C(255, 180, 30)); c.Set(13, 21, C(255, 200, 40));
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
                    col = Mix(C(255, 236, 200), C(255, 255, 250), Sat(band / 4));
                    a = 1;
                }
                else
                {
                    double glow = Math.Exp(band / 3.2);
                    col = C(255, 70, 50);
                    a = glow * 0.75;
                }
                c.Set(x, y, Color.FromArgb(Cl(a * 255), col.R, col.G, col.B));
            }
        }
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
     * навершие внизу слева, остриё вверху справа. Рисуем в координатах клинка: u — вдоль, v — поперёк
     * (v &lt; 0 — обух сверху слева, v &gt; 0 — лезвие снизу справа).
     */
    static Canvas Sword(int state)
    {
        const int N = 64;
        var c = new Canvas(N, N);
        double s = Math.Sqrt(0.5);
        double px0 = 5.0, py0 = 59.0;
        var region = new int[N, N];
        for (int y = 0; y < N; y++)
        {
            for (int x = 0; x < N; x++)
            {
                double X = x + 0.5 - px0, Y = y + 0.5 - py0;
                double u = (X - Y) * s;
                double v = (X + Y) * s;
                double n = Hash(x, y, 7) - 0.5;
                Color col = Color.Empty;
                if (u >= 0 && u < 2.4 && Math.Abs(v) <= 3.3)
                {
                    // Навершие.
                    col = v < -1.6 ? C(118, 122, 134) : (v > 1.6 ? C(26, 27, 32) : C(52, 55, 63));
                    region[x, y] = 1;
                }
                else if (u >= 2.4 && u < 19.6 && Math.Abs(v) <= 2.7)
                {
                    // Оплётка: белые «ромбы» ткани между перекрещенными чёрными шнурами.
                    double p = 4.3;
                    double k = Math.Round((u - 2.4 - p / 2) / p);
                    double uc = 2.4 + p / 2 + k * p;
                    bool cloth = Math.Abs(u - uc) + Math.Abs(v) * 0.9 < p / 2 - 0.25;
                    if (cloth)
                    {
                        col = Mix(C(236, 232, 220), C(168, 162, 150), (v + 2.7) / 5.4);
                    }
                    else
                    {
                        col = v < -1.4 ? C(66, 60, 70) : C(22, 20, 25);
                    }
                    region[x, y] = 2;
                }
                else if (u >= 19.6 && u < 22.6 && Math.Abs(v) <= 3.8)
                {
                    // Муфта у основания клинка (хабаки).
                    col = v < -2.0 ? C(246, 214, 122) : (v > 1.8 ? C(112, 80, 30) : C(190, 148, 60));
                    region[x, y] = 3;
                }
                else if (u >= 22.6 && u <= 82.5)
                {
                    double edge = u < 68 ? 8.0 : 8.0 - (u - 68.0);
                    if (v >= -6.0 && v <= edge)
                    {
                        double hamon = 4.1 + 0.9 * Math.Sin(u * 0.55) + 0.35 * Math.Sin(u * 1.37 + 1.0);
                        hamon = Math.Min(hamon, edge - 1.6);
                        if (v < -5.1)
                        {
                            col = C(84, 89, 100);           // блик на обухе
                        }
                        else if (v < -4.4)
                        {
                            col = C(16, 17, 21);            // тёмный обух
                        }
                        else if (v < hamon - 0.45)
                        {
                            col = Mix(C(50, 53, 62), C(28, 30, 36), (v + 4.4) / 9.0);
                            col = Mul(col, 1.0 + n * 0.10);
                            // Дол (желобок) вдоль клинка — от глаза до острия.
                            if (u > 47 && u < 64 && v > -2.6 && v < -1.5) col = C(17, 18, 23);
                            else if (u > 47 && u < 64 && v >= -1.5 && v < -0.8) col = C(70, 75, 86);
                        }
                        else if (v < hamon + 0.45)
                        {
                            col = C(176, 182, 194);         // туманная линия хамона
                        }
                        else
                        {
                            double t = (v - hamon) / Math.Max(0.5, edge - hamon);
                            col = Mix(C(140, 147, 160), C(226, 231, 239), t);
                            if (v > edge - 0.95) col = C(252, 253, 255);
                            col = Mul(col, 1.0 + n * 0.05);
                        }
                        region[x, y] = 4;
                    }
                }
                if (col != Color.Empty) c.Set(x, y, col);
            }
        }

        // Тёмный контур по силуэту, как у ванильных предметов.
        var copy = (Color[,])c.Px.Clone();
        for (int y = 0; y < N; y++)
        {
            for (int x = 0; x < N; x++)
            {
                if (copy[x, y].A == 0) continue;
                bool border = !Filled(copy, x - 1, y) || !Filled(copy, x + 1, y) || !Filled(copy, x, y - 1) || !Filled(copy, x, y + 1);
                if (border) c.Set(x, y, Mix(copy[x, y], C(8, 8, 12), region[x, y] == 4 ? 0.55 : 0.7));
            }
        }

        // Глаз — в тёмной сердцевине у основания клинка, ровно по осям картинки.
        DrawEye(c, 32.5, 33.5, 1.0, state, region);
        if (state == EYE_BURNING) Cracks(c, region);
        return c;
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

    /** Раскалённые трещины от горящего глаза вдоль тёмной сердцевины клинка. */
    static void Cracks(Canvas c, int[,] region)
    {
        int[][] paths = {
            new[] { 39, 27, 41, 25, 42, 23, 44, 22, 45, 20, 47, 18, 48, 16 },
            new[] { 25, 39, 23, 41, 22, 42, 21, 44 },
            new[] { 40, 31, 42, 30, 44, 28, 46, 27, 48, 25 },
            new[] { 29, 27, 31, 25, 31, 23, 33, 21 },
        };
        foreach (var p in paths)
        {
            for (int i = 0; i + 3 < p.Length; i += 2)
            {
                Line(c, region, p[i], p[i + 1], p[i + 2], p[i + 3]);
            }
        }
    }

    static void Line(Canvas c, int[,] region, int xa, int ya, int xb, int yb)
    {
        int steps = Math.Max(Math.Abs(xb - xa), Math.Abs(yb - ya));
        for (int i = 0; i <= steps; i++)
        {
            int x = (int)Math.Round(xa + (xb - xa) * (double)i / Math.Max(1, steps));
            int y = (int)Math.Round(ya + (yb - ya) * (double)i / Math.Max(1, steps));
            if (x < 0 || y < 0 || x >= c.W || y >= c.H || region[x, y] != 4) continue;
            Color old = c.Px[x, y];
            if (old.R > 120 && old.G > 120) continue;  // серебряную кромку не трогаем
            c.Set(x, y, C(255, 120, 30));
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
