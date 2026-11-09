package com.zangetsu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zangetsu.ZangetsuMod;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.network.PlayerStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Сила Пустого над сердцами — вместо таймера живой Зангецу. Клинок налит чёрно-алой реяцу, по нему текут жилы света;
 * в форме Пустого он пылает, а фронт раскалённого металла ползёт от острия к рукояти: за ним только обугленная сталь
 * и пепел. У рукояти — маска: дышит алым, вздрагивает и роняет осколки, когда трескается вместе с настоящей.
 * Когда сила не используется, клинок дремлет и еле тлеет; когда на сегодня всё истрачено — разбит и тёмен.
 *
 * <p>Всё рисуется процедурно, ячейками в полпикселя интерфейса, цвета считаются по шуму каждый кадр.
 */
final class ReiatsuHud {
    private static final Identifier MASK_FULL = ZangetsuMod.id("textures/gui/mask_full.png");
    private static final Identifier MASK_EMPTY = ZangetsuMod.id("textures/gui/mask_empty.png");
    private static final Identifier FINAL_FULL = ZangetsuMod.id("textures/gui/mask_final_full.png");
    private static final Identifier FINAL_EMPTY = ZangetsuMod.id("textures/gui/mask_final_empty.png");
    private static final Identifier MASK_CRACKS = ZangetsuMod.id("textures/gui/mask_cracks.png");
    /** Клинок лежит над рядом сердец (его ширина — 81): длина, толщина и размер ячейки в пикселях интерфейса. */
    private static final float BLADE_LENGTH = 66.0F, BLADE_THICK = 7.0F, CELL = 0.5F;
    private static final int MASK_SIZE = 20;

    /** Плавно показанная доля силы: сервер присылает остаток раз в секунду. */
    private static float shown = -1.0F;
    private static boolean wasActive;
    private static int lastCrack;
    private static float activatedAt = -1000.0F, crackedAt = -1000.0F;
    private static float lastTime;

    private ReiatsuHud() {
    }

    static void render(DrawContext context, TextRenderer font, PlayerStatePayload state, boolean finalForm, float time) {
        MinecraftClient client = MinecraftClient.getInstance();
        float fraction = MathHelper.clamp(state.hollowLeft() / (float) HollowForm.BUDGET, 0.0F, 1.0F);
        float dt = MathHelper.clamp(time - lastTime, 0.0F, 5.0F);
        lastTime = time;
        shown = shown < 0.0F ? fraction : MathHelper.lerp(1.0F - (float) Math.exp(-dt * 0.12F), shown, fraction);
        boolean active = state.hollowActive();
        boolean empty = state.hollowLeft() <= 0;
        int crack = empty ? 3 : HollowForm.crackFor(state.hollowLeft());
        if (active && !wasActive) {
            activatedAt = time;
        }
        if (crack > lastCrack || (wasActive && !active && empty)) {
            crackedAt = time;
        }
        wasActive = active;
        lastCrack = crack;

        int left = context.getScaledWindowWidth() / 2 - 91;
        int bottom = aboveHearts(client.player, context.getScaledWindowHeight()) - 1;
        float bladeX = left + 15.0F, bladeY = bottom - BLADE_THICK - 2.0F;
        float front = BLADE_LENGTH * shown + (noise(time * 0.3F, 7.1F) - 0.5F) * 1.5F;
        float heat = !active ? 0.0F : (crack >= 2 ? 1.0F + 0.35F * MathHelper.sin(time * 0.9F) : 1.0F);
        float age = time - activatedAt;

        RenderSystem.enableBlend();
        // Каждый слой интерфейса лежит на 200 выше предыдущего, и фон чата пишет глубину — ниже него клинок и маска скрылись бы.
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 4000.0F);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer quads = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        if (active) {
            flames(quads, matrix, bladeX, bladeY, front, heat, finalForm, time);
        }
        blade(quads, matrix, bladeX, bladeY, front, heat, active, empty, finalForm, time);
        if (active && age < 18.0F) {
            awakening(quads, matrix, bladeX, bladeY, front, age);
        }
        if (active) {
            embers(quads, matrix, bladeX, bladeY, front, heat, time);
        }
        context.draw();

        renderMask(context, left - 1, bottom - MASK_SIZE, active, empty, crack, finalForm, time);

        quads = context.getVertexConsumers().getBuffer(RenderLayer.getGui());
        matrix = context.getMatrices().peek().getPositionMatrix();
        if (active && age < 22.0F) {
            ring(quads, matrix, left + 9.0F, bottom - MASK_SIZE / 2.0F, age);
        }
        float crackAge = time - crackedAt;
        if (crackAge < 24.0F) {
            shards(quads, matrix, left + 9.0F, bottom - MASK_SIZE / 2.0F, crack, crackAge);
        }
        context.draw();

        // Подсказка — только пока сила не используется; никаких цифр.
        if (!active) {
            Text hint = empty ? Text.translatable("zangetsu.hud.hollow_empty")
                    : Text.translatable("zangetsu.hud.hollow_ready", KeyBindings.hollow.getBoundKeyLocalizedText());
            int alpha = empty ? 150 : (int) (170.0F + 60.0F * MathHelper.sin(time * 0.12F));
            context.getMatrices().push();
            context.getMatrices().translate(bladeX + BLADE_LENGTH, bladeY - 7.0F, 0.0F);
            context.getMatrices().scale(0.75F, 0.75F, 1.0F);
            context.drawTextWithShadow(font, hint, -font.getWidth(hint), 0, (alpha << 24) | (empty ? 0x888888 : 0xE04040));
            context.getMatrices().pop();
        }
        context.getMatrices().pop();
    }

    /** Верх самого верхнего ряда над хотбаром: сердца занимают столько рядов, сколько нужно здоровью, выше — броня. */
    private static int aboveHearts(ClientPlayerEntity player, int height) {
        float health = Math.max((float) player.getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH), player.getHealth());
        int rows = MathHelper.ceil((health + player.getAbsorptionAmount()) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        int top = height - 39 - (rows - 1) * rowHeight;
        return player.getArmor() > 0 ? top - 10 : top;
    }

    // ---------------------------------------------------------------- клинок

    /** Верх клинка на доле длины u: обух ровный, у острия чуть падает. */
    private static float spine(float u) {
        return u < 0.8F ? 0.0F : 0.8F * (u - 0.8F) / 0.2F;
    }

    /** Низ клинка: кромка ровная, у острия дугой поднимается к обуху, как у тесака. */
    private static float edge(float u) {
        return u < 0.76F ? BLADE_THICK : BLADE_THICK - (BLADE_THICK - spine(u)) * (float) Math.pow((u - 0.76F) / 0.24F, 1.5F);
    }

    private static void blade(VertexConsumer q, Matrix4f m, float x0, float y0, float front, float heat, boolean active, boolean empty, boolean finalForm, float time) {
        // Тёмный контур и латунная гарда у рукояти.
        int columns = (int) (BLADE_LENGTH / CELL);
        for (int c = -2; c <= columns + 1; c++) {
            float u = MathHelper.clamp(c * CELL / BLADE_LENGTH, 0.0F, 1.0F);
            rect(q, m, x0 + c * CELL, y0 + spine(u) - 0.5F, CELL, edge(u) - spine(u) + 1.0F, 0xE0060406);
        }
        rect(q, m, x0 - 1.5F, y0 - 1.5F, 1.5F, BLADE_THICK + 3.0F, 0xFF8A6A2A);
        rect(q, m, x0 - 1.5F, y0 - 1.5F, 0.5F, BLADE_THICK + 3.0F, 0xFFD8B860);

        float breath = 0.55F + 0.2F * MathHelper.sin(time * 0.09F);
        for (int c = 0; c < columns; c++) {
            float x = c * CELL, u = x / BLADE_LENGTH;
            float top = spine(u), bottom = edge(u);
            int rows = Math.max(1, (int) ((bottom - top) / CELL));
            for (int r = 0; r < rows; r++) {
                float y = top + r * CELL, across = (y - top) / Math.max(0.5F, bottom - top);
                int color;
                if (x > front || empty) {
                    color = burnt(x, y, across, time, x - front, empty);
                } else {
                    color = energy(x, y, across, active, heat, finalForm, breath, time);
                    // Раскалённый фронт — неровная кромка у края силы.
                    float d = front + (noise(y * 0.9F, time * 0.25F) - 0.5F) * 2.2F - x;
                    if (active && d < 2.6F) {
                        color = mix(color, d < 1.0F ? 0xFFFFEEB8 : 0xFFFF7A1E, 1.0F - Math.max(0.0F, d) / 2.6F);
                    }
                }
                rect(q, m, x0 + x, y0 + y, CELL, CELL, color);
            }
        }
    }

    /** Живой клинок: по чёрной стали текут жилы реяцу; у кромки — светлая полоса заточки. */
    private static int energy(float x, float y, float across, boolean active, float heat, boolean finalForm, float breath, float time) {
        float speed = active ? 0.11F : 0.025F;
        float n = noise(x * 0.17F - time * speed, y * 0.5F + time * speed * 0.3F) * 0.65F
                + noise(x * 0.41F - time * speed * 1.7F, y * 0.95F) * 0.35F;
        float vein = 1.0F - Math.abs(2.0F * n - 1.0F);
        vein = vein * vein * vein;
        int color;
        if (active) {
            color = mix(finalForm ? 0xFF050304 : 0xFF180508, 0xFFD41A20, vein * heat);
            if (vein * heat > 0.72F) {
                color = mix(color, 0xFFFFB050, (vein * heat - 0.72F) / 0.28F);
            }
        } else {
            color = mix(0xFF120608, 0xFF7A0E14, vein * breath);
        }
        if (across > 0.78F) {
            color = mix(color, active ? 0xFFFF5038 : 0xFF8A8C98, (across - 0.78F) / 0.22F * (active ? 0.75F : 0.6F));
        } else if (across < 0.12F) {
            color = mix(color, active ? 0xFF3A0A0C : 0xFF34353C, 0.7F);
        }
        return color;
    }

    /** То, что сила уже покинула: обугленная сталь, тлеющие крапинки у самого фронта. */
    private static int burnt(float x, float y, float across, float time, float behind, boolean empty) {
        int color = mix(0xFF1E1F24, 0xFF121216, across);
        if (across > 0.78F) {
            color = mix(color, 0xFF55565E, 0.7F);
        }
        float grain = noise(x * 1.3F, y * 1.7F);
        if (grain > 0.74F) {
            color = mix(color, 0xFF08080A, 0.6F);
        }
        if (empty) {
            // Трещины разбитого клинка.
            float crack = Math.abs(noise(x * 0.3F, y * 0.55F) - 0.5F);
            return crack < 0.018F ? 0xFF040404 : color;
        }
        if (behind < 7.0F && grain > 0.62F) {
            float glow = (1.0F - behind / 7.0F) * (0.5F + 0.5F * MathHelper.sin(time * 0.5F + x * 3.0F + y * 5.0F));
            color = mix(color, 0xFFD8461C, glow);
        }
        return color;
    }

    /** Пламя над живой частью клинка: алое у обычной формы; у финальной чёрное с алым краем. */
    private static void flames(VertexConsumer q, Matrix4f m, float x0, float y0, float front, float heat, boolean finalForm, float time) {
        int columns = (int) (front / CELL);
        for (int c = 0; c < columns; c++) {
            float x = c * CELL;
            float lick = noise(x * 0.24F, time * 0.22F) * 0.7F + noise(x * 0.6F, time * 0.41F) * 0.3F;
            float rise = (float) Math.exp(-(x - front) * (x - front) / 60.0F);
            float height = (1.5F + lick * lick * 9.0F) * (0.75F + 0.8F * rise) * heat * (finalForm ? 1.25F : 1.0F);
            int rows = (int) (height / CELL);
            for (int r = 0; r < rows; r++) {
                float k = (float) r / Math.max(1, rows);
                float a = (1.0F - k) * (1.0F - k) * 0.9F;
                int color = finalForm ? mix(0xFF060305, 0xFFE0181E, k * 1.4F) : mix(0xFFFF4A1E, 0xFF6A0408, k);
                rect(q, m, x0 + x, y0 - CELL - r * CELL, CELL, CELL, withAlpha(color, a));
            }
        }
    }

    /** Искры срываются с фронта и пепел летит с обугленной части. */
    private static void embers(VertexConsumer q, Matrix4f m, float x0, float y0, float front, float heat, float time) {
        for (int i = 0; i < 18; i++) {
            float seed = i * 12.9898F;
            float speed = 0.012F + 0.016F * fract(seed * 0.37F);
            float life = fract(time * speed + fract(seed));
            float start = front - fract(seed * 1.71F) * (i < 12 ? 10.0F : 30.0F);
            float x = start + (life * 5.0F + MathHelper.sin(life * 9.0F + seed)) * (i % 2 == 0 ? 1.0F : -0.4F);
            if (x < 0.0F || x > BLADE_LENGTH) {
                continue;
            }
            float a = (1.0F - life) * heat;
            int color = i % 5 == 0 ? 0xFF140A0C : (life < 0.3F ? 0xFFFFD27A : 0xFFFF5A20);
            rect(q, m, x0 + x, y0 - life * 15.0F, CELL, CELL, withAlpha(color, MathHelper.clamp(a, 0.0F, 1.0F)));
        }
    }

    /** Пробуждение: волна света пробегает от рукояти к острию. */
    private static void awakening(VertexConsumer q, Matrix4f m, float x0, float y0, float front, float age) {
        float at = front * (age / 12.0F), fade = 1.0F - age / 18.0F;
        int columns = (int) (front / CELL);
        for (int c = 0; c < columns; c++) {
            float x = c * CELL, d = Math.abs(x - at);
            if (d < 9.0F) {
                float u = x / BLADE_LENGTH, a = (1.0F - d / 9.0F) * fade;
                rect(q, m, x0 + x, y0 + spine(u), CELL, edge(u) - spine(u), withAlpha(0xFFFFF0D8, a));
            }
        }
    }

    // ---------------------------------------------------------------- маска

    private static void renderMask(DrawContext context, int x, int y, boolean active, boolean empty, int crack, boolean finalForm, float time) {
        Identifier full = finalForm ? FINAL_FULL : MASK_FULL, dark = finalForm ? FINAL_EMPTY : MASK_EMPTY;
        float crackAge = time - crackedAt;
        // Дышит в форме Пустого; вздрагивает, когда трескается; у последней черты дрожит не переставая.
        float shake = crackAge < 12.0F ? (1.0F - crackAge / 12.0F) * 1.6F * MathHelper.sin(crackAge * 2.6F) : 0.0F;
        if (active && crack >= 3) {
            shake += 0.5F * MathHelper.sin(time * 2.9F);
        }
        float bob = active ? 0.5F * MathHelper.sin(time * 0.13F) : 0.0F;
        float scale = MASK_SIZE / 32.0F;
        context.getMatrices().push();
        context.getMatrices().translate(x + shake, y + bob, 0.0F);
        context.getMatrices().scale(scale, scale, 1.0F);
        if (active) {
            // Алое свечение — та же маска, крупнее и красная, пульсирует.
            float pulse = 0.35F + 0.25F * MathHelper.sin(time * 0.25F) + (crack == 3 ? 0.25F * Math.abs(MathHelper.sin(time * 1.3F)) : 0.0F);
            context.setShaderColor(1.0F, 0.1F, 0.08F, pulse);
            context.getMatrices().push();
            context.getMatrices().translate(16.0F, 16.0F, 0.0F);
            context.getMatrices().scale(1.25F, 1.25F, 1.0F);
            context.getMatrices().translate(-16.0F, -16.0F, 0.0F);
            context.drawTexture(full, 0, 0, 0.0F, 0.0F, 32, 32, 32, 32);
            context.getMatrices().pop();
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (empty) {
            context.drawTexture(dark, 0, 0, 0.0F, 0.0F, 32, 32, 32, 32);
        } else {
            if (!active) {
                context.setShaderColor(0.72F, 0.7F, 0.72F, 1.0F);
            }
            context.drawTexture(full, 0, 0, 0.0F, 0.0F, 32, 32, 32, 32);
            context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (crack > 0) {
            context.drawTexture(MASK_CRACKS, 0, 0, (crack - 1) * 32.0F, 0.0F, 32, 32, 96, 32);
        }
        context.getMatrices().pop();
    }

    /** Кольцо-ударная волна вокруг маски при превращении. */
    private static void ring(VertexConsumer q, Matrix4f m, float cx, float cy, float age) {
        float radius = 4.0F + age * 1.4F, fade = 1.0F - age / 22.0F;
        for (int i = 0; i < 48; i++) {
            float a = MathHelper.TAU * i / 48.0F;
            rect(q, m, cx + MathHelper.cos(a) * radius, cy + MathHelper.sin(a) * radius, 1.0F, 1.0F, withAlpha(0xFFFF3A28, fade * 0.85F));
        }
    }

    /** Осколки кости разлетаются от маски и падают. */
    private static void shards(VertexConsumer q, Matrix4f m, float cx, float cy, int crack, float age) {
        int count = 6 + crack * 5;
        for (int i = 0; i < count; i++) {
            float seed = i * 7.31F + crack;
            float angle = fract(seed * 0.73F) * MathHelper.TAU, speed = 0.5F + fract(seed * 1.37F) * 0.9F;
            float x = cx + MathHelper.cos(angle) * speed * age, y = cy + MathHelper.sin(angle) * speed * age + 0.035F * age * age;
            float size = 0.5F + fract(seed * 2.11F);
            rect(q, m, x, y, size, size, withAlpha(i % 3 == 0 ? 0xFFB8B4BC : 0xFFF4F2EC, 1.0F - age / 24.0F));
        }
    }

    // ---------------------------------------------------------------- мелочи

    private static void rect(VertexConsumer q, Matrix4f m, float x, float y, float w, float h, int color) {
        q.vertex(m, x, y, 0.0F).color(color);
        q.vertex(m, x, y + h, 0.0F).color(color);
        q.vertex(m, x + w, y + h, 0.0F).color(color);
        q.vertex(m, x + w, y, 0.0F).color(color);
    }

    private static int withAlpha(int color, float alpha) {
        return ((int) (MathHelper.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24) | (color & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        t = MathHelper.clamp(t, 0.0F, 1.0F);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (a & 0xFF000000) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private static float fract(float x) {
        return x - MathHelper.floor(x);
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }

    /** Плавный шум 0..1. */
    private static float noise(float x, float y) {
        int xi = MathHelper.floor(x), yi = MathHelper.floor(y);
        float fx = x - xi, fy = y - yi;
        float sx = fx * fx * (3.0F - 2.0F * fx), sy = fy * fy * (3.0F - 2.0F * fy);
        float a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
        return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy;
    }
}
