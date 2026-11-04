// Яхве: настоящая скульптура. Здесь лепится карта высот (нос, усы, губы, складки плаща) и запекается её свет с тенями;
// в игре та же карта становится объёмной сеткой, поэтому он из настоящего 3D, но весь раскинут по небу широким листом.
// Подключается к textures.cs (тот же класс). Запуск отдельно: tools/textures.ps1 -Yhwach
public static partial class ZangetsuTextures
{
    const int SHEET = 1536, RELIEF = 384;
    /** Пикселей листа на единицу головы и где на листе середина лица и кончик носа. Те же числа в YhwachSheet.java. */
    const float UNIT = 225f, FACE_X = 768f, NOSE_Y = 414f;

    /** Глаза на тёмной массе (как на кадре) и на плаще: x, y, полуширина, полувысота, наклон. Те же числа в YhwachSheet.java. */
    static readonly float[][] EYE_SOCKETS = new float[][] {
        new float[] { 0.70f, 0.95f, 0.33f, 0.35f, -28f },
        new float[] { -0.85f, 0.82f, 0.22f, 0.29f, 20f },
        new float[] { -0.32f, 1.22f, 0.19f, 0.24f, 10f },
        new float[] { -0.28f, 0.72f, 0.21f, 0.24f, -8f },
        new float[] { 0.18f, 0.82f, 0.15f, 0.22f, 4f },
        new float[] { 1.06f, 0.26f, 0.27f, 0.19f, -24f },
        new float[] { -1.08f, 0.13f, 0.22f, 0.17f, 24f },
        new float[] { -0.70f, 0.45f, 0.13f, 0.11f, 16f },
        new float[] { -2.05f, -2.05f, 0.34f, 0.25f, 18f },
        new float[] { 2.20f, -1.95f, 0.38f, 0.27f, -20f },
        new float[] { -1.15f, -2.95f, 0.40f, 0.31f, 8f },
        new float[] { 0.95f, -2.65f, 0.34f, 0.47f, -6f },
        new float[] { 0.00f, -3.65f, 0.32f, 0.36f, 3f },
        new float[] { 2.40f, -3.25f, 0.30f, 0.22f, -14f },
        new float[] { -2.55f, -3.45f, 0.32f, 0.23f, 16f },
        new float[] { -0.35f, -2.25f, 0.24f, 0.21f, -12f },
        new float[] { 1.70f, -3.95f, 0.24f, 0.18f, 10f },
        new float[] { -1.70f, -4.05f, 0.25f, 0.19f, -9f },
    };

    public static void RunYhwach(string assets, string preview)
    {
        int n = SHEET * SHEET;
        float[] cloak = Mask(PaintCloakMask);
        float[] neck = Mask(PaintNeckMask);
        float[] collar = Mask(PaintCollarMask);
        float[] face = Mask(PaintFaceMask);
        float[] moustache = Mask(PaintMoustacheMask);
        float[] mass = Mask(PaintMassMask);
        float[] moustacheSoft = (float[])moustache.Clone();
        Blur(moustacheSoft, SHEET, SHEET, 3);
        float[] massSoft = (float[])mass.Clone();
        Blur(massSoft, SHEET, SHEET, 3);
        float[] coverSoft = new float[n];
        float[] cover = new float[n];
        for (int i = 0; i < n; i++)
        {
            cover[i] = System.Math.Max(System.Math.Max(System.Math.Max(cloak[i], neck[i]), collar[i]), System.Math.Max(System.Math.Max(face[i], moustache[i]), mass[i]));
            coverSoft[i] = cover[i];
        }
        Blur(coverSoft, SHEET, SHEET, 8);

        // Складки плаща: от плеч вниз и в стороны.
        var rnd = new System.Random(7741);
        int foldCount = 34;
        double[] foldTop = new double[foldCount], foldBottom = new double[foldCount], foldWide = new double[foldCount], foldTall = new double[foldCount], foldPhase = new double[foldCount];
        for (int i = 0; i < foldCount; i++)
        {
            foldBottom[i] = (rnd.NextDouble() * 2 - 1) * 3.4;
            foldTop[i] = foldBottom[i] * (0.32 + rnd.NextDouble() * 0.2);
            foldWide[i] = 0.12 + rnd.NextDouble() * 0.26;
            foldTall[i] = 0.035 + rnd.NextDouble() * 0.085;
            foldPhase[i] = rnd.NextDouble() * 6.28;
        }

        // ---- лепка
        float[] z = new float[n];
        double[] foldX = new double[foldCount];
        for (int py = 0; py < SHEET; py++)
        {
            double y = (NOSE_Y - py - 0.5) / UNIT;
            for (int f = 0; f < foldCount; f++)
            {
                double t = Sat((-0.95 - y) / 3.95);
                foldX[f] = foldTop[f] + (foldBottom[f] - foldTop[f]) * t + 0.1 * System.Math.Sin(y * 2.7 + foldPhase[f]) * t;
            }
            for (int px = 0; px < SHEET; px++)
            {
                int i = py * SHEET + px;
                if (coverSoft[i] <= 0.0f)
                {
                    z[i] = -0.5f;
                    continue;
                }
                double x = (px + 0.5 - FACE_X) / UNIT;
                double h = -0.2;
                if (cloak[i] > 0)
                {
                    double c = 0.05 + SculptShoulders(x, y);
                    double t = Sat((-0.95 - y) / 3.95);
                    for (int f = 0; f < foldCount; f++)
                    {
                        double d = (x - foldX[f]) / (foldWide[f] * (0.5 + t));
                        if (d > -1 && d < 1)
                        {
                            double q = 1 - d * d;
                            c += foldTall[f] * q * q * Sm(0, 0.12, t);
                        }
                    }
                    h = h + (c - h) * cloak[i];
                }
                if (neck[i] > 0)
                {
                    h = h + (SculptNeck(x, y) - h) * neck[i];
                }
                if (collar[i] > 0)
                {
                    h = h + (SculptCollar(x, y) - h) * collar[i];
                }
                if (face[i] > 0)
                {
                    h = h + (SculptFace(x, y) - h) * face[i];
                }
                h += 0.11 * moustacheSoft[i] * (0.8 + 0.2 * Streak(x * 1.2 - y * 0.8, y * 3.0 + x * 1.4, 311));
                if (massSoft[i] > 0)
                {
                    h = h + (SculptMass(x, y) - h) * massSoft[i];
                }
                // Край листа скруглён, иначе обрезан ножом.
                z[i] = (float)(h - 0.22 * (1 - Sm(0.3, 1.0, coverSoft[i])));
            }
        }

        // ---- тени от лепки: настоящие, луч идёт к свету по карте высот
        const double LX = 0.46, LY = 0.62, LZ = 0.635;
        float[] shadow = CastShadows(z, LX, LY, LZ);
        float[] blurred = (float[])z.Clone();
        Blur(blurred, SHEET, SHEET, 9);

        // ---- цвет
        var bmp = new System.Drawing.Bitmap(SHEET, SHEET, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
        byte[] px4 = new byte[n * 4];
        double hx = LX, hy = LY, hz = LZ + 1;
        double hl = System.Math.Sqrt(hx * hx + hy * hy + hz * hz);
        hx /= hl;
        hy /= hl;
        hz /= hl;
        for (int py = 1; py < SHEET - 1; py++)
        {
            for (int px = 1; px < SHEET - 1; px++)
            {
                int i = py * SHEET + px;
                if (cover[i] <= 0.002f)
                {
                    continue;
                }
                double x = (px + 0.5 - FACE_X) / UNIT, y = (NOSE_Y - py - 0.5) / UNIT;
                double nx = -(z[i + 1] - z[i - 1]) * 0.5 * UNIT, ny = (z[i + SHEET] - z[i - SHEET]) * 0.5 * UNIT, nz = 1.0;
                double nl = System.Math.Sqrt(nx * nx + ny * ny + nz * nz);
                nx /= nl;
                ny /= nl;
                nz /= nl;
                double d = System.Math.Max(0.0, nx * LX + ny * LY + nz * LZ);
                double fill = System.Math.Max(0.0, nx * -0.3 + ny * -0.62 + nz * 0.72);
                double lit = d * shadow[i];
                double spec = System.Math.Pow(System.Math.Max(0.0, nx * hx + ny * hy + nz * hz), 26) * shadow[i];
                double cavity = System.Math.Max(0.0, blurred[i] - z[i]);
                double ao = 1.0 - System.Math.Min(0.65, cavity * 3.2);

                double r = 0, g = 0, b = 0, cov = 0;
                // Плащ: почти чёрный с тёмным багрянцем на гребнях.
                Layer(ref r, ref g, ref b, ref cov, cloak[i], Ramp(lit * ao, 7, 5, 8, 26, 13, 17, 74, 34, 40, 0.1, 0.42, 0.95));
                Layer(ref r, ref g, ref b, ref cov, neck[i], Ramp(lit * ao, 84, 62, 96, 190, 146, 128, 236, 200, 176, 0.1, 0.34, 0.8));
                Layer(ref r, ref g, ref b, ref cov, collar[i], Ramp(lit * ao, 104, 104, 170, 214, 216, 238, 252, 252, 255, 0.1, 0.38, 0.72));
                double[] skin = Ramp((lit + fill * 0.16) * ao, 86, 64, 104, 186, 144, 130, 240, 204, 180, 0.08, 0.42, 0.92);
                skin[0] += spec * 90;
                skin[1] += spec * 84;
                skin[2] += spec * 78;
                Layer(ref r, ref g, ref b, ref cov, face[i], skin);
                double strand = Streak(x * 1.2 - y * 0.8, y * 3.0 + x * 1.4, 311);
                double glint = System.Math.Pow(d, 2.0) * shadow[i] * (0.35 + 0.65 * strand);
                Layer(ref r, ref g, ref b, ref cov, moustache[i], new double[] { 12 + 42 * glint, 10 + 36 * glint, 13 + 42 * glint });
                // Тёмная масса и волосы: чёрные, с холодным отливом вдоль прядей.
                double hair = Streak(x * 7.0, y * 1.1, 73);
                double sheen = System.Math.Pow(d, 3.0) * shadow[i] * (0.3 + 0.7 * hair);
                Layer(ref r, ref g, ref b, ref cov, mass[i], new double[] { 8 + 40 * sheen, 8 + 44 * sheen, 12 + 62 * sheen });
                px4[i * 4] = (byte)Cl(b);
                px4[i * 4 + 1] = (byte)Cl(g);
                px4[i * 4 + 2] = (byte)Cl(r);
                px4[i * 4 + 3] = (byte)Cl(cover[i] * 255);
            }
        }
        var rect = new System.Drawing.Rectangle(0, 0, SHEET, SHEET);
        var data = bmp.LockBits(rect, System.Drawing.Imaging.ImageLockMode.WriteOnly, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
        System.Runtime.InteropServices.Marshal.Copy(px4, 0, data.Scan0, px4.Length);
        bmp.UnlockBits(data);
        PaintDetails(bmp);
        SaveBitmap(bmp, System.IO.Path.Combine(assets, "textures/entity/yhwach.png"));

        // ---- рельеф для сетки: высота в красном и зелёном, покрытие в синем
        using (var relief = new System.Drawing.Bitmap(RELIEF, RELIEF, System.Drawing.Imaging.PixelFormat.Format32bppArgb))
        {
            int step = SHEET / RELIEF;
            byte[] out4 = new byte[RELIEF * RELIEF * 4];
            for (int ry = 0; ry < RELIEF; ry++)
            {
                for (int rx = 0; rx < RELIEF; rx++)
                {
                    double sum = 0, sumCover = 0;
                    for (int a = 0; a < step; a++)
                    {
                        for (int c = 0; c < step; c++)
                        {
                            int i = (ry * step + a) * SHEET + rx * step + c;
                            sum += z[i];
                            sumCover += cover[i];
                        }
                    }
                    int v = (int)System.Math.Max(0, System.Math.Min(65535, (sum / (step * step) + 1.0) / 3.0 * 65535));
                    int o = (ry * RELIEF + rx) * 4;
                    out4[o] = (byte)Cl(sumCover / (step * step) * 255);
                    out4[o + 1] = (byte)(v & 255);
                    out4[o + 2] = (byte)(v >> 8);
                    out4[o + 3] = 255;
                }
            }
            var rr = new System.Drawing.Rectangle(0, 0, RELIEF, RELIEF);
            var rd = relief.LockBits(rr, System.Drawing.Imaging.ImageLockMode.WriteOnly, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
            System.Runtime.InteropServices.Marshal.Copy(out4, 0, rd.Scan0, out4.Length);
            relief.UnlockBits(rd);
            SaveBitmap(relief, System.IO.Path.Combine(assets, "textures/entity/yhwach_relief.png"));
        }

        Out(YhwachEye(), assets, "textures/entity/yhwach_eye.png", 1);
        Out(YhwachPupil(), assets, "textures/entity/yhwach_pupil.png", 2);

        if (!string.IsNullOrEmpty(preview))
        {
            System.IO.Directory.CreateDirectory(preview);
            using (var all = new System.Drawing.Bitmap(SHEET, SHEET, System.Drawing.Imaging.PixelFormat.Format32bppArgb))
            using (var g = System.Drawing.Graphics.FromImage(all))
            {
                g.Clear(System.Drawing.Color.FromArgb(214, 217, 221));
                g.DrawImage(bmp, 0, 0, SHEET, SHEET);
                all.Save(System.IO.Path.Combine(preview, "yhwach_all.png"), System.Drawing.Imaging.ImageFormat.Png);
            }
        }
        bmp.Dispose();
    }

    static void SaveBitmap(System.Drawing.Bitmap bmp, string path)
    {
        System.IO.Directory.CreateDirectory(System.IO.Path.GetDirectoryName(path));
        bmp.Save(path, System.Drawing.Imaging.ImageFormat.Png);
    }

    static double Sm(double a, double b, double x)
    {
        return Smooth(a, b, x);
    }

    static void Layer(ref double r, ref double g, ref double b, ref double cov, float amount, double[] c)
    {
        if (amount <= 0) return;
        r += (c[0] - r) * amount;
        g += (c[1] - g) * amount;
        b += (c[2] - b) * amount;
        cov = System.Math.Max(cov, amount);
    }

    /** Три тона: тень, середина, свет. */
    static double[] Ramp(double lit, double r0, double g0, double b0, double r1, double g1, double b1, double r2, double g2, double b2, double a, double m, double e)
    {
        double k1 = Sm(a, m, lit), k2 = Sm(m, e, lit);
        return new double[] { r0 + (r1 - r0) * k1 + (r2 - r1) * k2, g0 + (g1 - g0) * k1 + (g2 - g1) * k2, b0 + (b1 - b0) * k1 + (b2 - b1) * k2 };
    }

    // ------------------------------------------------------------------ скульптура

    static double Ell(double x, double y, double cx, double cy, double rx, double ry, double rz, double off)
    {
        double a = (x - cx) / rx, b = (y - cy) / ry, q = 1 - a * a - b * b;
        return q <= 0 ? off - 1 : off + rz * System.Math.Sqrt(q);
    }

    static double SMax(double a, double b, double k)
    {
        double h = Sat(0.5 + 0.5 * (b - a) / k);
        return a + (b - a) * h + k * h * (1 - h);
    }

    static double SegDist(double x, double y, double x0, double y0, double x1, double y1)
    {
        double dx = x1 - x0, dy = y1 - y0;
        double t = Sat(((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy));
        double ex = x - x0 - dx * t, ey = y - y0 - dy * t;
        return System.Math.Sqrt(ex * ex + ey * ey);
    }

    static double Dent(double dist, double depth, double width)
    {
        double q = dist / width;
        return depth * System.Math.Exp(-q * q);
    }

    static double SculptFace(double x, double y)
    {
        double ax = System.Math.Abs(x);
        double h = Ell(x, y, 0, -0.12, 0.82, 1.05, 0.62, 0.30);
        h = SMax(h, Ell(x, y, 0, -0.60, 0.72, 0.52, 0.50, 0.36), 0.10);
        h = SMax(h, Ell(ax, y, 0.44, 0.02, 0.30, 0.24, 0.20, 0.70), 0.08);
        h = SMax(h, Ell(x, y, 0, -0.88, 0.32, 0.20, 0.22, 0.72), 0.08);
        h = SMax(h, Ell(ax, y, 0.22, -0.20, 0.30, 0.22, 0.14, 0.78), 0.07);
        // Длинный нос с горбинкой, тяжёлый кончик и крылья.
        h = SMax(h, Ell(x, y, 0, 0.36, 0.125, 0.54, 0.24, 0.84), 0.05);
        h = SMax(h, Ell(x, y, 0, 0.03, 0.155, 0.14, 0.22, 0.92), 0.05);
        h = SMax(h, Ell(ax, y, 0.17, -0.02, 0.09, 0.08, 0.09, 0.90), 0.035);
        // Губы.
        h = SMax(h, Ell(x, y, 0, -0.385, 0.35, 0.055, 0.055, 0.90), 0.025);
        h = SMax(h, Ell(x, y, 0, -0.475, 0.28, 0.07, 0.07, 0.885), 0.03);
        // Ноздри и складки от носа ко рту.
        h -= Dent(System.Math.Sqrt((ax - 0.085) * (ax - 0.085) + (y + 0.075) * (y + 0.075) * 2.2), 0.09, 0.04);
        h -= Dent(System.Math.Min(SegDist(ax, y, 0.22, -0.05, 0.38, -0.26), SegDist(ax, y, 0.38, -0.26, 0.53, -0.56)), 0.055, 0.035);
        // Усмешка: уголки рта приподняты.
        if (ax < 0.42)
        {
            h -= Dent(System.Math.Abs(y - (-0.43 + 0.24 * ax * ax)), 0.05, 0.014);
        }
        h -= Dent(System.Math.Abs(y + 0.575) + ax * 0.12, 0.03, 0.05);
        h -= Dent(System.Math.Sqrt(ax * ax * 3 + (y + 0.86) * (y + 0.86)), 0.015, 0.04);
        return h;
    }

    static double SculptNeck(double x, double y)
    {
        double q = System.Math.Max(0.0, 1 - x * x / 0.42);
        double h = 0.18 + 0.32 * System.Math.Sqrt(q);
        h += 0.04 * System.Math.Exp(-System.Math.Pow((System.Math.Abs(x) - 0.2 + (y + 1.1) * 0.08) / 0.05, 2));
        h -= 0.03 * System.Math.Exp(-System.Math.Pow(x / 0.04, 2));
        return h;
    }

    static double SculptCollar(double x, double y)
    {
        double ax = System.Math.Abs(x);
        double h = 0.34 + 0.42 * Sm(1.75, 0.55, ax);
        h += 0.022 * System.Math.Sin(ax * 7 + y * 2.3) * Sm(0.7, 1.3, ax);
        h += 0.01 * System.Math.Sin(y * 11 - ax * 5);
        return h;
    }

    static double SculptShoulders(double x, double y)
    {
        double ax = System.Math.Abs(x);
        double h = 0.0;
        h = System.Math.Max(h, Ell(ax, y, 1.5, -1.2, 1.6, 0.8, 0.42, 0.0));
        h = SMax(h, 0.12 * Sat((y + 5.0) / 4.0), 0.1);
        return h;
    }

    static double SculptMass(double x, double y)
    {
        double ax = System.Math.Abs(x);
        double h = Ell(x, y, 0, 0.85, 1.75, 1.35, 0.62, 0.42);
        h = SMax(h, Ell(ax, y, 1.7, -0.3, 1.5, 0.9, 0.3, 0.1), 0.2);
        h = SMax(h, 0.26, 0.1);
        // Комья и пряди.
        h += 0.055 * (TileNoise(x * 3.1 + 20, y * 3.1 + 20, 512, 901) - 0.5) + 0.035 * (Streak(x * 7.0, y * 1.1, 73) - 0.5);
        // Вокруг глаз масса припухает, сами глаза лежат в гнёздах.
        foreach (float[] e in EYE_SOCKETS)
        {
            double t = e[4] * System.Math.PI / 180.0, c = System.Math.Cos(t), s = System.Math.Sin(t);
            double a = ((x - e[0]) * c + (y - e[1]) * s) / e[2], b = (-(x - e[0]) * s + (y - e[1]) * c) / e[3];
            double r = System.Math.Sqrt(a * a + b * b);
            if (r < 2.2)
            {
                h += 0.06 * System.Math.Exp(-System.Math.Pow((r - 1.25) / 0.34, 2)) - 0.05 * System.Math.Exp(-System.Math.Pow(r / 0.8, 2));
            }
        }
        return h;
    }

    /** Полосатый шум вдоль прядей. */
    static double Streak(double x, double y, int seed)
    {
        return 0.6 * TileNoise(x * 4 + 64, y * 4 + 64, 512, seed) + 0.4 * TileNoise(x * 11 + 64, y * 11 + 64, 512, seed + 1);
    }

    static float[] CastShadows(float[] z, double lx, double ly, double lz)
    {
        int half = SHEET / 2;
        float[] small = new float[half * half];
        for (int y = 0; y < half; y++)
        {
            for (int x = 0; x < half; x++)
            {
                small[y * half + x] = z[y * 2 * SHEET + x * 2];
            }
        }
        double along = System.Math.Sqrt(lx * lx + ly * ly);
        double stepX = lx / along, stepY = -ly / along;
        double rise = lz / along / (UNIT / 2);
        float[] lit = new float[half * half];
        for (int y = 0; y < half; y++)
        {
            for (int x = 0; x < half; x++)
            {
                double h = small[y * half + x] + 0.012, worst = 0, dist = 1.5;
                for (int s = 0; s < 46; s++)
                {
                    int sx = (int)(x + stepX * dist), sy = (int)(y + stepY * dist);
                    if (sx < 0 || sy < 0 || sx >= half || sy >= half) break;
                    double over = small[sy * half + sx] - (h + rise * dist);
                    if (over > worst) worst = over;
                    dist *= 1.08;
                    dist += 0.8;
                }
                lit[y * half + x] = (float)(1 - Sat(worst / 0.035));
            }
        }
        float[] full = new float[SHEET * SHEET];
        for (int y = 0; y < SHEET; y++)
        {
            for (int x = 0; x < SHEET; x++)
            {
                full[y * SHEET + x] = lit[System.Math.Min(half - 1, y / 2) * half + System.Math.Min(half - 1, x / 2)];
            }
        }
        Blur(full, SHEET, SHEET, 2);
        return full;
    }

    static void Blur(float[] v, int w, int h, int r)
    {
        BoxBlur(v, w, h, r);
        BoxBlur(v, w, h, r);
    }

    static void BoxBlur(float[] v, int w, int h, int r)
    {
        float[] tmp = new float[v.Length];
        float scale = 1f / (2 * r + 1);
        for (int y = 0; y < h; y++)
        {
            float sum = 0;
            for (int x = -r; x <= r; x++) sum += v[y * w + System.Math.Max(0, System.Math.Min(w - 1, x))];
            for (int x = 0; x < w; x++)
            {
                tmp[y * w + x] = sum * scale;
                sum += v[y * w + System.Math.Min(w - 1, x + r + 1)] - v[y * w + System.Math.Max(0, x - r)];
            }
        }
        for (int x = 0; x < w; x++)
        {
            float sum = 0;
            for (int y = -r; y <= r; y++) sum += tmp[System.Math.Max(0, System.Math.Min(h - 1, y)) * w + x];
            for (int y = 0; y < h; y++)
            {
                v[y * w + x] = sum * scale;
                sum += tmp[System.Math.Min(h - 1, y + r + 1) * w + x] - tmp[System.Math.Max(0, y - r) * w + x];
            }
        }
    }

    // ------------------------------------------------------------------ формы

    static float PX(float x) { return FACE_X + x * UNIT; }
    static float PY(float y) { return NOSE_Y - y * UNIT; }

    /** Точки в единицах головы → пиксели листа. */
    static float[] U(params float[] xy)
    {
        float[] p = new float[xy.Length];
        for (int i = 0; i < xy.Length; i += 2)
        {
            p[i] = PX(xy[i]);
            p[i + 1] = PY(xy[i + 1]);
        }
        return p;
    }

    static float[] FlipX(float[] xy)
    {
        float[] m = new float[xy.Length];
        for (int i = 0; i < xy.Length; i += 2)
        {
            m[i] = -xy[i];
            m[i + 1] = xy[i + 1];
        }
        return m;
    }

    /** Полная замкнутая форма из правой половины (сверху вниз). */
    static float[] BothU(params float[] half)
    {
        float[] m = FlipX(half);
        float[] all = new float[half.Length * 2];
        System.Array.Copy(half, all, half.Length);
        for (int i = 0; i < half.Length / 2; i++)
        {
            all[half.Length + i * 2] = m[half.Length - 2 - i * 2];
            all[half.Length + i * 2 + 1] = m[half.Length - 1 - i * 2];
        }
        return all;
    }

    static System.Drawing.PointF[] Pts(float[] xy)
    {
        var p = new System.Drawing.PointF[xy.Length / 2];
        for (int i = 0; i < p.Length; i++) p[i] = new System.Drawing.PointF(xy[i * 2], xy[i * 2 + 1]);
        return p;
    }

    static void FillU(System.Drawing.Graphics g, float tension, params float[] unitXy)
    {
        using (var path = new System.Drawing.Drawing2D.GraphicsPath())
        using (var brush = new System.Drawing.SolidBrush(System.Drawing.Color.White))
        {
            path.AddClosedCurve(Pts(U(unitXy)), tension);
            g.FillPath(brush, path);
        }
    }

    static void Spline(float[] xy, float t, out float x, out float y)
    {
        int n = xy.Length / 2;
        float f = t * (n - 1);
        int i = System.Math.Min((int)f, n - 2);
        float s = f - i;
        int i0 = System.Math.Max(i - 1, 0), i2 = i + 1, i3 = System.Math.Min(i + 2, n - 1);
        float s2 = s * s, s3 = s2 * s;
        x = 0.5f * (2 * xy[i * 2] + (xy[i2 * 2] - xy[i0 * 2]) * s + (2 * xy[i0 * 2] - 5 * xy[i * 2] + 4 * xy[i2 * 2] - xy[i3 * 2]) * s2 + (3 * xy[i * 2] - xy[i0 * 2] - 3 * xy[i2 * 2] + xy[i3 * 2]) * s3);
        y = 0.5f * (2 * xy[i * 2 + 1] + (xy[i2 * 2 + 1] - xy[i0 * 2 + 1]) * s + (2 * xy[i0 * 2 + 1] - 5 * xy[i * 2 + 1] + 4 * xy[i2 * 2 + 1] - xy[i3 * 2 + 1]) * s2 + (3 * xy[i * 2 + 1] - xy[i0 * 2 + 1] - 3 * xy[i2 * 2 + 1] + xy[i3 * 2 + 1]) * s3);
    }

    /** Мазок в пикселях листа: ширина w0 → w1, посередине добавка belly. */
    static void Brush(System.Drawing.Graphics g, System.Drawing.Color c, float w0, float w1, float belly, float[] xy)
    {
        int steps = 22;
        var left = new System.Drawing.PointF[steps + 1];
        var right = new System.Drawing.PointF[steps + 1];
        for (int i = 0; i <= steps; i++)
        {
            float t = (float)i / steps, x, y, x2, y2;
            Spline(xy, t, out x, out y);
            Spline(xy, System.Math.Min(1f, t + 0.01f), out x2, out y2);
            float dx = x2 - x, dy = y2 - y;
            if (i == steps)
            {
                float xb, yb;
                Spline(xy, 0.99f, out xb, out yb);
                dx = x - xb;
                dy = y - yb;
            }
            float l = (float)System.Math.Sqrt(dx * dx + dy * dy);
            if (l < 1e-4f) l = 1f;
            float w = (w0 + (w1 - w0) * t + belly * (float)System.Math.Sin(t * System.Math.PI)) * 0.5f;
            left[i] = new System.Drawing.PointF(x - dy / l * w, y + dx / l * w);
            right[steps - i] = new System.Drawing.PointF(x + dy / l * w, y - dx / l * w);
        }
        var all = new System.Drawing.PointF[(steps + 1) * 2];
        left.CopyTo(all, 0);
        right.CopyTo(all, steps + 1);
        using (var brush = new System.Drawing.SolidBrush(c))
        {
            g.FillPolygon(brush, all);
        }
    }

    /** Белый мазок для маски: толщина в единицах головы. */
    static void BrushU(System.Drawing.Graphics g, float w0, float w1, float belly, params float[] unitXy)
    {
        Brush(g, System.Drawing.Color.White, w0 * UNIT, w1 * UNIT, belly * UNIT, U(unitXy));
    }

    static float[] Mask(System.Action<System.Drawing.Graphics> paint)
    {
        using (var bmp = new System.Drawing.Bitmap(SHEET, SHEET, System.Drawing.Imaging.PixelFormat.Format32bppArgb))
        {
            using (var g = System.Drawing.Graphics.FromImage(bmp))
            {
                g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
                g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.HighQuality;
                paint(g);
            }
            var rect = new System.Drawing.Rectangle(0, 0, SHEET, SHEET);
            var data = bmp.LockBits(rect, System.Drawing.Imaging.ImageLockMode.ReadOnly, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
            byte[] px = new byte[SHEET * SHEET * 4];
            System.Runtime.InteropServices.Marshal.Copy(data.Scan0, px, 0, px.Length);
            bmp.UnlockBits(data);
            float[] a = new float[SHEET * SHEET];
            for (int i = 0; i < a.Length; i++) a[i] = px[i * 4 + 3] / 255f;
            return a;
        }
    }

    static void PaintFaceMask(System.Drawing.Graphics g)
    {
        FillU(g, 0.5f, BothU(0, 0.9f, 0.5f, 0.82f, 0.74f, 0.3f, 0.76f, -0.1f, 0.72f, -0.5f, 0.57f, -0.86f, 0.32f, -1.06f, 0, -1.1f));
    }

    static void PaintNeckMask(System.Drawing.Graphics g)
    {
        FillU(g, 0.4f, BothU(0, -0.7f, 0.56f, -0.7f, 0.6f, -1.3f, 0.66f, -1.8f, 0.3f, -2.05f, 0, -2.1f));
    }

    static void PaintCollarMask(System.Drawing.Graphics g)
    {
        float[] right = new float[] { 0.60f, -0.62f, 0.86f, -0.34f, 1.32f, -0.60f, 1.66f, -1.02f, 1.50f, -1.45f, 1.06f, -1.72f, 0.52f, -1.98f, 0.22f, -1.88f, 0.44f, -1.25f };
        FillU(g, 0.35f, right);
        FillU(g, 0.35f, FlipX(right));
    }

    static void PaintCloakMask(System.Drawing.Graphics g)
    {
        FillU(g, 0.45f, BothU(0, -1.4f, 0.7f, -1.0f, 1.3f, -0.72f, 2.0f, -0.92f, 2.7f, -1.45f, 3.1f, -2.3f, 3.28f, -3.2f, 3.3f, -4.15f, 2.4f, -4.3f, 1.2f, -4.2f, 0, -4.25f));
        // Рваный подол и края.
        var rnd = new System.Random(5150);
        for (int i = 0; i < 78; i++)
        {
            float k = (i + (float)rnd.NextDouble() * 0.8f) / 78;
            float x = -3.3f + 6.6f * k;
            float y = -3.75f - (float)rnd.NextDouble() * 0.35f;
            float end = -4.35f - (float)(rnd.NextDouble() * rnd.NextDouble()) * 0.95f;
            float drift = ((float)rnd.NextDouble() - 0.5f) * 0.45f;
            float wave = ((float)rnd.NextDouble() - 0.5f) * 0.2f;
            BrushU(g, 0.2f + (float)rnd.NextDouble() * 0.2f, 0.004f, 0.06f, x, y, x + drift * 0.3f + wave, (y + end) * 0.5f, x + drift, end);
        }
        for (int side = -1; side <= 1; side += 2)
        {
            for (int i = 0; i < 16; i++)
            {
                float k = i / 15f;
                float y = -1.9f - k * 2.3f;
                float x = side * (2.75f + k * 0.5f);
                float reach = 0.25f + (float)rnd.NextDouble() * 0.28f;
                BrushU(g, 0.18f, 0.004f, 0.05f, x, y + 0.1f, x + side * reach * 0.6f, y - 0.1f, x + side * reach, y - 0.34f);
            }
        }
    }

    static void PaintMoustacheMask(System.Drawing.Graphics g)
    {
        float[] right = new float[] { 0.012f, -0.02f, 0.18f, 0.005f, 0.40f, -0.05f, 0.60f, -0.16f, 0.77f, -0.33f, 0.86f, -0.52f, 0.70f, -0.43f, 0.50f, -0.34f, 0.28f, -0.31f, 0.02f, -0.23f };
        FillU(g, 0.4f, right);
        FillU(g, 0.4f, FlipX(right));
        // Неровный нижний край и волоски сверху.
        var rnd = new System.Random(808);
        for (int side = -1; side <= 1; side += 2)
        {
            for (int i = 0; i < 44; i++)
            {
                float k = i / 43f;
                float x = side * (0.04f + k * 0.76f);
                float y = -0.27f - k * k * 0.18f;
                float len = 0.06f + (float)rnd.NextDouble() * 0.09f;
                BrushU(g, 0.035f, 0.003f, 0.012f, x, y + 0.06f, x + side * len * 0.45f, y - len * 0.5f, x + side * len * 0.9f, y - len);
            }
            for (int i = 0; i < 20; i++)
            {
                float k = i / 19f;
                float x = side * (0.12f + k * 0.56f);
                float y = -0.01f - k * k * 0.2f;
                float len = 0.03f + (float)rnd.NextDouble() * 0.05f;
                BrushU(g, 0.025f, 0.003f, 0.008f, x, y - 0.03f, x + side * len, y + len * 0.5f);
            }
        }
    }

    static void PaintMassMask(System.Drawing.Graphics g)
    {
        // Тёмная масса на верхней половине головы: сверху широкий купол, внизу вырез под нос и щёки.
        FillU(g, 0.5f, BothU(0, 1.72f, 0.6f, 1.66f, 1.1f, 1.42f, 1.5f, 0.98f, 1.66f, 0.4f, 1.58f, -0.12f, 1.28f, -0.5f, 0.98f, -0.68f, 0.82f, -0.36f, 0.76f, -0.05f, 0.66f, 0.22f, 0.42f, 0.34f, 0.17f, 0.44f, 0, 0.62f));
        var rnd = new System.Random(2718);
        // Рваный край над щеками, как короткие чёрные пряди.
        for (int side = -1; side <= 1; side += 2)
        {
            for (int i = 0; i < 16; i++)
            {
                float k = i / 15f;
                float x = side * (0.16f + k * 0.58f);
                float y = 0.46f - k * 0.26f;
                float len = 0.13f + (float)rnd.NextDouble() * 0.17f;
                float lean = side * (float)rnd.NextDouble() * 0.05f;
                BrushU(g, 0.06f, 0.003f, 0.02f, x, y + 0.07f, x + lean, y - len * 0.5f, x + lean * 2f, y - len);
            }
        }
        // Грива по обе стороны: не зеркальная, живые волосы.
        FillU(g, 0.5f, 1.3f, 1.22f, 1.95f, 1.02f, 2.45f, 0.55f, 2.78f, -0.1f, 2.72f, -0.8f, 2.28f, -1.32f, 1.7f, -1.72f, 1.25f, -1.6f, 1.0f, -0.6f);
        FillU(g, 0.5f, -1.3f, 1.22f, -1.9f, 0.95f, -2.5f, 0.4f, -2.92f, -0.35f, -2.92f, -1.1f, -2.45f, -1.72f, -1.75f, -2.02f, -1.2f, -1.75f, -1.0f, -0.6f);
        // Концы прядей: идут наружу и загибаются вниз.
        for (int side = -1; side <= 1; side += 2)
        {
            float cx = side * 1.7f, cy = -0.3f;
            for (int i = 0; i < 90; i++)
            {
                float k = (i + (float)rnd.NextDouble()) / 90f;
                float a = (float)((70 - k * 195) * System.Math.PI / 180.0);
                float ca = (float)System.Math.Cos(a), sa = (float)System.Math.Sin(a);
                float r0 = 0.7f + (float)rnd.NextDouble() * 0.45f;
                float len = 0.45f + (float)(rnd.NextDouble() * rnd.NextDouble()) * 1.05f;
                float x0 = cx + side * ca * r0, y0 = cy + sa * r0;
                float x1 = x0 + side * ca * len * 0.55f, y1 = y0 + sa * len * 0.55f;
                float b = a - 0.8f - (float)rnd.NextDouble() * 0.7f;
                float x2 = x1 + side * (float)System.Math.Cos(b) * len * 0.45f, y2 = y1 + (float)System.Math.Sin(b) * len * 0.45f;
                BrushU(g, 0.16f + (float)rnd.NextDouble() * 0.1f, 0.003f, 0.05f, x0, y0, x1, y1, x2, y2);
            }
            // Отдельные тонкие волоски, закрученные на концах.
            for (int i = 0; i < 16; i++)
            {
                float k = (float)rnd.NextDouble();
                float a = (float)((60 - k * 170) * System.Math.PI / 180.0);
                float ca = (float)System.Math.Cos(a), sa = (float)System.Math.Sin(a);
                float len = 1.0f + (float)rnd.NextDouble() * 0.9f;
                float x0 = cx + side * ca * 0.9f, y0 = cy + sa * 0.9f;
                float x1 = x0 + side * ca * len * 0.6f, y1 = y0 + sa * len * 0.6f;
                float b = a - 1.4f, c = a - 2.6f;
                float x2 = x1 + side * (float)System.Math.Cos(b) * len * 0.3f, y2 = y1 + (float)System.Math.Sin(b) * len * 0.3f;
                float x3 = x2 + side * (float)System.Math.Cos(c) * len * 0.13f, y3 = y2 + (float)System.Math.Sin(c) * len * 0.13f;
                BrushU(g, 0.045f, 0.002f, 0.012f, x0, y0, x1, y1, x2, y2, x3, y3);
            }
        }
    }

    /** Тонкая прорисовка поверх света: контуры, морщины, отдельные волоски и светлые пряди справа, как на кадре. */
    static void PaintDetails(System.Drawing.Bitmap bmp)
    {
        var rnd = new System.Random(31);
        using (var g = System.Drawing.Graphics.FromImage(bmp))
        {
            g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            System.Drawing.Color line = System.Drawing.Color.FromArgb(170, 44, 28, 44), soft = System.Drawing.Color.FromArgb(110, 44, 28, 44);
            // Нос: контур крыла и линия спинки.
            Brush(g, line, 1.5f, 1, 1.5f, U(0.20f, 0.06f, 0.255f, -0.02f, 0.20f, -0.10f, 0.11f, -0.09f));
            Brush(g, line, 1.5f, 1, 1.5f, U(-0.20f, 0.06f, -0.255f, -0.02f, -0.20f, -0.10f, -0.11f, -0.09f));
            Brush(g, soft, 1, 1, 1, U(-0.12f, 0.42f, -0.14f, 0.26f, -0.17f, 0.1f));
            // Морщины на щеках.
            foreach (float[] w in new float[][] { new float[] { 0.34f, 0.16f, 0.36f, 0.02f, 0.46f, -0.08f }, new float[] { 0.48f, 0.2f, 0.50f, 0.08f, 0.58f, -0.02f }, new float[] { 0.26f, -0.06f, 0.42f, -0.28f, 0.55f, -0.58f } })
            {
                Brush(g, soft, 1.2f, 1, 1, U(w));
                Brush(g, soft, 1.2f, 1, 1, U(FlipX(w)));
            }
            // Рот и складки усмешки.
            Brush(g, System.Drawing.Color.FromArgb(230, 30, 16, 24), 1, 1, 3, U(-0.40f, -0.40f, -0.2f, -0.425f, 0, -0.432f, 0.2f, -0.425f, 0.40f, -0.40f));
            Brush(g, line, 1.2f, 1, 1, U(0.40f, -0.36f, 0.45f, -0.42f, 0.47f, -0.5f));
            Brush(g, line, 1.2f, 1, 1, U(-0.40f, -0.36f, -0.45f, -0.42f, -0.47f, -0.5f));
            Brush(g, soft, 1, 1, 1, U(-0.2f, -0.59f, 0, -0.615f, 0.2f, -0.59f));
            // Челюсть и шея: сухожилия.
            Brush(g, soft, 1.5f, 1, 1, U(0.26f, -1.05f, 0.24f, -1.5f, 0.3f, -2.1f));
            Brush(g, soft, 1.5f, 1, 1, U(-0.26f, -1.05f, -0.24f, -1.5f, -0.3f, -2.1f));
            Brush(g, soft, 1.5f, 1, 1, U(0.46f, -0.86f, 0.44f, -1.3f, 0.5f, -1.8f));
            Brush(g, soft, 1.5f, 1, 1, U(-0.46f, -0.86f, -0.44f, -1.3f, -0.5f, -1.8f));
            // Волоски усов.
            for (int side = -1; side <= 1; side += 2)
            {
                for (int i = 0; i < 26; i++)
                {
                    float k = i / 25f;
                    float y = -0.03f - k * 0.2f;
                    int light = 44 + rnd.Next(30);
                    Brush(g, System.Drawing.Color.FromArgb(180, light, light - 8, light), 1.5f, 0.5f, 0.5f, U(side * 0.04f, y, side * 0.40f, y - 0.08f - k * 0.04f, side * (0.66f + k * 0.16f), y - 0.27f - k * 0.1f));
                }
            }
            // Светлые седые пряди справа, как на кадре.
            for (int i = 0; i < 16; i++)
            {
                float j = (float)rnd.NextDouble();
                float x0 = 1.62f + j * 0.35f, y0 = 1.0f - j * 1.15f;
                int light = 120 + rnd.Next(64);
                Brush(g, System.Drawing.Color.FromArgb(205, light, light - 8, light + 22), 1, 0.5f, 1.5f + (float)rnd.NextDouble() * 3, U(x0, y0, x0 + 0.45f, y0 - 0.14f - j * 0.1f, x0 + 0.8f, y0 - 0.5f - j * 0.2f, x0 + 0.88f, y0 - 0.95f - j * 0.2f));
            }
        }
    }

    // ------------------------------------------------------------------ глаза

    /** Белок: холодно-белый, с мягкой голубой тенью и лёгкими чернильными штрихами у края, как на кадре. */
    static Canvas YhwachEye()
    {
        int size = 256;
        Canvas c = new Canvas(size, size);
        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                double u = (x + 0.5) / size * 2 - 1, v = (y + 0.5) / size * 2 - 1;
                double r = System.Math.Sqrt(u * u + v * v);
                double a = System.Math.Atan2(v, u);
                double shade = Smooth(0.45, 1.0, r);
                double top = Smooth(-0.3, -0.95, v) * 0.34;
                Color col = C(Cl(244 - 66 * shade - 60 * top), Cl(248 - 46 * shade - 44 * top), Cl(255 - 16 * shade - 20 * top));
                double hatch = System.Math.Abs(System.Math.Sin(a * 37.0 + 3.0 * System.Math.Sin(a * 5.0)));
                col = Mix(col, C(36, 44, 70), Smooth(0.12, 0.0, hatch) * Smooth(0.78, 0.97, r) * 0.8);
                c.Set(x, y, col);
            }
        }
        return c;
    }

    /** Зрачок: чёрное пятно с неровным рваным краем. */
    static Canvas YhwachPupil()
    {
        int size = 128;
        Canvas c = new Canvas(size, size);
        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                double u = (x + 0.5) / size * 2 - 1, v = (y + 0.5) / size * 2 - 1;
                double a = System.Math.Atan2(v, u);
                double r = System.Math.Sqrt(u * u + v * v);
                double edge = 0.62 + 0.09 * System.Math.Sin(a * 3 + 1.0) + 0.06 * System.Math.Sin(a * 7 + 2.0) + 0.035 * System.Math.Sin(a * 17);
                double core = Smooth(edge + 0.04, edge - 0.04, r);
                double haze = Smooth(1.0, edge, r) * 0.25;
                c.Set(x, y, CA(Cl(System.Math.Max(core, haze) * 255), 22, 20, 34));
            }
        }
        return c;
    }
}
