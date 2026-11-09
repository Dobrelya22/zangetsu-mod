package com.zangetsu.logic;

import com.zangetsu.registry.ModComponents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Глаз на клинке. Ночью или в темноте (свет 7 и меньше) он открывается — и тогда меч сражается сам.
 * В форме Пустого горит, а если дружба ушла в минус — сердито щурится и не помогает.
 */
public final class Eye {
    public static final int CLOSED = 0;
    public static final int OPEN = 1;
    public static final int BURNING = 2;
    public static final int ANGRY = 3;

    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private Eye() {
    }

    public static boolean isDark(ServerPlayerEntity player) {
        World world = player.getWorld();
        BlockPos pos = BlockPos.ofFloored(player.getEyePos());
        int light = world.getLightLevel(pos, world.getAmbientDarkness());
        return light <= 7 || (world.getDimension().hasSkyLight() && world.isNight());
    }

    /** Каким должен быть глаз у меча в руке. */
    public static int desired(ServerPlayerEntity player, ZangetsuData data) {
        if (data.hollowActive) {
            return BURNING;
        }
        if (data.bond < 0) {
            return ANGRY;
        }
        return isDark(player) ? OPEN : CLOSED;
    }

    /** Глаз открыт и меч в руке — способности работают. */
    public static boolean awake(ServerPlayerEntity player) {
        Integer state = LAST.get(player.getUuid());
        return state != null && (state == OPEN || state == BURNING) && SwordStacks.isHolding(player);
    }

    /** Обновить глаз на всех мечах игрока; меч не в руке спит. */
    public static void update(ServerPlayerEntity player, ZangetsuData data) {
        ItemStack held = SwordStacks.held(player);
        int target = held.isEmpty() ? CLOSED : desired(player, data);
        for (ItemStack stack : SwordStacks.find(player)) {
            int state = stack == held ? target : CLOSED;
            Integer current = stack.get(ModComponents.EYE);
            if (current == null || current != state) {
                stack.set(ModComponents.EYE, state);
            }
        }
        Integer previous = LAST.put(player.getUuid(), target);
        if (previous != null && previous != target && !held.isEmpty()) {
            if (target == OPEN) {
                player.sendMessage(Text.translatable("zangetsu.eye.open").formatted(Formatting.GOLD), true);
                Fx.sound(player, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, 0.7F, 0.6F);
            } else if (target == CLOSED && previous == OPEN) {
                player.sendMessage(Text.translatable("zangetsu.eye.close").formatted(Formatting.GRAY), true);
            }
        }
    }

    static void forget(ServerPlayerEntity player) {
        LAST.remove(player.getUuid());
    }
}
