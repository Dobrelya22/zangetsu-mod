package com.zangetsu.client;

import com.zangetsu.logic.Execution;
import com.zangetsu.network.AnimationPayload;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Сцена отличного попадания. Время будто встало: камера бросается к лицу врага, который ещё смотрит туда, где только что стоял игрок. За его спиной вспыхивает чёрно-алый след,
 * и появляется игрок: большим планом его маска за плечом врага, на экране «ты слишком медлителен» и имя врага. Камера разворачивается в сторону, и приходит удар.
 */
public final class TooSlowScene {
    /** Сколько тиков целиком: стойка и удар (сама длина удара зависит от приёма). */
    private static final int TOTAL = Execution.BEHIND_HOLD + 40;
    private static final float BARS = 0.115F;

    private static float start = -1.0F;
    private static Text name = Text.empty();
    private static boolean hudWas;

    private TooSlowScene() {
    }

    /** Игрока ещё не видно: он уже за спиной врага, но вынырнет только через миг. */
    public static boolean hidden(LivingEntity entity, float tickDelta) {
        ClientState.Animation animation = ClientState.animation(entity);
        return animation != null && animation.type() == AnimationPayload.EXECUTE_BEHIND && entity.age + tickDelta - animation.startAge() < Execution.BEHIND_APPEAR;
    }

    /** Телепорт разворачивает только взгляд, тело догнало бы его нескоро: он стоял бы спиной к врагу и с вывернутой головой. Пока идёт стойка, тело смотрит туда же, куда голова. */
    static void holdStance(LivingEntity player) {
        ClientState.Animation animation = ClientState.animation(player);
        if (animation != null && animation.type() == AnimationPayload.EXECUTE_BEHIND) {
            float yaw = player.getYaw();
            player.setBodyYaw(yaw);
            player.prevBodyYaw = yaw;
            player.setHeadYaw(yaw);
            player.prevHeadYaw = yaw;
        }
    }

    static void begin(LivingEntity target) {
        start = ClientState.ticks;
        holdStance(MinecraftClient.getInstance().player);
        name = target.getName();
        // Вся камера считается от игрока, а он стоит спиной к выходу: вперёд на {@code d} — это сам враг, разворот на 180° — смотреть ему в лицо.
        float d = (float) Execution.behindDistance(target);
        float head = (float) (target.getEyeY() - target.getY()) - 1.27F;
        float mask = 0.36F;
        int appear = Execution.BEHIND_APPEAR, hold = Execution.BEHIND_HOLD;
        CameraDirector.fly(TOTAL,
                // Рывок к лицу врага: он ещё ничего не понял.
                new CameraDirector.Key(0.0F, 208.0F, 3.0F, 5.6F, d, head),
                new CameraDirector.Key(appear - 1.0F, 206.0F, 2.0F, 3.0F, d, head),
                // Взгляд уходит ему за плечо: крупно маска, сам враг стоит сбоку на переднем плане и не видит, кто за спиной. Камера медленно наезжает.
                new CameraDirector.Key(appear + 6.0F, 224.0F, 0.0F, 2.5F, d * 0.1F, mask),
                new CameraDirector.Key(hold - 2.0F, 216.0F, -1.0F, 2.05F, d * 0.05F, mask),
                // Камера уходит в сторону: удар виден целиком.
                new CameraDirector.Key(hold + 7.0F, 262.0F, 7.0F, 4.6F, d * 0.55F, -0.15F),
                new CameraDirector.Key(TOTAL, 280.0F, 9.0F, 5.2F, d * 0.55F, -0.2F));
        // Интерфейс мешал бы кадру.
        MinecraftClient client = MinecraftClient.getInstance();
        hudWas = client.options.hudHidden;
        client.options.hudHidden = true;
    }

    static void reset() {
        finish();
    }

    private static void finish() {
        if (start >= 0.0F) {
            MinecraftClient.getInstance().options.hudHidden = hudWas;
        }
        start = -1.0F;
    }

    static void render(DrawContext context, float tickDelta) {
        if (start < 0.0F) {
            return;
        }
        float now = ClientState.ticks + tickDelta - start;
        if (now > TOTAL) {
            finish();
            return;
        }
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        // Широкий кадр: полосы приходят сразу и уходят только в конце.
        float bars = smooth(0.0F, 5.0F, now) * (1.0F - smooth(TOTAL - 8.0F, TOTAL, now));
        int bar = Math.round(h * BARS * bars);
        // Время встало: мир темнеет, пока игрока нет, а его появление — белая вспышка.
        float dark = (1.0F - smooth(Execution.BEHIND_APPEAR - 1.0F, Execution.BEHIND_APPEAR + 1.0F, now)) * smooth(0.0F, 2.0F, now);
        if (dark > 0.0F) {
            context.fill(0, 0, w, h, ((int) (dark * 110.0F) << 24) | 0x100006);
        }
        float flash = 1.0F - smooth(Execution.BEHIND_APPEAR, Execution.BEHIND_APPEAR + 4.0F, now);
        if (now >= Execution.BEHIND_APPEAR && flash > 0.0F) {
            context.fill(0, 0, w, h, ((int) (flash * 200.0F) << 24) | 0xFFF2F2);
        }
        if (bar > 0) {
            context.fill(0, 0, w, bar, 0xFF000000);
            context.fill(0, h - bar, w, h, 0xFF000000);
        }
        caption(context, now, w, h);
    }

    /** Фраза вбивается в кадр рывком, под ней алая черта и имя врага. */
    private static void caption(DrawContext context, float now, int w, int h) {
        float from = Execution.BEHIND_APPEAR + 3.0F, to = Execution.BEHIND_HOLD + 2.0F;
        if (now < from || now > to + 5.0F) {
            return;
        }
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        float slam = smooth(from, from + 3.0F, now);
        float fade = 1.0F - smooth(to, to + 5.0F, now);
        int alpha = Math.max(4, (int) (slam * fade * 255.0F)) << 24;
        // Слово большое, но помещается в экран при любом размере интерфейса.
        Text phrase = Text.translatable("zangetsu.reaction.too_slow");
        float room = w * 0.86F / Math.max(1, font.getWidth(phrase));
        float base = Math.min(3.2F, room);
        float scale = base * (1.0F + 0.5F * (1.0F - slam));
        float shake = (1.0F - smooth(from + 3.0F, from + 8.0F, now)) * 2.4F;
        float bottom = h * (1.0F - BARS);
        float x = w * 0.5F + MathHelper.sin(now * 5.3F) * shake, y = bottom - font.fontHeight * base - 7.0F + MathHelper.cos(now * 4.7F) * shake;

        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 400.0F);
        matrices.scale(scale, scale, 1.0F);
        // Алая тень со сдвигом, поверх белая фраза: читается и на снегу, и на огне.
        context.drawText(font, phrase, -font.getWidth(phrase) / 2 + 1, 1, alpha | 0xC00A18, false);
        context.drawText(font, phrase, -font.getWidth(phrase) / 2, 0, alpha | 0xFFFFFF, false);
        matrices.pop();

        // Алая черта растёт из середины.
        float line = smooth(from + 1.0F, from + 6.0F, now) * fade;
        int half = Math.round(font.getWidth(phrase) * base * 0.5F * line);
        int lineY = Math.round(bottom - 2.0F);
        context.fill(Math.round(x) - half, lineY, Math.round(x) + half, lineY + 1, ((int) (line * 255.0F) << 24) | 0xE3142A);

        // Имя врага: заглавными и вразрядку.
        float nameAlpha = smooth(from + 3.0F, from + 8.0F, now) * fade;
        if (nameAlpha > 0.02F) {
            Text enemy = Text.literal(spaced(name.getString()));
            float nameScale = Math.min(1.6F, w * 0.6F / Math.max(1, font.getWidth(enemy)));
            matrices.push();
            matrices.translate(w * 0.5F, bottom + (h * BARS - font.fontHeight * nameScale) * 0.5F, 400.0F);
            matrices.scale(nameScale, nameScale, 1.0F);
            context.drawCenteredTextWithShadow(font, enemy, 0, 0, (Math.max(4, (int) (nameAlpha * 255.0F)) << 24) | 0xE8C9C9);
            matrices.pop();
        }
    }

    private static String spaced(String text) {
        return String.join(" ", text.toUpperCase(Locale.ROOT).split(""));
    }

    private static float smooth(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Показать сцену для живой цели: сущность может быть уже не у клиента. */
    static void begin(Entity entity) {
        if (entity instanceof LivingEntity living) {
            begin(living);
        }
    }
}
