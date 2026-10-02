package com.evolt.teamecon.command;

import com.evolt.teamecon.api.QuestRewards;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class QuestRewardCommand {
    private QuestRewardCommand() { }
    public static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        boolean legacy = name.equals("teamecon_quest_reward");
        com.mojang.brigadier.arguments.ArgumentType<?> rewardType = legacy ? StringArgumentType.word() : net.minecraft.commands.arguments.ResourceLocationArgument.id();
        return Commands.literal(name).requires(source -> source.hasPermission(2))
                .then(Commands.argument("reward", rewardType)
                        .then(Commands.argument("amount", LongArgumentType.longArg(1, QuestRewards.MAX_AMOUNT))
                                .executes(ctx -> execute(ctx, ctx.getSource().getPlayerOrException(), legacy))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> execute(ctx, EntityArgument.getPlayer(ctx, "player"), legacy)))));
    }
    private static int execute(CommandContext<CommandSourceStack> ctx, ServerPlayer player, boolean legacy) throws CommandSyntaxException {
        try {
            String reward = legacy ? StringArgumentType.getString(ctx, "reward") : net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "reward").toString();
            var result = QuestRewards.grant(player, reward, LongArgumentType.getLong(ctx, "amount"));
            String key = "command.teamecon.reward." + result.status().name().toLowerCase(java.util.Locale.ROOT);
            if (result.status() == QuestRewards.Status.AWARDED) {
                ctx.getSource().sendSuccess(() -> Component.translatable(key, result.credited()), false);
                return (int) result.credited();
            }
            ctx.getSource().sendFailure(Component.translatable(key));
        } catch (IllegalArgumentException ex) {
            ctx.getSource().sendFailure(Component.translatable("command.teamecon.reward.invalid"));
        }
        return 0;
    }
}
