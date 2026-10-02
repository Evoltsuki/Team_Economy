package com.evolt.teamecon.command;

import com.evolt.teamecon.TeamEconomyMod;
import com.evolt.teamecon.economy.EconomyService;
import com.evolt.teamecon.price.PriceService;
import com.evolt.teamecon.team.TeamUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

/**
 * /teamecon commands: inspecting the economy and trading. Outcomes come from the server,
 * so repeated or forged client requests cannot create money.
 */
public final class ModCommands {

    private ModCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        if (!net.neoforged.fml.ModList.get().isLoaded("teamecon_quests"))
            event.getDispatcher().register(QuestRewardCommand.command("teamecon_quest_reward"));
        event.getDispatcher().register(Commands.literal("teamecon")
                .then(QuestRewardCommand.command("reward"))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.teamecon.help"), false);
                    return 1;
                })
                .then(Commands.literal("claimboxes").executes(ctx -> {
                    var player=ctx.getSource().getPlayerOrException();
                    var result=TeamEconomyMod.get().shop().claimBlindBoxes(player);
                    player.inventoryMenu.sendAllDataToRemote();
                    if(player.containerMenu instanceof com.evolt.teamecon.shop.ShopMenu)com.evolt.teamecon.network.ShopNetwork.sendSync(player);
                    ctx.getSource().sendSuccess(()->com.evolt.teamecon.network.ModNetwork.formatMessage(result.detailKey(),result.detailArg()),false);
                    return 1;
                }))
                .then(Commands.literal("balance")
                        .executes(ctx -> balance(ctx.getSource())))
                .then(Commands.literal("price")
                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                .executes(ctx -> price(ctx))))
                .then(Commands.literal("sell")
                        .executes(ctx -> sell(ctx)))
                .then(Commands.literal("buy")
                        .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, com.evolt.teamecon.economy.MoneyMath.MAX_PURCHASE))
                                        .executes(ctx -> buy(ctx)))))
                .then(Commands.literal("pending")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> pending(ctx.getSource())))
                .then(Commands.literal("shop")
                        .executes(ctx -> shop(ctx.getSource())))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("kit").executes(ModCommands::adminMachine))
                        .then(Commands.literal("boxes").executes(ctx -> {
                            com.evolt.teamecon.shop.BoxAdminMenu.open(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("prices").executes(ctx -> {
                            com.evolt.teamecon.price.PriceAdminMenu.open(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("unlock").executes(ctx -> adminProgress(ctx, 5, true))
                                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> adminProgress(ctx, 5, true))))
                        .then(Commands.literal("advancements").executes(ctx -> adminProgress(ctx, 0, true))
                                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> adminProgress(ctx, 0, true))))
                        .then(Commands.literal("bypass")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> adminProgress(ctx, 0, BoolArgumentType.getBool(ctx, "enabled")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> adminProgress(ctx, 0, BoolArgumentType.getBool(ctx, "enabled"))))))
                        .then(Commands.literal("status").executes(ModCommands::adminStatus)
                                .then(Commands.argument("player", EntityArgument.player()).executes(ModCommands::adminStatus)))
                        .then(Commands.literal("level")
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> adminProgress(ctx, IntegerArgumentType.getInteger(ctx, "level"), null))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> adminProgress(ctx, IntegerArgumentType.getInteger(ctx, "level"), null)))))
                        .then(Commands.literal("reload").executes(ctx -> {
                            TeamEconomyMod.get().reloadServices(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.translatable("command.teamecon.reload_ok"), true);
                            return 1;
                        }))
                        .then(Commands.literal("balance")
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(ctx -> adminBalance(ctx))))
                        .then(Commands.literal("machine")
                                .executes(ctx -> adminMachine(ctx)))));
    }

    private static EconomyService economyOr(CommandSourceStack source) {
        EconomyService economy = TeamEconomyMod.get().economy();
        if (economy == null) {
            source.sendFailure(Component.translatable("command.teamecon.economy_not_ready"));
        }
        return economy;
    }

    private static ServerPlayer adminTarget(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return ctx.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("player"))
                ? EntityArgument.getPlayer(ctx, "player") : ctx.getSource().getPlayerOrException();
    }

    private static int adminStatus(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var economy = economyOr(ctx.getSource());
        if (economy == null) return 0;
        var player = adminTarget(ctx);
        var wallet = TeamUtil.walletKey(player.getServer(), player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.teamecon.admin_status",
                economy.manager().casinoLevel(wallet), economy.manager().getBalance(wallet),
                bypassLabel(economy.manager().bypassesProgression(player.getUUID()))), false);
        return 1;
    }

    private static Component bypassLabel(boolean enabled) { return Component.translatable(enabled ? "options.on" : "options.off"); }

    private static int adminProgress(CommandContext<CommandSourceStack> ctx, int level, Boolean bypass) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var economy = economyOr(ctx.getSource());
        if (economy == null) return 0;
        var player = adminTarget(ctx);
        var wallet = TeamUtil.walletKey(player.getServer(), player.getUUID());
        if (level > 0) economy.manager().setCasinoLevel(wallet, level);
        if (bypass != null) economy.manager().setProgressionBypass(player.getUUID(), bypass);
        for (var member : player.getServer().getPlayerList().getPlayers()) {
            com.evolt.teamecon.network.ProgressionNetwork.send(member, true);
            if (member.containerMenu instanceof com.evolt.teamecon.shop.ShopMenu)
                com.evolt.teamecon.network.ShopNetwork.sendSync(member);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.teamecon.admin_progress",
                economy.manager().casinoLevel(wallet), bypassLabel(economy.manager().bypassesProgression(player.getUUID()))), true);
        return 1;
    }

    private static int balance(CommandSourceStack source) {
        EconomyService economy = economyOr(source);
        if (economy == null) {
            return 0;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.teamecon.players_only"));
            return 0;
        }
        UUID teamKey = TeamUtil.walletKey(source.getServer(), player.getUUID());
        long balance = economy.manager().getBalance(teamKey);
        String name = TeamUtil.displayName(source.getServer(), player.getUUID());
        source.sendSuccess(() -> Component.translatable("command.teamecon.balance", name, balance), false);
        return (int) Math.min(Integer.MAX_VALUE, balance);
    }

    private static int price(CommandContext<CommandSourceStack> ctx) {
        EconomyService economy = economyOr(ctx.getSource());
        if (economy == null) {
            return 0;
        }
        var item = ItemArgument.getItem(ctx, "item").getItem();
        String key = economy.prices().itemKey(item);
        PriceService.Result result = economy.prices().resolve(key);
        String text = switch (result.source()) {
            case BASE -> "command.teamecon.price_base";
            case CUSTOM -> "command.teamecon.price_custom";
            case DERIVED -> "command.teamecon.price_derived";
            case FALLBACK -> "command.teamecon.price_fallback";
            default -> "command.teamecon.price_unknown";
        };
        ctx.getSource().sendSuccess(() -> Component.translatable(text, key, result.unitPrice()), false);
        return (int) result.unitPrice();
    }

    private static int sell(CommandContext<CommandSourceStack> ctx) {
        EconomyService economy = economyOr(ctx.getSource());
        if (economy == null) {
            return 0;
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("command.teamecon.players_only"));
            return 0;
        }
        ItemStack hand = player.getMainHandItem();
        if (hand.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.teamecon.sell_empty"));
            return 0;
        }
        EconomyService.SellResult result = economy.sell(player, hand);
        switch (result.outcome()) {
            case OK -> ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.teamecon.sell_ok", result.soldCount(), result.total()), false);
            case NOT_PRICED -> ctx.getSource().sendFailure(Component.translatable(
                    "command.teamecon.sell_not_priced"));
            default -> ctx.getSource().sendFailure(Component.translatable("command.teamecon.cannot_sell"));
        }
        return result.soldCount();
    }

    private static int buy(CommandContext<CommandSourceStack> ctx) {
        EconomyService economy = economyOr(ctx.getSource());
        if (economy == null) {
            return 0;
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("command.teamecon.players_only"));
            return 0;
        }
        var item = ItemArgument.getItem(ctx, "item").getItem();
        int count = IntegerArgumentType.getInteger(ctx, "count");
        EconomyService.BuyResult result = economy.buy(player, item, count);
        switch (result.outcome()) {
            case OK -> ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.teamecon.buy_ok", count, result.total()), false);
            case NO_FUNDS -> ctx.getSource().sendFailure(Component.translatable(
                    "command.teamecon.buy_no_funds", result.total()));
            case NO_SPACE -> ctx.getSource().sendFailure(Component.translatable("command.teamecon.buy_no_space"));
            case PROGRESSION_LOCKED -> ctx.getSource().sendFailure(com.evolt.teamecon.shop.PurchaseRules.describe(
                    economy.purchaseAccess(player, economy.prices().itemKey(item)).reason()));
            default -> ctx.getSource().sendFailure(Component.translatable("command.teamecon.cannot_buy"));
        }
        return result.boughtCount();
    }


    private static int adminBalance(CommandContext<CommandSourceStack> ctx) {
        EconomyService economy = economyOr(ctx.getSource());
        if (economy == null) {
            return 0;
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("command.teamecon.players_only"));
            return 0;
        }
        long amount = LongArgumentType.getLong(ctx, "amount");
        java.util.UUID teamKey = com.evolt.teamecon.team.TeamUtil.walletKey(ctx.getSource().getServer(), player.getUUID());
        economy.manager().setBalance(teamKey, amount);
        long shown = economy.manager().getBalance(teamKey);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.teamecon.admin_balance_set", shown), true);
        return (int) Math.min(Integer.MAX_VALUE, shown);
    }

    private static int adminMachine(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.translatable("command.teamecon.players_only"));
            return 0;
        }
        net.minecraft.world.item.Item[] kit = {
            com.evolt.teamecon.init.ModRegistries.SLOT_MACHINE_ITEM.get(),
            com.evolt.teamecon.init.ModRegistries.MULTIPLIER_MACHINE_ITEM.get(),
            com.evolt.teamecon.init.ModRegistries.COLOR_WHEEL_TABLE_ITEM.get(),
            com.evolt.teamecon.init.ModRegistries.HILO_TABLE_ITEM.get(),
            com.evolt.teamecon.init.ModRegistries.ROULETTE_TABLE_ITEM.get(),
            com.evolt.teamecon.init.ModRegistries.PENGUIN_MACHINE_ITEM.get(),
            com.evolt.teamecon.init.ModRegistries.SHOP_MACHINE_ITEM.get(),
            com.evolt.teamecon.init.ModRegistries.BLIND_BOX_MACHINE_ITEM.get()
        };
        for (var item : kit) {
            ItemStack stack = new ItemStack(item);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.teamecon.admin_machine"), true);
        return 1;
    }


    private static int shop(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.teamecon.players_only"));
            return 0;
        }
        com.evolt.teamecon.shop.ShopService shop = com.evolt.teamecon.TeamEconomyMod.get().shop();
        if (shop == null) {
            source.sendFailure(Component.translatable("command.teamecon.economy_not_ready"));
            return 0;
        }
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (containerId, inventory, p) -> new com.evolt.teamecon.shop.ShopMenu(containerId, inventory),
                Component.translatable("container.teamecon.shop")), buffer -> buffer.writeBoolean(false));
        return 1;
    }    private static int pending(CommandSourceStack source) {
        EconomyService economy = economyOr(source);
        if (economy == null) {
            return 0;
        }
        var pending = economy.prices().unknownItems();
        if (pending.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.teamecon.pending_empty"), false);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "command.teamecon.pending_list", pending.size(), String.join(", ", pending)), false);
        }
        return pending.size();
    }
}
