package com.zangetsu.client;

import com.zangetsu.logic.Bond;
import com.zangetsu.logic.SwordStacks;
import com.zangetsu.network.ClientFxPayload;
import com.zangetsu.network.PlayerStatePayload;
import com.zangetsu.registry.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** Надписи на экране: уровень и дружба меча, сила Пустого ({@link ReiatsuHud}), проверка реакции ({@link ReactionCheck}) и вспышки сцен. */
public final class ZangetsuHud {
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};
    private static final ItemStack ICON = new ItemStack(ModItems.ZANGETSU);

    private ZangetsuHud() {
    }

    static void render(DrawContext context, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        float tickDelta = counter.getTickDelta(false);
        renderFlash(context, tickDelta);
        TooSlowScene.render(context, tickDelta);
        CallingScene.render(context, tickDelta);
        ReactionCheck.render(context, tickDelta);
        if (client.options.hudHidden) {
            return;
        }
        PlayerStatePayload state = ClientState.state;
        if (!state.bonded()) {
            return;
        }
        TextRenderer font = client.textRenderer;
        int x = 6, y = 6;
        context.drawItem(swordIcon(client), x, y);
        int level = Math.max(1, Math.min(Bond.MAX_LEVEL, state.level()));
        context.drawTextWithShadow(font, Text.translatable("zangetsu.hud.level", ROMAN[level - 1],
                Text.translatable(Bond.levelKey(level))), x + 20, y, state.bond() < 0 ? 0xFF5555 : 0xFFD24A);

        // Полоска дружбы до следующего уровня.
        int next = Bond.nextThreshold(level);
        int barX = x + 20, barY = y + 11, barW = 92;
        float progress;
        Text bondText;
        if (next > 0) {
            int from = Bond.THRESHOLDS[level - 1];
            progress = MathHelper.clamp((state.bond() - from) / (float) (next - from), 0.0F, 1.0F);
            bondText = Text.translatable("zangetsu.hud.bond", state.bond(), next);
        } else {
            progress = 1.0F;
            bondText = Text.translatable("zangetsu.hud.bond_max", state.bond());
        }
        context.fill(barX - 1, barY - 1, barX + barW + 1, barY + 3, 0xAA000000);
        context.fill(barX, barY, barX + Math.round(barW * progress), barY + 2, state.bond() < 0 ? 0xFFB01515 : 0xFFE8A317);
        context.drawTextWithShadow(font, bondText, barX, barY + 5, 0xBBBBBB);

        int line = barY + 16;
        boolean dreaming = client.world != null && client.world.getRegistryKey() == com.zangetsu.logic.Dream.WORLD;
        if (state.lost() && !dreaming) {
            context.drawTextWithShadow(font, Text.translatable("zangetsu.hud.lost"), x, line, 0xAAAAAA);
            line += 11;
        }
        if (level >= Bond.HOLLOW_LEVEL) {
            ReiatsuHud.render(context, font, state, level >= Bond.FINAL_LEVEL, ClientState.ticks + tickDelta);
        }
    }

    private static ItemStack swordIcon(MinecraftClient client) {
        PlayerInventory inventory = client.player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            if (SwordStacks.isSword(inventory.getStack(i))) {
                return inventory.getStack(i);
            }
        }
        return ICON;
    }

    /** Белая вспышка (сон кончается) или красные края (превращение в Пустого). */
    private static void renderFlash(DrawContext context, float tickDelta) {
        if (ClientState.flashStart < 0) {
            return;
        }
        float t = (ClientState.ticks + tickDelta - ClientState.flashStart) / Math.max(1, ClientState.flashDuration);
        if (t >= 1.0F) {
            ClientState.flashStart = -1;
            return;
        }
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        if (ClientState.flashType == ClientFxPayload.WHITE_FLASH) {
            // Нарастает, держится и медленно гаснет — игрок просыпается.
            float a = t < 0.3F ? t / 0.3F : (t < 0.55F ? 1.0F : 1.0F - (t - 0.55F) / 0.45F);
            context.fill(0, 0, w, h, ((int) (MathHelper.clamp(a, 0.0F, 1.0F) * 255) << 24) | 0xFFFFFF);
        } else if (ClientState.flashType == ClientFxPayload.RED_FLASH) {
            float a = (1.0F - t) * 0.7F;
            int edge = Math.max(w, h) / 6;
            for (int i = 0; i < edge; i += 2) {
                int alpha = (int) (a * 255 * (1.0F - i / (float) edge));
                int color = (alpha << 24) | 0x900000;
                context.fill(i, i, w - i, i + 2, color);
                context.fill(i, h - i - 2, w - i, h - i, color);
                context.fill(i, i + 2, i + 2, h - i - 2, color);
                context.fill(w - i - 2, i + 2, w - i, h - i - 2, color);
            }
        }
    }
}
