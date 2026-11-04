package com.zangetsu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zangetsu.logic.Reaction;
import com.zangetsu.network.ActionPayload;
import com.zangetsu.network.ReactionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;

/**
 * Круг проверки реакции посреди экрана. Стрелка делает один оборот; успел кликнуть, пока она в зоне, — казнь. Алая дуга — хорошая зона, маленькая белая перед ней — отличная.
 * Здесь же звук и то, чем круг отвечает на клик: вспышка и слово.
 */
public final class ReactionCheck {
    public static final int NONE = 0, MISS = 1, GOOD = 2, GREAT = 3;
    /** Радиус круга (пиксели интерфейса) и сколько тиков виден ответ на клик. */
    private static final float RADIUS = 34.0F, ANSWER_TICKS = 13.0F;
    private static final int SEGMENTS = 96;
    private static final int RED = 0xE3142A, WHITE = 0xFFFFFF, IRON = 0xD8D8DE, GREY = 0x8A8A92;

    private static boolean active;
    private static int lead, spin;
    private static float from, great, good, start;
    private static int answer = NONE;
    private static float answerAt, answerNeedle;

    private ReactionCheck() {
    }

    static void receive(ReactionPayload payload) {
        if (payload.targetId() < 0) {
            active = false;
            answer = NONE;
            return;
        }
        active = true;
        answer = NONE;
        lead = payload.lead();
        spin = payload.spin();
        from = payload.from();
        great = payload.great();
        good = payload.good();
        start = ClientState.ticks + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        play(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.9F, 1.5F);
        play(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0F, 1.1F);
    }

    /** Круг на экране и стрелка ещё идёт. */
    public static boolean isActive() {
        return active && answer == NONE;
    }

    /** Где стрелка: доля круга (меньше нуля — ещё стоит). */
    public static float needle() {
        return needle(ClientState.ticks + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false));
    }

    private static float needle(float now) {
        return (now - start - lead) / Math.max(1, spin);
    }

    /** Нажата атака. true — клик ушёл в проверку, обычного удара не будет. */
    public static boolean onAttackKey() {
        if (!isActive()) {
            return false;
        }
        float t = needle();
        // Пока стрелка только тронулась, клик ещё относится к драке, а не к проверке.
        if (t >= Reaction.IGNORE_UNTIL) {
            answerAt(t);
        }
        return true;
    }

    /** Ответить кликом на положении {@code t}. Открыто для игровых проверок. */
    public static void answerAt(float t) {
        float greatEnd = from + great;
        int result = great > 0.0F && t >= from && t < greatEnd ? GREAT : (t >= greatEnd && t < greatEnd + good ? GOOD : MISS);
        finish(result, t);
        ClientPlayNetworking.send(new ActionPayload(ActionPayload.REACTION, Math.round(t * 10000.0F)));
    }

    /** Начало хорошей или отличной зоны и её середина: для игровых проверок. */
    public static float zoneStart(int kind) {
        return kind == GREAT ? from : from + great;
    }

    public static float zoneMiddle(int kind) {
        return kind == GREAT ? from + great * 0.5F : from + great + good * 0.5F;
    }

    private static void finish(int result, float t) {
        answer = result;
        answerNeedle = t;
        answerAt = ClientState.ticks + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        switch (result) {
            case GREAT -> {
                play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
                play(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0F, 2.0F);
            }
            case GOOD -> play(SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 1.3F);
            default -> play(SoundEvents.ENTITY_ITEM_BREAK, 0.7F, 0.7F);
        }
    }

    private static void play(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.world != null) {
            client.world.playSound(client.player, client.player.getX(), client.player.getY(), client.player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    /** Каждый тик: стрелка дошла до конца, а клика не было. */
    static void tick() {
        if (isActive() && needle() > 1.0F) {
            finish(MISS, 1.0F);
        }
    }

    static void reset() {
        active = false;
        answer = NONE;
    }

    static void render(DrawContext context, float tickDelta) {
        if (!active) {
            return;
        }
        float now = ClientState.ticks + tickDelta;
        float after = answer == NONE ? 0.0F : (now - answerAt) * (answer == GREAT ? 1.6F : 1.0F);
        if (after > ANSWER_TICKS) {
            active = false;
            answer = NONE;
            return;
        }
        float cx = context.getScaledWindowWidth() * 0.5F, cy = context.getScaledWindowHeight() * 0.5F;
        float appear = MathHelper.clamp((now - start) / 4.0F, 0.0F, 1.0F);
        appear = appear * appear * (3.0F - 2.0F * appear);
        float fade = answer == NONE ? appear : 1.0F - MathHelper.clamp((after - ANSWER_TICKS * 0.55F) / (ANSWER_TICKS * 0.45F), 0.0F, 1.0F);
        // Промах: круг дёргается и сереет.
        if (answer == MISS) {
            cx += MathHelper.sin(after * 3.1F) * 3.0F * (1.0F - after / ANSWER_TICKS);
        }
        // Круг ещё и влетает: сжимается до своего размера, пока проявляется.
        float radius = RADIUS * (1.0F + 0.4F * (1.0F - appear));
        float t = answer == NONE ? MathHelper.clamp(needle(now), 0.0F, 1.0F) : answerNeedle;

        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();

        // Тёмная подложка, чтобы круг читался на снегу и в огне.
        float dim = fade * 0.5F;
        disc(matrix, cx, cy, radius * 0.9F, 0x0A0406, dim, 0.0F, radius + 12.0F);
        boolean miss = answer == MISS;
        arc(matrix, cx, cy, radius, 1.6F, 0.0F, 1.0F, miss ? GREY : IRON, fade * 0.9F);
        // Риски через каждую восьмую: круг читается как шкала.
        for (int i = 0; i < 8; i++) {
            arc(matrix, cx, cy, radius - 2.6F, 3.4F, i / 8.0F - 0.0025F, i / 8.0F + 0.0025F, miss ? GREY : IRON, fade * 0.45F);
        }
        float greatEnd = from + great, goodEnd = greatEnd + good;
        // Хорошая зона: алая дуга с белой окантовкой и свечением.
        arc(matrix, cx, cy, radius, 13.0F, greatEnd, goodEnd, miss ? GREY : RED, fade * 0.2F);
        arc(matrix, cx, cy, radius, 6.0F, greatEnd, goodEnd, miss ? GREY : RED, fade * 0.95F);
        arc(matrix, cx, cy, radius - 3.6F, 1.0F, greatEnd, goodEnd, miss ? GREY : WHITE, fade * 0.85F);
        arc(matrix, cx, cy, radius + 3.6F, 1.0F, greatEnd, goodEnd, miss ? GREY : WHITE, fade * 0.85F);
        // Отличная зона: маленькая и белая, ярче всего на круге.
        if (great > 0.0F) {
            arc(matrix, cx, cy, radius, 15.0F, from, greatEnd, miss ? GREY : WHITE, fade * 0.28F);
            arc(matrix, cx, cy, radius, 8.0F, from, greatEnd, miss ? GREY : WHITE, fade);
        }
        // След за стрелкой и сама стрелка.
        if (t > 0.0F && answer == NONE) {
            trail(matrix, cx, cy, radius, Math.max(0.0F, t - 0.14F), t, fade);
        }
        needleLine(matrix, cx, cy, radius, t, answer == GREAT ? WHITE : (miss ? GREY : 0xFF2A38), fade);
        // Ответ: кольцо расходится от круга, а отличная ещё и заливает его белым.
        if (answer == GOOD || answer == GREAT) {
            float k = MathHelper.clamp(after / (ANSWER_TICKS * 0.7F), 0.0F, 1.0F);
            int color = answer == GREAT ? WHITE : RED;
            arc(matrix, cx, cy, radius + k * (answer == GREAT ? 34.0F : 20.0F), 5.0F * (1.0F - k) + 0.6F, 0.0F, 1.0F, color, (1.0F - k) * 0.95F);
            if (answer == GREAT) {
                disc(matrix, cx, cy, radius, WHITE, (1.0F - k) * 0.5F, 0.0F, radius);
            }
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        var font = MinecraftClient.getInstance().textRenderer;
        int alpha = Math.max(4, (int) (fade * 255.0F)) << 24;
        if (answer == NONE) {
            context.getMatrices().push();
            context.getMatrices().translate(cx, cy - 4.0F, 300.0F);
            context.drawCenteredTextWithShadow(font, Text.translatable("zangetsu.reaction.click"), 0, 0, alpha | 0xFFFFFF);
            context.getMatrices().pop();
        } else {
            String key = answer == GREAT ? "zangetsu.reaction.great" : (answer == GOOD ? "zangetsu.reaction.good" : "zangetsu.reaction.miss");
            int color = answer == GREAT ? 0xFFFFFF : (answer == GOOD ? 0xFF3040 : 0xA0A0A8);
            // Слово вбивается в экран: большое и быстро садится на место.
            float slam = MathHelper.clamp(after / 3.0F, 0.0F, 1.0F);
            float scale = (answer == GREAT ? 2.4F : 2.0F) * (1.0F + 0.8F * (1.0F - slam) * (1.0F - slam));
            context.getMatrices().push();
            context.getMatrices().translate(cx, cy + radius + 12.0F, 300.0F);
            context.getMatrices().scale(scale, scale, 1.0F);
            context.drawCenteredTextWithShadow(font, Text.translatable(key), 0, 0, alpha | color);
            context.getMatrices().pop();
        }
    }

    /** Угол на круге: 0 — верх, дальше по часовой. */
    private static float px(float cx, float r, float t) {
        return cx + r * MathHelper.sin(t * MathHelper.TAU);
    }

    private static float py(float cy, float r, float t) {
        return cy - r * MathHelper.cos(t * MathHelper.TAU);
    }

    /** Дуга с мягкими краями: края сглажены прозрачностью, поэтому круг не лесенкой на любом размере интерфейса. */
    private static void arc(Matrix4f matrix, float cx, float cy, float r, float width, float a, float b, int rgb, float alpha) {
        if (alpha <= 0.0F || b <= a) {
            return;
        }
        float half = width * 0.5F, soft = 0.7F;
        band(matrix, cx, cy, r - half - soft, r - half, a, b, rgb, 0.0F, alpha);
        band(matrix, cx, cy, r - half, r + half, a, b, rgb, alpha, alpha);
        band(matrix, cx, cy, r + half, r + half + soft, a, b, rgb, alpha, 0.0F);
    }

    private static void band(Matrix4f matrix, float cx, float cy, float r0, float r1, float a, float b, int rgb, float alpha0, float alpha1) {
        int steps = Math.max(2, (int) Math.ceil((b - a) * SEGMENTS));
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        int inner = color(rgb, alpha0), outer = color(rgb, alpha1);
        for (int i = 0; i <= steps; i++) {
            float t = MathHelper.lerp((float) i / steps, a, b);
            buffer.vertex(matrix, px(cx, r0, t), py(cy, r0, t), 0.0F).color(inner);
            buffer.vertex(matrix, px(cx, r1, t), py(cy, r1, t), 0.0F).color(outer);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    /** След: прозрачность нарастает к стрелке. */
    private static void trail(Matrix4f matrix, float cx, float cy, float r, float a, float b, float alpha) {
        if (b <= a) {
            return;
        }
        int steps = 18;
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            float k = (float) i / steps, t = MathHelper.lerp(k, a, b);
            int color = color(0xFF2A38, alpha * k * k * 0.55F);
            buffer.vertex(matrix, px(cx, r * 0.42F, t), py(cy, r * 0.42F, t), 0.0F).color(color);
            buffer.vertex(matrix, px(cx, r + 1.0F, t), py(cy, r + 1.0F, t), 0.0F).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    /** Стрелка: клин от середины к кругу, с белой серединой. */
    private static void needleLine(Matrix4f matrix, float cx, float cy, float r, float t, int rgb, float alpha) {
        float inner = r * 0.42F, outer = r + 6.0F;
        float nx = MathHelper.cos(t * MathHelper.TAU), ny = MathHelper.sin(t * MathHelper.TAU);
        wedge(matrix, cx, cy, inner, outer, t, nx, ny, 2.4F, rgb, alpha * 0.35F);
        wedge(matrix, cx, cy, inner, outer, t, nx, ny, 1.1F, rgb, alpha);
        wedge(matrix, cx, cy, inner, outer, t, nx, ny, 0.45F, WHITE, alpha);
    }

    private static void wedge(Matrix4f matrix, float cx, float cy, float inner, float outer, float t, float nx, float ny, float half, int rgb, float alpha) {
        int color = color(rgb, alpha);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        float ix = px(cx, inner, t), iy = py(cy, inner, t), ox = px(cx, outer, t), oy = py(cy, outer, t);
        buffer.vertex(matrix, ix - nx * half * 0.4F, iy - ny * half * 0.4F, 0.0F).color(color);
        buffer.vertex(matrix, ix + nx * half * 0.4F, iy + ny * half * 0.4F, 0.0F).color(color);
        buffer.vertex(matrix, ox - nx * half, oy - ny * half, 0.0F).color(color);
        buffer.vertex(matrix, ox + nx * half, oy + ny * half, 0.0F).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    /** Диск с плотной серединой до {@code solid} и мягким краем до {@code edge}. */
    static void disc(Matrix4f matrix, float cx, float cy, float solid, int rgb, float alpha, float inner, float edge) {
        band(matrix, cx, cy, inner, solid, 0.0F, 1.0F, rgb, alpha, alpha);
        if (edge > solid) {
            band(matrix, cx, cy, solid, edge, 0.0F, 1.0F, rgb, alpha, 0.0F);
        }
    }

    private static int color(int rgb, float alpha) {
        return (MathHelper.clamp((int) (alpha * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}
