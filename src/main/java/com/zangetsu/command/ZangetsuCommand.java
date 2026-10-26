package com.zangetsu.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zangetsu.logic.Awakening;
import com.zangetsu.logic.Bond;
import com.zangetsu.logic.Dream;
import com.zangetsu.logic.HollowForm;
import com.zangetsu.logic.SwordStacks;
import com.zangetsu.logic.Sync;
import com.zangetsu.logic.ZangetsuData;
import com.zangetsu.registry.ModAttachments;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /zangetsu — команды для проверки (нужны читы): вызвать пробуждение, выдать меч,
 * поставить дружбу или уровень, обнулить время Пустого, отправить в сон, сбросить всё.
 */
public final class ZangetsuCommand {
    private ZangetsuCommand() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("zangetsu")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("awaken").executes(ZangetsuCommand::awaken))
                        .then(CommandManager.literal("give").executes(ZangetsuCommand::give))
                        .then(CommandManager.literal("bond")
                                .then(CommandManager.argument("value", IntegerArgumentType.integer(-1000, 100000))
                                        .executes(c -> setBond(c, IntegerArgumentType.getInteger(c, "value")))))
                        .then(CommandManager.literal("level")
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(1, Bond.MAX_LEVEL))
                                        .executes(c -> setBond(c, Bond.THRESHOLDS[IntegerArgumentType.getInteger(c, "level") - 1]))))
                        .then(CommandManager.literal("hollow").executes(ZangetsuCommand::refillHollow))
                        .then(CommandManager.literal("dream").executes(ZangetsuCommand::dream))
                        .then(CommandManager.literal("reset").executes(ZangetsuCommand::reset))));
    }

    private static int awaken(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (ModAttachments.data(player).bonded) {
            context.getSource().sendError(Text.translatable("zangetsu.command.already_bonded"));
            return 0;
        }
        player.setHealth(Math.min(player.getHealth(), 2.0F));
        Awakening.start(player);
        return 1;
    }

    private static int give(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ZangetsuData data = ModAttachments.data(player);
        data.bonded = true;
        data.lost = false;
        Awakening.applyHealthBonus(player);
        if (SwordStacks.find(player).isEmpty()) {
            SwordStacks.giveToHand(player, SwordStacks.create(player));
        }
        Sync.player(player);
        context.getSource().sendFeedback(() -> Text.translatable("zangetsu.command.given"), false);
        return 1;
    }

    private static int setBond(CommandContext<ServerCommandSource> context, int value) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ZangetsuData data = ModAttachments.data(player);
        if (!data.bonded) {
            context.getSource().sendError(Text.translatable("zangetsu.command.not_bonded"));
            return 0;
        }
        Bond.change(player, value - data.bond);
        context.getSource().sendFeedback(() -> Text.translatable("zangetsu.command.bond", data.bond, data.level()), false);
        return 1;
    }

    private static int refillHollow(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ModAttachments.data(player).hollowUsed = 0;
        Sync.player(player);
        context.getSource().sendFeedback(() -> Text.translatable("zangetsu.command.hollow"), false);
        return 1;
    }

    private static int dream(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (!ModAttachments.data(player).bonded) {
            context.getSource().sendError(Text.translatable("zangetsu.command.not_bonded"));
            return 0;
        }
        Dream.forceBegin(player);
        return 1;
    }

    private static int reset(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        HollowForm.end(player, false);
        for (ItemStack stack : SwordStacks.find(player)) {
            stack.setCount(0);
        }
        player.setAttached(ModAttachments.PLAYER, new ZangetsuData());
        Awakening.removeHealthBonus(player);
        Sync.player(player);
        context.getSource().sendFeedback(() -> Text.translatable("zangetsu.command.reset"), false);
        return 1;
    }
}
