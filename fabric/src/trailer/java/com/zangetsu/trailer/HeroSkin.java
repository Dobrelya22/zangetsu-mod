package com.zangetsu.trailer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

/**
 * Облик героя в трейлере — чёрное косодэ и хакама синигами, белый пояс и ворот, алая перевязь меча,
 * чёрные волосы. Рисуется кодом (64×64, классическая раскладка скина), без файлов.
 */
public final class HeroSkin {
    private static final Identifier ID = Identifier.of("zangetsu_trailer", "hero_skin");
    private static final int ROBE = 0x202027;
    private static final int ROBE_DARK = 0x131318;
    private static final int ROBE_LIGHT = 0x33333C;
    private static final int WHITE = 0xDCD8CF;
    private static final int WHITE_DARK = 0xB5B1A8;
    private static final int STRAP = 0x6E0F0F;
    private static final int SKIN = 0xC79878;
    private static final int SKIN_DARK = 0xA47658;
    private static final int HAIR = 0x15100F;
    private static final int HAIR_LIGHT = 0x2B2220;
    private static final int STRAW = 0x7C6038;
    private static SkinTextures textures;

    private HeroSkin() {
    }

    public static SkinTextures textures() {
        if (textures == null) {
            MinecraftClient.getInstance().getTextureManager().registerTexture(ID, new NativeImageBackedTexture(draw()));
            textures = new SkinTextures(ID, null, null, null, SkinTextures.Model.WIDE, true);
        }
        return textures;
    }

    private static NativeImage draw() {
        NativeImage img = new NativeImage(64, 64, true);
        // ---- голова: лицо, волосы со всех сторон, макушка
        box(img, 0, 0, 8, 8, 8, HAIR, HAIR, HAIR, HAIR, HAIR, SKIN);
        face(img, 8, 8);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                // Виски: волосы сверху и сзади, кожа у лица.
                if (y >= 3 && x >= 5) {
                    set(img, x, 8 + y, y >= 6 ? SKIN_DARK : SKIN);
                }
                if (y >= 3 && x <= 2) {
                    set(img, 16 + x, 8 + y, y >= 6 ? SKIN_DARK : SKIN);
                }
                if (y == 7) {
                    set(img, 24 + x, 8 + y, SKIN_DARK);
                }
            }
        }
        hairOverlay(img);
        // ---- туловище: чёрное косодэ, белый ворот, белый пояс, алая перевязь
        box(img, 16, 16, 8, 12, 4, ROBE, ROBE, ROBE, ROBE, ROBE, ROBE);
        robeNoise(img, 16, 16, 24, 16);
        torsoFront(img, 20, 20);
        torsoBack(img, 32, 20);
        for (int x = 16; x < 40; x++) {
            set(img, x, 28, WHITE);
            set(img, x, 29, WHITE_DARK);
        }
        set(img, 23, 16, SKIN);
        set(img, 24, 16, SKIN);
        // ---- руки: широкие рукава, белые манжеты, кисти
        arm(img, 40, 16);
        arm(img, 32, 48);
        // ---- ноги: хакама, белые таби, соломенные сандалии
        leg(img, 0, 16);
        leg(img, 16, 48);
        return img;
    }

    private static void face(NativeImage img, int ox, int oy) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                set(img, ox + x, oy + y, y >= 6 ? SKIN_DARK : SKIN);
            }
        }
        int[] fringe = {3, 2, 3, 2, 2, 3, 2, 3};
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < fringe[x]; y++) {
                set(img, ox + x, oy + y, (x + y) % 3 == 0 ? HAIR_LIGHT : HAIR);
            }
        }
        // Брови, глаза (тёмная радужка, белок), тень носа, рот.
        set(img, ox + 1, oy + 3, HAIR);
        set(img, ox + 2, oy + 3, HAIR);
        set(img, ox + 5, oy + 3, HAIR);
        set(img, ox + 6, oy + 3, HAIR);
        set(img, ox + 1, oy + 4, 0xE8E6E0);
        set(img, ox + 2, oy + 4, 0x2A1C14);
        set(img, ox + 5, oy + 4, 0x2A1C14);
        set(img, ox + 6, oy + 4, 0xE8E6E0);
        set(img, ox + 3, oy + 5, SKIN_DARK);
        set(img, ox + 4, oy + 5, SKIN_DARK);
        set(img, ox + 3, oy + 6, 0x7E4E40);
        set(img, ox + 4, oy + 6, 0x7E4E40);
    }

    /** Взъерошенные пряди поверх головы (второй слой). */
    private static void hairOverlay(NativeImage img) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                if ((x * 5 + y * 3) % 4 != 0) {
                    set(img, 40 + x, y, (x + y) % 2 == 0 ? HAIR : HAIR_LIGHT);
                }
            }
            if (x % 2 == 0) {
                set(img, 40 + x, 8, HAIR);
                set(img, 56 + x, 8, HAIR);
                set(img, 56 + x, 9, HAIR);
            }
            set(img, 56 + x, 8 + (x % 3), HAIR_LIGHT);
            if (x % 3 != 1) {
                set(img, 32 + x, 8, HAIR);
                set(img, 48 + x, 8, HAIR);
            }
        }
    }

    private static void torsoFront(NativeImage img, int ox, int oy) {
        // Ворот: белая V до середины груди, в вырезе — кожа.
        int[][] collar = {{1, 6}, {2, 5}, {2, 5}, {3, 4}, {3, 4}};
        for (int y = 0; y < collar.length; y++) {
            int l = collar[y][0], r = collar[y][1];
            set(img, ox + l, oy + y, WHITE);
            set(img, ox + r, oy + y, WHITE);
            for (int x = l + 1; x < r; x++) {
                set(img, ox + x, oy + y, y < 3 ? SKIN : WHITE_DARK);
            }
        }
        // Перевязь меча с левого плеча к правому бедру.
        for (int y = 0; y < 8; y++) {
            int x = 7 - y;
            set(img, ox + x, oy + y, STRAP);
        }
    }

    private static void torsoBack(NativeImage img, int ox, int oy) {
        for (int y = 0; y < 8; y++) {
            set(img, ox + y, oy + y, STRAP);
        }
    }

    private static void arm(NativeImage img, int bx, int by) {
        box(img, bx, by, 4, 12, 4, ROBE, ROBE, ROBE, ROBE, ROBE, SKIN);
        robeNoise(img, bx, by, 16, 16);
        for (int x = bx; x < bx + 16; x++) {
            set(img, x, by + 4 + 9, WHITE);
            set(img, x, by + 4 + 10, SKIN);
            set(img, x, by + 4 + 11, SKIN_DARK);
        }
    }

    private static void leg(NativeImage img, int bx, int by) {
        box(img, bx, by, 4, 12, 4, ROBE, ROBE, ROBE, ROBE, ROBE, STRAW);
        robeNoise(img, bx, by, 16, 14);
        for (int x = bx; x < bx + 16; x++) {
            // Складки хакама.
            if ((x - bx) % 4 == 1) {
                for (int y = by + 4; y < by + 13; y++) {
                    set(img, x, y, ROBE_DARK);
                }
            }
            set(img, x, by + 4 + 10, WHITE);
            set(img, x, by + 4 + 11, STRAW);
        }
    }

    /**
     * Развёртка коробки скина (w×h×d) от угла (bx, by): верх, низ, правый бок, перед, левый бок, спина.
     */
    private static void box(NativeImage img, int bx, int by, int w, int h, int d, int top, int right, int front, int left, int back,
            int bottom) {
        fill(img, bx + d, by, w, d, top);
        fill(img, bx + d + w, by, w, d, bottom);
        fill(img, bx, by + d, d, h, right);
        fill(img, bx + d, by + d, w, h, front);
        fill(img, bx + d + w, by + d, d, h, left);
        fill(img, bx + d + w + d, by + d, w, h, back);
    }

    /** Ткань не плоская: лёгкие складки и блики. */
    private static void robeNoise(NativeImage img, int x0, int y0, int w, int h) {
        for (int x = x0; x < x0 + w; x++) {
            for (int y = y0; y < y0 + h; y++) {
                int hash = (x * 73856093) ^ (y * 19349663);
                int k = Math.floorMod(hash, 11);
                if (get(img, x, y) == ROBE) {
                    if (k == 0) {
                        set(img, x, y, ROBE_LIGHT);
                    } else if (k == 1 || k == 2) {
                        set(img, x, y, ROBE_DARK);
                    }
                }
            }
        }
    }

    private static void fill(NativeImage img, int x0, int y0, int w, int h, int rgb) {
        for (int x = x0; x < x0 + w; x++) {
            for (int y = y0; y < y0 + h; y++) {
                set(img, x, y, rgb);
            }
        }
    }

    private static void set(NativeImage img, int x, int y, int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        img.setColor(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
    }

    private static int get(NativeImage img, int x, int y) {
        int abgr = img.getColor(x, y);
        return ((abgr & 0xFF) << 16) | (abgr & 0xFF00) | ((abgr >> 16) & 0xFF);
    }
}
