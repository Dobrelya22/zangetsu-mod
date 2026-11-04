package com.zangetsu.client;

import com.zangetsu.network.CallingPayload;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** Первый сон на экране: чёрное расходится, широкий кадр, реплики фигуры появляются по буквам, а когда меч воткнут — подсказка о выборе. */
public final class CallingScene {
    private static final float BARS = 0.115F;

    private static boolean active;
    private static boolean hudWas;
    private static boolean hudTaken;
    private static boolean barsOn;
    private static float barsAt = -100.0F;
    private static boolean hint;
    private static String hintKey = "zangetsu.calling.hint";

    /** Бледное небо вместо мира: насколько оно проступило. */
    private static boolean voidOn;
    private static float voidAt = -100.0F;
    private static int voidTicks = 1;
    private static float hintAt;

    private static float fadeAt = -1.0F;
    private static int fadeTicks;
    private static boolean fadeOut;
    private static boolean black;

    private static String line;
    private static float lineAt;
    private static int lineTicks;
    private static int typed;

    private static float lookAt = -1.0F;
    private static int lookTicks;
    private static double lookX, lookY, lookZ;
    private static float lookYaw, lookPitch;

    private CallingScene() {
    }

    private static float now() {
        return ClientState.ticks + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
    }

    public static void receive(CallingPayload payload) {
        float now = now();
        switch (payload.event()) {
            case CallingPayload.BEGIN -> {
                active = true;
                setBars(true, now);
                hint = false;
                line = null;
                // Пока грузится мир, экран чёрный, и ещё немного после.
                fade(false, payload.ticks(), now + 12.0F);
            }
            case CallingPayload.SPEECH -> {
                line = payload.key();
                lineAt = now;
                lineTicks = payload.ticks();
                typed = 0;
            }
            case CallingPayload.BARS_OFF -> {
                setBars(false, now);
                line = null;
                hint = payload.ticks() == 1;
                hintKey = payload.key().isEmpty() ? "zangetsu.calling.hint" : payload.key();
                hintAt = now;
            }
            case CallingPayload.BARS_ON -> {
                setBars(true, now);
                hint = false;
            }
            case CallingPayload.LOOK -> {
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                if (player != null) {
                    lookAt = now;
                    lookTicks = Math.max(1, payload.ticks());
                    lookX = payload.x();
                    lookY = payload.y();
                    lookZ = payload.z();
                    lookYaw = player.getYaw();
                    lookPitch = player.getPitch();
                }
            }
            case CallingPayload.FADE_OUT -> fade(true, payload.ticks(), now);
            case CallingPayload.VOID_ON -> {
                voidOn = true;
                voidAt = now;
                voidTicks = Math.max(1, payload.ticks());
            }
            case CallingPayload.VOID_OFF -> {
                voidOn = false;
                voidAt = now;
                voidTicks = Math.max(1, payload.ticks());
            }
            case CallingPayload.END -> {
                setBars(false, now);
                hint = false;
                line = null;
                lookAt = -1.0F;
                active = false;
                if (black) {
                    fade(false, Math.max(1, payload.ticks()), now);
                }
            }
            default -> {
            }
        }
    }

    private static void fade(boolean out, int ticks, float at) {
        fadeOut = out;
        fadeTicks = Math.max(1, ticks);
        fadeAt = at;
        black = true;
    }

    private static void setBars(boolean on, float now) {
        if (barsOn != on) {
            barsOn = on;
            barsAt = now;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        // Интерфейс мешал бы кадру; подписи и черноту рисуем сами.
        if (on && !hudTaken) {
            hudWas = client.options.hudHidden;
            client.options.hudHidden = true;
            hudTaken = true;
        } else if (!on && hudTaken) {
            client.options.hudHidden = hudWas;
            hudTaken = false;
        }
    }

    /** Насколько мир сейчас скрыт бледным небом. */
    public static float voidAmount() {
        float k = smooth(voidAt, voidAt + voidTicks, now());
        return voidOn ? k : (voidAt < -50.0F ? 0.0F : 1.0F - k);
    }

    public static boolean voidFull() {
        return voidAmount() >= 0.999F;
    }

    public static void reset() {
        voidOn = false;
        voidAt = -100.0F;
        active = false;
        hint = false;
        line = null;
        lookAt = -1.0F;
        fadeAt = -1.0F;
        black = false;
        barsOn = false;
        if (hudTaken) {
            MinecraftClient.getInstance().options.hudHidden = hudWas;
            hudTaken = false;
        }
    }

    public static void render(DrawContext context, float tickDelta) {
        float now = ClientState.ticks + tickDelta;
        turnView(now);
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        float barsShown = barsOn ? smooth(barsAt, barsAt + 10.0F, now) : 1.0F - smooth(barsAt, barsAt + 14.0F, now);
        int bar = Math.round(h * BARS * barsShown);
        if (bar > 0) {
            context.fill(0, 0, w, bar, 0xFF000000);
            context.fill(0, h - bar, w, h, 0xFF000000);
        }
        if (line != null) {
            speech(context, now, w, h, barsShown > 0.5F);
        }
        if (hint) {
            hint(context, now, w);
        }
        if (fadeAt >= 0.0F) {
            float k = fadeOut ? smooth(fadeAt, fadeAt + fadeTicks, now) : 1.0F - smooth(fadeAt, fadeAt + fadeTicks, now);
            int alpha = (int) (k * 255.0F);
            if (alpha > 0) {
                context.fill(0, 0, w, h, (alpha << 24));
            }
            if (!fadeOut && now > fadeAt + fadeTicks) {
                fadeAt = -1.0F;
                black = false;
            }
        }
    }

    /** Плавно довести взгляд до точки: иначе игрок просто не увидит, как вонзается меч. */
    private static void turnView(float now) {
        if (lookAt < 0.0F) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        float k = (now - lookAt) / lookTicks;
        if (player == null || k >= 1.0F) {
            lookAt = -1.0F;
            return;
        }
        k = k * k * (3.0F - 2.0F * k);
        double dx = lookX - player.getX(), dy = lookY - player.getEyeY(), dz = lookZ - player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYaw(lookYaw + MathHelper.wrapDegrees(yaw - lookYaw) * k);
        player.setPitch(MathHelper.lerp(k, lookPitch, pitch));
    }

    private static void speech(DrawContext context, float now, int w, int h, boolean inBars) {
        float t = now - lineAt;
        if (t > lineTicks) {
            line = null;
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;
        String full = Text.translatable(line).getString();
        // Буквы появляются за две трети реплики, остальное время она просто стоит.
        int count = MathHelper.clamp((int) (full.length() * t / (lineTicks * 0.62F)), 0, full.length());
        if (count > typed) {
            // Низкий, будто издалека: не на каждую букву.
            if (count / 3 != typed / 3 && full.charAt(count - 1) != ' ') {
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5F + (count % 5) * 0.02F, 0.4F));
            }
            typed = count;
        }
        float alpha = smooth(0.0F, 4.0F, t) * (1.0F - smooth(lineTicks - 8.0F, lineTicks, t));
        int a = Math.max(4, (int) (alpha * 255.0F)) << 24;
        float scale = Math.min(1.5F, w * 0.86F / Math.max(1.0F, font.getWidth(full)));
        scale = Math.max(scale, 1.1F);
        List<OrderedText> lines = font.wrapLines(Text.literal(full), (int) (w * 0.86F / scale));
        float height = lines.size() * (font.fontHeight + 2) * scale;
        float y = inBars ? h - h * BARS * 0.5F - height * 0.5F : h * 0.76F - height * 0.5F;
        if (!inBars) {
            context.fill(0, (int) (y - 6.0F), w, (int) (y + height + 5.0F), ((int) (alpha * 130.0F) << 24));
        }

        var matrices = context.getMatrices();
        int left = count;
        for (int i = 0; i < lines.size(); i++) {
            // Строка переносится заранее, чтобы слова не прыгали, а показывается её начало.
            StringBuilder text = new StringBuilder();
            lines.get(i).accept((index, style, codePoint) -> {
                text.appendCodePoint(codePoint);
                return true;
            });
            int shown = Math.min(left, text.length());
            left -= text.length();
            // Перенос съедает пробел между строками.
            if (left > 0 && i + 1 < lines.size()) {
                left--;
            }
            if (shown <= 0) {
                break;
            }
            float width = font.getWidth(text.toString()) * scale;
            String part = text.substring(0, shown);
            matrices.push();
            matrices.translate((w - width) * 0.5F, y + i * (font.fontHeight + 2) * scale, 400.0F);
            matrices.scale(scale, scale, 1.0F);
            Text shownText = Text.literal(part).setStyle(Style.EMPTY);
            context.drawText(font, shownText, 1, 1, a | 0x7A0A14, false);
            context.drawText(font, shownText, 0, 0, a | 0xF2F0EC, false);
            matrices.pop();
        }
    }

    private static void hint(DrawContext context, float now, int w) {
        float alpha = smooth(hintAt, hintAt + 12.0F, now);
        int a = Math.max(4, (int) (alpha * 255.0F)) << 24;
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        Text take = Text.translatable(hintKey + ".take"), refuse = Text.translatable(hintKey + ".refuse");
        int half = Math.max(font.getWidth(take), font.getWidth(refuse)) / 2 + 8;
        context.fill(w / 2 - half, 9, w / 2 + half, 38, ((int) (alpha * 150.0F) << 24));
        context.drawCenteredTextWithShadow(font, take, w / 2, 14, a | 0xFFD24A);
        context.drawCenteredTextWithShadow(font, refuse, w / 2, 26, a | 0xC9C4C4);
    }

    private static float smooth(float a, float b, float x) {
        float t = MathHelper.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
